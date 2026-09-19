-- Database-level numeric integrity for server-authoritative P2P state.
-- API validation is not enough because D1 must also reject malformed direct writes.

CREATE TRIGGER IF NOT EXISTS trg_p2p_ads_numeric_insert
BEFORE INSERT ON p2p_ads
WHEN NEW.crypto_amount <= 0
  OR NEW.fiat_price <= 0
  OR NEW.min_order_etb <= 0
  OR NEW.max_order_etb <= 0
  OR NEW.min_order_etb > NEW.max_order_etb
  OR NEW.original_max_order_etb <= 0
BEGIN
    SELECT RAISE(ABORT, 'p2p_ad_numeric_invariant_violation');
END;

CREATE TRIGGER IF NOT EXISTS trg_p2p_ads_numeric_update
BEFORE UPDATE OF crypto_amount, fiat_price, min_order_etb, max_order_etb, original_max_order_etb ON p2p_ads
WHEN NEW.crypto_amount < 0
  OR NEW.fiat_price <= 0
  OR NEW.min_order_etb <= 0
  OR NEW.max_order_etb < 0
  OR NEW.min_order_etb > NEW.max_order_etb
  OR NEW.original_max_order_etb <= 0
BEGIN
    SELECT RAISE(ABORT, 'p2p_ad_numeric_invariant_violation');
END;

CREATE TRIGGER IF NOT EXISTS trg_p2p_orders_numeric_insert
BEFORE INSERT ON p2p_orders
WHEN NEW.crypto_amount <= 0
  OR NEW.fiat_price <= 0
  OR NEW.fiat_order_amount <= 0
  OR NEW.expires_at_ms <= NEW.created_at_ms
BEGIN
    SELECT RAISE(ABORT, 'p2p_order_numeric_invariant_violation');
END;

CREATE TRIGGER IF NOT EXISTS trg_p2p_orders_numeric_update
BEFORE UPDATE OF crypto_amount, fiat_price, fiat_order_amount, created_at_ms, expires_at_ms ON p2p_orders
WHEN NEW.crypto_amount <= 0
  OR NEW.fiat_price <= 0
  OR NEW.fiat_order_amount <= 0
  OR NEW.expires_at_ms <= NEW.created_at_ms
BEGIN
    SELECT RAISE(ABORT, 'p2p_order_numeric_invariant_violation');
END;
