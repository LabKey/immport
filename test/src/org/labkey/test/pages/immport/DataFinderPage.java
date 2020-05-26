package org.labkey.test.pages.immport;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.junit.Assert;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.WebTestHelper;
import org.labkey.test.components.Component;
import org.labkey.test.components.ext4.Window;
import org.labkey.test.components.immport.StudySummaryWindow;
import org.labkey.test.pages.LabKeyPage;
import org.labkey.test.pages.study.ManageParticipantGroupsPage;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.Ext4Helper;
import org.labkey.test.util.LogMethod;
import org.labkey.test.util.LoggedParam;
import org.labkey.test.util.TestLogger;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.labkey.test.Locators.pageSignal;

public class DataFinderPage extends LabKeyPage
{
    private static final String CONTROLLER = "immport";
    private static final String ACTION = "dataFinder";
    private static final String COUNT_SIGNAL = "dataFinderCountsUpdated";
    private static final String GROUP_UPDATED_SIGNAL = "participantGroupUpdated";

    public DataFinderPage(BaseWebDriverTest test)
    {
        super(test);
    }

    private Integer parseInt(String s)
    {
        s = StringUtils.trimToEmpty(s).replace(",", "");
        return s.isEmpty() ? null : Integer.parseInt(s);
    }

    @Override
    protected void waitForPage()
    {
        waitForElement(pageSignal(COUNT_SIGNAL));
    }

    protected void doAndWaitForGroupUpdate(Runnable run)
    {
        doAndWaitForPageSignal(run, GROUP_UPDATED_SIGNAL);
    }

    public static DataFinderPage goDirectlyToPage(BaseWebDriverTest test, String containerPath)
    {
        test.beginAt(WebTestHelper.buildURL(CONTROLLER, containerPath, ACTION));
        return new DataFinderPage(test);
    }

    public ExportStudyDatasetsPage exportDatasets()
    {
        clickAndWait(Locators.exportDatasets);
        return new ExportStudyDatasetsPage(getDriver());
    }

    public void showUnloadedImmPortStudies()
    {
        selectStudySubset("Unloaded ImmPort studies");
    }

    public void showAllImmuneSpaceStudies()
    {
        selectStudySubset("ImmuneSpace studies");
    }

    public void selectStudySubset(String text)
    {
        String selectedText = getSelectedOptionText(Locators.studySubsetChooser);
        if (!selectedText.equals(text))
        {
            doAndWaitForPageSignal(() -> selectOptionByText(Locators.studySubsetChooser, text), COUNT_SIGNAL);
        }
    }

    @LogMethod
    public void studySearch(@LoggedParam final String search)
    {
        doAndWaitForPageSignal(() -> setFormElement(Locators.studySearchInput, search), COUNT_SIGNAL);
    }

    @LogMethod(quiet = true)
    public void clearSearch()
    {
        if (!getFormElement(Locators.studySearchInput).isEmpty())
            studySearch(" ");
    }

    public void saveGroup()
    {
        saveGroup(null);
    }

    public void saveGroup(String name)
    {
        setGroupName(name);
        doAndWaitForGroupUpdate(() -> clickButtonContainingText("Save", BaseWebDriverTest.WAIT_FOR_EXT_MASK_TO_DISSAPEAR));
    }

    public void saveAndSendGroup(String name)
    {
        setGroupName(name);
        clickAndWait(Ext4Helper.Locators.ext4Button("Save and Send"));
        waitForText("Message link:");
    }

    private void setGroupName(String name)
    {
        DataRegionTable.DataRegion(getDriver()).withName("demoDataRegion").waitFor();
        if (name != null)
            setFormElement(Locators.groupLabelInput, name);
    }

    public String getGroupNameFromForm()
    {
        DataRegionTable.DataRegion(getDriver()).withName("demoDataRegion").waitFor();
        return getFormElement(Locators.groupLabelInput);
    }

    public String getGroupLabel()
    {
        return Locators.groupLabel.findElement(getDriver()).getText().trim();
    }

    public SaveMenu saveMenu()
    {
        return new SaveMenu();
    }

    public LoadMenu loadMenu()
    {
        return new LoadMenu();
    }

    public ManageMenu manageMenu()
    {
        return new ManageMenu();
    }

    public boolean menuIsDisabled(Locator.CssLocator locator)
    {
        return isElementPresent(locator.append(" .labkey-disabled-text-link"));
    }

