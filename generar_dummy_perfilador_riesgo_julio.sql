-- ============================================================================
-- SCRIPT: DATOS SINTÉTICOS PARA EL INDICADOR 9 — PERFILADOR DE RIESGO
-- Período objetivo: JULIO DE 2026 (2026-07-01 a 2026-07-31)
--
-- Complementa a `generar_dummy_variacion_peso_julio.sql` (prefijo DUMMY-JUL6-),
-- cuyos ocho lotes con romana ya producen por sí solos una distribución de
-- 2 verdes / 3 amarillos / 3 rojos. Lo que este script agrega:
--
--   · Tres comercializadores DISTINTOS, para que la vista «Jerarquía de
--     Actores» tenga a quién jerarquizar. Con un solo actor la tabla muestra
--     una sola fila y el indicador pierde su función de priorizar inspecciones.
--   · Los dos AGRAVANTES del algoritmo: un lote con marca EN_VEDA y otro con
--     LED_EXCEDIDO, que suben un nivel el riesgo del lote.
--   · Más celdas de la matriz Variación × Retención ocupadas.
--
-- Prefijo propio (DUMMY-JUL9-): los dos scripts conviven sin pisarse y cada
-- uno se revierte por separado.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 0. LIMPIEZA — deja la base como estaba antes de correr este script
-- ----------------------------------------------------------------------------
DELETE FROM declaracion_marca
 WHERE declaracion_tipo = 'RECOLECTOR'
   AND declaracion_id IN (SELECT id FROM declaracion_recolector
                           WHERE folio_origen LIKE 'DUMMY-JUL9-%');

DELETE FROM declaracion_planta_abastecimiento WHERE folio_origen LIKE 'DUMMY-JUL9-%';
DELETE FROM declaracion_comercializador       WHERE folio_origen LIKE 'DUMMY-JUL9-%';
DELETE FROM declaracion_recolector            WHERE folio_origen LIKE 'DUMMY-JUL9-%';

-- ----------------------------------------------------------------------------
-- 1. RESOLUCIÓN DE MAESTROS DINÁMICOS
-- ----------------------------------------------------------------------------
SET @user_rec = (SELECT u.id FROM usuario u JOIN perfil p ON u.perfil_id = p.id WHERE p.nombre LIKE '%RECOLECTOR%' ORDER BY u.id LIMIT 1);
SET @user_pla = (SELECT u.id FROM usuario u JOIN perfil p ON u.perfil_id = p.id WHERE p.nombre LIKE '%PLANTA%' ORDER BY u.id LIMIT 1);

-- Tres comercializadores distintos; si el catálogo tiene menos, se repiten
SET @com_a = (SELECT u.id FROM usuario u JOIN perfil p ON u.perfil_id = p.id WHERE p.nombre LIKE '%COMER%' ORDER BY u.id LIMIT 1 OFFSET 0);
SET @com_b = (SELECT u.id FROM usuario u JOIN perfil p ON u.perfil_id = p.id WHERE p.nombre LIKE '%COMER%' ORDER BY u.id LIMIT 1 OFFSET 1);
SET @com_c = (SELECT u.id FROM usuario u JOIN perfil p ON u.perfil_id = p.id WHERE p.nombre LIKE '%COMER%' ORDER BY u.id LIMIT 1 OFFSET 2);

SET @user_rec = COALESCE(@user_rec, (SELECT id FROM usuario ORDER BY id LIMIT 1));
SET @user_pla = COALESCE(@user_pla, (SELECT id FROM usuario ORDER BY id LIMIT 1));
SET @com_a    = COALESCE(@com_a, (SELECT id FROM usuario ORDER BY id LIMIT 1));
SET @com_b    = COALESCE(@com_b, @com_a);
SET @com_c    = COALESCE(@com_c, @com_b, @com_a);

SET @esp = COALESCE((SELECT id FROM especie WHERE UPPER(nombre) LIKE '%PALO%' LIMIT 1), 1);

