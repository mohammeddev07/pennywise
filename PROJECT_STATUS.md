# Pennywise project status

## 1. Stack

Scope: branch `feat/mvp-gap-fixes` off `develop` at `e20b4aa` (2026-07-25), not yet merged/PR'd.
`develop` itself is unchanged from the prior audit; PRs #4 (`codex/review-backend-api-plan`) and #5
(`codex/add-profile-and-book-delete`) are both merged there, plus a follow-up reconciliation commit
(`a6c8216`) and Markdown lint fixes. This branch adds a CORS config and a scheduled Supabase
keepalive workflow (see sections 3 and 9 below) — both additive, no existing behavior changed.

- Java 21 toolchain (`build.gradle`); Gradle wrapper 8.14.3
  (`gradle/wrapper/gradle-wrapper.properties`). `gradle.properties` no longer hardcodes a
  developer-specific JDK path (previously flagged as non-portable; now fixed).
- Spring Boot 3.5.9, Spring Dependency Management 1.1.7, Spring Framework 6.2.15, and Spring
  Security 6.5.7 (Boot-managed versions).
- Gradle build with Spring Web (embedded Tomcat), Validation, Data JPA/Hibernate 6.6.39.Final,
  Security, OAuth2 Resource Server/Jose, and Actuator.
- PostgreSQL only: runtime PostgreSQL JDBC driver 42.7.8 and HikariCP 6.3.3 (resolved Boot-managed
  versions). There is no H2, MySQL, or Mongo dependency.
- Flyway Core and Flyway PostgreSQL 11.7.2; Springdoc OpenAPI WebMVC UI 2.8.15; Lombok 1.18.42.
- Tests use JUnit Jupiter 5.12.2, Mockito, Spring Security Test, and Testcontainers PostgreSQL
  1.21.4.
- A multi-stage `Dockerfile` now exists (`eclipse-temurin:21-jdk-jammy` build stage,
  `eclipse-temurin:21-jre-jammy` runtime stage), runs as a non-root `pennywise` user, and starts
  the jar with a constrained heap (`-XX:MaxRAMPercentage=70`, serial GC, small thread stacks) —
  this closes the previous "no deployment configuration" gap.
- A top-level `DEPLOYMENT.md` documents environment variables, a local Docker run recipe, and
  several verified API response shapes (ETag format, transaction-list envelope, budget response,
  signup fields, idempotency behavior).

## 2. Architecture

`com.axel.pennywise` — Spring Boot entry point (`Application`).

`com.axel.pennywise.api.controller` — REST controllers for health, auth, users, books, categories,
transactions, budgets, summaries, and exports.

`com.axel.pennywise.api.dto.auth` — sign-up/login/token DTOs.

`com.axel.pennywise.api.dto.book` — book request/response DTOs.

`com.axel.pennywise.api.dto.budget` — monthly budget request/response DTOs.

`com.axel.pennywise.api.dto.category` — category request/response DTOs.

`com.axel.pennywise.api.dto.common` — list and cursor-page wrappers.

`com.axel.pennywise.api.dto.export` — export job/download DTOs.

`com.axel.pennywise.api.dto.summary` — balance, monthly summary, and category-breakdown DTOs.

`com.axel.pennywise.api.dto.transaction` — transaction request, response, filter, and cursor DTOs.

`com.axel.pennywise.api.dto.user` — current-user response and update-request DTOs.

`com.axel.pennywise.config` — security, OpenAPI, request-id filter, CORS (new — `CorsConfig`,
`WebMvcConfigurer`), and `AppProperties` (`@ConfigurationProperties(prefix = "app")`, now bound to
`app.security`, `app.pagination`, and `app.export`; `app.cors.allowed-origins` was added as a plain
`@Value` in `CorsConfig`, not yet folded into the `AppProperties` record).

`com.axel.pennywise.domain.auth` — email/password signup/login and locally signed JWT creation.

`com.axel.pennywise.domain.book` — book persistence, ownership lookup, soft-delete (blocked while
active transactions exist), and default-category seeding orchestration.

`com.axel.pennywise.domain.budget` — budget entity/repository/service and monthly spend
calculations.

`com.axel.pennywise.domain.category` — category entity/repository, default category seeding, and
soft-delete (blocked while active transactions exist).

