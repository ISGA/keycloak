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

package org.keycloak.quarkus.deployment;

import io.quarkus.agroal.runtime.health.DataSourceHealthCheck;
import io.quarkus.agroal.spi.JdbcDataSourceBuildItem;
import io.quarkus.agroal.spi.JdbcDriverBuildItem;
import io.quarkus.arc.deployment.AnnotationsTransformerBuildItem;
import io.quarkus.arc.deployment.BuildTimeConditionBuildItem;
import io.quarkus.arc.processor.AnnotationsTransformer;
import io.quarkus.bootstrap.logging.InitialConfigurator;
import io.quarkus.datasource.deployment.spi.DevServicesDatasourceResultBuildItem;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Produce;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.StaticInitConfigBuilderBuildItem;
import io.quarkus.hibernate.orm.deployment.HibernateOrmConfig;
import io.quarkus.hibernate.orm.deployment.PersistenceXmlDescriptorBuildItem;
import io.quarkus.hibernate.orm.deployment.integration.HibernateOrmIntegrationRuntimeConfiguredBuildItem;
import io.quarkus.hibernate.orm.deployment.spi.AdditionalJpaModelBuildItem;
import io.quarkus.resteasy.reactive.server.spi.MethodScannerBuildItem;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.runtime.configuration.ProfileManager;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import io.quarkus.resteasy.reactive.spi.IgnoreStackMixingBuildItem;
import io.smallrye.config.ConfigValue;
import org.eclipse.microprofile.health.Readiness;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.jpa.boot.internal.ParsedPersistenceXmlDescriptor;
import org.hibernate.jpa.boot.internal.PersistenceXmlParser;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.model.HandlerChainCustomizer;
import org.jboss.resteasy.reactive.server.processor.scanning.MethodScanner;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.keycloak.Config;
import org.keycloak.authentication.AuthenticatorSpi;
import org.keycloak.authentication.authenticators.browser.DeployedScriptAuthenticatorFactory;
import org.keycloak.authorization.policy.provider.PolicySpi;
import org.keycloak.authorization.policy.provider.js.DeployedScriptPolicyFactory;
import org.keycloak.common.Profile;
import org.keycloak.common.crypto.FipsMode;
import org.keycloak.common.util.StreamUtil;
import org.keycloak.config.DatabaseOptions;
import org.keycloak.config.SecurityOptions;
import org.keycloak.connections.jpa.DefaultJpaConnectionProviderFactory;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.connections.jpa.JpaConnectionSpi;
import org.keycloak.connections.jpa.updater.liquibase.LiquibaseJpaUpdaterProviderFactory;
import org.keycloak.connections.jpa.updater.liquibase.conn.DefaultLiquibaseConnectionProvider;
import org.keycloak.policy.BlacklistPasswordPolicyProviderFactory;
import org.keycloak.protocol.ProtocolMapperSpi;
import org.keycloak.protocol.oidc.mappers.DeployedScriptOIDCProtocolMapper;
import org.keycloak.protocol.saml.mappers.DeployedScriptSAMLProtocolMapper;
import org.keycloak.provider.EnvironmentDependentProviderFactory;
import org.keycloak.provider.Provider;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.provider.ProviderManager;
import org.keycloak.provider.Spi;
import org.keycloak.quarkus.runtime.Environment;
import org.keycloak.quarkus.runtime.KeycloakRecorder;
import org.keycloak.quarkus.runtime.configuration.Configuration;
import org.keycloak.quarkus.runtime.configuration.KeycloakConfigSourceProvider;
import org.keycloak.quarkus.runtime.configuration.MicroProfileConfigProvider;
import org.keycloak.quarkus.runtime.configuration.PersistedConfigSource;
import org.keycloak.quarkus.runtime.configuration.QuarkusPropertiesConfigSource;
import org.keycloak.quarkus.runtime.configuration.mappers.PropertyMapper;
import org.keycloak.quarkus.runtime.configuration.mappers.PropertyMappers;
import org.keycloak.quarkus.runtime.integration.resteasy.KeycloakHandlerChainCustomizer;
import org.keycloak.quarkus.runtime.integration.web.NotFoundHandler;
import org.keycloak.quarkus.runtime.services.health.KeycloakReadyHealthCheck;
import org.keycloak.quarkus.runtime.storage.database.jpa.NamedJpaConnectionProviderFactory;
import org.keycloak.quarkus.runtime.themes.FlatClasspathThemeResourceProviderFactory;
import org.keycloak.representations.provider.ScriptProviderDescriptor;
import org.keycloak.representations.provider.ScriptProviderMetadata;
import org.keycloak.representations.userprofile.config.UPConfig;
import org.keycloak.services.ServicesLogger;
import org.keycloak.services.resources.KeycloakApplication;
import org.keycloak.theme.ClasspathThemeProviderFactory;
import org.keycloak.theme.ClasspathThemeResourceProviderFactory;
import org.keycloak.theme.FolderThemeProviderFactory;
import org.keycloak.theme.JarThemeProviderFactory;
import org.keycloak.theme.ThemeResourceSpi;
import org.keycloak.transaction.JBossJtaTransactionManagerLookup;
import org.keycloak.url.DefaultHostnameProviderFactory;
import org.keycloak.url.FixedHostnameProviderFactory;
import org.keycloak.url.RequestHostnameProviderFactory;
import org.keycloak.userprofile.config.UPConfigUtils;
import org.keycloak.util.JsonSerialization;
import org.keycloak.vault.FilesKeystoreVaultProviderFactory;
import org.keycloak.vault.FilesPlainTextVaultProviderFactory;

