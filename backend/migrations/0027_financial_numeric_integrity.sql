-- Extend numeric integrity to server-authoritative financial requests and ledgers.
-- The API already validates request amounts; these guards also protect against
-- direct database writes and non-finite/absurd numeric values.

CREATE TRIGGER IF NOT EXISTS trg_wallet_financial_request_numeric_insert
BEFORE INSERT ON wallet_financial_requests
WHEN NEW.amount <= 0 OR NEW.amount > 1000000000
BEGIN
    SELECT RAISE(ABORT, 'financial_amount_invalid');
END;

CREATE TRIGGER IF NOT EXISTS trg_wallet_financial_request_numeric_update
BEFORE UPDATE OF amount ON wallet_financial_requests
WHEN NEW.amount <= 0 OR NEW.amount > 1000000000
BEGIN
    SELECT RAISE(ABORT, 'financial_amount_invalid');
END;

CREATE TRIGGER IF NOT EXISTS trg_wallet_financial_ledger_numeric_insert
BEFORE INSERT ON wallet_financial_ledger
WHEN NEW.amount <= 0 OR NEW.amount > 1000000000
  OR NEW.balance_delta = 0
  OR NEW.balance_delta < -1000000000
  OR NEW.balance_delta > 1000000000
BEGIN
    SELECT RAISE(ABORT, 'financial_ledger_amount_invalid');
END;

CREATE TRIGGER IF NOT EXISTS trg_p2p_wallet_ledger_numeric_insert
BEFORE INSERT ON p2p_wallet_ledger
WHEN NEW.amount <= 0 OR NEW.amount > 1000000000
  OR NEW.balance_delta = 0
  OR NEW.balance_delta < -1000000000
  OR NEW.balance_delta > 1000000000
BEGIN
    SELECT RAISE(ABORT, 'p2p_ledger_amount_invalid');
END;
