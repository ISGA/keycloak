#!/bin/bash

dnf install -y java-21-openjdk-devel crypto-policies-scripts
fips-mode-setup --enable --no-bootcfg
fips-mode-setup --is-enabled
if [ $? -ne 0 ]; then
  exit 1
fi
export JAVA_HOME=/etc/alternatives/java_sdk_21

# Build all dependent modules
./mvnw install -nsu -B -am -pl crypto/default,crypto/fips1402 -DskipTests

./mvnw test -nsu -B -pl crypto/default,crypto/fips1402 -Dcom.redhat.fips=true
if [ $? -ne 0 ]; then
  exit 1
fi

# NOTE the use of  "org.bouncycastle.rsa.allow_pkcs15_enc" as per BCFIPS release notes:
#
# End of 2023 transition for RSA PKCS1.5 encryption. The provider blocks RSA with PKCS1.5 encryption.
# The following property can be used to override the default behavior:
# org.bouncycastle.rsa.allow_pkcs15_enc (allow use of PKCS1.5)
# This is required by crypto/fips1402/src/test/java/org/keycloak/crypto/fips/test/FIPS1402JWETest.java
./mvnw test -nsu -B -pl crypto/default,crypto/fips1402 -Dcom.redhat.fips=true -Dorg.bouncycastle.fips.approved_only=true -Dorg.bouncycastle.rsa.allow_pkcs15_enc=true
