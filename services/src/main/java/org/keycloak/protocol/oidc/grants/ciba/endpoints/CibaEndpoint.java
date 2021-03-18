/*
 *
 *  * Copyright 2021  Red Hat, Inc. and/or its affiliates
 *  * and other contributors as indicated by the @author tags.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package org.keycloak.protocol.oidc.grants.ciba.endpoints;

import javax.ws.rs.Path;

import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.keycloak.events.EventBuilder;
import org.keycloak.models.KeycloakSession;
import org.keycloak.protocol.oidc.ext.OIDCExtProvider;
import org.keycloak.protocol.oidc.ext.OIDCExtProviderFactory;

/**
 * @author <a href="mailto:psilva@redhat.com">Pedro Igor</a>
 */
public class CibaEndpoint implements OIDCExtProvider, OIDCExtProviderFactory {

    public static final String PROVIDER_ID = "ciba";

    private final KeycloakSession session;
    private EventBuilder event;

    public CibaEndpoint() {
        // for reflection
        this(null);
    }

    public CibaEndpoint(KeycloakSession session) {
        this.session = session;
    }

    /**
     * The backchannel authentication endpoint used by consumption devices to obtain authorization from end-users.
     *
     * @return
     */
    @Path("/auth")
    public BackchannelAuthenticationEndpoint authorize() {
        BackchannelAuthenticationEndpoint endpoint = new BackchannelAuthenticationEndpoint(session, event);

        ResteasyProviderFactory.getInstance().injectProperties(endpoint);

        return endpoint;
    }

    /**
     * The callback endpoint used by authentication devices tno notify Keycloak about the end-user authentication status.
     *
     * @return
     */
    @Path("/auth/callback")
    public BackchannelAuthenticationCallbackEndpoint authenticate() {
        BackchannelAuthenticationCallbackEndpoint endpoint = new BackchannelAuthenticationCallbackEndpoint(session, event);

        ResteasyProviderFactory.getInstance().injectProperties(endpoint);

        return endpoint;
    }

    @Override
    public OIDCExtProvider create(KeycloakSession session) {
        return new CibaEndpoint(session);
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public void setEvent(EventBuilder event) {
        this.event = event;
    }

    @Override
    public void close() {

    }
}
