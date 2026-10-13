-- ==============================================================================
-- FEATURE: Restaurant Floors & Tables (Layout Management)
-- ==============================================================================

-- 1. Pisos / Salones por sucursal
CREATE TABLE restaurant_floors (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  branch_id BIGINT NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(255),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_floor_name_per_branch UNIQUE (branch_id, name)
);

CREATE INDEX idx_restaurant_floors_branch_id ON restaurant_floors(branch_id);

-- 2. Mesas de restaurante
CREATE TABLE restaurant_tables (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  branch_id BIGINT NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
  floor_id BIGINT NOT NULL REFERENCES restaurant_floors(id) ON DELETE RESTRICT,
  name VARCHAR(50) NOT NULL,
  description VARCHAR(255),
  state VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
  chairs INT NOT NULL DEFAULT 4,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_table_name_per_floor UNIQUE (floor_id, name)
);

CREATE INDEX idx_restaurant_tables_branch_floor ON restaurant_tables(branch_id, floor_id);
CREATE INDEX idx_restaurant_tables_state ON restaurant_tables(state);

-- 3. Enlazar comanda con mesa, modalidad y notas generales
ALTER TABLE customer_orders ADD COLUMN table_id BIGINT REFERENCES restaurant_tables(id) ON DELETE SET NULL;
ALTER TABLE customer_orders ADD COLUMN order_type VARCHAR(30) NOT NULL DEFAULT 'DINE_IN';
ALTER TABLE customer_orders ADD COLUMN notes VARCHAR(500);

CREATE INDEX idx_customer_orders_table ON customer_orders(branch_id, table_id);

-- 4. Comentarios específicos por plato en el detalle de la comanda
ALTER TABLE order_items ADD COLUMN comments VARCHAR(255);
