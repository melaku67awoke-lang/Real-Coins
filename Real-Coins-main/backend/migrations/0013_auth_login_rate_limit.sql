-- Durable login throttling. Keys are HMAC-hashed identifiers, never raw credentials.
CREATE TABLE IF NOT EXISTS auth_login_rate_limits (
    key_b64 TEXT PRIMARY KEY,
    window_ends_at_ms INTEGER NOT NULL,
    attempt_count INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_auth_login_rate_limits_window
    ON auth_login_rate_limits(window_ends_at_ms);
