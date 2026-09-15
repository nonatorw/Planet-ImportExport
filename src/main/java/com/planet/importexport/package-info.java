/**
 * Root package of the Planet Import/Export service.
 *
 * <p>This file carries the application-wide MicroProfile OpenAPI definition:
 * API metadata ({@link Info}) and the {@code bearerAuth} {@link SecurityScheme}.
 * <p>
 *
 * <p>{@code E1} (ADR-0006): every REST resource in this project requires a
 * valid OIDC bearer token issued by Keycloak via the OAuth2 Client
 * Credentials grant (see
 * {@code com.planet.importexport.importapi.ImportResource},
 * {@code com.planet.importexport.exportapi.ExportResource}, and
 * {@code com.planet.importexport.jobconfigapi.JobConfigurationResource}, all
 * annotated with {@code io.quarkus.security.Authenticated}). Without a
 * declared security scheme, {@code /q/swagger-ui} gave no visual indication
 * that authentication is required, so callers exploring the API there would
 * otherwise hit {@code 401 Unauthorized} with no hint in the UI itself. Each
 * resource references the {@code bearerAuth} scheme declared here via a
 * class-level {@code @SecurityRequirement(name = "bearerAuth")}, matching the
 * class-level {@code @Authenticated} scope.</p>
 *
 * <p>{@link OpenAPIDefinition} is declared here, on {@code package-info.java},
 * rather than on a plain class: confirmed empirically (via
 * {@code curl /q/openapi} against a running {@code quarkusDev} instance, then
 * by inspecting SmallRye OpenAPI's own
 * {@code OpenApiAnnotationScanner.processPackageOpenAPIDefinitions} bytecode)
 * that SmallRye OpenAPI's annotation scanner only honors
 * {@code @OpenAPIDefinition} when its annotation target's simple class name
 * is literally {@code package-info} — an {@code @OpenAPIDefinition} on an
 * arbitrary class (even one dedicated solely to carrying it) is silently
 * ignored, regardless of the annotation's formal
 * {@code @Target({TYPE, PACKAGE})}.</p>
 *
 * <p>{@code quarkus.smallrye-openapi.auto-add-security}/
 * {@code auto-add-security-requirement} are disabled in
 * {@code application.yml}: with {@code quarkus-oidc} active, Quarkus
 * otherwise auto-generates its own {@code openIdConnect}-type scheme and
 * attaches it to every operation, which — confirmed empirically — replaced
 * this scheme entirely rather than coexisting with it.</p>
 */
@OpenAPIDefinition(info = @Info(
    title = "Planet Import/Export API",
    version = "1.0.0-SNAPSHOT",
    description = "Import, export, and job configuration API. Every endpoint requires a valid OIDC bearer token (ADR-0006)."),
    components = @Components(
        securitySchemes = @SecurityScheme(
            securitySchemeName = "bearerAuth",
            type = SecuritySchemeType.HTTP,
            scheme = "bearer",
            bearerFormat = "JWT",
            description = "OIDC bearer token issued by Keycloak via the OAuth2 Client Credentials grant (ADR-0006). " +
                          "Obtain one from the token endpoint and paste it here to authorize requests.")))
package com.planet.importexport;

import org.eclipse.microprofile.openapi.annotations.Components;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
