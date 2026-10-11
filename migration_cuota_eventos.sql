-- =========================================================================
-- MIGRACIÓN: migration_cuota_eventos.sql
-- TrazAlga - Refinamiento 09-oct / TC.2 Ciclo de vida de la cuota
-- 1. Tabla cuota_extraccion_evento para auditoría de ciclo de vida
--    (CREADA, EDITADA, CERRADA, REABIERTA, ACTIVADA, DESACTIVADA, RECALCULADA)
-- 2. Clave foránea e índice compuesto (cuota_id, created_at)
-- Idempotente conforme al estándar de ingeniería TrazAlga
-- =========================================================================

CREATE TABLE IF NOT EXISTS cuota_extraccion_evento (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  cuota_id   BIGINT       NOT NULL,
  tipo       VARCHAR(20)  NOT NULL,  -- CREADA | EDITADA | CERRADA | REABIERTA | ACTIVADA | DESACTIVADA | RECALCULADA
  motivo     VARCHAR(30)  NULL,      -- AGOTAMIENTO | ADMINISTRATIVO | VENCIMIENTO | texto en REABIERTA
  detalle    TEXT         NULL,      -- JSON con campos modificados o descripción
  usuario_id BIGINT       NULL,
  created_at DATETIME     NOT NULL,
  KEY idx_cee_cuota (cuota_id, created_at),
  CONSTRAINT fk_cee_cuota FOREIGN KEY (cuota_id) REFERENCES cuota_extraccion (id) ON DELETE CASCADE
);
