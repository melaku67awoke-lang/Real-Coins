CREATE TABLE IF NOT EXISTS reset_sessions (
    id TEXT PRIMARY KEY,
    normalized_email TEXT NOT NULL,
    otp_hash_b64 TEXT NOT NULL,
    otp_salt_b64 TEXT NOT NULL,
    created_at_ms INTEGER NOT NULL,
    expires_at_ms INTEGER NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL,
    verified_at_ms INTEGER,
    used_at_ms INTEGER,
    reset_token_hash_b64 TEXT
);

CREATE INDEX IF NOT EXISTS idx_reset_sessions_expires_at
    ON reset_sessions (expires_at_ms);

CREATE TABLE IF NOT EXISTS rate_limits (
    email_key_b64 TEXT PRIMARY KEY,
    window_ends_at_ms INTEGER NOT NULL,
    request_count INTEGER NOT NULL
);
