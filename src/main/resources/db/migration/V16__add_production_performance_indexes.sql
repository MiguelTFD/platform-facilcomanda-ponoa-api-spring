-- ==============================================================================
-- FEATURE: Production Performance Optimization Indexes
-- ==============================================================================

-- 1. Optimización para reportes de ventas por rango de fechas y sucursal
CREATE INDEX IF NOT EXISTS idx_invoices_branch_paid_at ON invoices(branch_id, paid_at);
CREATE INDEX IF NOT EXISTS idx_invoices_paid_at ON invoices(paid_at);

-- 2. Optimización para consultas agregadas de productos más vendidos
CREATE INDEX IF NOT EXISTS idx_customer_orders_branch_status_created 
    ON customer_orders(branch_id, status, created_at);

-- 3. Índice parcial para órdenes activas en mesa (búsqueda ultra rápida de pedidos abiertos)
CREATE INDEX IF NOT EXISTS idx_customer_orders_table_active 
    ON customer_orders(branch_id, table_id) 
    WHERE status NOT IN ('PAID', 'CANCELLED');

-- 4. Optimización de joins entre items de orden y SKU para rankings de ventas
CREATE INDEX IF NOT EXISTS idx_order_items_sku_order ON order_items(sku_id, order_id);

-- 5. Optimización para listado de gastos operativos por rango de fechas
CREATE INDEX IF NOT EXISTS idx_cash_movements_type_created ON cash_movements(type, created_at);

-- 6. Optimización para verificación de cooldown y jobs recientes de impresión
CREATE INDEX IF NOT EXISTS idx_print_jobs_branch_order_doc 
    ON print_jobs(branch_id, order_id, document_type);
