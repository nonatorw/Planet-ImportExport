# import-export

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: <https://quarkus.io/>.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./gradlew quarkusDev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Packaging and running the application

The application can be packaged using:

```shell script
./gradlew build
```

It produces the `quarkus-run.jar` file in the `build/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `build/quarkus-app/lib/` directory.

The application is now runnable using `java -jar build/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./gradlew build -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar build/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./gradlew build -Dquarkus.native.enabled=true
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./gradlew build -Dquarkus.native.enabled=true -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./build/import-export-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/gradle-tooling>.

## Authentication

All `/api/v1/**` endpoints require a valid OAuth2 access token (Client Credentials grant), issued by Keycloak. In dev/test, Keycloak is provisioned automatically by Quarkus Dev Services — no manual setup needed.

**Access control model:** every authenticated client can call every endpoint (`@Authenticated`, no role/scope differentiation). This matches the current requirements, which define only "authenticated or not," not per-client permissions. If a future need arises to restrict specific clients to specific capabilities (e.g. a client that may only export, never import), this can be added with `@RolesAllowed` per endpoint plus matching Keycloak client roles/scopes — no architectural change required, just additional configuration.

**Refresh tokens in dev/test:** the Dev-Services-managed Keycloak client issues a `refresh_token` for the Client Credentials grant. Although RFC 6749 does not mandate a refresh token for this grant, ADR-0006 requires one, so the Dev Services realm is provisioned from a custom realm-export (`src/main/resources/quarkus-realm.json`, wired via `quarkus.keycloak.devservices.realm-path`) that keeps the same realm/client id/secret as the Quarkus default but sets the client attribute `client_credentials.use_refresh_token=true` (Keycloak's "OpenID Connect Compatibility Modes" > "Use Refresh Tokens For Client Credentials Grant") and adds `offline_access` as a default client scope, so the token endpoint's response now includes a `refresh_token` (confirmed empirically; see `AuthenticationIT#tokenResponse_refreshTokenFieldPresent`). A real, non-Dev-Services production deployment must configure the same client attribute and scope on its own Keycloak realm to preserve this behavior.

## Related Guides

- REST ([guide](https://quarkus.io/guides/rest)): Build RESTful web services and APIs using Jakarta REST (formerly JAX-RS)
- REST Jackson ([guide](https://quarkus.io/guides/rest#json-serialisation)): Jackson serialization support for Quarkus REST. This extension is not compatible with the quarkus-resteasy extension, or any of the extensions that depend on it
- Hibernate Validator ([guide](https://quarkus.io/guides/validation)): Bean validation using Hibernate Validator and Jakarta Validation annotations
- MongoDB with Panache ([guide](https://quarkus.io/guides/mongodb-panache)): Simplify your persistence code for MongoDB via the active record or the repository pattern
- OpenID Connect ([guide](https://quarkus.io/guides/security-openid-connect)): Secure applications with OpenID Connect and OAuth 2.0 using bearer tokens and authorization code flow

## Provided Code

### REST

Easily start your REST Web Services

[Related guide section...](https://quarkus.io/guides/getting-started-reactive#reactive-jax-rs-resources)
