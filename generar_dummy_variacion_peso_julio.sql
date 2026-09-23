-- ============================================================================
-- SCRIPT: DATOS SINTÉTICOS PARA EL INDICADOR 6 — VARIACIÓN EN PESO
-- Período objetivo: JULIO DE 2026 (2026-07-01 a 2026-07-31)
--
-- Motivo: en julio el indicador muestra pesajes y conciliaciones documentales
-- sin alertas. Este script siembra catorce lotes que recorren
-- Origen → Comercializador → Planta con fechas dentro de julio, de modo que
-- las dos poblaciones del indicador queden diferenciadas y con casos fuera de umbral.
--
-- Distinto de `generar_dummy_cadena_custodia.sql`, que usa CURDATE() y por lo
-- tanto siembra en el mes en curso. Los dos pueden convivir: usan prefijos de
-- folio distintos (DUMMY-CADENA-% y DUMMY-JUL6-%).
--
-- REVERSIBLE: el bloque 0 borra todo lo que este script crea.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 0. LIMPIEZA — deja la base como estaba antes de correr este script
-- ----------------------------------------------------------------------------
DELETE FROM declaracion_marca
 WHERE declaracion_tipo = 'RECOLECTOR'
   AND declaracion_id IN (SELECT id FROM declaracion_recolector
                           WHERE folio_origen LIKE 'DUMMY-JUL6-%');

DELETE FROM declaracion_planta_abastecimiento WHERE folio_origen LIKE 'DUMMY-JUL6-%';
DELETE FROM declaracion_comercializador       WHERE folio_origen LIKE 'DUMMY-JUL6-%';
DELETE FROM declaracion_recolector            WHERE folio_origen LIKE 'DUMMY-JUL6-%';

-- ----------------------------------------------------------------------------
-- 1. RESOLUCIÓN DE MAESTROS DINÁMICOS
-- ----------------------------------------------------------------------------
SET @user_rec = (SELECT u.id FROM usuario u JOIN perfil p ON u.perfil_id = p.id WHERE p.nombre LIKE '%RECOLECTOR%' ORDER BY u.id LIMIT 1);
SET @user_com = (SELECT u.id FROM usuario u JOIN perfil p ON u.perfil_id = p.id WHERE p.nombre LIKE '%COMER%' ORDER BY u.id LIMIT 1);
SET @user_pla = (SELECT u.id FROM usuario u JOIN perfil p ON u.perfil_id = p.id WHERE p.nombre LIKE '%PLANTA%' ORDER BY u.id LIMIT 1);

-- Respaldos por si la BD no tuviese roles estándar
SET @user_rec = COALESCE(@user_rec, (SELECT id FROM usuario ORDER BY id LIMIT 1));
SET @user_com = COALESCE(@user_com, (SELECT id FROM usuario ORDER BY id LIMIT 1));
SET @user_pla = COALESCE(@user_pla, (SELECT id FROM usuario ORDER BY id LIMIT 1));

SET @esp = COALESCE((SELECT id FROM especie WHERE UPPER(nombre) LIKE '%PALO%' LIMIT 1), 1);

-- Estados de humedad: excluir 'SEMI' explícitamente para evitar ambigüedades
SET @hum_hum = COALESCE((SELECT id FROM humedad_estado WHERE UPPER(nombre) NOT LIKE '%SEMI%' AND UPPER(nombre) NOT LIKE '%SEC%' LIMIT 1), 1);
SET @hum_sse = COALESCE((SELECT id FROM humedad_estado WHERE UPPER(nombre) LIKE '%SEMI%' AND UPPER(nombre) LIKE '%SEC%' LIMIT 1), 3);
SET @hum_sec = COALESCE((SELECT id FROM humedad_estado WHERE UPPER(nombre) NOT LIKE '%SEMI%' AND UPPER(nombre) LIKE '%SEC%' LIMIT 1), 4);

