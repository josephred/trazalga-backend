-- =========================================================================
-- MIGRACIÓN NG.1: Coordenadas geográficas y tipo de planta en tabla planta
-- Ref: Planes/REF20261106/PLAN_ANTIGRAVITY_refinamiento_06nov.md (NG.1)
-- Idempotente y segura para múltiples ejecuciones.
-- =========================================================================

SET @col_tipo = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'planta' AND COLUMN_NAME = 'tipo');
SET @sql_tipo = IF(@col_tipo = 0,
  'ALTER TABLE planta ADD COLUMN tipo VARCHAR(20) NULL COMMENT ''SECADO | PROCESO | OTRA''',
  'SELECT ''planta.tipo ya existe'' AS msg');
PREPARE s FROM @sql_tipo; EXECUTE s; DEALLOCATE PREPARE s;

SET @col_lat = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'planta' AND COLUMN_NAME = 'latitud');
SET @sql_lat = IF(@col_lat = 0,
  'ALTER TABLE planta ADD COLUMN latitud DOUBLE NULL',
  'SELECT ''planta.latitud ya existe'' AS msg');
PREPARE s FROM @sql_lat; EXECUTE s; DEALLOCATE PREPARE s;

SET @col_lon = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'planta' AND COLUMN_NAME = 'longitud');
SET @sql_lon = IF(@col_lon = 0,
  'ALTER TABLE planta ADD COLUMN longitud DOUBLE NULL',
  'SELECT ''planta.longitud ya existe'' AS msg');
PREPARE s FROM @sql_lon; EXECUTE s; DEALLOCATE PREPARE s;

SET @col_fuente = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'planta' AND COLUMN_NAME = 'fuente_coordenada');
SET @sql_fuente = IF(@col_fuente = 0,
  'ALTER TABLE planta ADD COLUMN fuente_coordenada VARCHAR(100) NULL',
  'SELECT ''planta.fuente_coordenada ya existe'' AS msg');
PREPARE s FROM @sql_fuente; EXECUTE s; DEALLOCATE PREPARE s;

SET @col_act_en = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'planta' AND COLUMN_NAME = 'coordenada_actualizada_en');
SET @sql_act_en = IF(@col_act_en = 0,
  'ALTER TABLE planta ADD COLUMN coordenada_actualizada_en DATETIME NULL',
  'SELECT ''planta.coordenada_actualizada_en ya existe'' AS msg');
PREPARE s FROM @sql_act_en; EXECUTE s; DEALLOCATE PREPARE s;

SET @col_act_por = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'planta' AND COLUMN_NAME = 'coordenada_actualizada_por');
SET @sql_act_por = IF(@col_act_por = 0,
  'ALTER TABLE planta ADD COLUMN coordenada_actualizada_por VARCHAR(100) NULL',
  'SELECT ''planta.coordenada_actualizada_por ya existe'' AS msg');
PREPARE s FROM @sql_act_por; EXECUTE s; DEALLOCATE PREPARE s;