-- Estados de humedad: excluir 'SEMI' explícitamente para evitar ambigüedades
SET @hum_hum = COALESCE((SELECT id FROM humedad_estado WHERE UPPER(nombre) NOT LIKE '%SEMI%' AND UPPER(nombre) NOT LIKE '%SEC%' LIMIT 1), 1);
SET @hum_sse = COALESCE((SELECT id FROM humedad_estado WHERE UPPER(nombre) LIKE '%SEMI%' AND UPPER(nombre) LIKE '%SEC%' LIMIT 1), 3);
SET @hum_sec = COALESCE((SELECT id FROM humedad_estado WHERE UPPER(nombre) NOT LIKE '%SEMI%' AND UPPER(nombre) LIKE '%SEC%' LIMIT 1), 4);

SET @caleta   = COALESCE((SELECT id FROM caleta ORDER BY id LIMIT 1), 1);
SET @comuna   = COALESCE((SELECT id FROM comuna ORDER BY id LIMIT 1), 30);
SET @ext_tipo = COALESCE((SELECT id FROM extraccion_tipo ORDER BY id LIMIT 1), 1);
SET @comp     = COALESCE((SELECT id FROM composicion ORDER BY id LIMIT 1), 1);

SET @f_hum = 1.1300;
SET @f_sse = 2.7000;
SET @f_sec = 3.5800;

-- ----------------------------------------------------------------------------
-- 1.1 Asegurar parámetros de configuración del Perfilador en la base
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO configuracion_general (clave, valor, descripcion, categoria) VALUES
('riesgo_activo', 'true', 'Interruptor maestro del perfilador de riesgo', 'RIESGO'),
('riesgo_variacion_amarillo_pct', '5.0', 'Umbral amarillo de variación', 'RIESGO'),
('riesgo_variacion_rojo_pct', '10.0', 'Umbral rojo de variación', 'RIESGO'),
('riesgo_dias_amarillo', '3', 'Días en bodega alerta amarilla', 'RIESGO'),
('riesgo_dias_rojo', '7', 'Días en bodega alerta roja', 'RIESGO'),
('riesgo_escala_humedo_sin_merma', 'ROJO', 'Escala por alga húmeda sin merma', 'RIESGO'),
('riesgo_agravante_veda_niveles', '1', 'Nivel agravante por veda', 'RIESGO'),
('riesgo_agravante_led_niveles', '1', 'Nivel agravante por LED excedido', 'RIESGO'),
('retencion_bodega_estados_sujetos', 'HUMEDO', 'Estados sujetos a retención', 'RIESGO'),
('bio_perdida_activo', 'true', 'Control bio pérdida activo', 'RIESGO');

-- ============================================================================
-- 2. LOS DIEZ LOTES
--
-- Cortes vigentes del perfilador: amarillo desde ±5 % de variación o 3 días de
-- retención; rojo desde ±10 % o 7 días. La retención sólo puntúa en los estados
-- sujetos a control (por defecto HÚMEDO), por eso un lote seco con diez días en
-- bodega se mantiene verde: el recurso ya está estabilizado.
--
--  Lote  Actor  Humedad     Origen  Destino    Δ%    Días   Nivel esperado
--  A1    A      Húmedo       1.300   1.300    0,0     6     ROJO  (biológica crítica)
--  A2    A      Húmedo         900     792  -12,0     8     ROJO  (variación + retención)
--  A3    A      Húmedo       1.000     930   -7,0     4     AMARILLO
--  A4    A      Húmedo         750     705   -6,0     2     AMARILLO → ROJO (EN_VEDA)
--  B1    B      Húmedo       1.100   1.039,5 -5,5     3     AMARILLO
--  B2    B      Semi Seco      800     768   -4,0     2     VERDE → AMARILLO (LED_EXCEDIDO)
--  B3    B      Húmedo         950     931   -2,0     4     AMARILLO (retención 4d húmedo)
--  B4    B      Seco           700     686   -2,0    10     VERDE (seco no sujeto a retención)
--  C1    C      Húmedo       1.200   1.164   -3,0     1     VERDE
--  C2    C      Semi Seco      650     643,5 -1,0     1     VERDE
-- ============================================================================

