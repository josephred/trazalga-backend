-- =========================================================================
-- Script SQL: Inserción de declaraciones dummy de Armador para Julio 2026
-- Objetivo: Dotar de métricas y gráficos visibles al widget
--           "Límite de Extracción Diario - LED (Indicador 4)"
--           (Monitoreo por EMBARCACION, Límite: 2.000 kg/día)
-- =========================================================================

-- Limpiar declaraciones previas de prueba si existen
DELETE FROM declaracion_armador 
WHERE folio_origen LIKE 'FOL-ARM-LED-%';

-- Variables dinámicas según el entorno
SET @v_usuario_armador_id = COALESCE((SELECT id FROM usuario WHERE perfil_id IN (SELECT id FROM perfil WHERE nombre LIKE '%ARMADOR%') ORDER BY id ASC LIMIT 1), 12);
SET @v_usuario_dest_id    = COALESCE((SELECT id FROM usuario WHERE perfil_id IN (SELECT id FROM perfil WHERE nombre LIKE '%COMER%') ORDER BY id ASC LIMIT 1), 14);
SET @v_buzo_id            = COALESCE((SELECT id FROM buzo ORDER BY id ASC LIMIT 1), 9);
SET @v_caleta_id          = COALESCE((SELECT id FROM caleta ORDER BY id ASC LIMIT 1), 1);
SET @v_comuna_id          = COALESCE((SELECT comuna_id FROM caleta WHERE id = @v_caleta_id LIMIT 1), (SELECT id FROM comuna ORDER BY id ASC LIMIT 1));
SET @v_especie_id         = 1; -- HUIRO PALO
SET @v_ext_barreteado_id  = COALESCE((SELECT id FROM extraccion_tipo WHERE nombre LIKE '%Barreteado%' LIMIT 1), 2);
SET @v_comp_id            = COALESCE((SELECT id FROM composicion ORDER BY id ASC LIMIT 1), 1);
SET @v_hum_id             = COALESCE((SELECT id FROM humedad_estado ORDER BY id ASC LIMIT 1), 1);

-- 3 embarcaciones distintas para representar los 3 estados
SET @v_emb_1 = COALESCE((SELECT id FROM embarcacion ORDER BY id ASC LIMIT 1), 2162);
SET @v_emb_2 = COALESCE((SELECT id FROM embarcacion ORDER BY id ASC LIMIT 1 OFFSET 1), 2168);
SET @v_emb_3 = COALESCE((SELECT id FROM embarcacion ORDER BY id ASC LIMIT 1 OFFSET 2), 2195);

-- -------------------------------------------------------------------------
-- 1. Apertura de Julio (2026-06-30 y 2026-07-01 para cubrir rango y UTC shift)
-- -------------------------------------------------------------------------
-- Embarcación 1: Dentro de límite (1.200 kg -> 60.0%) [NORMAL]
INSERT INTO declaracion_armador (
    usuario_id, folio_origen, folio_desembarque_da, fecha_extraccion, fecha_declaracion, hora,
    embarcacion_id, buzo_id, desembarque, captura, tipo_destinatario, usuario_destinatario_id,
    caleta_id, comuna_id, especie_id, composicion_id, humedad_estado_id, extraccion_tipo_id,
    factor_aplicado, factor_conversion_id
) VALUES 
(@v_usuario_armador_id, 'FOL-ARM-LED-01-A-1', 'DA-ARM-LED-01-A-1', '2026-06-30', '2026-06-30', '10:00:00', @v_emb_1, @v_buzo_id, 1200.00, 1356.00, 'comercializador', @v_usuario_dest_id, @v_caleta_id, @v_comuna_id, @v_especie_id, @v_comp_id, @v_hum_id, @v_ext_barreteado_id, 1.1300, 1),
(@v_usuario_armador_id, 'FOL-ARM-LED-01-A-2', 'DA-ARM-LED-01-A-2', '2026-07-01', '2026-07-01', '10:00:00', @v_emb_1, @v_buzo_id, 1200.00, 1356.00, 'comercializador', @v_usuario_dest_id, @v_caleta_id, @v_comuna_id, @v_especie_id, @v_comp_id, @v_hum_id, @v_ext_barreteado_id, 1.1300, 1);

