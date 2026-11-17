-- =========================================================================
-- MIGRACIÓN PREPARADA (D57): migration_bloqueo_produccion_sin_lotes.sql (ZP.3)
-- Agrega PRODUCCION_SIN_LOTES a marcas_bloqueantes_produccion para retener
-- el despacho en destino de producciones ya guardadas sin lotes asociados.
--
-- ATENCIÓN: NO SE EJECUTA AUTOMÁTICAMENTE en el despliegue ni en local.
-- Aplicar únicamente previa confirmación D57 con Sernapesca.
-- Idempotente y segura para re-ejecución en MySQL 8.0+.
-- =========================================================================

SET NAMES utf8mb4;

INSERT INTO configuracion_general (clave, valor, descripcion, categoria)
SELECT
    'marcas_bloqueantes_produccion',
    'RENDIMIENTO_ANOMALO,PRODUCCION_SIN_LOTES',
    'Marcas que retienen e impiden despachar declaraciones de producción (NP.6 / D32 / D57)',
    'CADENA'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM configuracion_general WHERE clave = 'marcas_bloqueantes_produccion'
);

UPDATE configuracion_general
SET valor = CASE
    WHEN valor IS NULL OR TRIM(valor) = '' THEN 'PRODUCCION_SIN_LOTES'
    ELSE CONCAT(TRIM(TRAILING ',' FROM TRIM(valor)), ',PRODUCCION_SIN_LOTES')
END
WHERE clave = 'marcas_bloqueantes_produccion'
  AND FIND_IN_SET('PRODUCCION_SIN_LOTES', REPLACE(COALESCE(valor, ''), ' ', '')) = 0;