-- ---- A1 · húmedo 6 días con 0 % de merma: inconsistencia crítica ------------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL9-A1', @user_rec, '2026-07-02', '2026-07-02', '08:00:00',
    'Recolector Sintético A1', 'RPA-JUL9-A1', @caleta, @comuna, @esp, @ext_tipo,
    @comp, @hum_hum, 1300.00, 1300.00 * @f_hum, @f_hum,
    '78147430', 'Comercializador A', @com_a
);
INSERT INTO declaracion_comercializador (
    folio_origen, usuario_id, fecha_declaracion, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL9-A1', @com_a, '2026-07-03', '10:00:00', 1300.00,
    @esp, @hum_hum, @comp,
    '6666', 'Planta Sintética', @user_pla
);
INSERT INTO declaracion_planta_abastecimiento (
    folio_origen, usuario_id, fecha_ingreso_planta, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    nombre_planta, codigo_sernapesca,
    documento_tributario_fecha, documento_tributario_numero, documento_tributario_tipo,
    voucher_romana_numero, peso_romana_kg, fecha_pesaje
) VALUES (
    'DUMMY-JUL9-A1', @user_pla, '2026-07-09', '12:00:00', 1300.00,
    @esp, @hum_hum, @comp,
    'Planta Sintética', 'PLA-001',
    '2026-07-09', 'FACT-JUL9-A1', 'FACTURA',
    'VCH-JUL9-A1', 1300.00, '2026-07-09'
);

-- ---- A2 · rojo por variación (-12 %) y rojo por retención (8 días) ----------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL9-A2', @user_rec, '2026-07-04', '2026-07-04', '07:30:00',
    'Recolector Sintético A2', 'RPA-JUL9-A2', @caleta, @comuna, @esp, @ext_tipo,
    @comp, @hum_hum, 900.00, 900.00 * @f_hum, @f_hum,
    '78147430', 'Comercializador A', @com_a
);
INSERT INTO declaracion_comercializador (
    folio_origen, usuario_id, fecha_declaracion, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL9-A2', @com_a, '2026-07-05', '10:00:00', 900.00,
    @esp, @hum_hum, @comp,
    '6666', 'Planta Sintética', @user_pla
);
INSERT INTO declaracion_planta_abastecimiento (
    folio_origen, usuario_id, fecha_ingreso_planta, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    nombre_planta, codigo_sernapesca,
    documento_tributario_fecha, documento_tributario_numero, documento_tributario_tipo,
    voucher_romana_numero, peso_romana_kg, fecha_pesaje
) VALUES (
    'DUMMY-JUL9-A2', @user_pla, '2026-07-13', '12:00:00', 792.00,
    @esp, @hum_hum, @comp,
    'Planta Sintética', 'PLA-001',
    '2026-07-13', 'FACT-JUL9-A2', 'FACTURA',
    'VCH-JUL9-A2', 792.00, '2026-07-13'
);

-- ---- A3 · amarillo por variación (-7 %) y por retención (4 días) ------------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL9-A3', @user_rec, '2026-07-08', '2026-07-08', '09:00:00',
    'Recolector Sintético A3', 'RPA-JUL9-A3', @caleta, @comuna, @esp, @ext_tipo,
    @comp, @hum_hum, 1000.00, 1000.00 * @f_hum, @f_hum,
    '78147430', 'Comercializador A', @com_a
);
INSERT INTO declaracion_comercializador (
    folio_origen, usuario_id, fecha_declaracion, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL9-A3', @com_a, '2026-07-09', '10:00:00', 1000.00,
    @esp, @hum_hum, @comp,
    '6666', 'Planta Sintética', @user_pla
);
INSERT INTO declaracion_planta_abastecimiento (
    folio_origen, usuario_id, fecha_ingreso_planta, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    nombre_planta, codigo_sernapesca,
    documento_tributario_fecha, documento_tributario_numero, documento_tributario_tipo,
    voucher_romana_numero, peso_romana_kg, fecha_pesaje
) VALUES (
    'DUMMY-JUL9-A3', @user_pla, '2026-07-13', '12:00:00', 930.00,
    @esp, @hum_hum, @comp,
    'Planta Sintética', 'PLA-001',
    '2026-07-13', 'FACT-JUL9-A3', 'FACTURA',
    'VCH-JUL9-A3', 930.00, '2026-07-13'
);

-- ---- A4 · amarillo por métricas, escala a ROJO por la marca EN_VEDA ----------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL9-A4', @user_rec, '2026-07-14', '2026-07-14', '10:20:00',
    'Recolector Sintético A4', 'RPA-JUL9-A4', @caleta, @comuna, @esp, @ext_tipo,
    @comp, @hum_hum, 750.00, 750.00 * @f_hum, @f_hum,
    '78147430', 'Comercializador A', @com_a
);
SET @id_a4 = LAST_INSERT_ID();

