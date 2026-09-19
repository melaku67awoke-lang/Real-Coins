# RealCoin v30 admin-verification password recovery backend

This Worker provides the password-recovery approval service for the Android app.

## Flow

1. The user enters the registered email in **Forgot Password**.
2. `POST /v1/password-recovery/request` creates a `PENDING` request and returns a request ID.
3. An administrator reviews the request in Cloudflare D1 and changes the status to `APPROVED` or `REJECTED`.
4. The Android app calls `POST /v1/password-recovery/status` and, only after `APPROVED`, allows the user to enter a new password.
5. The app updates the local Room password and calls `POST /v1/password-recovery/complete`.

There is **no email OTP** in this flow. The administrator never receives, sees, or stores the user's new password.

## Endpoints

- `GET /health`
- `POST /v1/password-recovery/request`
- `POST /v1/password-recovery/status`
- `POST /v1/password-recovery/complete`

## D1

`0001_password_reset.sql` is retained from the previous implementation. `0002_password_recovery_requests.sql` is also retained for migration history. The active admin-verification table for the current Android Room architecture is created by `0003_password_recovery_requests_v2.sql`.

The current Android application stores account credentials locally in Room, so the active recovery request table intentionally stores the normalized email without requiring a server-side `auth_accounts` row.

## Security model

- Email addresses are normalized before storage.
- Recovery requests are rate-limited by a server-side HMAC of the normalized email.
- Request IDs are cryptographically random UUIDs.
- Recovery status is read from D1 rather than trusted from the Android client.
- Before changing the local password, the app re-checks the request status and the approved email.
- The Worker never receives the new password.
- Responses are marked `no-store`.

The server-side approval table does not provide an admin HTTP endpoint. Administrators change request status directly in the protected Cloudflare D1 console. This avoids putting an administrator secret inside the Android APK.