-- Embarcación 2: Alerta / Tolerancia (1.850 kg -> 92.5%) [ADVERTENCIA]
INSERT INTO declaracion_armador (
    usuario_id, folio_origen, folio_desembarque_da, fecha_extraccion, fecha_declaracion, hora,
    embarcacion_id, buzo_id, desembarque, captura, tipo_destinatario, usuario_destinatario_id,
    caleta_id, comuna_id, especie_id, composicion_id, humedad_estado_id, extraccion_tipo_id,
    factor_aplicado, factor_conversion_id
) VALUES 
(@v_usuario_armador_id, 'FOL-ARM-LED-01-B-1', 'DA-ARM-LED-01-B-1', '2026-06-30', '2026-06-30', '11:00:00', @v_emb_2, @v_buzo_id, 1850.00, 2090.50, 'comercializador', @v_usuario_dest_id, @v_caleta_id, @v_comuna_id, @v_especie_id, @v_comp_id, @v_hum_id, @v_ext_barreteado_id, 1.1300, 1),
(@v_usuario_armador_id, 'FOL-ARM-LED-01-B-2', 'DA-ARM-LED-01-B-2', '2026-07-01', '2026-07-01', '11:00:00', @v_emb_2, @v_buzo_id, 1850.00, 2090.50, 'comercializador', @v_usuario_dest_id, @v_caleta_id, @v_comuna_id, @v_especie_id, @v_comp_id, @v_hum_id, @v_ext_barreteado_id, 1.1300, 1);

-- Embarcación 3: Excedido (> 100%) (2.550 kg -> 127.5%) [EXCEDIDO]
INSERT INTO declaracion_armador (
    usuario_id, folio_origen, folio_desembarque_da, fecha_extraccion, fecha_declaracion, hora,
    embarcacion_id, buzo_id, desembarque, captura, tipo_destinatario, usuario_destinatario_id,
    caleta_id, comuna_id, especie_id, composicion_id, humedad_estado_id, extraccion_tipo_id,
    factor_aplicado, factor_conversion_id
) VALUES 
(@v_usuario_armador_id, 'FOL-ARM-LED-01-C-1', 'DA-ARM-LED-01-C-1', '2026-06-30', '2026-06-30', '11:30:00', @v_emb_3, @v_buzo_id, 2550.00, 2881.50, 'comercializador', @v_usuario_dest_id, @v_caleta_id, @v_comuna_id, @v_especie_id, @v_comp_id, @v_hum_id, @v_ext_barreteado_id, 1.1300, 1),
(@v_usuario_armador_id, 'FOL-ARM-LED-01-C-2', 'DA-ARM-LED-01-C-2', '2026-07-01', '2026-07-01', '11:30:00', @v_emb_3, @v_buzo_id, 2550.00, 2881.50, 'comercializador', @v_usuario_dest_id, @v_caleta_id, @v_comuna_id, @v_especie_id, @v_comp_id, @v_hum_id, @v_ext_barreteado_id, 1.1300, 1);

-- -------------------------------------------------------------------------
-- 2. Quincena de Julio (2026-07-15)
-- -------------------------------------------------------------------------
INSERT INTO declaracion_armador (
    usuario_id, folio_origen, folio_desembarque_da, fecha_extraccion, fecha_declaracion, hora,
    embarcacion_id, buzo_id, desembarque, captura, tipo_destinatario, usuario_destinatario_id,
    caleta_id, comuna_id, especie_id, composicion_id, humedad_estado_id, extraccion_tipo_id,
    factor_aplicado, factor_conversion_id
) VALUES 
(@v_usuario_armador_id, 'FOL-ARM-LED-15-A', 'DA-ARM-LED-15-A', '2026-07-15', '2026-07-15', '10:00:00', @v_emb_1, @v_buzo_id, 1350.00, 1525.50, 'comercializador', @v_usuario_dest_id, @v_caleta_id, @v_comuna_id, @v_especie_id, @v_comp_id, @v_hum_id, @v_ext_barreteado_id, 1.1300, 1),
(@v_usuario_armador_id, 'FOL-ARM-LED-15-B', 'DA-ARM-LED-15-B', '2026-07-15', '2026-07-15', '11:00:00', @v_emb_2, @v_buzo_id, 1820.00, 2056.60, 'comercializador', @v_usuario_dest_id, @v_caleta_id, @v_comuna_id, @v_especie_id, @v_comp_id, @v_hum_id, @v_ext_barreteado_id, 1.1300, 1),
(@v_usuario_armador_id, 'FOL-ARM-LED-15-C', 'DA-ARM-LED-15-C', '2026-07-15', '2026-07-15', '11:30:00', @v_emb_3, @v_buzo_id, 2450.00, 2768.50, 'comercializador', @v_usuario_dest_id, @v_caleta_id, @v_comuna_id, @v_especie_id, @v_comp_id, @v_hum_id, @v_ext_barreteado_id, 1.1300, 1);