SET @caleta   = COALESCE((SELECT id FROM caleta ORDER BY id LIMIT 1), 1);
SET @comuna   = COALESCE((SELECT id FROM comuna ORDER BY id LIMIT 1), 30);
SET @ext_tipo = COALESCE((SELECT id FROM extraccion_tipo ORDER BY id LIMIT 1), 1);
SET @comp     = COALESCE((SELECT id FROM composicion ORDER BY id LIMIT 1), 1);

-- Factores oficiales de Sernapesca (13-09-2026) para Huiro Palo
SET @f_hum = 1.1300;
SET @f_sse = 2.7000;
SET @f_sec = 3.5800;

-- ============================================================================
-- 2. POBLACIÓN A — PESAJE FÍSICO EN ROMANA (vinculante)
-- Ocho lotes con peso_romana_kg y voucher. Cuatro quedan fuera del umbral
-- de ±5 %, de modo que el contador de alertas deja de ser cero.
--
--  #   Humedad     Origen   Romana    Δ%     Días bodega   Lectura esperada
--  R1  Húmedo      1.200    1.128    -6,0        1         Merma normal de escurrido
--  R2  Húmedo        950      893    -6,0        2         Merma normal de escurrido
--  R3  Húmedo      1.500    1.500     0,0        5         CRÍTICA: húmedo sin merma
--  R4  Húmedo        800      816    +2,0        4         CRÍTICA: húmedo gana peso
--  R5  Seco          600      594    -1,0       12         Neutra: seco estabilizado
--  R6  Seco          700      637    -9,0        2         ATENCIÓN: merma seca anómala
--  R7  Húmedo      1.100      979   -11,0        9         Variación severa + retención
--  R8  Semi Seco     900      873    -3,0        3         Dentro de umbral
--
--  Promedio simple de las ocho variaciones: -4,25 %
--  Fuera de umbral (|Δ| > 5 %): R1, R2, R6, R7  → 4 alertas
-- ============================================================================

-- ---- R1 ---------------------------------------------------------------------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL6-R1', @user_rec, '2026-07-03', '2026-07-03', '08:10:00',
    'Recolector Sintético R1', 'RPA-JUL6-R1', @caleta, @comuna, @esp, @ext_tipo,
    @comp, @hum_hum, 1200.00, 1200.00 * @f_hum, @f_hum,
    '78147430', 'Comercializador Sintético', @user_com
);
INSERT INTO declaracion_comercializador (
    folio_origen, usuario_id, fecha_declaracion, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL6-R1', @user_com, '2026-07-04', '10:00:00', 1200.00,
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
    'DUMMY-JUL6-R1', @user_pla, '2026-07-05', '12:00:00', 1128.00,
    @esp, @hum_hum, @comp,
    'Planta Sintética', 'PLA-001',
    '2026-07-05', 'FACT-JUL6-R1', 'FACTURA',
    'VCH-JUL6-R1', 1128.00, '2026-07-05'
);

-- ---- R2 ---------------------------------------------------------------------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL6-R2', @user_rec, '2026-07-06', '2026-07-06', '07:45:00',
    'Recolector Sintético R2', 'RPA-JUL6-R2', @caleta, @comuna, @esp, @ext_tipo,
    @comp, @hum_hum, 950.00, 950.00 * @f_hum, @f_hum,
    '78147430', 'Comercializador Sintético', @user_com
);
INSERT INTO declaracion_comercializador (
    folio_origen, usuario_id, fecha_declaracion, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL6-R2', @user_com, '2026-07-07', '10:00:00', 950.00,
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
    'DUMMY-JUL6-R2', @user_pla, '2026-07-09', '12:00:00', 893.00,
    @esp, @hum_hum, @comp,
    'Planta Sintética', 'PLA-001',
    '2026-07-09', 'FACT-JUL6-R2', 'FACTURA',
    'VCH-JUL6-R2', 893.00, '2026-07-09'
);

