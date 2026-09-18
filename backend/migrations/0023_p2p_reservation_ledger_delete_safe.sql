-- Allow SELL ads to be deleted after their reservation is released.
-- The reservation ledger is an audit trail, so it must not be a child row that
-- prevents deletion of the ad it records. Keep account ownership as a foreign
-- key, but intentionally remove the ad_id foreign key.
PRAGMA foreign_keys=OFF;

CREATE TABLE p2p_ad_reservation_ledger_v3 (
    id TEXT PRIMARY KEY,
    ad_id TEXT NOT NULL,
    account_id TEXT NOT NULL,
    order_id TEXT,
    event TEXT NOT NULL CHECK (event IN ('RESERVE', 'RELEASE', 'CONSUME')),
    real_delta REAL NOT NULL,
    locked_delta REAL NOT NULL,
    created_at_ms INTEGER NOT NULL,
    FOREIGN KEY (account_id) REFERENCES auth_accounts(id)
);

INSERT INTO p2p_ad_reservation_ledger_v3(
    id, ad_id, account_id, order_id, event, real_delta, locked_delta, created_at_ms
)
SELECT id, ad_id, account_id, order_id, event, real_delta, locked_delta, created_at_ms
FROM p2p_ad_reservation_ledger;

DROP TABLE p2p_ad_reservation_ledger;
ALTER TABLE p2p_ad_reservation_ledger_v3 RENAME TO p2p_ad_reservation_ledger;

CREATE INDEX IF NOT EXISTS idx_p2p_ad_reservation_account
    ON p2p_ad_reservation_ledger(account_id, created_at_ms);
CREATE INDEX IF NOT EXISTS idx_p2p_ad_reservation_order
    ON p2p_ad_reservation_ledger(order_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_p2p_ad_reservation_reserve
    ON p2p_ad_reservation_ledger(ad_id) WHERE event = 'RESERVE';
CREATE UNIQUE INDEX IF NOT EXISTS uq_p2p_ad_reservation_release
    ON p2p_ad_reservation_ledger(ad_id) WHERE event = 'RELEASE';
CREATE UNIQUE INDEX IF NOT EXISTS uq_p2p_ad_reservation_order_consume
    ON p2p_ad_reservation_ledger(ad_id, order_id) WHERE event = 'CONSUME';

CREATE TRIGGER IF NOT EXISTS trg_p2p_ad_reservation_insert_ledger
AFTER INSERT ON p2p_ads
WHEN NEW.type = 'SELL'
BEGIN
    INSERT INTO p2p_ad_reservation_ledger(
        id, ad_id, account_id, order_id, event, real_delta, locked_delta, created_at_ms
    ) VALUES (
        'RC_AD_LEDGER_' || NEW.id || '_RESERVE',
        NEW.id,
        NEW.seller_id,
        NULL,
        'RESERVE',
        0,
        NEW.crypto_amount,
        NEW.created_at_ms
    );
END;

CREATE TRIGGER IF NOT EXISTS trg_p2p_ad_reservation_delete_ledger
BEFORE DELETE ON p2p_ads
WHEN OLD.type = 'SELL'
BEGIN
    INSERT INTO p2p_ad_reservation_ledger(
        id, ad_id, account_id, order_id, event, real_delta, locked_delta, created_at_ms
    ) VALUES (
        'RC_AD_LEDGER_' || OLD.id || '_RELEASE',
        OLD.id,
        OLD.seller_id,
        NULL,
        'RELEASE',
        0,
        -OLD.crypto_amount,
        strftime('%s','now') * 1000
    );
END;

CREATE TRIGGER IF NOT EXISTS trg_p2p_ad_reservation_consume_on_completion
AFTER UPDATE OF status ON p2p_orders
WHEN OLD.status = 'PAID'
 AND NEW.status = 'COMPLETED'
 AND (SELECT type FROM p2p_ads WHERE id = NEW.ad_id) = 'SELL'
BEGIN
    INSERT INTO p2p_ad_reservation_ledger(
        id, ad_id, account_id, order_id, event, real_delta, locked_delta, created_at_ms
    ) VALUES (
        'RC_AD_LEDGER_' || NEW.ad_id || '_ORDER_' || NEW.id || '_CONSUME',
        NEW.ad_id,
        NEW.seller_id,
        NEW.id,
        'CONSUME',
        -NEW.crypto_amount,
        -NEW.crypto_amount,
        COALESCE(NEW.completed_at_ms, strftime('%s','now') * 1000)
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

PRAGMA foreign_keys=ON;
