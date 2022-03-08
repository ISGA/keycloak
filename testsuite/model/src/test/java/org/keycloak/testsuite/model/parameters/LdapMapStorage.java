/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
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
package org.keycloak.testsuite.model.parameters;

import com.google.common.collect.ImmutableSet;
import org.junit.rules.ExternalResource;
import org.jboss.logging.Logger;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.keycloak.authorization.store.StoreFactorySpi;
import org.keycloak.events.EventStoreSpi;
import org.keycloak.models.ActionTokenStoreSpi;
import org.keycloak.models.DeploymentStateSpi;
import org.keycloak.models.LDAPConstants;
import org.keycloak.models.SingleUseObjectSpi;
import org.keycloak.models.UserLoginFailureSpi;
import org.keycloak.models.UserSessionSpi;
import org.keycloak.models.map.storage.MapStorageSpi;
import org.keycloak.models.map.storage.chm.ConcurrentHashMapStorageProviderFactory;
import org.keycloak.models.map.storage.ldap.LdapMapStorageProviderFactory;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.provider.Spi;
import org.keycloak.testsuite.model.Config;
import org.keycloak.testsuite.model.KeycloakModelParameters;
import org.keycloak.testsuite.util.LDAPRule;
import org.keycloak.util.ldap.LDAPEmbeddedServer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import javax.naming.NamingException;

/**
 * @author Alexander Schwartz
 */
public class LdapMapStorage extends KeycloakModelParameters {

    private static final Logger LOG = Logger.getLogger(LdapMapStorage.class.getName());

    static final Set<Class<? extends Spi>> ALLOWED_SPIS = ImmutableSet.<Class<? extends Spi>>builder()
            .build();

    static final Set<Class<? extends ProviderFactory>> ALLOWED_FACTORIES = ImmutableSet.<Class<? extends ProviderFactory>>builder()
            .add(ConcurrentHashMapStorageProviderFactory.class)
            .add(LdapMapStorageProviderFactory.class)
            .build();

    private final LDAPRule ldapRule = new LDAPRule();

    /**
     * Temporary folder for concurrent hashmap storage.
     * The classic {@link org.junit.rules.TemporaryFolder} won't work here, as we'll need the folder already
     * in {@link #updateConfig(Config)} that is executed too early for that rule as it hasn't been initialized there, yet.
     */
    private final Path temporaryFolder;

    public LdapMapStorage() {
        super(ALLOWED_SPIS, ALLOWED_FACTORIES);
        try {
            temporaryFolder = Files.createTempDirectory(Paths.get("target"), "mapstorage-");
        } catch (IOException e) {
            throw new RuntimeException("can't create temporary folder", e);
        }
    }

