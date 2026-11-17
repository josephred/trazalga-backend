-- =========================================================================
-- MIGRACIÓN: migration_planta_comuna.sql (ZM.4)
-- TrazAlga - Comuna de la planta para filtro por región de destino y capas
-- Idempotente con guardas sobre information_schema (MySQL 8.0+).
-- =========================================================================

SET NAMES utf8mb4;

SET @db_name = DATABASE();

-- 1. Agregar comuna_id a planta
SET @col_cnt = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db_name
      AND table_name = 'planta'
      AND column_name = 'comuna_id'
);
SET @sql_add_col = IF(
    @col_cnt = 0,
    'ALTER TABLE planta ADD COLUMN comuna_id BIGINT NULL AFTER direccion',
    'SELECT ''Columna comuna_id ya existe en planta'' AS info'
);
PREPARE s1 FROM @sql_add_col; EXECUTE s1; DEALLOCATE PREPARE s1;

-- 2. Índice auxiliar idx_planta_comuna
SET @idx_cnt = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = @db_name
      AND table_name = 'planta'
      AND index_name = 'idx_planta_comuna'
);
SET @sql_add_idx = IF(
    @idx_cnt = 0,
    'CREATE INDEX idx_planta_comuna ON planta (comuna_id)',
    'SELECT ''Indice idx_planta_comuna ya existe en planta'' AS info'
);
PREPARE s2 FROM @sql_add_idx; EXECUTE s2; DEALLOCATE PREPARE s2;

-- 3. Clave foránea fk_planta_comuna -> comuna(id)
SET @fk_cnt = (
    SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE constraint_schema = @db_name
      AND table_name = 'planta'
      AND constraint_name = 'fk_planta_comuna'
      AND constraint_type = 'FOREIGN KEY'
);
SET @sql_add_fk = IF(
    @fk_cnt = 0,
    'ALTER TABLE planta ADD CONSTRAINT fk_planta_comuna FOREIGN KEY (comuna_id) REFERENCES comuna(id) ON DELETE SET NULL',
    'SELECT ''FK fk_planta_comuna ya existe'' AS info'
);
PREPARE s3 FROM @sql_add_fk; EXECUTE s3; DEALLOCATE PREPARE s3;

-- 4. Backfill seguro desde usuario (solo cuando hay calce unívoco por RUT normalizado)
UPDATE planta pl
SET pl.comuna_id = (
    SELECT CASE WHEN COUNT(DISTINCT u.comuna_id) = 1 THEN MIN(u.comuna_id) ELSE NULL END
    FROM usuario u
    WHERE u.comuna_id IS NOT NULL
      AND UPPER(REPLACE(REPLACE(TRIM(u.rut), '.', ''), '-', ''))
        = UPPER(REPLACE(REPLACE(TRIM(pl.rut), '.', ''), '-', ''))
)
WHERE pl.comuna_id IS NULL
  AND pl.rut IS NOT NULL
  AND TRIM(pl.rut) <> '';
