-- ==============================================================================
-- FEATURE: Cash Register & Shift Management (Apertura, Arqueo y Cierre de Caja)
-- ==============================================================================

-- 1. Cajas Físicas / Puntos de Venta por Sucursal
CREATE TABLE cash_registers (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  branch_id BIGINT NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(255),
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_cash_register_branch_name UNIQUE (branch_id, name)
);

CREATE INDEX idx_cash_registers_branch ON cash_registers(branch_id);

-- 2. Turnos / Sesiones de Caja (Apertura y Cierre con Arqueo)
CREATE TABLE cash_shifts (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  branch_id BIGINT NOT NULL REFERENCES branches(id) ON DELETE RESTRICT,
  cash_register_id BIGINT NOT NULL REFERENCES cash_registers(id) ON DELETE RESTRICT,
  cashier_user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE RESTRICT,
  cashier_name VARCHAR(150),
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN', -- 'OPEN', 'CLOSED'
  opening_amount NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
  opened_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  closed_at TIMESTAMP WITH TIME ZONE,
  closing_cash_counted NUMERIC(12, 2),
  expected_cash_amount NUMERIC(12, 2),
  cash_difference NUMERIC(12, 2),
  total_sales_amount NUMERIC(12, 2) DEFAULT 0.00,
  notes VARCHAR(500),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Garantía estricta anti-race condition a nivel de motor: Solo 1 turno OPEN por caja y por cajero
CREATE UNIQUE INDEX uk_open_shift_per_register ON cash_shifts(cash_register_id) WHERE status = 'OPEN';
CREATE UNIQUE INDEX uk_open_shift_per_cashier ON cash_shifts(cashier_user_id) WHERE status = 'OPEN';

CREATE INDEX idx_cash_shifts_branch_status ON cash_shifts(branch_id, status);

-- 3. Movimientos Manuales de Caja (Ingresos / Gastos Menores)
CREATE TABLE cash_movements (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  cash_shift_id BIGINT NOT NULL REFERENCES cash_shifts(id) ON DELETE CASCADE,
  type VARCHAR(20) NOT NULL, -- 'INCOME', 'EXPENSE'
  amount NUMERIC(12, 2) NOT NULL,
  reason VARCHAR(255) NOT NULL,
  performed_by_user_id UUID REFERENCES app_users(id) ON DELETE SET NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_cash_movements_shift ON cash_movements(cash_shift_id);

-- 4. Vincular facturas/invoices con el turno de caja
ALTER TABLE invoices ADD COLUMN cash_shift_id BIGINT REFERENCES cash_shifts(id) ON DELETE RESTRICT;
CREATE INDEX idx_invoices_cash_shift ON invoices(cash_shift_id);