    @Override
    public void updateConfig(Config cf) {
        if (!temporaryFolder.toFile().exists()) {
            // temporary folder has been cleaned up after previous test
            if (!temporaryFolder.toFile().mkdir()) {
                throw new RuntimeException("can't create folder " + temporaryFolder);
            }
        }

        cf.spi(MapStorageSpi.NAME)
                .provider(ConcurrentHashMapStorageProviderFactory.PROVIDER_ID)
                .config("dir", temporaryFolder.toString());

        cf.spi(MapStorageSpi.NAME)
                .provider(LdapMapStorageProviderFactory.PROVIDER_ID)
                .config("vendor", "other")
                .config("usernameLDAPAttribute", "uid")
                .config("rdnLDAPAttribute", "uid")
                .config("uuidLDAPAttribute", "entryUUID")
                .config("userObjectClasses", "inetOrgPerson, organizationalPerson")
                .config("connectionUrl", "ldap://localhost:10389")
                .config("usersDn", "ou=People,dc=keycloak,dc=org")
                .config("bindDn", "uid=admin,ou=system")
                .config("bindCredential", "secret")
                .config("roles.realm.dn", "ou=RealmRoles,dc=keycloak,dc=org")
                .config("roles.client.dn", "ou={0},dc=keycloak,dc=org")
                .config("roles.common.dn", "dc=keycloak,dc=org") // this is the top DN that finds both client and realm roles
                .config("membership.ldap.attribute", "member")
                .config("role.name.ldap.attribute", "cn")
                .config("role.object.classes", "groupOfNames")
                .config("role.attributes", "ou")
                .config("mode", "LDAP_ONLY")
                .config("use.realm.roles.mapping", "true")
                .config(LDAPConstants.CONNECTION_POOLING, "true");

        cf.spi("client").config("map.storage.provider", ConcurrentHashMapStorageProviderFactory.PROVIDER_ID)
                .spi("clientScope").config("map.storage.provider", ConcurrentHashMapStorageProviderFactory.PROVIDER_ID)
                .spi("group").config("map.storage.provider", ConcurrentHashMapStorageProviderFactory.PROVIDER_ID)
                .spi("realm").config("map.storage.provider", ConcurrentHashMapStorageProviderFactory.PROVIDER_ID)
                .spi("role").config("map.storage.provider", LdapMapStorageProviderFactory.PROVIDER_ID)
                .spi(DeploymentStateSpi.NAME).config("map.storage.provider", ConcurrentHashMapStorageProviderFactory.PROVIDER_ID)
                .spi(StoreFactorySpi.NAME).config("map.storage.provider", ConcurrentHashMapStorageProviderFactory.PROVIDER_ID)
                .spi("user").config("map.storage.provider", LdapMapStorageProviderFactory.PROVIDER_ID)
                .spi(UserSessionSpi.NAME).config("map.storage-user-sessions.provider", ConcurrentHashMapStorageProviderFactory.PROVIDER_ID)
                .spi(UserSessionSpi.NAME).config("map.storage-client-sessions.provider", ConcurrentHashMapStorageProviderFactory.PROVIDER_ID)
                .spi(UserLoginFailureSpi.NAME).config("map.storage.provider", ConcurrentHashMapStorageProviderFactory.PROVIDER_ID)
                .spi("authorizationPersister").config("map.storage.provider", ConcurrentHashMapStorageProviderFactory.PROVIDER_ID)
                .spi("authenticationSessions").config("map.storage.provider", ConcurrentHashMapStorageProviderFactory.PROVIDER_ID)
                .spi(EventStoreSpi.NAME).config("map.storage-admin-events.provider", ConcurrentHashMapStorageProviderFactory.PROVIDER_ID)
                .spi(EventStoreSpi.NAME).config("map.storage-auth-events.provider", ConcurrentHashMapStorageProviderFactory.PROVIDER_ID)
                .spi(ActionTokenStoreSpi.NAME).config("map.storage.provider", ConcurrentHashMapStorageProviderFactory.PROVIDER_ID)
                .spi(SingleUseObjectSpi.NAME).config("map.storage.provider", ConcurrentHashMapStorageProviderFactory.PROVIDER_ID);

    }

    static {
        System.setProperty(LDAPEmbeddedServer.PROPERTY_ENABLE_SSL, "false");
    }

    @Override
    public Statement classRule(Statement base, Description description) {
        base = ldapRule.apply(base, description);

        // The folder cleanup is a classRule, as otherwise the @After methods might not be able to clean up realm information
        // as the rule will run before the @After steps.
        base = new ExternalResource() {
            @Override
            protected void before() throws Throwable {
                if (!temporaryFolder.toFile().exists()) {
                    // temporary folder has been cleaned up after previous test
                    if (!temporaryFolder.toFile().mkdir()) {
                        throw new RuntimeException("can't create folder " + temporaryFolder);
                    }
                }
            }

            @Override
            protected void after() {
                if (temporaryFolder.toFile().exists()) {
                    File[] files = temporaryFolder.toFile().listFiles();
                    if (files != null) {
                        for (File file : files) {
                            if (!file.delete()) {
                                throw new RuntimeException("can't delete file " + file);
                            }
                        }
                    }
                    if (!temporaryFolder.toFile().delete()) {
                        throw new RuntimeException("can't delete folder " + temporaryFolder);
                    }
                }
            }
        }.apply(base, description);

        return base;
    }

}
