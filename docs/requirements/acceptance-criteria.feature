# Acceptance Criteria
#
# Source: docs/requirements/functional-specification.md (Functional Specification for
# "Senior Software Engineer Exercise — File Import and Export").
# Behavior source of truth: docs/requirements/Senior Software Engineer Exercise.pdf,
# plus confirmed decisions made explicitly by the user (product owner for this
# exercise) for every point the PDF left open.
#
# Notation used below:
#   [PDF]       behavior directly stated or directly implied by the PDF's text or worked example.
#   [DECISION]  a design decision explicitly confirmed by the user, referencing the
#               numbered "Confirmed decisions" list in the Functional Specification's
#               Assumption Analysis section. These are binding requirements, not
#               open policy — no [POLICY] or @unresolved markers remain for them.
#
# Recognized schema (derived from the union of columns across all three sample
# files in the PDF): id, name, email, age, country, phone.
# Identity key: "id" is the record's identity key across files.
#
# [DECISION 16] The customer record storage model question (fixed relational
# schema vs. a flexible/versioned key-value model) is resolved: each record
# version is stored as a MongoDB document per version, with native variable
# fields (no fixed relational schema, no EAV). See the Functional
# Specification's Assumption Analysis, confirmed decision 16. No open
# architectural question remains on this topic; all scenarios below assume
# the recognized schema (id, name, email, age, country, phone) without
# implying a fixed relational storage structure.

Feature: Import a CSV file asynchronously into storage
  As the external system (or an operator acting on its behalf)
  I want to submit a CSV file reference for import and receive a job id
  So that the file is processed in the background and I can check its outcome later

  # [PDF] Requirement 1: Provide a way to import a CSV file into storage; imported
  # information must be persisted.
  # [DECISION 3] Import is asynchronous: the call accepts a file reference and
  # returns a jobId immediately; processing happens in the background.
  # [DECISION 8] The file is delivered as a path reference on the local/mounted
  # filesystem — no multipart upload, no remote URL.
  # [DECISION 9] Exactly one CSV file per import call.
  Scenario: Submitting an import request returns a job id immediately
    Given the file "customers_01.csv" is available at a path on the local/mounted filesystem
    When I call the import API with that file path
    Then the call returns immediately with a "jobId"
    And no synchronous error list or record outcome is returned in this response

  # [PDF] Requirement 1: the application must provide enough information to
  # determine whether an import was successful and which records could not be
  # processed.
  # [DECISION 3] That information is retrieved via a later status query, not in
  # the import response itself.
  Scenario: Querying job status after a successful import returns a numeric summary
    Given an import job was submitted for the file "customers_01.csv" with content:
      """
      id,name,email,age,country
      1,John Smith,john@example.com,35,Portugal
      2,Jane Doe,jane@example.com,28,Spain
      3,Bob Smith,bob@example.com,42,France
      """
    And the job has finished processing
    When I query the status of that job
    Then the status response shows total rows processed as 3
    And the status response shows total rows succeeded as 3
    And the status response shows total rows failed as 0
    And the detailed staging error list for that job is empty
    And 3 record versions are persisted, one for each imported id

  # [PDF] Requirement 2: "Files are produced by an external system and must be
  # processed according to the information they contain" — header-driven column
  # order, not a fixed positional schema.
  Scenario: Import a file whose columns are in a different order and include an extra field
    Given an import job was submitted for the file "customers_02.csv" with content:
      """
      id,name,email,age,country,phone
      1,John Smith,john@example.com,35,Portugal,+351910000000
      4,Ana Costa,ana@example.com,30,Portugal
      """
    And the job has finished processing
    When I query the status of that job
    Then the status response shows total rows succeeded as 2
    And the status response shows total rows failed as 0
    And the stored current version for id "1" has phone "+351910000000"

  # [DECISION 10] Chunk size is a runtime-configurable, database-backed setting,
  # not a hardcoded batch size.
  Scenario: A large file is processed in configured batches
    Given the job configuration "chunkSize" is set to 2
    And an import job was submitted for a file containing 5 valid rows
    And the job has finished processing
    When I query the status of that job
    Then the status response shows total rows processed as 5
    And the status response shows total rows succeeded as 5