-- ---- R3 · húmedo cinco días sin perder un gramo: inconsistencia crítica ------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL6-R3', @user_rec, '2026-07-08', '2026-07-08', '09:20:00',
    'Recolector Sintético R3', 'RPA-JUL6-R3', @caleta, @comuna, @esp, @ext_tipo,
    @comp, @hum_hum, 1500.00, 1500.00 * @f_hum, @f_hum,
    '78147430', 'Comercializador Sintético', @user_com
);
INSERT INTO declaracion_comercializador (
    folio_origen, usuario_id, fecha_declaracion, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL6-R3', @user_com, '2026-07-09', '10:00:00', 1500.00,
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
    'DUMMY-JUL6-R3', @user_pla, '2026-07-14', '12:00:00', 1500.00,
    @esp, @hum_hum, @comp,
    'Planta Sintética', 'PLA-001',
    '2026-07-14', 'FACT-JUL6-R3', 'FACTURA',
    'VCH-JUL6-R3', 1500.00, '2026-07-14'
);

-- ---- R4 · húmedo que llega pesando más: presunción de blanqueo --------------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL6-R4', @user_rec, '2026-07-11', '2026-07-11', '10:05:00',
    'Recolector Sintético R4', 'RPA-JUL6-R4', @caleta, @comuna, @esp, @ext_tipo,
    @comp, @hum_hum, 800.00, 800.00 * @f_hum, @f_hum,
    '78147430', 'Comercializador Sintético', @user_com
);
INSERT INTO declaracion_comercializador (
    folio_origen, usuario_id, fecha_declaracion, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL6-R4', @user_com, '2026-07-12', '10:00:00', 800.00,
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
    'DUMMY-JUL6-R4', @user_pla, '2026-07-16', '12:00:00', 816.00,
    @esp, @hum_hum, @comp,
    'Planta Sintética', 'PLA-001',
    '2026-07-16', 'FACT-JUL6-R4', 'FACTURA',
    'VCH-JUL6-R4', 816.00, '2026-07-16'
);

-- ---- R5 · seco doce días en bodega: sin sospecha biológica ------------------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL6-R5', @user_rec, '2026-07-05', '2026-07-05', '08:30:00',
    'Recolector Sintético R5', 'RPA-JUL6-R5', @caleta, @comuna, @esp, @ext_tipo,
    @comp, @hum_sec, 600.00, 600.00 * @f_sec, @f_sec,
    '78147430', 'Comercializador Sintético', @user_com
);
INSERT INTO declaracion_comercializador (
    folio_origen, usuario_id, fecha_declaracion, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL6-R5', @user_com, '2026-07-06', '10:00:00', 600.00,
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
    'DUMMY-JUL6-R5', @user_pla, '2026-07-18', '12:00:00', 594.00,
    @esp, @hum_sec, @comp,
    'Planta Sintética', 'PLA-001',
    '2026-07-18', 'FACT-JUL6-R5', 'FACTURA',
    'VCH-JUL6-R5', 594.00, '2026-07-18'
);

-- ---- R6 · seco con 9 % de merma: el alga seca no tiene agua que perder -----
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL6-R6', @user_rec, '2026-07-15', '2026-07-15', '11:15:00',
    'Recolector Sintético R6', 'RPA-JUL6-R6', @caleta, @comuna, @esp, @ext_tipo,
    @comp, @hum_sec, 700.00, 700.00 * @f_sec, @f_sec,
    '78147430', 'Comercializador Sintético', @user_com
);
INSERT INTO declaracion_comercializador (
    folio_origen, usuario_id, fecha_declaracion, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL6-R6', @user_com, '2026-07-16', '10:00:00', 700.00,
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
    'DUMMY-JUL6-R6', @user_pla, '2026-07-18', '12:00:00', 637.00,
    @esp, @hum_sec, @comp,
    'Planta Sintética', 'PLA-001',
    '2026-07-18', 'FACT-JUL6-R6', 'FACTURA',
    'VCH-JUL6-R6', 637.00, '2026-07-18'
);

