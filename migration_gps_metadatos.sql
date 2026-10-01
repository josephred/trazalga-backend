-- =========================================================================
-- MIGRACIÓN: Metadatos de Captura GPS en Declaraciones (T9.2)
-- Ref: PLAN_ANTIGRAVITY_refinamiento_25sep.md / 09_ORIGEN_VS_GPS.md
-- =========================================================================

-- 1. declaracion_recolector
SET @col_rec = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_recolector' AND COLUMN_NAME = 'precision_gps_m');
SET @s_rec = IF(@col_rec = 0,
  'ALTER TABLE declaracion_recolector ADD COLUMN precision_gps_m DOUBLE NULL, ADD COLUMN gps_capturado_en DATETIME NULL, ADD COLUMN envio_offline BOOLEAN NULL',
  'SELECT 1');
PREPARE stmt FROM @s_rec; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. declaracion_armador
SET @col_arm = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_armador' AND COLUMN_NAME = 'precision_gps_m');
SET @s_arm = IF(@col_arm = 0,
  'ALTER TABLE declaracion_armador ADD COLUMN precision_gps_m DOUBLE NULL, ADD COLUMN gps_capturado_en DATETIME NULL, ADD COLUMN envio_offline BOOLEAN NULL',
  'SELECT 1');
PREPARE stmt FROM @s_arm; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. declaracion_area
SET @col_area = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_area' AND COLUMN_NAME = 'precision_gps_m');
SET @s_area = IF(@col_area = 0,
  'ALTER TABLE declaracion_area ADD COLUMN precision_gps_m DOUBLE NULL, ADD COLUMN gps_capturado_en DATETIME NULL, ADD COLUMN envio_offline BOOLEAN NULL',
  'SELECT 1');
PREPARE stmt FROM @s_area; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4. declaracion_comercializador
SET @col_com = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_comercializador' AND COLUMN_NAME = 'precision_gps_m');
SET @s_com = IF(@col_com = 0,
  'ALTER TABLE declaracion_comercializador ADD COLUMN precision_gps_m DOUBLE NULL, ADD COLUMN gps_capturado_en DATETIME NULL, ADD COLUMN envio_offline BOOLEAN NULL',
  'SELECT 1');
PREPARE stmt FROM @s_com; EXECUTE stmt; DEALLOCATE PREPARE stmt;