import jakarta.persistence.Entity;
import jakarta.persistence.spi.PersistenceUnitTransactionType;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Handler;

import static org.keycloak.connections.jpa.util.JpaUtils.loadSpecificNamedQueries;
import static org.keycloak.quarkus.runtime.Environment.getCurrentOrCreateFeatureProfile;
import static org.keycloak.quarkus.runtime.Environment.getProviderFiles;
import static org.keycloak.quarkus.runtime.KeycloakRecorder.DEFAULT_HEALTH_ENDPOINT;
import static org.keycloak.quarkus.runtime.KeycloakRecorder.DEFAULT_METRICS_ENDPOINT;
import static org.keycloak.quarkus.runtime.Providers.getProviderManager;
import static org.keycloak.quarkus.runtime.configuration.Configuration.getOptionalKcValue;
import static org.keycloak.quarkus.runtime.configuration.Configuration.getOptionalValue;
import static org.keycloak.quarkus.runtime.configuration.Configuration.getPropertyNames;
import static org.keycloak.quarkus.runtime.configuration.MicroProfileConfigProvider.NS_KEYCLOAK_PREFIX;
import static org.keycloak.quarkus.runtime.configuration.MicroProfileConfigProvider.NS_QUARKUS;
import static org.keycloak.quarkus.runtime.configuration.QuarkusPropertiesConfigSource.QUARKUS_PROPERTY_ENABLED;
import static org.keycloak.quarkus.runtime.storage.legacy.database.QuarkusJpaConnectionProviderFactory.QUERY_PROPERTY_PREFIX;
import static org.keycloak.representations.provider.ScriptProviderDescriptor.AUTHENTICATORS;
import static org.keycloak.representations.provider.ScriptProviderDescriptor.MAPPERS;
import static org.keycloak.representations.provider.ScriptProviderDescriptor.POLICIES;
import static org.keycloak.representations.provider.ScriptProviderDescriptor.SAML_MAPPERS;
import static org.keycloak.theme.ClasspathThemeProviderFactory.KEYCLOAK_THEMES_JSON;

class KeycloakProcessor {

    private static final Logger logger = Logger.getLogger(KeycloakProcessor.class);

    private static final String JAR_FILE_SEPARATOR = "!/";
    private static final Map<String, Function<ScriptProviderMetadata, ProviderFactory>> DEPLOYEABLE_SCRIPT_PROVIDERS = new HashMap<>();
    private static final String KEYCLOAK_SCRIPTS_JSON_PATH = "META-INF/keycloak-scripts.json";

    private static final List<Class<? extends ProviderFactory>> IGNORED_PROVIDER_FACTORY = List.of(
            JBossJtaTransactionManagerLookup.class,
            DefaultJpaConnectionProviderFactory.class,
            DefaultLiquibaseConnectionProvider.class,
            FolderThemeProviderFactory.class,
            LiquibaseJpaUpdaterProviderFactory.class,
            DefaultHostnameProviderFactory.class,
            FixedHostnameProviderFactory.class,
            RequestHostnameProviderFactory.class,
            FilesKeystoreVaultProviderFactory.class,
            FilesPlainTextVaultProviderFactory.class,
            BlacklistPasswordPolicyProviderFactory.class,
            ClasspathThemeResourceProviderFactory.class,
            JarThemeProviderFactory.class);

    static {
        DEPLOYEABLE_SCRIPT_PROVIDERS.put(AUTHENTICATORS, KeycloakProcessor::registerScriptAuthenticator);
        DEPLOYEABLE_SCRIPT_PROVIDERS.put(POLICIES, KeycloakProcessor::registerScriptPolicy);
        DEPLOYEABLE_SCRIPT_PROVIDERS.put(MAPPERS, KeycloakProcessor::registerScriptMapper);
        DEPLOYEABLE_SCRIPT_PROVIDERS.put(SAML_MAPPERS, KeycloakProcessor::registerSAMLScriptMapper);
    }

    private static ProviderFactory registerScriptAuthenticator(ScriptProviderMetadata metadata) {
        return new DeployedScriptAuthenticatorFactory(metadata);
    }

    private static ProviderFactory registerScriptPolicy(ScriptProviderMetadata metadata) {
        return new DeployedScriptPolicyFactory(metadata);
    }

    private static ProviderFactory registerScriptMapper(ScriptProviderMetadata metadata) {
        return new DeployedScriptOIDCProtocolMapper(metadata);
    }

