# Planet Import/Export Service

A backend REST service that imports customer records from CSV files asynchronously and exports stored records as CSV, TXT, or XLSX. It tolerates heterogeneous or invalid input row-by-row (invalid/missing values and unknown columns are staged with an error description, not rejected outright), applies an explicit versioning policy for repeated records, and protects every endpoint with OAuth2 Client Credentials.

This project was built as a single, cohesive, greenfield deliverable. Every functional decision below traces back to `docs/requirements/functional-specification.md` and `docs/openspec/changes/file-import-export/proposal.md`, and every architectural decision traces to an ADR under `docs/adr/`.

## What this service does

- **Import**: accepts a request naming a single CSV file already available on the local/mounted filesystem, and responds immediately with a `jobId` — the file is then processed asynchronously in the background.
- **Job status**: exposes a query endpoint returning a numeric summary (rows processed/succeeded/failed) and the full list of staging entries (rejected rows, with a reason) for a given `jobId`.
- **Export**: accepts a target format (`CSV`, `TXT`, or `XLSX`) and an ordered list of columns, and returns the current version of every stored record with exactly those columns, in exactly that order.
- **Job configuration**: a generic CRUD API over runtime settings (starting with the import `chunkSize`), backed by the database and seeded at startup — no redeploy needed to change a setting.
- **Authentication**: every `/api/v1/**` endpoint requires a valid OAuth2 access token (Client Credentials grant), issued by Keycloak.

Out of scope, by explicit confirmed decision (see `proposal.md`, "Non-goals"): no CLI, no legacy XLS export, no multipart upload or remote URL fetch for import (filesystem path only), no multi-file import per request, no file size/row count limits, no per-row locking (whole-job serialization only), and no hand-built token implementation (Quarkus OIDC + Keycloak only).

## How it works

### Import flow

1. `POST /api/v1/imports` validates that the given file path is readable, persists a job record, computes the set of business ids present in the file, and returns a `jobId` immediately — no row-outcome information is returned at this point (spec: job-status, "Status information is available only via query, not at submission").
2. Before processing starts, the job is serialized against any other running or queued-ahead job whose id-set intersects its own, preserving arrival order for intersecting jobs; jobs with fully disjoint id-sets run concurrently (**ADR-0003**).
3. The file is processed in chunks, with the chunk size read from the current `chunkSize` job configuration value at the moment the job starts (**ADR-0007**).
4. Each row is interpreted according to the file's own header (no fixed column position or set is assumed). A row is staged — not rejected, and not silently dropped — when it has an invalid value (email/age), a missing value in a recognized column, or a value under an unrecognized header column.
5. A row that passes validation is persisted as a new version of the corresponding customer record, following an explicit merge rule for repeated ids (**ADR-0004**), on top of an append-only, one-document-per-version storage model (**ADR-0001**).
6. The job transitions `PENDING` → `RUNNING` → `COMPLETED`/`FAILED`, with a final numeric summary.
7. `GET /api/v1/imports/{jobId}` returns that summary plus the full staging entry list (each with `jobId`, `rowId`, `rowData`, `errorDescription`, `processedAt`) at any time after submission.

The asynchronous processing mechanism itself (a managed executor, not an external queue) is described in **ADR-0002**; the staging store is a single generic collection shared by every kind of row rejection, distinguished only by its error description (**ADR-0005**).

### Export flow

1. `POST /api/v1/exports` validates the requested columns against the recognized schema (`id`, `name`, `email`, `age`, `country`, `phone`); an unrecognized column is rejected with HTTP 400 naming it explicitly — never silently dropped or emitted empty.
2. The current version of every stored record (the highest version per id) is projected into exactly the requested columns, in exactly the requested order.
3. The result is serialized as CSV, TXT, or XLSX (Office Open XML) and returned as the response body; the legacy binary XLS format is explicitly rejected, not silently downgraded.

### Job configuration

Settings such as `chunkSize` live in the database, not in static application configuration, and are seeded by a startup migration. The same generic CRUD API can hold any future key/value setting, each with a declared value type (`STRING`, `INTEGER`, `BOOLEAN`) validated at write time (**ADR-0007**).

### Architecture at a glance

