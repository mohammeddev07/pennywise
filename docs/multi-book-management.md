# Multi-book management implementation report

## Delivery

Implemented on `codex/multi-book-management`, based on refreshed `develop` at `687568a`.
Five local phase commits; no push, merge, deployment, or production database changes.
The frozen style keys, response fields, order request/status, and error codes are unchanged.

| Phase | Delivered                                                                                                                                                          |
| ----- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 1     | V8 adds icon/color/sort order with defaults and deterministic per-owner backfill; existing financial and audit data is preserved.                                  |
| 2     | Central style vocabulary, owner-scoped reads, grouped balances, owner-row locking, cap, last-book protection, atomic reorder, and after-commit eviction.           |
| 3     | Additive book response fields, styling in create/PATCH, order PUT, populated-book soft-delete, and preserved ETag behavior.                                        |
| 4     | OpenAPI enums and limits, reorder concurrency, deletion/retention documentation, and backend/spec parity tests.                                                    |
| 5     | PostgreSQL regression tests for ownership, deleted data access, caches, concurrency, query counts, imports/exports, signup/login, and migration preservation/cost. |

## Compatibility and implementation details

- Creation keeps nonblank names up to 80 characters, stores surrounding whitespace, and permits duplicates. PATCH retains its pre-existing rename trimming behavior.
- Currency still accepts nonblank three-character strings, including lowercase and unknown codes; no recognized-code or uppercase enforcement was added.
- Opening balance remains a signed Java `long`/PostgreSQL `BIGINT`, defaults to zero, and has no narrower validation. A characterization test confirms that opening balance `2^53`, with no transactions, returns `9007199254740992` from both the list and balance endpoint. Summaries remain zero.
- Opening balance stays outside transaction summaries, analysis, and both exports. Import only adds transactions and does not replace the opening balance.
- New books default to `book`/`green`; supplied unknown style keys fail with `VALIDATION_ERROR`. All nine icons and six colors are tested through HTTP creation and service updates.
- Existing accounts above 10 active books remain readable. The cap only prevents further creation.
- Owner-row locks serialize book create/update/delete/reorder. The last successfully serialized reorder wins, not necessarily the last request sent by the client. Reorder changes versions only for books whose position changes.
- Reorder checks duplicates, then active-book count, then ownership/membership. A count mismatch returns 400 `VALIDATION_ERROR`; with matching counts, foreign/unknown/deleted IDs return 404 `NOT_FOUND`. Failed requests leave positions unchanged.
- List metadata and balances are read fresh under one repeatable-read snapshot. The HTTP list bypasses the existing entity cache so stale membership cannot be paired with fresh balances. Existing cache eviction is retained for legacy service reads; deletion also evicts balance, monthly summary, budget, and category caches.
- The list endpoint executes exactly three SQL statements for an existing authenticated user with either 1 or 10 books: user lookup, ordered books, and one grouped balance query. There is no per-book balance query.
- The new filter-proposals endpoint added on develop is included in deleted-book and foreign-owner coverage.

## Every test run

Counts are JUnit test cases, not individual assertions. Every completed run had zero skipped tests, and PostgreSQL tests ran using Docker/Testcontainers.

