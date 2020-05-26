package org.labkey.test.pages.immport;

import org.labkey.test.Locator;
import org.labkey.test.WebDriverWrapper;
import org.labkey.test.WebTestHelper;
import org.labkey.test.components.html.SelectWrapper;
import org.labkey.test.pages.LabKeyPage;
import org.labkey.test.util.PipelineStatusTable;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

public class ImportExpressionMatrixPage extends LabKeyPage<ImportExpressionMatrixPage.ElementCache>
{
    public ImportExpressionMatrixPage(WebDriver driver)
    {
        super(driver);
    }

    public static ImportExpressionMatrixPage beginAt(WebDriverWrapper driver)
    {
        return beginAt(driver, driver.getCurrentContainerPath());
    }

    public static ImportExpressionMatrixPage beginAt(WebDriverWrapper driver, String containerPath)
    {
        driver.beginAt(WebTestHelper.buildURL("immport", containerPath, "importExpressionMatrix"));
        return new ImportExpressionMatrixPage(driver.getDriver());
    }

    public ImportExpressionMatrixPage selectXarPath(String xarPath)
    {
        elementCache().xarPathSelect.selectByVisibleText(xarPath);
        return this;
    }

    public String getSelectedXar()
    {
        return elementCache().xarPathSelect.getFirstSelectedOption().getText();
    }

    public PipelineStatusTable clickImport()
    {
        doAndWaitForPageToLoad(elementCache().form::submit);
        return new PipelineStatusTable(this);
    }

    @Override
    protected ElementCache newElementCache()
    {
        return new ElementCache();
    }

    protected class ElementCache extends LabKeyPage.ElementCache
    {
        Select xarPathSelect = SelectWrapper.Select(Locator.name("xarPath")).findWhenNeeded(this);
        WebElement form = Locator.tagWithName("form", "importExpressionMatrix").findWhenNeeded(this);
    }
}
