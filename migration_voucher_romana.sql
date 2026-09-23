-- ============================================================
-- Migración: Voucher de Romana y Pesaje Físico en Planta (H12)
-- Requerimiento: R6.2 (Indicador 6 - Variación en Peso)
-- Tabla: declaracion_planta_abastecimiento
-- Idempotente: Verifica existencia previa en information_schema
-- ============================================================

-- 1. voucher_romana_numero
SET @col_vrn = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_planta_abastecimiento' AND COLUMN_NAME = 'voucher_romana_numero');
SET @sql_vrn = IF(@col_vrn = 0,
    'ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN voucher_romana_numero VARCHAR(50) NULL;',
    'SELECT "voucher_romana_numero ya existe";');
PREPARE stmt_vrn FROM @sql_vrn;
EXECUTE stmt_vrn;
DEALLOCATE PREPARE stmt_vrn;

-- 2. voucher_romana_adjunto
SET @col_vra = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_planta_abastecimiento' AND COLUMN_NAME = 'voucher_romana_adjunto');
SET @sql_vra = IF(@col_vra = 0,
    'ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN voucher_romana_adjunto VARCHAR(255) NULL;',
    'SELECT "voucher_romana_adjunto ya existe";');
PREPARE stmt_vra FROM @sql_vra;
EXECUTE stmt_vra;
DEALLOCATE PREPARE stmt_vra;

-- 3. peso_romana_kg
SET @col_prk = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_planta_abastecimiento' AND COLUMN_NAME = 'peso_romana_kg');
SET @sql_prk = IF(@col_prk = 0,
    'ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN peso_romana_kg DECIMAL(12,2) NULL;',
    'SELECT "peso_romana_kg ya existe";');
PREPARE stmt_prk FROM @sql_prk;
EXECUTE stmt_prk;
DEALLOCATE PREPARE stmt_prk;

-- 4. fecha_pesaje
SET @col_fp = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_planta_abastecimiento' AND COLUMN_NAME = 'fecha_pesaje');
SET @sql_fp = IF(@col_fp = 0,
    'ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN fecha_pesaje DATE NULL;',
    'SELECT "fecha_pesaje ya existe";');
PREPARE stmt_fp FROM @sql_fp;
EXECUTE stmt_fp;
DEALLOCATE PREPARE stmt_fp;
