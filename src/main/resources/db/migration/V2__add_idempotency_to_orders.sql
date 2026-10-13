ALTER TABLE customer_orders ADD COLUMN idempotency_key VARCHAR(36);
ALTER TABLE customer_orders ADD CONSTRAINT uk_order_idempotency UNIQUE (idempotency_key);