-- ---- R7 · variación severa y nueve días de retención ------------------------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL6-R7', @user_rec, '2026-07-10', '2026-07-10', '07:55:00',
    'Recolector Sintético R7', 'RPA-JUL6-R7', @caleta, @comuna, @esp, @ext_tipo,
    @comp, @hum_hum, 1100.00, 1100.00 * @f_hum, @f_hum,
    '78147430', 'Comercializador Sintético', @user_com
);
INSERT INTO declaracion_comercializador (
    folio_origen, usuario_id, fecha_declaracion, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL6-R7', @user_com, '2026-07-11', '10:00:00', 1100.00,
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
    'DUMMY-JUL6-R7', @user_pla, '2026-07-20', '12:00:00', 979.00,
    @esp, @hum_hum, @comp,
    'Planta Sintética', 'PLA-001',
    '2026-07-20', 'FACT-JUL6-R7', 'FACTURA',
    'VCH-JUL6-R7', 979.00, '2026-07-20'
);

-- ---- R8 · semiseco dentro de umbral -----------------------------------------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL6-R8', @user_rec, '2026-07-17', '2026-07-17', '09:40:00',
    'Recolector Sintético R8', 'RPA-JUL6-R8', @caleta, @comuna, @esp, @ext_tipo,
    @comp, @hum_sse, 900.00, 900.00 * @f_sse, @f_sse,
    '78147430', 'Comercializador Sintético', @user_com
);
INSERT INTO declaracion_comercializador (
    folio_origen, usuario_id, fecha_declaracion, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL6-R8', @user_com, '2026-07-18', '10:00:00', 900.00,
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
    'DUMMY-JUL6-R8', @user_pla, '2026-07-21', '12:00:00', 873.00,
    @esp, @hum_sse, @comp,
    'Planta Sintética', 'PLA-001',
    '2026-07-21', 'FACT-JUL6-R8', 'FACTURA',
    'VCH-JUL6-R8', 873.00, '2026-07-21'
);

-- ============================================================================
-- 3. POBLACIÓN B — CONCILIACIÓN DOCUMENTAL (sin romana)
-- Seis lotes sin voucher ni peso de romana. Las cifras se transcriben de la
-- guía, así que la variación es nula o mínima: promedio -0,33 %, sin alertas.
-- ============================================================================

-- ---- D1 · guía transcrita, variación nula -----------------------------------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL6-D1', @user_rec, '2026-07-02', '2026-07-02', '08:00:00',
    'Recolector Sintético D1', 'RPA-JUL6-D1', @caleta, @comuna, @esp, @ext_tipo,
    @comp, @hum_hum, 1000.00, 1000.00 * @f_hum, @f_hum,
    '78147430', 'Comercializador Sintético', @user_com
);
INSERT INTO declaracion_comercializador (
    folio_origen, usuario_id, fecha_declaracion, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL6-D1', @user_com, '2026-07-03', '10:00:00', 1000.00,
    @esp, @hum_hum, @comp,
    '6666', 'Planta Sintética', @user_pla
);
INSERT INTO declaracion_planta_abastecimiento (
    folio_origen, usuario_id, fecha_ingreso_planta, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    nombre_planta, codigo_sernapesca,
    documento_tributario_fecha, documento_tributario_numero, documento_tributario_tipo
) VALUES (
    'DUMMY-JUL6-D1', @user_pla, '2026-07-04', '12:00:00', 1000.00,
    @esp, @hum_hum, @comp,
    'Planta Sintética', 'PLA-001',
    '2026-07-04', 'FACT-JUL6-D1', 'FACTURA'
);

-- ---- D2 ---------------------------------------------------------------------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL6-D2', @user_rec, '2026-07-07', '2026-07-07', '08:20:00',
    'Recolector Sintético D2', 'RPA-JUL6-D2', @caleta, @comuna, @esp, @ext_tipo,
    @comp, @hum_sse, 850.00, 850.00 * @f_sse, @f_sse,
    '78147430', 'Comercializador Sintético', @user_com
);
INSERT INTO declaracion_comercializador (
    folio_origen, usuario_id, fecha_declaracion, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL6-D2', @user_com, '2026-07-08', '10:00:00', 850.00,
    @esp, @hum_sse, @comp,
    '6666', 'Planta Sintética', @user_pla
);
INSERT INTO declaracion_planta_abastecimiento (
    folio_origen, usuario_id, fecha_ingreso_planta, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    nombre_planta, codigo_sernapesca,
    documento_tributario_fecha, documento_tributario_numero, documento_tributario_tipo
) VALUES (
    'DUMMY-JUL6-D2', @user_pla, '2026-07-09', '12:00:00', 850.00,
    @esp, @hum_sse, @comp,
    'Planta Sintética', 'PLA-001',
    '2026-07-09', 'FACT-JUL6-D2', 'FACTURA'
);

