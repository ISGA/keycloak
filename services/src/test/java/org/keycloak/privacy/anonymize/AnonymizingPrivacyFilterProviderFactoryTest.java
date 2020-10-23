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

import org.junit.Test;
import org.keycloak.Config;
import org.keycloak.privacy.PrivacyFilterProvider;
import org.keycloak.privacy.PrivacyTypeHints;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AnonymizingPrivacyFilterProviderFactoryTest {

    /**
     * see KEYCLOAK-13160
     */
    @Test
    public void createProviderWithDefaults() {

        AnonymizingPrivacyFilterProvider provider = createProvider(null);

        String filtered = provider.filter("thomas.darimont@example.com", PrivacyTypeHints.EMAIL);

        assertEquals("th%com", filtered);
        assertEquals("7a%b5f", provider.filter("7adf8d60-8205-44d3-a191-6cd5e22d7b5f", PrivacyTypeHints.USER_ID));
    }

    /**
     * see KEYCLOAK-13160
     */
    @Test
    public void createProviderWithCustomConfig() {

        Map<String, String> config = new HashMap<>();
        config.put("minLength", "5");
        config.put("prefixLength", "1");
        config.put("suffixLength", "2");
        config.put("placeHolder", "*");

        config.put("useDefaultFilteredTypeHints", "false");
        config.put("filteredTypeHints", "confidential,birthdate,pii,");
        config.put("typeHintAliasMapping", "confidential:pii,birthdate:pii,:");
        config.put("fallbackTypeHint", PrivacyTypeHints.PII);

        AnonymizingPrivacyFilterProvider provider = createProvider(config);

        DefaultAnonymizer anonymizer = (DefaultAnonymizer) provider.getAnonymizer();
        assertEquals(5, anonymizer.getMinLength());
        assertEquals(1, anonymizer.getPrefixLength());
        assertEquals(2, anonymizer.getSuffixLength());
        assertEquals("*", anonymizer.getPlaceHolder());

        assertEquals(PrivacyTypeHints.PII, provider.getFallbackTypeHint());
        assertEquals(PrivacyTypeHints.PII, provider.getTypeAliases().get("birthdate"));
        assertTrue(provider.getTypeHints().contains("birthdate"));
        assertTrue(provider.getTypeHints().contains("pii"));

        assertEquals("thomas.darimont@example.com", provider.filter("thomas.darimont@example.com", PrivacyTypeHints.EMAIL));
        assertEquals("t*om", provider.filter("thomas.darimont@example.com", "confidential"));
    }

    @SuppressWarnings("unchecked")
    static <T extends PrivacyFilterProvider> T createProvider(Map<String, String> config) {

        Map<String, String> combinedConfig = new HashMap<>();
        if (config != null) {
            combinedConfig.putAll(config);
        }

        Config.SystemPropertiesScope scope = new Config.SystemPropertiesScope("") {
            @Override
            public String get(String key, String defaultValue) {
                return combinedConfig.getOrDefault(key, defaultValue);
            }
        };

        AnonymizingPrivacyFilterProviderFactory factory = new AnonymizingPrivacyFilterProviderFactory();
        factory.init(scope);

        return (T) factory.create(null);
    }

}