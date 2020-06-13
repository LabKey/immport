package org.labkey.test.tests.immport;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.test.Locator;
import org.labkey.test.categories.Git;
import org.labkey.test.pages.immport.ImmPortBeginPage;
import org.labkey.test.pages.immport.ImportExpressionMatrixPage;
import org.labkey.test.pages.immport.PublishExpressionMatrixPage;
import org.labkey.test.tests.microarray.BaseExpressionMatrixTest;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.Maps;
import org.labkey.test.util.PipelineAnalysisHelper;
import org.labkey.test.util.PipelineStatusTable;
import org.labkey.test.util.PortalHelper;
import org.labkey.test.util.PostgresOnlyTest;
import org.openqa.selenium.NoSuchElementException;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@Category({Git.class})
public class ExpressionMatrixPublishTest extends BaseExpressionMatrixTest implements PostgresOnlyTest
{
    private static final String PUBLISH_PROJECT = "Expression Matrix Publish Target";
    private static final String runName = "publishing_run";

    @Override
    protected void doCleanup(boolean afterTest)
    {
        super.doCleanup(afterTest);
        _containerHelper.deleteProject(PUBLISH_PROJECT, false);
    }

    @BeforeClass
    public static void initialImport()
    {
        ExpressionMatrixPublishTest initTest = (ExpressionMatrixPublishTest)getCurrentTest();

        initTest.doInitialImport();
    }

    private void doInitialImport()
    {
        final PipelineAnalysisHelper pipelineAnalysis = new PipelineAnalysisHelper(this);

        final String importAction = "Use R to generate a dummy matrix tsv output file with two samples and two features.";
        final String protocolName = "CreateMatrix";
        final String description = "Execute create-matrix R pipeline, import values. Also protocolDescription comment field.";
        final String[] targetFiles = {CEL_FILE1.getName(), CEL_FILE2.getName()};
        final String parameterXml = getParameterXml(runName, protocolName, null, String.valueOf(getFeatureSetId()), false, null);
        final Map<String, String> protocolProperties = Maps.of(
                "protocolName", protocolName,
                "protocolDescription", description,
                "xmlParameters", parameterXml,
                "saveProtocol", "false");
        pipelineAnalysis.runPipelineAnalysis(importAction, targetFiles, protocolProperties);
        goToModule("Pipeline");
        waitForPipelineJobsToComplete(1, protocolName, false);
    }

    @Test
    public void testExpressionMatrixPublish()
    {
        log("Create publish target");
        _containerHelper.createProject(PUBLISH_PROJECT, null);
        _containerHelper.enableModule("Microarray");
        log("Create feature set to match published expression matrix");
        createFeatureSet(getFeatureSetName(), new PortalHelper(this));

        log("Publish Expression Matrix run from " + getProjectName());
        goToProjectHome();
        goToModule("ImmPort");
        ImportExpressionMatrixPage importExpressionMatrixPage = new ImmPortBeginPage(this)
                .publishExpressionMatrix()
                .selectTargetFolder(PUBLISH_PROJECT)
                .clickPublish();
        assertEquals("Expression matrix publish didn't go to the correct folder", "/" + PUBLISH_PROJECT, getCurrentContainerPath());
        assertEquals("Unexpected XAR selected for import", "analysis/exprs_matrices/matrix_export.xar.xml", importExpressionMatrixPage.getSelectedXar());
        PipelineStatusTable pipelineStatusTable = importExpressionMatrixPage.clickImport();
        waitForPipelineJobsToComplete(1, "import published expression matrices", false);

        goToManageAssays();
        clickAndWait(Locator.linkWithText(ASSAY_NAME));
        assertTrue("Imported expression matrices did get linked to existing feature set: " + getFeatureSetName(),
                isElementPresent(Locator.linkWithText(getFeatureSetName())));
        clickAndWait(Locator.linkWithText(runName));

        log("Verify expression matrix results table");
        DataRegionTable resultTable = new DataRegionTable("Data", this);
        List<String> expectedColumns = Arrays.asList("Value", "Probe Id", "Sample Id", "Run");
        List<String> columnLabels = resultTable.getColumnLabels();
        assertEquals("Wrong columns in published expression matrix results", expectedColumns, columnLabels);
        goBack();

        log("Verify Experiment Run Graph");
        clickAndWait(Locator.linkWithTitle("Experiment run graph"));
        clickAndWait(Locator.linkWithText("Text View"));
        assertTextPresent("create-matrix.xml", CEL_FILE1.getName(), CEL_FILE2.getName());
        clickAndWait(Locator.linkWithText(CEL_FILE1.getName()));
        File download = clickAndWaitForDownload(Locator.linkWithText("Download"));
        assertTrue("Downloaded file was empty: " + download.getAbsolutePath(),download.length() > 0);

        log("Verify assay schema");
        goToSchemaBrowser();
        selectQuery("assay.ExpressionMatrix." + ASSAY_NAME, "FeatureDataBySample");

        // Re-publish/merge not supported; sample type throws duplicate key error
    }

    @Test
    public void testEmptyPublishAction()
    {
        PublishExpressionMatrixPage publishPage = PublishExpressionMatrixPage.beginAt(this, "home");
        assertTextPresent("No Expression Matrix assay was found");
        try
        {
            publishPage.clickPublish();
            fail("Expression Matrix publish form should not be present when there are no eligible runs");
        }
        catch (NoSuchElementException expected)
        {
            // expected exception
        }
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList("microarray", "immport");
    }
}
