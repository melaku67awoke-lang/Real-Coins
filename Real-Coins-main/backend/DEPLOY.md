# RealCoin v30 deployment

The current password recovery flow uses Cloudflare Workers + D1 for **admin verification**. It does not use email OTP.

## 1. Cloudflare D1

The repository already contains the D1 binding in `wrangler.toml`. From `backend`:

```bash
npx wrangler d1 migrations apply realcoin-password-reset --remote
```

Apply all pending migrations, including `0003_password_recovery_requests_v2.sql`.

## 2. Worker secret

The current Worker only needs the rate-limit secret:

```bash
npx wrangler secret put RATE_LIMIT_SECRET
```

Never commit the secret value.

The old Resend/OTP/signing secrets may still exist in an already configured Cloudflare Worker, but the current code does not use them for password recovery.

## 3. Deploy

From `backend`:

```bash
npx wrangler deploy
```

Then verify:

```bash
curl https://YOUR_WORKER_URL/health
```

The Android project is configured for the production Worker URL used by this project.

## 4. Admin recovery review

See `ADMIN_RECOVERY.md` for the exact D1 SQL used to approve or reject a recovery request.
