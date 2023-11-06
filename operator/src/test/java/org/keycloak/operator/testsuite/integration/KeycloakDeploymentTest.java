/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.operator.testsuite.integration;

import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.LocalObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.SecretKeySelectorBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSetSpecBuilder;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.keycloak.operator.Constants;
import org.keycloak.operator.controllers.KeycloakAdminSecretDependentResource;
import org.keycloak.operator.controllers.KeycloakDistConfigurator;
import org.keycloak.operator.controllers.KeycloakServiceDependentResource;
import org.keycloak.operator.crds.v2alpha1.deployment.Keycloak;
import org.keycloak.operator.crds.v2alpha1.deployment.KeycloakStatusCondition;
import org.keycloak.operator.crds.v2alpha1.deployment.ValueOrSecret;
import org.keycloak.operator.crds.v2alpha1.deployment.spec.HostnameSpecBuilder;
import org.keycloak.operator.testsuite.utils.CRAssert;
import org.keycloak.operator.testsuite.utils.K8sUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.keycloak.operator.testsuite.utils.CRAssert.assertKeycloakStatusCondition;
import static org.keycloak.operator.testsuite.utils.K8sUtils.deployKeycloak;
import static org.keycloak.operator.testsuite.utils.K8sUtils.getResourceFromFile;
import static org.keycloak.operator.testsuite.utils.K8sUtils.waitForKeycloakToBeReady;

@QuarkusTest
public class KeycloakDeploymentTest extends BaseOperatorTest {
    @Test
    public void testBasicKeycloakDeploymentAndDeletion() {
        // CR
        Log.info("Creating new Keycloak CR example");
        var kc = getTestKeycloakDeployment(true);
        var deploymentName = kc.getMetadata().getName();
        deployKeycloak(k8sclient, kc, true);

        // Check Operator has deployed Keycloak
        Log.info("Checking Operator has deployed Keycloak deployment");
        assertThat(k8sclient.apps().statefulSets().inNamespace(namespace).withName(deploymentName).get()).isNotNull();

        // Check Keycloak has correct replicas
        Log.info("Checking Keycloak pod has ready replicas == 1");
        assertThat(k8sclient.apps().statefulSets().inNamespace(namespace).withName(deploymentName).get().getStatus().getReadyReplicas()).isEqualTo(1);

        Log.info("Checking observedGeneration is the same as the spec");
        Keycloak latest = k8sclient.resource(kc).get();
        assertThat(latest.getMetadata().getGeneration()).isEqualTo(latest.getStatus().getObservedGeneration());

        // Delete CR
        Log.info("Deleting Keycloak CR and watching cleanup");
        k8sclient.resource(kc).delete();
        Awaitility.await()
                .untilAsserted(() -> assertThat(k8sclient.apps().statefulSets().inNamespace(namespace).withName(deploymentName).get()).isNull());
    }

    @Test
    public void testKeycloakDeploymentBeforeSecret() {
        // CR
        var kc = getTestKeycloakDeployment(true);
        var deploymentName = kc.getMetadata().getName();
        deployKeycloak(k8sclient, kc, false, false);

        // Check Operator has deployed Keycloak and the statefulset exists, this allows for the watched secret to be picked up
        Log.info("Checking Operator has deployed Keycloak deployment");
        Resource<StatefulSet> stsResource = k8sclient.resources(StatefulSet.class).withName(deploymentName);
        Resource<Keycloak> keycloakResource = k8sclient.resources(Keycloak.class).withName(deploymentName);
        // expect no errors and not ready, which means we'll keep reconciling
        Awaitility.await().ignoreExceptions().untilAsserted(() -> {
            assertThat(stsResource.get()).isNotNull();
            Keycloak keycloak = keycloakResource.get();
            CRAssert.assertKeycloakStatusCondition(keycloak, KeycloakStatusCondition.HAS_ERRORS, false);
            CRAssert.assertKeycloakStatusCondition(keycloak, KeycloakStatusCondition.READY, false);
        });
    }

