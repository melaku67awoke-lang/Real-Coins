-- RealCoins admin-verification password recovery
-- No email OTP is used in this flow.

CREATE TABLE IF NOT EXISTS auth_accounts (
    id TEXT PRIMARY KEY,
    username TEXT NOT NULL UNIQUE,
    normalized_email TEXT NOT NULL UNIQUE,
    password_hash_b64 TEXT NOT NULL,
    password_salt_b64 TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'USER'
        CHECK (role IN ('USER', 'ADMIN')),
    created_at_ms INTEGER NOT NULL,
    updated_at_ms INTEGER NOT NULL,
    disabled_at_ms INTEGER
);

CREATE INDEX IF NOT EXISTS idx_auth_accounts_email
    ON auth_accounts (normalized_email);

CREATE TABLE IF NOT EXISTS auth_sessions (
    id TEXT PRIMARY KEY,
    account_id TEXT NOT NULL,
    token_hash_b64 TEXT NOT NULL UNIQUE,
    created_at_ms INTEGER NOT NULL,
    expires_at_ms INTEGER NOT NULL,
    revoked_at_ms INTEGER,
    FOREIGN KEY (account_id) REFERENCES auth_accounts(id)
);

CREATE INDEX IF NOT EXISTS idx_auth_sessions_token
    ON auth_sessions (token_hash_b64);

CREATE INDEX IF NOT EXISTS idx_auth_sessions_account
    ON auth_sessions (account_id);

CREATE TABLE IF NOT EXISTS password_recovery_requests (
    id TEXT PRIMARY KEY,
    account_id TEXT NOT NULL,
    normalized_email TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'COMPLETED')),
    requested_at_ms INTEGER NOT NULL,
    reviewed_at_ms INTEGER,
    reviewed_by_account_id TEXT,
    completed_at_ms INTEGER,
    FOREIGN KEY (account_id) REFERENCES auth_accounts(id),
    FOREIGN KEY (reviewed_by_account_id) REFERENCES auth_accounts(id)
);

CREATE INDEX IF NOT EXISTS idx_password_recovery_status
    ON password_recovery_requests (status);

CREATE INDEX IF NOT EXISTS idx_password_recovery_account
    ON password_recovery_requests (account_id);

CREATE INDEX IF NOT EXISTS idx_password_recovery_requested
    ON password_recovery_requests (requested_at_ms);
