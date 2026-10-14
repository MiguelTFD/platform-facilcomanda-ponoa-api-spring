-- ==============================================================================
-- FEATURE: Order Cancellation Audit Fields
-- ==============================================================================

ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS cancelled_by UUID REFERENCES app_users(id) ON DELETE SET NULL;
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS cancellation_reason VARCHAR(255);
ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_customer_orders_status ON customer_orders(branch_id, status);
