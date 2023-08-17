/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.testsuite.federation.kerberos;

import org.ietf.jgss.GSSCredential;
import org.junit.Before;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.models.LDAPConstants;
import org.keycloak.models.utils.ModelToRepresentation;
import org.keycloak.representations.idm.ComponentRepresentation;
import org.keycloak.testsuite.admin.ApiUtil;
import org.keycloak.storage.UserStorageProviderModel;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.testsuite.arquillian.annotation.AuthServerContainerExclude;
import org.keycloak.testsuite.arquillian.annotation.AuthServerContainerExclude.AuthServer;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.security.sasl.Sasl;
import jakarta.ws.rs.core.Response;
import java.util.Hashtable;
import java.util.Map;

/**
 * Contains just helper methods. No test methods.
 *
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public abstract class AbstractKerberosSingleUserStorageTest extends AbstractKerberosTest {

    protected ComponentRepresentation getUserStorageConfiguration(String providerName, String providerId) {
        Map<String,String> kerberosConfig = getKerberosRule().getConfig();
        MultivaluedHashMap<String, String> config = toComponentConfig(kerberosConfig);

        UserStorageProviderModel model = new UserStorageProviderModel();
        model.setLastSync(0);
        model.setChangedSyncPeriod(-1);
        model.setFullSyncPeriod(-1);
        model.setName(providerName);
        model.setPriority(0);
        model.setProviderId(providerId);
        model.setConfig(config);

        ComponentRepresentation rep = ModelToRepresentation.toRepresentationWithoutConfig(model);
        return rep;
    }


    @Before
    @Override
    public void beforeAbstractKeycloakTest() throws Exception {
        super.beforeAbstractKeycloakTest();

        ComponentRepresentation rep = getUserStorageConfiguration();
        Response resp = testRealmResource().components().add(rep);
        getCleanup().addComponentId(ApiUtil.getCreatedId(resp));
        resp.close();
    }

    protected String invokeLdap(GSSCredential gssCredential, String username) throws NamingException {
        Hashtable env = new Hashtable(11);
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, "ldap://localhost:10389");

        if (gssCredential != null) {
            env.put(Context.SECURITY_AUTHENTICATION, "GSSAPI");
            env.put(Sasl.CREDENTIALS, gssCredential);
        }

        DirContext ctx = new InitialDirContext(env);
        try {
            Attributes attrs = ctx.getAttributes("uid=" + username + ",ou=People,dc=keycloak,dc=org");
            String cn = (String) attrs.get("cn").get();
            String sn = (String) attrs.get("sn").get();
            return cn + " " + sn;
        } finally {
            ctx.close();
        }
    }


    protected void updateProviderEditMode(UserStorageProvider.EditMode editMode) {
        updateUserStorageProvider(kerberosProvider -> kerberosProvider.getConfig().putSingle(LDAPConstants.EDIT_MODE, editMode.toString()));
    }

    protected void updateProviderValidatePasswordPolicy(Boolean validatePasswordPolicy) {
        updateUserStorageProvider(kerberosProvider -> kerberosProvider.getConfig().putSingle(LDAPConstants.VALIDATE_PASSWORD_POLICY, validatePasswordPolicy.toString()));
    }

}