`com.axel.pennywise.domain.common` — common audited/soft-delete/versioned entity fields.

`com.axel.pennywise.domain.export` — export-job persistence and a non-functional presigned-URL
placeholder (still returns a hardcoded `example.com` URL — unchanged from the prior audit).

`com.axel.pennywise.domain.idempotency` — per-user idempotency-key persistence and replay service.

`com.axel.pennywise.domain.summary` — balance and monthly aggregate services.

`com.axel.pennywise.domain.transaction` — transaction entity/repository, filtering, cursor
pagination, updates, soft deletion, and category/transaction-type consistency validation.

`com.axel.pennywise.domain.user` — user entity/repository, lazy user provisioning from a JWT
subject, and default-currency update.

`com.axel.pennywise.exception` — API exception type and JSON error advice, including a dedicated
`DateTimeParseException` handler.

`com.axel.pennywise.security` — current-JWT helpers and JWT audience validation.

`com.axel.pennywise.util` — request-id logging filter and SHA-256 utility.

The normal request path is controller -> service/repository -> JPA entity -> PostgreSQL. Ownership
is enforced at the book boundary before nested category, transaction, budget, summary, and export
operations.

## 3. API Surface

All controller paths below are prefixed by `/api` because `server.servlet.context-path=/api` in
`src/main/resources/application.properties`. "Required outside local" means security defaults to
disabled when `ENV=local` and enabled for any other `ENV` unless overridden.