    public void openMenu(Locator locator)
    {
        locator.findElement(getDriver()).click();
    }

    public Map<Dimension, Integer> getSummaryCounts()
    {
        WebElement summaryElement = Locators.summaryArea.findElement(getDriver());
        DimensionPanel summary = new DimensionPanel(summaryElement);

        Map<Dimension, Integer> countMap = new HashMap<>();
        for (String value : summary.getValues())
        {
            String[] parts = value.split("\n");
            Dimension dimension = Dimension.fromString(parts[0].toLowerCase());
            Integer count = parseInt(parts[1]);
            countMap.put(dimension, count);
        }
        return countMap;
    }

    public List<StudyCard> getStudyCards()
    {
        List<WebElement> studyCardEls = Locators.studyCard.findElements(getDriver());
        List<StudyCard> studyCards = new ArrayList<>();

        for (WebElement el : studyCardEls)
        {
            studyCards.add(new StudyCard(el));
        }

        return studyCards;
    }

    public List<DimensionMember> getSelectedMembers()
    {
        List<DimensionMember> members = new ArrayList<>();
        for (WebElement el : Locators.selection.findElements(getDriver()))
        {
            members.add(new DimensionMember(el));
        }
        return members;
    }

    public Map<Dimension, List<String>> getSelectionValues()
    {
        Map<Dimension, List<String>> selectionValues = new HashMap<>();

        for (DimensionMember member : getSelectedMembers())
        {
            List<String> values = selectionValues.get(member.getDimension());
            if (values == null)
            {
                values = new ArrayList<>();
                selectionValues.put(member.getDimension(), values);
            }
            values.add(member.getName());
        }

        return selectionValues;
    }

    public Map<Dimension, DimensionPanel> getAllDimensionPanels()
    {
        return getDimensionPanels(Locators.facet);
    }

    public Map<Dimension, DimensionPanel> getDimensionPanels(Locator locator)
    {
        List<WebElement> dimensionPanelEls = locator.findElements(getDriver());
        Map<Dimension, DimensionPanel> dimensionPanels = new HashMap<>();

        for (WebElement el : dimensionPanelEls)
        {
            DimensionPanel panel = new DimensionPanel(el);
            dimensionPanels.put(panel.getDimension(), panel);
        }

        return dimensionPanels;
    }

    public void clearAllFilters()
    {
        final WebElement clearAll = Locator.findAnyElementOrNull(getDriver(), Locators.clearAll, Locators.clearAllFilters);
        if (clearAll != null && clearAll.isDisplayed())
        {
            doAndWaitForPageSignal(clearAll::click, COUNT_SIGNAL);
        }
    }

    public SendParticipantPage clickSend()
    {
        clickAndWait(Locators.sendMenu);
        return new SendParticipantPage(getDriver());
    }

    public Window clickSendWithUnsavedGroup()
    {
        click(Locators.sendMenu);
        return new Window.WindowFinder(getDriver()).withTitle("Save Group Before Sending").waitFor();
    }

    public void dismissTour()
    {
        shortWait().until(new Function<WebDriver, Boolean>()
        {
            @Override
            public Boolean apply(WebDriver webDriver)
            {
                try
                {
                    return executeScript("" +
                            "if (window.hopscotch)" +
                            "  return !hopscotch.endTour().isActive;" +
                            "else" +
                            "  return true;", Boolean.class);
                }
                catch (WebDriverException recheck)
                {
                    return false;
                }
            }

            @Override
            public String toString()
            {
                return "tour to be dismissed.";
            }
        });
    }

    public static class Locators
    {
        public static final Locator.CssLocator studyFinder = Locator.css("#dataFinderApp");
        public static final Locator.XPathLocator exportDatasets = Locator.linkWithText("Export Study Datasets");
        public static final Locator.CssLocator studySearchInput = studyFinder.append(Locator.css("#searchTerms"));
        public static final Locator studySubsetChooser = Locator.name("studySubsetSelect");
        public static final Locator.CssLocator studyCard = studyFinder.append(Locator.css(".labkey-study-card"));
        public static final Locator.CssLocator selectionPanel = studyFinder.append(Locator.css(".df-selection-panel"));
        public static final Locator.CssLocator facetPanel = selectionPanel.append(Locator.css("#facetPanel"));
        public static final Locator.CssLocator facet = facetPanel.append(" .df-facet");
        public static final Locator.CssLocator summaryArea = selectionPanel.append(Locator.css("#summaryArea"));
        public static final Locator.CssLocator selection = facetPanel.append(Locator.css(" .df-selected-member"));
        public static final Locator.CssLocator clearAll = Locator.css("span[ng-click='clearAllFilters(true);']");
        public static final Locator.CssLocator clearAllFilters = Locator.css("span[ng-click='clearAllClick();']");
        public static final Locator.CssLocator groupLabel = Locator.css(".labkey-group-label");
        public static final Locator groupLabelInput = Locator.name("groupLabel");
        private static final Locator.CssLocator sendMenu = Locator.css("#sendMenu");
    }

