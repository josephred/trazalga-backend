-- ================================================================================
-- TRAZALGA — SCRIPT DE LIMPIEZA DE DATOS DE PRUEBA (T0.2)
-- ================================================================================
-- PRECAUCIÓN CRÍTICA:
-- ESTE SCRIPT NUNCA SE EJECUTA EN PRODUCCIÓN SIN AUTORIZACIÓN ESCRITA DE AARÓN.
-- Su ejecución elimina registros ficticios utilizados en desarrollo/QA para permitir
-- la carga limpia de datos reales de SERNAPESCA / SUBPESCA 2026 (Decisión D8).
--
-- Orden de dependencias respetado:
--   1. Marcas normativas asociadas a dummies
--   2. Bloqueos y desbloqueos de carga asociados a dummies
--   3. Planta de producción (dummies)
--   4. Planta de abastecimiento (dummies)
--   5. Comercializador (dummies)
--   6. Declaraciones de origen: Recolector, Armador, Área (dummies)
--   7. Cuotas de extracción marcadas como prueba (DUMMY / TEST)
-- ================================================================================

START TRANSACTION;

-- 1. Eliminar marcas normativas sobre declaraciones dummy
DELETE FROM declaracion_marca 
WHERE declaracion_tipo = 'RECOLECTOR' 
  AND declaracion_id IN (SELECT id FROM declaracion_recolector WHERE folio_origen LIKE 'DUMMY-%' OR folio_origen LIKE 'FOL-REC-%' OR folio_origen LIKE 'TEST-%');

DELETE FROM declaracion_marca 
WHERE declaracion_tipo = 'ARMADOR' 
  AND declaracion_id IN (SELECT id FROM declaracion_armador WHERE folio_origen LIKE 'DUMMY-%' OR folio_origen LIKE 'FOL-ARM-%' OR folio_origen LIKE 'TEST-%');

DELETE FROM declaracion_marca 
WHERE declaracion_tipo = 'AREA' 
  AND declaracion_id IN (SELECT id FROM declaracion_area WHERE folio_origen LIKE 'DUMMY-%' OR folio_origen LIKE 'FOL-ARE-%' OR folio_origen LIKE 'TEST-%');

DELETE FROM declaracion_marca 
WHERE declaracion_tipo = 'COMERCIALIZADOR' 
  AND declaracion_id IN (SELECT id FROM declaracion_comercializador WHERE folio_origen LIKE 'DUMMY-%' OR folio_origen LIKE 'FOL-COM-%' OR folio_origen LIKE 'TEST-%');

DELETE FROM declaracion_marca 
WHERE declaracion_tipo = 'PLANTA_ABASTECIMIENTO' 
  AND declaracion_id IN (SELECT id FROM declaracion_planta_abastecimiento WHERE folio_origen LIKE 'DUMMY-%' OR folio_origen LIKE 'FOL-PLA-%' OR folio_origen LIKE 'TEST-%');

-- 2. Eliminar logs de desbloqueo y bloqueos de carga sobre declaraciones dummy
DELETE FROM desbloqueo_carga_log 
WHERE bloqueo_id IN (
    SELECT id FROM bloqueo_carga 
    WHERE (declaracion_tipo = 'RECOLECTOR' AND declaracion_id IN (SELECT id FROM declaracion_recolector WHERE folio_origen LIKE 'DUMMY-%' OR folio_origen LIKE 'FOL-REC-%'))
       OR (declaracion_tipo = 'ARMADOR' AND declaracion_id IN (SELECT id FROM declaracion_armador WHERE folio_origen LIKE 'DUMMY-%' OR folio_origen LIKE 'FOL-ARM-%'))
       OR (declaracion_tipo = 'AREA' AND declaracion_id IN (SELECT id FROM declaracion_area WHERE folio_origen LIKE 'DUMMY-%' OR folio_origen LIKE 'FOL-ARE-%'))
       OR (declaracion_tipo = 'COMERCIALIZADOR' AND declaracion_id IN (SELECT id FROM declaracion_comercializador WHERE folio_origen LIKE 'DUMMY-%' OR folio_origen LIKE 'FOL-COM-%'))
       OR (declaracion_tipo = 'PLANTA_ABASTECIMIENTO' AND declaracion_id IN (SELECT id FROM declaracion_planta_abastecimiento WHERE folio_origen LIKE 'DUMMY-%' OR folio_origen LIKE 'FOL-PLA-%'))
);

