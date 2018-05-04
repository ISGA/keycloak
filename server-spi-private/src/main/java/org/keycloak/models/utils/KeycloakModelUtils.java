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

package org.keycloak.models.utils;

import java.security.Key;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;
import javax.transaction.InvalidTransactionException;
import javax.transaction.SystemException;
import javax.transaction.Transaction;

import org.keycloak.broker.social.SocialIdentityProvider;
import org.keycloak.broker.social.SocialIdentityProviderFactory;
import org.keycloak.common.util.CertificateUtils;
import org.keycloak.common.util.KeyUtils;
import org.keycloak.common.util.PemUtils;
import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInput;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.AuthenticationFlowModel;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientTemplateModel;
import org.keycloak.models.Constants;
import org.keycloak.models.GroupModel;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.KeycloakSessionTask;
import org.keycloak.models.KeycloakTransaction;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleContainerModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.ScopeContainerModel;
import org.keycloak.models.UserCredentialManager;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.models.UserModel;
import org.keycloak.representations.idm.CertificateRepresentation;
import org.keycloak.storage.UserStorageProviderModel;
import org.keycloak.transaction.JtaTransactionManagerLookup;

/**
 * Set of helper methods, which are useful in various model implementations.
 *
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public final class KeycloakModelUtils {

    private KeycloakModelUtils() {
    }

    public static String generateId() {
        return UUID.randomUUID().toString();
    }

    public static byte[] generateSecret() {
        return generateSecret(32);
    }

    public static byte[] generateSecret(int bytes) {
        byte[] buf = new byte[bytes];
        new SecureRandom().nextBytes(buf);
        return buf;
    }

    public static PublicKey getPublicKey(String publicKeyPem) {
        if (publicKeyPem != null) {
            try {
                return PemUtils.decodePublicKey(publicKeyPem);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            return null;
        }
    }

    public static X509Certificate getCertificate(String cert) {
        if (cert != null) {
            try {
                return PemUtils.decodeCertificate(cert);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            return null;
        }
    }


    public static PrivateKey getPrivateKey(String privateKeyPem) {
        if (privateKeyPem != null) {
            try {
                return PemUtils.decodePrivateKey(privateKeyPem);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    public static Key getSecretKey(String secret) {
        return secret != null ? new SecretKeySpec(secret.getBytes(), "HmacSHA256") : null;
    }

    public static String getPemFromKey(Key key) {
        return PemUtils.encodeKey(key);
    }

    public static String getPemFromCertificate(X509Certificate certificate) {
        return PemUtils.encodeCertificate(certificate);
    }

    public static CertificateRepresentation generateKeyPairCertificate(String subject) {
        KeyPair keyPair = KeyUtils.generateRsaKeyPair(2048);
        X509Certificate certificate = CertificateUtils.generateV1SelfSignedCertificate(keyPair, subject);

        String privateKeyPem = PemUtils.encodeKey(keyPair.getPrivate());
        String certPem = PemUtils.encodeCertificate(certificate);

        CertificateRepresentation rep = new CertificateRepresentation();
        rep.setPrivateKey(privateKeyPem);
        rep.setCertificate(certPem);
        return rep;
    }

    public static UserCredentialModel generateSecret(ClientModel client) {
        UserCredentialModel secret = UserCredentialModel.generateSecret();
        client.setSecret(secret.getValue());
        return secret;
    }

    public static String getDefaultClientAuthenticatorType() {
        return "client-secret";
    }

    public static String generateCodeSecret() {
        return UUID.randomUUID().toString();
    }

    public static ClientModel createClient(RealmModel realm, String name) {
        ClientModel app = realm.addClient(name);
        app.setClientAuthenticatorType(getDefaultClientAuthenticatorType());
        generateSecret(app);
        app.setFullScopeAllowed(true);

        return app;
    }

    /**
     * Deep search if given role is descendant of composite role
     *
     * @param role      role to check
     * @param composite composite role
     * @param visited   set of already visited roles (used for recursion)
     * @return true if "role" is descendant of "composite"
     */
    public static boolean searchFor(RoleModel role, RoleModel composite, Set<String> visited) {
        if (visited.contains(composite.getId())) {
            return false;
        }

        visited.add(composite.getId());

        if (!composite.isComposite()) {
            return false;
        }

        Set<RoleModel> compositeRoles = composite.getComposites();
        return compositeRoles.contains(role) ||
                        compositeRoles.stream()
                                .filter(x -> x.isComposite() && searchFor(role, x, visited))
                                .findFirst()
                                .isPresent();
    }

    /**
     * Try to find user by username or email for authentication, depending on relm settings and provided input info.
     *
     * @param session
     * @param realm to find user for
     * @param username username or email of the user to find
     * @param password may be used to select correct user in case of ambiguous email-like username if duplicate emails are enabled. Can be null.
     * @return found user
     */
    public static UserModel findUserByNameOrEmail(KeycloakSession session, RealmModel realm, String username, String password) {
      
        if(password != null && password.isEmpty())
            password = null;
        
        UserModel byUsername = session.users().getUserByUsername(username, realm); 
        
        if ((byUsername == null || !byUsername.isEnabled() || password != null) && realm.isLoginWithEmailAllowed() && username.indexOf('@') != -1) {
            if (!realm.isDuplicateEmailsAllowed()) {
                UserModel byEmail = session.users().getUserByEmail(username, realm);
                if (byEmail != null && byUsername != null) {
                    return selectBetterUserAccount(session, realm, password, byUsername, byEmail);
                } else if (byUsername == null) {
                    return byEmail;
                } else {
                    return byUsername;
                }
            } else {
                // [1] optimization not to search accounts by email if we have active one found by username already and password matches to it, or if we do not know password to select any other better
                if(byUsername != null && byUsername.isEnabled() && (password == null || validatePassword(session, realm, byUsername, password))) {
                    return byUsername;
                }

                Map<String, String> params = new HashMap<>();
                params.put("email", username);
                List<UserModel> allByEmailList = session.users().searchForUser(params, realm);
                if(allByEmailList !=null && !allByEmailList.isEmpty()) {
                    Set<UserModel> allByEmail = preprocessByEmailListForUserSelection(allByEmailList, byUsername);
                    UserModel byEmailFirstDisabled = null;
                    UserModel byEmailFirstEnabled = null;
                    for (UserModel byEmail : allByEmail) {
                        if(byEmail.isEnabled()) {
                            if(password == null) {
                                // this is best choice now, byUsername was returned by optimization [1] if it is better
                                return byEmail;
                            } else if(validatePassword(session, realm, byEmail, password)) {
                                // this is best choice now, byUsername was returned by optimization [1] if it is better
                                return byEmail;
                            } else if(byEmailFirstEnabled == null) {
                                //store it for later use if no better will be found in the list 
                                byEmailFirstEnabled = byEmail;
                            }
                        } else if(byEmailFirstDisabled == null) {
                            //store it for later use if no better will be found in the list
                            byEmailFirstDisabled = byEmail;
                        }
                    }
                    
                    // no enabled user with matching password found, going to select the best one from what we have 
                    if(byUsername != null && (byUsername.isEnabled() || byEmailFirstEnabled == null)) {
                        return byUsername;
                    }
                    if(byEmailFirstEnabled != null) {
                        return byEmailFirstEnabled;
                    }
                    return byEmailFirstDisabled;
                }
            }
        }

        return byUsername;
    }

    /**
     * Preprocess list of users found by email for best user selection. Steps: 
     * <ul>
     * <li> order users by creation time to have consistent selection logic
     * <li> remove user found by username not to be processed twice
     * </ul>
     * 
     * @param allByEmail List of users found by email to preprocess
     * @param byUsername user we found by username. Can be null.
     * @return
     * 
     * @see #findUserByNameOrEmail(KeycloakSession, RealmModel, String, String)
     */
    private static Set<UserModel> preprocessByEmailListForUserSelection(List<UserModel> allByEmail, UserModel byUsername) {
        TreeSet<UserModel> ret =  new TreeSet<>(UserModelByCreatedTimestampComparator.INSTANCE);
        for(UserModel u : allByEmail) {
            if(u != null && (byUsername == null || !byUsername.getId().equals(u.getId()))) {
                ret.add(u);
            }
        }
        return ret;
    }

    /**
     * Select better account from two provided. Priority of features used for selection:
     * <ul>
     * <li> not null
     * <li> enabled
     * <li> matching password if provided
     * <li> first one passed into the method
     * </ul>
     * 
     * 
     * @param session
     * @param realm
     * @param password to be used for selection, can be null
     * @param byUsername first account to select from
     * @param byEmail second account to select from
     * @return
     */
    protected static UserModel selectBetterUserAccount(KeycloakSession session, RealmModel realm, String password, UserModel byUsername, UserModel byEmail) {
        if (byUsername.isEnabled() && !byEmail.isEnabled()) {
            return byUsername;
        } else if (!byUsername.isEnabled() && byEmail.isEnabled()) {
            return byEmail;
        } else if (byUsername.isEnabled() && byEmail.isEnabled()) {
            if(password != null) {
                if(validatePassword(session, realm, byUsername, password)) {
                    return byUsername;
                } else if(validatePassword(session, realm, byEmail, password)) {
                    return byEmail;
                } else {
                    return byUsername;
                }
            } else {
                return byUsername;
            }
        } else {
            return byUsername;
        }
    }
    
    /**
     * Validate password for the user.
     * 
     * @param session
     * @param realm
     * @param user to validate password for
     * @param password to validate
     * @return true if password is valid for given user
     * @see UserCredentialManager#isValid(RealmModel, UserModel, CredentialInput...)
     */
    private static boolean validatePassword(KeycloakSession session, RealmModel realm, UserModel user, String password) {
        if(password == null)
            return false;
        return session.userCredentialManager().isValid(realm, user, UserCredentialModel.password(password));
    }

    /**
     * Wrap given runnable job into KeycloakTransaction.
     *
     * @param factory
     * @param task
     */
    public static void runJobInTransaction(KeycloakSessionFactory factory, KeycloakSessionTask task) {
        KeycloakSession session = factory.create();
        KeycloakTransaction tx = session.getTransactionManager();
        try {
            tx.begin();
            task.run(session);

            if (tx.isActive()) {
                if (tx.getRollbackOnly()) {
                    tx.rollback();
                } else {
                    tx.commit();
                }
            }
        } catch (RuntimeException re) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw re;
        } finally {
            session.close();
        }
    }


    public static String getMasterRealmAdminApplicationClientId(String realmName) {
        return realmName + "-realm";
    }

    // USER FEDERATION RELATED STUFF


    public static UserStorageProviderModel findUserStorageProviderByName(String displayName, RealmModel realm) {
        if (displayName == null) {
            return null;
        }

        for (UserStorageProviderModel fedProvider : realm.getUserStorageProviders()) {
            if (displayName.equals(fedProvider.getName())) {
                return fedProvider;
            }
        }
        return null;
    }

    public static UserStorageProviderModel findUserStorageProviderById(String fedProviderId, RealmModel realm) {
        for (UserStorageProviderModel fedProvider : realm.getUserStorageProviders()) {
            if (fedProviderId.equals(fedProvider.getId())) {
                return fedProvider;
            }
        }
        return null;
    }

    public static ComponentModel createComponentModel(String name, String parentId, String providerId, String providerType, String... config) {
        ComponentModel mapperModel = new ComponentModel();
        mapperModel.setParentId(parentId);
        mapperModel.setName(name);
        mapperModel.setProviderId(providerId);
        mapperModel.setProviderType(providerType);

        String key = null;
        for (String configEntry : config) {
            if (key == null) {
                key = configEntry;
            } else {
                mapperModel.getConfig().add(key, configEntry);
                key = null;
            }
        }
        if (key != null) {
            throw new IllegalStateException("Invalid count of arguments for config. Maybe mistake?");
        }

        return mapperModel;
    }


    // END USER FEDERATION RELATED STUFF

    public static String toLowerCaseSafe(String str) {
        return str==null ? null : str.toLowerCase();
    }

    public static void setupOfflineTokens(RealmModel realm) {
        if (realm.getRole(Constants.OFFLINE_ACCESS_ROLE) == null) {
            RoleModel role = realm.addRole(Constants.OFFLINE_ACCESS_ROLE);
            role.setDescription("${role_offline-access}");
            role.setScopeParamRequired(true);
            realm.addDefaultRole(Constants.OFFLINE_ACCESS_ROLE);
        }
    }


    /**
     * Recursively find all AuthenticationExecutionModel from specified flow or all it's subflows
     *
     * @param realm
     * @param flow
     * @param result input should be empty list. At the end will be all executions added to this list
     */
    public static void deepFindAuthenticationExecutions(RealmModel realm, AuthenticationFlowModel flow, List<AuthenticationExecutionModel> result) {
        List<AuthenticationExecutionModel> executions = realm.getAuthenticationExecutions(flow.getId());
        for (AuthenticationExecutionModel execution : executions) {
            if (execution.isAuthenticatorFlow()) {
                AuthenticationFlowModel subFlow = realm.getAuthenticationFlowById(execution.getFlowId());
                deepFindAuthenticationExecutions(realm, subFlow, result);
            } else {
                result.add(execution);
            }
        }
    }

    public static String resolveFirstAttribute(GroupModel group, String name) {
        String value = group.getFirstAttribute(name);
        if (value != null) return value;
        if (group.getParentId() == null) return null;
        return resolveFirstAttribute(group.getParent(), name);

    }

    /**
     *
     *
     * @param user
     * @param name
     * @return
     */
    public static String resolveFirstAttribute(UserModel user, String name) {
        String value = user.getFirstAttribute(name);
        if (value != null) return value;
        for (GroupModel group : user.getGroups()) {
            value = resolveFirstAttribute(group, name);
            if (value != null) return value;
        }
        return null;

    }

    public static List<String>  resolveAttribute(GroupModel group, String name) {
        List<String> values = group.getAttribute(name);
        if (values != null && !values.isEmpty()) return values;
        if (group.getParentId() == null) return null;
        return resolveAttribute(group.getParent(), name);

    }


    public static List<String> resolveAttribute(UserModel user, String name) {
        List<String> values = user.getAttribute(name);
        if (!values.isEmpty()) return values;
        for (GroupModel group : user.getGroups()) {
            values = resolveAttribute(group, name);
            if (values != null) return values;
        }
        return Collections.emptyList();
    }


    private static GroupModel findSubGroup(String[] path, int index, GroupModel parent) {
        for (GroupModel group : parent.getSubGroups()) {
            if (group.getName().equals(path[index])) {
                if (path.length == index + 1) {
                    return group;
                }
                else {
                    if (index + 1 < path.length) {
                        GroupModel found = findSubGroup(path, index + 1, group);
                        if (found != null) return found;
                    } else {
                        return null;
                    }
                }

            }
        }
        return null;
    }

    public static GroupModel findGroupByPath(RealmModel realm, String path) {
        if (path == null) {
            return null;
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        String[] split = path.split("/");
        if (split.length == 0) return null;
        GroupModel found = null;
        for (GroupModel group : realm.getTopLevelGroups()) {
            if (group.getName().equals(split[0])) {
                if (split.length == 1) {
                    found = group;
                    break;
                }
                else {
                    if (split.length > 1) {
                        found = findSubGroup(split, 1, group);
                        if (found != null) break;
                    }
                }

            }
        }
        return found;
    }

    public static Set<RoleModel> getClientScopeMappings(ClientModel client, ScopeContainerModel container) {
        Set<RoleModel> mappings = container.getScopeMappings();
        Set<RoleModel> result = new HashSet<>();
        for (RoleModel role : mappings) {
            RoleContainerModel roleContainer = role.getContainer();
            if (roleContainer instanceof ClientModel) {
                if (client.getId().equals(((ClientModel)roleContainer).getId())) {
                    result.add(role);
                }

            }
        }
        return result;
    }

    // Used in various role mappers
    public static RoleModel getRoleFromString(RealmModel realm, String roleName) {
        String[] parsedRole = parseRole(roleName);
        RoleModel role = null;
        if (parsedRole[0] == null) {
            role = realm.getRole(parsedRole[1]);
        } else {
            ClientModel client = realm.getClientByClientId(parsedRole[0]);
            if (client != null) {
                role = client.getRole(parsedRole[1]);
            }
        }
        return role;
    }

    // Used for hardcoded role mappers
    public static String[] parseRole(String role) {
        int scopeIndex = role.lastIndexOf('.');
        if (scopeIndex > -1) {
            String appName = role.substring(0, scopeIndex);
            role = role.substring(scopeIndex + 1);
            String[] rtn = {appName, role};
            return rtn;
        } else {
            String[] rtn = {null, role};
            return rtn;

        }
    }

    /**
     * Check to see if a flow is currently in use
     *
     * @param realm
     * @param model
     * @return
     */
    public static boolean isFlowUsed(RealmModel realm, AuthenticationFlowModel model) {
        AuthenticationFlowModel realmFlow = null;

        if ((realmFlow = realm.getBrowserFlow()) != null && realmFlow.getId().equals(model.getId())) return true;
        if ((realmFlow = realm.getRegistrationFlow()) != null && realmFlow.getId().equals(model.getId())) return true;
        if ((realmFlow = realm.getClientAuthenticationFlow()) != null && realmFlow.getId().equals(model.getId())) return true;
        if ((realmFlow = realm.getDirectGrantFlow()) != null && realmFlow.getId().equals(model.getId())) return true;
        if ((realmFlow = realm.getResetCredentialsFlow()) != null && realmFlow.getId().equals(model.getId())) return true;
        if ((realmFlow = realm.getDockerAuthenticationFlow()) != null && realmFlow.getId().equals(model.getId())) return true;

        for (IdentityProviderModel idp : realm.getIdentityProviders()) {
            if (model.getId().equals(idp.getFirstBrokerLoginFlowId())) return true;
            if (model.getId().equals(idp.getPostBrokerLoginFlowId())) return true;
        }

        return false;

    }

    public static boolean isClientTemplateUsed(RealmModel realm, ClientTemplateModel template) {
        for (ClientModel client : realm.getClients()) {
            if (client.getClientTemplate() != null && client.getClientTemplate().getId().equals(template.getId())) return true;
        }
        return false;
    }

    public static ClientTemplateModel getClientTemplateByName(RealmModel realm, String templateName) {
        for (ClientTemplateModel clientTemplate : realm.getClientTemplates()) {
            if (templateName.equals(clientTemplate.getName())) {
                return clientTemplate;
            }
        }

        return null;
    }

    public static void setupAuthorizationServices(RealmModel realm) {
        for (String roleName : Constants.AUTHZ_DEFAULT_AUTHORIZATION_ROLES) {
            if (realm.getRole(roleName) == null) {
                RoleModel role = realm.addRole(roleName);
                role.setDescription("${role_" + roleName + "}");
                role.setScopeParamRequired(false);
                realm.addDefaultRole(roleName);
            }
        }
    }

    public static void suspendJtaTransaction(KeycloakSessionFactory factory, Runnable runnable) {
        JtaTransactionManagerLookup lookup = (JtaTransactionManagerLookup)factory.getProviderFactory(JtaTransactionManagerLookup.class);
        Transaction suspended = null;
        try {
            if (lookup != null) {
                if (lookup.getTransactionManager() != null) {
                    try {
                        suspended = lookup.getTransactionManager().suspend();
                    } catch (SystemException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            runnable.run();
        } finally {
            if (suspended != null) {
                try {
                    lookup.getTransactionManager().resume(suspended);
                } catch (InvalidTransactionException e) {
                    throw new RuntimeException(e);
                } catch (SystemException e) {
                    throw new RuntimeException(e);
                }
            }

        }

    }

    public static String getIdentityProviderDisplayName(KeycloakSession session, IdentityProviderModel provider) {
        String displayName = provider.getDisplayName();
        if (displayName != null && !displayName.isEmpty()) {
            return displayName;
        }

        SocialIdentityProviderFactory providerFactory = (SocialIdentityProviderFactory) session.getKeycloakSessionFactory()
                .getProviderFactory(SocialIdentityProvider.class, provider.getProviderId());
        if (providerFactory != null) {
            return providerFactory.getName();
        } else {
            return provider.getAlias();
        }
    }

}
