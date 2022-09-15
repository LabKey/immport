/*
 * Copyright (c) 2014 LabKey Corporation
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

package org.labkey.test.tests.immport;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.labkey.remoteapi.Command;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.Connection;
import org.labkey.remoteapi.security.WhoAmICommand;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.Locators;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.WebTestHelper;
import org.labkey.test.categories.Git;
import org.labkey.test.components.ParticipantListWebPart;
import org.labkey.test.components.core.NotificationPanelItem;
import org.labkey.test.components.core.UserNotificationsPanel;
import org.labkey.test.components.dumbster.EmailRecordTable;
import org.labkey.test.components.ext4.Window;
import org.labkey.test.components.immport.StudySummaryWindow;
import org.labkey.test.components.study.StudyOverviewWebPart;
import org.labkey.test.pages.core.UserNotificationsPage;
import org.labkey.test.pages.immport.DataFinderPage;
import org.labkey.test.pages.immport.DataFinderPage.Dimension;
import org.labkey.test.pages.immport.ExportStudyDatasetsPage;
import org.labkey.test.pages.immport.ImmPortBeginPage;
import org.labkey.test.pages.immport.SendParticipantPage;
import org.labkey.test.pages.study.ManageParticipantGroupsPage;
import org.labkey.test.pages.study.OverviewPage;
import org.labkey.test.util.APIContainerHelper;
import org.labkey.test.util.AbstractContainerHelper;
import org.labkey.test.util.ApiPermissionsHelper;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.ExperimentalFeaturesHelper;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.PortalHelper;
import org.labkey.test.util.PostgresOnlyTest;
import org.labkey.test.util.ReadOnlyTest;
import org.labkey.test.util.ext4cmp.Ext4CmpRef;
import org.labkey.test.util.ext4cmp.Ext4GridRef;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.labkey.test.util.PermissionsHelper.MemberType;

@Category({Git.class})
public class DataFinderTest extends BaseWebDriverTest implements PostgresOnlyTest, ReadOnlyTest
{
    private static final File immPortArchive = TestFileUtils.getSampleData("HIPC/ANIMAL_STUDIES-DR23.zip");
    private static final File TEMPLATE_ARCHIVE = TestFileUtils.getSampleData("HIPC/SDY_template.zip");
    private static final String[] ANIMAL_STUDIES = {"SDY99", "SDY139", "SDY147", "SDY208", "SDY215", "SDY217"};
    private static final String[] STUDY_SUBFOLDERS = {"SDY139", "SDY147", "SDY208", "SDY217"};
    private static final String USER2_FOLDER = "SDY139";
    private static final String USER1 = "user1@immport.test";
    private static final String USER2 = "user2@immport.test";
    private static final String USER3 = "user3@immport.test";

    @Override
    protected String getProjectName()
    {
        return "ImmuneSpace Test Data Finder DR23";
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        _userHelper.deleteUsers(false, USER1, USER2, USER3);
        AbstractContainerHelper containerHelper = new APIContainerHelper(this);
        containerHelper.deleteProject(getProjectName(), afterTest);
    }

    @BeforeClass
    public static void initTest()
    {
        if (WebTestHelper.getDatabaseType() != WebTestHelper.DatabaseType.PostgreSQL)
        {
            Assert.fail("Unsupported DB. This must be run against a Postgres DB.");
        }

        DataFinderTest init = (DataFinderTest)getCurrentTest();

        if (init.needsSetup())
            init.setupProject();

        init.createUsers();
    }

    @Override
    public boolean needsSetup()
    {
        for (String subfolder : STUDY_SUBFOLDERS)
        {
            if (!_studyHelper.doesStudyExist(getProjectName() + "/" + subfolder))
                return true;
        }
        return false;
    }

    private void setupProject()
    {
        AbstractContainerHelper containerHelper = new APIContainerHelper(this);

        containerHelper.createProject(getProjectName(), "Study");
        containerHelper.enableModule("ImmPort");
        ImmPortBeginPage
            .beginAt(this, getProjectName())
            .importArchive(immPortArchive, false);

        goToProjectHome();
        clickButton("Create Study");
        checkRadioButton(Locator.radioButtonByNameAndValue("shareDatasets", "true"));
        checkRadioButton(Locator.radioButtonByNameAndValue("shareVisits", "true"));
        selectOptionByValue(Locator.name("securityString"), "ADVANCED_WRITE");
        clickButton("Create Study");
        containerHelper.setFolderType("Dataspace");
        new PortalHelper(this).addWebPart("ImmPort Data Finder");

        containerHelper.createSubfolder(getProjectName(), "SDY_template", "Study");
        importStudyFromZip(TEMPLATE_ARCHIVE, true, true);


        for (String studyAccession : STUDY_SUBFOLDERS)
        {
            containerHelper.createSubfolder(getProjectName(), studyAccession, "Study");
            clickButton("Create Study");
            setFormElement(Locator.name("label"), studyAccession);
            selectOptionByValue(Locator.name("securityString"), "ADVANCED_WRITE");
            clickButton("Create Study");
            goToModule("ImmPort");

            new ImmPortBeginPage(this)
                    .copyDatasetsForOneStudy()
                    .copyStudyResults(studyAccession);
        }

        // Navigate to pipeline status page and show jobs in sub-folders
        beginAt("/pipeline-status/" + getProjectName() + "/showList.view?StatusFiles.containerFilterName=CurrentAndSubfolders");
        int expectedJobs =
              1                       // load ImmPort archive
            + 1                       // SDY_template folder import
            + STUDY_SUBFOLDERS.length // copy datasets jobs
        ;
        waitForPipelineJobsToComplete(expectedJobs, "immport data copy", false);

        ImmPortBeginPage.beginAt(this, getProjectName()).populateCube();
    }

    public void createUsers()
    {
        _userHelper.createUser(USER1);
        _userHelper.createUser(USER2);
        _userHelper.createUser(USER3);

        ApiPermissionsHelper permissionsHelper = new ApiPermissionsHelper(this);
        permissionsHelper.addMemberToRole(USER1, "Project Administrator", MemberType.user, getProjectName());
        permissionsHelper.addMemberToRole(USER2, "Reader", MemberType.user, getProjectName());

        // Assign users permissions to the various folders.
        log("Assign User1 Folder Administrator permissions to all of the sub folders.");
        for(String subFolder : STUDY_SUBFOLDERS)
        {
            permissionsHelper.addMemberToRole(USER1, "Folder Administrator", MemberType.user, getProjectName() + "/" + subFolder);
        }

        log("Assign User2 Read permissions to just one of the folders: " + USER2_FOLDER);
        permissionsHelper.addMemberToRole(USER2, "Reader", MemberType.user, getProjectName() + "/" + USER2_FOLDER);

    }

    @Before
    public void preTest() throws Exception
    {
        clearSharedStudyContainerFilter();
        goToProjectHome();
        DataFinderPage finder = new DataFinderPage(this);
        finder.showAllImmuneSpaceStudies();
        finder.clearSearch();
        finder.clearAllFilters();
        finder.dismissTour();
    }

    public void clearSharedStudyContainerFilter() throws IOException, CommandException
    {
        Connection connection = createDefaultConnection();
        new WhoAmICommand().execute(connection, "/"); // Populate session & csrf
        Command<?> command = new Command<>("study-shared", "sharedStudyContainerFilter")
        {
            @Override
            protected HttpUriRequest createRequest(URI uri)
            {
                return new HttpDelete(uri);
            }
        };

        command.execute(connection, getProjectName());
    }

    @Test
    public void testCounts()
    {
        DataFinderPage finder = new DataFinderPage(this);
        assertCountsSynced(finder);

        Map<Dimension, Integer> studyCounts = finder.getSummaryCounts();

        for (Map.Entry<Dimension, Integer> count : studyCounts.entrySet())
        {
            if (count.getKey().getSummaryLabel() != null)
                assertNotEquals("No " + count.getKey().getSummaryLabel(), 0, count.getValue().intValue());
        }
    }

    @Test
    public void testStudyCards()
    {
        DataFinderPage finder = new DataFinderPage(this);

        List<DataFinderPage.StudyCard> studyCards = finder.getStudyCards();

        studyCards.get(0).viewSummary();
    }

    @Test
    public void testImmuneSpaceStudySubset()
    {
        DataFinderPage finder = new DataFinderPage(this);

        assertEquals("Wrong ImmPort studies have LabKey study links", Arrays.asList(STUDY_SUBFOLDERS),
                getTexts(Locator.tagWithClass("div", "labkey-study-card").withPredicate(Locator.linkWithText("go to study"))
                        .append(Locator.tagWithClass("span", "labkey-study-card-accession")).findElements(getDriver())));

        List<DataFinderPage.StudyCard> studyCards = finder.getStudyCards();
        List<String> studies = new ArrayList<>();
        for (DataFinderPage.StudyCard studyCard : studyCards)
        {
            studies.add(studyCard.getAccession());
        }
        assertEquals("Wrong study cards for ImmuneSpace studies", Arrays.asList(STUDY_SUBFOLDERS), studies);
    }

    @Test
    public void testSelection()
    {
        DataFinderPage finder = new DataFinderPage(this);
        finder.showUnloadedImmPortStudies();

        Map<Dimension, DataFinderPage.DimensionPanel> dimensionPanels = finder.getAllDimensionPanels();

        String selectedSpecies = dimensionPanels.get(Dimension.SPECIES).selectFirstIntersectingMeasure();
        dimensionPanels.get(Dimension.GENDER).selectFirstIntersectingMeasure();

        assertCountsSynced(finder);

        dimensionPanels.get(Dimension.GENDER).clearFilters();
        assertEquals("Clearing Gender filters did not remove selection",  Collections.emptyList(), dimensionPanels.get(Dimension.GENDER).getSelectedValues());

        // re-select a gender
        String selectedGender = dimensionPanels.get(Dimension.GENDER).selectFirstIntersectingMeasure();
        dimensionPanels.get(Dimension.SPECIES).deselectMember(selectedSpecies);
        assertEquals("Clearing Species selection did not remove selection", Collections.emptyList(), dimensionPanels.get(Dimension.SPECIES).getSelectedValues());
        assertEquals("Clearing Species selection removed Gender filter", Collections.singletonList(selectedGender), dimensionPanels.get(Dimension.GENDER).getSelectedValues());

        finder.clearAllFilters();
        assertEquals("Clearing all filters didn't clear gender selection", Collections.emptyList(), dimensionPanels.get(Dimension.GENDER).getSelectedValues());

        assertCountsSynced(finder);
    }

    @Test
    public void testSelectingEmptyMeasure()
    {
        Map<Dimension, Integer> expectedCounts = new HashMap<>();
        expectedCounts.put(Dimension.STUDIES, 0);
        expectedCounts.put(Dimension.SUBJECTS, 0);

        DataFinderPage finder = new DataFinderPage(this);

        Map<Dimension, DataFinderPage.DimensionPanel> dimensionPanels = finder.getAllDimensionPanels();

        dimensionPanels.get(Dimension.GENDER).selectMember("Male");

        List<DataFinderPage.StudyCard> filteredStudyCards = finder.getStudyCards();
        assertEquals("Study cards visible after selection", 0, filteredStudyCards.size());

        Map<Dimension, Integer> filteredSummaryCounts = finder.getSummaryCounts();
        assertEquals("Wrong counts after selecting empty measure", expectedCounts, filteredSummaryCounts);

        for (DataFinderPage.DimensionPanel panel : dimensionPanels.values())
        {
            Map<String, Integer> memberCounts = panel.getMemberCounts();
            for (Map.Entry<String, Integer> memberCount : memberCounts.entrySet())
            {
                assertEquals("Wrong counts for member '" + memberCount.getKey() + "' of dimension '" + panel.getDimension() + "' after selecting empty measure", Integer.valueOf(0), memberCount.getValue());
            }
        }
    }

    @Test
    public void testSearch()
    {
        DataFinderPage finder = new DataFinderPage(this);
        finder.showUnloadedImmPortStudies();

        List<DataFinderPage.StudyCard> studyCards = finder.getStudyCards();
        String searchString = studyCards.get(0).getAccession();

        finder.studySearch(searchString);

        shortWait().until(ExpectedConditions.stalenessOf(studyCards.get(1).getComponentElement()));
        studyCards = finder.getStudyCards();

        assertEquals("Wrong number of studies after search", 1, studyCards.size());

        assertCountsSynced(finder);
    }

    @Test
    public void testStudySummaryWindow()
    {
        DataFinderPage finder = new DataFinderPage(this);

        DataFinderPage.StudyCard studyCard = finder.getStudyCards().get(0);

        StudySummaryWindow summaryWindow = studyCard.viewSummary();

        assertEquals("Study card does not match summary (Accession)", studyCard.getAccession(), summaryWindow.getAccession());
        assertEquals("Study card does not match summary (Title)", studyCard.getTitle().toUpperCase(), summaryWindow.getTitle());
        String cardPI = studyCard.getPI();
        String summaryPI = summaryWindow.getPI();
        assertTrue("Study card does not match summary (PI)", summaryPI.contains(cardPI));

        summaryWindow.closeWindow();
    }

    @Test
    public void testStudyParticipantCounts()
    {
        Map<String, Integer> finderParticipantCounts = new HashMap<>();
        Map<String, Integer> studyParticipantCounts = new HashMap<>();

        DataFinderPage finder = new DataFinderPage(this);
        for (String studyAccession : STUDY_SUBFOLDERS)
        {
            finder.studySearch(studyAccession);
            finderParticipantCounts.put(studyAccession, finder.getSummaryCounts().get(Dimension.SUBJECTS));
        }

        for (String studyAccession : STUDY_SUBFOLDERS)
        {
            clickFolder(studyAccession);
            StudyOverviewWebPart studyOverview = new StudyOverviewWebPart(getDriver());
            studyParticipantCounts.put(studyAccession, studyOverview.getParticipantCount());
        }

        assertEquals("Participant counts in study finder don't match LabKey studies", finderParticipantCounts, studyParticipantCounts);
    }

    @Test
    public void testStudyCardStudyLinks()
    {
        Set<String> foundAccessions = new HashSet<>();
        for (int i = 0; i < STUDY_SUBFOLDERS.length; i++)
        {
            DataFinderPage finder = new DataFinderPage(this);
            DataFinderPage.StudyCard studyCard = finder.getStudyCards().get(i);
            String studyAccession = studyCard.getAccession();
            foundAccessions.add(studyAccession);
            studyCard.clickGoToStudy();
            WebElement title = Locators.bodyTitle().waitForElement(shortWait());
            assertEquals("Study card " + studyAccession + " linked to wrong study", studyAccession, title.getText());
            goBack();
        }

        assertEquals("Didn't find all studies", new HashSet<>(Arrays.asList(STUDY_SUBFOLDERS)), foundAccessions);
    }

    @Test
    public void testNavigationDoesNotRemoveFinderFilter()
    {
        DataFinderPage finder = new DataFinderPage(this);
        Map<Dimension, DataFinderPage.DimensionPanel> dimensionPanels = finder.getAllDimensionPanels();
        dimensionPanels.get(Dimension.SPECIES).selectFirstIntersectingMeasure();

        Map<Dimension, List<String>> selections = finder.getSelectionValues();
        clickTab("Manage");
        clickTab("Overview");
        assertEquals("Navigation cleared study finder filter", selections, finder.getSelectionValues());
    }

    @Test
    public void testRefreshDoesNotRemoveFinderFilter()
    {
        DataFinderPage finder = new DataFinderPage(this);
        Map<Dimension, DataFinderPage.DimensionPanel> dimensionPanels = finder.getAllDimensionPanels();
        dimensionPanels.get(Dimension.SPECIES).selectFirstIntersectingMeasure();

        Map<Dimension, List<String>> selections = finder.getSelectionValues();
        refresh();
        assertEquals("'Refresh' cleared study finder filter", selections, finder.getSelectionValues());
    }

    @Test
    public void testBackDoesNotRemoveFinderFilter()
    {
        DataFinderPage finder = new DataFinderPage(this);
        Map<Dimension, DataFinderPage.DimensionPanel> dimensionPanels = finder.getAllDimensionPanels();
        dimensionPanels.get(Dimension.SPECIES).selectFirstIntersectingMeasure();

        Map<Dimension, List<String>> selections = finder.getSelectionValues();
        clickTab("Manage");
        goBack();
        assertEquals("'Back' cleared study finder filter", selections, finder.getSelectionValues());
    }

    @Test
    public void testFinderWebPartAndActionShareFilter()
    {
        DataFinderPage finder = new DataFinderPage(this);
        Map<Dimension, DataFinderPage.DimensionPanel> dimensionPanels = finder.getAllDimensionPanels();
        dimensionPanels.get(Dimension.SPECIES).selectFirstIntersectingMeasure();

        Map<Dimension, List<String>> selections = finder.getSelectionValues();
        DataFinderPage.goDirectlyToPage(this, getProjectName());
        assertEquals("WebPart study finder filter didn't get applied", selections, finder.getSelectionValues());
    }

    @Test
    public void testStickyFinderFilterOnDataset()
    {
        Map<Dimension, Integer> expectedCounts = new HashMap<>();
        expectedCounts.put(Dimension.STUDIES, 2);
        expectedCounts.put(Dimension.SUBJECTS, 345);

        DataFinderPage finder = new DataFinderPage(this);
        Map<DataFinderPage.Dimension, DataFinderPage.DimensionPanel> dimensionPanels = finder.getAllDimensionPanels();
        dimensionPanels.get(Dimension.CATEGORY).selectMember("Immune Response");

        Map<Dimension, Integer> finderSummaryCounts = finder.getSummaryCounts();
        assertEquals("Study finder counts not as expected for 'Immune Response'.", expectedCounts, finderSummaryCounts);

        int numGender = dimensionPanels.get(Dimension.GENDER).getNonEmptyValues().size();
        int numRace = dimensionPanels.get(Dimension.RACE).getNonEmptyValues().size();
        int numSpecies = dimensionPanels.get(Dimension.SPECIES).getNonEmptyValues().size();

        clickAndWait(Locator.linkContainingText("datasets"));
        clickAndWait(Locator.linkWithText("Demographics"));
        DataRegionTable demData = new DataRegionTable("Dataset", this);
        demData.showAll();
        demData.openFilterDialog("gender");
        assertEquals("Demographics data set doesn't have same number of genders as filtered study finder",
                Locator.css(".labkey-filter-dialog .labkey-link").findElements(getDriver()).size(), numGender);
        clickButton("Cancel", 0);

        demData.openFilterDialog("race");
        assertEquals("Demographics dataset doesn't have same number of races as filtered study finder",
                Locator.css(".labkey-filter-dialog .labkey-link").findElements(getDriver()).size(), numRace);
        clickButton("Cancel", 0);

        demData.openFilterDialog("species");
        assertEquals("Demographics dataset doesn't have same number of species as filtered study finder",
                Locator.css(".labkey-filter-dialog .labkey-link").findElements(getDriver()).size(), numSpecies);
        clickButton("Cancel", 0);

        assertEquals("Demographics dataset doesn't have same number of participants as filtered study finder",
                demData.getDataRowCount(), finderSummaryCounts.get(Dimension.SUBJECTS).intValue());

        clickTab("Participants");
        ParticipantListWebPart participantListWebPart = new ParticipantListWebPart(this);
        assertEquals("Participant list count doesn't match study finder", participantListWebPart.getParticipantCount(), finderSummaryCounts.get(Dimension.SUBJECTS));
    }

    @Test
    public void testStickyFinderFilterOnStudyNavigator()
    {
        DataFinderPage finder = new DataFinderPage(this);
        finder.getAllDimensionPanels().get(Dimension.CATEGORY).selectMember("Immune Response");

        List<String> assaysWithData = finder.getAllDimensionPanels().get(Dimension.ASSAY).getNonEmptyValues();
        List<String> assaysWithoutData = finder.getAllDimensionPanels().get(Dimension.ASSAY).getEmptyValues();
        Map<Dimension, Integer> finderSummaryCounts = finder.getSummaryCounts();

        OverviewPage studyOverview = new StudyOverviewWebPart(getDriver()).clickStudyNavigator();

        Map<String, Integer> studyOverviewParticipantCounts = studyOverview.getDatasetTotalParticipantCounts();

        for (String assayWithData : assaysWithData)
        {
            assayWithData = assayWithData.toLowerCase();
            for (Map.Entry<String, Integer> participantCount : studyOverviewParticipantCounts.entrySet())
            {
                if (participantCount.getKey().toLowerCase().contains(assayWithData))
                {
                    assertTrue(String.format("Assay [%s] should have data with current filter, but does not.",
                            assayWithData), participantCount.getValue() > 0);
                    break;
                }
            }
        }


        for (String assayWithoutData : assaysWithoutData)
        {
            assayWithoutData = assayWithoutData.toLowerCase();
            for (Map.Entry<String, Integer> participantCount : studyOverviewParticipantCounts.entrySet())
            {
                if (participantCount.getKey().toLowerCase().contains(assayWithoutData))
                {
                    assertEquals(String.format("Assay [%s] should be empty with current filter, but is not.",
                            assayWithoutData), 0, participantCount.getValue().intValue());
                    break;
                }
            }
        }

        // Issue 23689: study overview navigator displays incorrect participant and row counts for demographics
        assertEquals("Participant count from study finder does not match Demographics dataset participant count.",
                finderSummaryCounts.get(Dimension.SUBJECTS), studyOverviewParticipantCounts.get("Demographics"));
    }

    @Test
    public void testDatasetExport() throws IOException
    {
        DataFinderPage finder = new DataFinderPage(this);
        finder.getAllDimensionPanels().get(Dimension.CATEGORY).selectMember("Immune Response");

        Map<Dimension, Integer> studyCounts = finder.getSummaryCounts();
        assertEquals("Study count mismatch", 2, studyCounts.get(Dimension.STUDIES).intValue());

        final int fcs_analyzed_rowCount = 78;

        log("Verify dataset row counts");
        ExportStudyDatasetsPage exportDatasetsPage = finder.exportDatasets();
        // wait for all the dataset row counts to be loaded
        Ext4CmpRef ref = new Ext4CmpRef("downloadBtn", this);
        ref.waitForEnabled();

        Ext4GridRef grid = new Ext4GridRef("datasets", this);
        Map<String, Integer> datasetCounts = new HashMap<>();
        for (int i = 1; i < grid.getRowCount()+1; i++)
        {
            String name = (String)grid.getFieldValue(i, "name");
            String fieldValue = grid.getFieldValue(i, "numRows").toString();
            Long numRows;
            if(NumberUtils.isCreatable(fieldValue))
                numRows = NumberUtils.createLong(fieldValue);
            else
                numRows = 0L;

            datasetCounts.put(name, numRows.intValue());
        }

        Assert.assertEquals(Integer.valueOf(345), datasetCounts.get("demographics"));
        Assert.assertEquals(Integer.valueOf(960), datasetCounts.get("elispot"));
        Assert.assertEquals(Integer.valueOf(fcs_analyzed_rowCount), datasetCounts.get("fcs_analyzed_result"));


        log("Download datasets zip");
        final File exportedFile = exportDatasetsPage.download();
        assertTrue("Expected file name to end in .tables.zip: " + exportedFile.getAbsolutePath(), exportedFile.getName().endsWith(".tables.zip"));
        assertTrue("Exported file does not exist: " + exportedFile.getAbsolutePath(), exportedFile.exists());

        waitFor(() -> exportedFile.length() > 0, "Exported file is empty: " + exportedFile.getAbsolutePath(), WAIT_FOR_JAVASCRIPT * 10);

        log("Validate contents");
        try (FileSystem fs = FileSystems.newFileSystem(exportedFile.toPath(), (ClassLoader) null)) // 'null' is ambiguous in Java 13+
        {
            // Extract a file
            List<String> lines = Files.readAllLines(fs.getPath("fcs_analyzed_result.tsv"), StandardCharsets.UTF_8);
            Assert.assertEquals(
                    "Expected " + fcs_analyzed_rowCount + " rows and header (dumping first two lines):\n" +
                            StringUtils.join(lines.subList(0, 2), "\n"),
                    fcs_analyzed_rowCount + 1, lines.size());
        }
    }

    @Test
    public void testDisabledLoadAndSave()
    {
        DataFinderPage finder = new DataFinderPage(this);
        finder.showUnloadedImmPortStudies();
        Assert.assertFalse("Save menu should not be enabled for unloaded studies", finder.saveMenu().isEnabled());
        Assert.assertFalse("Load menu should not be enabled for unloaded studies", finder.loadMenu().isEnabled());
    }

    @Test
    @Ignore ("Group menu interactions are unreliable and this data finder is no longer in use.")
    public void testGroupSaveAndLoad()
    {
        DataFinderPage finder = new DataFinderPage(this);
        assertEquals("Group label not as expected", "Unsaved Group", finder.getGroupLabel());

        Map<Dimension, DataFinderPage.DimensionPanel> dimensionPanels = finder.getAllDimensionPanels();
        Map<Dimension, List<String>> selections = new HashMap<>();
        selections.put(Dimension.CONDITION, Collections.singletonList(dimensionPanels.get(Dimension.CONDITION).selectFirstIntersectingMeasure()));
        selections.put(Dimension.CATEGORY, Collections.singletonList(dimensionPanels.get(Dimension.CATEGORY).selectFirstIntersectingMeasure()));
        Map<Dimension, Integer> summaryCounts = finder.getSummaryCounts();

        // click on "Save" menu and assert "Save" is not active then assert "Save as" is active
        DataFinderPage.SaveMenu saveMenu = finder.saveMenu();
        Assert.assertEquals("Inactive options", Arrays.asList("Save"), saveMenu.getInactiveOptions());
        Assert.assertEquals("Active options", Arrays.asList("Save As"), saveMenu.getActiveOptions());

        String filterName = "testGroupSaveAndLoad" + System.currentTimeMillis();
        saveMenu.saveAs();
        // assert that popup has the proper number of Selected Studies and Subjects
        DataRegionTable subjectData = new DataRegionTable("demoDataRegion", this);
        Assert.assertEquals("Subject counts on save group window differ from those on data finder", summaryCounts.get(Dimension.SUBJECTS).intValue(), subjectData.getDataRowCount());
        finder.saveGroup(filterName);

        assertEquals("Group label not as expected", "Saved group: " + filterName, finder.getGroupLabel());

        finder.clearAllFilters();
        //load group with test name
        DataFinderPage.LoadMenu loadMenu = finder.loadMenu();
        loadMenu.loadGroup(filterName);
        assertEquals("Group label not as expected", "Saved group: " + filterName, finder.getGroupLabel());

        // assert the selected items are the same and the counts are the same as before.
        assertEquals("Summary counts not as expected after load", summaryCounts, finder.getSummaryCounts());
        assertEquals("Selected items not as expected after load", selections, finder.getSelectionValues());
        // assert that "Save" is now active in the menu
        saveMenu = finder.saveMenu();
        Assert.assertEquals("Active options after saving group", Arrays.asList("Save", "Save As"), saveMenu.getActiveOptions());

        // Choose another dimension and save the summary counts
        log("selecting an Assay filter");
        selections.put(Dimension.ASSAY, Collections.singletonList(dimensionPanels.get(Dimension.ASSAY).selectFirstIntersectingMeasure()));
        summaryCounts = finder.getSummaryCounts();
        log("Selections is now " + selections);
        assertEquals("Selected items not as expected after assay selection", selections, finder.getSelectionValues());

        // Save the filter
        finder.saveMenu().save();

        finder.clearAllFilters();

        // Load the filter
        finder.loadMenu().loadGroup(filterName);

        // assert that the selections are as expected.
        assertEquals("Summary counts not as expected after load", summaryCounts, finder.getSummaryCounts());
        assertEquals("Selected items not as expected after load", selections, finder.getSelectionValues());

        // manage group and delete the group that was created
        ManageParticipantGroupsPage managePage = finder.manageMenu().manageGroups();
        waitForText("Manage Participant Groups");
        managePage.selectGroup(filterName);
        Assert.assertTrue("Delete should be enabled for group created through data finder", managePage.isDeleteEnabled());
        Assert.assertFalse("Edit should not be enabled for group created through data finder", managePage.isEditEnabled());
        managePage.deleteGroup(filterName);
    }

    @Test
    public void testSend() throws MalformedURLException
    {
        String filter, groupName, returnedString, messageSubject, previewURL;
        DataFinderPage finder;
        Map<Dimension, String> selectedFacets = new HashMap<>();
        List<DataFinderPage.DimensionMember> filters;
        List<String> recipients;
        Map<Dimension, DataFinderPage.DimensionPanel> dimensionPanels;
        SendParticipantPage sendPage;

        goToProjectHome();

        filter = "Immune Response";
        selectedFacets.put(Dimension.CATEGORY, filter);
        groupName = "group" + System.currentTimeMillis();
        createAndSaveStudyGroup(groupName, selectedFacets);

        recipients = new ArrayList<>();
        recipients.add(USER1);
        recipients.add(USER2);
        returnedString = sendStudyGroup(recipients, groupName, false);

        String[] returnedParts = returnedString.split(";");
        messageSubject = returnedParts[0];
        previewURL = returnedParts[1].replace(" ", "%20");

        log("Go get the url from the email message.");
        String url = getSharedLinks(messageSubject);
        assertEquals("URL in email message not same as preview URL.", previewURL, url);
        URL sharedUrl = new URL(url);

        log("Impersonate one of the recipients and validate that the link works as expected.");
        impersonate(USER1);
        goToURL(sharedUrl, 10000);

        log("Get the new finder page");
        finder = new DataFinderPage(this);

        log("Validate that the facets are as expected.");
        filters = finder.getSelectedMembers();
        assertEquals("Count of filters is not as expected.", 1, filters.size());
        assertEquals("Filter name not as expected.", filter, filters.get(0).getName());

        log("Validate card count."); // Not going to look at cards because filtering is tested elsewhere.
        assertEquals("Count of cards not as expected.", 2, finder.getStudyCards().size());

        stopImpersonating();

        log("Impersonate a user who has limited permissions and validate that they only see what they should.");
        impersonate(USER2);
        goToURL(sharedUrl, 10000);

        log("Look at the loaded ImmuneSpace studies.");
        getSelectedOptionText(DataFinderPage.Locators.studySubsetChooser);
        selectOptionByText(DataFinderPage.Locators.studySubsetChooser, "ImmuneSpace studies");

        log("Get the new finder page");
        finder = new DataFinderPage(this);

        log("Validate that the facets are as expected.");
        filters = finder.getSelectedMembers();
        assertEquals("Count of filters is not as expected.", 1, filters.size());
        assertEquals("Filter name not as expected.", filter, filters.get(0).getName());

        log("Validate card count.");
        assertEquals("Count of cards not as expected.", 1, finder.getStudyCards().size());

        log("Validate that the card shown to User2 is the only one they are allowed to see.");
        Assert.assertEquals("Study card shown was not limited to the one User2 can see", USER2_FOLDER, finder.getStudyCards().get(0).getAccession());

        log("Validate this user can save the group.");
        finder.saveMenu().saveAs();
        String defaultGroupName = finder.getGroupNameFromForm();

        Assert.assertEquals("Default group name not as expected.", groupName, defaultGroupName);

        finder.saveGroup();

        stopImpersonating();

        log("Create a new filter and try to mail it to someone who doesn't have permissions.");

        goToProjectHome();
        finder = new DataFinderPage(this);

        log("Clear any filters that are currently applied and create a new filter.");
        finder.clearAllFilters();

        filter = "Immune Response";
        selectedFacets.put(Dimension.CATEGORY, filter);
        groupName = "group" + System.currentTimeMillis();
        log("Group name: " + groupName);
        createAndSaveStudyGroup(groupName, selectedFacets);

        recipients = new ArrayList<>();
        recipients.add(USER1);
        recipients.add(USER3);
        String errorMessage = sendStudyGroup(recipients, groupName, true);
        Assert.assertEquals("Error message not as expected.", "User does not have permissions to this folder: " + USER3, errorMessage);

        log("Error message was as expected. Cancel out of this form.");
        clickButton("Cancel");

        enableEmailRecorder(); // Clear out previous messages
        goToProjectHome();

        sleep(1000); // Yes I know this is ugly, but running out of time and need to move on. Works around an issue where DataFinder page isn't completely loaded before clearAllFilters is called.

        finder = new DataFinderPage(this);

        log("Clear any filters that are currently applied and create a new filter looking at Condition\\Influenza.");
        finder.clearAllFilters();

        filter = "Influenza";
        selectedFacets.clear();
        selectedFacets.put(Dimension.CONDITION, filter);
        dimensionPanels = finder.getAllDimensionPanels();
        for(Map.Entry<Dimension, String> entry : selectedFacets.entrySet())
        {
            log("For '" + entry.getKey().toString() + "' select '" + entry.getValue() + "'");
            dimensionPanels.get(entry.getKey()).selectMember(entry.getValue());
        }

        log("Try to send the filter without saving first.");
        Window window = finder.clickSendWithUnsavedGroup();

        assertEquals("Text in Message Box not as expected.", "You must save a group before you can send a copy.", window.getBody());

        log("Choose 'Save' to dismiss the dialog.");
        clickButton("Save", "Save and Send");

        log("Now save and send the group.");
        groupName = "group" + System.currentTimeMillis();
        log("Group name: " + groupName);
        finder.saveAndSendGroup(groupName);

        recipients = new ArrayList<>();
        recipients.add(USER1);

        log("Fill out the 'Send Participant Group' form.");
        sendPage = new SendParticipantPage(getDriver());
        sendPage.setRecipients(recipients);
        String subject = sendPage.getMessageSubject() + " named: " + groupName;
        sendPage.setMessageSubject(subject);
        sendPage.clickSubmit();

        log("Validate that the send did not error.");
        validateSendDidNotError();

        EmailRecordTable emailRecordTable = goToEmailRecord();
        assertEquals("Email messages", 1, emailRecordTable.getEmailCount());
        EmailRecordTable.EmailMessage email = emailRecordTable.getEmailAtTableIndex(3); // EmailRecordTable row indexing is messed up
        assertEquals("Email recipient", Arrays.asList(USER1), Arrays.asList(email.getTo()));
        assertEquals("Email subject", subject, email.getSubject());
    }

    // This class is used to create an object that has an associated groupName with it's shared url.
    // This is used in the testNotifications to validate that visiting a page/url will make the notification go away.
    class studyGroupInfo
    {
        public String groupName;
        public String sharedURL;

        studyGroupInfo(String groupname, String url)
        {
            groupName = groupname;
            sharedURL = url;
        }
    }

    @Test
    public void testNotifications()
    {
        UserNotificationsPanel notificationsPanel;
        UserNotificationsPage notificationsPage;
        int expectedNotifications;
        int notificationCount;
        ArrayList<studyGroupInfo> groupsSent;

        // These are indexes to messages in groupsSent. The index indicates the test it is used for.
        final int TOTAL_NOTIFICATIONS_TO_SEND = 8;
        final int SENT_CLICK_IN_PANEL = 0;
        final int SENT_VISIT_URL = 1;
        final int SENT_MARK_AS_READ = 2;
        final int SENT_CLICK_VIEW_LINK = 3;
        final int SENT_CLICK_MARK_AS_READ = 4;
        final int SENT_CLICK_DISMISS_LINK = 5;

        log("Turn on the experimental feature.");
        Connection cn = createDefaultConnection();
        ExperimentalFeaturesHelper.setExperimentalFeature(cn, "experimental-notificationmenu", true);

        goToProjectHome();

        log("First thing, clear out any existing notifications.");
        impersonate(USER1);
        UserNotificationsPage.beginAt(this).deleteAll();
        stopImpersonating();

        log("Create several different study groups that will cause notifications to be sent.");
        goToProjectHome();
        // I create several different groups/notifications because the last test run validates the "Clear All" functionality.
        groupsSent = createAllStudyGroupsForNotificationTest(TOTAL_NOTIFICATIONS_TO_SEND);
        expectedNotifications = TOTAL_NOTIFICATIONS_TO_SEND;
        assertEquals("Failed to create expected number of initial notifications", expectedNotifications, groupsSent.size());

        log("Go home so we don't accidentally visit the page and cause a notification to disappear");
        goToHome();

        log("Impersonate the recipient and validate that they see notifications.");
        impersonate(USER1);

        notificationCount = UserNotificationsPanel.getInboxCount(this);
        log("Message count on inbox: " + notificationCount);
        Assert.assertEquals("Notification count for the inbox is not as expected.", expectedNotifications, notificationCount);

        notificationsPanel = UserNotificationsPanel.clickInbox(this);
        notificationCount = notificationsPanel.getNotificationCount();
        log("Message count in panel: " + notificationCount);
        Assert.assertEquals("Count of notifications in the panel is not as expected.", expectedNotifications, notificationCount);

        log("Find the notification that is for the study group: " + groupsSent.get(SENT_CLICK_IN_PANEL).groupName);

        final NotificationPanelItem notificationPanelItem = notificationsPanel.findNotificationInList(groupsSent.get(SENT_CLICK_IN_PANEL).groupName, "Study");

        assertNotNull("Did not find a notice with the group name '" + groupsSent.get(SENT_CLICK_IN_PANEL).groupName + "' in it.", notificationPanelItem);
        assertTrue("Item in panel did not have the expected created by.", notificationPanelItem.getCreatedBy().contains("Today -"));
        assertTrue("Item in panel did not have the expected body.", notificationPanelItem.getBody().contains("A participant group has been sent: " + groupsSent.get(SENT_CLICK_IN_PANEL).groupName));
        assertEquals("Icon for item in panel not as expected.", "fa-users", notificationPanelItem.getIconType());

        log("Click on the item in the list and validate we go to the correct page.");
        doAndWaitForPageToLoad(notificationPanelItem::click);
        expectedNotifications--;

        String currentUrl = getDriver().getCurrentUrl();
        assertTrue("URL for page is not as expected. We should be at dataFinder.view. Actually at: " + currentUrl, currentUrl.contains("dataFinder.view"));

        log("Validate that the notification count has gone down.");
        notificationCount = UserNotificationsPanel.getInboxCount(this);
        assertEquals("Count after clicking the item in the panel was not as expected.", expectedNotifications, notificationCount);

        log("Visit the url link for a different group and confirm that this causes the notification count to go down.");
        beginAt(groupsSent.get(SENT_VISIT_URL).sharedURL);
        expectedNotifications--;

        notificationCount = UserNotificationsPanel.getInboxCount(this);
        assertEquals("Count was not as expected after visiting notification target directly.",  expectedNotifications, notificationCount);

        log("Go home, kind of resetting.");
        goToHome();

        notificationsPanel = UserNotificationsPanel.clickInbox(this);

        NotificationPanelItem notificationPanelItem2 = notificationsPanel.findNotificationInList(groupsSent.get(SENT_MARK_AS_READ).groupName, "Study");

        assertNotNull("Did not find a notice with the group name '" + groupsSent.get(SENT_MARK_AS_READ).groupName + "' in it.", notificationPanelItem2);

        log("Mark the notification as being read.");
        notificationPanelItem2.markAsRead();
        expectedNotifications--;

        notificationCount = notificationsPanel.getNotificationCount();
        assertEquals("Count of notifications in the panel after marking one as read.", expectedNotifications, notificationCount);

        log("Go view all notifications.");
        notificationsPage = notificationsPanel.viewAll();

        log("Find the notification '" + groupsSent.get(SENT_MARK_AS_READ).groupName + "' and confirm it is read.");
        UserNotificationsPage.NotificationItem pageNotificationItem = notificationsPage.findNotificationInPage(groupsSent.get(SENT_MARK_AS_READ).groupName, UserNotificationsPage.NotificationTypes.STUDY);
        assertNotNull("Did not find the 'Read' notification in the list of all notifications.", pageNotificationItem);
        assertTrue("The 'Read' notification is not marked as read in the list.", pageNotificationItem.isRead());
        assertEquals("Text for the 'Read On:'.", "Read On: Today", pageNotificationItem.getReadOnText());

        log("Find notification for group '" + groupsSent.get(SENT_CLICK_VIEW_LINK).groupName + "' that was sent and validate that the 'view' links takes you to the expected page.");
        final UserNotificationsPage.NotificationItem pageNotificationItem2 = notificationsPage.findNotificationInPage(groupsSent.get(SENT_CLICK_VIEW_LINK).groupName, UserNotificationsPage.NotificationTypes.STUDY);
        assertNotNull("Did not find the 'going to view' notification in the list of all notifications.", pageNotificationItem2);
        log("Click the 'view' link.");
        doAndWaitForPageToLoad(pageNotificationItem2::clickView);
        assertTrue("Wrong URL for page. We should be at dataFinder.view.", getURL().getPath().contains("dataFinder.view"));

        log("Go back to the notifications page and click the 'Mark As Read' link.");
        notificationsPanel = UserNotificationsPanel.clickInbox(this);
        notificationsPage = notificationsPanel.viewAll();

        log("Find notification that was sent and validate that the 'Mark As Read' links works as expected.");
        pageNotificationItem = notificationsPage.findNotificationInPage(groupsSent.get(SENT_CLICK_MARK_AS_READ).groupName, UserNotificationsPage.NotificationTypes.STUDY);
        assertNotNull("Did not find the notification for group '" + groupsSent.get(SENT_CLICK_MARK_AS_READ).groupName + "' in the list of all notifications.", pageNotificationItem);
        log("Click 'Mark As Read'.");
        pageNotificationItem.clickMarkAsRead();
        assertTrue("The notification is not marked as read in the list.", pageNotificationItem.isRead());
        assertEquals("Text for the 'Read On:'.", "Read On: Today", pageNotificationItem.getReadOnText());

        log("Find notification '" + groupsSent.get(SENT_CLICK_DISMISS_LINK).groupName + "' that was sent and validate that the 'Dismiss' links works as expected.");
        // Get a new instance of the notifications page.
        notificationsPage = new UserNotificationsPage(getDriver());
        pageNotificationItem = notificationsPage.findNotificationInPage(groupsSent.get(SENT_CLICK_DISMISS_LINK).groupName, UserNotificationsPage.NotificationTypes.STUDY);
        assertNotNull("Did not find the notification for group '" + groupsSent.get(SENT_CLICK_DISMISS_LINK).groupName + "' in the list of all notifications.", pageNotificationItem);
        log("Click 'Delete'.");
        pageNotificationItem.clickDelete();
        sleep(500);
        pageNotificationItem = notificationsPage.findNotificationInPage(groupsSent.get(SENT_CLICK_DISMISS_LINK).groupName, UserNotificationsPage.NotificationTypes.STUDY);
        assertNull("Found the notification for group '" + groupsSent.get(SENT_CLICK_DISMISS_LINK).groupName + "' in the list of all notifications. It should not be there.", pageNotificationItem);

        log("Finally, from the panel click the 'Clear All' link and validate all messages are now marked as read.");
        notificationsPanel = UserNotificationsPanel.clickInbox(this);
        notificationsPanel.clearAll();

        assertEquals("Notification count for inbox.", 0, UserNotificationsPanel.getInboxCount(this));
        assertEquals("Count of notifications in the panel.", 0, notificationsPanel.getNotificationCount());

        log("The text 'No new notifications' should be shown in the panel.");
        assertTrue("Text 'No new notifications' was not present in notification panel.", notificationsPanel.getComponentElement().getText().contains("No new notifications"));

        log("The 'View All' link should still be valid.");
        notificationsPage = notificationsPanel.viewAll();

        log("Validate that all items on the page are marked as read.");
        List<UserNotificationsPage.NotificationItem> pageNotificationItems = notificationsPage.getNotificationsOfType(UserNotificationsPage.NotificationTypes.STUDY);
        for(UserNotificationsPage.NotificationItem pageItem : pageNotificationItems)
        {
            assertTrue("The notification '" + pageItem.getHeaderText() + "' is not marked as read in the list.", pageItem.isRead());
        }

        log("Get the new unread count for this user (should be 0) and stop impersonating.");
        assertEquals("Should be no more unread notifications", 0, UserNotificationsPanel.getInboxCount(this));

        stopImpersonating();

        log("Create a few more study groups and then validate that 'Mark All As Read' and 'Delete All' works as expected from the notification page.");
        goToProjectHome();
        groupsSent = createAllStudyGroupsForNotificationTest(2);
        expectedNotifications = 2;
        assertEquals("Did not create correct number of groups", expectedNotifications, groupsSent.size());

        log("Go home again so we don't accidentally visit the page and cause a notification to disappear");
        goToHome();

        log("Again impersonate the recipient and validate that they see notifications.");
        impersonate(USER1);

        notificationCount = UserNotificationsPanel.getInboxCount(this);
        log("Message count on inbox: " + notificationCount);
        Assert.assertEquals("Notification count for the inbox is not as expected.", expectedNotifications, notificationCount);

        notificationsPanel = UserNotificationsPanel.clickInbox(this);
        log("Message count in panel: " + notificationsPanel.getNotificationCount());
        Assert.assertEquals("Count of notifications in the panel is not as expected.", expectedNotifications, notificationsPanel.getNotificationCount());

        log("Go view all notifications.");
        notificationsPage = notificationsPanel.viewAll();

        log("Validate that the 'Mark All As read' and 'Delete All' links are presnet at the top of the page.");
        assertElementVisible(UserNotificationsPage.Locators.markAllAsRead);
        assertElementVisible(UserNotificationsPage.Locators.deleteAll);

        log("Validate that the new notification is unread.");
        UserNotificationsPage.NotificationItem pageNotificationItemLastOne = notificationsPage.findNotificationInPage(groupsSent.get(0).groupName, UserNotificationsPage.NotificationTypes.STUDY);
        assertFalse("The new notification with group id: " + groupsSent.get(0).groupName + " is marked as read, it should not be.", pageNotificationItemLastOne.isRead());

        log("Validate that the 'Mark All As Read' link does what it says it will do.");
        click(UserNotificationsPage.Locators.markAllAsRead);
        sleep(500);

        log("Again loop through the list of notifications and validate that they are all marked as read.");
        pageNotificationItems = notificationsPage.getNotificationsOfType(UserNotificationsPage.NotificationTypes.STUDY);
        for(UserNotificationsPage.NotificationItem pageItem : pageNotificationItems)
        {
            assertTrue("The notification '" + pageItem.getHeaderText() + "' is not marked as read in the list.", pageItem.isRead());
        }

        log("Validate that the 'Delete All' link does what it says it will do.");
        click(UserNotificationsPage.Locators.deleteAll);
        sleep(500);
        assertEquals("Count of notifications in page not as expected.", 0, notificationsPage.getNotificationCount());

        stopImpersonating();

        log("We are done, turn off the experimental feature and go home");
        ExperimentalFeaturesHelper.setExperimentalFeature(cn, "experimental-notificationmenu", false);
    }

    private ArrayList<studyGroupInfo> createAllStudyGroupsForNotificationTest(int noticeCount)
    {
        String filter, studyGroupName, previewURL;
        Map<Dimension, String> selectedFacets = new HashMap<>();
        List<String> recipients;
        ArrayList<studyGroupInfo> groupsSent = new ArrayList<>();

        recipients = new ArrayList<>();
        recipients.add(USER1);

        // Create one study group to start.
        filter = "Mus musculus";
        selectedFacets.put(Dimension.SPECIES, filter);
        sleep(500); // Need to slow down, the UI stumbles and tries to create a group with the same name.
        studyGroupName = "group" + System.currentTimeMillis();

        createStudyGroup(selectedFacets);
        saveStudyGroup(studyGroupName);
        previewURL = sendStudyGroup(recipients, studyGroupName, false)
                .split(";")[1].replace(" ", "%20");

        groupsSent.add(new studyGroupInfo(studyGroupName, previewURL));

        // Using the same filter just change the group name and send it.
        for(int i=2; i <= noticeCount; i++)
        {
            sleep(500); // Need to slow down, the UI stumbles and tries to create a group with the same name.
            studyGroupName = "group" + System.currentTimeMillis();
            saveStudyGroup(studyGroupName);
            previewURL = sendStudyGroup(recipients, studyGroupName, false)
                    .split(";")[1].replace(" ", "%20");

            groupsSent.add(new studyGroupInfo(studyGroupName, previewURL));
        }

        return groupsSent;
    }

    private void createAndSaveStudyGroup(String groupName, Map<Dimension, String> facets)
    {
        createStudyGroup(facets);
        saveStudyGroup(groupName);
    }

    private void createStudyGroup(Map<Dimension, String> facets)
    {
        DataFinderPage finder;
        Map<Dimension, DataFinderPage.DimensionPanel> dimensionPanels;

        finder = new DataFinderPage(this);

        log("Clear any filters that are currently applied.");
        sleep(500);
        finder.clearAllFilters();
        sleep(500);
        dimensionPanels = finder.getAllDimensionPanels();

        log("Apply the filters.");
        for(Map.Entry<Dimension, String> entry : facets.entrySet())
        {
            log("For '" + entry.getKey().toString() + "' select '" + entry.getValue() + "'");
            dimensionPanels.get(entry.getKey()).selectMember(entry.getValue());
        }
    }

    private void saveStudyGroup(String groupName)
    {
        DataFinderPage finder = new DataFinderPage(this);

        log("Save the group and name it: " + groupName);
        finder.saveMenu().saveAs();
        finder.saveGroup(groupName);
    }

    private String sendStudyGroup(List<String> recipients, String groupName, boolean shouldError)
    {
        String returnString, sharedLink, msgSubject;
        DataFinderPage finder;
        SendParticipantPage sendPage;

        log("Send the link to the list of recipients.");
        finder = new DataFinderPage(this);
        sendPage = finder.clickSend();
        sendPage.setRecipients(recipients);
        msgSubject = sendPage.getMessageSubject() + " named: " + groupName;
        sendPage.setMessageSubject(msgSubject);
        sharedLink = sendPage.getMessageLink();
        sendPage.clickSubmit();

        if(shouldError)
        {
            log("An error was expected. Get the error message shown and return it.");
            WebElement error = Locators.labkeyError.findElementOrNull(getDriver());
            returnString = error != null ? error.getText() : null;
        }
        else
        {
            // Might be nicer if in the future this returns an object with values set and not a string to be split.
            returnString = msgSubject + ";" + sharedLink;
            validateSendDidNotError();
        }

        return returnString;
    }

    private String getSharedLinks(String msgSubject)
    {
        EmailRecordTable emailRecordTable = goToEmailRecord();

        log("Find the message based on subject and user.");
        emailRecordTable.clickSubject(msgSubject);
        return getAttribute(Locator.css("a[href*='dataFinder.view?groupId=']"), "href");
    }

    private void validateSendDidNotError()
    {
        List<String> errors = getTexts(Locators.labkeyError.findElements(getDriver()));
        assertTrue("Unexpected error(s) when sending group: [" + String.join(",", errors) + "]", errors.isEmpty());

        // If send worked you should be on another page now.
        if(getURL().getPath().contains("sendParticipantGroup.view"))
        {
            Assert.fail("Did not navigate away from 'study-sendParticipantGroup.view' after clicking send (should have). And no error message was shown on the page (and there should have been).");
        }
    }

    @Test
    public void testExportDataWithFiles() throws Exception
    {
        List<String> controlFileList = Arrays.asList(
                "Fig7_Compensation Controls_Blue E 530,2f,30 Stained Control.297194.fcs",
                "Fig7_Compensation Controls_Violet B 450,2f,50 Stained Control.297200.fcs",
                "Fig7_Compensation Controls_Unstained Control.297193.fcs",
                "Fig7_Compensation Controls_Red C 670,2f,14 Stained Control.297198.fcs",
                "Fig7_Compensation Controls_Red A 780,2f,60 Stained Control.297199.fcs",
                "Fig7_Compensation Controls_Violet A 550,5c,50 Stained Control.297201.fcs",
                "Fig7_Compensation Controls_Blue A 780,2f,60 Stained Control.297197.fcs",
                "Fig7_Compensation Controls_Blue B 670LP Stained Control.297196.fcs",
                "Fig7_Compensation Controls_Blue D 585,2f,42 Stained Control.297195.fcs",
                "Compensation Controls_Blue A 780,2f,60 Stained Control.297084.fcs",
                "Compensation Controls_Unstained Control.297089.fcs",
                "Compensation Controls_Violet A 550,5c,50 Stained Control.297088.fcs",
                "Compensation Controls_Blue D 585,2f,42 Stained Control.297082.fcs",
                "Compensation Controls_Red C 670,2f,14 Stained Control.297085.fcs",
                "Compensation Controls_Blue B 670LP Stained Control.297083.fcs",
                "Compensation Controls_Red A 780,2f,60 Stained Control.297086.fcs",
                "Compensation Controls_Violet B 450,2f,50 Stained Control.297087.fcs",
                "Compensation Controls_Blue E 530,2f,30 Stained Control.297081.fcs");

        log("Go to study: " + STUDY_SUBFOLDERS[0]);
        clickFolder(STUDY_SUBFOLDERS[0]);

        goToModule("FileContent");

        // Would be nice to be able to use a pipeline to populate the files.
        // However the export with folder feature was explicitly spec'd to look at @files and does not see the @pipeline.

        log("Check to see if a rawdata folder is there, if not create it.");
        if(!_fileBrowserHelper.fileIsPresent("rawdata")) {
            log("No rawdata folder is there, going to create it.");
            _fileBrowserHelper.createFolder("rawdata");
        }

        _fileBrowserHelper.selectFileBrowserItem("rawdata/");

        boolean createdFolder = false;
        log("Check to see if a flow_cytometry folder is there, if not create it.");
        if(!_fileBrowserHelper.fileIsPresent("flow_cytometry")) {
            log("No flow_cytometry folder is there, going to create it.");
            _fileBrowserHelper.createFolder("flow_cytometry");
            createdFolder = true;
        }

        _fileBrowserHelper.selectFileBrowserItem("rawdata/flow_cytometry/");

        if(createdFolder)
        {
            log("Had to create the folder so have to upload the control files.");
            for (String cntrlFileName : controlFileList)
            {
                File fl = TestFileUtils.getSampleData("HIPC/downloadFiles/rawdata/flow_cytometry/" + cntrlFileName);
                _fileBrowserHelper.uploadFile(fl);
            }
        }
        else
        {
            log("Didn't create any folders so going to assume the file content is there and as expected.");
        }

        goToProjectHome();
        log("Limit export to the one sub folder.");
        DataFinderPage dataFinderPage = new DataFinderPage(this);
        dataFinderPage.selectStudySubset("ImmuneSpace studies");
        dataFinderPage.studySearch(STUDY_SUBFOLDERS[0]);

        log("Wait until the other study cards are gone.");
        waitForElementToDisappear(Locator.xpath("//div[contains(@class, 'labkey-study-card')]//span[text()='" + STUDY_SUBFOLDERS[1] + "']"));

        log("Export.");
        ExportStudyDatasetsPage exportPage = dataFinderPage.exportDatasets();

        log("Limit the export to only the fcs control files.");
        String controlFilesDatasetId = "5019";
        exportPage.selectDatasetFile(controlFilesDatasetId);
        Locator.id("summaryData").childTag("div").withText("Files number: 45").waitForElement(getDriver(), WAIT_FOR_JAVASCRIPT);

        File download = exportPage.download();

        log("Look at zip file and make sure the expected files are there.");
        Set<String> filesInZip = new HashSet<>();
        try (
                InputStream is = new FileInputStream(download);
                ZipInputStream zip = new ZipInputStream(is))
        {
            while (zip.available() != 0)
            {
                ZipEntry entry = zip.getNextEntry();
                if (entry != null)
                    filesInZip.add(entry.getName());
            }
        }

        String folder = "fcs_control_files/";
        assertTrue("Did not find any individual control files in zip export: " + download.getName() + "\nFound: \n" + String.join("\n", filesInZip),
                filesInZip.stream().anyMatch(file -> file.startsWith(folder)));

        for (String controlFile : controlFileList)
        {
            assertTrue("Did not find file '" + controlFile + "' in zip export: " + download.getName(),
                    filesInZip.contains(folder + controlFile));
        }
    }

    @LogMethod(quiet = true)
    private void assertCountsSynced(DataFinderPage finder)
    {
        List<DataFinderPage.StudyCard> studyCards = finder.getStudyCards();
        Map<Dimension, Integer> studyCounts = finder.getSummaryCounts();

        assertEquals("Study count mismatch", studyCards.size(), studyCounts.get(Dimension.STUDIES).intValue());
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList("ImmPort");
    }
}
