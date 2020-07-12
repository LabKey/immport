/* * Copyright (c) 2013-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.immport.data;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.VFS;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveTreeSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.ParameterMapStatement;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.FilterDataIterator;
import org.labkey.api.dataiterator.Pump;
import org.labkey.api.dataiterator.ResultSetDataIterator;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.dataiterator.StandardDataIteratorBuilder;
import org.labkey.api.dataiterator.StatementDataIterator;
import org.labkey.api.dataiterator.WrapperDataIterator;
import org.labkey.api.exp.list.ListImportProgress;
import org.labkey.api.pipeline.CancelledException;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.User;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.immport.ImmPortController;
import org.labkey.immport.ImmPortDocumentProvider;
import org.springframework.dao.DataAccessException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DataLoader extends PipelineJob
{
    static final transient Logger LOG = Logger.getLogger(DataLoader.class);

    Map<String,Map<String,String>> _lookupDictionary = new CaseInsensitiveHashMap<>();

    ArrayList<String> studyAccessions = new ArrayList<>();

    static class CopyConfig extends org.labkey.api.dataiterator.CopyConfig
    {
        final QueryUpdateService.InsertOption option;

        CopyConfig(String sourceSchema, String source, String targetSchema, String target, QueryUpdateService.InsertOption option)
        {
            super(sourceSchema, source, targetSchema, target);
            this.option = option;
        }

        QueryUpdateService.InsertOption getInsertOption()
        {
            return option;
        }

        DataIteratorBuilder selectFromSource(DataLoader dl, DataIteratorContext context, @Nullable FileObject dir, Logger log) throws SQLException, IOException
        {
            QuerySchema sourceSchema = DefaultSchema.get(dl.getUser(), dl.getContainer(), this.getSourceSchema());
            if (null == sourceSchema)
            {
                context.getErrors().addRowError(new ValidationException("Could not find source schema: " + this.getSourceSchema()));
                return null;
            }
            String sql = getSourceQuery().startsWith("SELECT") ? getSourceQuery() : "SELECT * FROM " + getSourceQuery();
            ResultSet rs = QueryService.get().select(sourceSchema, sql);
            return new DataIteratorBuilder.Wrapper(ResultSetDataIterator.wrap(rs, context));
        }

        int copyFrom(Container c, User u, DataIteratorContext context, DataIteratorBuilder from, DataLoader dl)
                throws IOException, BatchValidationException, SQLException
        {
            assert this.getTargetSchema().getParts().size()==1;
            DbSchema targetSchema = DbSchema.get(this.getTargetSchema().getName());
            TableInfo targetTableInfo = targetSchema.getTable(getTargetQuery());
            updateInsertOptionBeforeCopy(context, targetTableInfo);
            return copy(context, from, targetTableInfo, c, u, dl);
        }

        protected void updateInsertOptionBeforeCopy(DataIteratorContext context, TableInfo targetTableInfo)
        {
            if (context.getInsertOption() == QueryUpdateService.InsertOption.MERGE && null != targetTableInfo)
            {
                if (!(new TableSelector(targetTableInfo).exists()))
                    context.setInsertOption(QueryUpdateService.InsertOption.IMPORT);
            }
        }

        public void deleteFromTarget(PipelineJob job, List<String> studies) throws IOException, SQLException
        {
            DbSchema targetSchema = DbSchema.get(getTargetSchema().getName());
            int rows = new SqlExecutor(targetSchema).execute("DELETE FROM " + getTargetSchema().getName() + "." + getTargetQuery());
            job.info("" + rows + " " + (rows == 1 ? "row" : "rows") + " deleted from " + getTargetQuery());
        }
    }


    // Like DataIteratorUtil.copy, but with cancel support
    static int copy(final DataIteratorContext context, DataIteratorBuilder from, TableInfo to, Container c, User user, final DataLoader dl)
            throws IOException, BatchValidationException
    {
        StandardDataIteratorBuilder etl = StandardDataIteratorBuilder.forInsert(to, from, c, user, context);
        DataIteratorBuilder insert = ((UpdateableTableInfo)to).persistRows(etl, context);
        Pump pump = new Pump(insert, context);
        pump.setProgress(new ListImportProgress()
        {
            @Override
            public void setTotalRows(int rows)
            {

            }

            @Override
            public void setCurrentRow(int currentRow)
            {
                if (dl.checkInterrupted())
                    throw new CancelledException();
            }
        });
        pump.run();
        return pump.getRowCount();
    }



    /* copy from a .zip mysql archive */

    static class ImmPortCopyConfig extends CopyConfig
    {
        // we do not delete from shared tables when in import a single study
        String file;


        ImmPortCopyConfig(String table)
        {
            super("#TEXT#", table, "immport", table, QueryUpdateService.InsertOption.IMPORT);
            file = table;
        }

        ImmPortCopyConfig(String table, QueryUpdateService.InsertOption option)
        {
            super("#TEXT#", table, "immport", table, option);
            file = table;
        }


        @Override
        DataIteratorBuilder selectFromSource(DataLoader dl, DataIteratorContext context, @Nullable FileObject dir, Logger log) throws SQLException, IOException
        {
            if (null == dir)
                return super.selectFromSource(dl,context,dir,log);

            FileObject tsvFile = null;
            FileObject loadFile = null;
            FileObject f;
            if ((f=dir.resolveFile("Tab/" + file + ".txt")).exists())
                tsvFile = f;
            else if ((f=dir.resolveFile("load/" + file + ".load")).exists())
                loadFile = f;

            ColumnDescriptor[] cols = null;
            if (null == tsvFile && null == loadFile)
            {
                context.getErrors().addRowError(new ValidationException("Could not find data file: " + file + ".txt"));
                return null;
            }

            if (null != tsvFile)
            {
                if (0 == tsvFile.getContent().getSize())
                {
                    log.warn("Data file is empty: " + file + ".txt");
                    return null;
                }
                TabLoader tl = (TabLoader)new TabLoader.TsvFactory().createLoader(tsvFile.getContent().getInputStream(), true, null);
                tl.setInferTypes(false);
                return tl;
            }
            else
            {
                try (InputStream is = loadFile.getContent().getInputStream())
                {
                    String loadConfig = IOUtils.toString(is, Charset.forName("UTF-8"));
                    Matcher m = Pattern.compile("\"([^\"]*)\"").matcher(loadConfig);
                    if (m.find() && !StringUtils.isEmpty(m.group(1)))
                        tsvFile = dir.resolveFile("load/" + m.group(1));
                    m = Pattern.compile("\\(([^\\)]*)\\)",Pattern.MULTILINE).matcher(loadConfig);
                    if (m.find())
                    {
                        String[] colNames = StringUtils.split(StringUtils.trim(m.group(1)),",");
                        cols = new ColumnDescriptor[colNames.length];
                        for (int i=0 ; i<colNames.length ; i++)
                            cols[i] = new ColumnDescriptor(StringUtils.trim(colNames[i]));
                    }
                    if (null == tsvFile || null == cols)
                    {
                        context.getErrors().addRowError(new ValidationException("Could not parse file: " + loadFile.toString()));
                        return null;
                    }
                    else if (!tsvFile.exists())
                    {
                        context.getErrors().addRowError(new ValidationException("Could not find data file: " + tsvFile.toString()));
                        return null;
                    }
                    if (0 == tsvFile.getContent().getSize())
                    {
                        log.warn("Data file is empty: " + tsvFile.getName());
                        return null;
                    }
                    final TabLoader tl = (TabLoader)new TabLoader.MysqlFactory().createLoader(tsvFile.getContent().getInputStream(), false, null);
                    tl.setInferTypes(false);
                    tl.setColumns(cols);
                    return tl;
                }
            }
        }

        protected void afterCopy(PipelineJob job)
        {
        }
    }


    /**
     *  Tables that don't have a study_accession column
     */
    static class SharedCopyConfig extends ImmPortCopyConfig
    {
        SharedCopyConfig(String table)
        {
            super(table, QueryUpdateService.InsertOption.MERGE);
        }

        SharedCopyConfig(String table, QueryUpdateService.InsertOption option)
        {
            super(table, option);
        }

        @Override
        public void deleteFromTarget(PipelineJob job, List<String> studies) throws IOException, SQLException
        {
            assert 1==1 : "place to put breakpoint";
        }
    }


    // hand-written for postgres performance
    static abstract class CustomMergeCopyConfig extends BiosampleCopyConfig
    {
        CustomMergeCopyConfig(String table)
        {
            super(table, QueryUpdateService.InsertOption.MERGE);
        }

        // TODO don't hard code the custom queries, generate the "upsert" statement using schema meta-data
        abstract ParameterMapStatement getParameterMap() throws SQLException;

        @Override
        int copyFrom(Container c, User user, DataIteratorContext context, DataIteratorBuilder from, DataLoader dl) throws IOException, BatchValidationException, SQLException
        {
            assert this.getTargetSchema().getParts().size()==1;
            DbSchema targetSchema = DbSchema.get(this.getTargetSchema().getName());
            TableInfo targetTableInfo = targetSchema.getTable(getTargetQuery());

            // non-merge case
            if (context.getInsertOption() == QueryUpdateService.InsertOption.MERGE && null != targetTableInfo)
            {
                if (!(new TableSelector(targetTableInfo).exists()))
                {
                    context.setInsertOption(QueryUpdateService.InsertOption.IMPORT);
                    return super.copyFrom(c,user,context,from,dl);
                }
            }

            StandardDataIteratorBuilder etl = StandardDataIteratorBuilder.forInsert(targetTableInfo, from, c, user, context);
            //DataIteratorBuilder insert = ((UpdateableTableInfo)targetTableInfo).persistRows(etl, context);
            DataIterator insert = new _StatementDataIterator(etl.getDataIterator(context),getParameterMap(),context);
            Pump pump = new Pump(insert, context);
            pump.setProgress(new ListImportProgress()
            {
                @Override
                public void setTotalRows(int rows)
                {

                }

                @Override
                public void setCurrentRow(int currentRow)
                {
                    if (dl.checkInterrupted())
                        throw new CancelledException();
                }
            });
            pump.run();
            return pump.getRowCount();
        }
    }


    static class _StatementDataIterator extends StatementDataIterator
    {
        _StatementDataIterator(DataIterator data, @Nullable ParameterMapStatement map, DataIteratorContext context)
        {
            super(data, context, map);
        }
    }


    /**
     * Small one or two column tables (name) or (name,description)
     * Can use in-memory filtering to avoid merge
     */
    static class LookupCopyConfig extends SharedCopyConfig
    {
        final boolean force;

        LookupCopyConfig(String table, boolean forceRemoveDuplicates)
        {
            // we don't need to merge, because we're filtering in memory
            super(table, QueryUpdateService.InsertOption.IMPORT);
            force = forceRemoveDuplicates;
        }

        LookupCopyConfig(String table)
        {
            this(table,false);
        }


        @Override
        DataIteratorBuilder selectFromSource(DataLoader dl, DataIteratorContext context, @Nullable FileObject dir, Logger log) throws SQLException, IOException
        {
            // select existing names
            DbSchema targetSchema = DbSchema.get(getTargetSchema().getName());
            final ArrayList<String> names = new SqlSelector(targetSchema, "SELECT Name FROM " + getTargetSchema().getName() + "." + getTargetQuery()).getArrayList(String.class);
            final DataIteratorBuilder select = super.selectFromSource(dl,context,dir,log);
            if (select == null)
                return null;
            if (names.isEmpty() && !force)
                return select;
            return new LookupInsertFilter(select, names);
        }
    }


    /**
     * Tables that have a study_accession column
     */
    static class StudyCopyConfig extends ImmPortCopyConfig
    {
        StudyCopyConfig(String table)
        {
            super(table);
        }

        @Override
        public void deleteFromTarget(PipelineJob job, List<String> studies) throws IOException, SQLException
        {
            DbSchema targetSchema = DbSchema.get(getTargetSchema().getName());

            SQLFragment deleteSql = new SQLFragment();
            deleteSql.append(
                "DELETE FROM " + getTargetSchema().getName() + "." + getTargetQuery() + "\n" +
                    "WHERE study_accession ");
            targetSchema.getSqlDialect().appendInClauseSql(deleteSql,studies);

            int rows = new SqlExecutor(targetSchema).execute(deleteSql);
            job.info("" + rows + " " + (rows == 1 ? "row" : "rows") + " deleted from " + getTargetQuery());
        }

        @Override
        DataIteratorBuilder selectFromSource(DataLoader dl, DataIteratorContext context, @Nullable FileObject dir, Logger log) throws SQLException, IOException
        {
            final DataIteratorBuilder select = super.selectFromSource(dl,context,dir,log);
            final boolean restricted = dl._restricted;

            if (null == select)
                return null;
            if (!"study".equals(file))
                return select;

            // OK wrapp the data iterator with a wrapper, that adds the "restricted" column;
                DataIteratorBuilder dib;
                dib = new DataIteratorBuilder()
                {
                    @Override
                    public DataIterator getDataIterator(DataIteratorContext context)
                    {
                        DataIterator in = select.getDataIterator(context);
                        // TODO in DR30 type column has gone away, fill in with '-' for now
                        // TODO DROP COLUMN in 19.2 or 19.3
                        boolean hasTypeColumn = false;
                        for (int i=1 ; i<= in.getColumnCount() ; i++)
                            hasTypeColumn |= "type".equalsIgnoreCase(in.getColumnInfo(i).getName());

                        SimpleTranslator out = new SimpleTranslator(in, context);
                        out.selectAll();
                        out.addConstantColumn("restricted", JdbcType.BOOLEAN, restricted);
                        if (!hasTypeColumn)
                            out.addConstantColumn("type", JdbcType.VARCHAR, "-");
                        return out;
                    }
                };
                return dib;
        }
    }


    /* get study by joining to biosample.biosample_accession */
    static class BiosampleCopyConfig extends ImmPortCopyConfig
    {
        BiosampleCopyConfig(String table)
        {
            super(table);
        }

        BiosampleCopyConfig(String table, QueryUpdateService.InsertOption option)
        {
            super(table, option);
        }

        @Override
        DataIteratorBuilder selectFromSource(DataLoader dl, DataIteratorContext context, @Nullable FileObject dir, Logger log) throws SQLException, IOException
        {
            return super.selectFromSource(dl, context, dir, log);
        }

        @Override
        public void deleteFromTarget(PipelineJob job, List<String> studies) throws IOException, SQLException
        {
            DbSchema targetSchema = DbSchema.get(getTargetSchema().getName());
            TableInfo targetTableInfo = targetSchema.getTable(getTargetQuery());

            if (null == targetTableInfo)
                throw new ConfigurationException("table not found: " + getTargetQuery());

            SQLFragment deleteSql = new SQLFragment();

            if (null != targetTableInfo.getColumn("biosample_accession"))
            {
                deleteSql.append(
                        "DELETE FROM " + getTargetSchema().getName() + "." + getTargetQuery() + "\n" +
                                "WHERE biosample_accession IN (SELECT biosample_accession FROM " + getTargetSchema().getName() + ".biosample WHERE study_accession ");
                targetSchema.getSqlDialect().appendInClauseSql(deleteSql, studies);
                deleteSql.append(")");
            }
            else if (null != targetTableInfo.getColumn("expsample_accession"))
            {
                deleteSql.append(
                        "DELETE FROM " + getTargetSchema().getName() + "." + getTargetQuery() + "\n" +
                        "WHERE expsample_accession IN \n" +
                        "  (SELECT expsample_2_biosample.expsample_accession FROM " + getTargetSchema().getName() + ".biosample \n" +
                        "    INNER JOIN " + getTargetSchema().getName() + ".expsample_2_biosample ON biosample.biosample_accession=expsample_2_biosample.biosample_accession\n" +
                        "    WHERE biosample.study_accession ");
                targetSchema.getSqlDialect().appendInClauseSql(deleteSql, studies);
                deleteSql.append(")");
            }
            else
            {
                throw new IllegalStateException("could not find biosample_accession or expsample_accession");
            }

            int rows = new SqlExecutor(targetSchema).execute(deleteSql);
            job.info("" + rows + " " + (rows == 1 ? "row" : "rows") + " deleted from " + getTargetQuery());
        }
    }

    /* get study by joining to arm_accession -> arm_or_cohort */
    static class ArmCopyConfig extends ImmPortCopyConfig
    {
        ArmCopyConfig(String table)
        {
            super(table);
        }

        @Override
        public void deleteFromTarget(PipelineJob job, List<String> studies) throws IOException, SQLException
        {
            DbSchema targetSchema = DbSchema.get(getTargetSchema().getName());

            SQLFragment deleteSql = new SQLFragment();
            deleteSql.append(
                    "DELETE FROM " + getTargetSchema().getName() + "." + getTargetQuery() + "\n" +
                            "WHERE arm_accession IN (SELECT arm_accession FROM " + getTargetSchema().getName() + ".arm_or_cohort WHERE study_accession ");
            targetSchema.getSqlDialect().appendInClauseSql(deleteSql, studies);
            deleteSql.append(")");

            int rows = new SqlExecutor(targetSchema).execute(deleteSql);
            job.info("" + rows + " " + (rows == 1 ? "row" : "rows") + " deleted from " + getTargetQuery());
        }
    }

    /* get study by joining to experiment_accession -> experiment */
    static class ExperimentCopyConfig extends ImmPortCopyConfig
    {
        ExperimentCopyConfig(String table)
        {
            super(table);
        }

        @Override
        public void deleteFromTarget(PipelineJob job, List<String> studies) throws IOException, SQLException
        {
            DbSchema targetSchema = DbSchema.get(getTargetSchema().getName());

            SQLFragment deleteSql = new SQLFragment();
            deleteSql.append(
                    "DELETE FROM " + getTargetSchema().getName() + "." + getTargetQuery() + "\n" +
                            "WHERE experiment_accession IN (SELECT experiment_accession FROM " + getTargetSchema().getName() + ".experiment WHERE study_accession ");
            targetSchema.getSqlDialect().appendInClauseSql(deleteSql, studies);
            deleteSql.append(")");

            int deleted = new SqlExecutor(targetSchema).execute(deleteSql);
            job.info("" + deleted + " " + (deleted == 1 ? "row" : "rows") + " deleted from " + getTargetQuery());
        }
    }

    /* get study by joining to expsample_accession -> expsample -> experiment */
    static class ExpsampleCopyConfig extends ImmPortCopyConfig
    {
        ExpsampleCopyConfig(String table)
        {
            super(table);
        }

        @Override
        public void deleteFromTarget(PipelineJob job, List<String> studies) throws IOException, SQLException
        {
            DbSchema targetSchema = DbSchema.get(getTargetSchema().getName());

            SQLFragment deleteSql = new SQLFragment();
            deleteSql.append(
                    "DELETE FROM " + getTargetSchema().getName() + "." + getTargetQuery() + "\n" +
                            "WHERE expsample_accession IN (SELECT expsample_accession from immport.expsample WHERE experiment_accession IN (SELECT experiment_accession FROM " + getTargetSchema().getName() + ".experiment WHERE study_accession ");
            targetSchema.getSqlDialect().appendInClauseSql(deleteSql, studies);
            deleteSql.append("))");

            int deleted = new SqlExecutor(targetSchema).execute(deleteSql);
            job.info("" + deleted + " " + (deleted == 1 ? "row" : "rows") + " deleted from " + getTargetQuery());
        }
    }


    static CopyConfig[] immportTables = new CopyConfig[]
    {
            // lookup tables
        new SharedCopyConfig("lk_adverse_event_severity"),
        new SharedCopyConfig("lk_age_event"),
        new SharedCopyConfig("lk_data_completeness"),
//        new LookupCopyConfig("lk_data_format"),
        new LookupCopyConfig("lk_disease"),
        new LookupCopyConfig("lk_disease_stage"),
        new LookupCopyConfig("lk_ethnicity"),
        new LookupCopyConfig("lk_exposure_material"),
        new LookupCopyConfig("lk_exposure_process"),
        new SharedCopyConfig("lk_exp_measurement_tech"),
        new LookupCopyConfig("lk_expsample_result_schema"),
//        new LookupCopyConfig("lk_experiment_purpose"),

        new LookupCopyConfig("lk_file_detail"),
//        new LookupCopyConfig("lk_file_purpose"),
        new SharedCopyConfig("lk_gender"),
        new LookupCopyConfig("lk_locus_name"),
        new LookupCopyConfig("lk_personnel_role"),
        new SharedCopyConfig("lk_plate_type"),
        new LookupCopyConfig("lk_protocol_type"),
        new SharedCopyConfig("lk_public_repository"),
        new LookupCopyConfig("lk_race"),
        new SharedCopyConfig("lk_reagent_type"),
        new LookupCopyConfig("lk_research_focus"),
        new SharedCopyConfig("lk_sample_type"),
        new SharedCopyConfig("lk_source_type"),
        new SharedCopyConfig("lk_species"),
        new SharedCopyConfig("lk_study_file_type"),
        new SharedCopyConfig("lk_study_panel"),
        new LookupCopyConfig("lk_study_type"),
        new LookupCopyConfig("lk_subject_location"),
        new SharedCopyConfig("lk_t0_event"),
        new SharedCopyConfig("lk_time_unit"),
        new LookupCopyConfig("lk_transcript_type"),
        new SharedCopyConfig("lk_unit_of_measure"),

            // high-level tables
        new SharedCopyConfig("workspace"),
        new StudyCopyConfig("study"),
        new SharedCopyConfig("subject"),
        new StudyCopyConfig("period"),
        new StudyCopyConfig("planned_visit"),
        new StudyCopyConfig("arm_or_cohort"),
        new StudyCopyConfig("biosample"),
        new SharedCopyConfig("experiment"),
        new ExperimentCopyConfig("expsample"),
        new SharedCopyConfig("file_info"),
        new SharedCopyConfig("protocol"),
        new SharedCopyConfig("reagent"),
        new SharedCopyConfig("treatment"),
        new StudyCopyConfig("adverse_event"),
        new ExperimentCopyConfig("control_sample"),
        new SharedCopyConfig("expsample_mbaa_detail"),
        new SharedCopyConfig("expsample_public_repository"),
        new SharedCopyConfig("inclusion_exclusion"),
        new StudyCopyConfig("reference_range"),
        new BiosampleCopyConfig("lab_test"),
        new StudyCopyConfig("protocol_deviation"),
        new StudyCopyConfig("reported_early_termination"),
        new ExperimentCopyConfig("standard_curve"),
        new StudyCopyConfig("study_categorization"),
        new StudyCopyConfig("study_file"),
        new StudyCopyConfig("study_glossary"),
        new StudyCopyConfig("study_image"),
        new StudyCopyConfig("study_link"),
        new StudyCopyConfig("study_personnel"),
        new StudyCopyConfig("study_pubmed"),
        new StudyCopyConfig("subject_measure_definition"),
            // lots of duplicates in contract_grant, is this only the test data???
            // force using merge by override updateInsertOptionBeforeCopy()
        new SharedCopyConfig("contract_grant")
        {
            @Override
            protected void updateInsertOptionBeforeCopy(DataIteratorContext context, TableInfo targetTableInfo)
            {
            }
        },
        new SharedCopyConfig("program"),

            // force using merge by override updateInsertOptionBeforeCopy()
        new SharedCopyConfig("fcs_header_marker")
        {
            @Override
            protected void updateInsertOptionBeforeCopy(DataIteratorContext context, TableInfo targetTableInfo)
            {
            }
        },
            // force using merge by override updateInsertOptionBeforeCopy()
        new SharedCopyConfig("fcs_header")
        {
            @Override
            protected void updateInsertOptionBeforeCopy(DataIteratorContext context, TableInfo targetTableInfo)
            {
            }
        },
        new ArmCopyConfig("immune_exposure"),

            // results
        new StudyCopyConfig("elisa_result"),
        new StudyCopyConfig("elispot_result"),
        new StudyCopyConfig("hai_result"),
        new StudyCopyConfig("fcs_analyzed_result"),
        new StudyCopyConfig("hla_typing_result"),
        new StudyCopyConfig("kir_typing_result"),
        new StudyCopyConfig("mbaa_result"),
        new StudyCopyConfig("neut_ab_titer_result")
        {
            @Override
            protected void afterCopy(PipelineJob job)
            {
                DbSchema s = DbSchema.get("immport", DbSchemaType.Module);
                int count = new SqlExecutor(s).execute(
                        "UPDATE immport.neut_ab_titer_result SET value_preferred=to_number(value_reported,'99999999999.9999') " +
                        "WHERE value_preferred IS NULL and value_reported  ~ '^([0-9]+[.]?[0-9]*|[.][0-9]+)$'");
                job.info("Updated value_preferred column: " + count + " rows");
            }
        },
        new StudyCopyConfig("pcr_result"),
        new StudyCopyConfig("subject_measure_result"),
        new StudyCopyConfig("rna_seq_result"),

            // junction tables
        new ArmCopyConfig("arm_2_subject"),
        new ExpsampleCopyConfig("expsample_2_biosample"),
        new BiosampleCopyConfig("biosample_2_treatment"),
        new ExperimentCopyConfig("experiment_2_protocol"),
        new ExpsampleCopyConfig("expsample_2_file_info"),
        new ExpsampleCopyConfig("expsample_2_reagent"),
        new StudyCopyConfig("study_2_protocol"),
        new SharedCopyConfig("control_sample_2_file_info"),
        new ExpsampleCopyConfig("expsample_2_treatment"),
        new ArmCopyConfig("planned_visit_2_arm"),
        new SharedCopyConfig("standard_curve_2_file_info"),
        new StudyCopyConfig("study_2_panel"),
        new SharedCopyConfig("reagent_set_2_reagent"),

        // this is basically a materialized view, database->database copy
        new CopyConfig("immport", "q_subject_2_study", "immport", "subject_2_study", QueryUpdateService.InsertOption.IMPORT),

        /*
         *  DR20 new tables
         */

        new StudyCopyConfig("assessment_panel"),
        new SharedCopyConfig("assessment_component")
        {
            @Override
            public void deleteFromTarget(PipelineJob job, List<String> studies) throws IOException, SQLException
            {
                DbSchema targetSchema = DbSchema.get(getTargetSchema().getName());
                SQLFragment deleteSql = new SQLFragment();
                deleteSql.append(
                        "DELETE FROM " + getTargetSchema().getName() + "." + getTargetQuery() + "\n" +
                                "WHERE assessment_panel_accession IN (SELECT assessment_panel_accession FROM " + getTargetSchema().getName() + ".assessment_panel WHERE study_accession ");
                targetSchema.getSqlDialect().appendInClauseSql(deleteSql, studies);
                deleteSql.append(")");
                int rows = new SqlExecutor(targetSchema).execute(deleteSql);
                job.info("" + rows + " " + (rows == 1 ? "row" : "rows") + " deleted from " + getTargetQuery());
            }
       },
        new SharedCopyConfig("contract_grant_2_personnel"),
        new StudyCopyConfig("contract_grant_2_study"),

        new SharedCopyConfig("fcs_analyzed_result_marker"),
        new SharedCopyConfig("fcs_header_marker_2_reagent"),

        new StudyCopyConfig("intervention"),
        new StudyCopyConfig("lab_test_panel"),
            new ImmPortCopyConfig("lab_test_panel_2_protocol")
            {
                @Override
                public void deleteFromTarget(PipelineJob job, List<String> studies) throws IOException, SQLException
                {
                    DbSchema targetSchema = DbSchema.get(getTargetSchema().getName());
                    SQLFragment deleteSql = new SQLFragment(
                        "DELETE FROM " + getTargetSchema().getName() + "." + getTargetQuery() + "\n" +
                            "WHERE lab_test_panel_accession IN (SELECT lab_test_panel_accession FROM " + getTargetSchema().getName() + ".lab_test_panel WHERE study_accession ");
                    targetSchema.getSqlDialect().appendInClauseSql(deleteSql, studies);
                    deleteSql.append(")");
                    int rows = new SqlExecutor(targetSchema).execute(deleteSql);
                    job.info("" + rows + " " + (rows == 1 ? "row" : "rows") + " deleted from " + getTargetQuery());
                }
            },
        new SharedCopyConfig("lk_analyte"),
        new SharedCopyConfig("lk_ancestral_population"),
//        new LookupCopyConfig("lk_kir_gene"),
//        new LookupCopyConfig("lk_kir_locus"),
//        new LookupCopyConfig("lk_kir_present_absent"),
        new LookupCopyConfig("lk_organization", true),
        new LookupCopyConfig("lk_user_role_type"),
        new LookupCopyConfig("lk_visibility_category"),
        new SharedCopyConfig("personnel"),
        new SharedCopyConfig("program_2_personnel")
    };


    /**
     * load from external schema attached as "hipc"
     * to the immport schema
     **/
    public void loadFromArchive()
    {
        try
        {
            FileSystemManager fsManager = VFS.getManager();
            FileObject archiveFile;
            if (new File(_archive).isDirectory())
                archiveFile = fsManager.resolveFile(new File(_archive).getAbsoluteFile().toURI().toString());
            else if (new File(_archive).isFile() && _archive.endsWith(".zip"))
                archiveFile = fsManager.resolveFile("zip://" + new File(_archive).getAbsolutePath());
            else
                throw new FileNotFoundException(_archive);

            // DR16 added a top level directory to the archive.  Check for that here.
            // find directories
            Map<String,FileObject> dirs = (Arrays.asList(archiveFile.getChildren())).stream().filter(fo ->
                {
                    try
                    {
                        return fo.getType() == FileType.FOLDER;
                    }
                    catch (FileSystemException fse)
                    {
                        return false;
                    }
                })
            .filter(fo -> !fo.getName().getBaseName().startsWith("."))
            .collect(Collectors.toMap(fo -> fo.getName().getBaseName(), fo -> fo));

            if (!dirs.containsKey("MySQL") && dirs.size()==1)
                archiveFile = dirs.values().iterator().next();

            execute(immportTables, archiveFile);
        }
        catch (IOException|SQLException x)
        {
            error("Unexpected exception", x);
        }
    }


    /** CONSIDER : drop indexes and constraints and add them back for performance */

    public boolean executeCopy(CopyConfig config, @Nullable FileObject dir) throws IOException, SQLException
    {
        DbSchema targetSchema = DbSchema.get(config.getTargetSchema().getName());

        DataIteratorContext context = new DataIteratorContext();
        context.setInsertOption(config.getInsertOption());
        context.setFailFast(true);

        assert !targetSchema.getScope().isTransactionActive();
        try (DbScope.Transaction tx = targetSchema.getScope().ensureTransaction())
        {
            long start = System.currentTimeMillis();
            DataIteratorBuilder source = config.selectFromSource(this, context, dir, getLogger());
            if (null != source)
            {
                if (null == dir)
                {
                    info("Copying data from " + config.getSourceSchema() + "." + config.getSourceQuery() + " to " +
                            config.getTargetSchema() + "." + config.getTargetQuery());
                }
                else
                {
                    info("Copying data from " + dir.toString() + " to " +
                            config.getTargetSchema() + "." + config.getTargetQuery());
                }

                if (config instanceof ImmPortCopyConfig)
                {
                    if (((ImmPortCopyConfig)config).file.startsWith("lk_"))
                    {

                        CaseInsensitiveHashMap<String> values = new CaseInsensitiveHashMap<>();
                        _lookupDictionary.put(((ImmPortCopyConfig)config).file,values);
                        source = new CollectLookups(source,values);
                    }
                    else if (((ImmPortCopyConfig)config).file.equals("experiment"))
                    {
                        source = new FixLookups(source,"purpose",_lookupDictionary.get("lk_experiment_purpose"));
                    }
                }

                int count = config.copyFrom(getContainer(), getUser(), context, source, this);
                if (config instanceof ImmPortCopyConfig)
                    ((ImmPortCopyConfig)config).afterCopy(DataLoader.this);

                tx.commit();

                long finish = System.currentTimeMillis();
                if (!context.getErrors().hasErrors())
                    info("Copied " + count + " row" + (count != 1 ? "s" : "") + " in " + DateUtil.formatDuration(finish - start) + ".");
            }
        }
        catch (BatchValidationException x)
        {
            assert x == context.getErrors();
            /* fall through */
        }
        catch (Exception x)
        {
            error(null==x.getMessage()?x.toString():x.getMessage());
            return false;
        }
        finally
        {
            assert !targetSchema.getScope().isTransactionActive();
        }
        if (context.getErrors().hasErrors())
        {
            for (ValidationException v : context.getErrors().getRowErrors())
            {
                String msg = v.getMessage();
                error(msg);
            }
            return false;
        }
        return true;
    }


    public void execute(CopyConfig[] configs, @Nullable FileObject dir) throws IOException, SQLException
    {
        Set<String> setStudyAccession = new TreeSet<>();

        Exception preException = null;
        try
        {
            // find study accession numbers
            DataIteratorContext dix = new DataIteratorContext();
            DataIteratorBuilder dib = new StudyCopyConfig("study").selectFromSource(this,dix,dir,getLogger());
            if (dix.getErrors().hasErrors())
                throw dix.getErrors();

            if (null != dib)
            {
                DataIterator it = dib.getDataIterator(dix);
                while (it.next())
                {
                    String sdy = (String) it.get(1);
                    setStudyAccession.add(sdy);
                }
            }
        }
        catch (BatchValidationException bve)
        {
            bve.getRowErrors().stream().forEach(ve->error(ve.getMessage()));
        }
        catch (Exception x)
        {
            preException = x;
            setStudyAccession.clear();
        }

        if (setStudyAccession.size() == 0)
        {
            error("Could not load study accession numbers", preException);
            return;
        }

        if (setStudyAccession.contains("SDY998"))
            setStudyAccession.add("SDY999");    // because DR30

        info("Archive contains " + setStudyAccession.size() +" "+(setStudyAccession.size()==1?"study":"studies"));

        studyAccessions.addAll(setStudyAccession);


        ArrayList<CopyConfig> reverse = new ArrayList<>(Arrays.asList(configs));
        Collections.reverse(reverse);

        for (CopyConfig config : reverse)
        {
            if (checkInterrupted())
            {
                throw new CancelledException();
            }
            try
            {
                setStatus(TaskStatus.running, "DELETE from " + config.getTargetQuery());
                config.deleteFromTarget(this,studyAccessions);
            }
            catch (SQLException | DataAccessException | ConfigurationException x)
            {
                error("deleting from " + config.getTargetQuery() + "\n\t" + x.getMessage(), x);
            }
        }

        for (CopyConfig config : configs)
        {
            if (checkInterrupted())
                throw new CancelledException();
            try
            {
                setStatus(TaskStatus.running, "COPY to " + config.getTargetQuery());
                executeCopy(config, dir);
            }
            catch (SQLException | DataAccessException x)
            {
                error("copying to " + config.getTargetQuery() + "\n\t" + x.getMessage(), x);
            }
        }
        setStatus(TaskStatus.running, "DONE copying");
    }


    @Override
    public boolean setStatus(@NotNull TaskStatus status, @Nullable String info)
    {
        DbSchema p = DbSchema.get("pipeline");
        assert !p.getScope().isTransactionActive();

        return super.setStatus(status, info);
    }


    // DataIterator Helpers

    static class CollectLookups implements DataIteratorBuilder
    {
        DataIteratorBuilder _source;
        Map<String,String> _values;

        CollectLookups(DataIteratorBuilder source, Map<String,String> map)
        {
            _source = source;
            _values = map;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            return new WrapperDataIterator(_source.getDataIterator(context))
            {
                @Override
                public Object get(int i)
                {
                    Object o = _delegate.get(i);
                    if (1==i && o instanceof String)
                        _values.put((String)o, (String)o);
                    return o;
                }
            };
        }
    }


    static class FixLookups implements DataIteratorBuilder
    {
        DataIteratorBuilder _source;
        String fk;
        int indexFK=-1;
        Map<String,String> _values;

        FixLookups(DataIteratorBuilder source, String colname, Map<String,String> map)
        {
            _source = source;
            fk = colname;
            _values = map;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator di = _source.getDataIterator(context);
            if (null == di)
                return null;
            if (null != fk && null != _values && !_values.isEmpty())
            {
                for (int i=1 ; i<=di.getColumnCount() ; i++)
                    if (di.getColumnInfo(i).getName().equalsIgnoreCase(fk))
                        indexFK = i;
            }

            return new WrapperDataIterator(_source.getDataIterator(context))
            {
                @Override
                public Object get(int i)
                {
                    Object o = _delegate.get(i);
                    if (i==indexFK && o instanceof String)
                        return StringUtils.defaultString(_values.get(o),(String)o);
                    return o;
                }
            };
        }
    }


    static class LookupInsertFilter implements DataIteratorBuilder
    {
        DataIteratorBuilder in;
        Set<String> existingNames = new CaseInsensitiveTreeSet();

        LookupInsertFilter(DataIteratorBuilder in, Collection<String> names)
        {
            this.in = in;
            this.existingNames.addAll(names);
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            DataIterator it = in.getDataIterator(context);
            int index = 0;
            for (int i=1 ; i<=it.getColumnCount() ; i++)
            {
                ColumnInfo col = it.getColumnInfo(i);
                if ("name".equalsIgnoreCase(col.getName()))
                    index = i;
            }
            if (0 == index)
            {
                context.getErrors().addRowError(new ValidationException("Could not find field called 'name'"));
            }
            final int nameField = index;
            return new FilterDataIterator(it)
            {
                @Override
                protected boolean accept()
                {
                    String name = (String)get(nameField);
                    return existingNames.add(name);
                }
            };
        }
    }



    //
    // PipelineJob
    //


    private String _archive;
    private boolean _restricted;

    // For serialization
    protected DataLoader() { }

    public DataLoader(Container container, User user, String archive, boolean restricted)
    {
        super("ImmPort", new ViewBackgroundInfo(container, user, null), PipelineService.get().getPipelineRootSetting(container));
        _archive = archive;
        _restricted = restricted;
        setLogFile(new File(new File(archive).getParentFile(), FileUtil.makeFileNameWithTimestamp("import", "log")));
    }

    @Override
    public URLHelper getStatusHref()
    {
        return null;
    }

    @Override
    public String getDescription()
    {
        if (null != _archive)
        {
            Path p = Path.parse(_archive);
            return "Load ImmPort archive " + p.getName();
        }
        return "Load ImmPort archive";
    }

    @Override
    public void setLogFile(File fileLog)
    {
        super.setLogFile(fileLog);
    }


    @Override
    public void run()
    {
        try
        {
            _run();
            ImmPortDocumentProvider.reindex();
        }
        catch (CancelledException e)
        {
            setStatus(TaskStatus.cancelled);
        }
        catch (RuntimeException|Error e)
        {
            setStatus(TaskStatus.error);
            ExceptionUtil.logExceptionToMothership(null, e);
            // Rethrow to let the standard Mule exception handler fire and deal with the job state
            throw e;
        }
    }


    public void _run()
    {
        if (checkInterrupted())
            throw new CancelledException();

        setStatus(TaskStatus.running, "Starting import");
        loadFromArchive();

        if (checkInterrupted())
            throw new CancelledException();

        if (CoreSchema.getInstance().getSqlDialect().isPostgreSQL())
        {
            try
            {
                setStatus(TaskStatus.running, "VACUUM ANALYZE");
                info("running VACUUM ANALYZE");
                new SqlExecutor(CoreSchema.getInstance().getScope()).execute("VACUUM ANALYZE");
            }
            catch (Exception x)
            {
                warn(x.getMessage());
            }

            if (checkInterrupted())
                throw new CancelledException();
        }

        setStatus(TaskStatus.running, "Populate cube");
        info("populating cube");
        ImmPortController.populateCube(getContainer());

        if (checkInterrupted())
            throw new CancelledException();

        setStatus(TaskStatus.running, "Adding studies to full text index");
        info("adding studies to full text index");
        ImmPortDocumentProvider.reindex();

        if (checkInterrupted())
            throw new CancelledException();

        info("COMPLETE");
        setStatus(PipelineJob.TaskStatus.complete, "Complete");
    }
}

