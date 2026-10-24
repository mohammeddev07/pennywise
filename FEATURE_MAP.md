# FEATURE MAP

Index of domain packages, Spring Security endpoints, database entities, and test commands.

## Display Name

- **Packages**: `com.axel.pennywise.domain.user`, `com.axel.pennywise.api.controller`, `com.axel.pennywise.api.dto.user`.
- **Primary classes**: `UserController`, `UserService`, `UserEntity`, `MeUpdateRequest`, `MeResponse`.
- **Endpoints**: `GET /api/v1/me` returns `displayName`; `PATCH /api/v1/me` sets or clears it while preserving omitted fields.
- **Database**: `users.display_name`, added by `src/main/resources/db/migration/V11__users_display_name.sql`.
- **Verification**: `./gradlew test --tests "com.axel.pennywise.domain.user.UserServiceDisplayNameTest" --tests "com.axel.pennywise.api.controller.UserControllerTest"`.

## Backend Capacity Protection

- **Packages**: `com.axel.pennywise.config`, `com.axel.pennywise.security.admission`, `com.axel.pennywise.security.ratelimit`, `com.axel.pennywise.exception`.
- **Primary classes**: `CapacityFilterConfig`, `AdmissionFilter`, `LoginRateLimitFilter`, `GlobalExceptionHandler`.
- **Behavior**: Admission runs before authentication and database work. Excess in-flight requests receive `503`; excess password logins receive `429`. Both include `Retry-After`. Database acquisition failures receive `503` with `Retry-After`.
- **Configuration**: `src/main/resources/application.properties` contains the Hikari acquisition timeout, Tomcat worker cap and accept backlog, and login limit. Admission defaults are in `CapacityFilterConfig`.
- **Database**: No schema change; the filters protect the existing connection pool and login path.
- **Verification**: `./gradlew test --tests "com.axel.pennywise.config.CapacityFilterConfigTest" --tests "com.axel.pennywise.security.admission.*" --tests "com.axel.pennywise.security.ratelimit.LoginRateLimitFilterTest" --tests "com.axel.pennywise.exception.GlobalExceptionHandlerTest"`.

## AI Filter Proposals

- **Package**: `com.axel.pennywise.domain.transaction.query.ai`.
- **Primary classes**: `AiFilterController`, `FilterProposalService`, `GeminiFilterClient`.
- **Endpoint**: `POST /api/v1/books/{bookId}/filter-proposals`.
- **Prompt**: `src/main/resources/prompts/filter-proposal-system-instruction.md`.
- **Entities / tables**: Read-only queries over `transactions`; `ai_filter_quota` tracks quota.
- **Verification**: `./gradlew test --tests "com.axel.pennywise.domain.transaction.query.ai.*"`.