    public enum Dimension
    {
        STUDIES(null, "studies"),
        SUBJECTS(null, "subjects"),
        SPECIES("Species", "species"),
        CONDITION("Condition", "conditions"),
        CATEGORY("Research focus", "Category"),
        ASSAY("Assay", "assays"),
        TIMEPOINT("Day of Study", "timepoints"),
        GENDER("Gender", "genders"),
        RACE("Race", "races"),
        AGE("Age", "age groups"),
        STUDY("Study", "studies"),
        MATERIAL("Exposure Material", "exposure materials"),
        PROCESS("Exposure Process", "exposure processes"),
        SAMPLETYPE("Sample Type", "sample types");

        private String caption;
        private String summaryLabel;

        Dimension(String caption, String summaryLabel)
        {
            this.caption = caption;
            this.summaryLabel = summaryLabel;
        }

        public String getCaption()
        {
            return caption;
        }

        public String getSummaryLabel()
        {
            return summaryLabel;
        }

        public static Dimension fromString(String value)
        {
            for (Dimension dimension : values())
            {
                if (value.equals(dimension.getSummaryLabel()) || value.equals(dimension.getCaption()))
                    return dimension;
                if (value.equalsIgnoreCase("participants"))
                    return Dimension.SUBJECTS;
            }

            throw new IllegalArgumentException("No such dimension: " + value);
        }
    }

    private abstract class GroupMenu extends Component
    {
        private final WebElement menu;
        private final WebElement menuAnchor = Locator.xpath("./a").findWhenNeeded(this);
        private final WebElement dropDownMenu = Locator.xpath("./ul").findWhenNeeded(this);
        private final Locator.XPathLocator menuOption = Locator.byClass("df-menu-item-link");
        private final Locator activeOption = menuOption.withoutClass("inactive");
        private final Locator inactiveOption = menuOption.withClass("inactive");

        private GroupMenu(WebElement menu)
        {
            this.menu = menu;
        }

        @Override
        public WebElement getComponentElement()
        {
            return menu;
        }

        public boolean isEnabled()
        {
            return !menuAnchor.getAttribute("class").contains("disabled");
        }

        public List<String> getActiveOptions()
        {
            openMenu();
            return getOptions(activeOption);
        }

        public List<String> getInactiveOptions()
        {
            openMenu();
            return getOptions(inactiveOption);
        }

        protected WebElement getMenuOption(String optionText)
        {
            return menuOption.withText(optionText).findElement(this);
        }

        protected void clickOptionAndWaitForUpdate(String optionText)
        {
            doAndWaitForGroupUpdate(() -> clickOption(optionText));
        }

        protected void clickOption(String optionText)
        {
            TestLogger.log("Choosing menu option " + optionText);
            WebElement option = getMenuOption(optionText);
            int height = menuAnchor.getSize().getHeight();

            openMenu();
            Assert.assertFalse("Menu option is not active: " + optionText, option.getAttribute("class").contains("inactive"));

            for (int i = 0; i < 5; i++)
            {
                try
                {
                    new Actions(getDriver())
                            .moveToElement(menuAnchor)
                            .moveByOffset(0, height) // Move down so that move to option doesn't pass over another menu
                            .moveToElement(option)
                            .perform();
                    break;
                }
                catch (WebDriverException retry)
                {
                    if (i > 3)
                        throw retry;
                }
            }
            option.click();
        }

        private void openMenu()
        {
            if (!isEnabled())
                throw new IllegalStateException("Menu is not enabled: " + getComponentElement().getText());
            new Actions(getDriver()).moveToElement(menuAnchor).perform();
            shortWait().until(ExpectedConditions.and(
                    ExpectedConditions.visibilityOf(menuOption.findElement(this)),
                    wd -> dropDownMenu.getAttribute("class").contains("labkey-dropdown-menu-active")));
        }

