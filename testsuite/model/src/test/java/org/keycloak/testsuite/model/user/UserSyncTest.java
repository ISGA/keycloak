/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.testsuite.model.user;

import org.hamcrest.Matchers;
import org.junit.Test;
import org.keycloak.cluster.ClusterProvider;
import org.keycloak.component.ComponentModel;
import org.keycloak.component.ComponentValidationException;
import org.keycloak.models.Constants;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.LDAPConstants;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.storage.CacheableStorageProviderModel;
import org.keycloak.storage.UserStorageManager;
import org.keycloak.storage.datastore.LegacyDatastoreProvider;
import org.keycloak.storage.ldap.idm.model.LDAPObject;
import org.keycloak.storage.ldap.idm.store.ldap.LDAPOperationManager;
import org.keycloak.storage.ldap.mappers.LDAPStorageMapper;
import org.keycloak.storage.ldap.mappers.UserAttributeLDAPStorageMapper;
import org.keycloak.storage.ldap.mappers.UserAttributeLDAPStorageMapperFactory;
import org.keycloak.storage.managers.UserStorageSyncManager;
import org.keycloak.storage.UserStoragePrivateUtil;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.UserStorageProviderFactory;
import org.keycloak.storage.UserStorageProviderModel;
import org.keycloak.storage.ldap.LDAPStorageProvider;
import org.keycloak.storage.ldap.LDAPStorageProviderFactory;
import org.keycloak.storage.user.ImportSynchronization;
import org.keycloak.storage.user.ImportedUserValidation;
import org.keycloak.storage.user.SynchronizationResult;
import org.keycloak.testsuite.model.KeycloakModelTest;
import org.keycloak.testsuite.model.RequireProvider;
import org.keycloak.testsuite.util.LDAPTestUtils;

import javax.naming.directory.BasicAttribute;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeThat;

@RequireProvider(UserProvider.class)
@RequireProvider(ClusterProvider.class)
@RequireProvider(RealmProvider.class)
@RequireProvider(value = UserStorageProvider.class, only = LDAPStorageProviderFactory.PROVIDER_NAME)
public class UserSyncTest extends KeycloakModelTest {

    private static final int NUMBER_OF_USERS = 5000;
    private String realmId;
    private String userFederationId;

    @Override
    public void createEnvironment(KeycloakSession s) {
        inComittedTransaction(session -> {
            RealmModel realm = session.realms().createRealm("realm");
            realm.setDefaultRole(session.roles().addRealmRole(realm, Constants.DEFAULT_ROLES_ROLE_PREFIX + "-" + realm.getName()));
            this.realmId = realm.getId();
        });

        getParameters(UserStorageProviderModel.class).forEach(fs -> inComittedTransaction(session -> {
            if (userFederationId != null || !fs.isImportEnabled()) return;
            RealmModel realm = session.realms().getRealm(realmId);

            fs.setParentId(realmId);

            ComponentModel res = realm.addComponentModel(fs);

            // Check if the provider implements ImportSynchronization interface
            UserStorageProviderFactory userStorageProviderFactory = (UserStorageProviderFactory)session.getKeycloakSessionFactory().getProviderFactory(UserStorageProvider.class, res.getProviderId());
            if (!ImportSynchronization.class.isAssignableFrom(userStorageProviderFactory.getClass())) {
                return;
            }

            userFederationId = res.getId();
            log.infof("Added %s user federation provider: %s", fs.getName(), res.getId());
        }));

        assumeThat("Cannot run UserSyncTest because there is no user federation provider that supports sync", userFederationId, notNullValue());
    }

    @Override
    public void cleanEnvironment(KeycloakSession s) {
        final RealmModel realm = s.realms().getRealm(realmId);

        ComponentModel ldapModel = LDAPTestUtils.getLdapProviderModel(realm);
        LDAPStorageProvider ldapFedProvider = LDAPTestUtils.getLdapProvider(s, ldapModel);
        LDAPTestUtils.removeAllLDAPUsers(ldapFedProvider, realm);

        s.realms().removeRealm(realmId);
    }

    @Override
    protected boolean isUseSameKeycloakSessionFactoryForAllThreads() {
        return true;
    }