-- ---- D3 ---------------------------------------------------------------------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL6-D3', @user_rec, '2026-07-12', '2026-07-12', '09:00:00',
    'Recolector Sintético D3', 'RPA-JUL6-D3', @caleta, @comuna, @esp, @ext_tipo,
    @comp, @hum_sec, 1400.00, 1400.00 * @f_sec, @f_sec,
    '78147430', 'Comercializador Sintético', @user_com
);
INSERT INTO declaracion_comercializador (
    folio_origen, usuario_id, fecha_declaracion, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL6-D3', @user_com, '2026-07-13', '10:00:00', 1400.00,
    @esp, @hum_sec, @comp,
    '6666', 'Planta Sintética', @user_pla
);
INSERT INTO declaracion_planta_abastecimiento (
    folio_origen, usuario_id, fecha_ingreso_planta, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    nombre_planta, codigo_sernapesca,
    documento_tributario_fecha, documento_tributario_numero, documento_tributario_tipo
) VALUES (
    'DUMMY-JUL6-D3', @user_pla, '2026-07-14', '12:00:00', 1386.00,
    @esp, @hum_sec, @comp,
    'Planta Sintética', 'PLA-001',
    '2026-07-14', 'FACT-JUL6-D3', 'FACTURA'
);

-- ---- D4 ---------------------------------------------------------------------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL6-D4', @user_rec, '2026-07-19', '2026-07-19', '07:50:00',
    'Recolector Sintético D4', 'RPA-JUL6-D4', @caleta, @comuna, @esp, @ext_tipo,
    @comp, @hum_hum, 620.00, 620.00 * @f_hum, @f_hum,
    '78147430', 'Comercializador Sintético', @user_com
);
INSERT INTO declaracion_comercializador (
    folio_origen, usuario_id, fecha_declaracion, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL6-D4', @user_com, '2026-07-20', '10:00:00', 620.00,
    @esp, @hum_hum, @comp,
    '6666', 'Planta Sintética', @user_pla
);
INSERT INTO declaracion_planta_abastecimiento (
    folio_origen, usuario_id, fecha_ingreso_planta, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    nombre_planta, codigo_sernapesca,
    documento_tributario_fecha, documento_tributario_numero, documento_tributario_tipo
) VALUES (
    'DUMMY-JUL6-D4', @user_pla, '2026-07-21', '12:00:00', 620.00,
    @esp, @hum_hum, @comp,
    'Planta Sintética', 'PLA-001',
    '2026-07-21', 'FACT-JUL6-D4', 'FACTURA'
);

-- ---- D5 ---------------------------------------------------------------------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL6-D5', @user_rec, '2026-07-22', '2026-07-22', '10:30:00',
    'Recolector Sintético D5', 'RPA-JUL6-D5', @caleta, @comuna, @esp, @ext_tipo,
    @comp, @hum_sse, 1750.00, 1750.00 * @f_sse, @f_sse,
    '78147430', 'Comercializador Sintético', @user_com
);
INSERT INTO declaracion_comercializador (
    folio_origen, usuario_id, fecha_declaracion, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL6-D5', @user_com, '2026-07-23', '10:00:00', 1750.00,
    @esp, @hum_sse, @comp,
    '6666', 'Planta Sintética', @user_pla
);
INSERT INTO declaracion_planta_abastecimiento (
    folio_origen, usuario_id, fecha_ingreso_planta, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    nombre_planta, codigo_sernapesca,
    documento_tributario_fecha, documento_tributario_numero, documento_tributario_tipo
) VALUES (
    'DUMMY-JUL6-D5', @user_pla, '2026-07-24', '12:00:00', 1732.50,
    @esp, @hum_sse, @comp,
    'Planta Sintética', 'PLA-001',
    '2026-07-24', 'FACT-JUL6-D5', 'FACTURA'
);

