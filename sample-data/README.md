# Sample data for the live demo

CSV files to submit through `POST /api/v1/imports` during the presentation.
Each one exercises a specific import scenario. Expected outcomes were
verified by simulating `ImportRowValidator`'s exact rules against every
file's rows before committing them here — the counts below are not
estimates.

Recognized columns: `id, name, email, age, country, phone`. `id` is
required and non-blank; the other five, when present in the header, must be
non-blank and (for `email`/`age`) pass their format check. `chunkSize`
defaults to `500`.

| File | Rows | Purpose |
|---|---|---|
| `01-all-valid.csv` | 10 | Golden path — every row succeeds. Submit first to seed `customer_records`. |
| `02-mixed-valid-and-invalid.csv` | 10 (3 OK / 7 staged) | One file exercising every negative row-level rule: malformed email (no TLD), out-of-range age (`150`, `-5`), non-numeric age (`thirty`), blank email, blank name, blank age. Each staged row gets its own distinct `errorDescription`. |
| `03-unknown-column.csv` | 3 (0 OK / 3 staged) | Header carries an extra `loyalty_tier` column outside the recognized schema. Because unknown-column detection is header-level (`B6`), **every** row in the file is staged, even though the recognized columns' values are otherwise fine. |
| `04-versioning-update.csv` | 3 | Re-submits ids `1001`, `1002`, `1005` from file 01 with changed values (email, age) — demonstrates the append-only version chain: query `GET /api/v1/imports/{jobId}` then export to see the *new* values win. |
| `05-partial-columns-merge.csv` | 2 | Header only has `id,name,phone` for ids `1003`/`1004` (already in `customer_records` from file 01). Demonstrates the version-merge rule: `email`, `age`, `country` are inherited from the previous version rather than cleared. |
| `06-large-chunked-1200-rows.csv` | 1200 (1176 OK / 24 staged) | Exceeds the default `chunkSize` of 500, so the job processes in 3 chunks. 24 rows are randomly invalid (bad email/age/missing name), spread across all three chunks, to show partial success within a chunked run. |
| `07-header-only-empty.csv` | 0 | Empty file (header row only) — job completes immediately with `totalRows=0`. |

## Suggested demo order

1. **`01-all-valid.csv`** — submit, poll status, show `COMPLETED` with
   `succeeded=10, failed=0`, then export to CSV/XLSX to show the persisted
   data.
2. **`02-mixed-valid-and-invalid.csv`** — submit, show the job status
   response's staging entry list with each row's specific
   `errorDescription`.
3. **`03-unknown-column.csv`** — submit, show that *all* rows were staged
   for the same reason (unknown header column), illustrating the
   whole-file/header-level check.
4. **`04-versioning-update.csv`** then export again — show the previously
   imported ids now carry the new values (append-only versioning, ADR
   for the versioning scheme).
5. **`05-partial-columns-merge.csv`** then export again — show that fields
   not present in this file's header were *not* cleared, only `name`/`phone`
   changed.
6. **`06-large-chunked-1200-rows.csv`** — submit, and while it processes
   (async, on the bounded executor) explain ADR-0002/ADR-0003: the request
   returns immediately with `202 Accepted`, and the id-intersection gate
   would serialize this against any other in-flight job sharing an id.
   Poll status afterward to show the final summary.
7. **`07-header-only-empty.csv`** — optional edge case if time remains.

## Two-job intersection gate demo (optional, ADR-0003)

To demonstrate the whole-job serialization gate itself (not just chunking),
submit `01-all-valid.csv` and `04-versioning-update.csv` back-to-back
quickly — both share ids (`1001`, `1002`, `1005`), so the second job's
processing waits for the first to fully release before starting, even
though both return `202 Accepted` immediately.
