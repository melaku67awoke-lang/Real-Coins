# Step 19 deployment setup

This document prepares the project for Cloudflare Workers + D1 + Resend. It does not deploy anything and it does not contain secrets.

## 1. Cloudflare

Create a Cloudflare account and a Worker project. A payment card is not required for the Workers Free plan according to Cloudflare's current pricing information.

## 2. Create D1

From the `backend` directory:

```bash
npx wrangler d1 create realcoin-password-reset --binding DB
```

Copy the returned database ID into `wrangler.toml`.

Apply the schema remotely only after the database ID is correct:

```bash
npx wrangler d1 migrations apply realcoin-password-reset --remote
```

## 3. Generate signing keys

Generate an Ed25519 key pair locally. Convert the PKCS#8 private DER and SPKI public DER to base64. Set them as Worker secrets:

```bash
npx wrangler secret put RESET_TOKEN_PRIVATE_KEY_PKCS8_B64
npx wrangler secret put RESET_TOKEN_PUBLIC_KEY_SPKI_B64
```

Also set:

```bash
npx wrangler secret put RESEND_API_KEY
npx wrangler secret put RESEND_FROM_EMAIL
npx wrangler secret put OTP_HASH_SECRET
npx wrangler secret put RATE_LIMIT_SECRET
```

Never commit these values.

## 4. Resend

Create a Resend API key and verify the sending domain used by `RESEND_FROM_EMAIL`. Resend's API uses HTTPS and a bearer API key. Production sending should use a verified domain.

## 5. Deploy

After the D1 binding and required secrets are configured:

```bash
npx wrangler deploy
```

Then verify:

```bash
curl https://YOUR_WORKER_URL/health
```

The Android `PASSWORD_RESET_BACKEND_URL` should only be changed to the real HTTPS Worker URL after this health check and an end-to-end OTP test succeed.
