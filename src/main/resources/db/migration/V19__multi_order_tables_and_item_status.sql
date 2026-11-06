-- =================================================================================
-- V19: Multi-Order Tables, 10s Pending Cooldown Index, and Order Item Status Tracking
-- =================================================================================

-- 1. Add per-item attention status (PENDING, COMPLETED, MODIFIED, CANCELLED) to order_items
ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;

-- 2. Partial index for O(1) lookup of the latest PENDING order assigned to a table (10s cooldown rule)
CREATE INDEX IF NOT EXISTS idx_customer_orders_table_pending_created
    ON customer_orders(branch_id, table_id, created_at DESC)
    WHERE status = 'PENDING' AND table_id IS NOT NULL;

-- 3. Partial index for fast verification of remaining active (non-PAID, non-CANCELLED) orders on a table
CREATE INDEX IF NOT EXISTS idx_customer_orders_table_active
    ON customer_orders(branch_id, table_id)
    WHERE status NOT IN ('PAID', 'CANCELLED') AND table_id IS NOT NULL;

-- 4. Widen idempotency_key to VARCHAR(128) and add composite index for branch-scoped idempotency key resolution
ALTER TABLE customer_orders
    ALTER COLUMN idempotency_key TYPE VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_customer_orders_branch_idempotency
    ON customer_orders(branch_id, idempotency_key);
