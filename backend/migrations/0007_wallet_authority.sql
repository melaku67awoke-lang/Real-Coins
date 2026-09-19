-- Server-authoritative wallet adjustment audit trail.
CREATE TABLE IF NOT EXISTS p2p_wallet_ledger (
    id TEXT PRIMARY KEY,
    account_id TEXT NOT NULL,
    admin_account_id TEXT NOT NULL,
    operation TEXT NOT NULL CHECK (operation IN ('CREDIT', 'DEBIT')),
    amount REAL NOT NULL CHECK (amount > 0),
    balance_delta REAL NOT NULL CHECK (balance_delta != 0),
    reason TEXT NOT NULL,
    created_at_ms INTEGER NOT NULL,
    FOREIGN KEY (account_id) REFERENCES auth_accounts(id),
    FOREIGN KEY (admin_account_id) REFERENCES auth_accounts(id)
);
CREATE INDEX IF NOT EXISTS idx_p2p_wallet_ledger_account ON p2p_wallet_ledger(account_id, created_at_ms);
CREATE INDEX IF NOT EXISTS idx_p2p_wallet_ledger_admin ON p2p_wallet_ledger(admin_account_id, created_at_ms);