| Method | Path                                                        | Controller.method              | Auth required?         | Status (working / stubbed / broken)                                                                                                                                                                                                |
| ------ | ----------------------------------------------------------- | ------------------------------ | ---------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| GET    | `/api/health`                                               | `HealthController.health`      | No                     | working — returns a fixed `{"status":"UP"}`; it does not check PostgreSQL.                                                                                                                                                         |
| POST   | `/api/v1/auth/signup`                                       | `AuthController.signup`        | No                     | working — creates a user, BCrypt hash, local HS256 JWT; accepts optional `defaultCurrencyCode`.                                                                                                                                    |
| POST   | `/api/v1/auth/login`                                        | `AuthController.login`         | No                     | working — verifies BCrypt hash and returns a local HS256 JWT.                                                                                                                                                                      |
| GET    | `/api/v1/me`                                                | `UserController.me`            | Required outside local | working — returns/creates the current subject's user.                                                                                                                                                                              |
| PATCH  | `/api/v1/me`                                                | `UserController.patchMe`       | Required outside local | working (new) — updates `defaultCurrencyCode`; requires at least one field.                                                                                                                                                        |
| GET    | `/api/v1/books`                                             | `BookController.list`          | Required outside local | working — current user's active books only.                                                                                                                                                                                        |
| POST   | `/api/v1/books`                                             | `BookController.create`        | Required outside local | working — creates a book and seeds categories.                                                                                                                                                                                     |
| GET    | `/api/v1/books/{bookId}`                                    | `BookController.get`           | Required outside local | working — owner-only lookup with ETag.                                                                                                                                                                                             |
| PATCH  | `/api/v1/books/{bookId}`                                    | `BookController.patch`         | Required outside local | working — name-only update; requires `If-Match`.                                                                                                                                                                                   |
| DELETE | `/api/v1/books/{bookId}`                                    | `BookController.delete`        | Required outside local | working (new) — soft delete; requires `If-Match`; refuses (`409 BOOK_HAS_TRANSACTIONS`) while active transactions exist.                                                                                                           |
| GET    | `/api/v1/books/{bookId}/categories`                         | `CategoryController.list`      | Required outside local | working — active categories for an owned book.                                                                                                                                                                                     |
| POST   | `/api/v1/books/{bookId}/categories`                         | `CategoryController.create`    | Required outside local | working — validates/normalizes name and prevents same book/type/name duplicates.                                                                                                                                                   |
| PATCH  | `/api/v1/books/{bookId}/categories/{categoryId}`            | `CategoryController.patch`     | Required outside local | working — update name/disabled/icon/color; requires `If-Match`.                                                                                                                                                                    |
| DELETE | `/api/v1/books/{bookId}/categories/{categoryId}`            | `CategoryController.delete`    | Required outside local | working (new) — soft delete; requires `If-Match`; refuses (`409 CATEGORY_IN_USE`) while active transactions reference it.                                                                                                          |
| GET    | `/api/v1/books/{bookId}/transactions`                       | `TransactionController.list`   | Required outside local | working — `from`, `to`, `type`, `categoryId`, `q`, `limit`, and cursor filters. Malformed `from`/`to` now returns a mapped `400` via `DateTimeParseException` handling (previously fell through to 500).                           |
| POST   | `/api/v1/books/{bookId}/transactions`                       | `TransactionController.create` | Required outside local | working — requires `Idempotency-Key`; category type must now match transaction type (`validateCategoryType`, 400 on mismatch) — previously unenforced.                                                                             |
| GET    | `/api/v1/books/{bookId}/transactions/{txId}`                | `TransactionController.get`    | Required outside local | working — active transaction in an owned book, with ETag.                                                                                                                                                                          |
| PATCH  | `/api/v1/books/{bookId}/transactions/{txId}`                | `TransactionController.patch`  | Required outside local | working — partial update; requires `If-Match`; category/type consistency is now enforced on update too.                                                                                                                            |
| DELETE | `/api/v1/books/{bookId}/transactions/{txId}`                | `TransactionController.delete` | Required outside local | working — soft delete; requires `If-Match`.                                                                                                                                                                                        |
| GET    | `/api/v1/books/{bookId}/budgets?month=YYYY-MM`              | `BudgetController.list`        | Required outside local | working — extra feature, lists monthly budgets and spent/remaining values.                                                                                                                                                         |
| PUT    | `/api/v1/books/{bookId}/budgets/{categoryId}?month=YYYY-MM` | `BudgetController.upsert`      | Required outside local | working — extra feature; expense-category budgets only.                                                                                                                                                                            |
| DELETE | `/api/v1/books/{bookId}/budgets/{categoryId}?month=YYYY-MM` | `BudgetController.delete`      | Required outside local | working — soft delete; optional `If-Match`.                                                                                                                                                                                        |
| GET    | `/api/v1/books/{bookId}/balance`                            | `SummaryController.balance`    | Required outside local | working — opening balance + all income - all expense.                                                                                                                                                                              |
| GET    | `/api/v1/books/{bookId}/summary/monthly?month=YYYY-MM`      | `SummaryController.monthly`    | Required outside local | working — monthly income/expense and expense category breakdown.                                                                                                                                                                   |
| POST   | `/api/v1/books/{bookId}/exports/csv`                        | `ExportController.createCsv`   | Required outside local | stubbed — only creates a `PENDING` job; no CSV worker exists. Unchanged.                                                                                                                                                           |
| GET    | `/api/v1/books/{bookId}/exports/{exportId}`                 | `ExportController.get`         | Required outside local | stubbed — exposes the job, which never advances without external/manual DB changes. Unchanged.                                                                                                                                     |
| GET    | `/api/v1/books/{bookId}/exports/{exportId}/download`        | `ExportController.download`    | Required outside local | stubbed — `ExportPresignService` still returns a hardcoded `example.com` URL; no storage integration exists. Unchanged.                                                                                                            |
| GET    | `/api/actuator/health/**`, `/api/actuator/info`             | Spring Actuator                | No                     | working framework endpoints; health is public. `DEPLOYMENT.md` documents `/api/actuator/health` as the host health-check path.                                                                                                     |
| GET    | `/api/v3/api-docs/**`                                       | Springdoc                      | No                     | working framework endpoint by configuration; enabled by default in every environment; can be disabled via `SPRINGDOC_API_DOCS_ENABLED`.                                                                                            |
| GET    | `/api/swagger-ui/**`                                        | Springdoc                      | No                     | still pointed at `/api/openapi/pennywise-v1.yaml` via `springdoc.swagger-ui.url`; `src/main/resources/openapi/` still has no resource-handler/static-location configuration — this remains broken, unchanged from the prior audit. |

`management.endpoints.web.exposure.include` is now `health,info` only — the previous
`metrics`/`prometheus` exposure (and the broken Prometheus registration, since
`micrometer-registry-prometheus` was never a dependency) has been removed.

## 4. Data Model

