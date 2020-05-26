package org.labkey.test.pages.immport;

import org.labkey.test.Locator;
import org.labkey.test.WebDriverWrapper;
import org.labkey.test.WebTestHelper;
import org.labkey.test.components.html.SelectWrapper;
import org.labkey.test.pages.LabKeyPage;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

public class PublishExpressionMatrixPage extends LabKeyPage<PublishExpressionMatrixPage.ElementCache>
{
    public PublishExpressionMatrixPage(WebDriver driver)
    {
        super(driver);
    }

    public static PublishExpressionMatrixPage beginAt(WebDriverWrapper driver)
    {
        return beginAt(driver, driver.getCurrentContainerPath());
    }

    public static PublishExpressionMatrixPage beginAt(WebDriverWrapper driver, String containerPath)
    {
        driver.beginAt(WebTestHelper.buildURL("immport", containerPath, "publishExpressionMatrix"));
        return new PublishExpressionMatrixPage(driver.getDriver());
    }

    public PublishExpressionMatrixPage selectTargetFolder(String containerPath)
    {
        if (!containerPath.startsWith("/"))
            containerPath = "/" + containerPath;
        elementCache().targetSelect.selectByVisibleText(containerPath);

        return this;
    }

    public ImportExpressionMatrixPage clickPublish()
    {
        doAndWaitForPageToLoad(elementCache().form::submit);
        return new ImportExpressionMatrixPage(getDriver());
    }

    @Override
    protected ElementCache newElementCache()
    {
        return new ElementCache();
    }

    protected class ElementCache extends LabKeyPage.ElementCache
    {
        Select targetSelect = SelectWrapper.Select(Locator.name("target")).findWhenNeeded(this);
        WebElement form = Locator.tagWithName("form", "publishExpressionMatrix").findWhenNeeded(this);
    }
}
