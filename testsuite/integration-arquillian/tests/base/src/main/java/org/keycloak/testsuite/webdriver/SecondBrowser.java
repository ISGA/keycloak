package org.keycloak.testsuite.webdriver;

import org.openqa.selenium.WebDriver;

public class SecondBrowser implements Browser {
    private WebDriver webDriver;

    private final WebDriverLifecycle webDriverLifecycle;

    public  SecondBrowser() {
        this.webDriverLifecycle = new WebDriverLifecycle();
    }

    @Override
    public WebDriver getBrowser() {
        return this.webDriver;
    }

    @Override
    public void startBrowser() {
        this.webDriver = this.webDriverLifecycle.startBrowser();
    }

    @Override
    public void stopBrowser() {
        this.webDriverLifecycle.stopBrowser(this.webDriver);
    }
}