-- -------------------------------------------------------------------------
-- 3. Cierre de Julio (2026-07-30 y 2026-07-31)
-- -------------------------------------------------------------------------
INSERT INTO declaracion_armador (
    usuario_id, folio_origen, folio_desembarque_da, fecha_extraccion, fecha_declaracion, hora,
    embarcacion_id, buzo_id, desembarque, captura, tipo_destinatario, usuario_destinatario_id,
    caleta_id, comuna_id, especie_id, composicion_id, humedad_estado_id, extraccion_tipo_id,
    factor_aplicado, factor_conversion_id
) VALUES 
(@v_usuario_armador_id, 'FOL-ARM-LED-31-A-1', 'DA-ARM-LED-31-A-1', '2026-07-30', '2026-07-30', '10:00:00', @v_emb_1, @v_buzo_id, 1400.00, 1582.00, 'comercializador', @v_usuario_dest_id, @v_caleta_id, @v_comuna_id, @v_especie_id, @v_comp_id, @v_hum_id, @v_ext_barreteado_id, 1.1300, 1),
(@v_usuario_armador_id, 'FOL-ARM-LED-31-A-2', 'DA-ARM-LED-31-A-2', '2026-07-31', '2026-07-31', '10:00:00', @v_emb_1, @v_buzo_id, 1400.00, 1582.00, 'comercializador', @v_usuario_dest_id, @v_caleta_id, @v_comuna_id, @v_especie_id, @v_comp_id, @v_hum_id, @v_ext_barreteado_id, 1.1300, 1),
(@v_usuario_armador_id, 'FOL-ARM-LED-31-B-1', 'DA-ARM-LED-31-B-1', '2026-07-30', '2026-07-30', '11:00:00', @v_emb_2, @v_buzo_id, 1900.00, 2147.00, 'comercializador', @v_usuario_dest_id, @v_caleta_id, @v_comuna_id, @v_especie_id, @v_comp_id, @v_hum_id, @v_ext_barreteado_id, 1.1300, 1),
(@v_usuario_armador_id, 'FOL-ARM-LED-31-B-2', 'DA-ARM-LED-31-B-2', '2026-07-31', '2026-07-31', '11:00:00', @v_emb_2, @v_buzo_id, 1900.00, 2147.00, 'comercializador', @v_usuario_dest_id, @v_caleta_id, @v_comuna_id, @v_especie_id, @v_comp_id, @v_hum_id, @v_ext_barreteado_id, 1.1300, 1),
(@v_usuario_armador_id, 'FOL-ARM-LED-31-C-1', 'DA-ARM-LED-31-C-1', '2026-07-30', '2026-07-30', '11:30:00', @v_emb_3, @v_buzo_id, 2600.00, 2938.00, 'comercializador', @v_usuario_dest_id, @v_caleta_id, @v_comuna_id, @v_especie_id, @v_comp_id, @v_hum_id, @v_ext_barreteado_id, 1.1300, 1),
(@v_usuario_armador_id, 'FOL-ARM-LED-31-C-2', 'DA-ARM-LED-31-C-2', '2026-07-31', '2026-07-31', '11:30:00', @v_emb_3, @v_buzo_id, 2600.00, 2938.00, 'comercializador', @v_usuario_dest_id, @v_caleta_id, @v_comuna_id, @v_especie_id, @v_comp_id, @v_hum_id, @v_ext_barreteado_id, 1.1300, 1);
