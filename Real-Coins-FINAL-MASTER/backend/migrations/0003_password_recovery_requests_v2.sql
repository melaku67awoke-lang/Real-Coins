-- RealCoins admin-verification password recovery v2.
-- This table is intentionally independent from server-side auth_accounts because
-- the current Android application stores its user accounts in local Room.
-- An administrator reviews requests in D1 and changes status to APPROVED or REJECTED.

CREATE TABLE IF NOT EXISTS password_recovery_requests_v2 (
    id TEXT PRIMARY KEY,
    normalized_email TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'COMPLETED')),
    requested_at_ms INTEGER NOT NULL,
    reviewed_at_ms INTEGER,
    completed_at_ms INTEGER
);

CREATE INDEX IF NOT EXISTS idx_password_recovery_v2_status
    ON password_recovery_requests_v2 (status);

CREATE INDEX IF NOT EXISTS idx_password_recovery_v2_email
    ON password_recovery_requests_v2 (normalized_email);

CREATE INDEX IF NOT EXISTS idx_password_recovery_v2_requested
    ON password_recovery_requests_v2 (requested_at_ms);