DELETE FROM bloqueo_carga 
WHERE (declaracion_tipo = 'RECOLECTOR' AND declaracion_id IN (SELECT id FROM declaracion_recolector WHERE folio_origen LIKE 'DUMMY-%' OR folio_origen LIKE 'FOL-REC-%'))
   OR (declaracion_tipo = 'ARMADOR' AND declaracion_id IN (SELECT id FROM declaracion_armador WHERE folio_origen LIKE 'DUMMY-%' OR folio_origen LIKE 'FOL-ARM-%'))
   OR (declaracion_tipo = 'AREA' AND declaracion_id IN (SELECT id FROM declaracion_area WHERE folio_origen LIKE 'DUMMY-%' OR folio_origen LIKE 'FOL-ARE-%'))
   OR (declaracion_tipo = 'COMERCIALIZADOR' AND declaracion_id IN (SELECT id FROM declaracion_comercializador WHERE folio_origen LIKE 'DUMMY-%' OR folio_origen LIKE 'FOL-COM-%'))
   OR (declaracion_tipo = 'PLANTA_ABASTECIMIENTO' AND declaracion_id IN (SELECT id FROM declaracion_planta_abastecimiento WHERE folio_origen LIKE 'DUMMY-%' OR folio_origen LIKE 'FOL-PLA-%'));

-- 3. Eliminar Planta de Producción dummy
DELETE FROM declaracion_planta_produccion 
WHERE folio_origen LIKE 'DUMMY-%' OR folio_origen LIKE 'TEST-%';

-- 4. Eliminar Planta de Abastecimiento dummy
DELETE FROM declaracion_planta_abastecimiento 
WHERE folio_origen LIKE 'DUMMY-%' 
   OR folio_origen LIKE 'FOL-PLA-%' 
   OR folio_origen LIKE 'TEST-%' 
   OR folio_declaracion_a_pla LIKE 'DUMMY-%' 
   OR folio_declaracion_a_pla LIKE 'FOL-%';

-- 5. Eliminar Comercializador dummy
DELETE FROM declaracion_comercializador 
WHERE folio_origen LIKE 'DUMMY-%' 
   OR folio_origen LIKE 'FOL-COM-%' 
   OR folio_origen LIKE 'TEST-%' 
   OR folio_desembarque_ac LIKE 'DUMMY-%' 
   OR folio_desembarque_ac LIKE 'FOL-%';

-- 6. Eliminar Orígenes dummy (Recolector, Armador, Área)
DELETE FROM declaracion_recolector 
WHERE folio_origen LIKE 'DUMMY-%' 
   OR folio_origen LIKE 'FOL-REC-%' 
   OR folio_origen LIKE 'TEST-%'
   OR folio_desembarque_ro LIKE 'DUMMY-%'
   OR folio_desembarque_ro LIKE 'FOL-%';

DELETE FROM declaracion_armador 
WHERE folio_origen LIKE 'DUMMY-%' 
   OR folio_origen LIKE 'FOL-ARM-%' 
   OR folio_origen LIKE 'TEST-%'
   OR folio_desembarque_da LIKE 'DUMMY-%'
   OR folio_desembarque_da LIKE 'FOL-%';

DELETE FROM declaracion_area 
WHERE folio_origen LIKE 'DUMMY-%' 
   OR folio_origen LIKE 'FOL-ARE-%' 
   OR folio_origen LIKE 'TEST-%'
   OR folio_desembarque_amerb LIKE 'DUMMY-%'
   OR folio_desembarque_amerb LIKE 'FOL-%';

-- 7. Eliminar Cuotas de extracción dummy
-- Si la tabla cuota_extraccion_comuna existe, se limpian sus referencias primero
SET @existe_cuota_comuna = (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'cuota_extraccion_comuna');
SET @sql_del_cuota_comuna = IF(@existe_cuota_comuna > 0, 
    'DELETE FROM cuota_extraccion_comuna WHERE cuota_id IN (SELECT id FROM cuota_extraccion WHERE resolucion LIKE "%DUMMY%" OR resolucion LIKE "%TEST%" OR resolucion LIKE "%PRUEBA%")', 
    'DO 0');
PREPARE stmt_del_cc FROM @sql_del_cuota_comuna;
EXECUTE stmt_del_cc;
DEALLOCATE PREPARE stmt_del_cc;

DELETE FROM cuota_extraccion 
WHERE resolucion LIKE '%DUMMY%' 
   OR resolucion LIKE '%TEST%' 
   OR resolucion LIKE '%PRUEBA%';

-- IMPORTANTE: Por defecto ROLLBACK para evitar accidentes si alguien lo corre sin querer.
-- Cambiar a COMMIT sólo cuando se ejecute intencionalmente en el ambiente correspondiente.
ROLLBACK;
-- COMMIT;