    private static ProviderFactory registerSAMLScriptMapper(ScriptProviderMetadata metadata) {
        return new DeployedScriptSAMLProtocolMapper(metadata);
    }

    @BuildStep
    IgnoreStackMixingBuildItem getIgnoreStackMixing() {
        return new IgnoreStackMixingBuildItem();
    }

    @BuildStep
    FeatureBuildItem getFeature() {
        return new FeatureBuildItem("keycloak");
    }

    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep
    @Produce(ConfigBuildItem.class)
    void initConfig(KeycloakRecorder recorder) {
        Config.init(new MicroProfileConfigProvider());
        recorder.initConfig();
    }

    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep
    @Consume(ConfigBuildItem.class)
    @Produce(ProfileBuildItem.class)
    void configureProfile(KeycloakRecorder recorder) {
        Profile profile = getCurrentOrCreateFeatureProfile();

        // record the features so that they are not calculated again at runtime
        recorder.configureProfile(profile.getName(), profile.getFeatures());
    }

    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep
    @Consume(ConfigBuildItem.class)
    void configureTruststore(KeycloakRecorder recorder) {
        recorder.configureTruststore();
    }

    /**
     * Check whether JDBC driver is present for the specified DB
     *
     * @param ignore used for changing build items execution order with regards to AgroalProcessor
     */
    @BuildStep
    @Produce(CheckJdbcBuildStep.class)
    void checkJdbcDriver(BuildProducer<JdbcDriverBuildItem> ignore) {
        final Optional<String> dbDriver = Configuration.getOptionalValue("quarkus.datasource.jdbc.driver");

        if (dbDriver.isPresent()) {
            try {
                // We do not want to initialize the JDBC driver class
                Class.forName(dbDriver.get(), false, Thread.currentThread().getContextClassLoader());
            } catch (ClassNotFoundException e) {
                // Ignore queued TRACE and DEBUG messages for not initialized log handlers
                InitialConfigurator.DELAYED_HANDLER.setBuildTimeHandlers(new Handler[]{});
                throw new ConfigurationException(String.format("Unable to find the JDBC driver (%s). You need to install it.", dbDriver.get()));
            }
        }
    }

    /**
     * Parse the default configuration for the User Profile provider
     */
    @BuildStep
    @Produce(UserProfileBuildItem.class)
    UserProfileBuildItem parseDefaultUserProfileConfig() {
        UPConfig defaultConfig = UPConfigUtils.parseSystemDefaultConfig();
        logger.debug("Parsing default configuration for the User Profile provider");
        return new UserProfileBuildItem(defaultConfig);
    }

    /**
     * Set the default configuration to the User Profile provider
     */
    @BuildStep
    @Consume(ProfileBuildItem.class)
    @Record(ExecutionTime.STATIC_INIT)
    void setDefaultUserProfileConfig(KeycloakRecorder recorder, UserProfileBuildItem configuration) {
        recorder.setDefaultUserProfileConfiguration(configuration.getDefaultConfig());
    }

    /**
     * <p>Configures the persistence unit for Quarkus.
     *
     * <p>The {@code hibernate-orm} extension expects that the dialect is statically
     * set to the persistence unit if there is any from the classpath and we use this method to obtain the dialect from the configuration
     * file so that we can build the application with whatever dialect we want. In addition to the dialect, we should also be
     * allowed to set any additional defaults that we think that makes sense.
     *
     * @param config
     * @param descriptors
     */
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void configurePersistenceUnits(HibernateOrmConfig config,
            List<PersistenceXmlDescriptorBuildItem> descriptors,
            List<JdbcDataSourceBuildItem> jdbcDataSources,
            BuildProducer<AdditionalJpaModelBuildItem> additionalJpaModel,
            CombinedIndexBuildItem indexBuildItem,
            BuildProducer<HibernateOrmIntegrationRuntimeConfiguredBuildItem> runtimeConfigured,
            KeycloakRecorder recorder) {
        ParsedPersistenceXmlDescriptor defaultUnitDescriptor = null;
        List<String> userManagedEntities = new ArrayList<>();

        for (PersistenceXmlDescriptorBuildItem item : descriptors) {
            ParsedPersistenceXmlDescriptor descriptor = item.getDescriptor();

            if ("keycloak-default".equals(descriptor.getName())) {
                defaultUnitDescriptor = descriptor;
                configureDefaultPersistenceUnitProperties(defaultUnitDescriptor, config, getDefaultDataSource(jdbcDataSources));
                runtimeConfigured.produce(new HibernateOrmIntegrationRuntimeConfiguredBuildItem("keycloak", defaultUnitDescriptor.getName())
                        .setInitListener(recorder.createDefaultUnitListener()));
            } else {
                Properties properties = descriptor.getProperties();
                // register a listener for customizing the unit configuration at runtime
                runtimeConfigured.produce(new HibernateOrmIntegrationRuntimeConfiguredBuildItem("keycloak", descriptor.getName())
                        .setInitListener(recorder.createUserDefinedUnitListener(properties.getProperty(AvailableSettings.DATASOURCE))));
                userManagedEntities.addAll(descriptor.getManagedClassNames());
            }
        }

        if (defaultUnitDescriptor == null) {
            throw new RuntimeException("No default persistence unit found.");
        }

        configureDefaultPersistenceUnitEntities(defaultUnitDescriptor, indexBuildItem, userManagedEntities);
    }

