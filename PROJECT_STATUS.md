# Pennywise project status

## 1. Stack

Scope: current checkout `codex/review-backend-api-plan` at `a75f923` (also open as PR #4), plus the
two open PRs described below. The production baseline branch is `develop` at `58fa814`.

- Java 21 toolchain (`build.gradle`); Gradle wrapper 8.14.3
  (`gradle/wrapper/gradle-wrapper.properties`). `gradle.properties` also hardcodes one developer's
  Corretto 21 path, which makes the build non-portable until removed or changed.
- Spring Boot 3.5.9, Spring Dependency Management 1.1.7, Spring Framework 6.2.15, and Spring
  Security 6.5.7 (Boot-managed versions).
- Gradle build with Spring Web (embedded Tomcat), Validation, Data JPA/Hibernate 6.6.39.Final,
  Security, OAuth2 Resource Server/Jose, and Actuator.
- PostgreSQL only: runtime PostgreSQL JDBC driver 42.7.8 and HikariCP 6.3.3 (resolved Boot-managed
  versions). There is no H2, MySQL, or Mongo dependency.
- Flyway Core and Flyway PostgreSQL 11.7.2; Springdoc OpenAPI WebMVC UI 2.8.15; Lombok 1.18.42.
- Tests use JUnit Jupiter 5.12.2, Mockito, Spring Security Test, and Testcontainers PostgreSQL
  1.21.4.

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

`com.axel.pennywise.api.dto.user` — current-user response DTO.

`com.axel.pennywise.config` — security, OpenAPI, request-id filter, and unused/unbound
`AppProperties` definitions.

`com.axel.pennywise.domain.auth` — email/password signup/login and locally signed JWT creation.

`com.axel.pennywise.domain.book` — book persistence, ownership lookup, and default-category seeding
orchestration.

`com.axel.pennywise.domain.budget` — budget entity/repository/service and monthly spend
calculations.

`com.axel.pennywise.domain.category` — category entity/repository and default category seeding.

`com.axel.pennywise.domain.common` — common audited/soft-delete/versioned entity fields.

`com.axel.pennywise.domain.export` — export-job persistence and a non-functional presigned-URL
placeholder.

`com.axel.pennywise.domain.idempotency` — per-user idempotency-key persistence and replay service.

`com.axel.pennywise.domain.summary` — balance and monthly aggregate services.

`com.axel.pennywise.domain.transaction` — transaction entity/repository, filtering, cursor
pagination, updates, and soft deletion.

`com.axel.pennywise.domain.user` — user entity/repository and lazy user provisioning from a JWT
subject.

`com.axel.pennywise.exception` — API exception type and JSON error advice.

`com.axel.pennywise.security` — current-JWT helpers and JWT audience validation.

`com.axel.pennywise.util` — request-id logging filter and SHA-256 utility.

The normal request path is controller -> service/repository -> JPA entity -> PostgreSQL. Ownership
is enforced at the book boundary before nested category, transaction, budget, summary, and export
operations.

## 3. API Surface

All controller paths below are prefixed by `/api` because `server.servlet.context-path=/api` in
`src/main/resources/application.properties`. “Required outside local” means security defaults to
disabled when `ENV=local` and enabled for any other `ENV` unless overridden.

| Method | Path                                                        | Controller.method              | Auth required?         | Status (working / stubbed / broken)                                                                                                                          |
| ------ | ----------------------------------------------------------- | ------------------------------ | ---------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| GET    | `/api/health`                                               | `HealthController.health`      | No                     | working — returns a fixed `{"status":"UP"}`; it does not check PostgreSQL.                                                                                   |
| POST   | `/api/v1/auth/signup`                                       | `AuthController.signup`        | No                     | working — creates a user, BCrypt hash, and local HS256 JWT.                                                                                                  |
| POST   | `/api/v1/auth/login`                                        | `AuthController.login`         | No                     | working — verifies BCrypt hash and returns a local HS256 JWT.                                                                                                |
| GET    | `/api/v1/me`                                                | `UserController.me`            | Required outside local | working — returns/creates the current subject's user.                                                                                                        |
| GET    | `/api/v1/books`                                             | `BookController.list`          | Required outside local | working — current user's active books only.                                                                                                                  |
| POST   | `/api/v1/books`                                             | `BookController.create`        | Required outside local | working — creates a book and seeds categories.                                                                                                               |
| GET    | `/api/v1/books/{bookId}`                                    | `BookController.get`           | Required outside local | working — owner-only lookup with ETag.                                                                                                                       |
| PATCH  | `/api/v1/books/{bookId}`                                    | `BookController.patch`         | Required outside local | working — name-only update; requires `If-Match`.                                                                                                             |
| GET    | `/api/v1/books/{bookId}/categories`                         | `CategoryController.list`      | Required outside local | working — active categories for an owned book.                                                                                                               |
| POST   | `/api/v1/books/{bookId}/categories`                         | `CategoryController.create`    | Required outside local | working — validates/normalizes name and prevents same book/type/name duplicates.                                                                             |
| PATCH  | `/api/v1/books/{bookId}/categories/{categoryId}`            | `CategoryController.patch`     | Required outside local | working — update/disable category; requires `If-Match`.                                                                                                      |
| GET    | `/api/v1/books/{bookId}/transactions`                       | `TransactionController.list`   | Required outside local | working — `from`, `to`, `type`, `categoryId`, `q`, `limit`, and cursor filters. Invalid manually parsed dates currently fall through to a 500 error.         |
| POST   | `/api/v1/books/{bookId}/transactions`                       | `TransactionController.create` | Required outside local | working — requires `Idempotency-Key`; it does not ensure that the category type matches transaction type.                                                    |
| GET    | `/api/v1/books/{bookId}/transactions/{txId}`                | `TransactionController.get`    | Required outside local | working — active transaction in an owned book, with ETag.                                                                                                    |
| PATCH  | `/api/v1/books/{bookId}/transactions/{txId}`                | `TransactionController.patch`  | Required outside local | working — partial update; requires `If-Match`; category/type mismatch remains possible.                                                                      |
| DELETE | `/api/v1/books/{bookId}/transactions/{txId}`                | `TransactionController.delete` | Required outside local | working — soft delete; requires `If-Match`.                                                                                                                  |
| GET    | `/api/v1/books/{bookId}/budgets?month=YYYY-MM`              | `BudgetController.list`        | Required outside local | working — extra feature, lists monthly budgets and spent/remaining values.                                                                                   |
| PUT    | `/api/v1/books/{bookId}/budgets/{categoryId}?month=YYYY-MM` | `BudgetController.upsert`      | Required outside local | working — extra feature; expense-category budgets only.                                                                                                      |
| DELETE | `/api/v1/books/{bookId}/budgets/{categoryId}?month=YYYY-MM` | `BudgetController.delete`      | Required outside local | working — soft delete; optional `If-Match`.                                                                                                                  |
| GET    | `/api/v1/books/{bookId}/balance`                            | `SummaryController.balance`    | Required outside local | working — opening balance + all income - all expense.                                                                                                        |
| GET    | `/api/v1/books/{bookId}/summary/monthly?month=YYYY-MM`      | `SummaryController.monthly`    | Required outside local | working — monthly income/expense and expense category breakdown.                                                                                             |
| POST   | `/api/v1/books/{bookId}/exports/csv`                        | `ExportController.createCsv`   | Required outside local | stubbed — only creates a `PENDING` job; no CSV worker exists.                                                                                                |
| GET    | `/api/v1/books/{bookId}/exports/{exportId}`                 | `ExportController.get`         | Required outside local | stubbed — exposes the job, which never advances without external/manual DB changes.                                                                          |
| GET    | `/api/v1/books/{bookId}/exports/{exportId}/download`        | `ExportController.download`    | Required outside local | stubbed — `ExportPresignService` returns an `example.com` URL and no storage integration exists.                                                             |
| GET    | `/api/actuator/health/**`, `/api/actuator/info`             | Spring Actuator                | No                     | working framework endpoints; health is public.                                                                                                               |
| GET    | `/api/actuator/metrics`                                     | Spring Actuator                | Required outside local | working framework endpoint; exposed by configuration, not tested end-to-end.                                                                                 |
| GET    | `/api/actuator/prometheus`                                  | Spring Actuator                | Required outside local | broken — configuration exposes it but `micrometer-registry-prometheus` is not a dependency, so no Prometheus endpoint is registered.                         |
| GET    | `/api/v3/api-docs/**`                                       | Springdoc                      | No                     | working framework endpoint by configuration; enabled by default in every environment.                                                                        |
| GET    | `/api/swagger-ui/**`                                        | Springdoc                      | No                     | broken — the configured UI URL is `/api/openapi/pennywise-v1.yaml`, but `src/main/resources/openapi/` has no resource handler/static-location configuration. |

## 4. Data Model

Migrations exist: `src/main/resources/db/migration/V1__init.sql` through
`V4__mobile_contract_auth_and_budgets.sql`. Flyway creates and migrates schema `expense_tracker`;
Hibernate is validation-only (`spring.jpa.hibernate.ddl-auto=validate`), so it will not generate or
alter tables.

<!-- markdownlint-disable MD013 -->

| Table / entity                              | Fields and PostgreSQL types                                                                                                                                                                                                                                                                                                                     | Relationships                                                                                                                    |
| ------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| `users` / `UserEntity`                      | `id UUID`; `auth_subject TEXT UNIQUE NOT NULL`; `email TEXT NULL`; `password_hash TEXT NULL`; `default_currency_code VARCHAR(3) NULL`; inherited `created_at TIMESTAMPTZ`, `updated_at TIMESTAMPTZ`, `deleted_at TIMESTAMPTZ`, `version BIGINT`.                                                                                                | Referenced by `books.owner_user_id`, `export_jobs.requested_by_user_id`, and `idempotency_keys.user_id`.                         |
| `books` / `BookEntity`                      | `id UUID`; `owner_user_id UUID NOT NULL`; `name TEXT`; `currency_code VARCHAR(3)`; `timezone TEXT`; `opening_balance_minor BIGINT`; inherited audit/version fields.                                                                                                                                                                             | Many books to one owner user; referenced by categories, transactions, budgets, and export jobs.                                  |
| `categories` / `CategoryEntity`             | `id UUID`; `book_id UUID`; `type TEXT CHECK (INCOME, EXPENSE)`; `name TEXT`; `is_disabled BOOLEAN`; `icon TEXT NULL`; `color VARCHAR(7) NULL` with hex-color check; inherited audit/version fields.                                                                                                                                             | Many categories to one book; referenced by transactions and budgets. Active name uniqueness is `(book_id, type, lower(name))`.   |
| `transactions` / `TransactionEntity`        | `id UUID`; `book_id UUID`; `type TEXT CHECK (INCOME, EXPENSE)`; `amount_minor BIGINT CHECK (>0)`; `occurred_on DATE`; `occurred_at TIMESTAMPTZ NULL`; `category_id UUID`; `title TEXT NULL` (max-120 check); `payment_method TEXT NULL` (`CASH`, `CARD`, `BANK_TRANSFER`, `WALLET`, `OTHER`); `note TEXT NULL`; inherited audit/version fields. | Many transactions to one book and one category. “Expense” is modelled as this transaction table, not a separate `ExpenseEntity`. |
| `budgets` / `BudgetEntity`                  | `id UUID`; `book_id UUID`; `category_id UUID`; `month_start DATE`; `amount_minor BIGINT CHECK (>=0)`; inherited audit/version fields.                                                                                                                                                                                                           | Many budgets to one book and one category. Active uniqueness is `(book_id, category_id, month_start)`.                           |
| `export_jobs` / `ExportJobEntity`           | `id UUID`; `book_id UUID`; `requested_by_user_id UUID`; `status TEXT CHECK (PENDING, RUNNING, COMPLETED, FAILED)`; `file_name TEXT NULL`; `storage_key TEXT NULL`; `error_message TEXT NULL`; inherited audit/version fields.                                                                                                                   | Many jobs to one book and requesting user. No producer changes status or creates a file.                                         |
| `idempotency_keys` / `IdempotencyKeyEntity` | Composite primary key `user_id UUID`, `idem_key TEXT`; `request_hash TEXT`; `response_code INT NULL`; `response_body TEXT NULL`; `created_at TIMESTAMPTZ`.                                                                                                                                                                                      | Per-user request-response cache. Its `user_id` references `users`.                                                               |

<!-- markdownlint-enable MD013 -->

All supplied DDL is PostgreSQL-specific and PostgreSQL-compatible: `pgcrypto`, `gen_random_uuid()`,
`TIMESTAMPTZ`, partial indices, and PostgreSQL regular-expression syntax are intentional. It will
not run on H2/MySQL without replacement. Fresh database validation against a live PostgreSQL
instance is **UNKNOWN — needs manual check**: the sole Testcontainers context test was skipped on
this machine, so Flyway plus `ddl-auto=validate` has not been exercised here.

## 5. Authentication

Authentication is implemented, not a demo stub. `SecurityConfig` configures stateless Spring
Security with a JWT resource server; `AuthService` signs HS256 tokens and `PasswordEncoder` is
`BCryptPasswordEncoder`. `POST /v1/auth/signup` stores a BCrypt `passwordHash`;
`POST /v1/auth/login` verifies it. Tokens include issuer, expiration, subject
(`local:<normalized-email>`), email, and `user_id` claims.

The dangerous default is local mode: `ENV` defaults to `local`, which causes
`SecurityConfig.isAuthEnabled()` to permit every request. In that mode every unauthenticated request
falls back to subject `local` in controllers such as `TransactionController`, so all callers share
one lazily created user. Do not deploy with this default.

With authentication enabled, isolation is implemented through `BookService.requireOwned`: every
nested category, transaction, budget, summary, and export route first queries the book by both book
ID and authenticated user's database ID. A user cannot read or modify another user's data through
these routes; ownership failures become `404`. This has unit coverage only, not a real
JWT-plus-PostgreSQL integration test.

`APP_JWT_ISSUER_URI` enables external issuer validation in the decoder, but the built-in
signup/login service still signs local HS256 tokens. Therefore external-issuer mode and this
backend-owned email/password flow are incompatible unless additional auth work is done. For the
target state, use the backend-owned mode: leave issuer URI unset and set a strong
`APP_JWT_LOCAL_SECRET`.

There are no refresh tokens, logout/revocation, password-reset, email-verification, throttling, or
account-recovery flows.

## 6. Configuration & Secrets

`src/main/resources/application.properties` defines the following environment-backed settings:

| Variable                                                      | Default / purpose                                                                           | Required for a safe deployment?                                                       |
| ------------------------------------------------------------- | ------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------- |
| `ENV`                                                         | `local`; controls default security behaviour.                                               | Yes — set a non-`local` value, e.g. `production`.                                     |
| `JDBC_URL`                                                    | Local PostgreSQL `jdbc:postgresql://localhost:5432/postgres?currentSchema=expense_tracker`. | Yes — replace with the Supabase/Postgres JDBC URL, including required SSL parameters. |
| `DB_USERNAME` / `DB_PASSWORD`                                 | Both default to `postgres`.                                                                 | Yes — set database credentials from the provider.                                     |
| `APP_SECURITY_AUTH_ENABLED`                                   | Blank; otherwise overrides the `ENV`-derived setting.                                       | Set to `true` defensively.                                                            |
| `APP_JWT_LOCAL_SECRET`                                        | Hardcoded `pennywise-local-development-secret-change-me`.                                   | Yes — supply a long random secret; never use the default.                             |
| `APP_JWT_ISSUER`                                              | `pennywise`.                                                                                | Recommended — set a stable production issuer.                                         |
| `APP_JWT_AUDIENCE`                                            | Blank; validates `aud` only when supplied.                                                  | Optional, but recommended for a mobile API.                                           |
| `APP_JWT_ACCESS_TOKEN_TTL_MINUTES`                            | `60`.                                                                                       | Optional.                                                                             |
| `APP_JWT_ISSUER_URI`                                          | Blank; switches decoder to an external issuer.                                              | Leave blank for the built-in signup/login flow.                                       |
| `SPRINGDOC_API_DOCS_ENABLED` / `SPRINGDOC_SWAGGER_UI_ENABLED` | Both `true`.                                                                                | Optional; disable in production if public docs are not wanted.                        |
| `APP_EXPORT_BUCKET`                                           | Blank.                                                                                      | Not required; it is not used by any export implementation.                            |

`LOG_FILE`, `LOG_PATH`, and `LOG_TEMP` are optional Logback substitutions. The app also uses fixed
`app.db.schema=expense_tracker`, enables Flyway, exposes health/info/metrics and configures (but
cannot provide) Prometheus, sets a Hikari maximum pool of 10 and minimum idle of 2, and writes a
rolling file log under `/tmp` by default.

Hardcoded credentials/secrets exist in committed configuration: the local PostgreSQL
`postgres`/`postgres` defaults and the JWT development secret above. Test code also embeds test
secrets and a Testcontainers password. No real production secret file was found.

## 7. Testing

`./gradlew test` passed on 2026-07-25: 72 tests discovered, 71 passed, 0 failed, and 1 skipped.
`./gradlew bootJar` also passed.

Coverage is mostly unit/standalone MockMvc tests: books, categories, transactions, summaries,
exports, budgets, idempotency, category seeding, authentication, and security error handling.
`AuthServiceTest` uses a real BCrypt encoder and a real in-memory HS256 encoder, but mocks
`UserRepository`. Controller tests generally use mocked services/repositories and standalone
MockMvc; they do not run the production filter chain or a database.

`ApplicationTests.contextLoads` is the only `@SpringBootTest` and PostgreSQL Testcontainers test. It
is annotated `@Testcontainers(disabledWithoutDocker = true)` and was skipped because Docker was
unavailable. There are no live repository-query, Flyway migration, PostgreSQL schema-validation,
endpoint-with-real-JWT, or end-to-end tests. This means a green test result does not prove the
service starts against Supabase.

The only CI workflow, `.github/workflows/super-linter.yml`, runs Super-Linter on pull requests
targeting `main` or `develop`; it does not run Gradle build or tests. PR #5's latest workflow run
failed: `GOOGLE_JAVA_FORMAT`, `JAVA`, and `SQLFLUFF` reported errors; Gitleaks, merge-conflict
markers, and XML passed. PR #4 has no workflow run because its base branch is `feature/update_apis`,
which is outside the workflow trigger.

## 8. Build & Run

Prerequisites: a Java 21 runtime/toolchain and a reachable PostgreSQL database. The wrapper
currently pins `org.gradle.java.home` to
`/Users/mak/Library/Java/JavaVirtualMachines/corretto-21.0.9/Contents/Home`; remove or replace that
setting on any other machine.

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

`bootJar` currently produces `build/libs/pennywise-0.0.1-SNAPSHOT.jar` (63 MB) with `JarLauncher`
and start class `com.axel.pennywise.Application`; the command passed during this audit. The default
listener is port 8080 and the API context is `/api`. No `PORT` environment variable is read.

The tests and JAR packaging pass, but a live application boot against PostgreSQL was not verified in
this audit. Without the exported variables, the default configuration attempts `localhost:5432` as
`postgres/postgres`; if no such database is running, startup will fail during datasource/Flyway
initialization. Actual Supabase startup is **UNKNOWN — needs manual check**.

## 9. In-Flight Work

- PR #4, `Add mobile auth, budgets, and ledger metadata` (`codex/review-backend-api-plan` ->
  `feature/update_apis`, 3 commits relative to that target, head `a75f923`): this is the current
  checkout. Its effective changes include backend-owned email/password JWT auth, user currency data,
  budgets, title/payment/timestamp transaction metadata, category metadata, request/error security
  handling, and related tests/migration `V4__mobile_contract_auth_and_budgets.sql`. Its common
  ancestor with current `develop` is `e5c335b Add mobile auth, budgets, and ledger metadata`; its
  two branch-only commits are `2526138 Handle transaction reload and security errors` and
  `a75f923 Remove hardcoded server port`. Meanwhile, `develop` has the separate `58fa814`
  category/transaction refactor, which this checkout does not contain. Rebase/retarget before
  merging; its base branch does not trigger the CI workflow.