Migrations exist: `src/main/resources/db/migration/V1__init.sql` through
`V4__mobile_contract_auth_and_budgets.sql` — **4 migrations, not 5**. The prior audit flagged two
competing `V4` scripts from PR #4 and PR #5; per `DEPLOYMENT.md`, the duplicate was reconciled by
deletion because the proposed second `V4` contained no statements not already present in the
merged `V4`, and there is intentionally no `V5`. Flyway creates and migrates schema
`expense_tracker`; Hibernate is validation-only (`spring.jpa.hibernate.ddl-auto=validate`), so it
will not generate or alter tables.

<!-- markdownlint-disable MD013 -->

| Table / entity                              | Fields and PostgreSQL types                                                                                                                                                                                                                                                                                                                     | Relationships                                                                                                                                                                                              |
| ------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `users` / `UserEntity`                      | `id UUID`; `auth_subject TEXT UNIQUE NOT NULL`; `email TEXT NULL`; `password_hash TEXT NULL`; `default_currency_code VARCHAR(3) NULL`; inherited `created_at TIMESTAMPTZ`, `updated_at TIMESTAMPTZ`, `deleted_at TIMESTAMPTZ`, `version BIGINT`.                                                                                                | Referenced by `books.owner_user_id`, `export_jobs.requested_by_user_id`, and `idempotency_keys.user_id`.                                                                                                   |
| `books` / `BookEntity`                      | `id UUID`; `owner_user_id UUID NOT NULL`; `name TEXT`; `currency_code VARCHAR(3)`; `timezone TEXT`; `opening_balance_minor BIGINT`; inherited audit/version fields.                                                                                                                                                                             | Many books to one owner user; referenced by categories, transactions, budgets, and export jobs. Now soft-deletable via `DELETE /v1/books/{bookId}`.                                                        |
| `categories` / `CategoryEntity`             | `id UUID`; `book_id UUID`; `type TEXT CHECK (INCOME, EXPENSE)`; `name TEXT`; `is_disabled BOOLEAN`; `icon TEXT NULL`; `color VARCHAR(7) NULL` with hex-color check; inherited audit/version fields.                                                                                                                                             | Many categories to one book; referenced by transactions and budgets. Active name uniqueness is `(book_id, type, lower(name))`. Now soft-deletable via `DELETE /v1/books/{bookId}/categories/{categoryId}`. |
| `transactions` / `TransactionEntity`        | `id UUID`; `book_id UUID`; `type TEXT CHECK (INCOME, EXPENSE)`; `amount_minor BIGINT CHECK (>0)`; `occurred_on DATE`; `occurred_at TIMESTAMPTZ NULL`; `category_id UUID`; `title TEXT NULL` (max-120 check); `payment_method TEXT NULL` (`CASH`, `CARD`, `BANK_TRANSFER`, `WALLET`, `OTHER`); `note TEXT NULL`; inherited audit/version fields. | Many transactions to one book and one category. "Expense" is modelled as this transaction table, not a separate `ExpenseEntity`. Create/update now validate category type matches transaction type.        |
| `budgets` / `BudgetEntity`                  | `id UUID`; `book_id UUID`; `category_id UUID`; `month_start DATE`; `amount_minor BIGINT CHECK (>=0)`; inherited audit/version fields.                                                                                                                                                                                                           | Many budgets to one book and one category. Active uniqueness is `(book_id, category_id, month_start)`.                                                                                                     |
| `export_jobs` / `ExportJobEntity`           | `id UUID`; `book_id UUID`; `requested_by_user_id UUID`; `status TEXT CHECK (PENDING, RUNNING, COMPLETED, FAILED)`; `file_name TEXT NULL`; `storage_key TEXT NULL`; `error_message TEXT NULL`; inherited audit/version fields.                                                                                                                   | Many jobs to one book and requesting user. No producer changes status or creates a file.                                                                                                                   |
| `idempotency_keys` / `IdempotencyKeyEntity` | Composite primary key `user_id UUID`, `idem_key TEXT`; `request_hash TEXT`; `response_code INT NULL`; `response_body TEXT NULL`; `created_at TIMESTAMPTZ`.                                                                                                                                                                                      | Per-user request-response cache. Its `user_id` references `users`.                                                                                                                                         |

<!-- markdownlint-enable MD013 -->

