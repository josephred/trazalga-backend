-- ================================================================================
-- TRAZALGA — AUDITORÍA DE DATOS DE PRUEBA POR AMBIENTE (T0.2)
-- ================================================================================
-- Fecha: 3 de octubre de 2026
-- Propósito: Contar de forma segura (SÓLO LECTURA) la cantidad de registros de prueba
--            existentes en cada tabla transaccional antes de la migración y carga 2026.
-- Prefijos auditados: 'DUMMY-%', 'FOL-REC-%', 'FOL-ARM-%', 'FOL-ARE-%', 'FOL-COM-%',
--                     'FOL-PLA-%', 'TEST-%', 'DAPLA-DUMMY-%', 'RES-DUMMY-%'
-- ================================================================================

SELECT '--- 1. DECLARACIONES DE RECOLECTOR ---' AS seccion;
SELECT 
    COUNT(*) AS total_recolector,
    SUM(CASE WHEN folio_origen LIKE 'DUMMY-%' OR folio_origen LIKE 'FOL-REC-%' OR folio_origen LIKE 'TEST-%' THEN 1 ELSE 0 END) AS total_dummy_folio_origen,
    SUM(CASE WHEN folio_desembarque_ro LIKE 'DUMMY-%' OR folio_desembarque_ro LIKE 'FOL-%' OR folio_desembarque_ro LIKE 'TEST-%' THEN 1 ELSE 0 END) AS total_dummy_folio_desembarque,
    SUM(CASE WHEN nombre LIKE '%Dummy%' OR codigo_sernapesca LIKE '%Dummy%' THEN 1 ELSE 0 END) AS total_dummy_por_nombre
FROM declaracion_recolector;

SELECT '--- 2. DECLARACIONES DE ARMADOR ---' AS seccion;
SELECT 
    COUNT(*) AS total_armador,
    SUM(CASE WHEN folio_origen LIKE 'DUMMY-%' OR folio_origen LIKE 'FOL-ARM-%' OR folio_origen LIKE 'TEST-%' THEN 1 ELSE 0 END) AS total_dummy_folio_origen,
    SUM(CASE WHEN folio_desembarque_da LIKE 'DUMMY-%' OR folio_desembarque_da LIKE 'FOL-%' OR folio_desembarque_da LIKE 'TEST-%' THEN 1 ELSE 0 END) AS total_dummy_folio_desembarque
FROM declaracion_armador;

SELECT '--- 3. DECLARACIONES DE AREA (AMERB) ---' AS seccion;
SELECT 
    COUNT(*) AS total_area,
    SUM(CASE WHEN folio_origen LIKE 'DUMMY-%' OR folio_origen LIKE 'FOL-ARE-%' OR folio_origen LIKE 'TEST-%' THEN 1 ELSE 0 END) AS total_dummy_folio_origen,
    SUM(CASE WHEN folio_desembarque_amerb LIKE 'DUMMY-%' OR folio_desembarque_amerb LIKE 'FOL-%' OR folio_desembarque_amerb LIKE 'TEST-%' THEN 1 ELSE 0 END) AS total_dummy_folio_desembarque
FROM declaracion_area;

SELECT '--- 4. DECLARACIONES DE COMERCIALIZADOR ---' AS seccion;
SELECT 
    COUNT(*) AS total_comercializador,
    SUM(CASE WHEN folio_origen LIKE 'DUMMY-%' OR folio_origen LIKE 'FOL-COM-%' OR folio_origen LIKE 'TEST-%' THEN 1 ELSE 0 END) AS total_dummy_folio_origen,
    SUM(CASE WHEN folio_desembarque_ac LIKE 'DUMMY-%' OR folio_desembarque_ac LIKE 'FOL-%' OR folio_desembarque_ac LIKE 'TEST-%' THEN 1 ELSE 0 END) AS total_dummy_folio_desembarque,
    SUM(CASE WHEN documento_tributario_origen_numero LIKE 'DUMMY-%' OR documento_tributario_destino_numero LIKE 'DUMMY-%' THEN 1 ELSE 0 END) AS total_dummy_por_documento
FROM declaracion_comercializador;

SELECT '--- 5. DECLARACIONES DE PLANTA DE ABASTECIMIENTO ---' AS seccion;
SELECT 
    COUNT(*) AS total_planta_abastecimiento,
    SUM(CASE WHEN folio_origen LIKE 'DUMMY-%' OR folio_origen LIKE 'FOL-PLA-%' OR folio_origen LIKE 'TEST-%' THEN 1 ELSE 0 END) AS total_dummy_folio_origen,
    SUM(CASE WHEN folio_declaracion_a_pla LIKE 'DUMMY-%' OR folio_declaracion_a_pla LIKE 'FOL-%' OR folio_declaracion_a_pla LIKE 'TEST-%' THEN 1 ELSE 0 END) AS total_dummy_folio_declaracion
FROM declaracion_planta_abastecimiento;

SELECT '--- 6. DECLARACIONES DE PLANTA DE PRODUCCIÓN ---' AS seccion;
SELECT 
    COUNT(*) AS total_planta_produccion,
    SUM(CASE WHEN folio_origen LIKE 'DUMMY-%' OR folio_origen LIKE 'TEST-%' THEN 1 ELSE 0 END) AS total_dummy_folio_origen
