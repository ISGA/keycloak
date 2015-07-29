/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2013 Red Hat, Inc. and/or its affiliates.
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
package org.keycloak.testsuite.console.session;

import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.keycloak.testsuite.console.page.settings.TokensPage;

import static org.jboss.arquillian.graphene.Graphene.waitModel;
import org.keycloak.testsuite.console.AbstractAdminConsoleTest;
import static org.keycloak.testsuite.util.SeleniumUtils.waitGuiForElement;

/**
 *
 * @author Petr Mensik
 */
public class TokensTest extends AbstractAdminConsoleTest<TokensPage> {

    private static final int TIMEOUT = 10;
    private static final TimeUnit TIME_UNIT = TimeUnit.SECONDS;

    @Before
    public void beforeTokensTest() {
        navigation.tokens(masterRealm.MASTER);
    }

    @Test
    public void testTimeoutForRealmSession() throws InterruptedException {
        page.setSessionTimeout(TIMEOUT, TIME_UNIT);
        TIME_UNIT.sleep(TIMEOUT + 2); //add 2 secs to timeout
        driver.navigate().refresh();
        waitGuiForElement(login.getLoginPageHeader(), "Home page should be visible after session timeout");
        login.loginAsAdmin();
        page.setSessionTimeout(30, TimeUnit.MINUTES);
    }

    @Test
    public void testLifespanOfRealmSession() {
        page.setSessionTimeoutLifespan(TIMEOUT, TIME_UNIT);
        logOut();
        //loginAsAdmin();
        waitModel().withTimeout(TIMEOUT + 2, TIME_UNIT) //adds 2 seconds to the timeout
                .pollingEvery(1, TIME_UNIT)
                .until("Home page should be visible after session timeout")
                .element(login.getLoginPageHeader())
                .is()
                .present();
        login.loginAsAdmin();
        navigation.tokens(masterRealm.MASTER);
        page.setSessionTimeoutLifespan(10, TimeUnit.HOURS);
    }
}
