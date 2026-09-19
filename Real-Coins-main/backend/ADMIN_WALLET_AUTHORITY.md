# RealCoins backend wallet/admin authority

## Admin bootstrap
New registrations are always `USER`; the client cannot request `ADMIN`.

Set `ADMIN_BOOTSTRAP_SECRET` as a Cloudflare Worker Secret. Then call `POST /v1/admin/bootstrap` with header `X-Admin-Bootstrap-Secret` and body `{"email":"admin@example.com"}`. It can promote only an enabled USER account and only while no ADMIN exists. After the first admin exists, bootstrap is disabled.

Never put the bootstrap secret in the Android app.

## Wallet authority
`p2p_wallets` is server-authoritative for P2P REAL. The Android client must not set or bootstrap balances.

An authenticated ADMIN can use `POST /v1/admin/p2p/wallet-adjustment` with `accountId`, `operation` (`CREDIT` or `DEBIT`), `amount`, and `reason`. Successful adjustments are recorded in `p2p_wallet_ledger`. DEBIT cannot reduce available REAL below locked REAL.

Deposit/withdrawal automation still requires a separate server-authoritative ledger/integration before financial authority is complete.