All supplied DDL is PostgreSQL-specific and PostgreSQL-compatible: `pgcrypto`, `gen_random_uuid()`,
`TIMESTAMPTZ`, partial indices, and PostgreSQL regular-expression syntax are intentional. It will
not run on H2/MySQL without replacement. Fresh database validation against a live PostgreSQL
instance is now **verified locally**: Docker is available on this machine, so
`ApplicationTests.contextLoads` (the Testcontainers PostgreSQL context test) ran and passed as part
of the full suite (see Testing) — previously this was skipped and unverified.

## 5. Authentication

Authentication is implemented, not a demo stub. `SecurityConfig` configures stateless Spring
Security with a JWT resource server; `AuthService` signs HS256 tokens and `PasswordEncoder` is
`BCryptPasswordEncoder`. `POST /v1/auth/signup` stores a BCrypt `passwordHash` and accepts an
optional `defaultCurrencyCode`; `POST /v1/auth/login` verifies it. Tokens include issuer,
expiration, subject (`local:<normalized-email>`), email, and `user_id` claims.

Local mode still defaults `ENV` to `local`, which causes `SecurityConfig.isAuthEnabled()` to permit
every request; in that mode every unauthenticated request falls back to subject `local`, so all
callers share one lazily created user. This is intentional for local development, not a production
default. **New since the prior audit**: `SecurityConfig.localSecretKey()` now actively rejects
startup if `APP_JWT_LOCAL_SECRET` is left at the committed development value
(`pennywise-local-development-secret-change-me`) _unless_ running in local mode with auth
explicitly disabled — this closes the "dangerous default" gap the prior audit flagged, by making
misconfiguration fail fast instead of silently signing tokens with a public secret. It also now
enforces a minimum 32-byte (HS256-safe) secret length.

With authentication enabled, isolation is implemented through `BookService.requireOwned`: every
nested category, transaction, budget, summary, and export route first queries the book by both book
ID and authenticated user's database ID. A user cannot read or modify another user's data through
these routes; ownership failures become `404`. This still has unit coverage only (`SecurityConfigTest`,
`SecurityConfigLocalSecretTest`), not a real JWT-plus-PostgreSQL integration test, though the
Testcontainers context test now exercises a real database connection.

`APP_JWT_ISSUER_URI` enables external issuer validation in the decoder, but the built-in
signup/login service still signs local HS256 tokens. External-issuer mode and this backend-owned
email/password flow remain incompatible unless additional auth work is done; `DEPLOYMENT.md`
explicitly instructs leaving `APP_JWT_ISSUER_URI` unset for the built-in signup/login flow.

There are still no refresh tokens, logout/revocation, password-reset, email-verification,
throttling, or account-recovery flows.

## 6. Configuration & Secrets

`src/main/resources/application.properties` defines the following environment-backed settings:

| Variable                                                      | Default / purpose                                                                                      | Required for a safe deployment?                                                                                    |
| ------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------ |
| `ENV`                                                         | `local`; controls default security behaviour.                                                          | Yes — set a non-`local` value, e.g. `production`.                                                                  |
| `PORT`                                                        | `8080` (new — `server.port=${PORT:8080}`).                                                             | No — the prior "ignores injected PORT" gap is fixed; free hosts that inject `PORT` now work.                       |
| `JDBC_URL`                                                    | Local PostgreSQL `jdbc:postgresql://localhost:5432/postgres?currentSchema=expense_tracker`.            | Yes — replace with the Supabase/Postgres JDBC URL, including required SSL parameters.                              |
| `DB_USERNAME` / `DB_PASSWORD`                                 | Both default to `postgres`.                                                                            | Yes — set database credentials from the provider.                                                                  |
| `DB_POOL_MAX` / `DB_POOL_MIN`                                 | `3` / `0` (new — previously hardcoded to 10/2; now smaller and env-tunable for small free-tier hosts). | Optional; tune to the host's resource limits.                                                                      |
| `APP_SECURITY_AUTH_ENABLED`                                   | Blank; otherwise overrides the `ENV`-derived setting.                                                  | Set to `true` defensively.                                                                                         |
| `APP_JWT_LOCAL_SECRET`                                        | Hardcoded `pennywise-local-development-secret-change-me`.                                              | Yes — supply at least 32 random bytes; startup now rejects the committed default outside local auth-disabled mode. |
| `APP_JWT_ISSUER`                                              | `pennywise`.                                                                                           | Recommended — set a stable production issuer.                                                                      |
| `APP_JWT_AUDIENCE`                                            | Blank; validates `aud` only when supplied.                                                             | Optional, but recommended for a mobile API.                                                                        |
| `APP_JWT_ACCESS_TOKEN_TTL_MINUTES`                            | `60`.                                                                                                  | Optional.                                                                                                          |
| `APP_JWT_ISSUER_URI`                                          | Blank; switches decoder to an external issuer.                                                         | Leave blank for the built-in signup/login flow.                                                                    |
| `SPRINGDOC_API_DOCS_ENABLED` / `SPRINGDOC_SWAGGER_UI_ENABLED` | Both `true`.                                                                                           | Optional; disable in production if public docs are not wanted.                                                     |
| `APP_EXPORT_BUCKET`                                           | Blank.                                                                                                 | Not required; it is not used by any export implementation.                                                         |

