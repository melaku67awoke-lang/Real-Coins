-- Server-authoritative deposit/withdrawal request workflow.
-- Client requests never mutate wallet balances directly.

CREATE TABLE IF NOT EXISTS wallet_financial_requests (
    id TEXT PRIMARY KEY,
    account_id TEXT NOT NULL,
    type TEXT NOT NULL CHECK (type IN ('DEPOSIT', 'WITHDRAW')),
    amount REAL NOT NULL CHECK (amount > 0),
    reference TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    idempotency_key TEXT NOT NULL,
    requested_at_ms INTEGER NOT NULL,
    reviewed_at_ms INTEGER,
    reviewed_by_admin_id TEXT,
    FOREIGN KEY (account_id) REFERENCES auth_accounts(id),
    FOREIGN KEY (reviewed_by_admin_id) REFERENCES auth_accounts(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_wallet_financial_request_idempotency
    ON wallet_financial_requests(account_id, idempotency_key);
CREATE INDEX IF NOT EXISTS idx_wallet_financial_request_status
    ON wallet_financial_requests(status, requested_at_ms);
CREATE INDEX IF NOT EXISTS idx_wallet_financial_request_account
    ON wallet_financial_requests(account_id, requested_at_ms);

CREATE TABLE IF NOT EXISTS wallet_financial_ledger (
    id TEXT PRIMARY KEY,
    request_id TEXT NOT NULL UNIQUE,
    account_id TEXT NOT NULL,
    admin_account_id TEXT NOT NULL,
    operation TEXT NOT NULL CHECK (operation IN ('DEPOSIT', 'WITHDRAW')),
    amount REAL NOT NULL CHECK (amount > 0),
    balance_delta REAL NOT NULL CHECK (balance_delta != 0),
    created_at_ms INTEGER NOT NULL,
    FOREIGN KEY (request_id) REFERENCES wallet_financial_requests(id),
    FOREIGN KEY (account_id) REFERENCES auth_accounts(id),
    FOREIGN KEY (admin_account_id) REFERENCES auth_accounts(id)
);
CREATE INDEX IF NOT EXISTS idx_wallet_financial_ledger_account
    ON wallet_financial_ledger(account_id, created_at_ms);

-- Approval is the only event that can change the wallet for a request.
-- SQLite triggers make the wallet mutation and request status transition atomic.
CREATE TRIGGER IF NOT EXISTS trg_wallet_financial_approve_withdraw
BEFORE UPDATE OF status ON wallet_financial_requests
WHEN OLD.status = 'PENDING' AND NEW.status = 'APPROVED' AND OLD.type = 'WITHDRAW'
BEGIN
    SELECT CASE
        WHEN (SELECT COUNT(*) FROM p2p_wallets w
              WHERE w.account_id = OLD.account_id
                AND w.real_balance - w.real_locked_balance >= OLD.amount) = 0
        THEN RAISE(ABORT, 'insufficient_available_balance')
    END;
END;

CREATE TRIGGER IF NOT EXISTS trg_wallet_financial_apply_deposit
AFTER UPDATE OF status ON wallet_financial_requests
WHEN OLD.status = 'PENDING' AND NEW.status = 'APPROVED' AND OLD.type = 'DEPOSIT'
BEGIN
    INSERT INTO p2p_wallets(account_id, real_balance, real_locked_balance, updated_at_ms)
    VALUES (OLD.account_id, OLD.amount, 0, NEW.reviewed_at_ms)
    ON CONFLICT(account_id) DO UPDATE SET
        real_balance = real_balance + OLD.amount,
        updated_at_ms = NEW.reviewed_at_ms;
END;

CREATE TRIGGER IF NOT EXISTS trg_wallet_financial_apply_withdraw
AFTER UPDATE OF status ON wallet_financial_requests
WHEN OLD.status = 'PENDING' AND NEW.status = 'APPROVED' AND OLD.type = 'WITHDRAW'
BEGIN
    UPDATE p2p_wallets
       SET real_balance = real_balance - OLD.amount,
           updated_at_ms = NEW.reviewed_at_ms
     WHERE account_id = OLD.account_id;
END;

CREATE TRIGGER IF NOT EXISTS trg_wallet_financial_ledger
AFTER UPDATE OF status ON wallet_financial_requests
WHEN OLD.status = 'PENDING' AND NEW.status = 'APPROVED'
BEGIN
    INSERT INTO wallet_financial_ledger(
        id, request_id, account_id, admin_account_id, operation,
        amount, balance_delta, created_at_ms
    ) VALUES (
        'RC_FIN_' || OLD.id,
        OLD.id,
        OLD.account_id,
        NEW.reviewed_by_admin_id,
        OLD.type,
        OLD.amount,
        CASE WHEN OLD.type = 'DEPOSIT' THEN OLD.amount ELSE -OLD.amount END,
        NEW.reviewed_at_ms
    );
END;
