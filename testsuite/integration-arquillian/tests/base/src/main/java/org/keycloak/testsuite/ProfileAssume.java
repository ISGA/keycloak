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

package org.keycloak.testsuite;

import org.junit.Assume;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.common.Profile;
import org.keycloak.representations.info.ProfileInfoRepresentation;
import org.keycloak.testsuite.util.AdminClientUtil;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author <a href="mailto:sthorger@redhat.com">Stian Thorgersen</a>
 */
public class ProfileAssume {

    private static String profile;

    static {
        String host = System.getProperty("auth.server.host", "localhost");
        String port = System.getProperty("auth.server.http.port", "8180");

        String authServerContextRoot = "http://" + host + ":" + port;
        try {
            Keycloak adminClient = AdminClientUtil.createAdminClient(false, authServerContextRoot);
            ProfileInfoRepresentation profileInfo = adminClient.serverInfo().getInfo().getProfileInfo();
            profile = profileInfo.getName();
            adminClient.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to obtain profile / features info from serverinfo endpoint of " + authServerContextRoot, e);
        }
    }

    public static void assumeFeatureEnabled(Profile.Feature feature) {
        Assume.assumeTrue("Ignoring test as feature " + feature.name() + " is not enabled", isFeatureEnabled(feature));
    }

    public static void assumePreview() {
        Assume.assumeTrue("Ignoring test as community/preview profile is not enabled", !profile.equals("product"));
    }

    public static void assumePreviewDisabled() {
        Assume.assumeFalse("Ignoring test as community/preview profile is enabled", !profile.equals("product"));
    }

    public static void assumeCommunity() {
        Assume.assumeTrue("Ignoring test as community profile is not enabled", profile.equals("community"));
    }

    private static boolean isFeatureEnabled(Profile.Feature feature) {
        return Profile.isFeatureEnabled(feature);
    }
}
