# Contact support deployment

The authenticated `POST /v1/support/contact` endpoint sends a plain-text message through Resend's
HTTPS API. The mobile app sends only a subject, message, and stable `Idempotency-Key`; the backend
obtains the sender's email from their account. A `204` means Resend accepted the request, not that
delivery has been confirmed. The email uses the account address as Reply-To.

Set these variables on the **backend Render service** before publishing the mobile change:

| Variable | Value |
| --- | --- |
| `RESEND_API_KEY` | A send-only API key restricted to the verified sender domain |
| `SUPPORT_FROM_EMAIL` | An address on that verified domain (for example `support@your-domain`) |
| `SUPPORT_TO_EMAIL` | The private support inbox address |

Do not add the recipient or provider key to EAS, Expo public variables, mobile source, or the
checked-in Render blueprint. A missing variable returns `503 SUPPORT_NOT_CONFIGURED`. Provider
rejection or network failure returns `502 SUPPORT_SEND_FAILED`. Verify the sender domain in Resend
and test one authenticated message after the backend deploy. Check both inbox receipt and Reply-To.

The endpoint requires a JWT and an account email. Subject and message are required after trimming,
with limits of 120 and 5000 characters. The backend uses Postgres-backed support token buckets
for each user, each client IP, and the entire app. Limiter database failure stops the send. Provider
idempotency deduplicates retries with the same key for 24 hours. The app keeps the draft and key
after a failed send and generates a new key when either field changes.

Deploy the backend and set its variables first; then publish the mobile update. This mobile change
uses existing JavaScript and Expo components, so an OTA update is enough for an installed build on
the same runtime version. No new native module or fresh APK is needed for this contact form.
