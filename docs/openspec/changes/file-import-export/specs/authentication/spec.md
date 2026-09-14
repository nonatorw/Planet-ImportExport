# Capability: Authentication

## ADDED Requirements

### Requirement: OAuth2 Client Credentials protection on every endpoint

The system SHALL require a valid OAuth2 access token, obtained via the Client Credentials grant, on every import, export, job status, and job configuration endpoint. The system SHALL reject any request lacking a valid access token.

#### Scenario: A request without a valid access token is rejected

- **GIVEN** no valid access token is provided
- **WHEN** the import endpoint is called
- **THEN** the request is rejected as unauthorized

#### Scenario: A request with a valid access token is accepted

- **GIVEN** a valid access token obtained via the client_credentials grant
- **WHEN** the import endpoint is called with that access token
- **THEN** the request is authorized and processed

### Requirement: Standards-compliant token issuance via a ready-made OIDC provider

The system SHALL rely on a ready-made Quarkus ecosystem OIDC extension and a real Keycloak identity provider (provisioned via Quarkus Dev Services for local/dev/test runs) for token issuance, rather than implementing a custom OAuth2 authorization server.

#### Scenario: A client obtains an access token via the Client Credentials grant

- **GIVEN** a registered OAuth2 client with a valid client id and client secret
- **WHEN** the client requests a token from the identity provider's token endpoint using the `client_credentials` grant
- **THEN** an access token is returned
- **AND** a refresh token is returned, if the identity provider's client configuration issues one for this grant

### Requirement: No custom-built authorization server

The system SHALL NOT implement its own token-signing, token-validation, or grant-handling logic; all such behavior SHALL be delegated to the Quarkus OIDC extension and the Keycloak identity provider.

#### Scenario: The application performs no local token issuance

- **GIVEN** the application is running with Quarkus OIDC configured against the Dev-Services-provisioned Keycloak realm
- **WHEN** a client requests a token
- **THEN** the token is issued by Keycloak, not by an endpoint implemented in this application's own codebase
