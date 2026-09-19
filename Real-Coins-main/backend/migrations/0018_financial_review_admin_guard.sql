-- Enforce that financial request reviews are performed only by an active ADMIN.
-- This protects the server-authoritative wallet workflow if a status update
-- reaches D1 outside the normal HTTP route.

CREATE TRIGGER IF NOT EXISTS trg_wallet_financial_review_admin_guard
BEFORE UPDATE OF status ON wallet_financial_requests
WHEN OLD.status = 'PENDING'
 AND NEW.status IN ('APPROVED', 'REJECTED')
BEGIN
    SELECT (CASE
        WHEN NEW.reviewed_by_admin_id IS NULL
          OR (
              SELECT COUNT(*)
              FROM auth_accounts a
              WHERE a.id = NEW.reviewed_by_admin_id
                AND a.role = 'ADMIN'
                AND a.disabled_at_ms IS NULL
          ) = 0
        THEN RAISE(ABORT, 'financial_review_admin_required')
    END);
END;