    @BuildStep
    @Consume(CheckJdbcBuildStep.class)
    void produceDefaultPersistenceUnit(BuildProducer<PersistenceXmlDescriptorBuildItem> producer) {
        ParsedPersistenceXmlDescriptor descriptor = PersistenceXmlParser.locateIndividualPersistenceUnit(
                Thread.currentThread().getContextClassLoader().getResource("default-persistence.xml"));

        producer.produce(new PersistenceXmlDescriptorBuildItem(descriptor));
    }

    private void configureDefaultPersistenceUnitProperties(ParsedPersistenceXmlDescriptor descriptor, HibernateOrmConfig config,
            JdbcDataSourceBuildItem defaultDataSource) {
        if (defaultDataSource == null || !defaultDataSource.isDefault()) {
            throw new RuntimeException("The server datasource must be the default datasource.");
        }

        Properties unitProperties = descriptor.getProperties();

        final Optional<String> dialect = getOptionalKcValue(DatabaseOptions.DB_DIALECT.getKey());
        dialect.ifPresent(d -> unitProperties.setProperty(AvailableSettings.DIALECT, d));

        final Optional<String> defaultSchema = getOptionalKcValue(DatabaseOptions.DB_SCHEMA.getKey());
        defaultSchema.ifPresent(ds -> unitProperties.setProperty(AvailableSettings.DEFAULT_SCHEMA, ds));

        unitProperties.setProperty(AvailableSettings.JAKARTA_TRANSACTION_TYPE, PersistenceUnitTransactionType.JTA.name());
        descriptor.setTransactionType(PersistenceUnitTransactionType.JTA);

        unitProperties.setProperty(AvailableSettings.QUERY_STARTUP_CHECKING, Boolean.FALSE.toString());

        String dbKind = defaultDataSource.getDbKind();

        for (Entry<Object, Object> query : loadSpecificNamedQueries(dbKind.toLowerCase()).entrySet()) {
            unitProperties.setProperty(QUERY_PROPERTY_PREFIX + query.getKey(), query.getValue().toString());
        }
    }

    private void configureDefaultPersistenceUnitEntities(ParsedPersistenceXmlDescriptor descriptor, CombinedIndexBuildItem indexBuildItem,
            List<String> userManagedEntities) {
        IndexView index = indexBuildItem.getIndex();
        Collection<AnnotationInstance> annotations = index.getAnnotations(DotName.createSimple(Entity.class.getName()));

        for (AnnotationInstance annotation : annotations) {
            AnnotationTarget target = annotation.target();
            String targetName = target.asClass().name().toString();

            if (!userManagedEntities.contains(targetName)
                    && (!targetName.startsWith("org.keycloak") || targetName.startsWith("org.keycloak.testsuite"))) {
                descriptor.addClasses(targetName);
            }
        }
    }

    /**
     * <p>Load the built-in provider factories during build time so we don't spend time looking up them at runtime. By loading
     * providers at this stage we are also able to perform a more dynamic configuration based on the default providers.
     *
     * <p>User-defined providers are going to be loaded at startup</p>
     *
     * @param recorder
     */
    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep
    @Consume(CryptoProviderInitBuildItem.class)
    @Produce(KeycloakSessionFactoryPreInitBuildItem.class)
    KeycloakSessionFactoryPreInitBuildItem configureKeycloakSessionFactory(KeycloakRecorder recorder, List<PersistenceXmlDescriptorBuildItem> descriptors) {
        Map<Spi, Map<Class<? extends Provider>, Map<String, Class<? extends ProviderFactory>>>> factories = new HashMap<>();
        Map<Class<? extends Provider>, String> defaultProviders = new HashMap<>();
        Map<String, ProviderFactory> preConfiguredProviders = new HashMap<>();

        for (Entry<Spi, Map<Class<? extends Provider>, Map<String, ProviderFactory>>> entry : loadFactories(preConfiguredProviders)
                .entrySet()) {
            Spi spi = entry.getKey();

            checkProviders(spi, entry.getValue(), defaultProviders);

            for (Entry<Class<? extends Provider>, Map<String, ProviderFactory>> value : entry.getValue().entrySet()) {
                for (ProviderFactory factory : value.getValue().values()) {
                    factories.computeIfAbsent(spi,
                            key -> new HashMap<>())
                            .computeIfAbsent(spi.getProviderClass(), aClass -> new HashMap<>()).put(factory.getId(),factory.getClass());
                }
            }

            if (spi instanceof JpaConnectionSpi) {
                configureUserDefinedPersistenceUnits(descriptors, factories, preConfiguredProviders, spi);
            }

            if (spi instanceof ThemeResourceSpi) {
                configureThemeResourceProviders(factories, spi);
            }
        }

        List<ClasspathThemeProviderFactory.ThemesRepresentation> themes = loadThemesFromClassPath();
        recorder.configSessionFactory(factories, defaultProviders, preConfiguredProviders, themes, Environment.isRebuild());
        return new KeycloakSessionFactoryPreInitBuildItem(factories, defaultProviders, preConfiguredProviders, themes);
    }

