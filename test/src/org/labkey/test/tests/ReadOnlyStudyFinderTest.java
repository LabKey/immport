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

package org.labkey.test.tests;

import org.apache.commons.collections15.Bag;
import org.apache.commons.collections15.bag.HashBag;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.InDevelopment;
import org.labkey.test.components.immport.StudySummaryWindow;
import org.labkey.test.pages.immport.ImmPortBeginPage;
import org.labkey.test.pages.immport.StudyFinderPage;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.PostgresOnlyTest;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.junit.Assert.*;

/**
 * Test assumes test data is loaded manually
 * Before running this test, you should manually load an ImmPort archive and ensure that {@link #getProjectName()}
 * links to the project containing that archive
 * TODO: Set-up and tear-down project once smaller sample data is available
 */
@Category({InDevelopment.class})
public class ReadOnlyStudyFinderTest extends BaseWebDriverTest implements PostgresOnlyTest
{
    private static File immPortArchive = new File("C:\\labkey\\HIPC Test Data\\ALLSTUDIES-DR11_MySQL.zip"); // Change to actual path to archive

    public ReadOnlyStudyFinderTest()
    {
        super();
        _containerHelper = null; // Prevent accidental project deletion
    }

    @Override
    protected String getProjectName()
    {
        return "ImmPortTest Project";
    }

    @Override
    protected BrowserType bestBrowser()
    {
        return BrowserType.CHROME;
    }

    @BeforeClass
    public static void initTest()
    {
        ReadOnlyStudyFinderTest init = (ReadOnlyStudyFinderTest)getCurrentTest();

        //init.setupProject();
    }

    private void setupProject()
    {
        _containerHelper.createProject(getProjectName(), null);
        _containerHelper.enableModule("ImmPort");

        clickTab("ImmPort");
        ImmPortBeginPage beginPage = new ImmPortBeginPage(this);
        beginPage.importArchive(immPortArchive.getAbsolutePath(), false);
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        // do nothing
    }

    @Test
    public void testCounts()
    {
        goToProjectHome();
        clickTab("ImmPort");
        clickAndWait(Locator.linkWithText("Study Finder"));

        StudyFinderPage studyFinder = new StudyFinderPage(this);

        assertCountsSynced(studyFinder);

        Map<String, Integer> studyCounts = studyFinder.getSummaryCounts();

        for (Map.Entry count : studyCounts.entrySet())
        {
            if (!count.getKey().equals("participants"))
                assertNotEquals("No " + count.getKey(), 0, count.getValue());
        }
    }

    @Test
    public void testStudyCards()
    {
        StudyFinderPage studyFinder = StudyFinderPage.goDirectlyToPage(this, getProjectName());
        studyFinder.dismissTour();

        List<StudyFinderPage.StudyCard> studyCards = studyFinder.getStudyCards();

        studyCards.get(0).viewSummary();
    }

    @Test
    public void testSelection()
    {
        StudyFinderPage studyFinder = StudyFinderPage.goDirectlyToPage(this, getProjectName());
        studyFinder.dismissTour();

        Map<StudyFinderPage.Dimension, StudyFinderPage.DimensionPanel> dimensionPanels = studyFinder.getDimensionPanels();

        dimensionPanels.get(StudyFinderPage.Dimension.SPECIES).selectFirstIntersectingMeasure();
        List<String> selectedGenders = new ArrayList<>();
        selectedGenders.add(dimensionPanels.get(StudyFinderPage.Dimension.GENDER).selectFirstIntersectingMeasure());
        selectedGenders.add(dimensionPanels.get(StudyFinderPage.Dimension.GENDER).selectFirstIntersectingMeasure());

        assertCountsSynced(studyFinder);
        assertSelectionsSynced(studyFinder);

        dimensionPanels.get(StudyFinderPage.Dimension.SPECIES).selectAll();

        List<String> finalSelectedGenders = dimensionPanels.get(StudyFinderPage.Dimension.GENDER).getSelectedValues();
        List<String> finalSelectedSpecies = dimensionPanels.get(StudyFinderPage.Dimension.SPECIES).getSelectedValues();

        assertEquals("Clearing Species selection removed Gender filter", selectedGenders, finalSelectedGenders);
        assertEquals("Clicking 'ALL' didn't clear species selection", 0, finalSelectedSpecies.size());

        assertCountsSynced(studyFinder);
        assertSelectionsSynced(studyFinder);
    }

