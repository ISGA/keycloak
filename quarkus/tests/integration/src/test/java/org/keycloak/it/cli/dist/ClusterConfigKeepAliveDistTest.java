/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.it.cli.dist;

import static io.restassured.RestAssured.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.infinispan.commons.dataconversion.MediaType;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.configuration.parsing.ParserRegistry;
import org.junit.jupiter.api.Test;
import org.keycloak.config.CachingOptions;
import org.keycloak.it.junit5.extension.DistributionTest;
import org.keycloak.it.junit5.extension.RawDistOnly;
import org.keycloak.it.junit5.extension.TestProvider;
import org.keycloak.it.resource.realm.TestRealmResourceTestProvider;
import org.keycloak.it.utils.KeycloakDistribution;

import com.google.common.base.CaseFormat;

/**
 * @author Ryan Emerson <remerson@redhat.com>
 */
@DistributionTest(keepAlive = true, enableTls = true)
@RawDistOnly(reason = "Containers are immutable")
public class ClusterConfigKeepAliveDistTest {
    @Test
    @TestProvider(TestRealmResourceTestProvider.class)
    void testMaxCountApplied(KeycloakDistribution dist) {
        int maxCount = 100;
        int numCaches = CachingOptions.MAX_COUNT_CACHES.length;
        String[] args = new String[numCaches + 2];
        args[0] = "start-dev";
        args[1] = "--cache=ispn";
        for (int i = 0; i < numCaches; i++)
            args[i + 2] = String.format("--%s=%d", CachingOptions.cacheMaxCountProperty(CachingOptions.MAX_COUNT_CACHES[i]), maxCount);

        dist.run(args);
        ParserRegistry parserRegistry = new ParserRegistry();

        for (String cache : CachingOptions.MAX_COUNT_CACHES) {
            String configJson = when()
                  .get("/realms/master/test-resources/cache/" + cache + "/config")
                  .thenReturn()
                  .getBody()
                  .jsonPath()
                  .prettyPrint();

            ConfigurationBuilderHolder configHolder = parserRegistry.parse(configJson, MediaType.APPLICATION_JSON);
            // Workaround for ISPN-16595
            String cacheName = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN, cache);
            Configuration config = configHolder.getNamedConfigurationBuilders().get(cacheName).build();
            assertEquals(maxCount, config.memory().maxCount());
        }
    }
}