-- ---- D6 ---------------------------------------------------------------------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL6-D6', @user_rec, '2026-07-25', '2026-07-25', '08:45:00',
    'Recolector Sintético D6', 'RPA-JUL6-D6', @caleta, @comuna, @esp, @ext_tipo,
    @comp, @hum_sec, 980.00, 980.00 * @f_sec, @f_sec,
    '78147430', 'Comercializador Sintético', @user_com
);
INSERT INTO declaracion_comercializador (
    folio_origen, usuario_id, fecha_declaracion, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-JUL6-D6', @user_com, '2026-07-26', '10:00:00', 980.00,
    @esp, @hum_sec, @comp,
    '6666', 'Planta Sintética', @user_pla
);
INSERT INTO declaracion_planta_abastecimiento (
    folio_origen, usuario_id, fecha_ingreso_planta, hora, cantidad,
    especie_id, humedad_estado_id, composicion_id,
    nombre_planta, codigo_sernapesca,
    documento_tributario_fecha, documento_tributario_numero, documento_tributario_tipo
) VALUES (
    'DUMMY-JUL6-D6', @user_pla, '2026-07-27', '12:00:00', 980.00,
    @esp, @hum_sec, @comp,
    'Planta Sintética', 'PLA-001',
    '2026-07-27', 'FACT-JUL6-D6', 'FACTURA'
);

-- ============================================================================
-- 4. VERIFICACIÓN — qué quedó sembrado y qué debería mostrar el indicador
-- ============================================================================

-- 4.1 Resumen por población, tal como lo calcula el reporte
SELECT
    CASE WHEN p.peso_romana_kg IS NOT NULL AND p.voucher_romana_numero IS NOT NULL
         THEN 'PESAJE' ELSE 'DOCUMENTO' END                                AS poblacion,
    COUNT(*)                                                               AS conciliaciones,
    ROUND(SUM(r.desembarque), 2)                                           AS kg_origen,
    ROUND(SUM(COALESCE(p.peso_romana_kg, p.cantidad)), 2)                  AS kg_destino,
    ROUND(AVG((COALESCE(p.peso_romana_kg, p.cantidad) - r.desembarque)
              / r.desembarque * 100), 2)                                   AS promedio_variacion_pct,
    SUM(CASE WHEN ABS((COALESCE(p.peso_romana_kg, p.cantidad) - r.desembarque)
                      / r.desembarque * 100) > 5.0 THEN 1 ELSE 0 END)      AS fuera_de_umbral
FROM declaracion_recolector r
JOIN declaracion_planta_abastecimiento p ON p.folio_origen = r.folio_origen
WHERE r.folio_origen LIKE 'DUMMY-JUL6-%'
GROUP BY poblacion;

-- 4.2 Detalle lote a lote, con días de tránsito y de bodega
SELECT
    r.folio_origen,
    h.nombre                                                      AS humedad,
    r.desembarque                                                 AS kg_origen,
    COALESCE(p.peso_romana_kg, p.cantidad)                        AS kg_destino,
    ROUND((COALESCE(p.peso_romana_kg, p.cantidad) - r.desembarque)
          / r.desembarque * 100, 2)                               AS variacion_pct,
    p.voucher_romana_numero,
    DATEDIFF(p.fecha_ingreso_planta, r.fecha_declaracion)         AS dias_transito,
    DATEDIFF(p.fecha_ingreso_planta, c.fecha_declaracion)         AS dias_bodega
FROM declaracion_recolector r
JOIN declaracion_planta_abastecimiento p ON p.folio_origen = r.folio_origen
LEFT JOIN declaracion_comercializador   c ON c.folio_origen = r.folio_origen
LEFT JOIN humedad_estado                h ON h.id = r.humedad_estado_id
WHERE r.folio_origen LIKE 'DUMMY-JUL6-%'
ORDER BY r.folio_origen;
