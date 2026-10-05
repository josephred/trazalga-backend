-- ================================================================================
-- TRAZALGA — AUDITORÍA DE FECHAS DE CUOTAS MENSUALES (T0.3)
-- ================================================================================
-- Fecha: 3 de octubre de 2026
-- Propósito: Detectar cuotas afectadas por el hallazgo K1 (desfase por zona horaria 'Z'
--            donde Jackson/MySQL almacena el último día del mes anterior, ej. 28-02-2026
--            en lugar de 01-03-2026).
-- Regla de corrección: NO aplicar UPDATE manual directo en BD; cualquier cuota afectada
--                      se regulariza editando desde el formulario corregido (T1.4).
-- ================================================================================

-- 1. Cuotas MENSUALES con fecha_inicio en el último día de un mes (síntoma directo de desfase Z)
SELECT 
    id,
    periodo,
    nivel_agregacion,
    ambito,
    resolucion,
    limite_kg,
    fecha_inicio,
    fecha_fin,
    comuna_id,
    region_id,
    especie_id,
    activo,
    'FECHA_INICIO_FIN_DE_MES' AS anomalia_detectada
FROM cuota_extraccion
WHERE periodo = 'MENSUAL'
  AND activo = 1
  AND fecha_inicio IS NOT NULL
  AND fecha_inicio = LAST_DAY(fecha_inicio);

-- 2. Cuotas MENSUALES donde el mes de inicio y el mes de fin NO coinciden
SELECT 
    id,
    periodo,
    nivel_agregacion,
    ambito,
    resolucion,
    limite_kg,
    fecha_inicio,
    fecha_fin,
    comuna_id,
    region_id,
    especie_id,
    activo,
    'INICIO_FIN_MESES_DISTINTOS' AS anomalia_detectada
FROM cuota_extraccion
WHERE periodo = 'MENSUAL'
  AND activo = 1
  AND fecha_inicio IS NOT NULL
  AND fecha_fin IS NOT NULL
  AND (MONTH(fecha_inicio) <> MONTH(fecha_fin) OR YEAR(fecha_inicio) <> YEAR(fecha_fin));

-- 3. Cuotas MENSUALES donde fecha_inicio no comienza el día 1 del mes
SELECT 
    id,
    periodo,
    nivel_agregacion,
    ambito,
    resolucion,
    limite_kg,
    fecha_inicio,
    fecha_fin,
    DAY(fecha_inicio) AS dia_inicio,
    activo,
    'INICIO_NO_ES_PRIMERO_DE_MES' AS anomalia_detectada
FROM cuota_extraccion
WHERE periodo = 'MENSUAL'
  AND activo = 1
  AND fecha_inicio IS NOT NULL
  AND DAY(fecha_inicio) <> 1;

-- 4. Resumen general de cuotas mensuales activas y diagnóstico de estado
SELECT 
    COUNT(*) AS total_cuotas_mensuales_activas,
    SUM(CASE WHEN fecha_inicio IS NULL OR fecha_fin IS NULL THEN 1 ELSE 0 END) AS sin_fechas_definidas,
    SUM(CASE WHEN fecha_inicio = LAST_DAY(fecha_inicio) THEN 1 ELSE 0 END) AS con_inicio_ultimo_dia,
    SUM(CASE WHEN MONTH(fecha_inicio) <> MONTH(fecha_fin) OR YEAR(fecha_inicio) <> YEAR(fecha_fin) THEN 1 ELSE 0 END) AS con_meses_desalineados,
    SUM(CASE WHEN DAY(fecha_inicio) = 1 AND fecha_fin = LAST_DAY(fecha_fin) AND MONTH(fecha_inicio) = MONTH(fecha_fin) THEN 1 ELSE 0 END) AS cuotas_mensuales_perfectas
FROM cuota_extraccion
WHERE periodo = 'MENSUAL'
  AND activo = 1;
