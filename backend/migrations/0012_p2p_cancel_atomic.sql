-- Make P2P escrow cancellation atomic at the database level.
-- Expiring an ESCROW_LOCKED order must restore ad capacity, and BUY orders
-- must also unlock the seller's REAL. If any required state is missing,
-- the cancellation statement aborts and D1 rolls the whole statement back.

CREATE TRIGGER IF NOT EXISTS trg_p2p_order_cancel_atomic
BEFORE UPDATE OF status ON p2p_orders
WHEN OLD.status = 'ESCROW_LOCKED'
 AND NEW.status = 'CANCELLED'
BEGIN
    SELECT (CASE
        WHEN OLD.expires_at_ms > (CAST(strftime('%s','now') AS INTEGER) * 1000)
        THEN RAISE(ABORT, 'p2p_order_not_expired')
    END);

    SELECT (CASE
        WHEN (
            SELECT COUNT(*)
            FROM p2p_ads a
            WHERE a.id = OLD.ad_id
        ) = 0
        THEN RAISE(ABORT, 'p2p_ad_not_found')
    END);

    SELECT (CASE
        WHEN (
            SELECT type
            FROM p2p_ads
            WHERE id = OLD.ad_id
        ) = 'BUY'
         AND (
            SELECT COUNT(*)
            FROM p2p_wallets w
            WHERE w.account_id = OLD.seller_id
              AND w.real_locked_balance >= OLD.crypto_amount
        ) = 0
        THEN RAISE(ABORT, 'p2p_escrow_unlock_unavailable')
    END);
END;

CREATE TRIGGER IF NOT EXISTS trg_p2p_order_cancel_unlock_buy
AFTER UPDATE OF status ON p2p_orders
WHEN OLD.status = 'ESCROW_LOCKED'
 AND NEW.status = 'CANCELLED'
 AND (
     SELECT type
     FROM p2p_ads
     WHERE id = OLD.ad_id
 ) = 'BUY'
BEGIN
    UPDATE p2p_wallets
       SET real_locked_balance =
               real_locked_balance - OLD.crypto_amount,
           updated_at_ms =
               COALESCE(
                   NEW.completed_at_ms,
                   OLD.expires_at_ms
               )
     WHERE account_id = OLD.seller_id;
END;

CREATE TRIGGER IF NOT EXISTS trg_p2p_order_cancel_restore_ad
AFTER UPDATE OF status ON p2p_orders
WHEN OLD.status = 'ESCROW_LOCKED'
 AND NEW.status = 'CANCELLED'
BEGIN
    UPDATE p2p_ads
       SET crypto_amount =
               crypto_amount + OLD.crypto_amount,
           max_order_etb =
               original_max_order_etb,
           is_active =
               CASE
                   WHEN crypto_amount + OLD.crypto_amount > 0
                    AND original_max_order_etb > 0
                   THEN 1
                   ELSE 0
               END
     WHERE id = OLD.ad_id;
END;
