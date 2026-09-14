# Capability: Job Configuration

## ADDED Requirements

### Requirement: Database-backed, migration-seeded job configuration

The system SHALL store job configuration values (including `chunkSize`) in a database collection rather than in static application configuration files, and SHALL seed an initial value for each known setting via a database migration run at startup.

#### Scenario: A database migration seeds an initial chunk size configuration value

- **GIVEN** the database migrations have run and the configuration collection is seeded
- **WHEN** the job configuration for `chunkSize` is queried
- **THEN** a seeded initial value is returned

### Requirement: Runtime-updatable configuration without redeploy

The system SHALL expose an API to update a job configuration value at runtime, and SHALL apply the updated value to subsequently started jobs without requiring an application redeploy or restart.

#### Scenario: An operator updates the chunk size configuration at runtime

- **GIVEN** the current `chunkSize` configuration value is known
- **WHEN** the job configuration API is called to update `chunkSize` to a new value
- **THEN** the configuration is updated without requiring a redeploy
- **AND** subsequent import jobs use the new chunk size

### Requirement: Generic CRUD API not limited to chunk size

The system SHALL expose the job configuration API generically, allowing any key/value configuration entry to be created, read, updated, and deleted, so that settings beyond `chunkSize` can be introduced without an API change.

#### Scenario: The job configuration API supports managing settings beyond chunk size

- **GIVEN** the job configuration API is available
- **WHEN** a new job configuration entry with a different key and value is created
- **THEN** that entry can be retrieved, updated, and deleted through the same generic API

### Requirement: Declared value type per configuration entry

The system SHALL store, alongside each configuration entry's free-form string value, a declared value type (`STRING`, `INTEGER`, or `BOOLEAN`), and SHALL validate at write time that the value parses according to its declared type.

#### Scenario: Writing a value inconsistent with its declared type is rejected

- **GIVEN** a job configuration entry is being created or updated with `valueType` "INTEGER"
- **WHEN** the supplied value does not parse as an integer
- **THEN** the write is rejected with a validation error