- PR #5, `Add soft-delete and profile currency endpoints` (`codex/add-profile-and-book-delete` ->
  `develop`, 1 commit, head `f515bb0`): adds DELETE book/category endpoints that refuse deletion
  when active transactions exist, `PATCH /v1/me` to update default currency, additional category
  icon/color changes, and tests. It carries a different
  `V4__add_user_currency_and_category_metadata.sql` while the current code already has a different
  V4 migration; inspect and reconcile the migration and overlapping edits before merging. Its only
  CI run is currently failing lint, as described in Testing.

Recent `develop` commits are `58fa814` (2026-04-26, category/transaction refactor), `e5c335b`
(2026-04-26, mobile auth/budgets/ledger metadata), and `802420a` (2026-01-26, merged Super-Linter
PR). No deployment configuration arrived in those commits.

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
- [x] Create expense endpoint — **Done**: transaction POST persists an owned-book entry; it should
      still reject category/type mismatches.
- [x] List expenses endpoint — **Done**: transaction GET lists active entries in an owned book.
- [x] Update expense endpoint — **Done**: transaction PATCH is ETag-protected.
- [x] Delete expense endpoint — **Done**: transaction DELETE soft-deletes and is ETag-protected.
- [x] Filter by date range (day / 7 days / month) — **Done**: generic `from`/`to` dates let the
      mobile client request any of those windows; there are no shortcut parameters. Malformed dates
      need a proper 400 response.
