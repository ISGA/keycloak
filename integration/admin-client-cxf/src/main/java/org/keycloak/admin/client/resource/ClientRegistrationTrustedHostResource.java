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

package org.keycloak.admin.client.resource;

import org.keycloak.representations.idm.ClientRegistrationTrustedHostRepresentation;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public interface ClientRegistrationTrustedHostResource {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Response create(ClientRegistrationTrustedHostRepresentation config);

    @PUT
    @Path("{hostname}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Response update(final @PathParam("hostname") String hostName, ClientRegistrationTrustedHostRepresentation config);

    @GET
    @Path("{hostname}")
    @Produces(MediaType.APPLICATION_JSON)
    ClientRegistrationTrustedHostRepresentation get(final @PathParam("hostname") String hostName);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    List<ClientRegistrationTrustedHostRepresentation> list();

    @DELETE
    @Path("{hostname}")
    void delete(final @PathParam("hostname") String hostName);

}
