-- ==============================================================================
-- FEATURE: branch (Branches of the restaurant chain)
-- ==============================================================================

CREATE TABLE branches (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name VARCHAR(100) NOT NULL UNIQUE,
  code VARCHAR(20) NOT NULL UNIQUE,
  address VARCHAR(255) NOT NULL,
  phone_number VARCHAR(20),
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TRIGGER update_branches_modtime
    BEFORE UPDATE ON branches
    FOR EACH ROW
    EXECUTE FUNCTION update_modified_column();

CREATE INDEX idx_branches_code ON branches(code);

-- ==============================================================================
-- LINK: Link customer_orders to branch
-- ==============================================================================

ALTER TABLE customer_orders
  ADD COLUMN branch_id BIGINT REFERENCES branches(id) ON DELETE RESTRICT;

CREATE INDEX idx_customer_orders_branch ON customer_orders(branch_id);
