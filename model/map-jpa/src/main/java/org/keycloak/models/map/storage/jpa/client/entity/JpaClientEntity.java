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
package org.keycloak.models.map.storage.jpa.client.entity;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import jakarta.persistence.Basic;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;
import org.hibernate.usertype.UserTypeLegacyBridge;
import org.keycloak.models.map.client.MapClientEntity.AbstractClientEntity;
import org.keycloak.models.map.client.MapProtocolMapperEntity;
import org.keycloak.models.map.common.DeepCloner;
import static org.keycloak.models.map.storage.jpa.Constants.CURRENT_SCHEMA_VERSION_CLIENT;

import org.keycloak.models.map.common.UuidValidator;
import org.keycloak.models.map.storage.jpa.JpaRootVersionedEntity;
import org.keycloak.models.map.storage.jpa.hibernate.jsonb.JsonbType;
import java.util.Optional;

/**
 * There are some fields marked by {@code @Column(insertable = false, updatable = false)}.
 * Those fields are automatically generated by database from json field,
 * therefore marked as non-insertable and non-updatable to instruct hibernate.
 */
@Entity
@Table(name = "kc_client",
    uniqueConstraints = {
            @UniqueConstraint(
                    columnNames = {"realmId", "clientId"}
            )
})
public class JpaClientEntity extends AbstractClientEntity implements JpaRootVersionedEntity {

    @Id
    @Column
    private UUID id;

    //used for implicit optimistic locking
    @Version
    @Column
    private int version;

    @Type(value = UserTypeLegacyBridge.class, parameters = @Parameter(name = UserTypeLegacyBridge.TYPE_NAME_PARAM_KEY, value = "jsonb"))
    @Column(columnDefinition = "jsonb")
    private final JpaClientMetadata metadata;

    @Column(insertable = false, updatable = false)
    @Basic(fetch = FetchType.LAZY)
    private Integer entityVersion;

    @Column(insertable = false, updatable = false)
    @Basic(fetch = FetchType.LAZY)
    private String realmId;

    @Column(insertable = false, updatable = false)
    @Basic(fetch = FetchType.LAZY)
    private String clientId;

    @Column(insertable = false, updatable = false)
    @Basic(fetch = FetchType.LAZY)
    private String protocol;

    @Column(insertable = false, updatable = false)
    @Basic(fetch = FetchType.LAZY)
    private Boolean enabled;

    @OneToMany(mappedBy = "root", cascade = CascadeType.PERSIST, orphanRemoval = true)
    private final Set<JpaClientAttributeEntity> attributes = new HashSet<>();

    /**
     * No-argument constructor, used by hibernate to instantiate entities.
     */
    public JpaClientEntity() {
        this.metadata = new JpaClientMetadata();
    }

    public JpaClientEntity(DeepCloner cloner) {
        this.metadata = new JpaClientMetadata(cloner);
    }

    /**
     * Used by hibernate when calling cb.construct from read(QueryParameters) method.
     * It is used to select client without metadata(json) field.
     */
    public JpaClientEntity(UUID id, int version, Integer entityVersion,  String realmId, String clientId,
            String protocol, Boolean enabled) {
        this.id = id;
        this.version = version;
        this.entityVersion = entityVersion;
        this.realmId = realmId;
        this.clientId = clientId;
        this.protocol = protocol;
        this.enabled = enabled;
        this.metadata = null;
    }

    public boolean isMetadataInitialized() {
        return metadata != null;
    }

    @Override
    public Integer getEntityVersion() {
        if (isMetadataInitialized()) return metadata.getEntityVersion();
        return entityVersion;
    }

    @Override
    public void setEntityVersion(Integer entityVersion) {
        metadata.setEntityVersion(entityVersion);
    }

    @Override
    public Integer getCurrentSchemaVersion() {
        return CURRENT_SCHEMA_VERSION_CLIENT;
    }

    @Override
    public int getVersion() {
        return version;
    }

    @Override
    public String getId() {
        return id == null ? null : id.toString();
    }

    @Override
    public void setId(String id) {
        String validatedId = UuidValidator.validateAndConvert(id);
        this.id = UUID.fromString(validatedId);
    }

    @Override
    public String getRealmId() {
        if (isMetadataInitialized()) return metadata.getRealmId();
        return realmId;
    }

    @Override
    public void setRealmId(String realmId) {
        metadata.setRealmId(realmId);
    }

    @Override
    public String getClientId() {
        if (isMetadataInitialized()) return metadata.getClientId();
        return clientId;
    }

    @Override
    public void setClientId(String clientId) {
        metadata.setClientId(clientId);
    }

    @Override
    public void setEnabled(Boolean enabled) {
        metadata.setEnabled(enabled);
    }

    @Override
    public Boolean isEnabled() {
        if (isMetadataInitialized()) return metadata.isEnabled();
        return enabled;
    }

