-- Atomically release SELL REAL locked for an ad when that ad is deleted.
-- The unlock occurs inside the same SQLite statement as the DELETE, so a
-- failed/racing delete cannot accidentally release the seller's funds.

CREATE TRIGGER IF NOT EXISTS trg_p2p_sell_ad_delete_unlock
BEFORE DELETE ON p2p_ads
WHEN OLD.type = 'SELL'
BEGIN
    SELECT (CASE
        WHEN (
            SELECT COUNT(*)
            FROM p2p_wallets w
            WHERE w.account_id = OLD.seller_id
              AND w.real_locked_balance >= OLD.crypto_amount
        ) = 0
        THEN RAISE(ABORT, 'wallet_lock_inconsistent')
    END);

    UPDATE p2p_wallets
       SET real_locked_balance =
               real_locked_balance - OLD.crypto_amount,
           updated_at_ms =
               strftime('%s','now') * 1000
     WHERE account_id = OLD.seller_id;
END;