| Phase/run                   | Command or selection                                                      | Passed | Failed | Skipped | Result                                                                                     |
| --------------------------- | ------------------------------------------------------------------------- | -----: | -----: | ------: | ------------------------------------------------------------------------------------------ |
| 1 targeted                  | `./gradlew test --tests '*BookMigrationTest'`                             |      1 |      0 |       0 | Passed                                                                                     |
| 1 full                      | `./gradlew build`                                                         |    233 |      0 |       0 | Passed                                                                                     |
| 2 targeted, initial         | `BookServiceTest`, `BookManagementServiceTest`                            |     10 |      3 |       0 | Reorder null-check failed on immutable lists; corrected before proceeding                  |
| 2 targeted, rerun           | Same selections                                                           |     13 |      0 |       0 | Passed                                                                                     |
| 2 full                      | `./gradlew build`                                                         |    240 |      0 |       0 | Passed                                                                                     |
| 3 targeted, initial         | `BookControllerTest`, `BookManagementApiTest`                             |     12 |      1 |       0 | Test compared different timestamp offsets for the same instant; assertion corrected        |
| 3 targeted, rerun           | Same selections, with added ownership test                                |     14 |      0 |       0 | Passed                                                                                     |
| 3 full                      | `./gradlew build`                                                         |    241 |      0 |       0 | Passed                                                                                     |
| 4 targeted                  | `./gradlew test --tests '*BookContractTest'`                              |      2 |      0 |       0 | Passed                                                                                     |
| 4 full                      | `./gradlew build`                                                         |    243 |      0 |       0 | Passed                                                                                     |
| 5 targeted, initial         | `BookManagementApiTest`, `BookManagementServiceTest`, `BookMigrationTest` |     17 |      2 |       0 | Corrected signup fixture to expect 201 and analysis fixture to include required DAY bucket |
| 5 targeted, rerun           | Same selections                                                           |     19 |      0 |       0 | Passed                                                                                     |
| 5 expanded, compile attempt | Same selections after adding final style/migration assertions             |      0 |      0 |       0 | Test compilation failed due to an escaped string; no tests executed                        |
| 5 expanded, corrected       | Same selections                                                           |     20 |      0 |       0 | Passed                                                                                     |
| 5 full                      | `./gradlew build`                                                         |    249 |      0 |       0 | Passed                                                                                     |

Selections above were run with `./gradlew test` and one `--tests '*ClassName'` argument per listed class.
Initial sandbox-restricted Gradle attempts in phases 1 and 2 stopped before execution; approved reruns used the Gradle cache and Docker. These are not counted as test passes.

Additional checks:

- `npx --yes @apidevtools/swagger-cli validate src/main/resources/openapi/pennywise-v1.yaml`: passed in phases 4 and 5. The CLI emitted its upstream deprecation notice; validation succeeded.
- Google Java Format 1.36.1: final dry-run passed for all changed Java files.
- Prettier: initial OpenAPI formatting check failed; formatting was applied and subsequent checks passed.
- `git diff --check`: passed.

No phase advanced with a failing suite. No database-dependent tests were skipped or left unverified.

## Migration cost and unverified production conditions

V8 sets a five-second lock acquisition timeout. It adds constant-default columns, ranks all existing books by owner/creation time/ID, and updates one row per book, including soft-deleted books.
Ranking requires a scan and sort; the UPDATE adds row writes, WAL, and dead tuples. The migration transaction holds its acquired DDL lock through the backfill; lock timeout does not bound that duration.

A PostgreSQL 16 fixture containing 10,007 books produced `Seq Scan -> Sort -> WindowAgg`, with an in-memory sort using about 1,010 kB. One targeted run of `EXPLAIN (ANALYZE, BUFFERS)` took approximately 4 ms for ranking. This measures ranking on a small test database, not full production migration duration or lock contention. Production row counts, hardware, load, WAL volume, and lock duration remain unverified.

## Retained risks and scope

- Very large opening balances can silently overflow Java balance arithmetic. Values outside +/- 9007199254740991 can lose precision in JavaScript clients. No cap or arithmetic behavior change was introduced.
- The grouped query has bounded query count, but its cost still grows with the number of active transactions. Production latency and query plans on large ledgers are unverified.
- Caffeine caches remain process-local. The fresh list avoids cached membership/balance problems, but cross-instance invalidation for other cached endpoints is not provided or verified.
- Soft-deleted books and their financial records remain stored indefinitely by this application. No purge job, retention deadline, or recovery endpoint exists. External database retention jobs were not inspected. Login and other books remain available.
- Requests/export streams authorized before deletion may finish afterward; cancellation is not implemented.
- No mobile changes were made. Theme mappings and unchanged visual appearance on the device were not verified.
- Hosted CI, SQLFluff, deployment, and production migration execution were not run. Local build, database tests, Java formatting, and OpenAPI validation passed.

## Deviations

No frozen contract changes. The fresh HTTP list bypasses the legacy entity cache to provide coherent metadata and balances. OpenAPI formatting was finalized in phase 5. V8 and filter-proposal coverage reflect the approved develop recheck. No purge job, recovery API, mobile work, currency tightening, or opening-balance cap was added.
