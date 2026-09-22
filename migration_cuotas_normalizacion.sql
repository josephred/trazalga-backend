-- ============================================================================
-- MIGRACIÓN: NORMALIZACIÓN DE CUOTAS DE EXTRACCIÓN (R3.1)
-- Normaliza nivel_agregacion, metrica y estado para evitar fallbacks nacionales
-- ============================================================================

-- 1. Normalizar métrica y estado en cuotas existentes
UPDATE cuota_extraccion
SET metrica = 'CAPTURA'
WHERE metrica IS NULL OR TRIM(metrica) = '';

UPDATE cuota_extraccion
SET estado = 'ABIERTA'
WHERE estado IS NULL OR TRIM(estado) = '';

-- 2. Deducir nivel_agregacion según claves foráneas existentes
-- Nivel INDIVIDUAL si tiene usuario asignado
UPDATE cuota_extraccion
SET nivel_agregacion = 'INDIVIDUAL'
WHERE (nivel_agregacion IS NULL OR TRIM(nivel_agregacion) = '')
  AND usuario_id IS NOT NULL;

-- Nivel COMUNA si tiene comuna asignada
UPDATE cuota_extraccion
SET nivel_agregacion = 'COMUNA'
WHERE (nivel_agregacion IS NULL OR TRIM(nivel_agregacion) = '')
  AND comuna_id IS NOT NULL;

-- Nivel PROVINCIA si tiene provincia asignada
UPDATE cuota_extraccion
SET nivel_agregacion = 'PROVINCIA'
WHERE (nivel_agregacion IS NULL OR TRIM(nivel_agregacion) = '')
  AND provincia_id IS NOT NULL;

-- Nivel REGION si tiene region asignada
UPDATE cuota_extraccion
SET nivel_agregacion = 'REGION'
WHERE (nivel_agregacion IS NULL OR TRIM(nivel_agregacion) = '')
  AND region_id IS NOT NULL;

-- Nivel MACROZONA si tiene macrozona asignada
UPDATE cuota_extraccion
SET nivel_agregacion = 'MACROZONA'
WHERE (nivel_agregacion IS NULL OR TRIM(nivel_agregacion) = '')
  AND macrozona_id IS NOT NULL;

-- 3. Desactivar cualquier cuota que no tenga alcance territorial identificable
UPDATE cuota_extraccion
SET activo = FALSE
WHERE (nivel_agregacion IS NULL OR TRIM(nivel_agregacion) = '')
  AND usuario_id IS NULL
  AND comuna_id IS NULL
  AND provincia_id IS NULL
  AND region_id IS NULL
  AND macrozona_id IS NULL;

-- Si alguna quedó aún activa sin nivel, asignar COMUNA por defecto
UPDATE cuota_extraccion
SET nivel_agregacion = 'COMUNA'
WHERE nivel_agregacion IS NULL OR TRIM(nivel_agregacion) = '';
