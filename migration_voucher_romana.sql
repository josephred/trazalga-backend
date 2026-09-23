-- ============================================================
-- Migración: Voucher de Romana y Pesaje Físico en Planta
-- Requerimiento: R6.2 (Indicador 6 - Variación en Peso)
-- Tabla: declaracion_planta_abastecimiento
-- ============================================================

ALTER TABLE declaracion_planta_abastecimiento
    ADD COLUMN voucher_romana_numero VARCHAR(50) NULL,
    ADD COLUMN voucher_romana_adjunto VARCHAR(255) NULL,
    ADD COLUMN peso_romana_kg DECIMAL(12,2) NULL,
    ADD COLUMN fecha_pesaje DATE NULL;
