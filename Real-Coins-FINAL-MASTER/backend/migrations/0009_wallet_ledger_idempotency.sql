-- Prevent replaying an administrator wallet adjustment.
ALTER TABLE p2p_wallet_ledger ADD COLUMN idempotency_key TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS uq_p2p_wallet_ledger_idempotency
    ON p2p_wallet_ledger(idempotency_key)
    WHERE idempotency_key IS NOT NULL;
