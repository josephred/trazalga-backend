-- ==============================================================================
-- Migración · Refinamiento 23-10-2026, punto 1: imputación cruzada por veda
-- Idempotente para MySQL 8.0+
--
-- Regla: si se declara un método en veda sin cuota propia en la comuna y el período
-- (p. ej. huiro negro por barreteado en octubre), la alerta EN_VEDA se mantiene y la
-- captura corregida se imputa a la cuota del método de destino (varado) de la misma
-- especie, comuna y período.
--
-- Lo declarado no se reescribe: extraccion_tipo_id sigue siendo el método declarado.
-- La nueva columna extraccion_tipo_imputacion_id guarda, sólo cuando hay imputación
-- cruzada, el método cuya cuota recibe la captura. NULL = consume con el declarado.
--
-- Hibernate (ddl-auto=update) también crea la columna al arrancar; este script deja
-- además el índice y la llave foránea, y sirve para ambientes con ddl-auto=validate.
--
-- Después de aplicarla, para completar las declaraciones ya guardadas:
--   POST /api/cuotas/imputacion-cruzada/recalcular?desde=AAAA-MM-DD&hasta=AAAA-MM-DD          (vista previa)
--   POST /api/cuotas/imputacion-cruzada/recalcular?desde=AAAA-MM-DD&hasta=AAAA-MM-DD&aplicar=true
-- y luego, por cada cuota de destino afectada, POST /api/cuotas/{id}/recalcular-hallazgos.
-- ==============================================================================

-- 1. declaracion_recolector
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_recolector' AND COLUMN_NAME = 'extraccion_tipo_imputacion_id'
);
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE declaracion_recolector ADD COLUMN extraccion_tipo_imputacion_id BIGINT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_recolector' AND INDEX_NAME = 'idx_decl_rec_ext_imputacion'
);
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE declaracion_recolector ADD INDEX idx_decl_rec_ext_imputacion (extraccion_tipo_imputacion_id)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @fk_exists = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_recolector' AND CONSTRAINT_NAME = 'fk_decl_rec_ext_imputacion'
);
SET @sql = IF(@fk_exists = 0,
    'ALTER TABLE declaracion_recolector ADD CONSTRAINT fk_decl_rec_ext_imputacion FOREIGN KEY (extraccion_tipo_imputacion_id) REFERENCES extraccion_tipo(id)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. declaracion_armador
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_armador' AND COLUMN_NAME = 'extraccion_tipo_imputacion_id'
);
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE declaracion_armador ADD COLUMN extraccion_tipo_imputacion_id BIGINT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_armador' AND INDEX_NAME = 'idx_decl_arm_ext_imputacion'
);
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE declaracion_armador ADD INDEX idx_decl_arm_ext_imputacion (extraccion_tipo_imputacion_id)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @fk_exists = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_armador' AND CONSTRAINT_NAME = 'fk_decl_arm_ext_imputacion'
);
SET @sql = IF(@fk_exists = 0,
    'ALTER TABLE declaracion_armador ADD CONSTRAINT fk_decl_arm_ext_imputacion FOREIGN KEY (extraccion_tipo_imputacion_id) REFERENCES extraccion_tipo(id)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. declaracion_area
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_area' AND COLUMN_NAME = 'extraccion_tipo_imputacion_id'
);
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE declaracion_area ADD COLUMN extraccion_tipo_imputacion_id BIGINT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_area' AND INDEX_NAME = 'idx_decl_area_ext_imputacion'
);
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE declaracion_area ADD INDEX idx_decl_area_ext_imputacion (extraccion_tipo_imputacion_id)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @fk_exists = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_area' AND CONSTRAINT_NAME = 'fk_decl_area_ext_imputacion'
);
SET @sql = IF(@fk_exists = 0,
    'ALTER TABLE declaracion_area ADD CONSTRAINT fk_decl_area_ext_imputacion FOREIGN KEY (extraccion_tipo_imputacion_id) REFERENCES extraccion_tipo(id)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4. Parámetros (ConfiguracionGeneralService también los siembra al arrancar)
INSERT INTO configuracion_general (clave, valor, descripcion, categoria)
SELECT 'cuota_imputacion_veda_activo', 'true',
       'Método en veda sin cuota propia (barreteado): se mantiene la alerta de veda y la captura se imputa a la cuota del método de destino (varado) de la misma especie, comuna y período (refinamiento 23-10)',
       'CUOTA'
WHERE NOT EXISTS (SELECT 1 FROM configuracion_general WHERE clave = 'cuota_imputacion_veda_activo');

INSERT INTO configuracion_general (clave, valor, descripcion, categoria)
SELECT 'cuota_imputacion_veda_metodo_origen', 'BARRETEADO',
       'Método declarado que, en veda y sin cuota propia, se imputa a la cuota del método de destino (nombre en el maestro de métodos)',
       'CUOTA'
WHERE NOT EXISTS (SELECT 1 FROM configuracion_general WHERE clave = 'cuota_imputacion_veda_metodo_origen');

INSERT INTO configuracion_general (clave, valor, descripcion, categoria)
SELECT 'cuota_imputacion_veda_metodo_destino', 'VARADO',
       'Método cuya cuota recibe la imputación cruzada por veda (nombre en el maestro de métodos)',
       'CUOTA'
WHERE NOT EXISTS (SELECT 1 FROM configuracion_general WHERE clave = 'cuota_imputacion_veda_metodo_destino');

-- 5. Verificación (sólo lectura): métodos que reconocerá la regla
SELECT id, nombre FROM extraccion_tipo ORDER BY id;
