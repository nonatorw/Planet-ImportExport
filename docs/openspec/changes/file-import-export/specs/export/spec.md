# Capability: Export

## ADDED Requirements

### Requirement: Export stored data as CSV, TXT, or XLSX

The system SHALL export the current version of every stored customer record in the format the caller requests, supporting exactly three formats: CSV, TXT, and XLSX (Office Open XML). Legacy binary XLS SHALL NOT be supported.

#### Scenario: Export selected columns as CSV

- **WHEN** an export is requested with format "CSV" and columns `["id", "name", "email", "country"]`
- **THEN** the export succeeds
- **AND** the output format is CSV
- **AND** each output row contains exactly those columns in that order

#### Scenario: Export selected columns as TXT

- **WHEN** an export is requested with format "TXT" and columns `["name", "email"]`
- **THEN** the export succeeds
- **AND** the output format is TXT
- **AND** each output row contains exactly those columns in that order

#### Scenario: Export selected columns as XLSX

- **WHEN** an export is requested with format "XLSX" and columns `["id", "name", "country"]`
- **THEN** the export succeeds
- **AND** the output is a valid XLSX (Office Open XML) spreadsheet
- **AND** each output row contains exactly those columns in that order

#### Scenario: Requesting the legacy XLS format is rejected

- **WHEN** an export is requested with format "XLS"
- **THEN** the export request is rejected as an unsupported format

### Requirement: Caller-controlled column selection and order

The system SHALL include in the exported output exactly the columns the caller requested, in exactly the order requested, regardless of the columns' storage order.

#### Scenario Outline: Requested column order is honored

- **WHEN** an export is requested with format "CSV" and columns in a given order
- **THEN** the output columns appear in exactly that order

  Examples: `["email", "id", "name"]`, `["country", "name"]`

### Requirement: Unrecognized requested columns are rejected explicitly

The system SHALL reject an export request that names a column outside the recognized schema (`id`, `name`, `email`, `age`, `country`, `phone`) with an HTTP 400 response identifying the invalid column by name. The system SHALL NOT silently ignore an unrecognized column or emit it as an empty column.

#### Scenario: Export is requested with an unrecognized column name

- **WHEN** an export is requested with format "CSV" and columns `["id", "loyalty_tier"]`
- **THEN** the export request is rejected with an HTTP 400 response
- **AND** the error response identifies "loyalty_tier" as the invalid column
