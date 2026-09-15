package com.planet.importexport.authapi;

import jakarta.inject.Inject;

import com.planet.importexport.authapi.support.BearerTokenTestSupport;
import com.planet.importexport.authapi.support.KeycloakTokenClient;
import com.planet.importexport.mongo.FlapdoodleMongoTestResource;

import org.assertj.core.api.Assertions;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.response.Response;

/**
 * Integration coverage for Group E (Authentication capability; ADR-0006;
 * {@code specs/authentication/spec.md}), exercising the three required
 * scenarios end to end against the real Dev-Services-provisioned Keycloak
 * container (started automatically because {@code quarkus.oidc.tenant-enabled}
 * plus {@code %test.quarkus.keycloak.devservices.enabled=true} are set in
 * {@code application.yml} and no static {@code auth-server-url} is
 * configured for {@code %test}):
 *
 * <ol>
 *   <li>{@code E1}/spec scenario "A request without a valid access token is
 *       rejected": calling a protected endpoint with no {@code Authorization}
 *       header returns {@code 401}.</li>
 *   <li>{@code E1}/spec scenario "A request with a valid access token is
 *       accepted": calling the same endpoint with a bearer token obtained
 *       from Keycloak succeeds (not {@code 401}/{@code 403}).</li>
 *   <li>{@code E2}/spec scenario "A client obtains an access token via the
 *       Client Credentials grant": requesting a token directly from
 *       Keycloak's own {@code /realms/{realm}/protocol/openid-connect/token}
 *       endpoint with {@code grant_type=client_credentials} returns an access
 *       token; {@code E3} additionally confirms that the same response
 *       contains a {@code refresh_token} field — see the Javadoc on
 *       {@link #tokenResponse_refreshTokenFieldPresent()} below for how that
 *       was made to work.</li>
 * </ol>
 *
 * <p>The Dev-Services-managed client id/secret ({@code quarkus-app}/
 * {@code secret}, held by {@link BearerTokenTestSupport}) are Quarkus's own
 * documented defaults for this extension version (3.39.3), preserved as-is by
 * the custom realm-export at {@code src/main/resources/quarkus-realm.json}
 * (wired via {@code quarkus.keycloak.devservices.realm-path}) so that only the
 * client's refresh-token behavior changes (per ADR-0006 — see
 * {@link #tokenResponse_refreshTokenFieldPresent()}) and nothing else about
 * the Dev-Services-provisioned realm/client shifts.
 */
@QuarkusTest
@QuarkusTestResource(FlapdoodleMongoTestResource.class)
class AuthenticationIT {

    private static final String ACCESS_TOKEN = "access_token";
    private static final String TOKEN_TYPE = "token_type";
    private static final String URI_PROTECTED_ENDPOINT = "/api/v1/job-configurations";

    @Inject
    @ConfigProperty(name = KeycloakTokenClient.AUTH_SERVER_URL_PROPERTY)
    String authServerUrl;

    @Inject
    BearerTokenTestSupport bearerTokenTestSupport;

    @Test
    void protectedEndpoint_withoutBearerToken_returns401() {
        RestAssured.given()
                   .when()
                   .get(URI_PROTECTED_ENDPOINT)
                   .then()
                   .statusCode(401);
    }

    @Test
    void protectedEndpoint_withValidBearerToken_isAuthorizedAndProcessed() {
        String accessToken = bearerTokenTestSupport.obtainAccessToken();

        RestAssured.given()
                   .auth()
                   .oauth2(accessToken)
                   .when()
                   .get(URI_PROTECTED_ENDPOINT)
                   .then()
                   .statusCode(200);
    }

    @Test
    void tokenEndpoint_withClientCredentialsGrant_returnsAccessToken() {
        Response response =
                KeycloakTokenClient.requestToken(authServerUrl,
                                                 BearerTokenTestSupport.DEV_SERVICES_CLIENT_ID,
                                                 BearerTokenTestSupport.DEV_SERVICES_CLIENT_SECRET);

        response.then()
                .statusCode(200)
                .body(ACCESS_TOKEN,
                      Matchers.notNullValue())
                .body(TOKEN_TYPE,
                      Matchers.equalToIgnoringCase("bearer"));
    }

    /**
     * {@code E3}: confirms that the Dev-Services-managed {@code quarkus-app}
     * client issues a {@code refresh_token} for the {@code client_credentials}
     * grant, per ADR-0006's "More Information" section and the spec scenario
     * "A client obtains an access token via the Client Credentials grant"
     * ("a refresh token is returned").
     *
     * <p><b>Original state:</b> Quarkus Dev Services for Keycloak's built-in
     * default realm/client only assigns {@code ["microprofile-jwt", "basic"]}
     * as default client scopes, which does not include {@code offline_access}
     * — so Keycloak did not mint a {@code refresh_token} for this grant out of
     * the box.</p>
     *
     * <p><b>Fix applied, per ADR-0006's explicit instruction</b> ("if Dev
     * Services' default realm configuration does not issue refresh tokens for
     * Client Credentials out of the box, that is an implementation-time
     * configuration adjustment, not a new architectural decision"): a custom
     * Dev Services realm-export,
     * {@code src/main/resources/quarkus-realm.json}, wired via
     * {@code quarkus.keycloak.devservices.realm-path} in
     * {@code application.yml}, reproduces the same realm name
     * ({@code quarkus}), client id ({@code quarkus-app}), and secret
     * ({@code secret}) as the Dev-Services default, but additionally sets the
     * client attribute {@code client_credentials.use_refresh_token=true}
     * (Keycloak's "OpenID Connect Compatibility Modes" > "Use Refresh Tokens
     * For Client Credentials Grant" switch — confirmed empirically to be the
     * actual mechanism, since adding {@code offline_access} as a default
     * client scope alone was not sufficient) and adds the built-in
     * {@code offline_access} client scope to the client's <i>default</i>
     * client scopes so the issued refresh token is a long-lived offline
     * token. That combination causes Keycloak to include a
     * {@code refresh_token} field in the {@code client_credentials} token
     * response, confirmed empirically by the assertion below.</p>
     */
    @Test
    void tokenResponse_refreshTokenFieldPresent() {
        Response response =
                KeycloakTokenClient.requestToken(authServerUrl,
                                                 BearerTokenTestSupport.DEV_SERVICES_CLIENT_ID,
                                                 BearerTokenTestSupport.DEV_SERVICES_CLIENT_SECRET);

        response.then()
                .statusCode(200);

        boolean refreshTokenPresent =
                response.jsonPath().get("refresh_token") != null;

        /*
         * Confirmed empirically: with the custom Dev Services realm-export
         * (src/main/resources/quarkus-realm.json) adding "offline_access" as
         * a DEFAULT client scope and the "client_credentials.use_refresh_token"
         * client attribute on "quarkus-app", Keycloak now issues a
         * refresh_token for the client_credentials grant, per ADR-0006's
         * explicit "More Information" instruction to fix this via realm/client
         * configuration rather than merely document its absence.
         */
        Assertions.assertThat(refreshTokenPresent)
                  .isTrue();
    }
}
