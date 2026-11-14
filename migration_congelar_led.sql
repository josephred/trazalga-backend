-- ============================================================================
-- Migración: Bloquear declaraciones con marca LED_EXCEDIDO por defecto
-- Refinamiento 13-11-2026 · Tarea QL.1 (D22 / Anexo 4 #1)
-- Idempotente: puede ejecutarse múltiples veces sin duplicar LED_EXCEDIDO.
-- ============================================================================

INSERT INTO configuracion_general (clave, valor, descripcion, categoria)
SELECT 'declaracion_congelar_marcas',
       'EN_VEDA,CUOTA_EXCEDIDA,POSTERIOR_CIERRE,LED_EXCEDIDO',
       'Marcas que congelan una declaración de recolector, armador o área (separadas por coma)',
       'CADENA'
WHERE NOT EXISTS (
    SELECT 1 FROM configuracion_general WHERE clave = 'declaracion_congelar_marcas'
);

UPDATE configuracion_general
SET valor = CASE
    WHEN valor IS NULL OR TRIM(valor) = '' THEN 'LED_EXCEDIDO'
    ELSE CONCAT(TRIM(BOTH ',' FROM TRIM(valor)), ',LED_EXCEDIDO')
END
WHERE clave = 'declaracion_congelar_marcas'
  AND FIND_IN_SET('LED_EXCEDIDO', REPLACE(IFNULL(valor, ''), ' ', '')) = 0;
