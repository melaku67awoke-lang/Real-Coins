-- Small MVP payment/chat image storage. Access is controlled by the authenticated Worker.
CREATE TABLE IF NOT EXISTS p2p_attachments (
    id TEXT PRIMARY KEY,
    owner_account_id TEXT NOT NULL,
    content_type TEXT NOT NULL,
    data_base64 TEXT NOT NULL,
    created_at_ms INTEGER NOT NULL,
    FOREIGN KEY (owner_account_id) REFERENCES auth_accounts(id)
);
CREATE INDEX IF NOT EXISTS idx_p2p_attachments_owner ON p2p_attachments(owner_account_id, created_at_ms);
