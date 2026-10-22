-- ==============================================================================
-- Migración TG.2: Varadero Declarado en Declaración Recolector
-- Idempotente para MySQL 8.0+
-- ==============================================================================

-- 1. Agregar columna varadero_id con índice si no existe
SET @col_exists = (
    SELECT COUNT(*) 
    FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() 
      AND TABLE_NAME = 'declaracion_recolector' 
      AND COLUMN_NAME = 'varadero_id'
);

SET @sql = IF(@col_exists = 0, 
    'ALTER TABLE declaracion_recolector ADD COLUMN varadero_id BIGINT NULL, ADD INDEX idx_decl_rec_varadero (varadero_id), ADD CONSTRAINT fk_decl_rec_varadero FOREIGN KEY (varadero_id) REFERENCES varadero(id) ON DELETE SET NULL', 
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. Calce histórico inteligente: vincular varadero_id calzando texto de varadero con varadero.nombre dentro de la misma comuna
UPDATE declaracion_recolector dr
JOIN varadero v ON v.comuna_id = dr.comuna_id 
  AND UPPER(TRIM(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(v.nombre, 'á', 'a'), 'é', 'e'), 'í', 'i'), 'ó', 'o'), 'ú', 'u'))) 
    = UPPER(TRIM(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(dr.varadero, 'á', 'a'), 'é', 'e'), 'í', 'i'), 'ó', 'o'), 'ú', 'u')))
SET dr.varadero_id = v.id
WHERE dr.varadero_id IS NULL 
  AND dr.varadero IS NOT NULL 
  AND TRIM(dr.varadero) != '';
