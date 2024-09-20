package org.keycloak.services.resources.account;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import org.jboss.resteasy.reactive.NoCache;
import org.keycloak.authentication.requiredactions.DeleteAccount;
import org.keycloak.common.Profile;
import org.keycloak.common.Version;
import org.keycloak.common.util.Environment;
import org.keycloak.models.AccountRoles;
import org.keycloak.models.ClientModel;
import org.keycloak.models.Constants;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RequiredActionProviderModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.protocol.oidc.OIDCLoginProtocolService;
import org.keycloak.protocol.oidc.utils.RedirectUtils;
import org.keycloak.services.Urls;
import org.keycloak.services.managers.AppAuthManager;
import org.keycloak.services.managers.Auth;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.services.resource.AccountResourceProvider;
import org.keycloak.services.resources.AbstractSecuredLocalService;
import org.keycloak.services.resources.RealmsResource;
import org.keycloak.services.util.ResolveRelative;
import org.keycloak.services.util.ViteManifest;
import org.keycloak.services.validation.Validation;
import org.keycloak.theme.FreeMarkerException;
import org.keycloak.theme.Theme;
import org.keycloak.theme.beans.MessageFormatterMethod;
import org.keycloak.theme.freemarker.FreeMarkerProvider;
import org.keycloak.urls.UrlType;
import org.keycloak.util.JsonSerialization;
import org.keycloak.utils.MediaType;
import org.keycloak.utils.StringUtil;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by st on 29/03/17.
 */
public class AccountConsole implements AccountResourceProvider {

    // Used when some other context (ie. IdentityBrokerService) wants to forward error to account management and display it here
    public static final String ACCOUNT_MGMT_FORWARDED_ERROR_NOTE = "ACCOUNT_MGMT_FORWARDED_ERROR";

    private final Pattern bundleParamPattern = Pattern.compile("(\\{\\s*(\\d+)\\s*\\})");

    protected final KeycloakSession session;

    private final AppAuthManager authManager;
    private final RealmModel realm;
    private final ClientModel client;
    private final Theme theme;

    private Auth auth;

    public AccountConsole(KeycloakSession session, ClientModel client, Theme theme) {
        this.session = session;
        this.realm = session.getContext().getRealm();
        this.client = client;
        this.theme = theme;
        this.authManager = new AppAuthManager();
        init();
    }

    public void init() {
        AuthenticationManager.AuthResult authResult = authManager.authenticateIdentityCookie(session, realm);
        if (authResult != null) {
            auth = new Auth(realm, authResult.getToken(), authResult.getUser(), client, authResult.getSession(), true);
        }
    }

    @Override
    public Object getResource() {
        return this;
    }

    @Override
    public void close() {
    }

