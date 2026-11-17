-- =========================================================================
-- MIGRACIÓN: migration_consulta_patente_indices.sql (ZC.5)
-- TrazAlga - Índices de consulta y retención en consulta_patente_log
-- Idempotente con guarda information_schema.statistics (MySQL 8.0+).
-- =========================================================================

SET NAMES utf8mb4;

SET @db_name = DATABASE();

SET @i1 = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = @db_name
      AND table_name = 'consulta_patente_log'
      AND index_name = 'idx_cpl_fecha'
);
SET @s1 = IF(
    @i1 = 0,
    'CREATE INDEX idx_cpl_fecha ON consulta_patente_log (fecha)',
    'SELECT ''Índice idx_cpl_fecha ya existe en consulta_patente_log'' AS info'
);
PREPARE p1 FROM @s1;
EXECUTE p1;
DEALLOCATE PREPARE p1;

SET @i2 = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = @db_name
      AND table_name = 'consulta_patente_log'
      AND index_name = 'idx_cpl_patente_fecha'
);
SET @s2 = IF(
    @i2 = 0,
    'CREATE INDEX idx_cpl_patente_fecha ON consulta_patente_log (patente, fecha)',
    'SELECT ''Índice idx_cpl_patente_fecha ya existe en consulta_patente_log'' AS info'
);
PREPARE p2 FROM @s2;
EXECUTE p2;
DEALLOCATE PREPARE p2;
