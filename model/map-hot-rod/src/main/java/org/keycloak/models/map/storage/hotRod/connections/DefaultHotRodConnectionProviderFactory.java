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
package org.keycloak.models.map.storage.hotRod.connections;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.RemoteCacheManagerAdmin;
import org.infinispan.client.hotrod.configuration.ClientIntelligence;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.configuration.NearCacheMode;
import org.infinispan.commons.marshall.ProtoStreamMarshaller;
import org.infinispan.protostream.GeneratedSchema;
import org.infinispan.query.remote.client.ProtobufMetadataManagerConstants;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.map.storage.hotRod.locking.HotRodLocksUtils;
import org.keycloak.models.map.storage.hotRod.common.HotRodEntityDescriptor;
import org.keycloak.models.map.storage.hotRod.common.CommonPrimitivesProtoSchemaInitializer;
import org.keycloak.models.map.storage.hotRod.common.HotRodVersionUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.keycloak.models.map.storage.hotRod.common.AutogeneratedHotRodDescriptors.ENTITY_DESCRIPTOR_MAP;
import static org.keycloak.models.map.storage.hotRod.common.HotRodVersionUtils.adjacentVersions;
import static org.keycloak.models.map.storage.hotRod.common.HotRodVersionUtils.isVersion2NewerThanVersion1;

/**
 * @author <a href="mailto:mkanis@redhat.com">Martin Kanis</a>
 */
public class DefaultHotRodConnectionProviderFactory implements HotRodConnectionProviderFactory {

    public static final String PROVIDER_ID = "default";
    public static final String HOT_ROD_LOCKS_CACHE_NAME = "locks";
    private static final String HOT_ROD_INIT_LOCK_NAME = "HOT_ROD_INIT_LOCK";
    private static final Logger LOG = Logger.getLogger(DefaultHotRodConnectionProviderFactory.class);

    private org.keycloak.Config.Scope config;

    private volatile RemoteCacheManager remoteCacheManager;

