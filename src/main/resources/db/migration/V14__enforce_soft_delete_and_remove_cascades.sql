-- ==============================================================================
-- FEATURE: Database Soft-Delete Fortification & Cascade Removal
-- ==============================================================================

-- 1. Convert all existing ON DELETE CASCADE foreign keys to ON DELETE RESTRICT
-- This prevents any cascading hard deletes across master and operational tables.
DO $$
DECLARE
    r RECORD;
    new_def TEXT;
BEGIN
    FOR r IN (
        SELECT c.oid,
               c.conname,
               conrelid::regclass::text AS tablename,
               pg_get_constraintdef(c.oid) AS def
        FROM pg_constraint c
        JOIN pg_namespace n ON n.oid = c.connamespace
        WHERE c.contype = 'f'
          AND c.confdeltype = 'c'
          AND n.nspname = 'public'
    ) LOOP
        new_def := replace(r.def, 'ON DELETE CASCADE', 'ON DELETE RESTRICT');
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT IF EXISTS %I', r.tablename, r.conname);
        EXECUTE format('ALTER TABLE %s ADD CONSTRAINT %I %s', r.tablename, r.conname, new_def);
    END LOOP;
END $$;

-- 2. Add Soft-Delete columns (is_active and deleted_at) to master tables

-- Products
ALTER TABLE products ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE products ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX IF NOT EXISTS idx_products_active ON products(is_active);

-- SKU Presentations
ALTER TABLE sku_presentations ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE sku_presentations ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX IF NOT EXISTS idx_sku_presentations_active ON sku_presentations(is_active);

-- Categories
ALTER TABLE categories ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE categories ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX IF NOT EXISTS idx_categories_active ON categories(is_active);

-- Restaurant Floors
ALTER TABLE restaurant_floors ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE restaurant_floors ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX IF NOT EXISTS idx_restaurant_floors_active ON restaurant_floors(branch_id, is_active);

-- Restaurant Tables
ALTER TABLE restaurant_tables ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE restaurant_tables ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX IF NOT EXISTS idx_restaurant_tables_active ON restaurant_tables(branch_id, is_active);

-- Customers
ALTER TABLE customers ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX IF NOT EXISTS idx_customers_active ON customers(is_active);

-- 3. Adjust unique constraints to partial unique indexes for soft-deleted entities
-- This allows recreating entities with the same unique name/code if the previous one was soft-deleted.

-- Restaurant Floors: Unique name per branch for active floors only
ALTER TABLE restaurant_floors DROP CONSTRAINT IF EXISTS uk_floor_name_per_branch;
DROP INDEX IF EXISTS uk_floor_name_per_branch;
CREATE UNIQUE INDEX IF NOT EXISTS uk_floor_name_per_branch 
    ON restaurant_floors(branch_id, name) 
    WHERE is_active = TRUE;

-- Restaurant Tables: Unique name per floor for active tables only
ALTER TABLE restaurant_tables DROP CONSTRAINT IF EXISTS uk_table_name_per_floor;
DROP INDEX IF EXISTS uk_table_name_per_floor;
CREATE UNIQUE INDEX IF NOT EXISTS uk_table_name_per_floor 
    ON restaurant_tables(floor_id, name) 
    WHERE is_active = TRUE;
