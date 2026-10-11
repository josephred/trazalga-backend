-- ================================================================================
-- MIGRACIÓN: TABLA AVISO_ENVIADO PARA IDEMPOTENCIA DE NOTIFICACIONES (TC.6)
-- ================================================================================

CREATE TABLE IF NOT EXISTS aviso_enviado (
    clave VARCHAR(160) NOT NULL PRIMARY KEY,
    enviado_at DATETIME NOT NULL,
    INDEX idx_aviso_enviado_at (enviado_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