    @Test
    public void testCRFields() {
        var kc = getTestKeycloakDeployment(true);
        var deploymentName = kc.getMetadata().getName();
        deployKeycloak(k8sclient, kc, true);

        final var dbConf = new ValueOrSecret("db-password", "Ay Caramba!");

        kc.getSpec().setImage("quay.io/keycloak/non-existing-keycloak");
        kc.getSpec().getAdditionalOptions().remove(dbConf);
        kc.getSpec().getAdditionalOptions().add(dbConf);
        deployKeycloak(k8sclient, kc, false);

        Awaitility.await()
                .timeout(Duration.ofMinutes(2))
                .during(Duration.ofSeconds(15)) // check if the Deployment is stable
                .untilAsserted(() -> {
                    var c = k8sclient.apps().statefulSets().inNamespace(namespace).withName(deploymentName).get()
                            .getSpec().getTemplate().getSpec().getContainers().get(0);
                    assertThat(c.getImage()).isEqualTo("quay.io/keycloak/non-existing-keycloak");
                    // additionalOptions should not override the first-class
                    assertThat(c.getEnv().stream()
                            .anyMatch(e -> e.getName().equals(KeycloakDistConfigurator.getKeycloakOptionEnvVarName(dbConf.getName()))
                                    && dbConf.getValue().equals(e.getValue())))
                            .isFalse();
                });
    }

    @Test
    public void testConfigInCRTakesPrecedence() {
        var defaultKCDeploy = getTestKeycloakDeployment(true);

        var valueSecretHealthProp = new ValueOrSecret("health-enabled", "false");
        var valueSecretProxyProp = new ValueOrSecret("proxy", "reencrypt");

        var healthEnvVar = new EnvVarBuilder()
                .withName(KeycloakDistConfigurator.getKeycloakOptionEnvVarName(valueSecretHealthProp.getName()))
                .withValue(valueSecretHealthProp.getValue())
                .build();

        var proxyEnvVar = new EnvVarBuilder()
                .withName(KeycloakDistConfigurator.getKeycloakOptionEnvVarName(valueSecretProxyProp.getName()))
                .withValue(valueSecretProxyProp.getValue())
                .build();

        defaultKCDeploy.getSpec().getAdditionalOptions().add(valueSecretHealthProp);
        defaultKCDeploy.getSpec().getAdditionalOptions().add(valueSecretProxyProp);

        deployKeycloak(k8sclient, defaultKCDeploy, false);

        assertThat(
                Constants.DEFAULT_DIST_CONFIG_LIST.stream()
                                                  .filter(oneValueOrSecret -> oneValueOrSecret.getName().equalsIgnoreCase(valueSecretHealthProp.getName()))
                                                  .findFirst()
                                                  .get()
                                                  .getValue()
        ).isEqualTo("true"); // just a sanity check default values did not change

        assertThat(
                Constants.DEFAULT_DIST_CONFIG_LIST.stream()
                                                  .filter(oneValueOrSecret -> oneValueOrSecret.getName().equalsIgnoreCase(valueSecretProxyProp.getName()))
                                                  .findFirst()
                                                  .get()
                                                  .getValue()
        ).isEqualTo("passthrough"); // just a sanity check default values did not change

        Awaitility.await()
                .ignoreExceptions()
                .untilAsserted(() -> {

                    Log.info("Asserting default value was overwritten by CR value");

                    var deployedKCStatefullSet = k8sclient.apps()
                            .statefulSets()
                            .inNamespace(namespace)
                            .withName(defaultKCDeploy.getMetadata().getName());

                    var firstKCContainer = deployedKCStatefullSet.get()
                            .getSpec()
                            .getTemplate()
                            .getSpec()
                            .getContainers()
                            .get(0);

                    assertThat(firstKCContainer.getEnv().stream()
                            .filter(oneEnvVar -> oneEnvVar.getName().equalsIgnoreCase(healthEnvVar.getName())))
                            .containsExactly(healthEnvVar);

                    assertThat(firstKCContainer.getEnv().stream()
                            .filter(oneEnvVar -> oneEnvVar.getName().equalsIgnoreCase(proxyEnvVar.getName())))
                            .containsExactly(proxyEnvVar);

                });
    }

