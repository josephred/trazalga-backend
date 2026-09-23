-- =========================================================================
-- Script SQL: Inserción de declaraciones dummy de Área de Manejo (AMERB)
--             para Julio 2026
-- Objetivo: Generar coincidencias sospechosas para el widget:
--           "Doble Operación (ALA / AMERB)"
--           (Mismo actor, misma especie y mismo día con volúmenes similares)
-- =========================================================================

-- Limpiar declaraciones previas de prueba en declaracion_area si existen
DELETE FROM declaracion_area 
WHERE folio_origen LIKE 'FOL-AREA-DOBLE-%';

-- Variables dinámicas según el entorno
SET @v_amerb_1 = COALESCE((SELECT id FROM amerb WHERE nombre LIKE '%LENGUA%' LIMIT 1), (SELECT id FROM amerb ORDER BY id ASC LIMIT 1));
SET @v_amerb_2 = COALESCE((SELECT id FROM amerb WHERE nombre LIKE '%HUENTELAUQUEN%' LIMIT 1), (SELECT id FROM amerb ORDER BY id ASC LIMIT 1 OFFSET 1), @v_amerb_1);
SET @v_amerb_3 = COALESCE((SELECT id FROM amerb WHERE nombre LIKE '%PICHIDANGUI%' LIMIT 1), (SELECT id FROM amerb ORDER BY id ASC LIMIT 1 OFFSET 2), @v_amerb_1);

SET @v_caleta_id = COALESCE((SELECT id FROM caleta ORDER BY id ASC LIMIT 1), 1);
SET @v_comp_id   = COALESCE((SELECT id FROM composicion ORDER BY id ASC LIMIT 1), 1);
SET @v_hum_id    = COALESCE((SELECT id FROM humedad_estado ORDER BY id ASC LIMIT 1), 1);
SET @v_dest_id   = COALESCE((SELECT id FROM usuario WHERE perfil_id IN (SELECT id FROM perfil WHERE nombre LIKE '%COMER%') ORDER BY id ASC LIMIT 1), 14);

-- -------------------------------------------------------------------------
-- 1. Coincidencia 01-Jul-2026: Origen RECOLECTOR
-- ALA: declaracion_recolector (CAROLA, 24.300 kg)
-- AMERB: declaracion_area (CAROLA, 24.000 kg -> variación 1.2%)
-- -------------------------------------------------------------------------
SET @v_rec_user_01 = COALESCE((SELECT usuario_id FROM declaracion_recolector WHERE fecha_declaracion = '2026-07-01' AND especie_id = 2 LIMIT 1), 11);

INSERT INTO declaracion_area (
    usuario_id, folio_origen, folio_desembarque_amerb, fecha_extraccion, fecha_declaracion, hora,
    amerb_id, caleta_id, especie_id, composicion_id, humedad_estado_id, desembarque, captura,
    tipo_destinatario, usuario_destinatario_id, factor_aplicado
) VALUES (
    @v_rec_user_01, 'FOL-AREA-DOBLE-01', 'AMERB-DOBLE-01', '2026-07-01', '2026-07-01', '10:30:00',
    @v_amerb_1, @v_caleta_id, 2, @v_comp_id, @v_hum_id, 24000.00, 24000.00,
    'comercializador', @v_dest_id, 1.0000
);

-- -------------------------------------------------------------------------
-- 2. Coincidencia 15-Jul-2026: Origen ARMADOR
-- ALA: declaracion_armador (HUIRO PALO, 1.820 kg)
-- AMERB: declaracion_area (HUIRO PALO, 1.850 kg -> variación 1.6%)
-- -------------------------------------------------------------------------
SET @v_arm_user_15 = COALESCE((SELECT usuario_id FROM declaracion_armador WHERE fecha_declaracion = '2026-07-15' AND especie_id = 1 LIMIT 1), 11);

INSERT INTO declaracion_area (
    usuario_id, folio_origen, folio_desembarque_amerb, fecha_extraccion, fecha_declaracion, hora,
    amerb_id, caleta_id, especie_id, composicion_id, humedad_estado_id, desembarque, captura,
    tipo_destinatario, usuario_destinatario_id, factor_aplicado
) VALUES (
    @v_arm_user_15, 'FOL-AREA-DOBLE-15', 'AMERB-DOBLE-15', '2026-07-15', '2026-07-15', '14:00:00',
    @v_amerb_2, @v_caleta_id, 1, @v_comp_id, @v_hum_id, 1850.00, 2090.50,
    'comercializador', @v_dest_id, 1.1300
);

-- -------------------------------------------------------------------------
-- 3. Coincidencia 31-Jul-2026: Origen ARMADOR
-- ALA: declaracion_armador (HUIRO PALO, 1.900 kg)
-- AMERB: declaracion_area (HUIRO PALO, 1.940 kg -> variación 2.1%)
-- -------------------------------------------------------------------------
SET @v_arm_user_31 = COALESCE((SELECT usuario_id FROM declaracion_armador WHERE fecha_declaracion = '2026-07-31' AND especie_id = 1 LIMIT 1), 11);

INSERT INTO declaracion_area (
    usuario_id, folio_origen, folio_desembarque_amerb, fecha_extraccion, fecha_declaracion, hora,
    amerb_id, caleta_id, especie_id, composicion_id, humedad_estado_id, desembarque, captura,
    tipo_destinatario, usuario_destinatario_id, factor_aplicado
) VALUES (
    @v_arm_user_31, 'FOL-AREA-DOBLE-31', 'AMERB-DOBLE-31', '2026-07-31', '2026-07-31', '12:00:00',
    @v_amerb_3, @v_caleta_id, 1, @v_comp_id, @v_hum_id, 1940.00, 2192.20,
    'comercializador', @v_dest_id, 1.1300
);
