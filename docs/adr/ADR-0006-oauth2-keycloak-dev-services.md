---
status: "accepted"
date: 2026-09-14
decision-makers: Product owner (user), Solutions Architect agent
consulted: Business Analyst agent (functional-specification.md, decision 12)
informed: DevOps/Platform Engineer agent, Technical Writer agent
---

# Protect the REST API with OAuth2 Client Credentials via Quarkus OIDC and Keycloak Dev Services

## Context and Problem Statement

The REST API must be protected end-to-end using OAuth2 Client Credentials (machine-to-machine, no end user), and the exercise must expose a token-issuance capability rather than assume an already-running external identity provider. The product owner was asked how access/refresh tokens should be issued and confirmed: use a real Keycloak instance as the Identity Provider, provisioned via Quarkus Dev Services (which manages a Keycloak container automatically for local runs), rather than a hand-built token endpoint. This is an explicit, accepted departure from the project's earlier "no external infrastructure" framing: Docker becomes a dependency for authentication, even though the database (ADR-0001) remains infrastructure-free via embedded MongoDB.

## Decision Drivers

* Confirmed decision 12 mandates a ready-made Quarkus ecosystem OAuth2/OIDC extension, not a custom-built authorization server.
* The product owner explicitly chose a real Keycloak IdP over alternatives (e.g., a minimal hand-rolled Client Credentials endpoint, or a mocked/simplified OIDC provider) for realism and standards compliance.
* Quarkus Dev Services must be used to avoid requiring the developer/evaluator to manually stand up and configure Keycloak — `./gradlew quarkusDev` / test runs should provision it automatically.
* The functional specification and Root Cause/Context Mapping sections must accurately reflect that Docker is now a required dependency (already corrected in `functional-specification.md`, Context Mapping — External dependencies).

## Decision Outcome

Chosen option: "Quarkus OIDC extension + Keycloak, provisioned via Quarkus Dev Services", because it is the explicit, confirmed product decision, and because it satisfies decision 12's "ready-made extension" requirement precisely: `quarkus-oidc` (resource-server side, enforcing bearer-token validation on protected endpoints) together with Quarkus Dev Services for Keycloak (which auto-starts a Keycloak container, pre-provisioned with a realm/client, for `quarkusDev` and tests) removes the need for a hand-built authorization server while still issuing real, standards-compliant OAuth2 tokens via the Client Credentials grant. Token issuance itself is delegated to Keycloak's own `/protocol/openid-connect/token` endpoint (exposed through Dev Services), not reimplemented by the application; the application's own REST resources are OIDC resource servers that validate the bearer token Keycloak issued.

### Consequences

* Good, because token issuance, signing, and validation follow a real, standards-compliant OAuth2/OIDC implementation rather than a custom approximation — directly satisfying decision 12's "ready-made extension" requirement and reducing security risk (ISO/IEC 27001 Annex A.8 — Technological Controls: authentication should rely on vetted, standard mechanisms rather than bespoke token handling).
* Good, because Quarkus Dev Services removes manual setup friction: running `./gradlew quarkusDev` or `./gradlew test` auto-provisions Keycloak with no developer action, keeping the "runs locally" experience largely intact despite the new Docker dependency.
* Bad, because the project is no longer infrastructure-free end-to-end: Docker must be installed and running for the application to start in dev/test mode, or for a client to obtain a token. This is now stated explicitly in `functional-specification.md` (Context Mapping — External dependencies) rather than left implicit.
* Bad, because a refresh-token flow is being requested (decision 12/acceptance criteria) alongside Client Credentials, which by OAuth2 specification (RFC 6749) does not mandate refresh tokens for the Client Credentials grant — Keycloak can be configured to issue one, but this is a Keycloak-side realm/client configuration choice, not an inherent property of the grant type itself. This is configured on the Dev Services realm via `src/main/resources/quarkus-realm.json` (the `client_credentials.use_refresh_token` client attribute plus the `offline_access` default client scope) and confirmed empirically by `AuthenticationIT#tokenResponse_refreshTokenFieldPresent`; a real, non-Dev-Services deployment must configure the same attribute/scope on its own Keycloak realm (see "More Information").
* Neutral, because production deployment would require a real, externally managed Keycloak (or another OIDC provider) rather than a Dev-Services-provisioned one — Dev Services is explicitly a development/test convenience, not a production topology; this is a DevOps/Platform Engineer concern for a future deployment topology decision, out of scope for this ADR.

### Confirmation

* Covered by `docs/requirements/acceptance-criteria.feature`, Feature "Protect the REST API with OAuth2 Client Credentials" (all three scenarios): token issuance via Client Credentials grant, rejection without a valid token, and acceptance with a valid token.
* An integration test (using `@QuarkusTest` with Dev Services active) requests a token from the Dev-Services-provisioned Keycloak realm using `client_credentials`, then calls a protected endpoint (e.g., the import endpoint) with the returned bearer token and asserts a 2xx/expected response; a second test omits the token and asserts 401.
* A build/CI precondition check (DevOps/Platform Engineer's concern) confirms Docker is available in the build environment before tests requiring Dev Services run, so failures are diagnosed as "Docker unavailable" rather than misattributed to application logic.

## Pros and Cons of the Options

### Quarkus OIDC + Keycloak via Dev Services (chosen)

* Good, because it is a real, standards-compliant IdP with no custom token-signing/validation code to write or audit.
* Good, because Dev Services automates provisioning for local development and tests.
* Bad, because it introduces a hard Docker dependency where none existed before.

### Hand-built Client Credentials token endpoint (custom authorization server)

* Bad, because confirmed decision 12 explicitly rules this out ("must use a ready-made Quarkus ecosystem extension... rather than a hand-built OAuth2 server").
* Bad, because implementing token signing, key rotation, and grant validation correctly and securely is a substantial undertaking disproportionate to this exercise's scope, and is exactly the kind of security-sensitive code ISO/IEC 27001 Annex A.8 guidance discourages reinventing when a vetted standard implementation is available.

### Quarkus OIDC against a statically configured external Keycloak (not Dev Services)

* Good, because it would remove the Dev-Services-managed container lifecycle from the local dev loop.
* Bad, because it would require the developer/evaluator to manually install, configure, and maintain a separate long-lived Keycloak instance, realm, and client — directly contradicting the "runs locally with minimal setup" spirit that Dev Services preserves despite the new Docker dependency.

## More Information

See `docs/requirements/functional-specification.md`, confirmed decision 12, and the corrected Context Mapping — External dependencies paragraph acknowledging Docker as a dependency for authentication only (not for storage, which remains embedded MongoDB per ADR-0001). Realm/client configuration details (client id/secret provisioning, refresh-token issuance toggle on the Dev-Services-managed realm, token lifetime) are implementation-level configuration to be finalized by the Java Full-stack Engineer agent during implementation, following this ADR's direction; if Dev Services' default realm configuration does not issue refresh tokens for Client Credentials out of the box, that is an implementation-time configuration adjustment, not a new architectural decision.