    @Test
    public void testDeploymentDurability() {
        var kc = getTestKeycloakDeployment(true);
        var deploymentName = kc.getMetadata().getName();

        // create a dummy StatefulSet representing the pre-multiinstance state that we'll be forced to delete
        StatefulSet statefulSet = new StatefulSetBuilder().withMetadata(kc.getMetadata()).editMetadata()
                .addToLabels(Constants.DEFAULT_LABELS).endMetadata().withNewSpec().withNewSelector()
                .withMatchLabels(Constants.DEFAULT_LABELS).endSelector().withServiceName("foo").withReplicas(0)
                .withNewTemplate().withNewMetadata().withLabels(Constants.DEFAULT_LABELS).endMetadata()
                .withNewSpec().addNewContainer().withName("pause").withImage("registry.k8s.io/pause:3.1")
                .endContainer().endSpec().endTemplate().endSpec().build();
        k8sclient.resource(statefulSet).create();

        // start will not be successful because the statefulSet is in the way
        deployKeycloak(k8sclient, kc, false);
        // once the statefulset is owned by the keycloak it will be picked up by the informer
        k8sclient.resource(statefulSet).accept(s -> s.addOwnerReference(k8sclient.resource(kc).get()));
        waitForKeycloakToBeReady(k8sclient, kc);

        Log.info("Trying to delete deployment");
        assertThat(k8sclient.apps().statefulSets().withName(deploymentName).delete()).isNotNull();
        Awaitility.await()
                .untilAsserted(() -> assertThat(k8sclient.apps().statefulSets().withName(deploymentName).get()).isNotNull());

        waitForKeycloakToBeReady(k8sclient, kc); // wait for reconciler to calm down to avoid race condititon

        Log.info("Trying to modify deployment");

        var deployment = k8sclient.apps().statefulSets().withName(deploymentName).get();

        // unmanaged changes
        var labels = Map.of("address", "EvergreenTerrace742");
        var flandersEnvVar = new EnvVarBuilder().withName("NEIGHBOR").withValue("Stupid Flanders!").build();
        deployment.getMetadata().getLabels().putAll(labels);

        var expectedSpec = new StatefulSetSpecBuilder(deployment.getSpec()).editTemplate().editSpec()
                .editContainer(0).addToEnv(0, flandersEnvVar).endContainer().endSpec().endTemplate().build(); // deep copy

        // managed changes
        deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setEnv(List.of(flandersEnvVar));
        String originalAnnotationValue = deployment.getMetadata().getAnnotations().put(Constants.KEYCLOAK_WATCHING_ANNOTATION, "not-right");

        deployment.getMetadata().setResourceVersion(null);
        k8sclient.resource(deployment).update();

        Awaitility.await()
                .atMost(1, MINUTES)
                .pollDelay(1, SECONDS)
                .ignoreExceptions()
                .untilAsserted(() -> {
                    var d = k8sclient.apps().statefulSets().withName(deploymentName).get();
                    // unmanaged changes won't get reverted
                    assertThat(d.getMetadata().getLabels().entrySet().containsAll(labels.entrySet())).isTrue();
                    // managed changes should get reverted
                    assertThat(d.getSpec()).isEqualTo(expectedSpec); // specs should be reconciled expected merged state
                    assertThat(d.getMetadata().getAnnotations().get(Constants.KEYCLOAK_WATCHING_ANNOTATION)).isEqualTo(originalAnnotationValue);
                });
    }

    @Test
    public void testTlsUsesCorrectSecret() {
        var kc = getTestKeycloakDeployment(true);
        deployKeycloak(k8sclient, kc, true);

        Awaitility.await()
                .ignoreExceptions()
                .untilAsserted(() -> {
                    String url = "https://" + KeycloakServiceDependentResource.getServiceName(kc) + "." + namespace + ":" + Constants.KEYCLOAK_HTTPS_PORT;
                    Log.info("Checking url: " + url);

                    var curlOutput = K8sUtils.inClusterCurl(k8sclient, namespace, "--insecure", "-s", "-w", "%{certs}", url);
                    Log.info("Curl Output: " + curlOutput);

                    assertTrue(curlOutput.contains("Issuer:O = mkcert development CA, OU = aperuffo@aperuffo-mac (Andrea Peruffo), CN = mkcert aperuffo@aperuffo-mac (Andrea Peruffo)"));
                });
    }

