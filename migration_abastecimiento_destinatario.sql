-- =============================================================================
-- Refinamiento 06-11 · NA.1: Destinatario propio en recepciones y producción de planta
-- Contexto auditoría 2026-11-12 (localhost:3306/trazalga):
--   - SHOW CREATE TABLE declaracion_planta_abastecimiento:
--       usuario_id bigint NOT NULL (FK752eu7xgksl4iur4akufki0qi)
--       usuario_destinatario_id bigint DEFAULT NULL (FKoybk1vttec8ca1l6j64gfuqu2)
--       32/32 filas existentes con usuario_destinatario_id IS NULL.
--   - Regla: el servidor asigna usuario_destinatario_id = usuario_id al guardar/editar.
--   - No toca recepciones que ya tengan un usuario_destinatario_id asignado.
-- Idempotente y re-ejecutable de forma segura.
-- =============================================================================

CREATE TABLE IF NOT EXISTS zz_respaldo_abast_destinatario_20261106 (
    id BIGINT NOT NULL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    usuario_destinatario_id BIGINT NULL,
    fecha_ingreso_planta DATE NULL,
    respaldado_en DATETIME NOT NULL
);

INSERT IGNORE INTO zz_respaldo_abast_destinatario_20261106
    (id, usuario_id, usuario_destinatario_id, fecha_ingreso_planta, respaldado_en)
SELECT id, usuario_id, usuario_destinatario_id, fecha_ingreso_planta, NOW()
FROM declaracion_planta_abastecimiento
WHERE usuario_destinatario_id IS NULL;

UPDATE declaracion_planta_abastecimiento
SET usuario_destinatario_id = usuario_id
WHERE usuario_destinatario_id IS NULL;

-- Respaldo y backfill preventivo para producciones de planta sin destinatario (D24)
CREATE TABLE IF NOT EXISTS zz_respaldo_prod_destinatario_20261106 (
    id BIGINT NOT NULL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    usuario_destinatario_id BIGINT NULL,
    fecha_produccion DATE NULL,
    respaldado_en DATETIME NOT NULL
);

INSERT IGNORE INTO zz_respaldo_prod_destinatario_20261106
    (id, usuario_id, usuario_destinatario_id, fecha_produccion, respaldado_en)
SELECT id, usuario_id, usuario_destinatario_id, fecha_produccion, NOW()
FROM declaracion_planta_produccion
WHERE usuario_destinatario_id IS NULL;

UPDATE declaracion_planta_produccion
SET usuario_destinatario_id = usuario_id
WHERE usuario_destinatario_id IS NULL;

SELECT COUNT(*) AS abastecimientos_sin_destinatario
FROM declaracion_planta_abastecimiento
WHERE usuario_destinatario_id IS NULL;
