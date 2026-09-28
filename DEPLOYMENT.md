# Pennywise deployment

Pennywise runs as a Docker container on Java 21 and stores its data in PostgreSQL. The
application serves every endpoint under the `/api` context path.

## Environment variables

| Variable                           | Required in production | Default                                                                   | Description                                                                                                                                                                                                |
| ---------------------------------- | ---------------------- | ------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ENV`                              | Yes                    | `local`                                                                   | Runtime environment. Use `production` on Render or another host. Authentication defaults to disabled only when this is `local`; it defaults to enabled for every other value.                              |
| `PORT`                             | No                     | `8080`                                                                    | HTTP port. Render and similar platforms inject this value.                                                                                                                                                 |
| `JDBC_URL`                         | Yes                    | `jdbc:postgresql://localhost:5432/postgres?currentSchema=expense_tracker` | PostgreSQL JDBC URL. For Supabase, use the JDBC form of its connection string, retain its required SSL options, and set `currentSchema=expense_tracker`.                                                   |
| `DB_USERNAME`                      | Yes                    | `postgres`                                                                | PostgreSQL user.                                                                                                                                                                                           |
| `DB_PASSWORD`                      | Yes                    | `postgres`                                                                | PostgreSQL password.                                                                                                                                                                                       |
| `DB_POOL_MAX`                      | No                     | `3`                                                                       | Maximum Hikari connection-pool size.                                                                                                                                                                       |
| `DB_POOL_MIN`                      | No                     | `0`                                                                       | Minimum number of idle Hikari connections.                                                                                                                                                                 |
| `APP_SECURITY_AUTH_ENABLED`        | No                     | blank/automatic                                                           | Optional explicit authentication switch; only `true`, `false`, or blank are accepted. Blank means disabled for `ENV=local` and enabled otherwise. Do not set this to `false` in production.                |
| `APP_JWT_LOCAL_SECRET`             | Yes                    | local development value                                                   | Secret used to sign and verify the service's HS256 access tokens. Production startup rejects the committed local-development value. Supply at least 32 random bytes through the host's secret manager.     |
| `APP_JWT_ISSUER`                   | No                     | `pennywise`                                                               | Issuer written to and required on locally issued access tokens.                                                                                                                                            |
| `APP_JWT_ACCESS_TOKEN_TTL_MINUTES` | No                     | `60`                                                                      | Access-token lifetime in minutes.                                                                                                                                                                          |
| `APP_JWT_AUDIENCE`                 | No                     | blank                                                                     | Optional required JWT audience. When set, locally issued tokens include this audience and the API validates it.                                                                                            |
| `APP_JWT_ISSUER_URI`               | No; leave unset        | blank                                                                     | External OAuth issuer discovery URL. **Leave this unset for Pennywise signup/login.** Setting it switches token decoding to the external issuer.                                                           |
| `GOOGLE_CLIENT_IDS`                | No                     | blank                                                                     | Comma-separated Google OAuth client IDs (web, plus iOS if used) whose ID tokens `/v1/auth/google` accepts. Blank disables Google sign-in (the endpoints return 503). See the Google Sign-In section below. |
| `SPRINGDOC_API_DOCS_ENABLED`       | No                     | `true`                                                                    | Enables generated OpenAPI JSON. Set to `false` in production if it is not needed.                                                                                                                          |
| `SPRINGDOC_SWAGGER_UI_ENABLED`     | No                     | `true`                                                                    | Enables Swagger UI. Set to `false` in production if it is not needed.                                                                                                                                      |
| `APP_EXPORT_BUCKET`                | No                     | blank                                                                     | Reserved export storage bucket. CSV export remains disabled for v1.                                                                                                                                        |

Real database credentials and JWT secrets must be host environment variables. Do not put
them in this repository or bake them into an image. Keep `APP_JWT_ISSUER_URI` blank when
using the built-in `/v1/auth/signup` and `/v1/auth/login` endpoints.

Flyway creates the `expense_tracker` schema and applies the immutable V1 through V4
migrations. Hibernate then validates the entity mappings rather than altering the schema.
There is intentionally no V5: the proposed second V4 contained no statements that were not
already in V4, so it was removed before any deployment.

## Google Sign-In

Users can log in with their own Google account in addition to email and password. The mobile
app gets a Google ID token from the native Google Sign-In SDK and sends it to
`POST /api/v1/auth/google`; the backend verifies it and returns the normal Pennywise bearer
token. No Google client secret is stored on the server, only the public client IDs.

### One-time setup