    @Test
    public void testTlsDisabled() {
        var kc = getTestKeycloakDeployment(true);
        kc.getSpec().getHttpSpec().setTlsSecret(null);
        kc.getSpec().getHttpSpec().setHttpEnabled(true);
        deployKeycloak(k8sclient, kc, true);

        assertKeycloakAccessibleViaService(kc, false, Constants.KEYCLOAK_HTTP_PORT);
    }

    @Test
    public void testHttpEnabledWithTls() {
        var kc = getTestKeycloakDeployment(true);
        kc.getSpec().getHttpSpec().setHttpEnabled(true);
        deployKeycloak(k8sclient, kc, true);

        assertKeycloakAccessibleViaService(kc, false, Constants.KEYCLOAK_HTTP_PORT);
    }

    @Test
    public void testHostnameStrict() {
        var kc = getTestKeycloakDeployment(true);
        deployKeycloak(k8sclient, kc, true);

        Awaitility.await()
                .ignoreExceptions()
                .untilAsserted(() -> {
                    String url = "https://" + KeycloakServiceDependentResource.getServiceName(kc) + "." + namespace + ":" + Constants.KEYCLOAK_HTTPS_PORT + "/admin/master/console/";
                    Log.info("Checking url: " + url);

                    var curlOutput = K8sUtils.inClusterCurl(k8sclient, namespace, "-s", "--insecure", "-H", "Host: foo.bar", url);
                    Log.info("Curl Output: " + curlOutput);

                    assertTrue(curlOutput.contains("\"authServerUrl\": \"https://example.com\""));
                });
    }

    @Test
    public void testHostnameStrictDisabled() {
        var kc = getTestKeycloakDeployment(true);
        var hostnameSpec = new HostnameSpecBuilder()
                .withStrict(false)
                .withStrictBackchannel(false)
                .build();
        kc.getSpec().setHostnameSpec(hostnameSpec);

        deployKeycloak(k8sclient, kc, true);

        Awaitility.await()
                .ignoreExceptions()
                .untilAsserted(() -> {
                    String url = "https://" + KeycloakServiceDependentResource.getServiceName(kc) + "." + namespace + ":" + Constants.KEYCLOAK_HTTPS_PORT + "/admin/master/console/";
                    Log.info("Checking url: " + url);

                    var curlOutput = K8sUtils.inClusterCurl(k8sclient, namespace, "-s", "--insecure", "-H", "Host: foo.bar", url);
                    Log.info("Curl Output: " + curlOutput);

                    assertTrue(curlOutput.contains("\"authServerUrl\": \"https://foo.bar\""));
                });
    }

    @Test
    public void testHttpsPort() {
        final int httpsPort = 8543;
        final int httpPort = 8180;
        var kc = getTestKeycloakDeployment(true);
        kc.getSpec().getHttpSpec().setHttpsPort(httpsPort);
        kc.getSpec().getHttpSpec().setHttpPort(httpPort);

        var hostnameSpec = new HostnameSpecBuilder()
                .withStrict(false)
                .withStrictBackchannel(false)
                .build();
        kc.getSpec().setHostnameSpec(hostnameSpec);

        deployKeycloak(k8sclient, kc, true);

        assertKeycloakAccessibleViaService(kc, true, httpsPort);
    }

    @Test
    public void testHttpPort() {
        final int httpsPort = 8543;
        final int httpPort = 8180;
        var kc = getTestKeycloakDeployment(true);
        kc.getSpec().getHttpSpec().setHttpsPort(httpsPort);
        kc.getSpec().getHttpSpec().setHttpPort(httpPort);
        kc.getSpec().getHttpSpec().setTlsSecret(null);
        kc.getSpec().getHttpSpec().setHttpEnabled(true);

        var hostnameSpec = new HostnameSpecBuilder()
                .withStrict(false)
                .withStrictBackchannel(false)
                .build();
        kc.getSpec().setHostnameSpec(hostnameSpec);

        deployKeycloak(k8sclient, kc, true);

        assertKeycloakAccessibleViaService(kc, false, httpPort);
    }