The diagrams below (Mermaid, rendered inline by GitHub) illustrate the flows described above in more detail. Each one also documents its own sources, the ADR decisions it reflects, and known limitations:

- [**C4 — System Context**](docs/diagrams/c4/context.md) — this service's place among its actors (caller, Keycloak) and the filesystem it reads from.
- [**C4 — Containers**](docs/diagrams/c4/container.md) — the REST API, MongoDB, Keycloak, and the dev-only Mongo Express UI as deployable units.
- [**C4 — Components**](docs/diagrams/c4/component.md) — internal decomposition of the REST API (resources, `ExportService` and its collaborators) and the import job orchestration (`ImportProcessingService`, `JobIntersectionGate`, validators).
- [**Sequence — Import flow**](docs/diagrams/sequence/import-flow.md) — submission, the ADR-0003 serialization gate (`arrive()`/`awaitTurn()`), chunked row processing, staging, and status polling.
- [**Sequence — Export flow**](docs/diagrams/sequence/export-flow.md) — column validation, current-version projection, and per-format serialization.
- [**Sequence — OAuth2 token flow**](docs/diagrams/sequence/oauth2-token-flow.md) — the Client Credentials grant against Keycloak and bearer-token validation on protected endpoints.
- [**Use cases**](docs/diagrams/use-case/use-cases.md) — the full set of caller-facing capabilities, one per REST endpoint.

The architectural decisions behind these flows are recorded as ADRs under [`docs/adr/`](docs/adr/) (storage model, async processing, job serialization, record versioning, staging, authentication, job configuration).

## How to use the services

