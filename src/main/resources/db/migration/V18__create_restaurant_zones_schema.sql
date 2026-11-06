-- ==============================================================================
-- FEATURE: Restaurant Zones (Intermediate Layer Between Floors and Tables)
-- ==============================================================================

-- 1. Tabla de Zonas por Piso y Sucursal (ej. Juegos, Karaoke, Terraza VIP, Salon Principal)
CREATE TABLE IF NOT EXISTS restaurant_zones (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    branch_id BIGINT NOT NULL REFERENCES branches(id) ON DELETE RESTRICT,
    floor_id BIGINT NOT NULL REFERENCES restaurant_floors(id) ON DELETE RESTRICT,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_restaurant_zones_branch_floor
    ON restaurant_zones(branch_id, floor_id)
    WHERE is_active = TRUE;

-- Índice único parcial: permite recrear una zona con el mismo nombre en un piso si la anterior fue desactivada
CREATE UNIQUE INDEX IF NOT EXISTS uk_zone_name_per_floor
    ON restaurant_zones(floor_id, name)
    WHERE is_active = TRUE;

-- 2. Relación de Mesas hacia Zonas (una zona tiene N mesas; una mesa pertenece a una zona)
ALTER TABLE restaurant_tables
    ADD COLUMN IF NOT EXISTS zone_id BIGINT REFERENCES restaurant_zones(id) ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS idx_restaurant_tables_zone_id
    ON restaurant_tables(branch_id, zone_id)
    WHERE is_active = TRUE;