    // Reference curl command:
    // curl --insecure --data "grant_type=password&client_id=admin-cli&username=admin&password=adminPassword" https://localhost:8443/realms/master/protocol/openid-connect/token
    @Test
    public void testInitialAdminUser() {
        var kc = getTestKeycloakDeployment(true);
        String secretName = KeycloakAdminSecretDependentResource.getName(kc);

        k8sclient
                .resources(Keycloak.class)
                .inNamespace(namespace)
                .delete();
        k8sclient
                .secrets()
                .inNamespace(namespace)
                .withName(secretName)
                .delete();

        // Making sure no other Keycloak pod is still around
        Awaitility.await()
                .ignoreExceptions()
                .untilAsserted(() ->
                        assertThat(k8sclient
                                .pods()
                                .inNamespace(namespace)
                                .withLabel("app", "keycloak")
                                .list()
                                .getItems()
                                .size()).isZero());
        // Recreating the database to keep this test isolated
        deleteDB();
        deployDB();
        deployKeycloak(k8sclient, kc, true);
        var decoder = Base64.getDecoder();

        AtomicReference<String> adminUsername = new AtomicReference<>();
        AtomicReference<String> adminPassword = new AtomicReference<>();
        Awaitility.await()
                .ignoreExceptions()
                .untilAsserted(() -> {
                    Log.info("Checking secret, ns: " + namespace + ", name: " + secretName);
                    var adminSecret = k8sclient
                            .secrets()
                            .inNamespace(namespace)
                            .withName(secretName)
                            .get();

                    adminUsername.set(new String(decoder.decode(adminSecret.getData().get("username").getBytes(StandardCharsets.UTF_8))));
                    adminPassword.set(new String(decoder.decode(adminSecret.getData().get("password").getBytes(StandardCharsets.UTF_8))));

                    String url = "https://" + KeycloakServiceDependentResource.getServiceName(kc) + "." + namespace + ":" + Constants.KEYCLOAK_HTTPS_PORT + "/realms/master/protocol/openid-connect/token";
                    Log.info("Checking url: " + url);

                    var curlOutput = K8sUtils.inClusterCurl(k8sclient, namespace, "--insecure", "-s", "--data", "grant_type=password&client_id=admin-cli&username=" + adminUsername.get() + "&password=" + adminPassword.get(), url);
                    Log.info("Curl Output: " + curlOutput);

                    assertTrue(curlOutput.contains("\"access_token\""));
                    assertTrue(curlOutput.contains("\"token_type\":\"Bearer\""));
                });

        // Redeploy the same Keycloak without redeploying the Database
        k8sclient.resource(kc).delete();
        deployKeycloak(k8sclient, kc, true);
        Awaitility.await()
                .ignoreExceptions()
                .untilAsserted(() -> {
                    Log.info("Checking secret, ns: " + namespace + ", name: " + secretName);
                    var adminSecret = k8sclient
                            .secrets()
                            .inNamespace(namespace)
                            .withName(secretName)
                            .get();

                    var newPassword = new String(decoder.decode(adminSecret.getData().get("password").getBytes(StandardCharsets.UTF_8)));

                    String url = "https://" + KeycloakServiceDependentResource.getServiceName(kc) + "." + namespace + ":" + Constants.KEYCLOAK_HTTPS_PORT + "/realms/master/protocol/openid-connect/token";
                    Log.info("Checking url: " + url);

                    var curlOutput = K8sUtils.inClusterCurl(k8sclient, namespace, "--insecure", "-s", "--data", "grant_type=password&client_id=admin-cli&username=" + adminUsername.get() + "&password=" + adminPassword.get(), url);
                    Log.info("Curl Output: " + curlOutput);

                    assertTrue(curlOutput.contains("\"access_token\""));
                    assertTrue(curlOutput.contains("\"token_type\":\"Bearer\""));
                    assertNotEquals(adminPassword.get(), newPassword);
                });
    }

    @Test
    @EnabledIfSystemProperty(named = OPERATOR_CUSTOM_IMAGE, matches = ".+")
    public void testCustomImage() {
        var kc = getTestKeycloakDeployment(true);
        kc.getSpec().setImage(customImage);
        deployKeycloak(k8sclient, kc, true);

        var pods = k8sclient
                .pods()
                .inNamespace(namespace)
                .withLabels(Constants.DEFAULT_LABELS)
                .list()
                .getItems();

        assertThat(pods.get(0).getSpec().getContainers().get(0).getArgs()).endsWith("--verbose", "start", "--optimized");
    }

