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

package org.keycloak.it.cli.dist;

import org.junit.jupiter.api.Test;
import org.keycloak.it.junit5.extension.CLIResult;
import org.keycloak.it.junit5.extension.DistributionTest;
import org.keycloak.it.junit5.extension.RawDistOnly;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import org.keycloak.it.junit5.extension.WithLegacyStoreOnly;

@RawDistOnly(reason = "Containers are immutable")
@DistributionTest
public class ExportDistTest {

    @Test
    @Launch({"export", "--realm=master", "--dir=."})
    @WithLegacyStoreOnly
    void testExport(LaunchResult result) {
        CLIResult cliResult = (CLIResult) result;
        cliResult.assertMessage("Export of realm 'master' requested.");
        cliResult.assertMessage("Export finished successfully");
        cliResult.assertNoMessage("Changes detected in configuration");
    }

    @Test
    @Launch({"export", "--realm=master" })
    @WithLegacyStoreOnly
    void testMissingDir(LaunchResult result) {
        CLIResult cliResult = (CLIResult) result;
        cliResult.assertError("Must specify either --dir or --file options.");
    }
}