    @GET
    @NoCache
    @Path("{path:.*}")
    public Response getMainPage(@PathParam("path") String path) throws IOException, FreeMarkerException {

        if (auth == null) {
            return redirectToLogin(path);
        }

        final var serverUriInfo = session.getContext().getUri(UrlType.FRONTEND);
        final var serverBaseUri = serverUriInfo.getBaseUri();
        // Strip any trailing slashes from the URL.
        final var serverBaseUrl = serverBaseUri.toString().replaceFirst("/+$", "");

        final var map = new HashMap<String, Object>();
        final var accountBaseUrl = serverUriInfo.getBaseUriBuilder()
                .path(RealmsResource.class)
                .path(realm.getName())
                .path(Constants.ACCOUNT_MANAGEMENT_CLIENT_ID)
                .path("/")
                .build(realm);

        map.put("serverBaseUrl", serverBaseUrl);
        // TODO: Some variables are deprecated and only exist to provide backwards compatibility for older themes, they should be removed in a future version.
        // Note that these should be removed from the template of the Account Console as well.
        map.put("authUrl", serverBaseUrl + "/"); // Superseded by 'serverBaseUrl', remove in the future.
        map.put("authServerUrl", serverBaseUrl + "/"); // Superseded by 'serverBaseUrl', remove in the future.
        map.put("baseUrl", accountBaseUrl.getPath().endsWith("/") ? accountBaseUrl : accountBaseUrl + "/");
        map.put("realm", realm);
        map.put("clientId", Constants.ACCOUNT_CONSOLE_CLIENT_ID);
        map.put("resourceUrl", Urls.themeRoot(serverBaseUri).getPath() + "/" + Constants.ACCOUNT_MANAGEMENT_CLIENT_ID + "/" + theme.getName());
        map.put("resourceCommonUrl", Urls.themeRoot(serverBaseUri).getPath() + "/common/keycloak");
        map.put("resourceVersion", Version.RESOURCES_VERSION);

        String[] referrer = getReferrer();
        if (referrer != null) {
            map.put("referrer", referrer[0]);
            map.put("referrerName", referrer[1]);
            map.put("referrer_uri", referrer[2]);
        }

        UserModel user = null;
        if (auth != null) user = auth.getUser();
        Locale locale = session.getContext().resolveLocale(user);
        map.put("locale", locale.toLanguageTag());
        Properties messages = theme.getEnhancedMessages(realm, locale);
        map.put("msg", new MessageFormatterMethod(locale, messages));
        map.put("msgJSON", messagesToJsonString(messages));
        map.put("supportedLocales", supportedLocales(messages));
        map.put("properties", theme.getProperties());
        map.put("theme", (Function<String, String>) file -> {
            try {
                final InputStream resource = theme.getResourceAsStream(file);
                return new Scanner(resource, "UTF-8").useDelimiter("\\A").next();
            } catch (IOException e) {
                throw new RuntimeException("could not load file", e);
            }
        });

        map.put("isAuthorizationEnabled", Profile.isFeatureEnabled(Profile.Feature.AUTHORIZATION));

        boolean deleteAccountAllowed = false;
        boolean isViewGroupsEnabled = false;
        if (user != null) {
            RoleModel deleteAccountRole = realm.getClientByClientId(Constants.ACCOUNT_MANAGEMENT_CLIENT_ID).getRole(AccountRoles.DELETE_ACCOUNT);
            deleteAccountAllowed = deleteAccountRole != null && user.hasRole(deleteAccountRole) && realm.getRequiredActionProviderByAlias(DeleteAccount.PROVIDER_ID).isEnabled();
            RoleModel viewGrouRole = realm.getClientByClientId(Constants.ACCOUNT_MANAGEMENT_CLIENT_ID).getRole(AccountRoles.VIEW_GROUPS);
            isViewGroupsEnabled = viewGrouRole != null && user.hasRole(viewGrouRole);
        }

        map.put("deleteAccountAllowed", deleteAccountAllowed);

        map.put("isViewGroupsEnabled", isViewGroupsEnabled);
        map.put("isViewOrganizationsEnabled", Profile.isFeatureEnabled(Profile.Feature.ORGANIZATION));
        map.put("isOid4VciEnabled", Profile.isFeatureEnabled(Profile.Feature.OID4VC_VCI));

        map.put("updateEmailFeatureEnabled", Profile.isFeatureEnabled(Profile.Feature.UPDATE_EMAIL));
        RequiredActionProviderModel updateEmailActionProvider = realm.getRequiredActionProviderByAlias(UserModel.RequiredAction.UPDATE_EMAIL.name());
        map.put("updateEmailActionEnabled", updateEmailActionProvider != null && updateEmailActionProvider.isEnabled());

        final var devServerUrl = Environment.isDevMode() ? System.getenv(ViteManifest.ACCOUNT_VITE_URL) : null;

        if (devServerUrl != null) {
            map.put("devServerUrl", devServerUrl);
        }

        final var manifestFile = theme.getResourceAsStream(ViteManifest.MANIFEST_FILE_PATH);

        if (devServerUrl == null && manifestFile != null) {
            final var manifest = ViteManifest.parseFromInputStream(manifestFile);
            final var entryChunk = manifest.getEntryChunk();
            final var entryStyles = entryChunk.css().orElse(new String[] {});
            final var entryScript = entryChunk.file();
            final var entryImports = entryChunk.imports().orElse(new String[] {});

            map.put("entryStyles", entryStyles);
            map.put("entryScript", entryScript);
            map.put("entryImports", entryImports);
        }

        FreeMarkerProvider freeMarkerUtil = session.getProvider(FreeMarkerProvider.class);
        String result = freeMarkerUtil.processTemplate(map, "index.ftl", theme);
        Response.ResponseBuilder builder = Response.status(Response.Status.OK).type(MediaType.TEXT_HTML_UTF_8).language(Locale.ENGLISH).entity(result);
        return builder.build();
    }

