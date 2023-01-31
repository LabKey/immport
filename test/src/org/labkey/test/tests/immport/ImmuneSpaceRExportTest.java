package org.labkey.test.tests.immport;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestFileUtils;
import org.labkey.test.categories.Git;
import org.labkey.test.util.DataRegionExportHelper;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.PostgresOnlyTest;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertTrue;

@Category({Git.class})
public class ImmuneSpaceRExportTest extends BaseWebDriverTest implements PostgresOnlyTest
{
    private final String IMMPORT_STUDY = "Study 123";
    private final String NON_IMMPORT_STUDY = "Study 456";
    private final String DATASET_NAME = "ELISA";
    private final String DATASET_COLUMN_NAME = "analyte";
    private final String LIST_NAME = "TestList1";
    private final String LIST_COLUMN_NAME = "FieldString";

    @Override
    protected String getProjectName()
    {
        return "ImmuneSpaceRExport Test";
    }

    @BeforeClass
    public static void setupProject()
    {
        ImmuneSpaceRExportTest init = (ImmuneSpaceRExportTest)getCurrentTest();
        init.doSetup();
    }

    private void doSetup()
    {
        _containerHelper.createProject(getProjectName(), "Study");
        importFolderFromZip(TestFileUtils.getSampleData("HIPC/ImmuneSpaceRExport.folder.zip"), false, 1);
        _containerHelper.setFolderType("Dataspace");
    }

    @Test
    public void testProjectDataset()
    {
        goToProjectHome();
        // Expect that the list of datasets should still be present from the folder archive import, even after we've
        // reset the folder type to Dataspace
        clickAndWait(Locator.linkContainingText(DATASET_NAME));
        verifyRExportScript(true, "", "Dataset", "study", DATASET_NAME.toLowerCase(), DATASET_COLUMN_NAME, "project");
    }

    @Test
    public void testProjectList()
    {
        goToProjectHome();
        // Expect that the list of lists should still be present from the folder archive import, even after we've
        // reset the folder type to Dataspace
        clickAndWait(Locator.linkContainingText(LIST_NAME));
        verifyRExportScript(false, "", "query", "lists", LIST_NAME, LIST_COLUMN_NAME, null);
    }

    @Test
    public void testImmPortStudyDataset()
    {
        goToProjectHome();
        clickFolder(IMMPORT_STUDY);
        clickAndWait(Locator.linkContainingText(DATASET_NAME));
        verifyRExportScript(true, IMMPORT_STUDY, "Dataset", "study", DATASET_NAME.toLowerCase(), DATASET_COLUMN_NAME, "study");
    }

    @Test
    public void testImmPortStudyList()
    {
        goToProjectHome();
        clickFolder(IMMPORT_STUDY);
        clickAndWait(Locator.linkContainingText(LIST_NAME));
        verifyRExportScript(false, IMMPORT_STUDY, "query", "lists", LIST_NAME, LIST_COLUMN_NAME, null);
    }

    @Test
    public void testNonImmPortStudyDataset()
    {
        goToProjectHome();
        clickFolder(NON_IMMPORT_STUDY);
        clickAndWait(Locator.linkContainingText(DATASET_NAME));
        verifyRExportScript(false, NON_IMMPORT_STUDY, "Dataset", "study", DATASET_NAME.toLowerCase(), DATASET_COLUMN_NAME, null);
    }

    @Test
    public void testNonImmPortStudyList()
    {
        goToProjectHome();
        clickFolder(NON_IMMPORT_STUDY);
        clickAndWait(Locator.linkContainingText(LIST_NAME));
        verifyRExportScript(false, NON_IMMPORT_STUDY, "query", "lists", LIST_NAME, LIST_COLUMN_NAME, null);
    }

    private void verifyRExportScript(boolean isImmPort, String containerName, String dataRegionName, String schemaName, String queryName, String filterColName, String noun)
    {
        // test default exported script - no filter
        DataRegionTable dataRegion = new DataRegionTable(dataRegionName, getDriver());
        DataRegionExportHelper exportHelper = new DataRegionExportHelper(dataRegion);
        exportHelper.exportAndVerifyScript(DataRegionExportHelper.ScriptExportType.R, rScript ->
        {
            if (isImmPort)
                assertImmuneSpaceRScriptContents(rScript, noun, containerName, queryName, null);
            else
                assertRScriptContents(rScript, schemaName, queryName, null);
        });

        // test exported script - with filter
        dataRegion.setFilter(filterColName, "Equals", "foo");
        exportHelper = new DataRegionExportHelper(dataRegion);
        exportHelper.exportAndVerifyScript(DataRegionExportHelper.ScriptExportType.R, rScript ->
        {
            if (isImmPort)
                assertImmuneSpaceRScriptContents(rScript, noun, containerName, queryName, filterColName);
            else
                assertRScriptContents(rScript, schemaName, queryName, filterColName);
        });
    }

    private void assertImmuneSpaceRScriptContents(String rScript, String noun, String connContainerName, String datasetName, String filterColName)
    {
        // some browsers return script with ">" and "<" and some with "&gt;" and "&lt;"
        rScript = rScript.replaceAll("&gt;", ">");
        rScript = rScript.replaceAll("&lt;", "<");

        assertTrue("Script is missing ImmuneSpaceR library", rScript.contains("library(ImmuneSpaceR)"));
        assertTrue("Script is missing CreateConnection call", rScript.contains(noun + " <- CreateConnection(\"" + connContainerName + "\")"));
        if (filterColName != null)
        {
            assertTrue("Script is missing Rlabkey library", rScript.contains("library(Rlabkey)"));
            assertTrue("", rScript.contains("colFilter <- makeFilter(c(\"" + filterColName + "\", \"EQUAL\", \"foo\"))"));
            assertTrue("Script is missing getDataset call", rScript.contains("dataset <- " + noun + "$getDataset(\"" + datasetName + "\", original_view = FALSE, colFilter = colFilter)"));
        }
        else
        {
            assertTrue("Script is missing getDataset call", rScript.contains("dataset <- " + noun + "$getDataset(\"" + datasetName + "\", original_view = FALSE)"));
        }
    }

    private void assertRScriptContents(String rScript, String schemaName, String queryName, String filterColName)
    {
        assertTrue("Script should not contain ImmuneSpaceR library", !rScript.contains("library(ImmuneSpaceR)"));
        assertTrue("Script is missing Rlabkey library", rScript.contains("library(Rlabkey)"));
        assertTrue("Script is missing labkey.selectRows call", rScript.contains("labkey.selectRows("));
        assertTrue("Script is missing schemaName property", rScript.contains("schemaName=\"" + schemaName + "\""));
        assertTrue("Script is missing queryName property", rScript.contains("queryName=\"" + queryName + "\""));
        if (filterColName != null)
            assertTrue("Script is missing colFilter property", rScript.contains("colFilter=makeFilter(c(\"" + filterColName + "\", \"EQUAL\", \"foo\"))"));
        else
            assertTrue("Script is missing colFilter property", rScript.contains("colFilter=NULL"));
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList("ImmPort");
    }
}
