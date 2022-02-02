package org.labkey.immport;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.FolderImporterFactory;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.User;
import org.labkey.api.writer.VirtualFile;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import static org.labkey.immport.DifferentialExpressionWriterFactory.DATA_TYPE;
import static org.labkey.immport.DifferentialExpressionWriterFactory.DIRECTORY_NAME;
import static org.labkey.immport.DifferentialExpressionWriterFactory.MODULE_NAME;
import static org.labkey.immport.DifferentialExpressionWriterFactory.SCHEMA_NAME;

public class DifferentialExpressionImporterFactory implements FolderImporterFactory
{
    @Override
    public FolderImporter create()
    {
        return new _FolderImporter();
    }

    @Override
    public int getPriority()
    {
        return 100;
    }


    private static class _FolderImporter implements FolderImporter
    {
        @Override
        public String getDataType()
        {
            return DATA_TYPE;
        }

        @Override
        public String getDescription()
        {
            return "Differential Expression data";
        }

        @Override
        public void process(@Nullable PipelineJob job, FolderImportContext ctx, VirtualFile root) throws Exception
        {
            boolean hasData = false;

            VirtualFile outputDir = root.getDir(DIRECTORY_NAME);
            Container c = ctx.getContainer();
            User user = ctx.getUser();

            QuerySchema schema = DefaultSchema.get(user, c).getSchema(SCHEMA_NAME);
            if (null == schema)
                return;

            for (String tableName : Arrays.asList("gene_expression_analysis", "gene_expression_analysis_results"))
            {
                TableInfo t = schema.getTable(tableName);
                QueryUpdateService qus = t.getUpdateService();
                try (InputStream is = outputDir.getInputStream(tableName + ".tsv"))
                {
                    if (is == null)
                        return;

                    if (hasData == false)
                    {
                        // enable module
                        if (null != job)
                            job.info("Enabling module " + MODULE_NAME);
                        HashSet<Module> enabled = new HashSet<>(ctx.getContainer().getActiveModules());
                        Module m = ModuleLoader.getInstance().getModule(MODULE_NAME);
                        boolean changed = enabled.add(m);
                        changed |= enabled.addAll(m.getResolvedModuleDependencies());
                        if (changed)
                            ctx.getContainer().setActiveModules(enabled);
                        hasData = true;
                    }

                    if (null != job)
                        job.info("Loading " + tableName);

                    TabLoader tab = new TabLoader(new InputStreamReader(is, StandardCharsets.UTF_8), true);
                    BatchValidationException errors = new BatchValidationException();
                    long count = qus.importRows(ctx.getUser(), ctx.getContainer(), tab, errors, null, null);
                    if (errors.hasErrors())
                    {
                        if (null != job)
                            job.error(errors.getRowErrors().get(0).getMessage());
                        else
                            throw errors;
                    }

                    if (null != job)
                        job.info("Done importing " + tableName + " - " + count + " rows");
                }
            }
        }

        @NotNull
        @Override
        public Collection<PipelineJobWarning> postProcess(FolderImportContext ctx, VirtualFile root)
        {
            return Collections.emptyList();
        }
    }
}
