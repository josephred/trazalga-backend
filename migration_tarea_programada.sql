-- ================================================================================
-- MIGRACIÓN: TABLA TAREA_PROGRAMADA_EJECUCION (TA.6)
-- ================================================================================

CREATE TABLE IF NOT EXISTS tarea_programada_ejecucion (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    inicio DATETIME NOT NULL,
    fin DATETIME NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'OK', -- 'OK' | 'ERROR'
    procesados INT NOT NULL DEFAULT 0,
    mensaje TEXT NULL,
    INDEX idx_tpe_nombre_inicio (nombre, inicio DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