INSERT INTO declaracion_comercializador (
    folio_origen, usuario_id, fecha_declaracion, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL9-A4', @com_a, '2026-07-15', '10:00:00', 750.00,
    @esp, @hum_hum, @comp,
    '6666', 'Planta Sintética', @user_pla
);
INSERT INTO declaracion_planta_abastecimiento (
    folio_origen, usuario_id, fecha_ingreso_planta, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    nombre_planta, codigo_sernapesca,
    documento_tributario_fecha, documento_tributario_numero, documento_tributario_tipo,
    voucher_romana_numero, peso_romana_kg, fecha_pesaje
) VALUES (
    'DUMMY-JUL9-A4', @user_pla, '2026-07-17', '12:00:00', 705.00,
    @esp, @hum_hum, @comp,
    'Planta Sintética', 'PLA-001',
    '2026-07-17', 'FACT-JUL9-A4', 'FACTURA',
    'VCH-JUL9-A4', 705.00, '2026-07-17'
);
INSERT INTO declaracion_marca (declaracion_tipo, declaracion_id, marca, detalle, resuelta, created_at)
VALUES ('RECOLECTOR', @id_a4, 'EN_VEDA',
        'Lote sintético: extracción declarada durante veda vigente. Agravante del Indicador 9.',
        FALSE, '2026-07-14 10:25:00');

-- ---- B1 ---------------------------------------------------------------------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL9-B1', @user_rec, '2026-07-06', '2026-07-06', '08:15:00',
    'Recolector Sintético B1', 'RPA-JUL9-B1', @caleta, @comuna, @esp, @ext_tipo,
    @comp, @hum_hum, 1100.00, 1100.00 * @f_hum, @f_hum,
    '78147430', 'Comercializador B', @com_b
);
INSERT INTO declaracion_comercializador (
    folio_origen, usuario_id, fecha_declaracion, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL9-B1', @com_b, '2026-07-07', '10:00:00', 1100.00,
    @esp, @hum_hum, @comp,
    '6666', 'Planta Sintética', @user_pla
);
INSERT INTO declaracion_planta_abastecimiento (
    folio_origen, usuario_id, fecha_ingreso_planta, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    nombre_planta, codigo_sernapesca,
    documento_tributario_fecha, documento_tributario_numero, documento_tributario_tipo,
    voucher_romana_numero, peso_romana_kg, fecha_pesaje
) VALUES (
    'DUMMY-JUL9-B1', @user_pla, '2026-07-10', '12:00:00', 1039.50,
    @esp, @hum_hum, @comp,
    'Planta Sintética', 'PLA-001',
    '2026-07-10', 'FACT-JUL9-B1', 'FACTURA',
    'VCH-JUL9-B1', 1039.50, '2026-07-10'
);

-- ---- B2 · verde por métricas, amarillo por la marca LED_EXCEDIDO ------------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL9-B2', @user_rec, '2026-07-11', '2026-07-11', '11:00:00',
    'Recolector Sintético B2', 'RPA-JUL9-B2', @caleta, @comuna, @esp, @ext_tipo,
    @comp, @hum_sse, 800.00, 800.00 * @f_sse, @f_sse,
    '78147430', 'Comercializador B', @com_b
);
SET @id_b2 = LAST_INSERT_ID();

INSERT INTO declaracion_comercializador (
    folio_origen, usuario_id, fecha_declaracion, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL9-B2', @com_b, '2026-07-12', '10:00:00', 800.00,
    @esp, @hum_sse, @comp,
    '6666', 'Planta Sintética', @user_pla
);
INSERT INTO declaracion_planta_abastecimiento (
    folio_origen, usuario_id, fecha_ingreso_planta, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    nombre_planta, codigo_sernapesca,
    documento_tributario_fecha, documento_tributario_numero, documento_tributario_tipo,
    voucher_romana_numero, peso_romana_kg, fecha_pesaje
) VALUES (
    'DUMMY-JUL9-B2', @user_pla, '2026-07-14', '12:00:00', 768.00,
    @esp, @hum_sse, @comp,
    'Planta Sintética', 'PLA-001',
    '2026-07-14', 'FACT-JUL9-B2', 'FACTURA',
    'VCH-JUL9-B2', 768.00, '2026-07-14'
);
INSERT INTO declaracion_marca (declaracion_tipo, declaracion_id, marca, detalle, resuelta, created_at)
VALUES ('RECOLECTOR', @id_b2, 'LED_EXCEDIDO',
        'Lote sintético: sobrepaso del límite diario. Agravante del Indicador 9.',
        FALSE, '2026-07-11 11:05:00');

