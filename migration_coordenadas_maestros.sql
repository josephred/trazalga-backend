-- =========================================================================
-- MIGRACIÓN: Coordenadas Geográficas en Maestros (T9.1)
-- Ref: PLAN_ANTIGRAVITY_refinamiento_25sep.md / 09_ORIGEN_VS_GPS.md
-- =========================================================================

-- 1. Coordenadas en tabla caleta
SET @col_cal_lat = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'caleta' AND COLUMN_NAME = 'latitud');
SET @s1 = IF(@col_cal_lat = 0,
  'ALTER TABLE caleta ADD COLUMN latitud DOUBLE NULL, ADD COLUMN longitud DOUBLE NULL',
  'SELECT 1');
PREPARE stmt FROM @s1; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. Centroide en tabla amerb
SET @col_am_lat = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amerb' AND COLUMN_NAME = 'latitud');
SET @s2 = IF(@col_am_lat = 0,
  'ALTER TABLE amerb ADD COLUMN latitud DOUBLE NULL, ADD COLUMN longitud DOUBLE NULL',
  'SELECT 1');
PREPARE stmt FROM @s2; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. Fallback inicial: heredar coordenadas del varadero asociado cuando caleta no tenga coordenadas directas
UPDATE caleta cal
JOIN varadero v ON cal.varadero_id = v.id
SET cal.latitud = v.latitud, cal.longitud = v.longitud
WHERE cal.latitud IS NULL AND v.latitud IS NOT NULL;
