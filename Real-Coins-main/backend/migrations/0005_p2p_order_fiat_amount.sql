-- Store the exact ETB amount agreed for each P2P order.
ALTER TABLE p2p_orders ADD COLUMN fiat_order_amount REAL NOT NULL DEFAULT 0;