1. Open the [Google Cloud console](https://console.cloud.google.com), and create a project
   (or pick an existing one) for Pennywise.
2. Go to **Google Auth Platform** (older console: **APIs & Services**, **OAuth consent
   screen**). Set the app name and support email, choose **External**, and keep the default
   scopes (`openid`, `email`, `profile`). While the publishing status is **Testing**, only
   accounts listed as **Test users** can sign in. Click **Publish app** so anyone can. These
   basic scopes need no Google verification review.
3. Go to **Clients** (older console: **Credentials**, **Create credentials**, **OAuth client
   ID**) and create:
   - **Web application** client. Its client ID is the one the mobile SDK is configured with
     as `webClientId`, which makes it the audience of the ID token. No redirect URI is needed.
   - **Android** client: the app's package name and the SHA-1 fingerprint of the certificate
     that signs the build. The debug keystore, the EAS build credentials, and the Play App
     Signing certificate each have a different SHA-1, so register every one you use.
   - **iOS** client, if you ship on iOS: the app's bundle ID.
4. In the Render dashboard, open the service, **Environment**, and add
   `GOOGLE_CLIENT_IDS` set to the web client ID, plus the iOS client ID if used, separated by a
   comma (no secrets are involved). Save; Render redeploys.
5. Check it: `curl -X POST https://<your-service>/api/v1/auth/google -H 'Content-Type:
application/json' -d '{"idToken":"x"}'`. A `401` with code `GOOGLE_TOKEN_INVALID` means the
   server is configured. A `503` with `GOOGLE_SIGN_IN_NOT_CONFIGURED` means
   `GOOGLE_CLIENT_IDS` is missing or blank.
6. In the mobile repository (not part of this change), add the native Google Sign-In
   library, configure it with the web client ID, and send the returned `idToken` to
   `/v1/auth/google`.

### Account rules

- A new Google user gets a new account with no password.
- If the Google email already belongs to an email and password account, Google login is
  refused with `409 GOOGLE_ACCOUNT_LINK_REQUIRED` and nothing is merged automatically, because
  signup does not verify email ownership. The owner logs in with their password and calls
  `POST /api/v1/auth/google/link` with a Google ID token to attach Google to that account.
- After linking, either login method reaches the same account and data.
- Signup rejects an email that any account already uses, including a Google account.

## Local Docker run

Build the application image:

```bash
docker build -t pennywise:local .
```

Start PostgreSQL and the API on an isolated Docker network:

```bash
docker network create pennywise-local

docker run --detach --rm \
  --name pennywise-postgres \
  --network pennywise-local \
  --env POSTGRES_DB=postgres \
  --env POSTGRES_USER=postgres \
  --env POSTGRES_PASSWORD=postgres \
  postgres:16-alpine

until docker exec pennywise-postgres pg_isready --username postgres --dbname postgres; do
  sleep 1
done

export PENNYWISE_LOCAL_JWT_SECRET="$(openssl rand -hex 32)"

docker run --rm \
  --name pennywise-api \
  --network pennywise-local \
  --publish 8080:8080 \
  --env PORT=8080 \
  --env ENV=production \
  --env APP_SECURITY_AUTH_ENABLED=true \
  --env APP_JWT_LOCAL_SECRET="${PENNYWISE_LOCAL_JWT_SECRET}" \
  --env APP_JWT_ISSUER=pennywise \
  --env 'JDBC_URL=jdbc:postgresql://pennywise-postgres:5432/postgres?currentSchema=expense_tracker' \
  --env DB_USERNAME=postgres \
  --env DB_PASSWORD=postgres \
  pennywise:local
```

The health-check path is:

```text
/api/actuator/health
```

For example:

```bash
curl --fail http://localhost:8080/api/actuator/health
```

## API contract — verified shapes

### ETag and If-Match

Versioned book, category, transaction, and budget responses emit a strong quoted ETag.
Version `3` is emitted exactly as:

```http
ETag: "3"
```

The backend trims the `If-Match` value, removes one surrounding pair of quotes, parses the
remaining decimal number, and compares it with the entity's numeric version. The mobile
header `If-Match: "<version>"` therefore matches. An unquoted number is also accepted;
`W/"3"` is rejected as an invalid value.

### Transaction list envelope

The transaction list serializes with the required nested `page` property:

```json
{
  "page": {
    "items": [],
    "nextCursor": null
  }
}
```

`nextCursor` is a string when another page exists.

### Budget response

`BudgetResponse` contains the required fields:

```json
{
  "id": "uuid",
  "bookId": "uuid",
  "categoryId": "uuid",
  "categoryName": "Food",
  "month": "2026-07",
  "amountMinor": 50000,
  "spentMinor": 12000,
  "remainingMinor": 38000,
  "currencyCode": "USD",
  "version": 0
}
```

`month` is explicitly a `String` produced from `YearMonth`, so it is `YYYY-MM`, not a
`LocalDate`. The actual response also contains `createdAt` and `updatedAt`.

### Signup request

`POST /api/v1/auth/signup` accepts optional `defaultCurrencyCode` alongside `email` and
`password`. The service normalizes a supplied three-letter value to uppercase and persists
it on the new user.

### Idempotency

`POST /api/v1/books/{bookId}/transactions` requires `Idempotency-Key`. The persisted key is
`(user_id, idem_key)`, with `request_hash` stored and checked on that row; it is not a
three-column primary key. Replaying the same key and body returns the stored response.
Reusing the key with a different body returns HTTP `409` with code
`IDEMPOTENCY_KEY_REUSED`, message `Idempotency-Key reused with a different request`, and the
key in the standard error details.
