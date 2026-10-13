-- ==============================================================================
-- FEATURE: Multi-Station Kitchen Routing & Targeted Cash Register Printing
-- ==============================================================================

-- 1. Área de producción en productos del catálogo (ej. COCINA, PARRILLA, BARRA, HORNO)
ALTER TABLE products ADD COLUMN production_area VARCHAR(50) DEFAULT 'COCINA';
CREATE INDEX idx_products_production_area ON products(production_area);

-- 2. Terminal de caja física en dispositivos de impresión (ej. CAJA-01, CAJA-02)
ALTER TABLE printer_devices ADD COLUMN terminal_code VARCHAR(50);
CREATE UNIQUE INDEX uk_printer_device_terminal 
    ON printer_devices(branch_id, terminal_code) 
    WHERE terminal_code IS NOT NULL;

-- 3. Área de producción en rutas de impresión para filtrado de comanda
ALTER TABLE printer_routes ADD COLUMN production_area VARCHAR(50);
ALTER TABLE printer_routes DROP CONSTRAINT uk_printer_route;
ALTER TABLE printer_routes ADD CONSTRAINT uk_printer_route 
    UNIQUE NULLS NOT DISTINCT (branch_id, printer_id, document_type, production_area);

-- 4. Llave de idempotencia en trabajos de impresión para prevenir impresiones duplicadas
ALTER TABLE print_jobs ADD COLUMN idempotency_key VARCHAR(100);
CREATE UNIQUE INDEX uk_print_job_idempotency 
    ON print_jobs(branch_id, order_id, printer_id, document_type, idempotency_key) 
    WHERE idempotency_key IS NOT NULL;
