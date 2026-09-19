-- Financial ledger is an immutable record of an approved deposit/withdrawal.
-- Wallet balances may change only through the financial-request workflow; the
-- corresponding ledger history must never be editable or removable.

CREATE TRIGGER IF NOT EXISTS trg_wallet_financial_ledger_immutable_update
BEFORE UPDATE ON wallet_financial_ledger
BEGIN
    SELECT RAISE(ABORT, 'wallet_financial_ledger_immutable');
END;

CREATE TRIGGER IF NOT EXISTS trg_wallet_financial_ledger_immutable_delete
BEFORE DELETE ON wallet_financial_ledger
BEGIN
    SELECT RAISE(ABORT, 'wallet_financial_ledger_immutable');
END;
