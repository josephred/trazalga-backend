-- =========================================================================
-- MIGRACIÓN: migration_fecha_ajuste_log.sql (ZT.2)
-- TrazAlga - Registro de ajustes de fechas futuras por desfase UTC
-- Idempotente conforme al estándar de ingeniería TrazAlga.
-- =========================================================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS declaracion_fecha_ajuste_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    declaracion_tipo VARCHAR(40) NOT NULL,
    declaracion_id BIGINT NULL,
    fecha_original DATE NOT NULL,
    fecha_guardada DATE NOT NULL,
    hora_declarada VARCHAR(16) NULL,
    motivo VARCHAR(60) NOT NULL,
    fecha_servidor DATETIME NOT NULL,
    creado_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX ix_dfal_tipo_id (declaracion_tipo, declaracion_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
