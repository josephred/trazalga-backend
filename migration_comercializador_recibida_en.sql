-- =========================================================================
-- MIGRACIÓN: migration_comercializador_recibida_en.sql (ZC.2)
-- TrazAlga - Marca de tiempo del servidor al recibir declaración de comercializador
-- Idempotente con guarda information_schema (MySQL 8.0+).
-- =========================================================================

SET NAMES utf8mb4;

SET @db_name = DATABASE();
SET @c_recibida = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @db_name
      AND table_name = 'declaracion_comercializador'
      AND column_name = 'recibida_en'
);
SET @s_recibida = IF(
    @c_recibida = 0,
    'ALTER TABLE declaracion_comercializador ADD COLUMN recibida_en DATETIME NULL AFTER gps_capturado_en',
    'SELECT ''Columna recibida_en ya existe en declaracion_comercializador'' AS info'
);
PREPARE p_recibida FROM @s_recibida;
EXECUTE p_recibida;
DEALLOCATE PREPARE p_recibida;
