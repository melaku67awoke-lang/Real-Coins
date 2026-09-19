-- Audit trail for privileged password-recovery review decisions.
-- The recovery request id is intentionally not a foreign key because the
-- audit table also covers recovery requests that may reference an email which
-- has no current auth account.
ALTER TABLE admin_audit_log ADD COLUMN target_password_recovery_id TEXT;
CREATE INDEX IF NOT EXISTS idx_admin_audit_target_password_recovery
    ON admin_audit_log(target_password_recovery_id);
