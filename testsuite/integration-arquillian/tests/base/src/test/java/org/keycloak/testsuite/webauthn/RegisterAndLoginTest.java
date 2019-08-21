/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.keycloak.testsuite.webauthn;

import org.jboss.arquillian.graphene.page.Page;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.keycloak.WebAuthnConstants;
import org.keycloak.authentication.authenticators.browser.WebAuthnAuthenticatorFactory;
import org.keycloak.authentication.requiredactions.WebAuthnRegisterFactory;
import org.keycloak.common.util.RandomString;
import org.keycloak.events.Details;
import org.keycloak.events.EventType;
import org.keycloak.representations.idm.AuthenticationExecutionInfoRepresentation;
import org.keycloak.representations.idm.AuthenticatorConfigRepresentation;
import org.keycloak.representations.idm.EventRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.testsuite.AssertEvents;
import org.keycloak.testsuite.admin.AbstractAdminTest;
import org.keycloak.testsuite.AbstractTestRealmKeycloakTest;
import org.keycloak.testsuite.pages.webauthn.LoginPage;
import org.keycloak.testsuite.pages.webauthn.RegisterPage;
import org.keycloak.testsuite.pages.webauthn.AppPage;
import org.keycloak.testsuite.pages.webauthn.AppPage.RequestType;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Ignore("not completed yet")
public class RegisterAndLoginTest extends AbstractTestRealmKeycloakTest {

    @Rule
    public AssertEvents events = new AssertEvents(this);

    @Page
    protected AppPage appPage;

    @Page
    protected LoginPage loginPage;

    @Page
    protected RegisterPage registerPage;

    @Override
    public void configureTestRealm(RealmRepresentation testRealm) {
    }

    protected WebDriver driverN;
    protected WebDriverWait waitN;

    private static final String ALL_ZERO_AAGUID = "00000000-0000-0000-0000-000000000000";
    private static final String AUTHN_FLOW_EXEC_ALIAS = "Copy of browser forms";
    private static final String AUTHN_CONFIG_ALIAS = "webauthn-2FA";

    private String authnConfigId;

    private List<String> signatureAlgorithms;
    private String attestationConveyancePreference;
    private String authenticatorAttachment;
    private String requireResidentKey;
    private String rpEntityName;
    private String userVerificationRequirement;
    private String rpId;
    private int createTimeout;
    private boolean avoidSameAuthenticatorRegister;
    private List<String> acceptableAaguids;

    private String userVerificationRequirementOnAuthentication;
    
    @BeforeClass
    public static void setupClassTest(){
        WebDriverManager.chromedriver().setup();
    }

    @Before
    public void setupTest() {
        ChromeOptions chromeOptions = new ChromeOptions();
        chromeOptions.addArguments("--enable-web-authentication-testing-api");
        chromeOptions.setHeadless(true);
        driverN = new ChromeDriver(chromeOptions);
        waitN = new WebDriverWait(driverN, 5);
        driverN.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
        appPage.setDriver(driverN);
        loginPage.setDriver(driverN);
        registerPage.setDriver(driverN);
        oauth.setDriver(driverN);

        authnConfigId = getAuthenticatorConfigId();
    }

    @After
    public void teardown() {
        if (driverN != null) driverN.quit();
    }

    @Override
    public void addTestRealms(List<RealmRepresentation> testRealms) {
        RealmRepresentation realmRepresentation = AbstractAdminTest.loadJson(getClass().getResourceAsStream("/webauthn/testrealm-webauthn.json"), RealmRepresentation.class);
        testRealms.add(realmRepresentation);
    }

    @Test
    public void registerUserSuccess() {
        String username = "registerUserSuccess";
        String password = "password";
        String email = "registerUserSuccess@email";

        try {
            RealmRepresentation rep = backupWebAuthnRealmSettings();
            rep.setWebAuthnPolicySignatureAlgorithms(Arrays.asList("ES256"));
            rep.setWebAuthnPolicyAttestationConveyancePreference("none");
            rep.setWebAuthnPolicyAuthenticatorAttachment("cross-platform");
            rep.setWebAuthnPolicyRequireResidentKey("No");
            rep.setWebAuthnPolicyRpId(null);
            rep.setWebAuthnPolicyUserVerificationRequirement("preferred");
            rep.setWebAuthnPolicyAcceptableAaguids(Arrays.asList(this.ALL_ZERO_AAGUID));
            testRealm().update(rep);

            setUserVerificationRequirementOnAuthentication("preferred");


            loginPage.open();
            loginPage.clickRegister();
            registerPage.assertCurrent();

            String authenticatorLabel = RandomString.randomCode(24);
            registerPage.register("firstName", "lastName", email, username, password, password, authenticatorLabel);

            appPage.assertCurrent();
            assertEquals(RequestType.AUTH_RESPONSE, appPage.getRequestType());
            appPage.openAccount();

            // confirm that registration is successfully completed
            String userId = events.expectRegister(username, email).assertEvent().getUserId();
            // confirm registration event
            EventRepresentation eventRep = events.expectRequiredAction(EventType.CUSTOM_REQUIRED_ACTION)
            .user(userId)
                .detail(Details.CUSTOM_REQUIRED_ACTION, WebAuthnRegisterFactory.PROVIDER_ID)
                .detail(WebAuthnConstants.PUBKEY_CRED_LABEL_ATTR, authenticatorLabel)
                .assertEvent();
            String regPubKeyCredentialId = eventRep.getDetails().get(WebAuthnConstants.PUBKEY_CRED_ID_ATTR);
            //String regPubKeyCredentialAaguid = eventRep.getDetails().get("public_key_credential_aaguid");
            //String regPubKeyCredentialLabel = eventRep.getDetails().get("public_key_credential_label");

            // confirm login event
            String sessionId = events.expectLogin()
                .user(userId)
                .detail(Details.CUSTOM_REQUIRED_ACTION, WebAuthnRegisterFactory.PROVIDER_ID)
                .detail(WebAuthnConstants.PUBKEY_CRED_LABEL_ATTR, authenticatorLabel)
                .assertEvent().getSessionId();
            // confirm user registered
            assertUserRegistered(userId, username.toLowerCase(), email.toLowerCase());

            // logout by user
            appPage.logout();
            // confirm logout event
            events.expectLogout(sessionId)
                .user(userId)
                .assertEvent();

            // login by user
            loginPage.open();
            loginPage.login(username, password);

            appPage.assertCurrent();
            assertEquals(RequestType.AUTH_RESPONSE, appPage.getRequestType());
            appPage.openAccount();
            // confirm login event
            sessionId = events.expectLogin()
                .user(userId)
                .detail(WebAuthnConstants.PUBKEY_CRED_ID_ATTR, regPubKeyCredentialId)
//              .detail("web_authn_authenticator_user_verification_checked", Boolean.FALSE.toString())
                .assertEvent().getSessionId();

            // logout by user
            appPage.logout();
            // confirm logout event
            events.expectLogout(sessionId)
                .user(userId)
                .assertEvent();
        } finally {
            restoreWebAuthnRealmSettings();
        }
    }