Feature: Route invalid values and unknown columns to staging
  As the service
  I want to record rows with invalid field values or unknown header columns in a
  single generic staging table rather than rejecting or silently dropping them
  So that every problem is auditable per job without losing the offending data

  # [PDF] Requirement 2: "Define the behaviour for fields that... contain
  # invalid values" — sample shows age "thirty" (non-numeric) and email
  # "marco@example" (no top-level domain).
  # [DECISION 2] Invalid field values are staged, not rejected outright.
  # [DECISION 13] Email must match a simplified RFC 5322 pattern (user@domain.tld).
  # [DECISION 14] Age must be an integer in the range 0-120 inclusive.
  Scenario Outline: A row with an invalid field value is written to staging
    Given an import job was submitted for a file containing the row:
      """
      id,name,phone,email,age,country
      5,Marco Rossi,+39000000000,<email>,<age>,Italy
      """
    And the job has finished processing
    When I query the status of that job
    Then the status response shows total rows failed as 1
    And the detailed staging error list for that job contains one entry with rowId for id "5"
    And that staging entry's "errorDescription" mentions "<field>"
    And that staging entry's "rowData" contains the original raw row

    Examples:
      | email               | age     | field |
      | marco@example       | 35      | email |
      | marco@example.com   | thirty  | age   |
      | marco@example.com   | 121     | age   |
      | marco@example.com   | -1      | age   |

  # [PDF] Requirement 2: "Define the behaviour for fields that... [are] unknown"
  # [DECISION 2] "Unknown" = a header column outside the recognized schema
  # (id, name, email, age, country, phone). It is staged using the same table
  # as invalid values, distinguished only by errorDescription text.
  Scenario: A row under an unrecognized header column is written to staging
    Given an import job was submitted for a file containing the header and row:
      """
      id,name,email,age,country,loyalty_tier
      6,New Customer,new@example.com,30,Germany,Gold
      """
    And the job has finished processing
    When I query the status of that job
    Then the status response shows total rows failed as 1
    And the detailed staging error list for that job contains one entry with rowId for id "6"
    And that staging entry's "errorDescription" mentions the unknown column "loyalty_tier"

  # [DECISION 2] Staging table layout is fixed: jobId, rowId, rowData,
  # errorDescription, processedAt.
  Scenario: A staging entry carries the required fields
    Given an import job was submitted for a file containing an invalid row for id "5"
    And the job has finished processing
    When I query the status of that job
    Then each entry in the detailed staging error list has a "jobId" equal to that job's id
    And each entry has a "rowId"
    And each entry has a "rowData"
    And each entry has an "errorDescription"
    And each entry has a "processedAt" timestamp

  # [DECISION 15] No file size or row-count limit exists; staging/chunking are
  # the only related controls.
  Scenario: A file with many rows is not rejected for its size
    Given an import job was submitted for a file containing a very large number of valid rows
    When I call the import API with that file path
    Then the call is accepted and returns a "jobId"
    And the import is not rejected on the basis of file size or row count