    @Test
    @EnabledIfSystemProperty(named = OPERATOR_CUSTOM_IMAGE, matches = ".+")
    public void testCustomImageWithImagePullSecrets() {
        String imagePullSecretName = "docker-regcred-custom-kc-imagepullsecret-01";
        String secretDescriptorFilename = "test-docker-registry-secret.yaml";

        var kc = getTestKeycloakDeployment(true);
        kc.getSpec().setImage(customImage);

        handleFakeImagePullSecretCreation(kc, secretDescriptorFilename);

        deployKeycloak(k8sclient, kc, true);

        var pods = k8sclient
                .pods()
                .inNamespace(namespace)
                .withLabels(Constants.DEFAULT_LABELS)
                .list()
                .getItems();

        assertThat(pods.get(0).getSpec().getContainers().get(0).getArgs()).endsWith("--verbose", "start", "--optimized");
        assertThat(pods.get(0).getSpec().getImagePullSecrets().size()).isEqualTo(1);
        assertThat(pods.get(0).getSpec().getImagePullSecrets().get(0).getName()).isEqualTo(imagePullSecretName);
    }

    @Test
    public void testInvalidCustomImageHasErrorMessage() {
        var kc = getTestKeycloakDeployment(true);
        kc.getSpec().setImage("does-not-exist");

        deployKeycloak(k8sclient, kc, false);

        var crSelector = k8sclient.resource(kc);

        Awaitility.await().atMost(3, MINUTES).pollDelay(1, SECONDS).ignoreExceptions().untilAsserted(() -> {
            Keycloak current = crSelector.get();
            CRAssert.assertKeycloakStatusCondition(current, KeycloakStatusCondition.READY, false);
            CRAssert.assertKeycloakStatusCondition(current, KeycloakStatusCondition.HAS_ERRORS, true,
                    String.format("Waiting for %s/%s-0 due to ErrImage", k8sclient.getNamespace(),
                            kc.getMetadata().getName()));
        });
    }

    @Test
    public void testHttpRelativePathWithPlainValue() {
        var kc = getTestKeycloakDeployment(false);
        kc.getSpec().setImage(null); // doesn't seem to become ready with the custom image
        kc.getSpec().getAdditionalOptions().add(new ValueOrSecret(Constants.KEYCLOAK_HTTP_RELATIVE_PATH_KEY, "/foobar"));
        deployKeycloak(k8sclient, kc, true);

        var pods = k8sclient
                .pods()
                .inNamespace(namespace)
                .withLabels(Constants.DEFAULT_LABELS)
                .list()
                .getItems();

        assertTrue(pods.get(0).getSpec().getContainers().get(0).getReadinessProbe().getHttpGet().getPath().contains("foobar"));
    }

    @Test
    public void testHttpRelativePathWithSecretValue() {
        var kc = getTestKeycloakDeployment(false);
        kc.getSpec().setImage(null); // doesn't seem to become ready with the custom image
        var secretName = "my-http-relative-path";
        var keyName = "rel-path";
        var httpRelativePathSecret = new SecretBuilder()
                .withNewMetadata()
                .withName(secretName)
                .withNamespace(namespace)
                .endMetadata()
                .addToStringData(keyName, "/barfoo")
                .build();
        K8sUtils.set(k8sclient, httpRelativePathSecret);

        kc.getSpec().getAdditionalOptions().add(new ValueOrSecret(Constants.KEYCLOAK_HTTP_RELATIVE_PATH_KEY,
                new SecretKeySelectorBuilder()
                    .withName(secretName)
                    .withKey(keyName)
                    .build()));
        deployKeycloak(k8sclient, kc, true);

        var pods = k8sclient
                .pods()
                .inNamespace(namespace)
                .withLabels(Constants.DEFAULT_LABELS)
                .list()
                .getItems();

        assertTrue(pods.get(0).getSpec().getContainers().get(0).getReadinessProbe().getHttpGet().getPath().contains("barfoo"));
    }