    private List<ClasspathThemeProviderFactory.ThemesRepresentation> loadThemesFromClassPath() {
        try {
            List<ClasspathThemeProviderFactory.ThemesRepresentation> themes = new ArrayList<>();
            Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(KEYCLOAK_THEMES_JSON);

            while (resources.hasMoreElements()) {
                themes.add(JsonSerialization.readValue(resources.nextElement().openStream(), ClasspathThemeProviderFactory.ThemesRepresentation.class));
            }

            return themes;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load themes", e);
        }
    }

    private void configureThemeResourceProviders(Map<Spi, Map<Class<? extends Provider>, Map<String, Class<? extends ProviderFactory>>>> factories, Spi spi) {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Enumeration<URL> resources = classLoader.getResources(FlatClasspathThemeResourceProviderFactory.THEME_RESOURCES);

            if (resources.hasMoreElements()) {
                // make sure theme resources are loaded using a flat classpath. if no resources are available the provider is not registered
                factories.computeIfAbsent(spi, key -> new HashMap<>()).computeIfAbsent(spi.getProviderClass(), aClass -> new HashMap<>()).put(FlatClasspathThemeResourceProviderFactory.ID, FlatClasspathThemeResourceProviderFactory.class);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to install default theme resource provider", e);
        }
    }

    private void configureUserDefinedPersistenceUnits(List<PersistenceXmlDescriptorBuildItem> descriptors,
            Map<Spi, Map<Class<? extends Provider>, Map<String, Class<? extends ProviderFactory>>>> factories,
            Map<String, ProviderFactory> preConfiguredProviders, Spi spi) {
        descriptors.stream()
                .map(PersistenceXmlDescriptorBuildItem::getDescriptor)
                .map(ParsedPersistenceXmlDescriptor::getName)
                .filter(Predicate.not("keycloak-default"::equals)).forEach((String unitName) -> {
                    NamedJpaConnectionProviderFactory factory = new NamedJpaConnectionProviderFactory();

                    factory.setUnitName(unitName);

                    factories.get(spi).get(JpaConnectionProvider.class).put(unitName, NamedJpaConnectionProviderFactory.class);
                    preConfiguredProviders.put(unitName, factory);
                });
    }

    /**
     * Register the custom {@link org.eclipse.microprofile.config.spi.ConfigSource} implementations.
     *
     * @param configSources
     */
    @BuildStep(onlyIfNot = IsIntegrationTest.class )
    void configureConfigSources(BuildProducer<StaticInitConfigBuilderBuildItem> configSources) {
        configSources.produce(new StaticInitConfigBuilderBuildItem(KeycloakConfigSourceProvider.class.getName()));
    }

    @BuildStep(onlyIf = IsIntegrationTest.class)
    void prepareTestEnvironment(BuildProducer< StaticInitConfigBuilderBuildItem> configSources, DevServicesDatasourceResultBuildItem dbConfig) {
        configSources.produce(new StaticInitConfigBuilderBuildItem("org.keycloak.quarkus.runtime.configuration.test.TestKeycloakConfigSourceProvider"));

        // we do not enable dev services by default and the DevServicesDatasourceResultBuildItem might not be available when discovering build steps
        // Quarkus seems to allow that when the DevServicesDatasourceResultBuildItem is not the only parameter to the build step
        // this might be too sensitive and break if Quarkus changes the behavior
        if (dbConfig != null && dbConfig.getDefaultDatasource() != null) {
            Map<String, String> configProperties = dbConfig.getDefaultDatasource().getConfigProperties();

            for (Entry<String, String> dbConfigProperty : configProperties.entrySet()) {
                PropertyMapper<?> mapper = PropertyMappers.getMapper(dbConfigProperty.getKey());

                if (mapper == null) {
                    continue;
                }

                String kcProperty = mapper.getFrom();

                if (kcProperty.endsWith("db")) {
                    // db kind set when running tests
                    continue;
                }

                System.setProperty(kcProperty, dbConfigProperty.getValue());
            }
        }
    }