`LOG_FILE`, `LOG_PATH`, and `LOG_TEMP` are optional Logback substitutions. The app also uses fixed
`app.db.schema=expense_tracker`, enables Flyway, and exposes health/info only via Actuator
(`metrics`/`prometheus` exposure was removed — see API Surface). Hikari pool sizing is now smaller
by default (`maximum-pool-size=3`, `minimum-idle=0`) and configurable via `DB_POOL_MAX`/`DB_POOL_MIN`,
addressing the prior "not comfortably tiny for a 512 MB host" note.

Hardcoded credentials/secrets still exist in committed configuration: the local PostgreSQL
`postgres`/`postgres` defaults and the JWT development secret above — both are development-only
defaults, and the JWT one is now actively rejected outside local auth-disabled mode (see
Authentication). Test code also embeds test secrets and a Testcontainers password. No real
production secret file was found.

## 7. Testing

A fresh (non-cached) `./gradlew test --rerun` run on 2026-08-08 passed: **103 tests discovered, 103
passed, 0 failed, 0 skipped** across 13 test classes — a meaningful jump from the prior audit's
72 discovered / 71 passed / 1 skipped. New test classes include `SecurityConfigTest`,
`SecurityConfigLocalSecretTest`, `UserControllerTest`, and `IdempotencyServiceTest`; existing
controller suites (books, categories, transactions, summaries, exports, budgets) now also cover the
new delete/patch endpoints. `./gradlew bootJar` also passed.

Docker was available on this machine during this audit, so
`ApplicationTests.contextLoads` — the only `@SpringBootTest`
(`@Testcontainers(disabledWithoutDocker = true)`) — actually ran against a real PostgreSQL
Testcontainer and passed, rather than being skipped. This resolves the prior audit's "a green test
result does not prove the service starts against Supabase" caveat for this environment, though it
does not by itself prove the _Supabase-hosted_ database works (SSL/connection-string differences
are still unverified).

The only CI workflow, `.github/workflows/super-linter.yml`, now runs **two jobs** on pull requests
targeting `main` or `develop`: a `Gradle Build` job (`./gradlew build`, i.e. compiles and runs tests)
and the pre-existing Super-Linter `lint` job. This resolves the prior "CI does not run Gradle build
or tests" gap. Whether the current `develop` HEAD's most recent CI run is green was not directly
checked in this audit (no `gh` lookup performed) — verify via the repository's Actions tab before
relying on it.

## 8. Build & Run

Prerequisites: a Java 21 runtime/toolchain and a reachable PostgreSQL database (or Docker, for the
containerized flow below). `gradle.properties` no longer pins a developer-specific
`org.gradle.java.home`, so the wrapper now builds portably on any machine with a discoverable JDK 21.

```bash
export JDBC_URL='jdbc:postgresql://localhost:5432/postgres?currentSchema=expense_tracker'
export DB_USERNAME='postgres'
export DB_PASSWORD='postgres'
export ENV='local'

./gradlew test
./gradlew bootRun
```

For a packaged run:

```bash
./gradlew bootJar
java -jar build/libs/pennywise-0.0.1-SNAPSHOT.jar
```