-- ---- B3 · retención en húmedo (4 días -> AMARILLO) --------------------------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL9-B3', @user_rec, '2026-07-16', '2026-07-16', '09:30:00',
    'Recolector Sintético B3', 'RPA-JUL9-B3', @caleta, @comuna, @esp, @ext_tipo,
    @comp, @hum_hum, 950.00, 950.00 * @f_hum, @f_hum,
    '78147430', 'Comercializador B', @com_b
);
INSERT INTO declaracion_comercializador (
    folio_origen, usuario_id, fecha_declaracion, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL9-B3', @com_b, '2026-07-17', '10:00:00', 950.00,
    @esp, @hum_hum, @comp,
    '6666', 'Planta Sintética', @user_pla
);
INSERT INTO declaracion_planta_abastecimiento (
    folio_origen, usuario_id, fecha_ingreso_planta, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    nombre_planta, codigo_sernapesca,
    documento_tributario_fecha, documento_tributario_numero, documento_tributario_tipo,
    voucher_romana_numero, peso_romana_kg, fecha_pesaje
) VALUES (
    'DUMMY-JUL9-B3', @user_pla, '2026-07-21', '12:00:00', 931.00,
    @esp, @hum_hum, @comp,
    'Planta Sintética', 'PLA-001',
    '2026-07-21', 'FACT-JUL9-B3', 'FACTURA',
    'VCH-JUL9-B3', 931.00, '2026-07-21'
);

-- ---- B4 · seco con diez días en bodega: la retención no puntúa --------------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL9-B4', @user_rec, '2026-07-09', '2026-07-09', '08:50:00',
    'Recolector Sintético B4', 'RPA-JUL9-B4', @caleta, @comuna, @esp, @ext_tipo,
    @comp, @hum_sec, 700.00, 700.00 * @f_sec, @f_sec,
    '78147430', 'Comercializador B', @com_b
);
INSERT INTO declaracion_comercializador (
    folio_origen, usuario_id, fecha_declaracion, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL9-B4', @com_b, '2026-07-10', '10:00:00', 700.00,
    @esp, @hum_sec, @comp,
    '6666', 'Planta Sintética', @user_pla
);
INSERT INTO declaracion_planta_abastecimiento (
    folio_origen, usuario_id, fecha_ingreso_planta, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    nombre_planta, codigo_sernapesca,
    documento_tributario_fecha, documento_tributario_numero, documento_tributario_tipo,
    voucher_romana_numero, peso_romana_kg, fecha_pesaje
) VALUES (
    'DUMMY-JUL9-B4', @user_pla, '2026-07-20', '12:00:00', 686.00,
    @esp, @hum_sec, @comp,
    'Planta Sintética', 'PLA-001',
    '2026-07-20', 'FACT-JUL9-B4', 'FACTURA',
    'VCH-JUL9-B4', 686.00, '2026-07-20'
);

-- ---- C1 · verde por variación (-3 %) y verde por retención (1 día) ----------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL9-C1', @user_rec, '2026-07-20', '2026-07-20', '07:30:00',
    'Recolector Sintético C1', 'RPA-JUL9-C1', @caleta, @comuna, @esp, @ext_tipo,
    @comp, @hum_hum, 1200.00, 1200.00 * @f_hum, @f_hum,
    '78147430', 'Comercializador C', @com_c
);
INSERT INTO declaracion_comercializador (
    folio_origen, usuario_id, fecha_declaracion, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL9-C1', @com_c, '2026-07-21', '10:00:00', 1200.00,
    @esp, @hum_hum, @comp,
    '6666', 'Planta Sintética', @user_pla
);
INSERT INTO declaracion_planta_abastecimiento (
    folio_origen, usuario_id, fecha_ingreso_planta, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    nombre_planta, codigo_sernapesca,
    documento_tributario_fecha, documento_tributario_numero, documento_tributario_tipo,
    voucher_romana_numero, peso_romana_kg, fecha_pesaje
) VALUES (
    'DUMMY-JUL9-C1', @user_pla, '2026-07-22', '12:00:00', 1164.00,
    @esp, @hum_hum, @comp,
    'Planta Sintética', 'PLA-001',
    '2026-07-22', 'FACT-JUL9-C1', 'FACTURA',
    'VCH-JUL9-C1', 1164.00, '2026-07-22'
);

