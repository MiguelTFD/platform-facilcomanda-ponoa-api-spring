-- ==============================================================================
-- FEATURE: inventory (Multi-Branch Stock Levels)
-- ==============================================================================

DROP TRIGGER IF EXISTS update_stock_levels_modtime ON stock_levels;
DROP TABLE IF EXISTS stock_levels;

CREATE TABLE stock_levels (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  branch_id BIGINT NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
  sku_id BIGINT NOT NULL REFERENCES sku_presentations(id) ON DELETE CASCADE,
  quantity INT NOT NULL DEFAULT 0 CHECK (quantity >= 0),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_stock_branch_sku UNIQUE (branch_id, sku_id)
);

CREATE INDEX idx_stock_levels_branch ON stock_levels(branch_id);
CREATE INDEX idx_stock_levels_sku ON stock_levels(sku_id);

CREATE TRIGGER update_stock_levels_modtime
    BEFORE UPDATE ON stock_levels
    FOR EACH ROW
    EXECUTE FUNCTION update_modified_column();
