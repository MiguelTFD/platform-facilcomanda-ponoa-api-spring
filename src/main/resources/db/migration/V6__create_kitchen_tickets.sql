-- ==============================================================================
-- FEATURE: kitchen (Live Kitchen Screen Tickets per Branch)
-- ==============================================================================

CREATE TABLE kitchen_tickets (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  branch_id BIGINT NOT NULL REFERENCES branches(id) ON DELETE RESTRICT,
  order_id BIGINT NOT NULL REFERENCES customer_orders(id) ON DELETE CASCADE,
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  notes TEXT,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_kitchen_order UNIQUE (order_id)
);

CREATE INDEX idx_kitchen_tickets_branch ON kitchen_tickets(branch_id);
CREATE INDEX idx_kitchen_tickets_status ON kitchen_tickets(status);

CREATE TRIGGER update_kitchen_tickets_modtime
    BEFORE UPDATE ON kitchen_tickets
    FOR EACH ROW
    EXECUTE FUNCTION update_modified_column();