-- ---- C2 · verde por variación (-1 %) y verde por retención (1 día) ----------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL9-C2', @user_rec, '2026-07-24', '2026-07-24', '08:05:00',
    'Recolector Sintético C2', 'RPA-JUL9-C2', @caleta, @comuna, @esp, @ext_tipo,
    @comp, @hum_sse, 650.00, 650.00 * @f_sse, @f_sse,
    '78147430', 'Comercializador C', @com_c
);
INSERT INTO declaracion_comercializador (
    folio_origen, usuario_id, fecha_declaracion, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL9-C2', @com_c, '2026-07-25', '10:00:00', 650.00,
    @esp, @hum_sse, @comp,
    '6666', 'Planta Sintética', @user_pla
);
INSERT INTO declaracion_planta_abastecimiento (
    folio_origen, usuario_id, fecha_ingreso_planta, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    nombre_planta, codigo_sernapesca,
    documento_tributario_fecha, documento_tributario_numero, documento_tributario_tipo,
    voucher_romana_numero, peso_romana_kg, fecha_pesaje
) VALUES (
    'DUMMY-JUL9-C2', @user_pla, '2026-07-26', '12:00:00', 643.50,
    @esp, @hum_sse, @comp,
    'Planta Sintética', 'PLA-001',
    '2026-07-26', 'FACT-JUL9-C2', 'FACTURA',
    'VCH-JUL9-C2', 643.50, '2026-07-26'
);

-- ============================================================================
-- 3. VERIFICACIÓN
-- ============================================================================

-- 3.1 El perfilador debe estar encendido y con sus cortes cargados
SELECT clave, valor
  FROM configuracion_general
 WHERE clave IN ('riesgo_activo', 'riesgo_variacion_amarillo_pct', 'riesgo_variacion_rojo_pct',
                 'riesgo_dias_amarillo', 'riesgo_dias_rojo', 'riesgo_escala_humedo_sin_merma',
                 'riesgo_agravante_veda_niveles', 'riesgo_agravante_led_niveles',
                 'retencion_bodega_estados_sujetos', 'bio_perdida_activo')
 ORDER BY clave;

-- 3.2 Los diez lotes con sus dos variables y sus agravantes
SELECT
    r.folio_origen,
    c.usuario_id                                                  AS comercializador,
    h.nombre                                                      AS humedad,
    r.desembarque                                                 AS kg_origen,
    p.peso_romana_kg                                              AS kg_romana,
    ROUND((p.peso_romana_kg - r.desembarque) / r.desembarque * 100, 2) AS variacion_pct,
    DATEDIFF(p.fecha_ingreso_planta, c.fecha_declaracion)         AS dias_bodega,
    GROUP_CONCAT(m.marca)                                         AS agravantes
FROM declaracion_recolector r
JOIN declaracion_comercializador       c ON c.folio_origen = r.folio_origen
JOIN declaracion_planta_abastecimiento p ON p.folio_origen = r.folio_origen
LEFT JOIN humedad_estado   h ON h.id = r.humedad_estado_id
LEFT JOIN declaracion_marca m ON m.declaracion_id = r.id AND m.declaracion_tipo = 'RECOLECTOR'
WHERE r.folio_origen LIKE 'DUMMY-JUL9-%'
GROUP BY r.folio_origen, c.usuario_id, h.nombre, r.desembarque, p.peso_romana_kg,
         p.fecha_ingreso_planta, c.fecha_declaracion
ORDER BY r.folio_origen;

-- 3.3 Totales sintéticos agregados a julio
SELECT
    ROUND(SUM(desembarque), 2) AS desembarque_sintetico_kg,
    ROUND(SUM(captura), 2)     AS captura_sintetica_kg,
    COUNT(*)                   AS declaraciones
FROM declaracion_recolector
WHERE folio_origen LIKE 'DUMMY-JUL9-%';