    private Response redirectToLogin(String path) {
        UriBuilder consoleUriBuilder = Urls.accountBase(session.getContext().getUri().getBaseUri());
        if (!StringUtil.isNullOrEmpty(path)) {
            consoleUriBuilder.path(path);
        }
        URI targetUri = consoleUriBuilder.build(realm.getName());

        var oauthRedirect = new AbstractSecuredLocalService.OAuthRedirect();
        oauthRedirect.setAuthUrl(OIDCLoginProtocolService.authUrl(session.getContext().getUri()).build(realm.getName()).toString());
        oauthRedirect.setClientId(client.getClientId());
        oauthRedirect.setSecure(realm.getSslRequired().isRequired(session.getContext().getConnection()));
        return oauthRedirect.redirect(session.getContext().getUri(), targetUri.toString());
    }

    private Map<String, String> supportedLocales(Properties messages) {
        return realm.getSupportedLocalesStream()
                .collect(Collectors.toMap(Function.identity(), l -> messages.getProperty("locale_" + l, l)));
    }

    private String messagesToJsonString(Properties props) {
        if (props == null) return "";
        Properties newProps = new Properties();
        for (String prop : props.stringPropertyNames()) {
            newProps.put(prop, convertPropValue(props.getProperty(prop)));
        }
        try {
            return JsonSerialization.writeValueAsString(newProps);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String convertPropValue(String propertyValue) {
        // this mimics the behavior of java.text.MessageFormat used for the freemarker templates:
        // To print a single quote one needs to write two single quotes.
        // Single quotes will be stripped.
        // Usually single quotes would escape parameters, but this not implemented here.
        propertyValue = propertyValue.replaceAll("'('?)", "$1");
        propertyValue = putJavaParamsInNgTranslateFormat(propertyValue);

        return propertyValue;
    }

    // Put java resource bundle params in ngx-translate format
    // Do you like {0} and {1} ?
    //    becomes
    // Do you like {{param_0}} and {{param_1}} ?
    private String putJavaParamsInNgTranslateFormat(String propertyValue) {
        Matcher matcher = bundleParamPattern.matcher(propertyValue);
        while (matcher.find()) {
            propertyValue = propertyValue.replace(matcher.group(1), "{{param_" + matcher.group(2) + "}}");
        }

        return propertyValue;
    }

    @GET
    @Path("index.html")
    public Response getIndexHtmlRedirect() {
        return Response.status(302).location(session.getContext().getUri().getRequestUriBuilder().path("../").build()).build();
    }

    private String[] getReferrer() {
        String referrer = session.getContext().getUri().getQueryParameters().getFirst("referrer");

        if (referrer == null) {
            return null;
        }

        ClientModel referrerClient = realm.getClientByClientId(referrer);

        if (referrerClient == null) {
            return null;
        }

        String referrerUri = session.getContext().getUri().getQueryParameters().getFirst("referrer_uri");

        if (referrerUri != null) {
            referrerUri = RedirectUtils.verifyRedirectUri(session, referrerUri, referrerClient);
        } else {
            referrerUri = ResolveRelative.resolveRelativeUri(session, referrerClient.getRootUrl(), referrerClient.getBaseUrl());
        }

        if (referrerUri == null) {
            return null;
        }

        String referrerName = referrerClient.getName();

        if (Validation.isBlank(referrerName)) {
            referrerName = referrer;
        }

        return new String[]{referrer, referrerName, referrerUri};
    }

}
