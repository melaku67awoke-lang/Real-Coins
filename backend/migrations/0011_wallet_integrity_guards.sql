-- Wallet integrity and audit protection.
-- Wallet balances must never become negative and locked REAL can never exceed total REAL.

CREATE TRIGGER IF NOT EXISTS trg_p2p_wallets_integrity_insert
BEFORE INSERT ON p2p_wallets
WHEN NEW.real_balance < 0
  OR NEW.real_locked_balance < 0
  OR NEW.real_locked_balance > NEW.real_balance
BEGIN
    SELECT RAISE(ABORT, 'wallet_balance_invariant_violation');
END;

CREATE TRIGGER IF NOT EXISTS trg_p2p_wallets_integrity_update
BEFORE UPDATE OF real_balance, real_locked_balance ON p2p_wallets
WHEN NEW.real_balance < 0
  OR NEW.real_locked_balance < 0
  OR NEW.real_locked_balance > NEW.real_balance
BEGIN
    SELECT RAISE(ABORT, 'wallet_balance_invariant_violation');
END;

-- Financial ledgers are append-only. Corrections must be represented by a new
-- compensating entry rather than modifying history.
CREATE TRIGGER IF NOT EXISTS trg_wallet_financial_ledger_no_update
BEFORE UPDATE ON wallet_financial_ledger
BEGIN
    SELECT RAISE(ABORT, 'financial_ledger_immutable');
END;

CREATE TRIGGER IF NOT EXISTS trg_wallet_financial_ledger_no_delete
BEFORE DELETE ON wallet_financial_ledger
BEGIN
    SELECT RAISE(ABORT, 'financial_ledger_immutable');
END;

CREATE TRIGGER IF NOT EXISTS trg_p2p_wallet_ledger_no_update
BEFORE UPDATE ON p2p_wallet_ledger
BEGIN
    SELECT RAISE(ABORT, 'p2p_ledger_immutable');
END;

CREATE TRIGGER IF NOT EXISTS trg_p2p_wallet_ledger_no_delete
BEFORE DELETE ON p2p_wallet_ledger
BEGIN
    SELECT RAISE(ABORT, 'p2p_ledger_immutable');
END;
