-- =========================================================================
-- MIGRACIÓN: migration_coordenadas_fuente.sql
-- TrazAlga - Refinamiento 16-oct / TG.1 Carga verificable de coordenadas
-- 1. Agregar columnas fuente_coordenada, coordenada_actualizada_en y
--    coordenada_actualizada_por en caleta, varadero y amerb.
-- 2. Crear tabla coordenada_cambio_log para auditoría de cambios de GPS.
-- Idempotente conforme al estándar de ingeniería TrazAlga
-- =========================================================================

-- 1. Columnas en tabla caleta
SET @col1 = (SELECT COUNT(*) FROM information_schema.COLUMNS 
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'caleta' AND COLUMN_NAME = 'fuente_coordenada');
SET @s1 = IF(@col1 = 0, 'ALTER TABLE caleta ADD COLUMN fuente_coordenada VARCHAR(100) NULL', 'SELECT 1');
PREPARE stmt1 FROM @s1; EXECUTE stmt1; DEALLOCATE PREPARE stmt1;

SET @col2 = (SELECT COUNT(*) FROM information_schema.COLUMNS 
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'caleta' AND COLUMN_NAME = 'coordenada_actualizada_en');
SET @s2 = IF(@col2 = 0, 'ALTER TABLE caleta ADD COLUMN coordenada_actualizada_en DATETIME NULL', 'SELECT 1');
PREPARE stmt2 FROM @s2; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;

SET @col3 = (SELECT COUNT(*) FROM information_schema.COLUMNS 
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'caleta' AND COLUMN_NAME = 'coordenada_actualizada_por');
SET @s3 = IF(@col3 = 0, 'ALTER TABLE caleta ADD COLUMN coordenada_actualizada_por VARCHAR(100) NULL', 'SELECT 1');
PREPARE stmt3 FROM @s3; EXECUTE stmt3; DEALLOCATE PREPARE stmt3;

-- 2. Columnas en tabla varadero
SET @col4 = (SELECT COUNT(*) FROM information_schema.COLUMNS 
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'varadero' AND COLUMN_NAME = 'fuente_coordenada');
SET @s4 = IF(@col4 = 0, 'ALTER TABLE varadero ADD COLUMN fuente_coordenada VARCHAR(100) NULL', 'SELECT 1');
PREPARE stmt4 FROM @s4; EXECUTE stmt4; DEALLOCATE PREPARE stmt4;

SET @col5 = (SELECT COUNT(*) FROM information_schema.COLUMNS 
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'varadero' AND COLUMN_NAME = 'coordenada_actualizada_en');
SET @s5 = IF(@col5 = 0, 'ALTER TABLE varadero ADD COLUMN coordenada_actualizada_en DATETIME NULL', 'SELECT 1');
PREPARE stmt5 FROM @s5; EXECUTE stmt5; DEALLOCATE PREPARE stmt5;

SET @col6 = (SELECT COUNT(*) FROM information_schema.COLUMNS 
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'varadero' AND COLUMN_NAME = 'coordenada_actualizada_por');
SET @s6 = IF(@col6 = 0, 'ALTER TABLE varadero ADD COLUMN coordenada_actualizada_por VARCHAR(100) NULL', 'SELECT 1');
PREPARE stmt6 FROM @s6; EXECUTE stmt6; DEALLOCATE PREPARE stmt6;

-- 3. Columnas en tabla amerb
SET @col7 = (SELECT COUNT(*) FROM information_schema.COLUMNS 
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amerb' AND COLUMN_NAME = 'fuente_coordenada');
SET @s7 = IF(@col7 = 0, 'ALTER TABLE amerb ADD COLUMN fuente_coordenada VARCHAR(100) NULL', 'SELECT 1');
PREPARE stmt7 FROM @s7; EXECUTE stmt7; DEALLOCATE PREPARE stmt7;

SET @col8 = (SELECT COUNT(*) FROM information_schema.COLUMNS 
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amerb' AND COLUMN_NAME = 'coordenada_actualizada_en');
SET @s8 = IF(@col8 = 0, 'ALTER TABLE amerb ADD COLUMN coordenada_actualizada_en DATETIME NULL', 'SELECT 1');
PREPARE stmt8 FROM @s8; EXECUTE stmt8; DEALLOCATE PREPARE stmt8;

SET @col9 = (SELECT COUNT(*) FROM information_schema.COLUMNS 
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'amerb' AND COLUMN_NAME = 'coordenada_actualizada_por');
SET @s9 = IF(@col9 = 0, 'ALTER TABLE amerb ADD COLUMN coordenada_actualizada_por VARCHAR(100) NULL', 'SELECT 1');
PREPARE stmt9 FROM @s9; EXECUTE stmt9; DEALLOCATE PREPARE stmt9;

-- 4. Tabla de auditoría coordenada_cambio_log
CREATE TABLE IF NOT EXISTS coordenada_cambio_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  entidad_tipo VARCHAR(20) NOT NULL COMMENT 'CALETA, VARADERO, AMERB',
  entidad_id BIGINT NOT NULL,
  entidad_nombre VARCHAR(150) NULL,
  latitud_anterior DOUBLE NULL,
  longitud_anterior DOUBLE NULL,
  fuente_anterior VARCHAR(100) NULL,
  latitud_nueva DOUBLE NOT NULL,
  longitud_nueva DOUBLE NOT NULL,
  fuente_nueva VARCHAR(100) NOT NULL,
  actualizado_en DATETIME NOT NULL,
  actualizado_por VARCHAR(100) NULL,
  INDEX idx_coordenada_log_entidad (entidad_tipo, entidad_id),
  INDEX idx_coordenada_log_fecha (actualizado_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
