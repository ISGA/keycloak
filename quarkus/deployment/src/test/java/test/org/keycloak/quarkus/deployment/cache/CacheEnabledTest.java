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
package test.org.keycloak.quarkus.deployment.cache;

import io.quarkus.test.QuarkusUnitTest;
import jakarta.enterprise.inject.Instance;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.keycloak.quarkus.runtime.storage.legacy.infinispan.CacheManagerFactory;

import jakarta.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheEnabledTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                .addAsResource("keycloak.conf", "META-INF/keycloak.conf"));

    @Inject
    Instance<CacheManagerFactory> cacheManagerFactory;


    @Test
    void testCacheEnabled() {
        assertTrue(cacheManagerFactory.isResolvable());
    }
}
