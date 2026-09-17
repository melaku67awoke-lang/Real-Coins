# RealCoin v30 password-reset backend

Step 19 replaces the previous Google Cloud Run + Firestore + Gmail SMTP backend with:

- Cloudflare Workers for the HTTPS API.
- Cloudflare D1 for reset sessions and rate-limit state.
- Resend for transactional OTP email delivery.

The Android API contract is intentionally unchanged:

- `POST /v1/password-reset/request`
- `POST /v1/password-reset/verify`
- `POST /v1/password-reset/consume`
- `GET /health`

No Gmail password, Resend API key, OTP secret, or signing private key belongs in the Android APK.

## Security model

- OTPs are 6-digit cryptographically random values.
- OTP hashes use HMAC-SHA-256 with a server-only secret and per-session random salt.
- OTP attempts are atomically claimed in D1 and limited to 5.
- Request rate limiting is keyed by a server-side HMAC of normalized email.
- Reset authorizations are short-lived Ed25519-signed tokens.
- The reset token binds the verified session to the normalized email.
- Consume is one-time and uses an atomic D1 update.
- The backend never receives or stores the user's new password.
- Responses are `no-store` and CORS is limited to headers needed by generic clients; native Android does not depend on CORS.

## Secrets

Configure Worker secrets with Wrangler. Required secret names are declared in `wrangler.toml`.

Generate a new Ed25519 key pair for this backend rather than reusing a development/private key. Store only the private key on Cloudflare. The public key is safe to distribute if a future Android-side signature verification layer needs it.

Resend requires an API key and a verified sending domain for production recipients. Do not put the Resend API key in the Android app.

## D1

Create the D1 database, replace `REPLACE_WITH_D1_DATABASE_ID` in `wrangler.toml`, then apply `migrations/0001_password_reset.sql` through Wrangler.

Do not run production migrations against a local database by accident; use Wrangler's remote migration mode when the production database exists.

## Free-tier note

Cloudflare currently lists Workers Free at 100,000 requests/day and D1 Free at 5 million rows read/day, 100,000 rows written/day, and 5 GB total storage. Cloudflare began enforcing D1 free-tier daily read/write limits on September 1, 2026. If a limit is reached, D1 requests fail until the daily reset unless the account is upgraded.
