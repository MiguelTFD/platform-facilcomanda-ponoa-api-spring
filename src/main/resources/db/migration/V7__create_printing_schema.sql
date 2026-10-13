-- ==============================================================================
-- FEATURE: printing (Automatic Printing, N Printers per Branch)
-- ==============================================================================

-- 1. Dispositivos de impresión configurados por sucursal
CREATE TABLE printer_devices (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  branch_id BIGINT NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
  name VARCHAR(100) NOT NULL,
  ip_address VARCHAR(45) NOT NULL,
  port INT NOT NULL DEFAULT 9100,
  connection_type VARCHAR(20) NOT NULL DEFAULT 'TCP',
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_printer_branch_id UNIQUE (branch_id, id)
);

CREATE INDEX idx_printer_devices_branch ON printer_devices(branch_id);

CREATE TRIGGER update_printer_devices_modtime
    BEFORE UPDATE ON printer_devices
    FOR EACH ROW
    EXECUTE FUNCTION update_modified_column();

-- 2. Rutas de impresión: qué impresora imprime qué tipo de documento
CREATE TABLE printer_routes (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  branch_id BIGINT NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
  printer_id BIGINT NOT NULL,
  document_type VARCHAR(50) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_printer_routes_device FOREIGN KEY (branch_id, printer_id)
      REFERENCES printer_devices(branch_id, id) ON DELETE CASCADE,
  CONSTRAINT uk_printer_route UNIQUE (branch_id, printer_id, document_type)
);

CREATE INDEX idx_printer_routes_branch_doc ON printer_routes(branch_id, document_type);

-- 3. Trabajos de impresión encolados con aislamiento estricto por sucursal
CREATE TABLE print_jobs (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  branch_id BIGINT NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
  printer_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL REFERENCES customer_orders(id) ON DELETE CASCADE,
  document_type VARCHAR(50) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  error_message TEXT,
  retry_count INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_print_jobs_device FOREIGN KEY (branch_id, printer_id)
      REFERENCES printer_devices(branch_id, id) ON DELETE RESTRICT
);

CREATE INDEX idx_print_jobs_branch_status ON print_jobs(branch_id, status);
CREATE INDEX idx_print_jobs_order ON print_jobs(order_id);

CREATE TRIGGER update_print_jobs_modtime
    BEFORE UPDATE ON print_jobs
    FOR EACH ROW
    EXECUTE FUNCTION update_modified_column();