Feature: Version a record when the same id is received more than once
  As the service
  I want to create a new, merged version of a record whenever its id is
  received again, keeping the full history
  So that stored data remains predictable, auditable, and never silently
  overwritten, merged without trace, or duplicated

  # [PDF] Requirement 3: "Some information may appear in more than one file. The
  # application must define how imported information is handled when the same
  # data is received more than once."
  # [DECISION 1] New id -> new version. The new version is a full snapshot:
  # fields present in the new file are updated; fields absent are inherited
  # from the immediately preceding version.
  Scenario: The same record id is re-imported from a later file with additional fields
    Given customer id "1" was already imported (version 1) with name "John Smith", email "john@example.com", age "35", country "Portugal"
    When I import a file containing id "1" with the same core fields plus phone "+351910000000"
    And the job has finished processing
    Then a new version (version 2) is created for id "1"
    And version 1 for id "1" remains unchanged in history
    And version 2 for id "1" has phone "+351910000000"
    And version 2 for id "1" inherits name "John Smith", email "john@example.com", age "35", country "Portugal" from version 1

  # [DECISION 1] A conflicting value for an existing field is resolved simply by
  # the new version recording the new value; the old value survives only in the
  # prior version. This is no longer an open question.
  Scenario: The same record id is re-imported with a different value for an existing field
    Given customer id "1" was already imported (version 1) with country "Portugal"
    When I import a file containing id "1" with country "Spain"
    And the job has finished processing
    Then a new version (version 2) is created for id "1"
    And version 2 for id "1" has country "Spain"
    And version 1 for id "1" still has country "Portugal" in history

  # [DECISION 7] Per-id serialized queue: jobs touching the same id are
  # processed strictly in arrival order; jobs touching different ids may run
  # in parallel.
  Scenario: Two import jobs for the same id are processed one at a time, in arrival order
    Given an import job A was submitted first, containing id "1" with country "Portugal"
    And an import job B was submitted second, containing id "1" with country "Spain"
    When both jobs have finished processing
    Then version 2 for id "1" (created by job A) has country "Portugal"
    And version 3 for id "1" (created by job B) has country "Spain"
    And no version was created out of arrival order

  Scenario: Import jobs for different ids may run concurrently
    Given an import job A was submitted containing only id "1"
    And an import job B was submitted containing only id "2"
    When both jobs are processed
    Then job A and job B are not required to wait on each other
    And a new version is created for id "1" and a new version is created for id "2"


Feature: Export stored data in multiple formats with caller-selected columns
  As a user or another system
  I want to export stored data as CSV, TXT, or XLSX with chosen columns and order
  So that I receive only the fields I need, in the order I need them

  Background:
    Given the following current record versions are stored:
      | id | name        | email             | age | country  | phone          |
      | 1  | John Smith  | john@example.com  | 35  | Portugal | +351910000000  |
      | 2  | Jane Doe    | jane@example.com  | 28  | Spain    |                |
      | 3  | Bob Smith   | bob@example.com   | 42  | France   |                |

  # [PDF] Requirement 5, worked example: { "format": "CSV", "columns": ["id", "name", "email", "country"] }
  Scenario: Export selected columns as CSV in the requested order
    When I request an export with:
      """
      { "format": "CSV", "columns": ["id", "name", "email", "country"] }
      """
    Then the export succeeds
    And the output format is CSV
    And each output row contains exactly the columns "id, name, email, country" in that order
    And the output contains 3 data rows corresponding to the stored current record versions

  # [PDF] Requirement 5, worked example: { "format": "TXT", "columns": ["name", "email"] }
  Scenario: Export selected columns as TXT in the requested order
    When I request an export with:
      """
      { "format": "TXT", "columns": ["name", "email"] }
      """
    Then the export succeeds
    And the output format is TXT
    And each output row contains exactly the columns "name, email" in that order

  # [PDF] Requirement 5: "Stored information must be exportable as CSV, TXT and XLS/XLSX."
  # [DECISION 6] Only modern XLSX is supported; legacy XLS is out of scope.
  Scenario: Export selected columns as XLSX
    When I request an export with:
      """
      { "format": "XLSX", "columns": ["id", "name", "country"] }
      """
    Then the export succeeds
    And the output is a valid XLSX (Office Open XML) spreadsheet
    And each output row contains exactly the columns "id, name, country" in that order

  # [DECISION 6] Legacy binary XLS is explicitly out of scope.
  Scenario: Requesting the legacy XLS format is not supported
    When I request an export with:
      """
      { "format": "XLS", "columns": ["id", "name"] }
      """
    Then the export request is rejected as an unsupported format

  # [PDF] Requirement 5: "The output should contain the requested fields, in the
  # requested order." — order is a first-class, explicitly stated requirement.
  Scenario Outline: Requested column order is honored regardless of storage order
    When I request an export with:
      """
      { "format": "CSV", "columns": [<columns>] }
      """
    Then the output columns appear in exactly this order: <columns>

    Examples:
      | columns                              |
      | "email", "id", "name"                |
      | "country", "name"                    |

  # [DECISION 4] An unrecognized requested column is rejected with an explicit
  # HTTP 400 identifying the invalid column — no silent ignore, no empty column.
  Scenario: Export is requested with an unrecognized column name
    When I request an export with:
      """
      { "format": "CSV", "columns": ["id", "loyalty_tier"] }
      """
    Then the export request is rejected with an HTTP 400 response
    And the error response identifies "loyalty_tier" as the invalid column