For a containerized run, see `DEPLOYMENT.md` for a full Docker network / Postgres / API recipe
using the new `Dockerfile`. The listener now honors `PORT` (`server.port=${PORT:8080}`, default 8080) and the API context remains `/api`.

## 9. In-Flight Work

Both PRs tracked in the prior audit are merged:

- PR #4, `Add mobile auth, budgets, and ledger metadata` — merged into `develop` (`a1998e0`).
- PR #5, `Add soft-delete and profile currency endpoints` — merged into `develop` (`e20b4aa`),
  after `f3f7278 Fix reconciliation migration collision` resolved the competing `V4` migration and
  `201836a Fix remaining Markdown lint errors` and `9a2a8a3`/`5e1d79d` (`Prepare backend for
production deployment`) added the Dockerfile, `PORT` support, Hikari tuning, the JWT
  local-secret guard, and `DEPLOYMENT.md`.

**New**: `feat/mvp-gap-fixes` (branched off `develop` at `e20b4aa`, not yet opened as a PR — no
`gh` CLI available when it was pushed; compare at
`github.com/mohammeddev07/pennywise/compare/develop...feat/mvp-gap-fixes`). Two commits:

- Adds `CorsConfig` and the `app.cors.allowed-origins` property (see Gaps, section 10) — permissive
  by default, MVC-level only, does not touch `SecurityConfig`.
- Adds `.github/workflows/supabase-keepalive.yml`: scheduled every 4 days
  (`cron: "0 6 */4 * *"`), runs `psql "$SUPABASE_DB_URL" -c "SELECT 1;"` to keep a free-tier
  Supabase project from pausing on inactivity. **Will not succeed yet** — it references a
  `SUPABASE_DB_URL` repository secret that has not been created in GitHub settings.

`./gradlew build` passed on this branch (full suite, no test changes needed since both additions
are additive). Before starting new work, run `git log` / `gh pr list` again to confirm no further
branches have been opened since this update.

## 10. Gaps vs. Target State

- [x] User registration endpoint — **Done**: `POST /api/v1/auth/signup` in `AuthController` persists
      a user and returns a JWT.
- [x] Login endpoint returning a token — **Done**: `POST /api/v1/auth/login` returns `AuthResponse`
      with bearer token and expiry.
- [x] Password hashing — **Done**: `AuthService` stores BCrypt hashes via `PasswordEncoder`.
- [x] User entity + persistence — **Done**: `UserEntity`, `UserRepository`, and Flyway table exist.
- [x] Expense entity with amount, category, date, note, type (in/out) — **Done**:
      `TransactionEntity` models income/expense using integer minor units; it is named transaction
      rather than expense.
- [x] Category entity or enum — **Done**: persisted `CategoryEntity` and `CategoryType` exist.
- [x] Create expense endpoint — **Done**: transaction POST persists an owned-book entry and now
      rejects category/type mismatches (previously partial).
- [x] List expenses endpoint — **Done**: transaction GET lists active entries in an owned book.
- [x] Update expense endpoint — **Done**: transaction PATCH is ETag-protected and now also
      validates category/type consistency.
- [x] Delete expense endpoint — **Done**: transaction DELETE soft-deletes and is ETag-protected.
- [x] Filter by date range (day / 7 days / month) — **Done**: generic `from`/`to` dates let the
      mobile client request any of those windows. Malformed dates now return a proper `400`
      (previously fell through to a 500 — fixed).
- [x] Filter by category — **Done**: `categoryId` filters transaction GET.
- [~] Summary/aggregate endpoint (totals, per-category, balance) — **Partial, unchanged**: balance
  is all-time; monthly summary exposes monthly income, expense, and expense-category totals. There
  is still no generic/all-time "total spent" aggregate endpoint or day/last-seven-day aggregate
  endpoint.
- [x] User-scoped data isolation (expenses filtered by authenticated user) — **Done when production
      auth is enabled**: book ownership scopes nested data, and production startup now actively
      refuses to run with the default JWT secret outside local auth-disabled mode.
- [x] Input validation on all write endpoints — **Done**: DTO validation and service checks cover
      the fields flagged before; transaction category type is now required to match INCOME/EXPENSE
      on both create and update. (Book timezone/currency semantic validation and empty-PATCH-body
      handling were not re-audited in detail this pass — spot-check before relying on this fully.)
