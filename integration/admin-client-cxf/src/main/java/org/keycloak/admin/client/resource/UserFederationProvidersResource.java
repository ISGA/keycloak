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

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.keycloak.representations.idm.UserFederationProviderFactoryRepresentation;
import org.keycloak.representations.idm.UserFederationProviderRepresentation;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public interface UserFederationProvidersResource {

    @GET
    @Path("providers")
    @Produces(MediaType.APPLICATION_JSON)
    List<UserFederationProviderFactoryRepresentation> getProviderFactories();


    @GET
    @Path("providers/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    UserFederationProviderFactoryRepresentation getProviderFactory(@PathParam("id") String id);


    @POST
    @Path("instances")
    @Consumes(MediaType.APPLICATION_JSON)
    Response create(UserFederationProviderRepresentation rep);


    @GET
    @Path("instances")
    @Produces(MediaType.APPLICATION_JSON)
    List<UserFederationProviderRepresentation> getProviderInstances();


    @Path("instances/{id}")
    UserFederationProviderResource get(@PathParam("id") String id);

}