Feature: Provide a REST API for import and export
  As a user or another system
  I want a REST API exposing import and export capabilities
  So that I can trigger these operations without direct access to storage

  # [PDF] Requirement 6: "Provide a REST API or a CLI supporting: Import — Import
  # a CSV file; Export — Export data, select format, select columns."
  # [DECISION 5] REST API only; no CLI is implemented.
  Scenario: The REST API exposes an import capability
    Given the REST API is available
    When I call its import endpoint with a file path reference
    Then a "jobId" is returned through that same REST API

  Scenario: The REST API exposes a job status query capability
    Given the REST API is available
    And an import job has been submitted and has finished processing
    When I call the job status endpoint for that "jobId"
    Then the numeric summary and the detailed staging error list are returned through that same REST API

  Scenario: The REST API exposes an export capability with format and column selection
    Given the REST API is available
    When I call its export endpoint specifying a format and a list of columns
    Then the exported output in the requested format is returned through that same REST API


Feature: Protect the REST API with OAuth2 Client Credentials
  As the service
  I want every import, export, status, and configuration endpoint protected by
  OAuth2 Client Credentials
  So that only authorized machine clients can use the API

  # [DECISION 12] OAuth2 Client Credentials grant (machine-to-machine, no end
  # user), implemented via a ready-made Quarkus ecosystem extension (e.g.,
  # Quarkus OIDC) rather than a hand-built OAuth2 server. Token and refresh
  # token issuance endpoints must exist.
  Scenario: A client obtains an access token via the Client Credentials grant
    Given a registered OAuth2 client with valid client id and client secret
    When the client requests a token from the token endpoint using the client_credentials grant
    Then an access token is returned
    And a refresh token is returned

  Scenario: A request without a valid access token is rejected
    Given no valid access token is provided
    When I call the import endpoint
    Then the request is rejected as unauthorized

  Scenario: A request with a valid access token is accepted
    Given a valid access token obtained via the client_credentials grant
    When I call the import endpoint with that access token
    Then the request is authorized and processed


Feature: Manage job configuration via a CRUD API
  As an operator
  I want a generic CRUD API for job configuration
  So that settings such as chunk size (and future job settings) can be changed
  at runtime without a redeploy

  # [DECISION 10] Chunk size is stored in a configuration collection, seeded by
  # a migration, and changeable at runtime via a service call.
  # [DECISION 11] The CRUD API is generic, not chunk-size-specific, so it can
  # accommodate other job settings introduced later.
  Scenario: A database migration seeds an initial chunk size configuration value
    Given the database migrations have run and the configuration collection is seeded
    When I query the job configuration for "chunkSize"
    Then a seeded initial value is returned

  Scenario: An operator updates the chunk size configuration at runtime
    Given the current "chunkSize" configuration value is known
    When I call the job configuration API to update "chunkSize" to a new value
    Then the configuration is updated without requiring a redeploy
    And subsequent import jobs use the new chunk size

  Scenario: The job configuration API supports managing settings beyond chunk size
    Given the job configuration API is available
    When I create a new job configuration entry with a different key and value
    Then that entry can be retrieved, updated, and deleted through the same generic API
