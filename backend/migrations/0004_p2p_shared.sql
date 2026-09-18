-- Shared P2P marketplace state. Room remains a local cache on Android.
CREATE TABLE IF NOT EXISTS p2p_wallets (
    account_id TEXT PRIMARY KEY,
    real_balance REAL NOT NULL DEFAULT 0,
    real_locked_balance REAL NOT NULL DEFAULT 0,
    updated_at_ms INTEGER NOT NULL,
    FOREIGN KEY (account_id) REFERENCES auth_accounts(id)
);

CREATE TABLE IF NOT EXISTS p2p_ads (
    id TEXT PRIMARY KEY,
    seller_id TEXT NOT NULL,
    seller_name TEXT NOT NULL,
    type TEXT NOT NULL CHECK (type IN ('BUY','SELL')),
    crypto_amount REAL NOT NULL,
    fiat_price REAL NOT NULL,
    fiat_currency TEXT NOT NULL DEFAULT 'ETB',
    payment_method TEXT NOT NULL,
    payment_name TEXT NOT NULL DEFAULT '',
    account_number TEXT NOT NULL DEFAULT '',
    is_active INTEGER NOT NULL DEFAULT 1,
    min_order_etb REAL NOT NULL,
    max_order_etb REAL NOT NULL,
    original_max_order_etb REAL NOT NULL,
    created_at_ms INTEGER NOT NULL,
    FOREIGN KEY (seller_id) REFERENCES auth_accounts(id)
);
CREATE INDEX IF NOT EXISTS idx_p2p_ads_active ON p2p_ads(is_active, type);
CREATE INDEX IF NOT EXISTS idx_p2p_ads_seller ON p2p_ads(seller_id);

CREATE TABLE IF NOT EXISTS p2p_orders (
    id TEXT PRIMARY KEY,
    ad_id TEXT NOT NULL,
    seller_id TEXT NOT NULL,
    seller_name TEXT NOT NULL,
    buyer_id TEXT NOT NULL,
    buyer_name TEXT NOT NULL,
    crypto_amount REAL NOT NULL,
    fiat_price REAL NOT NULL,
    fiat_currency TEXT NOT NULL DEFAULT 'ETB',
    payment_method TEXT NOT NULL,
    payment_name TEXT NOT NULL DEFAULT '',
    account_number TEXT NOT NULL DEFAULT '',
    payment_proof_url TEXT,
    paid_at_ms INTEGER,
    status TEXT NOT NULL DEFAULT 'ESCROW_LOCKED'
        CHECK (status IN ('ESCROW_LOCKED','PAID','COMPLETED','CANCELLED','DISPUTED')),
    created_at_ms INTEGER NOT NULL,
    expires_at_ms INTEGER NOT NULL,
    completed_at_ms INTEGER,
    dispute_reason TEXT,
    disputed_at_ms INTEGER,
    resolved_at_ms INTEGER,
    resolved_by_admin_id TEXT,
    FOREIGN KEY (ad_id) REFERENCES p2p_ads(id),
    FOREIGN KEY (seller_id) REFERENCES auth_accounts(id),
    FOREIGN KEY (buyer_id) REFERENCES auth_accounts(id)
);
CREATE INDEX IF NOT EXISTS idx_p2p_orders_user
    ON p2p_orders(buyer_id, seller_id, created_at_ms);
CREATE INDEX IF NOT EXISTS idx_p2p_orders_ad_open
    ON p2p_orders(ad_id, status);

CREATE TABLE IF NOT EXISTS p2p_chat_messages (
    id TEXT PRIMARY KEY,
    order_id TEXT NOT NULL,
    sender_id TEXT NOT NULL,
    sender_name TEXT NOT NULL,
    message TEXT NOT NULL DEFAULT '',
    attachment_url TEXT,
    created_at_ms INTEGER NOT NULL,
    FOREIGN KEY (order_id) REFERENCES p2p_orders(id),
    FOREIGN KEY (sender_id) REFERENCES auth_accounts(id)
);
CREATE INDEX IF NOT EXISTS idx_p2p_chat_order_created
    ON p2p_chat_messages(order_id, created_at_ms);
