-- Immutable audit trail for privileged financial and dispute decisions.
CREATE TABLE IF NOT EXISTS admin_audit_log (
    id TEXT PRIMARY KEY,
    admin_account_id TEXT NOT NULL,
    action TEXT NOT NULL,
    target_account_id TEXT,
    target_request_id TEXT,
    target_order_id TEXT,
    operation TEXT,
    amount REAL,
    reason TEXT NOT NULL DEFAULT '',
    created_at_ms INTEGER NOT NULL,
    FOREIGN KEY (admin_account_id) REFERENCES auth_accounts(id),
    FOREIGN KEY (target_account_id) REFERENCES auth_accounts(id),
    FOREIGN KEY (target_request_id) REFERENCES wallet_financial_requests(id),
    FOREIGN KEY (target_order_id) REFERENCES p2p_orders(id)
);
CREATE INDEX IF NOT EXISTS idx_admin_audit_created ON admin_audit_log(created_at_ms);
CREATE INDEX IF NOT EXISTS idx_admin_audit_admin ON admin_audit_log(admin_account_id, created_at_ms);
CREATE INDEX IF NOT EXISTS idx_admin_audit_target_account ON admin_audit_log(target_account_id, created_at_ms);

CREATE TRIGGER IF NOT EXISTS trg_admin_audit_immutable_update
BEFORE UPDATE ON admin_audit_log
BEGIN
    SELECT RAISE(ABORT, 'admin_audit_immutable');
END;

CREATE TRIGGER IF NOT EXISTS trg_admin_audit_immutable_delete
BEFORE DELETE ON admin_audit_log
BEGIN
    SELECT RAISE(ABORT, 'admin_audit_immutable');
END;