    /**
     * <p>Make the build time configuration available at runtime so that the server can run without having to specify some of
     * the properties again.
     */
    @BuildStep(onlyIf = IsReAugmentation.class)
    void persistBuildTimeProperties(BuildProducer<GeneratedResourceBuildItem> resources) {
        Properties properties = new Properties();

        putPersistedProperty(properties, "kc.db");

        for (String name : getPropertyNames()) {
            putPersistedProperty(properties, name);
        }

        for (File jar : getProviderFiles().values()) {
            properties.put(String.format("kc.provider.file.%s.last-modified", jar.getName()), String.valueOf(jar.lastModified()));
        }

        if (!Environment.isRebuildCheck()) {
            // not auto-build (e.g.: start without optimized option) but a regular build to create an optimized server image
            Configuration.markAsOptimized(properties);
        }

        String profile = Environment.getProfile();

        if (profile != null) {
            properties.put(Environment.PROFILE, profile);
            properties.put(ProfileManager.QUARKUS_PROFILE_PROP, profile);
        }

        properties.put(QUARKUS_PROPERTY_ENABLED, String.valueOf(QuarkusPropertiesConfigSource.getConfigurationFile() != null));

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            properties.store(outputStream, " Auto-generated, DO NOT change this file");
            resources.produce(new GeneratedResourceBuildItem(PersistedConfigSource.PERSISTED_PROPERTIES, outputStream.toByteArray()));
        } catch (Exception cause) {
            throw new RuntimeException("Failed to persist configuration", cause);
        }
    }

    private void putPersistedProperty(Properties properties, String name) {
        PropertyMapper<?> mapper = PropertyMappers.getMapper(name);
        ConfigValue value = null;

        if (mapper == null) {
            if (name.startsWith(NS_QUARKUS)) {
                value = Configuration.getConfigValue(name);

                if (!QuarkusPropertiesConfigSource.isSameSource(value)) {
                    return;
                }
            }
        } else if (mapper.isBuildTime()) {
            name = mapper.getFrom();
            value = Configuration.getConfigValue(name);
        }

        if (value != null && value.getValue() != null) {
            if (value.getConfigSourceName() == null) {
                // only persist build options resolved from config sources and not default values
                return;
            }
            String rawValue = value.getRawValue();

            if (rawValue == null) {
                rawValue = value.getValue();
            }

            properties.put(name, rawValue);
        }
    }

    /**
     * This will cause quarkus tu include specified modules in the jandex index. For example keycloak-services is needed as it includes
     * most of the JAX-RS resources, which are required to register Resteasy builtin providers. See {@link ResteasyDeployment#isRegisterBuiltin()}.
     * Similar reason is liquibase
     *
     * @param indexDependencyBuildItemBuildProducer
     */
    @BuildStep
    void index(BuildProducer<IndexDependencyBuildItem> indexDependencyBuildItemBuildProducer) {
        indexDependencyBuildItemBuildProducer.produce(new IndexDependencyBuildItem("org.liquibase", "liquibase-core"));
        indexDependencyBuildItemBuildProducer.produce(new IndexDependencyBuildItem("org.keycloak", "keycloak-services"));
    }

    @BuildStep
    @Consume(CheckJdbcBuildStep.class)
    void indexJpaStore(BuildProducer<IndexDependencyBuildItem> indexDependencyBuildItemBuildProducer) {
        indexDependencyBuildItemBuildProducer.produce(new IndexDependencyBuildItem("org.keycloak", "keycloak-model-jpa"));
    }

    @BuildStep
    void disableMetricsEndpoint(BuildProducer<RouteBuildItem> routes) {
        if (!isMetricsEnabled()) {
            routes.produce(RouteBuildItem.builder().route(DEFAULT_METRICS_ENDPOINT.concat("/*")).handler(new NotFoundHandler()).build());
        }
    }

    @BuildStep
    void disableHealthEndpoint(BuildProducer<RouteBuildItem> routes, BuildProducer<BuildTimeConditionBuildItem> removeBeans,
            CombinedIndexBuildItem index) {
        boolean healthDisabled = !isHealthEnabled();

        if (healthDisabled) {
            routes.produce(RouteBuildItem.builder().route(DEFAULT_HEALTH_ENDPOINT.concat("/*")).handler(new NotFoundHandler()).build());
        }

        boolean metricsDisabled = !isMetricsEnabled();

        if (healthDisabled || metricsDisabled) {
            // disables the single check we provide which depends on metrics enabled
            ClassInfo disabledBean = index.getIndex()
                    .getClassByName(DotName.createSimple(KeycloakReadyHealthCheck.class.getName()));
            removeBeans.produce(new BuildTimeConditionBuildItem(disabledBean.asClass(), false));
        }
    }

    // We can't use quarkus.datasource.health.enabled=false as that would remove the DataSourceHealthCheck from CDI and
    // it can't be instantiated via constructor as it now includes some field injection points. So we just make it a regular
    // bean without the @Readiness annotation so it won't be used as a health check on it's own.
    @BuildStep
    AnnotationsTransformerBuildItem disableDefaultDataSourceHealthCheck() {
        return new AnnotationsTransformerBuildItem(AnnotationsTransformer.appliedToClass()
                .whenClass(c -> c.name().equals(DotName.createSimple(DataSourceHealthCheck.class)))
                .thenTransform(t -> t.remove(
                        a -> a.name().equals(DotName.createSimple(Readiness.class)))));
    }

    @BuildStep
    void configureResteasy(CombinedIndexBuildItem index,
            BuildProducer<BuildTimeConditionBuildItem> buildTimeConditionBuildItemBuildProducer,
            BuildProducer<MethodScannerBuildItem> scanner) {
        buildTimeConditionBuildItemBuildProducer.produce(new BuildTimeConditionBuildItem(index.getIndex().getClassByName(DotName.createSimple(
                KeycloakApplication.class.getName())), false));

        KeycloakHandlerChainCustomizer chainCustomizer = new KeycloakHandlerChainCustomizer();

        scanner.produce(new MethodScannerBuildItem(new MethodScanner() {
            @Override
            public List<HandlerChainCustomizer> scan(MethodInfo method, ClassInfo actualEndpointClass,
                    Map<String, Object> methodContext) {
                return List.of(chainCustomizer);
            }
        }));
    }

    @Consume(ProfileBuildItem.class)
    @Produce(CryptoProviderInitBuildItem.class)
    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void setCryptoProvider(KeycloakRecorder recorder) {
        FipsMode fipsMode = getOptionalValue(NS_KEYCLOAK_PREFIX + SecurityOptions.FIPS_MODE.getKey())
                .map(FipsMode::valueOfOption)
                .orElse(FipsMode.DISABLED);
        if (Profile.isFeatureEnabled(Profile.Feature.FIPS) && !fipsMode.isFipsEnabled()) {
            // default to non strict when fips feature enabled
            fipsMode = FipsMode.NON_STRICT;
        } else if (fipsMode.isFipsEnabled() && !Profile.isFeatureEnabled(Profile.Feature.FIPS)) {
            throw new RuntimeException("FIPS mode cannot be enabled without enabling the FIPS feature --features=fips");
        }

        recorder.setCryptoProvider(fipsMode);
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    void configureDevMode(BuildProducer<HotDeploymentWatchedFileBuildItem> hotFiles) {
        hotFiles.produce(new HotDeploymentWatchedFileBuildItem("META-INF/keycloak.conf"));
    }

    private Map<Spi, Map<Class<? extends Provider>, Map<String, ProviderFactory>>> loadFactories(
            Map<String, ProviderFactory> preConfiguredProviders) {
        Config.init(new MicroProfileConfigProvider());
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        ProviderManager pm = getProviderManager(classLoader);
        Map<Spi, Map<Class<? extends Provider>, Map<String, ProviderFactory>>> factories = new HashMap<>();

        for (Spi spi : pm.loadSpis()) {
            Map<Class<? extends Provider>, Map<String, ProviderFactory>> providers = new HashMap<>();
            List<ProviderFactory> loadedFactories = new ArrayList<>();
            String provider = Config.getProvider(spi.getName());

            if (provider == null) {
                loadedFactories.addAll(pm.load(spi));
            } else {
                ProviderFactory factory = pm.load(spi, provider);

                if (factory != null) {
                    loadedFactories.add(factory);
                }
            }

            Map<String, ProviderFactory> deployedScriptProviders = loadDeployedScriptProviders(classLoader, spi);

            loadedFactories.addAll(deployedScriptProviders.values());
            preConfiguredProviders.putAll(deployedScriptProviders);

            for (ProviderFactory factory : loadedFactories) {
                if (IGNORED_PROVIDER_FACTORY.contains(factory.getClass())) {
                    continue;
                }

                Config.Scope scope = Config.scope(spi.getName(), factory.getId());

                if (isEnabled(factory, scope)) {
                    if (spi.isInternal() && !isInternal(factory)) {
                        ServicesLogger.LOGGER.spiMayChange(factory.getId(), factory.getClass().getName(), spi.getName());
                    }

                    providers.computeIfAbsent(spi.getProviderClass(), aClass -> new HashMap<>()).put(factory.getId(),
                            factory);
                } else {
                    logger.debugv("SPI {0} provider {1} disabled", spi.getName(), factory.getId());
                }
            }

            factories.put(spi, providers);
        }

        return factories;
    }

    private Map<String, ProviderFactory> loadDeployedScriptProviders(ClassLoader classLoader, Spi spi) {
        Map<String, ProviderFactory> providers = new HashMap<>();

        if (supportsDeployeableScripts(spi)) {
            try {
                Enumeration<URL> urls = classLoader.getResources(KEYCLOAK_SCRIPTS_JSON_PATH);

                while (urls.hasMoreElements()) {
                    URL url = urls.nextElement();
                    int fileSeparator = url.getFile().indexOf(JAR_FILE_SEPARATOR);

                    if (fileSeparator != -1) {
                        JarFile jarFile = new JarFile(url.getFile().substring("file:".length(), fileSeparator));
                        JarEntry descriptorEntry = jarFile.getJarEntry(KEYCLOAK_SCRIPTS_JSON_PATH);
                        ScriptProviderDescriptor descriptor;

                        try (InputStream is = jarFile.getInputStream(descriptorEntry)) {
                            descriptor = JsonSerialization.readValue(is, ScriptProviderDescriptor.class);
                        }

                        for (Entry<String, List<ScriptProviderMetadata>> entry : descriptor.getProviders().entrySet()) {
                            if (isScriptForSpi(spi, entry.getKey())) {
                                for (ScriptProviderMetadata metadata : entry.getValue()) {
                                    ProviderFactory provider = createDeployableScriptProvider(jarFile, entry, metadata);
                                    providers.put(metadata.getId(), provider);
                                }
                            }
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to discover script providers", e);
            }
        }

        return providers;
    }

    private ProviderFactory createDeployableScriptProvider(JarFile jarFile, Entry<String, List<ScriptProviderMetadata>> entry,
            ScriptProviderMetadata metadata) throws IOException {
        String fileName = metadata.getFileName();

        if (fileName == null) {
            throw new RuntimeException("You must provide the script file name");
        }

        JarEntry scriptFile = jarFile.getJarEntry(fileName);

        try (InputStream in = jarFile.getInputStream(scriptFile)) {
            metadata.setCode(StreamUtil.readString(in, StandardCharsets.UTF_8));
        }

        metadata.setId(new StringBuilder("script").append("-").append(fileName).toString());

        String name = metadata.getName();

        if (name == null) {
            name = fileName;
        }

        metadata.setName(name);

        return DEPLOYEABLE_SCRIPT_PROVIDERS.get(entry.getKey()).apply(metadata);
    }

    private boolean isScriptForSpi(Spi spi, String type) {
        if (spi instanceof ProtocolMapperSpi && (MAPPERS.equals(type) || SAML_MAPPERS.equals(type))) {
            return true;
        } else if (spi instanceof PolicySpi && POLICIES.equals(type)) {
            return true;
        } else if (spi instanceof AuthenticatorSpi && AUTHENTICATORS.equals(type)) {
            return true;
        }
        return false;
    }

    private boolean supportsDeployeableScripts(Spi spi) {
        return spi instanceof ProtocolMapperSpi || spi instanceof PolicySpi || spi instanceof AuthenticatorSpi;
    }

    private boolean isEnabled(ProviderFactory factory, Config.Scope scope) {
        if (!scope.getBoolean("enabled", true)) {
            return false;
        }
        if (factory instanceof EnvironmentDependentProviderFactory environmentDependentProviderFactory) {
            return environmentDependentProviderFactory.isSupported(scope);
        }
        return true;
    }

    private boolean isInternal(ProviderFactory<?> factory) {
        String packageName = factory.getClass().getPackage().getName();
        return packageName.startsWith("org.keycloak") && !packageName.startsWith("org.keycloak.examples");
    }

    private void checkProviders(Spi spi,
                                Map<Class<? extends Provider>, Map<String, ProviderFactory>> factoriesMap,
                                Map<Class<? extends Provider>, String> defaultProviders) {
        String defaultProvider = Config.getProvider(spi.getName());

        if (defaultProvider != null) {
            Map<String, ProviderFactory> map = factoriesMap.get(spi.getProviderClass());
            if (map == null || map.get(defaultProvider) == null) {
                throw new RuntimeException("Failed to find provider " + defaultProvider + " for " + spi.getName());
            }
        } else {
            Map<String, ProviderFactory> factories = factoriesMap.get(spi.getProviderClass());
            if (factories != null && factories.size() == 1) {
                defaultProvider = factories.values().iterator().next().getId();
            }

            if (factories != null) {
                if (defaultProvider == null) {
                    Optional<ProviderFactory> highestPriority = factories.values().stream()
                            .max(Comparator.comparing(ProviderFactory::order));
                    if (highestPriority.isPresent() && highestPriority.get().order() > 0) {
                        defaultProvider = highestPriority.get().getId();
                    }
                }
            }

            if (defaultProvider == null && (factories == null || factories.containsKey("default"))) {
                defaultProvider = "default";
            }
        }

        if (defaultProvider != null) {
            defaultProviders.put(spi.getProviderClass(), defaultProvider);
        } else {
            logger.debugv("No default provider for {0}", spi.getName());
        }
    }

    private boolean isMetricsEnabled() {
        return Configuration.getOptionalBooleanValue(NS_KEYCLOAK_PREFIX.concat("metrics-enabled")).orElse(false);
    }

    private boolean isHealthEnabled() {
        return Configuration.getOptionalBooleanValue(NS_KEYCLOAK_PREFIX.concat("health-enabled")).orElse(false);
    }

    static JdbcDataSourceBuildItem getDefaultDataSource(List<JdbcDataSourceBuildItem> jdbcDataSources) {
        for (JdbcDataSourceBuildItem jdbcDataSource : jdbcDataSources) {
            if (jdbcDataSource.isDefault()) {
                return jdbcDataSource;
            }
        }

        throw new RuntimeException("No default datasource found. The server datasource must be the default datasource.");
    }
}
