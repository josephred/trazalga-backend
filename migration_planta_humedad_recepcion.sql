-- ============================================================
-- Migración: Estado de Humedad en Recepción en Planta (T3.1)
-- Requerimiento: E.1 / T3.1 (PLAN_ANTIGRAVITY_prioridades_02oct.md)
-- Tabla: declaracion_planta_abastecimiento
-- Idempotente: Verifica existencia previa en information_schema
-- ============================================================

-- 1. Columna humedad_estado_recepcion_id
SET @col_her = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() 
      AND TABLE_NAME = 'declaracion_planta_abastecimiento' 
      AND COLUMN_NAME = 'humedad_estado_recepcion_id');

SET @sql_her = IF(@col_her = 0,
    'ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN humedad_estado_recepcion_id BIGINT NULL;',
    'SELECT "humedad_estado_recepcion_id ya existe";');
PREPARE stmt_her FROM @sql_her;
EXECUTE stmt_her;
DEALLOCATE PREPARE stmt_her;

-- 2. Clave foránea hacia humedad_estado(id)
SET @fk_her = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS 
    WHERE TABLE_SCHEMA = DATABASE() 
      AND TABLE_NAME = 'declaracion_planta_abastecimiento' 
      AND CONSTRAINT_NAME = 'fk_dpa_humedad_recepcion');

SET @sql_fk_her = IF(@fk_her = 0,
    'ALTER TABLE declaracion_planta_abastecimiento ADD CONSTRAINT fk_dpa_humedad_recepcion FOREIGN KEY (humedad_estado_recepcion_id) REFERENCES humedad_estado(id);',
    'SELECT "fk_dpa_humedad_recepcion ya existe";');
PREPARE stmt_fk_her FROM @sql_fk_her;
EXECUTE stmt_fk_her;
DEALLOCATE PREPARE stmt_fk_her;
