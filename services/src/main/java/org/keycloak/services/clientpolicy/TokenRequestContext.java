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

package org.keycloak.services.clientpolicy;

import javax.ws.rs.core.MultivaluedMap;

import org.keycloak.protocol.oidc.utils.OAuth2CodeParser;
import org.keycloak.services.clientpolicy.ClientPolicyContext;
import org.keycloak.services.clientpolicy.ClientPolicyEvent;

public class TokenRequestContext implements ClientPolicyContext {

    private final MultivaluedMap<String, String> params;
    private final OAuth2CodeParser.ParseResult parseResult;

    public TokenRequestContext(MultivaluedMap<String, String> params,
            OAuth2CodeParser.ParseResult parseResult) {
        this.params = params;
        this.parseResult = parseResult;
    }

    @Override
    public ClientPolicyEvent getEvent() {
        return ClientPolicyEvent.TOKEN_REQUEST;
    }

    public MultivaluedMap<String, String> getParams() {
        return params;
    }

    public OAuth2CodeParser.ParseResult getParseResult() {
        return parseResult;
    }

}
