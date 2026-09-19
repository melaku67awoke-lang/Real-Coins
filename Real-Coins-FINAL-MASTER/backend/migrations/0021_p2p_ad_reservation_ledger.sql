-- Immutable ledger for SELL-ad REAL reservations.
-- An ad reservation is distinct from an order escrow ledger because the
-- reservation can exist before any order and is released when the ad is deleted.
CREATE TABLE IF NOT EXISTS p2p_ad_reservation_ledger (
    id TEXT PRIMARY KEY,
    ad_id TEXT NOT NULL,
    account_id TEXT NOT NULL,
    event TEXT NOT NULL CHECK (event IN ('RESERVE', 'RELEASE')),
    real_delta REAL NOT NULL,
    locked_delta REAL NOT NULL,
    created_at_ms INTEGER NOT NULL,
    FOREIGN KEY (ad_id) REFERENCES p2p_ads(id),
    FOREIGN KEY (account_id) REFERENCES auth_accounts(id),
    UNIQUE(ad_id, event)
);
CREATE INDEX IF NOT EXISTS idx_p2p_ad_reservation_account
    ON p2p_ad_reservation_ledger(account_id, created_at_ms);

CREATE TRIGGER IF NOT EXISTS trg_p2p_ad_reservation_insert_ledger
AFTER INSERT ON p2p_ads
WHEN NEW.type = 'SELL'
BEGIN
    INSERT INTO p2p_ad_reservation_ledger(
        id, ad_id, account_id, event, real_delta, locked_delta, created_at_ms
    ) VALUES (
        'RC_AD_LEDGER_' || NEW.id || '_RESERVE',
        NEW.id,
        NEW.seller_id,
        'RESERVE',
        0,
        NEW.crypto_amount,
        NEW.created_at_ms
    );
END;

-- SQLite foreign keys normally make a parent-row DELETE incompatible with a
-- child ledger row. Capture the release before the ad disappears.
CREATE TRIGGER IF NOT EXISTS trg_p2p_ad_reservation_delete_ledger
BEFORE DELETE ON p2p_ads
WHEN OLD.type = 'SELL'
BEGIN
    INSERT INTO p2p_ad_reservation_ledger(
        id, ad_id, account_id, event, real_delta, locked_delta, created_at_ms
    ) VALUES (
        'RC_AD_LEDGER_' || OLD.id || '_RELEASE',
        OLD.id,
        OLD.seller_id,
        'RELEASE',
        0,
        -OLD.crypto_amount,
        strftime('%s','now') * 1000
    );
END;

CREATE TRIGGER IF NOT EXISTS trg_p2p_ad_reservation_ledger_immutable_update
BEFORE UPDATE ON p2p_ad_reservation_ledger
BEGIN
    SELECT RAISE(ABORT, 'p2p_ad_reservation_ledger_immutable');
END;

CREATE TRIGGER IF NOT EXISTS trg_p2p_ad_reservation_ledger_immutable_delete
BEFORE DELETE ON p2p_ad_reservation_ledger
BEGIN
    SELECT RAISE(ABORT, 'p2p_ad_reservation_ledger_immutable');
END;