All endpoints below require a Bearer access token — obtain one first via the Keycloak token endpoint (Client Credentials grant), then send it as `Authorization: Bearer <access_token>` on every call. See [Authentication](#authentication) for the full mechanism and dev/test credentials.

Here is the shortest path from a clean checkout to a successful authenticated call:

1. **Start the application** in dev mode and wait for the `Listening on: http://localhost:8080` log line (see [Prerequisites and running the project](#prerequisites-and-running-the-project)):

   ```shell
   ./gradlew quarkusDev
   ```

   This also provisions Keycloak automatically via Quarkus Dev Services, fixed to port `8543`, and starts a Mongo Express UI at `http://localhost:8081` for browsing the data MongoDB Dev Services provisions (see [Inspecting stored data](#inspecting-stored-data)).

2. **Get an access token** from Keycloak using the `curl` command below (see [Get an access token](#get-an-access-token)).

3. **Copy the `access_token` field** from the JSON response.

4. **Run a smoke test** with the simplest call in this API — it needs no prior data, so it is the fastest way to confirm authentication is working end-to-end:

   ```http
   GET /api/v1/job-configurations
   Authorization: Bearer <access_token>
   ```

   A `200 OK` with a JSON array (empty or not) confirms the token and the API are both working.

5. **Go deeper** with either of two tools once that first call succeeds:
   - **Swagger UI** at [`/q/swagger-ui`](http://localhost:8080/q/swagger-ui) — every endpoint documents a bearer-token security requirement, so an **Authorize** button is available; paste the `access_token` there once and it is sent automatically on every request you try from the UI.
   - The **Postman Collection** below — it automates step 2/3 for you, so you never paste a token by hand.

The rest of this section documents each endpoint's request/response shape in detail; the token/Swagger/Postman mechanics above are the same for all of them.

You do not need to construct these requests by hand: this repository ships a ready-to-use **[Postman Collection](postman/planet-import-export.postman_collection.json)** (with a matching [environment file](postman/planet-import-export.postman_environment.json)) that includes a pre-configured token request and auto-populates the Bearer token for every other request. Alternatively, once the application is running, browse and try every endpoint interactively via **Swagger UI at [`/q/swagger-ui`](http://localhost:8080/q/swagger-ui)** — the OpenAPI spec itself is served at `/q/openapi`, generated automatically from the REST resource annotations.

### Get an access token

In dev mode, Keycloak always listens on the **fixed port `8543`** (see [Authentication](#authentication)), so this command works as-is, no lookup needed:

```bash
curl -X POST http://localhost:8543/realms/quarkus/protocol/openid-connect/token \
  -d "grant_type=client_credentials&client_id=quarkus-app&client_secret=secret"
```

The response is a JSON object; copy the `access_token` field and send it as `Authorization: Bearer <access_token>` on every call below:

```json
{
  "access_token": "eyJhbGciOi...",
  "expires_in": 300,
  "refresh_token": "eyJhbGciOi...",
  "token_type": "Bearer"
}
```

### Submit an import job

```http
POST /api/v1/imports
Authorization: Bearer <access_token>
Content-Type: application/json

{
  "filePath": "/data/imports/customers_01.csv"
}
```

Response — `202 Accepted`:

```json
{ "jobId": "6f1a2b3c-..." }
```

### Query import job status

```http
GET /api/v1/imports/{jobId}
Authorization: Bearer <access_token>
```

Response — `200 OK`:

```json
{
  "jobId": "6f1a2b3c-...",
  "status": "COMPLETED",
  "summary": { "totalRows": 5, "succeeded": 4, "failed": 1 },
  "stagingErrors": [
    {
      "jobId": "6f1a2b3c-...",
      "rowId": "3",
      "rowData": { "id": "3", "email": "marco@example" },
      "errorDescription": "invalid email",
      "processedAt": "2026-09-15T10:00:00Z"
    }
  ]
}
```

### Export stored records

```http
POST /api/v1/exports
Authorization: Bearer <access_token>
Content-Type: application/json

{
  "format": "CSV",
  "columns": ["id", "name", "email", "country"]
}
```

Response — `200 OK`, body = the file content in the requested format (`Content-Type` matches the format: `text/csv`, `text/plain`, or the XLSX Office Open XML type).

### Manage job configuration

```http
GET    /api/v1/job-configurations
GET    /api/v1/job-configurations/{key}
POST   /api/v1/job-configurations
PUT    /api/v1/job-configurations/{key}
DELETE /api/v1/job-configurations/{key}
```

Example — create a new entry:

```http
POST /api/v1/job-configurations
Authorization: Bearer <access_token>
Content-Type: application/json

{
  "key": "exampleFlag",
  "value": "true",
  "valueType": "BOOLEAN",
  "description": "Example generic configuration entry."
}
```

Response — `201 Created`:

```json
{ "key": "exampleFlag", "value": "true", "valueType": "BOOLEAN", "description": "Example generic configuration entry." }
```

### Inspecting stored data

`quarkusDev` also starts a [Mongo Express](https://github.com/mongo-express/mongo-express) UI at **[`http://localhost:8081`](http://localhost:8081)**, pointed at the same MongoDB Dev Services instance the application uses — no manual setup, no separate `docker run`, no auth. Open the `importexport` database from the sidebar to browse collections such as `customer_records` (imported records), `import_jobs` (job status), `staging_entries` (rejected rows), and `job_configuration`, and inspect individual documents without writing a query.

This is dev-mode only: automated tests use an embedded, in-memory MongoDB (Flapdoodle, **ADR-0001**), so there is no Dev Services container for it to attach to under `./gradlew test`. The container (name `planet-importexport-mongo-express-dev`) starts on application startup and is removed on shutdown, mirroring the lifecycle of the Keycloak/MongoDB Dev Services containers themselves.

## Prerequisites and running the project

- **Java 25**
- **Docker or Podman** — required only for Keycloak, provisioned automatically by Quarkus Dev Services in `%dev`/`%test`. This project already runs with a Podman-as-Docker-compatible setup as well as plain Docker; either works. Storage does not need a container: MongoDB is embedded in-memory (Flapdoodle, **ADR-0001**) and needs no manual setup at all.

Run in dev mode (live coding):

```shell
./gradlew quarkusDev
```

Run the test suite:

```shell
./gradlew test
```

Dev Services (Keycloak) starts automatically for both `quarkusDev` and `./gradlew test`/`./gradlew clean test`, so Docker/Podman must be available in the environment beforehand — there is no manual container setup step, but the container runtime itself is not optional. See `docs/adr/ADR-0006` for the authentication design and [Authentication](#authentication) below for the dev/test client details.

> **CI note:** this repository currently defines no CI pipeline (no `.github/workflows`, no other CI config present as of this writing). Whoever sets one up in the future must provision a Docker- or Podman-compatible container runtime on the runner, for the same reason it's required locally: `./gradlew test`/`./gradlew build` starts Keycloak via Dev Services.

Package the application:

```shell
./gradlew build
```

It produces the `quarkus-run.jar` file in the `build/quarkus-app/` directory. Be aware that it's not an _über-jar_ as the dependencies are copied into the `build/quarkus-app/lib/` directory.

The application is now runnable using `java -jar build/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./gradlew build -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar build/*-runner.jar`.

### Creating a native executable

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

**Dev/test client:** the Dev-Services default client id/secret are `quarkus-app` / `secret`, on realm `quarkus` — preserved as-is by the custom realm-export above. See the [Postman Collection](postman/planet-import-export.postman_collection.json) for a ready-to-run token request, or `src/test/java/com/planet/importexport/authapi/AuthenticationIT.java` for a fully worked example.

**Fixed port for `quarkusDev` (manual/demo use):** `%dev` pins Keycloak's Dev Services container to host port **`8543`** (`quarkus.keycloak.devservices.port` in `application.yml`) instead of Testcontainers' usual random free port. This makes the realm base URL always `http://localhost:8543/realms/quarkus`, so it can be used directly in a `curl` command, a Postman environment, or a devcontainer `forwardPorts` entry without discovering a new port on every run — see [Get an access token](#get-an-access-token) above for the exact command. `%test` intentionally keeps the random port: automated tests read the effective `quarkus.oidc.auth-server-url` via `@ConfigProperty` at runtime, so no human needs to know the port number, and a fixed port would only risk collisions between concurrent test runs.

**Working inside a devcontainer:** if you access the application from a browser on the host machine (not from a terminal already inside the container), port `8543` needs to be reachable from outside the container too. This repository does not ship its own `.devcontainer/devcontainer.json`; if the devcontainer you use declares a static `forwardPorts` list (as this project's own development environment does), add `8543` (and `8081` for [Mongo Express](#inspecting-stored-data)) to it so VS Code forwards them automatically the first time the services start. Otherwise, forward the ports by hand once from the **Ports** panel (`Forward a Port`) — this is a one-time step per devcontainer session, not a per-run one, since the port numbers do not change.

## Next Steps

The following capabilities are not part of the current implementation but are natural extensions of it:

- **List/query import jobs**: a `GET /api/v1/imports` endpoint returning jobs filtered by status (`PENDING`/`RUNNING`/`COMPLETED`/`FAILED`), by `jobId`, or by processing date range. Today a job's status/summary is only retrievable one at a time via `GET /api/v1/imports/{jobId}` (see [Query import job status](#query-import-job-status)); there is no way to list or search across jobs.
- **Query staging entries independently of a job**: `GET /api/v1/staging`-style endpoints returning rejected rows filtered by `jobId`, or by processing date range, without going through a specific job's status response. Today staging entries are only reachable embedded in a `GET /api/v1/imports/{jobId}` response (the `stagingErrors` field) — there is no standalone way to query the `staging_entries` collection by other criteria.
- **Versioning for the imported document schema itself**: an explicit mechanism for evolving the set of recognized fields (currently `id`, `name`, `email`, `age`, `country`, `phone`, fixed in `RecognizedField`) over time — e.g. adding/deprecating fields without breaking already-stored `customer_records` versions or in-flight imports. This is distinct from the existing **record**-versioning policy (ADR-0004, one version per repeated `id`), which versions data, not the schema shape.

## Related Guides

- REST ([guide](https://quarkus.io/guides/rest)): Build RESTful web services and APIs using Jakarta REST (formerly JAX-RS)
- REST Jackson ([guide](https://quarkus.io/guides/rest#json-serialisation)): Jackson serialization support for Quarkus REST
- Hibernate Validator ([guide](https://quarkus.io/guides/validation)): Bean validation using Hibernate Validator and Jakarta Validation annotations
- MongoDB with Panache ([guide](https://quarkus.io/guides/mongodb-panache)): Simplify your persistence code for MongoDB via the active record or the repository pattern
- OpenID Connect ([guide](https://quarkus.io/guides/security-openid-connect)): Secure applications with OpenID Connect and OAuth 2.0 using bearer tokens
- SmallRye OpenAPI ([guide](https://quarkus.io/guides/openapi-swaggerui)): Generate and expose an OpenAPI spec and Swagger UI from your REST resources
