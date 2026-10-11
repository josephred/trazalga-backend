-- =========================================================================
-- MIGRACIÓN: migration_hallazgo_criterio.sql
-- TrazAlga - Refinamiento 09-oct / TA.3 Modelo de hallazgos con criterio estructurado
-- 1. Columnas de criterio estructurado, origen y clave de idempotencia en declaracion_marca
-- 2. Índice único ux_dm_idempotencia para evitar duplicados en marcas de sistema
-- 3. Índice compuesto ix_dm_marca_fecha para optimización de consultas en Consola
-- Idempotente conforme al estándar de ingeniería TrazAlga
-- =========================================================================

-- 1. Columna criterio_parametro (p. ej. desembarque_umbral_atipico_kg, retencion_bloqueo_humedo_horas)
SET @col1 = (SELECT COUNT(*) FROM information_schema.COLUMNS 
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_marca' AND COLUMN_NAME = 'criterio_parametro');
SET @s1 = IF(@col1 = 0, 'ALTER TABLE declaracion_marca ADD COLUMN criterio_parametro VARCHAR(80) NULL', 'SELECT 1');
PREPARE stmt1 FROM @s1; EXECUTE stmt1; DEALLOCATE PREPARE stmt1;

-- 2. Columna criterio_umbral (p. ej. "5000", "2026-03-20", "120")
SET @col2 = (SELECT COUNT(*) FROM information_schema.COLUMNS 
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_marca' AND COLUMN_NAME = 'criterio_umbral');
SET @s2 = IF(@col2 = 0, 'ALTER TABLE declaracion_marca ADD COLUMN criterio_umbral VARCHAR(40) NULL', 'SELECT 1');
PREPARE stmt2 FROM @s2; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;

-- 3. Columna criterio_valor (valor observado en la declaración o medición)
SET @col3 = (SELECT COUNT(*) FROM information_schema.COLUMNS 
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_marca' AND COLUMN_NAME = 'criterio_valor');
SET @s3 = IF(@col3 = 0, 'ALTER TABLE declaracion_marca ADD COLUMN criterio_valor VARCHAR(40) NULL', 'SELECT 1');
PREPARE stmt3 FROM @s3; EXECUTE stmt3; DEALLOCATE PREPARE stmt3;

-- 4. Columna criterio_unidad (kg | h | % | fecha)
SET @col4 = (SELECT COUNT(*) FROM information_schema.COLUMNS 
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_marca' AND COLUMN_NAME = 'criterio_unidad');
SET @s4 = IF(@col4 = 0, 'ALTER TABLE declaracion_marca ADD COLUMN criterio_unidad VARCHAR(20) NULL', 'SELECT 1');
PREPARE stmt4 FROM @s4; EXECUTE stmt4; DEALLOCATE PREPARE stmt4;

-- 5. Columna origen (VALIDACION | BARRIDO | RECALCULO)
SET @col5 = (SELECT COUNT(*) FROM information_schema.COLUMNS 
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_marca' AND COLUMN_NAME = 'origen');
SET @s5 = IF(@col5 = 0, 'ALTER TABLE declaracion_marca ADD COLUMN origen VARCHAR(20) NOT NULL DEFAULT ''VALIDACION''', 'SELECT 1');
PREPARE stmt5 FROM @s5; EXECUTE stmt5; DEALLOCATE PREPARE stmt5;

-- 6. Columna clave_idempotencia (TIPO:ID:MARCA:REGLA o TIPO:ID:RETENCION_EXCEDIDA)
SET @col6 = (SELECT COUNT(*) FROM information_schema.COLUMNS 
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_marca' AND COLUMN_NAME = 'clave_idempotencia');
SET @s6 = IF(@col6 = 0, 'ALTER TABLE declaracion_marca ADD COLUMN clave_idempotencia VARCHAR(160) NULL', 'SELECT 1');
PREPARE stmt6 FROM @s6; EXECUTE stmt6; DEALLOCATE PREPARE stmt6;

-- 7. Índice único ux_dm_idempotencia en clave_idempotencia (MySQL permite múltiples NULLs)
SET @idx1 = (SELECT COUNT(*) FROM information_schema.STATISTICS 
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_marca' AND INDEX_NAME = 'ux_dm_idempotencia');
SET @s7 = IF(@idx1 = 0, 'CREATE UNIQUE INDEX ux_dm_idempotencia ON declaracion_marca (clave_idempotencia)', 'SELECT 1');
PREPARE stmt7 FROM @s7; EXECUTE stmt7; DEALLOCATE PREPARE stmt7;

-- 8. Índice compuesto ix_dm_marca_fecha en (marca, created_at)
SET @idx2 = (SELECT COUNT(*) FROM information_schema.STATISTICS 
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_marca' AND INDEX_NAME = 'ix_dm_marca_fecha');
SET @s8 = IF(@idx2 = 0, 'CREATE INDEX ix_dm_marca_fecha ON declaracion_marca (marca, created_at)', 'SELECT 1');
PREPARE stmt8 FROM @s8; EXECUTE stmt8; DEALLOCATE PREPARE stmt8;
