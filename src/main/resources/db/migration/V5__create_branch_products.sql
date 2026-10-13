-- ==============================================================================
-- FEATURE: catalog (Per-Branch Pricing and Availability)
-- ==============================================================================

CREATE TABLE branch_products (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  branch_id BIGINT NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
  sku_id BIGINT NOT NULL REFERENCES sku_presentations(id) ON DELETE CASCADE,
  price NUMERIC(10,2) NOT NULL CHECK (price >= 0),
  is_available BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_branch_product_sku UNIQUE (branch_id, sku_id)
);

CREATE INDEX idx_branch_products_branch ON branch_products(branch_id);
CREATE INDEX idx_branch_products_sku ON branch_products(sku_id);

CREATE TRIGGER update_branch_products_modtime
    BEFORE UPDATE ON branch_products
    FOR EACH ROW
    EXECUTE FUNCTION update_modified_column();