- [x] Filter by category — **Done**: `categoryId` filters transaction GET.
- [~] Summary/aggregate endpoint (totals, per-category, balance) — **Partial**: balance is all-time;
  monthly summary exposes monthly income, expense, and expense-category totals. There is no
  generic/all-time “total spent” aggregate endpoint or day/last-seven-day aggregate endpoint.
- [x] User-scoped data isolation (expenses filtered by authenticated user) — **Done when production
      auth is enabled**: book ownership scopes nested data. Local mode disables auth and collapses
      callers into one `local` user.
- [~] Input validation on all write endpoints — **Partial**: DTO validation and service checks cover
  many fields, but book timezone/currency semantics are not validated, empty PATCH bodies are
  accepted, and transaction category type is not required to match INCOME/EXPENSE.
- [~] Consistent error response format — **Partial**: `GlobalExceptionHandler` and security handlers
  return `ErrorResponse`, but missing request parameters/type mismatches and malformed date strings
  are not uniformly mapped (invalid `from`/`to` becomes a generic 500).
- [ ] CORS configured for the mobile client — **Missing**: no `CorsConfiguration`, `@CrossOrigin`,
      or `http.cors` configuration exists.
- [~] Pagination on list endpoints — **Partial**: transactions have cursor pagination (1–200,
  default 50); books, categories, and budgets return unbounded lists.

