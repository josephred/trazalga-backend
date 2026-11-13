-- NA.3: Agrega columna lote_interno e índice compuesto por planta a declaracion_planta_abastecimiento
-- Idempotente y seguro para re-ejecución en MySQL 8.0+

SET @db_name = DATABASE();

SET @col_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @db_name
      AND table_name = 'declaracion_planta_abastecimiento'
      AND column_name = 'lote_interno'
);

SET @sql_col = IF(
    @col_exists = 0,
    'ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN lote_interno VARCHAR(50) NULL AFTER lugar_acopio',
    'SELECT ''Columna lote_interno ya existe en declaracion_planta_abastecimiento'' AS info'
);

PREPARE stmt_col FROM @sql_col;
EXECUTE stmt_col;
DEALLOCATE PREPARE stmt_col;

SET @idx_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = @db_name
      AND table_name = 'declaracion_planta_abastecimiento'
      AND index_name = 'ix_dpa_usuario_lote'
);

SET @sql_idx = IF(
    @idx_exists = 0,
    'CREATE INDEX ix_dpa_usuario_lote ON declaracion_planta_abastecimiento (usuario_id, lote_interno)',
    'SELECT ''Indice ix_dpa_usuario_lote ya existe en declaracion_planta_abastecimiento'' AS info'
);

PREPARE stmt_idx FROM @sql_idx;
EXECUTE stmt_idx;
DEALLOCATE PREPARE stmt_idx;