    @Test
    public void testUpgradeRecreatesPods() {
        var kc = getTestKeycloakDeployment(true);
        kc.getSpec().setInstances(3);
        deployKeycloak(k8sclient, kc, true);

        var stsGetter = k8sclient.apps().statefulSets().inNamespace(namespace).withName(kc.getMetadata().getName());
        final String origImage = stsGetter.get().getSpec().getTemplate().getSpec().getContainers().get(0).getImage();
        final String newImage = "quay.io/keycloak/non-existing-keycloak";

        kc.getSpec().setImage(newImage);
        deployKeycloak(k8sclient, kc, false);

        Awaitility.await()
                .ignoreExceptions()
                .pollInterval(Duration.ZERO) // make the test super fast not to miss the moment when Operator changes the STS
                .untilAsserted(() -> {
                    var sts = stsGetter.get();
                    assertEquals(1, sts.getStatus().getReplicas());
                    assertEquals(origImage, sts.getSpec().getTemplate().getSpec().getContainers().get(0).getImage());

                    var currentKc = k8sclient.resources(Keycloak.class)
                                    .inNamespace(namespace).withName(kc.getMetadata().getName()).get();
                    assertKeycloakStatusCondition(currentKc, KeycloakStatusCondition.READY, false, "Performing Keycloak upgrade");
                });

        Awaitility.await()
                .ignoreExceptions()
                .untilAsserted(() -> {
                    var sts = stsGetter.get();
                    assertEquals(kc.getSpec().getInstances(), sts.getSpec().getReplicas()); // just checking specs as we're using a non-existing image
                    assertEquals(newImage, sts.getSpec().getTemplate().getSpec().getContainers().get(0).getImage());

                    var currentKc = k8sclient.resources(Keycloak.class)
                            .inNamespace(namespace).withName(kc.getMetadata().getName()).get();
                    assertKeycloakStatusCondition(currentKc, KeycloakStatusCondition.READY, false, "Waiting for more replicas");
                });
    }

    @Test
    public void testPreconfiguredPodLabels() {
        Assumptions.assumeTrue(operatorDeployment == OperatorDeployment.local,
                "Skipping the test when Operator deployed remotely to keep stuff simple, it's just SmallRye, we don't need to retest it");

        var kc = getTestKeycloakDeployment(true);
        deployKeycloak(k8sclient, kc, true);

        // labels are set in test/resources/application.properties
        var labels = k8sclient.apps().statefulSets().inNamespace(namespace).withName(kc.getMetadata().getName()).get()
                .getSpec().getTemplate().getMetadata().getLabels();

        var expected = Map.of(
                "test.label", "foobar",
                "testLabelWithExpression", "my-value"
        );
        assertThat(labels).containsAllEntriesOf(expected);
    }

    private void handleFakeImagePullSecretCreation(Keycloak keycloakCR,
                                                   String secretDescriptorFilename) {

        Secret imagePullSecret = getResourceFromFile(secretDescriptorFilename, Secret.class);
        K8sUtils.set(k8sclient, imagePullSecret);
        LocalObjectReference localObjRefAsSecretTmp = new LocalObjectReferenceBuilder().withName(imagePullSecret.getMetadata().getName()).build();
        keycloakCR.getSpec().setImagePullSecrets(Collections.singletonList(localObjRefAsSecretTmp));
    }

    private void assertKeycloakAccessibleViaService(Keycloak kc, boolean https, int port) {
        Awaitility.await()
                .ignoreExceptions()
                .untilAsserted(() -> {
                    String protocol = https ? "https" : "http";

                    String serviceName = KeycloakServiceDependentResource.getServiceName(kc);
                    assertThat(k8sclient.resources(Service.class).withName(serviceName).require().getSpec().getPorts()
                            .stream().map(ServicePort::getName).anyMatch(protocol::equals));

                    String url = protocol + "://" + serviceName + "." + namespace + ":" + port;
                    Log.info("Checking url: " + url);

                    var curlOutput = K8sUtils.inClusterCurl(k8sclient, namespace, url);
                    Log.info("Curl Output: " + curlOutput);

                    assertEquals("200", curlOutput);
                });
    }
}
