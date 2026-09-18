-- Prevent two simultaneously open orders from claiming the same P2P ad.
CREATE UNIQUE INDEX IF NOT EXISTS idx_p2p_orders_one_open_per_ad
    ON p2p_orders(ad_id)
    WHERE status IN ('ESCROW_LOCKED', 'PAID', 'DISPUTED');
