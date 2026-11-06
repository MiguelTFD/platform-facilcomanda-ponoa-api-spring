-- ==============================================================================
-- FEATURE: Multi-Branch & Multi-Role Users, Non-Stockable SKUs, Individual Product Routing
-- ==============================================================================

-- 1. Relación muchos-a-muchos entre Usuarios y Sucursales de la organización
CREATE TABLE IF NOT EXISTS app_user_branches (
    app_user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    branch_id BIGINT NOT NULL REFERENCES branches(id) ON DELETE RESTRICT,
    PRIMARY KEY (app_user_id, branch_id)
);

CREATE INDEX IF NOT EXISTS idx_app_user_branches_branch_id ON app_user_branches(branch_id);

-- Backfill de sucursales existentes en app_users
INSERT INTO app_user_branches (app_user_id, branch_id)
SELECT id, branch_id FROM app_users WHERE branch_id IS NOT NULL
ON CONFLICT DO NOTHING;

-- 2. Relación uno-a-muchos entre Usuarios y Roles dentro de la organización
CREATE TABLE IF NOT EXISTS app_user_roles (
    app_user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL,
    PRIMARY KEY (app_user_id, role)
);

CREATE INDEX IF NOT EXISTS idx_app_user_roles_role ON app_user_roles(role);

-- Backfill de roles existentes en app_users
INSERT INTO app_user_roles (app_user_id, role)
SELECT id, role FROM app_users WHERE role IS NOT NULL
ON CONFLICT DO NOTHING;

-- 3. Soporte por SKU para definir si el producto requiere control de stock (ej. platos a pedido = FALSE)
ALTER TABLE sku_presentations ADD COLUMN IF NOT EXISTS requires_stock BOOLEAN NOT NULL DEFAULT TRUE;

-- 4. Soporte en rutas de impresión para enrutar opcionalmente un producto individual específico
ALTER TABLE printer_routes ADD COLUMN IF NOT EXISTS product_id BIGINT REFERENCES products(id) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS idx_printer_routes_branch_doc_product
    ON printer_routes(branch_id, document_type, product_id);
