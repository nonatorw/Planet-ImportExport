package com.planet.importexport.authapi.support;

import java.util.Map;

import io.restassured.RestAssured;
import io.restassured.response.Response;

/**
 * Test-only helper that obtains an OAuth2 access token from the
 * Dev-Services-provisioned Keycloak realm's own token endpoint, using the
 * {@code client_credentials} grant (ADR-0006; spec: authentication,
 * "A client obtains an access token via the Client Credentials grant").
 *
 * <p>This is deliberately the only place in the test tree that talks to
 * Keycloak's {@code /protocol/openid-connect/token} endpoint directly: every
 * {@code *IT} class that needs an authenticated request obtains its token
 * through this helper rather than duplicating the HTTP call, keeping E1's
 * test coverage consistent with the "no custom token endpoint" rule (ADR-0006
 * — token issuance is Keycloak's job, not this application's, and not
 * reimplemented ad hoc in test code either).
 */
public final class KeycloakTokenClient {
    /**
     * The Quarkus OIDC config key exposing the Dev-Services-provisioned
     * realm's base URL. Shared here (rather than duplicated) because both
     * {@link com.planet.importexport.authapi.AuthenticationIT} and
     * {@link BearerTokenTestSupport} inject it via {@code @ConfigProperty}.
     */
    public static final String AUTH_SERVER_URL_PROPERTY =
            "quarkus.oidc.auth-server-url";

    private static final String ACCESS_TOKEN = "access_token";
    private static final String CLIENT_CREDENTIALS_GRANT = "client_credentials";
    private static final String CLIENT_ID_FIELD = "client_id";
    private static final String CLIENT_SECRET_FIELD = "client_secret";
    private static final String CONTENT_TYPE = "application/x-www-form-urlencoded";
    private static final String GRANT_TYPE_FIELD = "grant_type";

    private static final String URI_PROTOCOL_OPENID_CONNECT_TOKEN =
            "/protocol/openid-connect/token";

    /**
     * Private constructor: this class only exposes static helpers.
     */
    private KeycloakTokenClient() {
    }

    /**
     * Requests a token from {@code authServerUrl} (the realm's own base URL,
     * e.g. {@code http://localhost:32771/realms/quarkus}, exactly as exposed
     * by {@code quarkus.oidc.auth-server-url} once Dev Services has started
     * the container) using {@code grant_type=client_credentials}.
     *
     * @param authServerUrl the realm base URL, as Dev Services configured it
     * @param clientId      the OAuth2 client id registered in that realm
     * @param clientSecret  the OAuth2 client secret registered in that realm
     *
     * @return the raw REST Assured {@link Response}, left unvalidated so
     *         callers can assert on status code and body shape themselves
     *         (used both for the "happy path" token request and for the
     *         {@code E3} refresh-token field inspection)
     */
    public static Response requestToken(String authServerUrl,
                                        String clientId,
                                        String clientSecret) {
        return RestAssured.given()
                          .contentType(CONTENT_TYPE)
                          .formParams(Map.of(GRANT_TYPE_FIELD,
                                             CLIENT_CREDENTIALS_GRANT,
                                             CLIENT_ID_FIELD,
                                             clientId,
                                             CLIENT_SECRET_FIELD,
                                             clientSecret))
                          .when()
                          .post(authServerUrl + URI_PROTOCOL_OPENID_CONNECT_TOKEN);
    }

    /**
     * Convenience wrapper over {@link #requestToken} for tests that only need
     * a bearer token string and can assume the request succeeds.
     *
     * @param authServerUrl the realm base URL, as Dev Services configured it
     * @param clientId      the OAuth2 client id registered in that realm
     * @param clientSecret  the OAuth2 client secret registered in that realm
     *
     * @return the {@code access_token} field from the token response
     */
    public static String obtainAccessToken(String authServerUrl,
                                           String clientId,
                                           String clientSecret) {
        return requestToken(authServerUrl,
                            clientId,
                            clientSecret).then()
                                         .statusCode(200)
                                         .extract()
                                         .path(ACCESS_TOKEN);
    }
}
