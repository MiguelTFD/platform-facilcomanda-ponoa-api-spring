-- ==============================================================================
-- FEATURE: userprofile (Identity and Business Data)
-- ==============================================================================

CREATE TABLE app_users (
  id UUID PRIMARY KEY, -- Provided by Keycloak UUID
  first_name VARCHAR(100) NOT NULL,
  last_name VARCHAR(100) NOT NULL,
  phone_number VARCHAR(20),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE identity_documents (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  app_user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  document_type VARCHAR(10) NOT NULL,
  document_number VARCHAR(20) NOT NULL,
  CONSTRAINT uk_identity_document UNIQUE (document_type, document_number)
);

CREATE TABLE addresses (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  app_user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  street VARCHAR(255) NOT NULL,
  city VARCHAR(100) NOT NULL,
  district VARCHAR(100) NOT NULL,
  is_default BOOLEAN DEFAULT FALSE
);

-- ==============================================================================
-- FEATURE: catalog (E-commerce Catalog)
-- ==============================================================================

CREATE TABLE brands (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE categories (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE products (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  brand_id BIGINT REFERENCES brands(id) ON DELETE RESTRICT,
  name VARCHAR(200) NOT NULL,
  base_description TEXT
);

CREATE TABLE product_categories (
  product_id BIGINT REFERENCES products(id) ON DELETE CASCADE,
  category_id BIGINT REFERENCES categories(id) ON DELETE CASCADE,
  PRIMARY KEY (product_id, category_id)
);

CREATE TABLE sku_presentations (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  sku_code VARCHAR(50) NOT NULL UNIQUE,
  presentation_name VARCHAR(100) NOT NULL,
  current_price NUMERIC(10,2) NOT NULL CHECK (current_price >= 0)
);

CREATE TABLE product_pictures (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  image_url VARCHAR(255) NOT NULL,
  is_primary BOOLEAN DEFAULT FALSE
);

-- ==============================================================================
-- FEATURE: inventory (ERP Stock Management)
-- ==============================================================================

CREATE TABLE stock_levels (
  sku_id BIGINT PRIMARY KEY REFERENCES sku_presentations(id) ON DELETE CASCADE,
  quantity INT NOT NULL DEFAULT 0 CHECK (quantity >= 0),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ==============================================================================
-- FEATURE: sales (Purchase Orders)
-- ==============================================================================

CREATE TABLE customer_orders (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  app_user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
  total_amount NUMERIC(10,2) NOT NULL CHECK (total_amount >= 0),
  status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE order_items (
  order_id BIGINT REFERENCES customer_orders(id) ON DELETE CASCADE,
  sku_id BIGINT REFERENCES sku_presentations(id) ON DELETE RESTRICT,
  quantity INT NOT NULL CHECK (quantity > 0),
  unit_price_at_purchase NUMERIC(10,2) NOT NULL CHECK (unit_price_at_purchase >= 0),
  PRIMARY KEY (order_id, sku_id)
);

-- ==============================================================================
-- FEATURE: billing (Electronic Invoicing - SUNAT)
-- ==============================================================================

CREATE TABLE invoices (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  order_id BIGINT NOT NULL REFERENCES customer_orders(id) ON DELETE RESTRICT, 
  
  invoice_type VARCHAR(2) NOT NULL,
  invoice_series VARCHAR(4) NOT NULL,
  invoice_number INT NOT NULL,
  
  sunat_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  xml_hash VARCHAR(255),
  cdr_notes TEXT,
  
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_invoice_series_number UNIQUE (invoice_series, invoice_number)
);

-- ==============================================================================
-- INDEXES
-- ==============================================================================

CREATE INDEX idx_sku_product ON sku_presentations(product_id);
CREATE INDEX idx_order_user ON customer_orders(app_user_id);
CREATE INDEX idx_invoice_order ON invoices(order_id);

-- ==============================================================================
-- AUDITORÍA: Triggers de actualización de estado (updated_at)
-- ==============================================================================

CREATE OR REPLACE FUNCTION update_modified_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE 'plpgsql';

CREATE TRIGGER update_customer_orders_modtime
    BEFORE UPDATE ON customer_orders
    FOR EACH ROW
    EXECUTE FUNCTION update_modified_column();

CREATE TRIGGER update_invoices_modtime
    BEFORE UPDATE ON invoices
    FOR EACH ROW
    EXECUTE FUNCTION update_modified_column();

CREATE TRIGGER update_stock_levels_modtime
    BEFORE UPDATE ON stock_levels
    FOR EACH ROW
    EXECUTE FUNCTION update_modified_column();
