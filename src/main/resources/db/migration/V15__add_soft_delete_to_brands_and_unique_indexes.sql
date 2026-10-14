-- ==============================================================================
-- FEATURE: Brands & Categories Soft-Delete and Partial Unique Indexes
-- ==============================================================================

-- 1. Brands Soft-Delete & Description
ALTER TABLE brands ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE brands ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE brands ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX IF NOT EXISTS idx_brands_active ON brands(is_active);

-- Allow recreating a brand with the same name if the old one was soft-deleted
ALTER TABLE brands DROP CONSTRAINT IF EXISTS brands_name_key;
DROP INDEX IF EXISTS uk_brands_name_active;
CREATE UNIQUE INDEX IF NOT EXISTS uk_brands_name_active ON brands(name) WHERE is_active = TRUE;

-- 2. Categories Soft-Delete & Description
ALTER TABLE categories ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE categories DROP CONSTRAINT IF EXISTS categories_name_key;
DROP INDEX IF EXISTS uk_categories_name_active;
CREATE UNIQUE INDEX IF NOT EXISTS uk_categories_name_active ON categories(name) WHERE is_active = TRUE;