    @Test
    public void testManyUsersImport() {
        IntStream.range(0, NUMBER_OF_USERS).parallel().forEach(index -> inComittedTransaction(index, (session, i) -> {
            final RealmModel realm = session.realms().getRealm(realmId);

            ComponentModel ldapModel = LDAPTestUtils.getLdapProviderModel(realm);
            LDAPStorageProvider ldapFedProvider = LDAPTestUtils.getLdapProvider(session, ldapModel);
            LDAPTestUtils.addLDAPUser(ldapFedProvider, realm, "user" + i, "User" + i + "FN", "User" + i + "LN", "user" + i + "@email.org", null, "12" + i);
            return null;
        }));

        assertThat(withRealm(realmId, (session, realm) -> UserStoragePrivateUtil.userLocalStorage(session).getUsersCount(realm)), is(0));

        long start = System.currentTimeMillis();
        SynchronizationResult res = withRealm(realmId, (session, realm) -> {
            UserStorageProviderModel providerModel = new UserStorageProviderModel(realm.getComponent(userFederationId));
            return UserStorageSyncManager.syncAllUsers(session.getKeycloakSessionFactory(), realm.getId(), providerModel);
        });
        long end = System.currentTimeMillis();
        long timeNeeded = end - start;

        // The sync shouldn't take more than 18 second per user
        assertThat(String.format("User sync took %f seconds per user, but it should take less than 18 seconds",
                (float)(timeNeeded) / NUMBER_OF_USERS), timeNeeded, Matchers.lessThan((long) (18 * NUMBER_OF_USERS)));
        assertThat(res.getAdded(), is(NUMBER_OF_USERS));
        assertThat(withRealm(realmId, (session, realm) -> UserStoragePrivateUtil.userLocalStorage(session).getUsersCount(realm)), is(NUMBER_OF_USERS));
    }

    @Test
    public void testRemovedLDAPUserShouldNotFailGetUserByEmail() {
        withRealm(realmId, (session, realm) -> {
            UserStorageProviderModel providerModel = new UserStorageProviderModel(realm.getComponent(userFederationId));
            // disable cache
            providerModel.setCachePolicy(CacheableStorageProviderModel.CachePolicy.NO_CACHE);
            realm.updateComponent(providerModel);

            ComponentModel ldapModel = LDAPTestUtils.getLdapProviderModel(realm);
            LDAPStorageProvider ldapFedProvider = LDAPTestUtils.getLdapProvider(session, ldapModel);
            LDAPTestUtils.addLDAPUser(ldapFedProvider, realm, "user", "UserFN", "UserLN", "user@email.org", "userStreet", "1450");
            return null;
        });

        assertThat(withRealm(realmId, (session, realm) -> session.users().getUserByEmail(realm, "user@email.org")), is(notNullValue()));

        withRealm(realmId, (session, realm) -> {
            ComponentModel ldapModel = LDAPTestUtils.getLdapProviderModel(realm);
            LDAPStorageProvider ldapFedProvider = LDAPTestUtils.getLdapProvider(session, ldapModel);
            LDAPTestUtils.removeLDAPUserByUsername(ldapFedProvider, realm, ldapFedProvider.getLdapIdentityStore().getConfig(), "user");
            return null;
        });

        assertThat(withRealm(realmId, (session, realm) -> session.users().getUserByEmail(realm, "user@email.org")), is(nullValue()));
    }