    @Override
    public Map<String, Boolean> getClientScopes() {
        return metadata.getClientScopes();
    }

    @Override
    public void setClientScope(String id, Boolean defaultScope) {
        metadata.setClientScope(id, defaultScope);
    }

    @Override
    public void removeClientScope(String id) {
        metadata.removeClientScope(id);
    }

    @Override
    public Set<MapProtocolMapperEntity> getProtocolMappers() {
        return metadata.getProtocolMappers();
    }

    @Override
    public Optional<MapProtocolMapperEntity> getProtocolMapper(String id) {
        return metadata.getProtocolMapper(id);
    }

    @Override
    public void addProtocolMapper(MapProtocolMapperEntity mapping) {
        metadata.addProtocolMapper(mapping);
    }

    @Override
    public void removeProtocolMapper(String id) {
        metadata.removeProtocolMapper(id);
    }

    @Override
    public void addRedirectUri(String redirectUri) {
        metadata.addRedirectUri(redirectUri);
    }

    @Override
    public Set<String> getRedirectUris() {
        return metadata.getRedirectUris();
    }

    @Override
    public void removeRedirectUri(String redirectUri) {
        metadata.removeRedirectUri(redirectUri);
    }

    @Override
    public void setRedirectUris(Set<String> redirectUris) {
        metadata.setRedirectUris(redirectUris);
    }

    @Override
    public void addScopeMapping(String id) {
        metadata.addScopeMapping(id);
    }

    @Override
    public void removeScopeMapping(String id) {
        metadata.removeScopeMapping(id);
    }

    @Override
    public Collection<String> getScopeMappings() {
        return metadata.getScopeMappings();
    }

    @Override
    public void addWebOrigin(String webOrigin) {
        metadata.addWebOrigin(webOrigin);
    }

    @Override
    public Set<String> getWebOrigins() {
        return metadata.getWebOrigins();
    }

    @Override
    public void removeWebOrigin(String webOrigin) {
        metadata.removeWebOrigin(webOrigin);
    }

    @Override
    public void setWebOrigins(Set<String> webOrigins) {
        metadata.setWebOrigins(webOrigins);
    }

    @Override
    public String getAuthenticationFlowBindingOverride(String binding) {
        return metadata.getAuthenticationFlowBindingOverride(binding);
    }

    @Override
    public Map<String, String> getAuthenticationFlowBindingOverrides() {
        return metadata.getAuthenticationFlowBindingOverrides();
    }

    @Override
    public void removeAuthenticationFlowBindingOverride(String binding) {
        metadata.removeAuthenticationFlowBindingOverride(binding);
    }

    @Override
    public void setAuthenticationFlowBindingOverride(String binding, String flowId) {
        metadata.setAuthenticationFlowBindingOverride(binding, flowId);
    }

    @Override
    public String getBaseUrl() {
        return metadata.getBaseUrl();
    }

    @Override
    public void setBaseUrl(String baseUrl) {
        metadata.setBaseUrl(baseUrl);
    }

    @Override
    public String getClientAuthenticatorType() {
        return metadata.getClientAuthenticatorType();
    }

    @Override
    public void setClientAuthenticatorType(String clientAuthenticatorType) {
        metadata.setClientAuthenticatorType(clientAuthenticatorType);
    }

    @Override
    public String getDescription() {
        return metadata.getDescription();
    }

    @Override
    public void setDescription(String description) {
        metadata.setDescription(description);
    }

    @Override
    public String getManagementUrl() {
        return metadata.getManagementUrl();
    }

    @Override
    public void setManagementUrl(String managementUrl) {
        metadata.setManagementUrl(managementUrl);
    }

    @Override
    public String getName() {
        return metadata.getName();
    }

    @Override
    public void setName(String name) {
        metadata.setName(name);
    }

    @Override
    public Integer getNodeReRegistrationTimeout() {
        return metadata.getNodeReRegistrationTimeout();
    }

    @Override
    public void setNodeReRegistrationTimeout(Integer nodeReRegistrationTimeout) {
        metadata.setNodeReRegistrationTimeout(nodeReRegistrationTimeout);
    }

    @Override
    public Long getNotBefore() {
        return metadata.getNotBefore();
    }

    @Override
    public void setNotBefore(Long notBefore) {
        metadata.setNotBefore(notBefore);
    }

    @Override
    public String getProtocol() {
        if (isMetadataInitialized()) return metadata.getProtocol();
        return protocol;
    }

    @Override
    public void setProtocol(String protocol) {
        metadata.setProtocol(protocol);
    }

    @Override
    public String getRegistrationToken() {
        return metadata.getRegistrationToken();
    }

    @Override
    public void setRegistrationToken(String registrationToken) {
        metadata.setRegistrationToken(registrationToken);
    }

    @Override
    public String getRootUrl() {
        return metadata.getRootUrl();
    }

