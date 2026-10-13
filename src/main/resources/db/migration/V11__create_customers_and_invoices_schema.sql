-- ==============================================================================
-- FEATURE: Invoicing & Global Customers (DNI/RUC Lookup, Payments & Invoices)
-- ==============================================================================

-- 1. Tabla Global de Clientes (compartida entre sucursales)
CREATE TABLE customers (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name VARCHAR(200) NOT NULL,
  identity_document_type VARCHAR(20) NOT NULL, -- 'DNI', 'RUC', 'CE', 'PASAPORTE'
  identity_document_number VARCHAR(20) NOT NULL,
  phone_number VARCHAR(30),
  address VARCHAR(255),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_customer_doc UNIQUE (identity_document_type, identity_document_number)
);

CREATE INDEX idx_customers_doc ON customers(identity_document_type, identity_document_number);

-- 2. Tabla de Series y Correlativos Concurrente por Sucursal
CREATE TABLE invoice_series (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  branch_id BIGINT NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
  invoice_type VARCHAR(20) NOT NULL, -- 'BOLETA', 'FACTURA', 'TICKET'
  series VARCHAR(10) NOT NULL,       -- 'B001', 'F001', 'T001'
  current_number BIGINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_invoice_series UNIQUE (branch_id, invoice_type, series)
);

CREATE INDEX idx_invoice_series_branch_type ON invoice_series(branch_id, invoice_type);

-- 3. Tabla de Comprobantes de Pago (Invoices)
CREATE TABLE invoices (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  branch_id BIGINT NOT NULL REFERENCES branches(id) ON DELETE RESTRICT,
  order_id BIGINT NOT NULL REFERENCES customer_orders(id) ON DELETE RESTRICT,
  customer_id BIGINT REFERENCES customers(id) ON DELETE RESTRICT,
  invoice_number VARCHAR(80) NOT NULL, -- Ej: 'B001-00000001', 'F001-00000001'
  invoice_type VARCHAR(50) NOT NULL,   -- 'BOLETA', 'FACTURA', 'TICKET'
  order_total NUMERIC(12, 2) NOT NULL,
  amount_paid NUMERIC(12, 2) NOT NULL,
  change_amount NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
  cashier_user_id UUID REFERENCES app_users(id) ON DELETE SET NULL,
  cashier_email VARCHAR(150),
  restaurant_table_id BIGINT REFERENCES restaurant_tables(id) ON DELETE SET NULL,
  restaurant_table_name VARCHAR(100),
  notes VARCHAR(500),
  paid_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_invoice_order UNIQUE (order_id),
  CONSTRAINT uk_invoice_branch_number UNIQUE (branch_id, invoice_number)
);

CREATE INDEX idx_invoices_branch ON invoices(branch_id);
CREATE INDEX idx_invoices_customer ON invoices(customer_id);
CREATE INDEX idx_invoices_order ON invoices(order_id);

-- 4. Métodos de Pago Desglosados (Split Payment)
CREATE TABLE invoice_payments (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  invoice_id BIGINT NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
  method VARCHAR(30) NOT NULL, -- 'EFECTIVO', 'YAPE', 'PLIN', 'TARJETA', 'CREDITO'
  amount NUMERIC(12, 2) NOT NULL,
  reference VARCHAR(255),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_invoice_payments_invoice ON invoice_payments(invoice_id);
