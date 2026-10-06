-- =========================================================================
-- MIGRACIÓN: migration_consulta_folio_log.sql
-- TrazAlga - Prioridad 2 / T2.2 Búsqueda por Folio
-- Registro de auditoría de búsquedas puntuales por folio
-- Idempotente conforme al estándar de ingeniería Trazalga
-- =========================================================================

CREATE TABLE IF NOT EXISTS `consulta_folio_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `usuario_rut` VARCHAR(20) NOT NULL,
    `texto_buscado` VARCHAR(100) NOT NULL,
    `cantidad_resultados` INT NOT NULL,
    `fecha` DATETIME NOT NULL,
    INDEX `idx_consulta_folio_log_fecha` (`fecha`),
    INDEX `idx_consulta_folio_log_rut` (`usuario_rut`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
