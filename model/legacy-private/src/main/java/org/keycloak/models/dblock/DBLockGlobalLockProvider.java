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

package org.keycloak.models.dblock;

import org.jboss.logging.Logger;
import org.keycloak.models.locking.GlobalLock;
import org.keycloak.models.locking.GlobalLockProvider;

import java.time.Duration;
import java.util.Objects;

import static org.keycloak.models.locking.GlobalLock.Constants.KEYCLOAK_BOOT;

public class DBLockGlobalLockProvider implements GlobalLockProvider {

    private static final Logger LOG = Logger.getLogger(DBLockGlobalLockProvider.class);
    public static final String DATABASE = "database";
    private final DBLockProvider dbLockProvider;
    public DBLockGlobalLockProvider(DBLockProvider dbLockProvider) {
        this.dbLockProvider = dbLockProvider;
    }

    private static DBLockProvider.Namespace stringToNamespace(String lockName) {
        switch (lockName) {
            case DATABASE:
                return DBLockProvider.Namespace.DATABASE;
            case KEYCLOAK_BOOT:
                return DBLockProvider.Namespace.KEYCLOAK_BOOT;
            default:
                throw new RuntimeException("Lock with name " + lockName + " not supported by DBLockGlobalLockProvider.");
        }
    }

    @Override
    public GlobalLock acquire(String lockName, Duration timeToWaitForLock) {
        Objects.requireNonNull(lockName, "lockName cannot be null");

        if (timeToWaitForLock != null) {
            LOG.debug("DBLockGlobalLockProvider does not support setting timeToWaitForLock per lock.");
        }
        dbLockProvider.waitForLock(stringToNamespace(lockName));
        return () -> releaseLock(lockName);
    }

    private void releaseLock(String lockName) {
        if (dbLockProvider.getCurrentLock() != stringToNamespace(lockName)) {
            throw new RuntimeException("Requested releasing lock with name " + lockName + ", but lock is currently acquired for " + dbLockProvider.getCurrentLock() + ".");
        }

        dbLockProvider.releaseLock();
    }

    @Override
    public void forceReleaseAllLocks() {
        if (dbLockProvider.supportsForcedUnlock()) {
            dbLockProvider.releaseLock();
        } else {
            throw new IllegalStateException("Forced unlock requested, but provider " + dbLockProvider + " does not support it.");
        }
    }

    @Override
    public void close() {

    }
}