    private void assertUserRegistered(String userId, String username, String email) {
        UserRepresentation user = getUser(userId);
        Assert.assertNotNull(user);
        Assert.assertNotNull(user.getCreatedTimestamp());
        // test that timestamp is current with 10s tollerance
        Assert.assertTrue((System.currentTimeMillis() - user.getCreatedTimestamp()) < 10000);
        // test user info is set from form
        assertEquals(username.toLowerCase(), user.getUsername());
        assertEquals(email.toLowerCase(), user.getEmail());
        assertEquals("firstName", user.getFirstName());
        assertEquals("lastName", user.getLastName());
    }

    protected UserRepresentation getUser(String userId) {
        return testRealm().users().get(userId).toRepresentation();
    }

    private String getAuthenticatorConfigId() {
        for (AuthenticationExecutionInfoRepresentation aeir : testRealm().flows().getExecutions(AUTHN_FLOW_EXEC_ALIAS))
            if (AUTHN_CONFIG_ALIAS.equals(aeir.getAlias())) return aeir.getAuthenticationConfig();
        return null;
    }

    private String getUserVerificationRequirementOnAuthentication() {
        return testRealm().flows().getAuthenticatorConfig(authnConfigId).getConfig().get(WebAuthnAuthenticatorFactory.USER_VERIFICATION_REQUIREMENT);
    }

    private void setUserVerificationRequirementOnAuthentication(String userVerificationRequirementOnAuthentication) {
        AuthenticatorConfigRepresentation conf = testRealm().flows().getAuthenticatorConfig(authnConfigId);
        Map<String, String> config = conf.getConfig();
        config.put(WebAuthnAuthenticatorFactory.USER_VERIFICATION_REQUIREMENT, userVerificationRequirementOnAuthentication);
        conf.setConfig(config);
        testRealm().flows().updateAuthenticatorConfig(authnConfigId, conf);
    }

    private RealmRepresentation backupWebAuthnRealmSettings() {
        RealmRepresentation rep = testRealm().toRepresentation();
        signatureAlgorithms = rep.getWebAuthnPolicySignatureAlgorithms();
        attestationConveyancePreference = rep.getWebAuthnPolicyAttestationConveyancePreference();
        authenticatorAttachment = rep.getWebAuthnPolicyAuthenticatorAttachment();
        requireResidentKey = rep.getWebAuthnPolicyRequireResidentKey();
        rpEntityName = rep.getWebAuthnPolicyRpEntityName();
        userVerificationRequirement = rep.getWebAuthnPolicyUserVerificationRequirement();
        rpId = rep.getWebAuthnPolicyRpId();
        createTimeout = rep.getWebAuthnPolicyCreateTimeout();
        avoidSameAuthenticatorRegister = rep.isWebAuthnPolicyAvoidSameAuthenticatorRegister();
        acceptableAaguids = rep.getWebAuthnPolicyAcceptableAaguids();

        userVerificationRequirementOnAuthentication = getUserVerificationRequirementOnAuthentication();

        return rep;
    }

    public void restoreWebAuthnRealmSettings() {
        RealmRepresentation rep = testRealm().toRepresentation();
        rep.setWebAuthnPolicySignatureAlgorithms(signatureAlgorithms);
        rep.setWebAuthnPolicyAttestationConveyancePreference(attestationConveyancePreference);
        rep.setWebAuthnPolicyAuthenticatorAttachment(authenticatorAttachment);
        rep.setWebAuthnPolicyRequireResidentKey(requireResidentKey);
        rep.setWebAuthnPolicyRpEntityName(rpEntityName);
        rep.setWebAuthnPolicyUserVerificationRequirement(userVerificationRequirement);
        rep.setWebAuthnPolicyRpId(rpId);
        rep.setWebAuthnPolicyCreateTimeout(createTimeout);
        rep.setWebAuthnPolicyAvoidSameAuthenticatorRegister(avoidSameAuthenticatorRegister);
        rep.setWebAuthnPolicyAcceptableAaguids(acceptableAaguids);
        testRealm().update(rep);

        setUserVerificationRequirementOnAuthentication(userVerificationRequirementOnAuthentication);
    }

}