    @Test
    public void testSelectingEmptyMeasure()
    {
        StudyFinderPage studyFinder = StudyFinderPage.goDirectlyToPage(this, getProjectName());
        studyFinder.dismissTour();

        WebElement emptyMember = Locator.css("fieldset.group-fieldset > div.emptyMember").waitForElement(getDriver(), shortWait());
        String value = emptyMember.getText().trim();
        emptyMember.click();

        studyFinder.waitForSelection(value);

        List<StudyFinderPage.StudyCard> filteredStudyCards = studyFinder.getStudyCards();
        Map<String, Integer> filteredSummaryCounts = studyFinder.getSummaryCounts();

        assertEquals("Study cards visible after selection", 0, filteredStudyCards.size());

        for (Map.Entry count : filteredSummaryCounts.entrySet())
        {
            assertEquals(String.format("Wrong %s count after selection", count.getKey()), 0, count.getValue());
        }
    }

    @Test
    public void testSearch()
    {
        StudyFinderPage studyFinder = StudyFinderPage.goDirectlyToPage(this, getProjectName());
        studyFinder.dismissTour();
        studyFinder.checkShowAllImmPortStudies();

        List<StudyFinderPage.StudyCard> studyCards = studyFinder.getStudyCards();
        String searchString = studyCards.get(0).getAccession();

        studyFinder.studySearch(searchString);

        shortWait().until(ExpectedConditions.stalenessOf(studyCards.get(1).getCardElement()));
        studyCards = studyFinder.getStudyCards();

        assertEquals("Wrong number of studies after search", 1, studyCards.size());

        assertCountsSynced(studyFinder);
    }

    @Test @Ignore("Requires selenium.reuseWebDriver = false. Dubious usefulness")
    public void testAutoShowQuickHelp()
    {
        StudyFinderPage studyFinder = StudyFinderPage.goDirectlyToPage(this, getProjectName());

        Locator helpBubbleLoc = Locator.css(".hopscotch-bubble");
        WebElement helpBubble = helpBubbleLoc.waitForElement(getDriver(), shortWait());
    }

    @Test
    public void testStudySummaryWindow()
    {
        StudyFinderPage studyFinder = StudyFinderPage.goDirectlyToPage(this, getProjectName());
        studyFinder.dismissTour();

        StudyFinderPage.StudyCard studyCard = studyFinder.getStudyCards().get(0);

        StudySummaryWindow summaryWindow = studyCard.viewSummary();

        assertEquals("Study card does not match summary (Accession)", studyCard.getAccession(), summaryWindow.getAccession());
        assertEquals("Study card does not match summary (Title)", studyCard.getTitle().toUpperCase(), summaryWindow.getTitle());
        String cardPI = studyCard.getPI();
        String summaryPI = summaryWindow.getPI();
        assertTrue("Study card does not match summary (PI)", summaryPI.contains(cardPI));

        summaryWindow.closeWindow();
    }

    @Test @Ignore("TODO: Add a LabKey study to the cube and test that it has a 'go to study' link in the study finder")
    public void testLabKeyStudyIntegration() {}

    @LogMethod(quiet = true)
    private void assertCountsSynced(StudyFinderPage studyFinder)
    {
        List<StudyFinderPage.StudyCard> studyCards = studyFinder.getStudyCards();
        Map<String, Integer> studyCounts = studyFinder.getSummaryCounts();
        Map<StudyFinderPage.Dimension, StudyFinderPage.DimensionPanel> dimensions = studyFinder.getDimensionPanels();

        assertEquals("Study count mismatch", studyCards.size(), studyCounts.get("studies").intValue());

        for (StudyFinderPage.Dimension dim : StudyFinderPage.Dimension.values())
        {
            assertEquals(dim.getPlural(), studyCounts.get(dim.getPlural()).intValue(), dimensions.get(dim).getNonEmptyValues().size());
        }
    }

    @LogMethod(quiet = true)
    private void assertSelectionsSynced(StudyFinderPage studyFinder)
    {
        Map<StudyFinderPage.Dimension, StudyFinderPage.DimensionPanel> dimensionPanels = studyFinder.getDimensionPanels();
        Map<StudyFinderPage.Dimension, StudyFinderPage.DimensionFilter> summarySelections = studyFinder.getSelections();

        for (StudyFinderPage.Dimension dim : StudyFinderPage.Dimension.values())
        {
            Bag<String> panelSelections = new HashBag<>(dimensionPanels.get(dim).getSelectedValues());

            Bag<String> dimensionSummarySelections;
            if (summarySelections.containsKey(dim))
                dimensionSummarySelections = new HashBag<>(summarySelections.get(dim).getFilterValues());
            else
                dimensionSummarySelections = new HashBag<>();

            assertEquals("Selection did not match summary for " + dim.getSingular(), panelSelections, dimensionSummarySelections);
        }
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList("ImmPort");
    }
}
