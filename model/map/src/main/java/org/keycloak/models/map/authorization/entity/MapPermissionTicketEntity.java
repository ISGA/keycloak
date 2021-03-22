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

package org.keycloak.models.map.authorization.entity;

import java.util.Comparator;
import java.util.UUID;

public class MapPermissionTicketEntity extends AbstractPermissionTicketEntity<UUID> {

    public static final Comparator<AbstractPermissionTicketEntity<UUID>> COMPARE_BY_ID = Comparator.comparing(AbstractPermissionTicketEntity::getId);
    public static final Comparator<AbstractPermissionTicketEntity<UUID>> COMPARE_BY_RESOURCE_ID = Comparator.comparing(AbstractPermissionTicketEntity::getResourceId);


    protected MapPermissionTicketEntity() {
        super();
    }

    public MapPermissionTicketEntity(UUID id) {
        super(id);
    }

    @Override
    public String toString() {
        return String.format("%s@%08x", getId(), System.identityHashCode(this));
    }
}