        private List<String> getOptions(Locator locator)
        {
            List<WebElement> options = locator.findElements(this);
            List<String> optionStrings = new ArrayList<>();
            for (WebElement option : options)
            {
                shortWait().until(wd -> !option.getText().replace(Locator.NBSP, "").isBlank());
                optionStrings.add(option.getText().trim());
            }
            return optionStrings;
        }
    }

    public class SaveMenu extends GroupMenu
    {
        public SaveMenu()
        {
            super(Locator.css("#saveMenu").findElement(getDriver()));
        }

        public void save()
        {
            clickOptionAndWaitForUpdate("Save");
        }

        public void saveAs()
        {
            clickOption("Save As");
        }
    }

    public class LoadMenu extends GroupMenu
    {
        public LoadMenu()
        {
            super(Locator.css("#loadMenu").findElement(getDriver()));
        }

        public void loadGroup(String groupName)
        {
            clickOptionAndWaitForUpdate(groupName);
        }
    }

    public class ManageMenu extends GroupMenu
    {
        public ManageMenu()
        {
            super(Locator.id("df-manageMenu").findElement(getDriver()));
        }

        public ManageParticipantGroupsPage manageGroups()
        {
            // Manage Groups menu likes to disappear in Firefox sometimes. We aren't here to test angularJS
            beginAt(getMenuOption("Manage Groups").getAttribute("href"));
            return new ManageParticipantGroupsPage(DataFinderPage.this);
        }
    }

    public class DimensionPanel extends Component
    {
        private WebElement panel;
        private Elements elements;
        private Dimension dimension;

        private DimensionPanel(WebElement panel)
        {
            this.panel = panel;
            elements = new Elements();
        }

        public Dimension getDimension()
        {
            if (dimension == null)
            {
                dimension = Dimension.fromString(findElement(elements.dimension).getText());
            }

            return dimension;
        }

        @Override
        public WebElement getComponentElement()
        {
            return panel;
        }

        public List<String> getValues()
        {
            displayDimension();
            return getTexts(elements.member.findElements(this));
        }

        public List<String> getEmptyValues()
        {
            displayDimension();
            return getTexts(elements.emptyMemberName.findElements(this));
        }

        public List<String> getNonEmptyValues()
        {
            displayDimension();
            return getTexts(elements.nonEmptyMemberName.findElements(this));
        }

        public List<String> getSelectedValues()
        {
            displayDimension();
            return getTexts(elements.selectedMemberName.findElements(this));
        }

        public void displayDimension()
        {
            if (!isDisplayed())
            {
                log("Member list is not displayed.");
                WebElement caption = findElement(elements.facetCaption);
                log("Click facet: '" + caption.getText() + "'.");
                scrollIntoView(caption);
                caption.click();
                sleep(500);
            }
            else
            {
                log("Member list is displayed.");
            }
        }

        public boolean isDisplayed()
        {
            return this.panel.getAttribute("class").contains("expanded");
        }

        public String selectFirstIntersectingMeasure()
        {
            displayDimension();

            WebElement el = findElement(elements.nonEmptyNonSelectedMemberName);
            String value = el.getText();

            addToSelection(el);

            waitForSelection(value);
            return value;
        }

        public Map<String, Integer> getMemberCounts()
        {
            displayDimension();
            Map<String, Integer> countMap = new HashMap<>();
            List<WebElement> members = elements.member.findElements(this);
            log("getMemberCounts: dimension: " + getDimension().name());
            log("There are " + members.size() + " members in the list.");
            for (WebElement member : members)
            {
                String name = elements.memberName.findElement(member).getText();

                if(name.trim().length() == 0)
                {
                    // The panel wasn't expanded, try again.
                    log("The dimension wasn't expanded trying again.");
                    displayDimension();
                    name = elements.memberName.findElement(member).getText();
                }

                String countText = elements.memberCount.findElement(member).getText();
                log("getMemberCounts: name: " + name + " countText: " + countText);
                Integer count = parseInt(countText);
                countMap.put(name, count);
            }

            return countMap;
        }

        public void selectMember(String memberName)
        {
            displayDimension();
            select(findElement(elements.memberName.withText(memberName)));
            waitForSelection(memberName);
        }

        public void deselectMember(String memberName)
        {
            WebElement member = findElement(elements.member.containing(memberName));
            WebElement check = elements.selectedMemberCheck.findElement(member);
            select(check);
        }