## 11. Free-Tier Deployment Readiness

The configured database is PostgreSQL and is appropriate for Supabase or a comparable free-tier
Postgres service. Migrations are PostgreSQL-only by design, so no database-engine migration is
needed. The app expects it can create/use schema `expense_tracker`; verify that the deployed
database role has the required schema privileges.

There is no Dockerfile, Procfile, Compose file, `render.yaml`, `railway.json`, or other deployment
configuration. `./gradlew bootJar` does produce a runnable Spring Boot JAR, but a live database boot
has not been verified. The 63 MB JAR bundles a full Spring Boot/Tomcat/JPA/Security/Flyway stack. It
may fit a 512 MB service at this traffic level, but it is not comfortably tiny: reduce Hikari from
`maximum-pool-size=10` / `minimum-idle=2` to roughly 2–3 / 0–1 and set an appropriate JVM heap cap
for the host before relying on a 512 MB tier. The rolling file logger also targets ephemeral `/tmp`
and has a 5 GB retention cap, which is unsuitable as production log storage.

Before deployment, make these changes/configurations:

1. Remove the developer-specific `org.gradle.java.home` from `gradle.properties`; build with the
   host's JDK 21.
2. Add a Dockerfile or configure the host explicitly to build with `./gradlew bootJar` and start
   `java -jar build/libs/pennywise-0.0.1-SNAPSHOT.jar`.
