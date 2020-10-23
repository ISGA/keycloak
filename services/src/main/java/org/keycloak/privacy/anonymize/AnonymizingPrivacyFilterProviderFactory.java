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
package org.keycloak.privacy.anonymize;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.privacy.PrivacyFilterProvider;
import org.keycloak.privacy.PrivacyFilterProviderFactory;
import org.keycloak.privacy.PrivacyTypeHints;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link PrivacyFilterProviderFactory} for {@link AnonymizingPrivacyFilterProvider}.
 * <p>
 * In order to use this PrivacyFilterProvider, you need to configure the privacyFilter SPI to use the {@code anonymize} provider
 * in {@code keycloak-server.json}, or the wildfly configuration.
 * <p>
 * An example configuration for `keycloak-server.json` looks like this:
 * <pre>{@code
 *  "privacy-filter":{
 *         "provider": "${keycloak.privacyFilter.provider:anonymize}",
 *         "anonymize": {
 *              // your custom config here
 *         }
 *     },
 * }</pre>
 * <p>
 * Users can implement their own Anonymization strategies by subclassing this class, e.g.:
 * <pre>{@code
 * class CustomAnonymizingPrivacyFilterProviderFactory extends AnonymizingPrivacyFilterProviderFactory {
 *     @Override
 *     public String getId() {
 *         return "anonymize-custom";
 *     }
 *
 *     @Override
 *     protected Anonymizer createAnonymizer(Config.Scope config) {
 *         Anonymizer custom = (input, typeHint) -> "*";
 *         return custom;
 *     }
 * }
 * }</pre>
 *
 * @author <a href="mailto:thomas.darimont@googlemail.com">Thomas Darimont</a>
 */
public class AnonymizingPrivacyFilterProviderFactory implements PrivacyFilterProviderFactory {

    public static final String ID = "anonymize";

    public static final List<String> DEFAULT_FILTERED_TYPE_HINTS;

    public static final Map<String, String> DEFAULT_TYPE_HINT_ALIAS_MAPPING;

    private static final Logger LOGGER = Logger.getLogger(AnonymizingPrivacyFilterProviderFactory.class);

    private static final String DELIMITER = ",";

    static {
        List<String> filteredTypesHints = Arrays.asList(
                PrivacyTypeHints.USER_ID,
                PrivacyTypeHints.IP_ADDRESS,
                PrivacyTypeHints.USERNAME,
                PrivacyTypeHints.NAME,
                PrivacyTypeHints.EMAIL,
                PrivacyTypeHints.ADDRESS,
                PrivacyTypeHints.PHONE_NUMBER,
                PrivacyTypeHints.PII,
                PrivacyTypeHints.PERSONAL_DATA,
                PrivacyTypeHints.DEFAULT);

        DEFAULT_FILTERED_TYPE_HINTS = Collections.unmodifiableList(filteredTypesHints);

        Map<String, String> aliasMapping = new HashMap<>();
        aliasMapping.put("mobile", PrivacyTypeHints.PHONE_NUMBER);
        aliasMapping.put("phone", PrivacyTypeHints.PHONE_NUMBER);
        aliasMapping.put("address", PrivacyTypeHints.ADDRESS);
        aliasMapping.put("firstName", PrivacyTypeHints.NAME);
        aliasMapping.put("lastName", PrivacyTypeHints.NAME);

        DEFAULT_TYPE_HINT_ALIAS_MAPPING = Collections.unmodifiableMap(aliasMapping);
    }

    protected volatile PrivacyFilterProvider provider;

    @Override
    public PrivacyFilterProvider create(KeycloakSession session) {
        return provider;
    }

    @Override
    public void init(Config.Scope config) {

        if (provider != null) {
            return;
        }

        provider = createProvider(config);
    }

    protected PrivacyFilterProvider createProvider(Config.Scope config) {

        // The list of supported type-hints that should be filtered
        Set<String> filteredTypeHints = new HashSet<>();
        // useDefaultFilteredTypeHints controls whether the default filtered type-hints should be used, defaults to true.
        // if set to false, then only explicitly provided type-hints (e.g. via filteredTypes) are considered for filtering.
        if (config.getBoolean("useDefaultFilteredTypeHints", true)) {
            filteredTypeHints.addAll(DEFAULT_FILTERED_TYPE_HINTS);
        }
        // filteredTypeHints denotes a string of $DELIMITER separated type alias that should be filtered, e.g.:
        // "pii,confidential"
        Set<String> customFilteredTypeHints = parseTypeHints(config.get("filteredTypeHints"));
        filteredTypeHints.addAll(customFilteredTypeHints);

        // can be used to map new type-hints to existing ones to reuse anonymization rules bound to a type-hint
        // typeHintAliasMapping denotes a string of alias:type-hint pairs separated by $DELIMITER, e.g.:
        // "birthdate:pii,memberId:pii"
        Map<String, String> typeHintAliases = new HashMap<>(DEFAULT_TYPE_HINT_ALIAS_MAPPING);
        typeHintAliases.putAll(parseTypeHintAliasMapping(config.get("typeHintAliasMapping")));

        // fallbackTypeHint denotes the type-hint that should be used if no type-hint could be resolved or no explicit type-hint is provided
        String fallbackTypeHint = config.get("fallbackTypeHint", PrivacyTypeHints.DEFAULT);

        Anonymizer anonymizer = createAnonymizer(config);

        return new AnonymizingPrivacyFilterProvider(filteredTypeHints, typeHintAliases, fallbackTypeHint, anonymizer);
    }

    /**
     * Creates a new {@link Anonymizer}. Sub-classes can override this method to return
     * a custom {@link Anonymizer} implementation.
     *
     * @param config
     * @return
     */
    protected Anonymizer createAnonymizer(Config.Scope config) {

        // Note: setting minLength=1, prefixLength=0, suffixLength=0 and placeHolder=%,
        // will effectively replace the given input with '%'.
        int minLength = config.getInt("minLength", 6);
        int prefixLength = config.getInt("prefixLength", 2);
        int suffixLength = config.getInt("suffixLength", 3);
        String placeHolder = config.get("placeHolder", "%");

        return new DefaultAnonymizer(minLength, prefixLength, suffixLength, placeHolder);
    }

    protected Map<String, String> parseTypeHintAliasMapping(String typeAliasesMappingInput) {

        String typeAliasesMapping = typeAliasesMappingInput == null ? null : typeAliasesMappingInput.trim();

        if (typeAliasesMapping == null || typeAliasesMapping.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> mapping = new HashMap<>();
        for (String entry : typeAliasesMapping.split(DELIMITER)) {
            String[] aliasToTypeHint = entry.split(":");
            if (aliasToTypeHint.length != 2) {
                LOGGER.warnf("Skipping bad typeAliasMapping: wrong length. entry=%s", entry);
                continue;
            }

            String alias = aliasToTypeHint[0].trim();
            String typeHint = aliasToTypeHint[1].trim();

            if (alias.isEmpty() || typeHint.isEmpty()) {
                // skip entry with bad formatting
                LOGGER.warnf("Skipping bad typeAliasMapping: bad formatting. entry=%s", entry);
                continue;
            }

            mapping.put(alias, typeHint);
        }

        return mapping;
    }

    protected Set<String> parseTypeHints(String typeHintListInput) {

        String typeHintList = typeHintListInput == null ? null : typeHintListInput.trim();
        if (typeHintList == null || typeHintList.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> typeHints = Arrays.stream(typeHintList.split(DELIMITER))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        return typeHints;
    }

    @Override
    public String getId() {
        return ID;
    }
}