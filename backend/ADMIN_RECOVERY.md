# RealCoins admin password recovery

The Android app uses local Room accounts. Password recovery therefore uses the Cloudflare Worker as an approval service:

1. The user submits their registered email in **Forgot Password**.
2. The Worker creates a `PENDING` request and returns a request ID.
3. An administrator reviews the request in the Cloudflare D1 database and changes only the status to `APPROVED` or `REJECTED`. The administrator never receives or stores the user's new password.
4. The user taps **Check Admin Approval**. After `APPROVED`, the app lets the user create a new local password.
5. The app verifies the request is still `APPROVED`, changes the local Room password, then marks the request `COMPLETED`.

## Approve a request

In Cloudflare D1, use:

```sql
UPDATE password_recovery_requests_v2
SET status = 'APPROVED', reviewed_at_ms = <CURRENT_TIME_MS>
WHERE id = '<REQUEST_ID>' AND status = 'PENDING';
```

Replace `<CURRENT_TIME_MS>` with the current Unix time in milliseconds and `<REQUEST_ID>` with the request ID shown to the user.

## Reject a request

```sql
UPDATE password_recovery_requests_v2
SET status = 'REJECTED', reviewed_at_ms = <CURRENT_TIME_MS>
WHERE id = '<REQUEST_ID>' AND status = 'PENDING';
```

The admin must never ask the user for, enter, or store the new password.
