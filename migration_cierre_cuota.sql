-- ============================================================================
-- MIGRACIÓN: CIERRE AUTOMÁTICO SEPARADO DEL ADMINISTRATIVO (R3.3)
-- Agrega fecha_cierre_automatico y motivo_cierre a cuota_extraccion
-- Resolviendo la separación entre cierre por AGOTAMIENTO vs ADMINISTRATIVO
-- ============================================================================

-- 1. Agregar fecha_cierre_automatico si no existe
SET @col_ce_fca = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cuota_extraccion' AND COLUMN_NAME = 'fecha_cierre_automatico');

SET @sql_ce_fca = IF(@col_ce_fca = 0,
    'ALTER TABLE cuota_extraccion ADD COLUMN fecha_cierre_automatico DATE NULL AFTER fecha_cierre;',
    'SELECT "fecha_cierre_automatico ya existe en cuota_extraccion";');

PREPARE stmt_fca FROM @sql_ce_fca;
EXECUTE stmt_fca;
DEALLOCATE PREPARE stmt_fca;

-- 2. Agregar motivo_cierre si no existe (AGOTAMIENTO | ADMINISTRATIVO | VENCIMIENTO)
SET @col_ce_mc = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cuota_extraccion' AND COLUMN_NAME = 'motivo_cierre');

SET @sql_ce_mc = IF(@col_ce_mc = 0,
    'ALTER TABLE cuota_extraccion ADD COLUMN motivo_cierre VARCHAR(30) NULL AFTER fecha_cierre_automatico;',
    'SELECT "motivo_cierre ya existe en cuota_extraccion";');

PREPARE stmt_mc FROM @sql_ce_mc;
EXECUTE stmt_mc;
DEALLOCATE PREPARE stmt_mc;

-- 3. Retrocompatibilidad: Si una cuota ya estaba CERRADA con fecha_cierre poblada pero sin motivo,
--    asignar ADMINISTRATIVO por defecto para mantener consistencia de auditoría
UPDATE cuota_extraccion
SET motivo_cierre = 'ADMINISTRATIVO'
WHERE estado = 'CERRADA' AND (motivo_cierre IS NULL OR TRIM(motivo_cierre) = '');

-- 4. Constraint CHECK de integridad para motivo_cierre (idempotente)
SET @chk_mc = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cuota_extraccion' AND CONSTRAINT_NAME = 'chk_cuota_motivo_cierre');

SET @sql_chk_mc = IF(@chk_mc = 0,
    'ALTER TABLE cuota_extraccion ADD CONSTRAINT chk_cuota_motivo_cierre CHECK (motivo_cierre IS NULL OR motivo_cierre IN ("AGOTAMIENTO", "ADMINISTRATIVO", "VENCIMIENTO"));',
    'SELECT "chk_cuota_motivo_cierre ya existe";');

PREPARE stmt_chk_mc FROM @sql_chk_mc;
EXECUTE stmt_chk_mc;
DEALLOCATE PREPARE stmt_chk_mc;
