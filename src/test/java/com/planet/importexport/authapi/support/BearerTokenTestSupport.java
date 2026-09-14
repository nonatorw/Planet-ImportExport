package com.planet.importexport.authapi.support;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * CDI-injectable test helper that resolves a fresh OAuth2 access token from
 * the Dev-Services-provisioned Keycloak realm, for {@code @QuarkusTest}
 * classes that exercise now-{@code @Authenticated} resources (Group E,
 * ADR-0006) but are not themselves testing authentication — e.g. Import,
 * Export, and Job Configuration's {@code *IT} classes, which need a valid
 * bearer token purely to reach their endpoints under test.
 *
 * <p>Kept separate from {@link KeycloakTokenClient} (the lower-level HTTP
 * helper used by {@code AuthenticationIT} itself) so that business-capability
 * tests depend on a one-line {@code @Inject BearerTokenTestSupport} rather
 * than repeating the realm-URL/client-id/client-secret wiring in every test
 * class.
 */
@ApplicationScoped
public class BearerTokenTestSupport {

    /**
     * The Dev-Services-provisioned client id, per Quarkus 3.39.3's
     * {@code KeycloakDevServicesProcessor} default (confirmed by
     * decompilation; see {@code AuthenticationIT}'s class Javadoc). Exposed
     * publicly so every {@code *IT} class shares this one named constant
     * instead of repeating the literal.
     */
    public static final String DEV_SERVICES_CLIENT_ID = "quarkus-app";

    /**
     * The Dev-Services-provisioned client secret, per the same default.
     */
    public static final String DEV_SERVICES_CLIENT_SECRET = "secret";

    @ConfigProperty(name = "quarkus.oidc.auth-server-url")
    String authServerUrl;

    /**
     * @return a freshly obtained access token, valid for use as a bearer
     *         token against any {@code /api/v1/**} endpoint
     */
    public String obtainAccessToken() {
        return KeycloakTokenClient.obtainAccessToken(authServerUrl,
                                                      DEV_SERVICES_CLIENT_ID,
                                                      DEV_SERVICES_CLIENT_SECRET);
    }
}