3. Add `server.port=${PORT:8080}` (or pass `--server.port=$PORT` in the host start command). The
   code currently ignores the port supplied by most free hosts.
4. Configure `JDBC_URL`, `DB_USERNAME`, and `DB_PASSWORD` with the Supabase connection data and
   required SSL mode; keep `currentSchema=expense_tracker` or explicitly set the equivalent schema
   configuration.
5. Set `ENV=production`, `APP_SECURITY_AUTH_ENABLED=true`, and a unique strong
   `APP_JWT_LOCAL_SECRET`; do not deploy the checked-in local secret or local DB defaults. Leave
   `APP_JWT_ISSUER_URI` blank while using this app's signup/login.
6. Add restrictive CORS configuration for the actual mobile/web origins. Native mobile clients may
   not need browser CORS, but no browser/mobile-web client can rely on it today.
7. Run Flyway plus Hibernate validation against a fresh Supabase project and add a non-skipped
   PostgreSQL integration test. This is required because the current live context/migration test was
   skipped.
8. Configure the host health check to `/api/actuator/health` (or `/api/health`); do not use
   `/health` without the context path.
9. Decide whether public Swagger/OpenAPI and unauthenticated health/info are acceptable; otherwise
   set the Springdoc flags to `false` and tighten security.
10. Keep all durable data in Postgres. The export feature is incomplete and no free object-storage
    integration is configured; leave it disabled or implement it separately if exports are required.
