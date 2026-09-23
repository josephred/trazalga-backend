-- ============================================================================
-- MIGRACIÓN: MARCAS DE FISCALIZACIÓN Y HALLAZGOS (R1.1, R3.2, R4.1, R5.2, RX.1)
-- ============================================================================

-- 1. Permitir declaracion_id nulo para registrar intentos bloqueados (auditoría formal)
ALTER TABLE declaracion_marca MODIFY COLUMN declaracion_id BIGINT NULL;

-- 2. Agregar modo_accion a cuota_extraccion (SOLO_ALERTA vs BLOQUEO_DECLARACION)
SET @col_ce_modo = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cuota_extraccion' AND COLUMN_NAME = 'modo_accion');

SET @sql_ce_modo = IF(@col_ce_modo = 0,
    'ALTER TABLE cuota_extraccion ADD COLUMN modo_accion VARCHAR(30) NOT NULL DEFAULT "SOLO_ALERTA";',
    'SELECT "modo_accion ya existe en cuota_extraccion";');

PREPARE stmt FROM @sql_ce_modo;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Asegurar que las cuotas existentes queden en SOLO_ALERTA si estaban vacías
UPDATE cuota_extraccion
SET modo_accion = 'SOLO_ALERTA'
WHERE modo_accion IS NULL OR TRIM(modo_accion) = '';