- [x] Consistent error response format — **Done**: `GlobalExceptionHandler` now maps
      `DateTimeParseException` (previously the one documented gap — malformed `from`/`to` produced
      a generic 500) in addition to the handlers already present.
- [~] CORS configured for the mobile client — **Partial, on `feat/mvp-gap-fixes`**: new
  `CorsConfig` (`WebMvcConfigurer`) permits GET/POST/PATCH/DELETE and
  Authorization/Content-Type/If-Match/Idempotency-Key headers, origins from
  `APP_CORS_ALLOWED_ORIGINS` (defaults to `*`, since auth is bearer-token only with no
  cookie/session state to protect). Native mobile clients don't need this — it targets Swagger
  UI / future web clients. It is MVC-level only and does **not** touch `SecurityConfig`, so a
  browser preflight `OPTIONS` to an `anyRequest().authenticated()` route (i.e. most of the API
  once auth is enabled) is still rejected by the security filter chain before reaching MVC's
  CORS handling; it reliably works only for the already-`permitAll` routes (health, auth,
  Springdoc). Extending it to authenticated routes needs a `SecurityConfig` change, deliberately
  left out of this branch.
- [~] Pagination on list endpoints — **Partial, unchanged**: transactions have cursor pagination
  (1–200, default 50); books, categories, and budgets still return unbounded lists.
- [x] User account/profile management — **New**: `PATCH /api/v1/me` updates `defaultCurrencyCode`.
- [x] Book/category deletion — **New**: `DELETE /api/v1/books/{bookId}` and
      `DELETE /api/v1/books/{bookId}/categories/{categoryId}` soft-delete and refuse when active
      transactions still reference the resource.

## 11. Free-Tier Deployment Readiness

The configured database is PostgreSQL and is appropriate for Supabase or a comparable free-tier
Postgres service. Migrations are PostgreSQL-only by design, so no database-engine migration is
needed. The app expects it can create/use schema `expense_tracker`; verify that the deployed
database role has the required schema privileges.

Most of the prior audit's readiness gaps are now closed:

- [x] A multi-stage `Dockerfile` exists and produces a runnable, non-root container image.
- [x] `server.port=${PORT:8080}` is configured, so hosts that inject `PORT` (Render, etc.) work.
- [x] Hikari pool defaults are now small (`maximum-pool-size=3`, `minimum-idle=0`) and
      env-tunable via `DB_POOL_MAX`/`DB_POOL_MIN`, better suited to a 512 MB tier.
- [x] The JVM is started with `-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k
-XX:TieredStopAtLevel=1` in the `Dockerfile` `ENTRYPOINT`, capping heap for small hosts.
- [x] The committed local JWT secret is now actively rejected at startup outside local
      auth-disabled mode, preventing an accidental production deploy with a public secret.
- [x] `DEPLOYMENT.md` documents the health-check path (`/api/actuator/health`) and environment
      variables end-to-end.

Remaining gaps before a real deployment:

1. CORS: `feat/mvp-gap-fixes` adds a permissive, env-var-configurable `CorsConfig`, but (a) it
   defaults to allowing all origins — decide whether to restrict `APP_CORS_ALLOWED_ORIGINS` before
   relying on it in production, and (b) it only reliably applies to `permitAll` routes, not
   authenticated ones (see Gaps, section 10). Native mobile clients don't need browser CORS at all.
2. Run Flyway plus Hibernate validation against a fresh **Supabase** project specifically (this
   audit verified against a local Testcontainers PostgreSQL instance, not Supabase's managed
   Postgres/SSL configuration) and confirm the `JDBC_URL` SSL parameters Supabase requires work
   with the current datasource config.
3. Decide whether public Swagger/OpenAPI and unauthenticated health/info are acceptable in
   production; otherwise set the Springdoc flags to `false`.
4. The rolling file logger still targets ephemeral `/tmp` with a 5 GB retention cap by default,
   unsuitable as production log storage — not re-verified this pass, check `logback` config if log
   persistence matters.
5. Keep all durable data in Postgres. The export feature (CSV job creation, download presigning)
is still entirely stubbed and unimplemented; leave it disabled or implement it separately if
exports are required. `/api/swagger-ui/**` also remains broken (see API Surface) — low priority
unless API docs are needed by consumers.
</content>
