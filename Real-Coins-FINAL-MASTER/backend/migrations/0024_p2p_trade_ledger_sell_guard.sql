-- SELL orders already reserve the seller's REAL at ad creation.
-- They must not create a second P2P order-level ESCROW_LOCK ledger event.
-- BUY orders are the order-level escrow case because the seller's REAL is
-- locked when the BUY order starts.
CREATE TRIGGER IF NOT EXISTS trg_p2p_trade_ledger_sell_escrow_lock_guard
BEFORE INSERT ON p2p_trade_ledger
WHEN NEW.event = 'ESCROW_LOCK'
 AND (SELECT type FROM p2p_ads a JOIN p2p_orders o ON o.ad_id = a.id WHERE o.id = NEW.order_id) = 'SELL'
BEGIN
    SELECT RAISE(ABORT, 'sell_order_escrow_lock_forbidden');
END;