FROM declaracion_planta_produccion;

SELECT '--- 7. MARCAS NORMATIVAS ASOCIADAS A DUMMIES ---' AS seccion;
SELECT 
    COUNT(*) AS total_marcas,
    SUM(CASE 
        WHEN declaracion_tipo = 'RECOLECTOR' AND declaracion_id IN (SELECT id FROM declaracion_recolector WHERE folio_origen LIKE 'DUMMY-%' OR folio_origen LIKE 'FOL-REC-%') THEN 1
        WHEN declaracion_tipo = 'ARMADOR' AND declaracion_id IN (SELECT id FROM declaracion_armador WHERE folio_origen LIKE 'DUMMY-%' OR folio_origen LIKE 'FOL-ARM-%') THEN 1
        WHEN declaracion_tipo = 'AREA' AND declaracion_id IN (SELECT id FROM declaracion_area WHERE folio_origen LIKE 'DUMMY-%' OR folio_origen LIKE 'FOL-ARE-%') THEN 1
        WHEN declaracion_tipo = 'COMERCIALIZADOR' AND declaracion_id IN (SELECT id FROM declaracion_comercializador WHERE folio_origen LIKE 'DUMMY-%' OR folio_origen LIKE 'FOL-COM-%') THEN 1
        WHEN declaracion_tipo = 'PLANTA_ABASTECIMIENTO' AND declaracion_id IN (SELECT id FROM declaracion_planta_abastecimiento WHERE folio_origen LIKE 'DUMMY-%' OR folio_origen LIKE 'FOL-PLA-%') THEN 1
        ELSE 0 
    END) AS marcas_en_declaraciones_dummy
FROM declaracion_marca;

SELECT '--- 8. BLOQUEOS DE CARGA ASOCIADOS A DUMMIES ---' AS seccion;
SELECT 
    COUNT(*) AS total_bloqueos,
    SUM(CASE 
        WHEN declaracion_tipo = 'RECOLECTOR' AND declaracion_id IN (SELECT id FROM declaracion_recolector WHERE folio_origen LIKE 'DUMMY-%' OR folio_origen LIKE 'FOL-REC-%') THEN 1
        WHEN declaracion_tipo = 'ARMADOR' AND declaracion_id IN (SELECT id FROM declaracion_armador WHERE folio_origen LIKE 'DUMMY-%' OR folio_origen LIKE 'FOL-ARM-%') THEN 1
        WHEN declaracion_tipo = 'AREA' AND declaracion_id IN (SELECT id FROM declaracion_area WHERE folio_origen LIKE 'DUMMY-%' OR folio_origen LIKE 'FOL-ARE-%') THEN 1
        WHEN declaracion_tipo = 'COMERCIALIZADOR' AND declaracion_id IN (SELECT id FROM declaracion_comercializador WHERE folio_origen LIKE 'DUMMY-%' OR folio_origen LIKE 'FOL-COM-%') THEN 1
        WHEN declaracion_tipo = 'PLANTA_ABASTECIMIENTO' AND declaracion_id IN (SELECT id FROM declaracion_planta_abastecimiento WHERE folio_origen LIKE 'DUMMY-%' OR folio_origen LIKE 'FOL-PLA-%') THEN 1
        ELSE 0 
    END) AS bloqueos_en_declaraciones_dummy
FROM bloqueo_carga;

SELECT '--- 9. CUOTAS DE EXTRACCIÓN DE PRUEBA ---' AS seccion;
SELECT 
    COUNT(*) AS total_cuotas,
    SUM(CASE WHEN resolucion LIKE '%DUMMY%' OR resolucion LIKE '%TEST%' OR resolucion LIKE '%PRUEBA%' THEN 1 ELSE 0 END) AS cuotas_dummy_resolucion,
    SUM(CASE WHEN limite_kg = 999999 OR limite_kg = 12345 THEN 1 ELSE 0 END) AS cuotas_dummy_limite_test
FROM cuota_extraccion;

SELECT '--- 10. RESUMEN GLOBAL ESTIMADO ---' AS seccion;
SELECT 
    (SELECT COUNT(*) FROM declaracion_recolector WHERE folio_origen LIKE 'DUMMY-%' OR folio_origen LIKE 'FOL-REC-%') AS rec_dummy,
    (SELECT COUNT(*) FROM declaracion_armador WHERE folio_origen LIKE 'DUMMY-%' OR folio_origen LIKE 'FOL-ARM-%') AS arm_dummy,
    (SELECT COUNT(*) FROM declaracion_area WHERE folio_origen LIKE 'DUMMY-%' OR folio_origen LIKE 'FOL-ARE-%') AS area_dummy,
    (SELECT COUNT(*) FROM declaracion_comercializador WHERE folio_origen LIKE 'DUMMY-%' OR folio_origen LIKE 'FOL-COM-%') AS com_dummy,
    (SELECT COUNT(*) FROM declaracion_planta_abastecimiento WHERE folio_origen LIKE 'DUMMY-%' OR folio_origen LIKE 'FOL-PLA-%') AS pla_dummy,
    (SELECT COUNT(*) FROM cuota_extraccion WHERE resolucion LIKE '%DUMMY%' OR resolucion LIKE '%TEST%') AS cuotas_dummy;
