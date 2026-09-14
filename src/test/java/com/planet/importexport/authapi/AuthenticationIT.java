package com.planet.importexport.authapi;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import com.planet.importexport.authapi.support.BearerTokenTestSupport;
import com.planet.importexport.authapi.support.KeycloakTokenClient;
import com.planet.importexport.mongo.FlapdoodleMongoTestResource;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;

/**
 * Integration coverage for Group E (Authentication capability; ADR-0006;
 * {@code specs/authentication/spec.md}), exercising the three required
 * scenarios end to end against the real Dev-Services-provisioned Keycloak
 * container (started automatically because {@code quarkus.oidc.tenant-enabled}
 * plus {@code %test.quarkus.keycloak.devservices.enabled=true} are set in
 * {@code application.properties} and no static {@code auth-server-url} is
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
 *       token; {@code E3} additionally documents what the same response
 *       contains (or does not contain) under {@code refresh_token} — see the
 *       Javadoc on {@link #tokenResponse_refreshTokenFieldObservation()}
 *       below for the investigation outcome.</li>
 * </ol>
 *
 * <p>The Dev-Services-managed client id/secret ({@code quarkus-app}/
 * {@code secret}, held by {@link BearerTokenTestSupport}) are Quarkus's own
 * documented defaults for this extension version (3.39.3) — confirmed by
 * decompiling
 * {@code io.quarkus.devservices.keycloak.KeycloakDevServicesProcessor}'s
 * {@code getOidcClientId}/{@code getOidcClientSecret} methods, since no
 * project-level override is configured in {@code application.properties} for
 * {@code %test} (deliberately: overriding them would fight Dev Services'
 * "no manual setup" convenience, per ADR-0006).
 */
@QuarkusTest
@QuarkusTestResource(FlapdoodleMongoTestResource.class)
class AuthenticationIT {

    private static final String PROTECTED_ENDPOINT = "/api/v1/job-configurations";

    @Inject
    @ConfigProperty(name = "quarkus.oidc.auth-server-url")
    String authServerUrl;

    @Inject
    BearerTokenTestSupport bearerTokenTestSupport;

    @Test
    void protectedEndpoint_withoutBearerToken_returns401() {
        given().when()
               .get(PROTECTED_ENDPOINT)
               .then()
               .statusCode(401);
    }

    @Test
    void protectedEndpoint_withValidBearerToken_isAuthorizedAndProcessed() {
        String accessToken = bearerTokenTestSupport.obtainAccessToken();

        given().auth()
               .oauth2(accessToken)
               .when()
               .get(PROTECTED_ENDPOINT)
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
               .body("access_token", org.hamcrest.Matchers.notNullValue())
               .body("token_type", org.hamcrest.Matchers.equalToIgnoringCase("bearer"));
    }

    /**
     * {@code E3} investigation: documents whether the Dev-Services-managed
     * {@code quarkus-app} client issues a {@code refresh_token} for the
     * {@code client_credentials} grant, as required by ADR-0006's "More
     * Information" section and the spec scenario "A client obtains an access
     * token via the Client Credentials grant" ("a refresh token is returned,
     * if the identity provider's client configuration issues one for this
     * grant").
     *
     * <p><b>What was tried:</b> requested a token from the Dev-Services realm
     * (realm name {@code quarkus}, client {@code quarkus-app}) with
     * {@code grant_type=client_credentials} and inspected the full JSON
     * response body.
     *
     * <p><b>What Keycloak actually returned:</b> the response contains
     * {@code access_token}, {@code token_type}, {@code expires_in}, and
     * {@code scope}, but <b>no {@code refresh_token} field</b>. This was
     * confirmed empirically by this test (see the assertion below) rather
     * than assumed.
     *
     * <p><b>Root cause, confirmed by decompiling
     * {@code io.quarkus.devservices.keycloak.KeycloakDevServicesProcessor}
     * (Quarkus 3.39.3, the version pinned in {@code gradle.properties}):</b>
     * the Dev-Services-provisioned client is created with
     * {@code setDefaultClientScopes(List.of("microprofile-jwt", "basic"))} —
     * it does not include {@code offline_access} or any other scope that
     * would cause Keycloak to mint a refresh token. This is consistent with
     * OAuth2 RFC 6749: the Client Credentials grant has no notion of a user
     * session to refresh (the client re-authenticates with its own
     * credentials on every token request instead), so Keycloak's default
     * behavior for a service-account/client-credentials client — and,
     * separately, Keycloak's own "Standard Token Exchange"/service-account
     * documentation — is to omit {@code refresh_token} for this grant unless
     * the client is explicitly configured otherwise.
     *
     * <p><b>Whether this is achievable via Quarkus Dev Services property
     * overrides:</b> no. As of Quarkus 3.39.3,
     * {@code quarkus.keycloak.devservices.*} exposes only coarse-grained
     * controls ({@code realm-name}, {@code realm-path} for a fully custom
     * realm JSON import, {@code roles.*}, {@code users.*}, {@code create-realm},
     * {@code create-client}) — there is no
     * {@code quarkus.keycloak.devservices.client.default-scopes} (or
     * equivalent per-client-scope) property to add {@code offline_access} to
     * the auto-generated {@code quarkus-app} client. The only Dev-Services
     * path to a different client configuration is
     * {@code quarkus.keycloak.devservices.realm-path}, which replaces the
     * entire generated realm with a hand-authored realm-export JSON file —
     * out of proportion for this one flag, and arguably reintroduces exactly
     * the "manually maintained IdP configuration" friction ADR-0006 chose
     * Dev Services to avoid.
     *
     * <p><b>Concrete recommendation for a real (non-Dev-Services) Keycloak
     * realm:</b> on the target client (e.g. via the Keycloak Admin Console or
     * a realm-export JSON), add the built-in {@code offline_access} client
     * scope to the client's <i>default</i> client scopes (not optional), and
     * ensure the realm's {@code Refresh Token} client capability is on. Note
     * that even then, Keycloak versions from the Keycloak 22+ line (which
     * this Dev Services image tracks) log an explicit warning and, depending
     * on version, may still refuse to issue a refresh token for
     * {@code client_credentials} because the grant is stateless by design —
     * operators who need a renewable machine-to-machine credential without
     * re-sending the client secret each time should instead shorten the
     * access-token lifetime and let the client simply request a new token via
     * {@code client_credentials} again (its credentials do not expire the way
     * a refresh token would), which is the standard, spec-aligned pattern for
     * this grant type rather than working around the missing refresh token.
     * This is a documentation/configuration recommendation only — no
     * production Keycloak realm exists in this project to apply it to (ADR-0006:
     * production requires an externally managed Keycloak, out of scope here).
     */
    @Test
    void tokenResponse_refreshTokenFieldObservation() {
        Response response =
                KeycloakTokenClient.requestToken(authServerUrl,
                                                 BearerTokenTestSupport.DEV_SERVICES_CLIENT_ID,
                                                 BearerTokenTestSupport.DEV_SERVICES_CLIENT_SECRET);

        response.then().statusCode(200);

        boolean refreshTokenPresent =
                response.jsonPath().get("refresh_token") != null;

        // Empirically confirmed (see class Javadoc): the Dev-Services default
        // realm/client does NOT issue a refresh token for client_credentials,
        // because its default client scopes are ["microprofile-jwt", "basic"]
        // with no offline_access. This assertion pins that observed behavior
        // so a future Quarkus/Keycloak Dev Services upgrade that silently
        // changes it is caught rather than assumed away.
        assertThat(refreshTokenPresent).isFalse();
    }
}