        public void addToSelection(String value)
        {
            displayDimension();
            addToSelection(findElement(elements.member.withText(value)));
            waitForSelection(value);
        }

        public void clearFilters()
        {
            select(findElement(elements.clearFilters));
        }

        private void select(final WebElement value)
        {
            doAndWaitForPageSignal(value::click, COUNT_SIGNAL);
        }

        private void addToSelection(final WebElement value)
        {
            doAndWaitForPageSignal(() -> controlClick(value), COUNT_SIGNAL);
        }

        private void controlClick(WebElement el)
        {
            Keys multiSelectKey;
            if (SystemUtils.IS_OS_MAC)
                multiSelectKey = Keys.COMMAND;
            else
                multiSelectKey = Keys.CONTROL;

            Actions builder = new Actions(getDriver());
            builder.keyDown(multiSelectKey).build().perform();
            el.click();
            builder.keyUp(multiSelectKey).build().perform();
        }

        private void waitForSelection(String value)
        {
            elements.selectedMemberName.containing(value).waitForElement(panel, BaseWebDriverTest.WAIT_FOR_JAVASCRIPT);
        }

        private class Elements
        {
            public Locator.CssLocator facetCaption = Locator.css(".df-facet-caption");
            public Locator.CssLocator dimension = Locator.css(".df-facet-caption > span");
            public Locator.CssLocator member = Locator.css(".df-member");
            public Locator.CssLocator memberName = Locator.css(".df-member-name");
            public Locator.CssLocator memberCount = Locator.css(".df-member-count");
            public Locator.CssLocator selectedMemberCheck = Locator.css(".df-member .df-member-indicator.selected");
            public Locator.CssLocator emptyMemberName = Locator.css(".ng-scope.df-empty-member .df-member-name");
            public Locator.CssLocator nonEmptyMemberName = Locator.css(".ng-scope.df-member:not(.df-empty-member) .df-member-name");
            public Locator.CssLocator nonEmptyNonSelectedMemberName = Locator.css(".ng-scope.df-member:not(.df-empty-member):not(.df-selected-member) .df-member-name");
            public Locator.CssLocator selectedMemberName = Locator.css(".ng-scope.df-selected-member .df-member-name");
            public Locator.CssLocator clearFilters = Locator.css(".df-clear-filter");
        }
    }

    public class StudyCard extends Component
    {
        private final WebElement card;

        private StudyCard(WebElement card)
        {
            this.card = card;
        }

        @Override
        public WebElement getComponentElement()
        {
            return card;
        }

        public StudySummaryWindow viewSummary()
        {
            viewStudyLink.click();
            return new StudySummaryWindow(getDriver());
        }

        public void clickGoToStudy()
        {
            clickAndWait(goToStudyLink);
        }

        public String getAccession()
        {
            return name.getText();
        }

        public String getPI()
        {
            return PI.getText();
        }

        public String getTitle()
        {
            return titleLoc.getText();
        }

        private final WebElement viewStudyLink = Locator.linkWithText("view summary").findWhenNeeded(this);
        private final WebElement goToStudyLink = Locator.linkWithText("go to study").findWhenNeeded(this);
        private final WebElement name = Locator.css(".labkey-study-card-accession").findWhenNeeded(this);
        private final WebElement PI = Locator.css(".labkey-study-card-pi").findWhenNeeded(this);
        private final WebElement titleLoc = Locator.css(".labkey-study-card-description").findWhenNeeded(this);
    }

    public class DimensionMember extends Component
    {
        private final Elements elements;
        private final WebElement memberElement;
        private Dimension dimension;

        private DimensionMember(final WebElement memberElement)
        {
            this.memberElement = memberElement;
            elements = new Elements();
            parseMemberId();
        }

        private void parseMemberId()
        {
            String id = this.memberElement.getAttribute("id");
            String[] parts = id.split("_");
            this.dimension = Dimension.fromString(parts[1]);
        }

        @Override
        public WebElement getComponentElement()
        {
            return memberElement;
        }

        public Dimension getDimension()
        {
            return dimension;
        }

        public String getName()
        {
            return elements.memberName.findElement(memberElement).getText();
        }

        public Integer getCount()
        {
            return parseInt(elements.memberCount.findElement(memberElement).getText());
        }

        private class Elements
        {
            public Locator memberName = Locator.css(".df-member-name");
            public Locator memberCount = Locator.css(".df-member-count");
        }
    }
}
