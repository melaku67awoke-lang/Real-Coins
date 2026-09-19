-- Immutable server-side ledger for P2P order wallet movements.
-- One row per order/account/event prevents silent double application.
CREATE TABLE IF NOT EXISTS p2p_trade_ledger (
    id TEXT PRIMARY KEY,
    order_id TEXT NOT NULL,
    account_id TEXT NOT NULL,
    event TEXT NOT NULL CHECK (event IN ('ESCROW_LOCK','ESCROW_UNLOCK','TRADE_DEBIT','TRADE_CREDIT')),
    real_delta REAL NOT NULL,
    locked_delta REAL NOT NULL,
    created_at_ms INTEGER NOT NULL,
    FOREIGN KEY (order_id) REFERENCES p2p_orders(id),
    FOREIGN KEY (account_id) REFERENCES auth_accounts(id),
    UNIQUE(order_id, account_id, event)
);
CREATE INDEX IF NOT EXISTS idx_p2p_trade_ledger_order ON p2p_trade_ledger(order_id, created_at_ms);
CREATE INDEX IF NOT EXISTS idx_p2p_trade_ledger_account ON p2p_trade_ledger(account_id, created_at_ms);

CREATE TRIGGER IF NOT EXISTS trg_p2p_trade_ledger_immutable_update
BEFORE UPDATE ON p2p_trade_ledger
BEGIN
    SELECT RAISE(ABORT, 'p2p_trade_ledger_immutable');
END;

CREATE TRIGGER IF NOT EXISTS trg_p2p_trade_ledger_immutable_delete
BEFORE DELETE ON p2p_trade_ledger
BEGIN
    SELECT RAISE(ABORT, 'p2p_trade_ledger_immutable');
END;

-- Expiry of a BUY order unlocks the seller's REAL in the same statement as
-- cancellation. Record that unlock atomically as well.
CREATE TRIGGER IF NOT EXISTS trg_p2p_trade_ledger_expiry_unlock
AFTER UPDATE OF status ON p2p_orders
WHEN OLD.status = 'ESCROW_LOCKED'
 AND NEW.status = 'CANCELLED'
 AND (SELECT type FROM p2p_ads WHERE id = OLD.ad_id) = 'BUY'
BEGIN
    INSERT INTO p2p_trade_ledger(
        id, order_id, account_id, event, real_delta, locked_delta, created_at_ms
    ) VALUES (
        'RC_TRADE_LEDGER_' || OLD.id || '_UNLOCK',
        OLD.id,
        OLD.seller_id,
        'ESCROW_UNLOCK',
        0,
        -OLD.crypto_amount,
        COALESCE(NEW.completed_at_ms, OLD.expires_at_ms)
    );
END;
