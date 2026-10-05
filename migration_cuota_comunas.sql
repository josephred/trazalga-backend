-- ================================================================================
-- TRAZALGA — MIGRACIÓN: SELECCIÓN MÚLTIPLE DE COMUNAS EN CUOTA DE EXTRACCIÓN (T1.1)
-- ================================================================================
-- Fecha: 4 de octubre de 2026
-- Propósito: Permitir que una cuota de nivel COMUNA agrupe múltiples comunas de una
--            misma región (ej. Coquimbo + La Serena).
-- Conserva: columna comuna_id en cuota_extraccion como comuna cabecera.
-- ================================================================================

CREATE TABLE IF NOT EXISTS cuota_extraccion_comuna (
  cuota_id  BIGINT NOT NULL,
  comuna_id BIGINT NOT NULL,
  PRIMARY KEY (cuota_id, comuna_id),
  KEY idx_cec_comuna (comuna_id),
  CONSTRAINT fk_cec_cuota  FOREIGN KEY (cuota_id)  REFERENCES cuota_extraccion (id) ON DELETE CASCADE,
  CONSTRAINT fk_cec_comuna FOREIGN KEY (comuna_id) REFERENCES comuna (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Respaldo inicial: cada cuota existente con comuna_id conserva su comuna en la tabla intermedia
INSERT IGNORE INTO cuota_extraccion_comuna (cuota_id, comuna_id)
SELECT id, comuna_id 
FROM cuota_extraccion 
WHERE comuna_id IS NOT NULL;
