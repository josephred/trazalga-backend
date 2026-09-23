-- ==============================================================================
-- Script: actualizar_factores_todas_especies.sql
-- Propósito: Homologar los factores de conversión biológica para TODAS las especies
--            en los 4 estados de humedad según la curva oficial de Sernapesca:
--            - Húmedo (1):      1.1300
--            - Semi Húmedo (2): 1.7500
--            - Semi Seco (3):   2.7000
--            - Seco (4):        3.5800
-- ==============================================================================

USE trazalga;

SET @resolucion = 'Res. Ex. Sernapesca 13-09-2026';
SET @fecha_inicio = '2024-01-01';

-- 1. Actualizar filas activas existentes con los nuevos factores oficiales
UPDATE factor_conversion
SET factor = 1.1300,
    resolucion = @resolucion,
    descripcion = 'Factor oficial Sernapesca Húmedo (1.1300)'
WHERE humedad_estado_id = 1 AND activo = 1;

UPDATE factor_conversion
SET factor = 1.7500,
    resolucion = @resolucion,
    descripcion = 'Factor oficial Sernapesca Semi Húmedo (1.7500)'
WHERE humedad_estado_id = 2 AND activo = 1;

UPDATE factor_conversion
SET factor = 2.7000,
    resolucion = @resolucion,
    descripcion = 'Factor oficial Sernapesca Semi Seco (2.7000)'
WHERE humedad_estado_id = 3 AND activo = 1;

UPDATE factor_conversion
SET factor = 3.5800,
    resolucion = @resolucion,
    descripcion = 'Factor oficial Sernapesca Seco (3.5800)'
WHERE humedad_estado_id = 4 AND activo = 1;

-- 2. Insertar combinaciones faltantes para cualquier especie/humedad que no tenga fila activa
INSERT INTO factor_conversion (especie_id, humedad_estado_id, factor, vigencia_inicio, vigencia_fin, resolucion, descripcion, activo)
SELECT e.id, 1, 1.1300, @fecha_inicio, NULL, @resolucion, 'Factor oficial Sernapesca Húmedo (1.1300)', 1
FROM especie e
WHERE NOT EXISTS (
    SELECT 1 FROM factor_conversion fc 
    WHERE fc.especie_id = e.id AND fc.humedad_estado_id = 1 AND fc.activo = 1
);

INSERT INTO factor_conversion (especie_id, humedad_estado_id, factor, vigencia_inicio, vigencia_fin, resolucion, descripcion, activo)
SELECT e.id, 2, 1.7500, @fecha_inicio, NULL, @resolucion, 'Factor oficial Sernapesca Semi Húmedo (1.7500)', 1
FROM especie e
WHERE NOT EXISTS (
    SELECT 1 FROM factor_conversion fc 
    WHERE fc.especie_id = e.id AND fc.humedad_estado_id = 2 AND fc.activo = 1
);

INSERT INTO factor_conversion (especie_id, humedad_estado_id, factor, vigencia_inicio, vigencia_fin, resolucion, descripcion, activo)
SELECT e.id, 3, 2.7000, @fecha_inicio, NULL, @resolucion, 'Factor oficial Sernapesca Semi Seco (2.7000)', 1
FROM especie e
WHERE NOT EXISTS (
    SELECT 1 FROM factor_conversion fc 
    WHERE fc.especie_id = e.id AND fc.humedad_estado_id = 3 AND fc.activo = 1
);

INSERT INTO factor_conversion (especie_id, humedad_estado_id, factor, vigencia_inicio, vigencia_fin, resolucion, descripcion, activo)
SELECT e.id, 4, 3.5800, @fecha_inicio, NULL, @resolucion, 'Factor oficial Sernapesca Seco (3.5800)', 1
FROM especie e
WHERE NOT EXISTS (
    SELECT 1 FROM factor_conversion fc 
    WHERE fc.especie_id = e.id AND fc.humedad_estado_id = 4 AND fc.activo = 1
);

-- 3. Verificación de la matriz completa (16 especies x 4 humedades = 64 combinaciones activas)
SELECT 
    h.id AS hum_id,
    h.nombre AS estado_humedad,
    COUNT(DISTINCT fc.especie_id) AS total_especies,
    MIN(fc.factor) AS factor_min,
    MAX(fc.factor) AS factor_max,
    COUNT(*) AS total_filas_activas
FROM factor_conversion fc
JOIN humedad_estado h ON fc.humedad_estado_id = h.id
WHERE fc.activo = 1
GROUP BY h.id, h.nombre
ORDER BY h.id;

SELECT 
    COUNT(*) AS total_factores_activos,
    COUNT(DISTINCT especie_id) AS total_especies_cubiertas,
    SUM(CASE WHEN resolucion = 'Res. Ex. Sernapesca 13-09-2026' THEN 1 ELSE 0 END) AS total_oficiales
FROM factor_conversion
WHERE activo = 1;