    @Override
    public HotRodConnectionProvider create(KeycloakSession session) {
        if (remoteCacheManager == null) {
            synchronized (this) {
                if (remoteCacheManager == null) {
                    lazyInit();
                }
            }
        }
        return new DefaultHotRodConnectionProvider(remoteCacheManager);
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {

    }

    @Override
    public void close() {
        if (remoteCacheManager != null) {
            remoteCacheManager.close();
            remoteCacheManager = null;
        }
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public void init(org.keycloak.Config.Scope config) {
        this.config = config;
    }

    public void lazyInit() {
        LOG.debugf("Initializing HotRod client connection to Infinispan server.");
        ConfigurationBuilder remoteBuilder = new ConfigurationBuilder();
        remoteBuilder.addServer()
                .host(config.get("host", "localhost"))
                .port(config.getInt("port", 11222))
                .clientIntelligence(ClientIntelligence.HASH_DISTRIBUTION_AWARE)
                .marshaller(new ProtoStreamMarshaller());

        if (config.getBoolean("enableSecurity", true)) {
            remoteBuilder.security()
                    .authentication()
                    .saslMechanism("SCRAM-SHA-512")
                    .username(config.get("username", "admin"))
                    .password(config.get("password", "admin"))
                    .realm(config.get("realm", "default"));
        }

        LOG.debugf("Configuring remote caches.");
        configureRemoteCaches(remoteBuilder);

        remoteBuilder.addContextInitializer(CommonPrimitivesProtoSchemaInitializer.INSTANCE);
        ENTITY_DESCRIPTOR_MAP.values().stream().map(HotRodEntityDescriptor::getProtoSchema).forEach(remoteBuilder::addContextInitializer);

        // Configure settings necessary for locking
        configureLocking(remoteBuilder);

        remoteCacheManager = new RemoteCacheManager(remoteBuilder.build());

        // Acquire initial phase lock to avoid concurrent schema update
        RemoteCache<String, String> locksCache = remoteCacheManager.getCache(HOT_ROD_LOCKS_CACHE_NAME);
        try {
            HotRodLocksUtils.repeatPutIfAbsent(locksCache, HOT_ROD_INIT_LOCK_NAME, Duration.ofMillis(900), 50);

            Set<String> remoteCaches = ENTITY_DESCRIPTOR_MAP.values().stream()
                    .map(HotRodEntityDescriptor::getCacheName).collect(Collectors.toSet());

            LOG.debugf("Uploading proto schema to Infinispan server.");
            registerSchemata();

            String reindexCaches = config.get("reindexCaches", null);
            RemoteCacheManagerAdmin administration = remoteCacheManager.administration();
            if (reindexCaches != null && reindexCaches.equals("all")) {
                LOG.infof("Reindexing all caches. This can take a long time to complete. While the rebuild operation is in progress, queries might return fewer results.");
                remoteCaches.stream()
                        .peek(remoteCacheManager::getCache) // access the caches to force their creation, otherwise reindexing fails if cache doesn't exist
                        .forEach(administration::reindexCache);
            } else if (reindexCaches != null && !reindexCaches.isEmpty()) {
                Arrays.stream(reindexCaches.split(","))
                        .map(String::trim)
                        .filter(e -> !e.isEmpty())
                        .filter(remoteCaches::contains)
                        .peek(cacheName -> LOG.infof("Reindexing %s cache. This can take a long time to complete. While the rebuild operation is in progress, queries might return fewer results.", cacheName))
                        .peek(remoteCacheManager::getCache) // access the caches to force their creation, otherwise reindexing fails if cache doesn't exist
                        .forEach(administration::reindexCache);
            }

            LOG.infof("HotRod client configuration was successful.");
        } finally {
            if (!HotRodLocksUtils.removeWithInstanceIdentifier(locksCache, HOT_ROD_INIT_LOCK_NAME)) {
                throw new RuntimeException("Cannot release HotRod init lock");
            }
        }
    }

    private void configureLocking(ConfigurationBuilder builder) {
        builder.remoteCache(HOT_ROD_LOCKS_CACHE_NAME)
                .configurationURI(getCacheConfigUri(HOT_ROD_LOCKS_CACHE_NAME));
    }

    private void registerSchemata() {
        final RemoteCache<String, String> protoMetadataCache = remoteCacheManager.getCache(ProtobufMetadataManagerConstants.PROTOBUF_METADATA_CACHE_NAME);
        Set<String> cachesForIndexUpdate = new HashSet<>();

        // First add Common classes definitions
        GeneratedSchema commonSchema = CommonPrimitivesProtoSchemaInitializer.INSTANCE;
        String currentProtoFile = protoMetadataCache.get(commonSchema.getProtoFileName());
        // there is no proto file deployed on the server
        if (currentProtoFile == null) {
            protoMetadataCache.put(commonSchema.getProtoFileName(), commonSchema.getProtoFile());
        }
        else if (isUpdateNeeded(commonSchema.getProtoFileName(), CommonPrimitivesProtoSchemaInitializer.COMMON_PRIMITIVES_VERSION, currentProtoFile)) {
            protoMetadataCache.put(commonSchema.getProtoFileName(), commonSchema.getProtoFile());

            // if there is a change in common primitives, update all caches as we don't track in what areas are these common primitives used
            cachesForIndexUpdate = ENTITY_DESCRIPTOR_MAP.values().stream().map(HotRodEntityDescriptor::getCacheName).collect(Collectors.toSet());
        }

        // Add schema for each entity descriptor
        for (HotRodEntityDescriptor<?,?> descriptor : ENTITY_DESCRIPTOR_MAP.values()) {
            GeneratedSchema schema = descriptor.getProtoSchema();
            currentProtoFile = protoMetadataCache.get(schema.getProtoFileName());
            // there is no proto file deployed on the server
            if (currentProtoFile == null) {
                protoMetadataCache.put(schema.getProtoFileName(), schema.getProtoFile());
            }
            else if (isUpdateNeeded(schema.getProtoFileName(), descriptor.getCurrentVersion(), currentProtoFile)) {
                protoMetadataCache.put(schema.getProtoFileName(), schema.getProtoFile());
                cachesForIndexUpdate.add(descriptor.getCacheName());
            }
        }

        String errors = protoMetadataCache.get(ProtobufMetadataManagerConstants.ERRORS_KEY_SUFFIX);
        if (errors != null) {
            for (String errorFile : errors.split("\n")) {
                LOG.errorf("\nThere was an error in proto file: %s\n" +
                        "Error message: %s\n" +
                        "Current proto schema: %s",
                        errorFile,
                        protoMetadataCache.get(errorFile + ProtobufMetadataManagerConstants.ERRORS_KEY_SUFFIX),
                        protoMetadataCache.get(errorFile));
            }

            throw new IllegalStateException("Some Protobuf schema files contain errors: " + errors);
        }

        // update index schema for caches, where a proto schema was updated
        RemoteCacheManagerAdmin administration = remoteCacheManager.administration();
        cachesForIndexUpdate.forEach(administration::updateIndexSchema);
    }

    /**
     * Decides whether the schema should be updated
     * @return true if schema in the server is obsolete or doesn't exist, false otherwise
     * @throws IllegalStateException when schema in the server is older than one version behind
     */
    private boolean isUpdateNeeded(String protoFileName, int currentSchemaVersion, String currentProtoFileDeployed) {
        // If there is no proto file deployed in the server return true
        if (currentProtoFileDeployed == null) return true;

        // Parse currently deployed schema version
        Integer deployedSchemaVersion = HotRodVersionUtils.parseSchemaVersionFromProtoFile(currentProtoFileDeployed);
        if (deployedSchemaVersion == null) {
            LOG.errorf("Schema %s does not contain expected schema-version definition:\n%s", protoFileName, currentProtoFileDeployed);
            throw new IllegalStateException("Deployed schema " + protoFileName + " does not contain expected schema-version definition. See log for more details.");
        }

        if (currentSchemaVersion != deployedSchemaVersion && !adjacentVersions(deployedSchemaVersion, currentSchemaVersion)) {
            // Infinispan server contains schema that is older than {current_version - 1}, upgrade needs to be done sequentially
            throw new IllegalStateException("Infinispan server contains too old schema version for " + protoFileName);
        }

        return isVersion2NewerThanVersion1(deployedSchemaVersion, currentSchemaVersion);
    }

    private void configureRemoteCaches(ConfigurationBuilder builder) {
        Consumer<String> configurator = configurationBuilderConsumer(builder);

        ENTITY_DESCRIPTOR_MAP.values().stream()
                .map(HotRodEntityDescriptor::getCacheName)
                .distinct()
                .forEach(configurator);
    }

    private static URI getCacheConfigUri(String cacheName) {
        try {
            return DefaultHotRodConnectionProviderFactory.class.getClassLoader().getResource("config/" + cacheName + "-cache-config.xml").toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException("Cannot read the cache configuration for cache + " + cacheName, e);
        }
    }

    private Consumer<String> configurationBuilderConsumer(ConfigurationBuilder builder) {
        return cacheName -> {
            LOG.debugf("Configuring cache %s", cacheName);
            builder.remoteCache(cacheName)
                    .configurationURI(getCacheConfigUri(cacheName))
                    .nearCacheMode(config.scope(cacheName).getBoolean("nearCacheEnabled", config.getBoolean("nearCacheEnabled", true)) ? NearCacheMode.INVALIDATED : NearCacheMode.DISABLED)
                    .nearCacheMaxEntries(config.scope(cacheName).getInt("nearCacheMaxEntries", config.getInt("nearCacheMaxEntries", 10000)))
                    .nearCacheUseBloomFilter(config.scope(cacheName).getBoolean("nearCacheBloomFilter", config.getBoolean("nearCacheBloomFilter", false)));
        };
    }
}