    @Override
    public void setRootUrl(String rootUrl) {
        metadata.setRootUrl(rootUrl);
    }

    @Override
    public Set<String> getScope() {
        return metadata.getScope();
    }

    @Override
    public void setScope(Set<String> scope) {
        metadata.setScope(scope);
    }

    @Override
    public String getSecret() {
        return metadata.getSecret();
    }

    @Override
    public void setSecret(String secret) {
        metadata.setSecret(secret);
    }

    @Override
    public Boolean isAlwaysDisplayInConsole() {
        return metadata.isAlwaysDisplayInConsole();
    }

    @Override
    public void setAlwaysDisplayInConsole(Boolean alwaysDisplayInConsole) {
        metadata.setAlwaysDisplayInConsole(alwaysDisplayInConsole);
    }

    @Override
    public Boolean isBearerOnly() {
        return metadata.isBearerOnly();
    }

    @Override
    public void setBearerOnly(Boolean bearerOnly) {
        metadata.setBearerOnly(bearerOnly);
    }

    @Override
    public Boolean isConsentRequired() {
        return metadata.isConsentRequired();
    }

    @Override
    public void setConsentRequired(Boolean consentRequired) {
        metadata.setConsentRequired(consentRequired);
    }

    @Override
    public Boolean isDirectAccessGrantsEnabled() {
        return metadata.isDirectAccessGrantsEnabled();
    }

    @Override
    public void setDirectAccessGrantsEnabled(Boolean directAccessGrantsEnabled) {
        metadata.setDirectAccessGrantsEnabled(directAccessGrantsEnabled);
    }

    @Override
    public Boolean isFrontchannelLogout() {
        return metadata.isFrontchannelLogout();
    }

    @Override
    public void setFrontchannelLogout(Boolean frontchannelLogout) {
        metadata.setFrontchannelLogout(frontchannelLogout);
    }

    @Override
    public Boolean isFullScopeAllowed() {
        return metadata.isFullScopeAllowed();
    }

    @Override
    public void setFullScopeAllowed(Boolean fullScopeAllowed) {
        metadata.setFullScopeAllowed(fullScopeAllowed);
    }

    @Override
    public Boolean isImplicitFlowEnabled() {
        return metadata.isImplicitFlowEnabled();
    }

    @Override
    public void setImplicitFlowEnabled(Boolean implicitFlowEnabled) {
        metadata.setImplicitFlowEnabled(implicitFlowEnabled);
    }

    @Override
    public Boolean isPublicClient() {
        return metadata.isPublicClient();
    }

    @Override
    public void setPublicClient(Boolean publicClient) {
        metadata.setPublicClient(publicClient);
    }

    @Override
    public Boolean isServiceAccountsEnabled() {
        return metadata.isServiceAccountsEnabled();
    }

    @Override
    public void setServiceAccountsEnabled(Boolean serviceAccountsEnabled) {
        metadata.setServiceAccountsEnabled(serviceAccountsEnabled);
    }

    @Override
    public Boolean isStandardFlowEnabled() {
        return metadata.isStandardFlowEnabled();
    }

    @Override
    public void setStandardFlowEnabled(Boolean standardFlowEnabled) {
        metadata.setStandardFlowEnabled(standardFlowEnabled);
    }

    @Override
    public Boolean isSurrogateAuthRequired() {
        return metadata.isSurrogateAuthRequired();
    }

    @Override
    public void setSurrogateAuthRequired(Boolean surrogateAuthRequired) {
        metadata.setSurrogateAuthRequired(surrogateAuthRequired);
    }

    @Override
    public void removeAttribute(String name) {
        attributes.removeIf(attr -> Objects.equals(attr.getName(), name));
    }

    @Override
    public void setAttribute(String name, List<String> values) {
        removeAttribute(name);
        for (String value : values) {
            JpaClientAttributeEntity attribute = new JpaClientAttributeEntity(this, name, value);
            attributes.add(attribute);
        }
    }

    @Override
    public List<String> getAttribute(String name) {
        return attributes.stream()
                .filter(a -> Objects.equals(a.getName(), name))
                .map(JpaClientAttributeEntity::getValue)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, List<String>> getAttributes() {
        Map<String, List<String>> result = new HashMap<>();
        for (JpaClientAttributeEntity attribute : attributes) {
            List<String> values = result.getOrDefault(attribute.getName(), new LinkedList<>());
            values.add(attribute.getValue());
            result.put(attribute.getName(), values);
        }
        return result;
    }

    @Override
    public void setAttributes(Map<String, List<String>> attributes) {
        this.attributes.clear();
        if (attributes != null) {
            for (Map.Entry<String, List<String>> attrEntry : attributes.entrySet()) {
                setAttribute(attrEntry.getKey(), attrEntry.getValue());
            }
        }
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof JpaClientEntity)) return false;
        return Objects.equals(getId(), ((JpaClientEntity) obj).getId());
    }
}