    @Test
    public void testAlwaysReadValueFromLDAPWorksWithNoCachePolicy() {
        // Create mapper from sn to a new user attribute
        withRealm(realmId, (session, realm) -> {
            UserStorageProviderModel providerModel = new UserStorageProviderModel(realm.getComponent(userFederationId));
            // disable cache
            providerModel.setCachePolicy(CacheableStorageProviderModel.CachePolicy.NO_CACHE);
            realm.updateComponent(providerModel);

            // Create mapper
            ComponentModel mapperModel = KeycloakModelUtils.createComponentModel("My-street-mapper", providerModel.getId(), UserAttributeLDAPStorageMapperFactory.PROVIDER_ID, LDAPStorageMapper.class.getName(),
                    UserAttributeLDAPStorageMapper.USER_MODEL_ATTRIBUTE, LDAPConstants.STREET,
                    UserAttributeLDAPStorageMapper.LDAP_ATTRIBUTE, LDAPConstants.STREET,
                    UserAttributeLDAPStorageMapper.READ_ONLY, "false",
                    UserAttributeLDAPStorageMapper.ALWAYS_READ_VALUE_FROM_LDAP, "true",
                    UserAttributeLDAPStorageMapper.IS_MANDATORY_IN_LDAP, "false");
            realm.addComponentModel(mapperModel);

            return null;
        });

        final String MY_STREET_NAME = "my-street 9";

        // create 1 user in LDAP
        withRealm(realmId, (session, realm) -> {
            ComponentModel ldapModel = LDAPTestUtils.getLdapProviderModel(realm);
            LDAPStorageProvider ldapFedProvider = LDAPTestUtils.getLdapProvider(session, ldapModel);
            int i = 1;
            LDAPTestUtils.addLDAPUser(ldapFedProvider, realm, "user" + i, "User" + i + "FN", "User" + i + "LN", "user" + i + "@email.org", MY_STREET_NAME, "12" + i);
            return null;
        });


        // Read attribute that should be mapped by created mapper
        String id = withRealm(realmId, (session, realm) -> {
            UserModel user1 = session.users().getUserByUsername(realm, "user1");
            assertThat(user1.getAttributes().get(LDAPConstants.STREET).get(0), is(equalTo(MY_STREET_NAME)));
            return user1.getId();
        });

        // Remove attribute from the LDAP for given user
        withRealm(realmId, (session, realm) -> {
            UserStorageProviderModel providerModel = new UserStorageProviderModel(realm.getComponent(userFederationId));
            LDAPStorageProvider ldapFedProvider = LDAPTestUtils.getLdapProvider(session, providerModel);

            LDAPObject user1LdapObject = ldapFedProvider.loadLDAPUserByUsername(realm, "user1");
            LDAPOperationManager ldapOperationManager = new LDAPOperationManager(session, ldapFedProvider.getLdapIdentityStore().getConfig());

            ldapOperationManager.removeAttribute(user1LdapObject.getDn().getLdapName(), new BasicAttribute(LDAPConstants.STREET));
            return null;
        });

        // Check local storage contains the old value
        withRealm(realmId, (session, realm) -> {
            UserModel user1 = UserStoragePrivateUtil.userLocalStorage(session).getUserById(realm, id);
            assertThat(user1.getAttributes().get(LDAPConstants.STREET).get(0), is(equalTo(MY_STREET_NAME)));
            return user1.getId();
        });

        // Read from Keycloak by id
        withRealm(realmId, (session, realm) -> {
            UserModel user1 = session.users().getUserById(realm, id);
            assertThat(user1.getAttributes().get(LDAPConstants.STREET), is(nullValue()));
            assertThat(user1.getFirstAttribute(LDAPConstants.STREET), is(nullValue()));
            assertThat(user1.getAttributeStream(LDAPConstants.STREET).findFirst().isPresent(), is(false));
            return null;
        });

        // Read from Keycloak by query
        withRealm(realmId, (session, realm) -> {
            UserModel user1 = session.users().searchForUserStream(realm, Collections.emptyMap()).findFirst().orElse(null);
            assertThat(user1.getAttributes().get(LDAPConstants.STREET), is(nullValue()));
            assertThat(user1.getFirstAttribute(LDAPConstants.STREET), is(nullValue()));
            assertThat(user1.getAttributeStream(LDAPConstants.STREET).findFirst().isPresent(), is(false));
            return null;
        });
    }

    @Test
    public void testThrowsExceptionWhenValidationDisabledWithoutSyncEnabled() {
        // Configure validation explicitly
        var componentException = assertThrows(ComponentValidationException.class, () -> {
            withRealm(realmId, (session, realm) -> {
                UserStorageProviderModel providerModel = new UserStorageProviderModel(realm.getComponent(userFederationId));

                // disable cache
                providerModel.setCachePolicy(CacheableStorageProviderModel.CachePolicy.NO_CACHE);
                providerModel.setValidateUserListings(false); //default, but explicit for this test
                providerModel.setFullSyncPeriod(-1); //not allowed in compination with validation

                realm.updateComponent(providerModel);

                return null;
            });
        });

        assertThat("Message is a valid translation key", componentException.getMessage(), equalTo("ldapErrorCantDisbleValidateUserListingsWithoutEnabledSync"));
    }

    @Test
    public void testValidationIsExecutedForLocalSyncedUser() {
        // Configure validation explicitly
        withRealm(realmId, (session, realm) -> {
            UserStorageProviderModel providerModel = new UserStorageProviderModel(realm.getComponent(userFederationId));

            // enable validation
            providerModel.setCachePolicy(CacheableStorageProviderModel.CachePolicy.NO_CACHE);
            providerModel.setValidateUserListings(true); //default, but explicit for this test
            realm.updateComponent(providerModel);

            return null;
        });

        // create 1 user in LDAP
        withRealm(realmId, (session, realm) -> {
            ComponentModel ldapModel = LDAPTestUtils.getLdapProviderModel(realm);
            LDAPStorageProvider ldapFedProvider = LDAPTestUtils.getLdapProvider(session, ldapModel);
            LDAPTestUtils.addLDAPUser(ldapFedProvider, realm, "user1", "User1FN", "User1LN", "user1@email.org", "Street One 1", "121");
            return null;
        });


        assertThat(withRealm(realmId, (session, realm) -> session.users().getUserByEmail(realm, "user1@email.org")), is(notNullValue()));
        assertThat(withRealm(realmId, (session, realm) -> session.users().searchForUserStream(realm,Map.of("email","user1@email.org")).collect(Collectors.toList())),Matchers.iterableWithSize(1));
        assertThat(withRealm(realmId, (session, realm) -> UserStoragePrivateUtil.userLocalStorage(session).getUsersCount(realm)), is(1));

        withRealm(realmId, (session, realm) -> {
            ComponentModel ldapModel = LDAPTestUtils.getLdapProviderModel(realm);
            LDAPStorageProvider ldapFedProvider = LDAPTestUtils.getLdapProvider(session, ldapModel);
            LDAPTestUtils.removeLDAPUserByUsername(ldapFedProvider, realm, ldapFedProvider.getLdapIdentityStore().getConfig(), "user1");
            return null;
        });

        assertThat(withRealm(realmId, (session, realm) -> session.users().searchForUserStream(realm,Map.of("email","user1@email.org")).collect(Collectors.toList())),Matchers.emptyIterable());
        assertThat(withRealm(realmId, (session, realm) -> session.users().getUserByEmail(realm, "user1@email.org")), is(nullValue()));
        assertThat(withRealm(realmId, (session, realm) -> UserStoragePrivateUtil.userLocalStorage(session).getUsersCount(realm)), is(0));
    }

