# Capability: Job Status

## ADDED Requirements

### Requirement: Numeric job summary on status query

The system SHALL expose a way to query the status of a previously submitted import job by its `jobId`, returning at minimum the total number of rows processed, the number that succeeded, and the number that failed.

#### Scenario: Querying job status after a successful import returns a numeric summary

- **GIVEN** an import job was submitted for a file containing 3 valid rows
- **AND** the job has finished processing
- **WHEN** the status of that job is queried
- **THEN** the response shows total rows processed as 3
- **AND** the response shows total rows succeeded as 3
- **AND** the response shows total rows failed as 0

### Requirement: Detailed staging error list on status query

The system SHALL include, in the same status response, the detailed list of staging entries recorded for that job, each carrying `jobId`, `rowId`, `rowData`, `errorDescription`, and `processedAt`.

#### Scenario: Status query returns an empty staging list for a fully successful job

- **GIVEN** an import job whose rows all succeeded
- **WHEN** the status of that job is queried
- **THEN** the detailed staging error list for that job is empty

#### Scenario: Status query returns staging entries with all required fields

- **GIVEN** an import job that produced at least one staging entry
- **WHEN** the status of that job is queried
- **THEN** each entry in the detailed staging error list has a `jobId` equal to that job's id, a `rowId`, a `rowData`, an `errorDescription`, and a `processedAt` timestamp

### Requirement: Status information is available only via query, not at submission

The system SHALL NOT return the numeric summary or the staging error list as part of the synchronous response to the import submission call; that information SHALL be obtainable only through a subsequent status query.

#### Scenario: Import submission response carries no outcome information

- **GIVEN** an import request is submitted
- **WHEN** the submission call returns
- **THEN** the response contains only the `jobId`
- **AND** it contains no row-outcome summary or staging error list
