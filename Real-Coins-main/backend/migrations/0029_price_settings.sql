CREATE TABLE IF NOT EXISTS app_price_settings (
    key TEXT PRIMARY KEY,
    value REAL NOT NULL,
    updated_at_ms INTEGER NOT NULL,
    updated_by_admin_id TEXT,
    FOREIGN KEY (updated_by_admin_id) REFERENCES auth_accounts(id)
);
INSERT OR IGNORE INTO app_price_settings(key, value, updated_at_ms, updated_by_admin_id)
VALUES ('REAL_COIN_USD_VALUE', 0.0027, 0, NULL);
