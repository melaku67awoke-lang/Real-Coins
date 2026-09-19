-- Reject non-finite or unreasonably large P2P numeric values at the database boundary.
-- API validation already requires finite JavaScript numbers, but D1 must also
-- protect against direct writes that could overflow or poison financial state.
CREATE TRIGGER IF NOT EXISTS trg_p2p_ads_finite_numeric_insert
BEFORE INSERT ON p2p_ads
WHEN typeof(NEW.crypto_amount) NOT IN ('integer', 'real') OR typeof(NEW.fiat_price) NOT IN ('integer', 'real')
  OR typeof(NEW.min_order_etb) NOT IN ('integer', 'real') OR typeof(NEW.max_order_etb) NOT IN ('integer', 'real')
  OR typeof(NEW.original_max_order_etb) NOT IN ('integer', 'real')
  OR abs(NEW.crypto_amount) > 1000000000000 OR abs(NEW.fiat_price) > 1000000000000
  OR abs(NEW.min_order_etb) > 1000000000000 OR abs(NEW.max_order_etb) > 1000000000000
  OR abs(NEW.original_max_order_etb) > 1000000000000
BEGIN SELECT RAISE(ABORT, 'p2p_ad_numeric_range_violation'); END;

CREATE TRIGGER IF NOT EXISTS trg_p2p_ads_finite_numeric_update
BEFORE UPDATE OF crypto_amount, fiat_price, min_order_etb, max_order_etb, original_max_order_etb ON p2p_ads
WHEN typeof(NEW.crypto_amount) NOT IN ('integer', 'real') OR typeof(NEW.fiat_price) NOT IN ('integer', 'real')
  OR typeof(NEW.min_order_etb) NOT IN ('integer', 'real') OR typeof(NEW.max_order_etb) NOT IN ('integer', 'real')
  OR typeof(NEW.original_max_order_etb) NOT IN ('integer', 'real')
  OR abs(NEW.crypto_amount) > 1000000000000 OR abs(NEW.fiat_price) > 1000000000000
  OR abs(NEW.min_order_etb) > 1000000000000 OR abs(NEW.max_order_etb) > 1000000000000
  OR abs(NEW.original_max_order_etb) > 1000000000000
BEGIN SELECT RAISE(ABORT, 'p2p_ad_numeric_range_violation'); END;

CREATE TRIGGER IF NOT EXISTS trg_p2p_orders_finite_numeric_insert
BEFORE INSERT ON p2p_orders
WHEN typeof(NEW.crypto_amount) NOT IN ('integer', 'real') OR typeof(NEW.fiat_price) NOT IN ('integer', 'real')
  OR typeof(NEW.fiat_order_amount) NOT IN ('integer', 'real')
  OR abs(NEW.crypto_amount) > 1000000000000 OR abs(NEW.fiat_price) > 1000000000000
  OR abs(NEW.fiat_order_amount) > 1000000000000
BEGIN SELECT RAISE(ABORT, 'p2p_order_numeric_range_violation'); END;

CREATE TRIGGER IF NOT EXISTS trg_p2p_orders_finite_numeric_update
BEFORE UPDATE OF crypto_amount, fiat_price, fiat_order_amount ON p2p_orders
WHEN typeof(NEW.crypto_amount) NOT IN ('integer', 'real') OR typeof(NEW.fiat_price) NOT IN ('integer', 'real')
  OR typeof(NEW.fiat_order_amount) NOT IN ('integer', 'real')
  OR abs(NEW.crypto_amount) > 1000000000000 OR abs(NEW.fiat_price) > 1000000000000
  OR abs(NEW.fiat_order_amount) > 1000000000000
BEGIN SELECT RAISE(ABORT, 'p2p_order_numeric_range_violation'); END;
