-- Enforce separation of duties for financial request approvals.
-- The requesting account must not approve its own deposit/withdrawal request.
-- This guard also protects against direct D1 writes that bypass the HTTP API.

CREATE TRIGGER IF NOT EXISTS trg_wallet_financial_no_self_approval
BEFORE UPDATE OF status ON wallet_financial_requests
WHEN OLD.status = 'PENDING'
 AND NEW.status = 'APPROVED'
BEGIN
    SELECT (CASE
        WHEN NEW.reviewed_by_admin_id IS NULL
          OR NEW.reviewed_by_admin_id = OLD.account_id
        THEN RAISE(ABORT, 'financial_request_self_approval_forbidden')
    END);
END;
