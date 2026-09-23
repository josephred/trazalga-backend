-- ============================================================================
-- MIGRACIÓN: NORMALIZACIÓN DE CUOTAS DE EXTRACCIÓN (R3.1, H2, H3, H4, H5)
-- Normaliza nivel_agregacion, metrica y estado para evitar fallbacks nacionales
-- ============================================================================

-- 1. Normalizar métrica y estado en cuotas existentes
UPDATE cuota_extraccion
SET metrica = 'CAPTURA'
WHERE metrica IS NULL OR TRIM(metrica) = '';

UPDATE cuota_extraccion
SET estado = 'ABIERTA'
WHERE estado IS NULL OR TRIM(estado) = '';

-- 2. Deducir nivel_agregacion según claves foráneas existentes (H2: AMERB incluido)
-- Nivel INDIVIDUAL si tiene usuario asignado
UPDATE cuota_extraccion
SET nivel_agregacion = 'INDIVIDUAL'
WHERE (nivel_agregacion IS NULL OR TRIM(nivel_agregacion) = '')
  AND usuario_id IS NOT NULL;

-- Nivel AMERB si tiene amerb_id asignado (H2: Precedencia antes de comuna)
UPDATE cuota_extraccion
SET nivel_agregacion = 'AMERB'
WHERE (nivel_agregacion IS NULL OR TRIM(nivel_agregacion) = '')
  AND amerb_id IS NOT NULL;

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

-- 3. Desactivar cualquier cuota que no tenga alcance territorial ni personal identificable
-- (H2: amerb_id IS NULL agregado para NO desactivar cuotas legítimas de AMERB)
UPDATE cuota_extraccion
SET activo = FALSE
WHERE (nivel_agregacion IS NULL OR TRIM(nivel_agregacion) = '')
  AND usuario_id IS NULL
  AND amerb_id IS NULL
  AND comuna_id IS NULL
  AND provincia_id IS NULL
  AND region_id IS NULL
  AND macrozona_id IS NULL;

-- (H3: Corregido para no reabrir ni estampar falsamente COMUNA en cuotas inactivas sin comuna_id)
-- Solo se asigna COMUNA a cuotas que efectivamente tengan comuna_id y permanezcan activas
UPDATE cuota_extraccion
SET nivel_agregacion = 'COMUNA'
WHERE activo = TRUE
  AND comuna_id IS NOT NULL
  AND (nivel_agregacion IS NULL OR TRIM(nivel_agregacion) = '');

-- Para cuotas que quedaron desactivadas sin alcance, asignar nivel 'IRRESOLUBLE' para auditoría
UPDATE cuota_extraccion
SET nivel_agregacion = 'IRRESOLUBLE'
WHERE activo = FALSE
  AND (nivel_agregacion IS NULL OR TRIM(nivel_agregacion) = '');

-- 4. Guardias de Base de Datos: NOT NULL con DEFAULTs (H5)
ALTER TABLE cuota_extraccion 
    MODIFY COLUMN nivel_agregacion VARCHAR(20) NOT NULL DEFAULT 'COMUNA',
    MODIFY COLUMN metrica VARCHAR(20) NOT NULL DEFAULT 'CAPTURA',
    MODIFY COLUMN estado VARCHAR(20) NOT NULL DEFAULT 'ABIERTA';

-- 5. Guardias de Base de Datos: Constraints CHECK (H5, idempotente)
SET @chk_ce_na = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cuota_extraccion' AND CONSTRAINT_NAME = 'chk_cuota_nivel_agregacion');

SET @sql_chk_ce_na = IF(@chk_ce_na = 0,
    'ALTER TABLE cuota_extraccion ADD CONSTRAINT chk_cuota_nivel_agregacion CHECK (nivel_agregacion IN ("INDIVIDUAL", "AMERB", "COMUNA", "PROVINCIA", "REGION", "MACROZONA", "IRRESOLUBLE"));',
    'SELECT "chk_cuota_nivel_agregacion ya existe";');

PREPARE stmt_chk_na FROM @sql_chk_ce_na;
EXECUTE stmt_chk_na;
DEALLOCATE PREPARE stmt_chk_na;

SET @chk_ce_met = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cuota_extraccion' AND CONSTRAINT_NAME = 'chk_cuota_metrica');

SET @sql_chk_ce_met = IF(@chk_ce_met = 0,
    'ALTER TABLE cuota_extraccion ADD CONSTRAINT chk_cuota_metrica CHECK (metrica IN ("CAPTURA", "DESEMBARQUE"));',
    'SELECT "chk_cuota_metrica ya existe";');

PREPARE stmt_chk_met FROM @sql_chk_ce_met;
EXECUTE stmt_chk_met;
DEALLOCATE PREPARE stmt_chk_met;

SET @chk_ce_est = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cuota_extraccion' AND CONSTRAINT_NAME = 'chk_cuota_estado');

SET @sql_chk_ce_est = IF(@chk_ce_est = 0,
    'ALTER TABLE cuota_extraccion ADD CONSTRAINT chk_cuota_estado CHECK (estado IN ("ABIERTA", "CERRADA"));',
    'SELECT "chk_cuota_estado ya existe";');

PREPARE stmt_chk_est FROM @sql_chk_ce_est;
EXECUTE stmt_chk_est;
DEALLOCATE PREPARE stmt_chk_est;

-- ============================================================================
-- 6. INFORME DE REGULARIZACIÓN (H4)
-- Consulta de auditoría: lista cuotas desactivadas por falta de alcance territorial
-- ============================================================================
SELECT 
    id AS cuota_id,
    perfil,
    especie_id,
    limite_kg,
    periodo,
    activo,
    estado,
    modo_accion,
    nivel_agregacion,
    'DESACTIVADA: Sin identificador territorial (comuna/provincia/region/macrozona), usuario ni AMERB' AS motivo_regularizacion
FROM cuota_extraccion
WHERE activo = FALSE 
  AND usuario_id IS NULL
  AND amerb_id IS NULL
  AND comuna_id IS NULL
  AND provincia_id IS NULL
  AND region_id IS NULL
  AND macrozona_id IS NULL;
