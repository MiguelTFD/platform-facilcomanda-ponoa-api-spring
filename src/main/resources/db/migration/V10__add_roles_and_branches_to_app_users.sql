-- ==============================================================================
-- FEATURE: Multi-tier User Hierarchy (EMPRESA -> ADMINISTRADOR -> STAFF)
-- ==============================================================================

ALTER TABLE app_users ADD COLUMN IF NOT EXISTS email VARCHAR(150);
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS role VARCHAR(50) NOT NULL DEFAULT 'MESERO';
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS branch_id BIGINT REFERENCES branches(id) ON DELETE RESTRICT;
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES app_users(id) ON DELETE SET NULL;
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS uk_app_users_email ON app_users(email) WHERE email IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_app_users_branch_id ON app_users(branch_id);
CREATE INDEX IF NOT EXISTS idx_app_users_role ON app_users(role);
CREATE INDEX IF NOT EXISTS idx_app_users_created_by ON app_users(created_by);