    @Test
    public void testValidationIsNotExecutedWithoutExplicitSyncForLocalSyncedUser() {
        // Configure validation explicitly
        withRealm(realmId, (session, realm) -> {
            UserStorageProviderModel providerModel = new UserStorageProviderModel(realm.getComponent(userFederationId));

            // disable validation
            providerModel.setCachePolicy(CacheableStorageProviderModel.CachePolicy.NO_CACHE);
            providerModel.setValidateUserListings(false); //default, but explicit for this test
            providerModel.setFullSyncPeriod(100000); //must be enabled to disable validation

            realm.updateComponent(providerModel);

            return null;
        });

        // create 1 user in LDAP
        withRealm(realmId, (session, realm) -> {
            ComponentModel ldapModel = LDAPTestUtils.getLdapProviderModel(realm);
            LDAPStorageProvider ldapFedProvider = LDAPTestUtils.getLdapProvider(session, ldapModel);
            LDAPTestUtils.addLDAPUser(ldapFedProvider, realm, "user1", "User1FN", "User1LN", "user1@email.org", "Street One 1", "121");
            return null;
        });

        assertThat(withRealm(realmId, (session, realm) -> session.users().getUserByEmail(realm, "user1@email.org")), is(notNullValue()));
        assertThat(withRealm(realmId, (session, realm) -> session.users().searchForUserStream(realm,Map.of("email","user1@email.org")).collect(Collectors.toList())),Matchers.iterableWithSize(1));

        withRealm(realmId, (session, realm) -> {
            ComponentModel ldapModel = LDAPTestUtils.getLdapProviderModel(realm);
            LDAPStorageProvider ldapFedProvider = LDAPTestUtils.getLdapProvider(session, ldapModel);
            LDAPTestUtils.removeLDAPUserByUsername(ldapFedProvider, realm, ldapFedProvider.getLdapIdentityStore().getConfig(), "user1");
            return null;
        });

        assertThat(withRealm(realmId, (session, realm) -> session.users().getUserByEmail(realm, "user1@email.org")), is(notNullValue()));
        assertThat(withRealm(realmId, (session, realm) -> session.users().searchForUserStream(realm,Map.of("email","user1@email.org")).collect(Collectors.toList())),Matchers.iterableWithSize(1));
        assertThat(withRealm(realmId, (session, realm) -> UserStoragePrivateUtil.userLocalStorage(session).getUsersCount(realm)), is(1));

        //distinguish mass operations from UserStorageManager does not work, because e.g. JpaUserProvider uses getUserById from mass-operation without forwarding the information
        //in org.keycloak.models.jpa.JpaUserProvider.searchForUserStream(org.keycloak.models.RealmModel, java.util.Map<java.lang.String,java.lang.String>, java.lang.Integer, java.lang.Integer)
        //for each user a getUserById is called --> through an interface we must not change

        //syncAllUsers does not remove users that are not present

        withRealm(realmId, (session, realm) -> {
            UserStorageProviderModel providerModel = new UserStorageProviderModel(realm.getComponent(userFederationId));
            return UserStorageSyncManager.syncAllUsers(session.getKeycloakSessionFactory(), realm.getId(), providerModel);
        });

        assertThat(withRealm(realmId, (session, realm) -> session.users().searchForUserStream(realm,Map.of("email","user1@email.org")).collect(Collectors.toList())), Matchers.emptyIterable());
        assertThat(withRealm(realmId, (session, realm) -> session.users().getUserByEmail(realm, "user1@email.org")), is(nullValue()));
        assertThat(withRealm(realmId, (session, realm) -> UserStoragePrivateUtil.userLocalStorage(session).getUsersCount(realm)), is(0));

    }
}

