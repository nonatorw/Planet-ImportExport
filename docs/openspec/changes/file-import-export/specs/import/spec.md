# Capability: Import

## ADDED Requirements

### Requirement: Asynchronous CSV import by file path reference

The system SHALL accept a request identifying a single CSV file already available on the local/mounted filesystem, and SHALL respond immediately with a job identifier without waiting for the file to be processed.

#### Scenario: Submitting an import request returns a job id immediately

- **GIVEN** the file "customers_01.csv" is available at a path on the local/mounted filesystem
- **WHEN** the import API is called with that file path
- **THEN** the call returns immediately with a `jobId`
- **AND** no synchronous error list or record outcome is returned in that response

#### Scenario: Exactly one file per import call

- **GIVEN** an import request naming exactly one file path
- **WHEN** the request is submitted
- **THEN** the system processes only that one file for the resulting job
- **AND** the system provides no mechanism to reference more than one file in a single import call

#### Scenario: A file with many rows is not rejected for its size

- **GIVEN** an import job is submitted for a file containing a very large number of valid rows
- **WHEN** the import API is called with that file path
- **THEN** the call is accepted and returns a `jobId`
- **AND** the import is not rejected on the basis of file size or row count

### Requirement: Header-driven column interpretation

The system SHALL interpret each row of an imported file according to that file's own header row rather than assuming a fixed column position or fixed column set.

#### Scenario: Import a file whose columns are reordered and include an extra field

- **GIVEN** a file with header `id,name,email,age,country,phone` and rows including one for id "1" with phone "+351910000000"
- **WHEN** the job finishes processing
- **THEN** the stored current version for id "1" has phone "+351910000000"
- **AND** the row succeeds despite the column set differing from another previously imported file for the same source

### Requirement: Chunked batch processing with runtime-configurable batch size

The system SHALL process an accepted import file in batches (chunks), where the number of rows per batch is read from the current job configuration value for `chunkSize` (see the Job Configuration capability) at the time the job begins, not a hardcoded constant.

#### Scenario: A large file is processed in configured batches

- **GIVEN** the job configuration `chunkSize` is set to 2
- **AND** an import job is submitted for a file containing 5 valid rows
- **WHEN** the job finishes processing
- **THEN** the status response shows total rows processed as 5
- **AND** the status response shows total rows succeeded as 5

### Requirement: Whole-job serialization by id-set intersection

The system SHALL compute the set of business ids present in a submitted file before that job begins row processing, and SHALL NOT begin processing a job whose id-set intersects the id-set of any currently running or already-queued-ahead job until every such intersecting job has completed. Jobs whose id-sets are fully disjoint from all in-flight jobs SHALL be permitted to process concurrently.

#### Scenario: Two import jobs for the same id are processed one at a time, in arrival order

- **GIVEN** import job A is submitted first, containing id "1" with country "Portugal"
- **AND** import job B is submitted second, containing id "1" with country "Spain"
- **WHEN** both jobs have finished processing
- **THEN** the version created by job A precedes the version created by job B
- **AND** no version is created out of arrival order

#### Scenario: Import jobs for different ids may run concurrently

- **GIVEN** import job A is submitted containing only id "1"
- **AND** import job B is submitted containing only id "2"
- **WHEN** both jobs are processed
- **THEN** job A and job B are not required to wait on each other
- **AND** a new version is created for id "1" and a new version is created for id "2"

### Requirement: Invalid field values are staged, not rejected

The system SHALL validate each recognized field of each row (email against a simplified RFC 5322 pattern; age as an integer in the range 0-120 inclusive) and SHALL route a row containing an invalid value to the staging store rather than rejecting the entire file or silently dropping the row.

#### Scenario: A row with an invalid email is staged

- **GIVEN** a row with email "marco@example" (no top-level domain)
- **WHEN** the job finishes processing
- **THEN** the row is recorded in the staging store with an error description mentioning "email"
- **AND** the row is not persisted as a customer record version

#### Scenario: A row with a non-numeric or out-of-range age is staged

- **GIVEN** a row with age "thirty", or age "121", or age "-1"
- **WHEN** the job finishes processing
- **THEN** the row is recorded in the staging store with an error description mentioning "age"
- **AND** the row is not persisted as a customer record version

### Requirement: Unknown header columns are staged, not rejected

The system SHALL route a row whose file declares a header column outside the recognized schema (`id`, `name`, `email`, `age`, `country`, `phone`) to the same staging store used for invalid values, distinguished only by the error description text.

#### Scenario: A row under an unrecognized header column is staged

- **GIVEN** a file with header `id,name,email,age,country,loyalty_tier`
- **WHEN** the job finishes processing
- **THEN** the affected row is recorded in the staging store with an error description identifying the unknown column `loyalty_tier`
