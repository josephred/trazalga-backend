-- ============================================================================
-- SCRIPT: GENERAR DUMMY DATA DE CADENA DE CUSTODIA (R0.3 / T2.5)
-- Crea lotes trazados a través de los tres eslabones de custodia:
-- (1) Extracción (Recolector/Armador/Área)
-- (2) Comercializador intermediario (Bodega virtual)
-- (3) Planta de abastecimiento / proceso (Voucher romana y recepción física)
--
-- Casos normativos cubiertos según PLAN_ANTIGRAVITY_refinamiento_11sep.md:
-- 1. Húmedo, 1 día de tránsito, merma 6% -> Verde (rotación normal)
-- 2. Húmedo, 5 días en bodega, merma 0% -> ROJO (inconsistencia biológica CRÍTICA)
-- 3. Húmedo, 9 días en bodega, merma 7% -> ROJO (retención en bodega > 7 días)
-- 4. Seco, 12 días en bodega, merma 1% -> Verde/Neutro (recurso estabilizado)
-- 5. Seco, 2 días, merma 9% -> ROJO/ATENCIÓN (merma seca anómala)
-- 6. Lote con marca EN_VEDA activa -> ROJO por agravante
-- 7. Lote con pesaje de romana y voucher -> Variación física vinculante
-- 8. Lote sólo con conciliación documental -> No contamina promedio de romana
--
-- T2.5: Enlace por tokens y punteros reales (declaracion_destinatario_id,
-- consumida_por_tipo, declaraciones_seleccionadas) con folios distintos por eslabón.
-- ============================================================================

-- 0. Limpieza previa de datos sintéticos de cadena de custodia
DELETE FROM declaracion_marca WHERE declaracion_id IN (
    SELECT id FROM declaracion_recolector WHERE folio_origen LIKE 'DUMMY-CADENA-%'
) AND declaracion_tipo = 'RECOLECTOR';

DELETE FROM declaracion_planta_abastecimiento WHERE folio_origen LIKE 'DUMMY-CADENA-%';
DELETE FROM declaracion_comercializador WHERE folio_origen LIKE 'DUMMY-CADENA-%';
DELETE FROM declaracion_recolector WHERE folio_origen LIKE 'DUMMY-CADENA-%';

-- Obtener IDs maestros de referencia
SET @user_rec = (SELECT id FROM usuario WHERE perfil = 'RECOLECTOR' LIMIT 1);
SET @user_com = (SELECT id FROM usuario WHERE perfil = 'COMERCIALIZADOR' LIMIT 1);
SET @user_pla = (SELECT id FROM usuario WHERE perfil = 'PLANTA' LIMIT 1);
SET @esp_huiro = (SELECT id FROM especie LIMIT 1);
SET @hum_humedo = (SELECT id FROM humedad_estado WHERE UPPER(nombre) LIKE '%HUMED%' LIMIT 1);
SET @hum_seco = (SELECT id FROM humedad_estado WHERE UPPER(nombre) LIKE '%SEC%' LIMIT 1);
SET @caleta = (SELECT id FROM caleta LIMIT 1);
SET @comuna = (SELECT id FROM comuna LIMIT 1);
SET @ext_tipo = (SELECT id FROM extraccion_tipo LIMIT 1);
SET @comp = (SELECT id FROM composicion LIMIT 1);

-- Valores fallback si la base está vacía de maestros
SET @user_rec = COALESCE(@user_rec, 1);
SET @user_com = COALESCE(@user_com, 1);
SET @user_pla = COALESCE(@user_pla, 1);
SET @esp_huiro = COALESCE(@esp_huiro, 1);
SET @hum_humedo = COALESCE(@hum_humedo, 1);
SET @hum_seco = COALESCE(@hum_seco, 2);
SET @caleta = COALESCE(@caleta, 1);
SET @comuna = COALESCE(@comuna, 1);
SET @ext_tipo = COALESCE(@ext_tipo, 1);
SET @comp = COALESCE(@comp, 1);

-- ----------------------------------------------------------------------------
-- CASO 1: Húmedo, 1 día de tránsito, merma 6% (Verde: rotación normal)
-- Directo a planta
-- ----------------------------------------------------------------------------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    consumida_por_tipo
) VALUES (
    'DUMMY-CADENA-REC-CASO1', @user_rec, CURDATE() - INTERVAL 1 DAY, CURDATE() - INTERVAL 1 DAY, '08:00:00',
    'Recolector Sintético 1', 'RPA-DUMMY-1', @caleta, @comuna, @esp_huiro, @ext_tipo,
    @comp, @hum_humedo, 1000.00, 1000.00, 1.0000,
    'PLANTA_ABASTECIMIENTO'
);
SET @rec1 = LAST_INSERT_ID();

INSERT INTO declaracion_planta_abastecimiento (
    folio_origen, usuario_id, fecha_ingreso_planta, cantidad, voucher_romana_numero,
    peso_romana_kg, fecha_pesaje, declaraciones_seleccionadas
) VALUES (
    'DUMMY-CADENA-PLA-CASO1', @user_pla, CURDATE(), 940.00, 'VCH-CASO1-001', 940.00, CURDATE(),
    CONCAT('RECOLECTOR:', @rec1)
);
SET @pla1 = LAST_INSERT_ID();
UPDATE declaracion_recolector SET declaracion_destinatario_id = @pla1 WHERE id = @rec1;

-- ----------------------------------------------------------------------------
-- CASO 2: Húmedo, 5 días en bodega, merma 0% (ROJO: inconsistencia biológica CRÍTICA)
-- Recolector -> Comercializador -> Planta
-- ----------------------------------------------------------------------------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    consumida_por_tipo
) VALUES (
    'DUMMY-CADENA-REC-CASO2', @user_rec, CURDATE() - INTERVAL 5 DAY, CURDATE() - INTERVAL 5 DAY, '09:00:00',
    'Recolector Sintético 2', 'RPA-DUMMY-2', @caleta, @comuna, @esp_huiro, @ext_tipo,
    @comp, @hum_humedo, 1000.00, 1000.00, 1.0000,
    'COMERCIALIZADOR'
);
SET @rec2 = LAST_INSERT_ID();

INSERT INTO declaracion_comercializador (
    folio_origen, usuario_id, fecha_declaracion, hora, cantidad,
    especie_id, humedad_estado_id, declaraciones_seleccionadas, consumida_por_tipo
) VALUES (
    'DUMMY-CADENA-COM-CASO2', @user_com, CURDATE() - INTERVAL 5 DAY, '14:00:00', 1000.00,
    @esp_huiro, @hum_humedo, CONCAT('RECOLECTOR:', @rec2), 'PLANTA_ABASTECIMIENTO'
);
SET @com2 = LAST_INSERT_ID();
UPDATE declaracion_recolector SET declaracion_destinatario_id = @com2 WHERE id = @rec2;

INSERT INTO declaracion_planta_abastecimiento (
    folio_origen, usuario_id, fecha_ingreso_planta, hora, cantidad, voucher_romana_numero,
    peso_romana_kg, fecha_pesaje, declaraciones_seleccionadas
) VALUES (
    'DUMMY-CADENA-PLA-CASO2', @user_pla, CURDATE(), '10:00:00', 1000.00, 'VCH-CASO2-001', 1000.00, CURDATE(),
    CONCAT('COMERCIALIZADOR:', @com2)
);
SET @pla2 = LAST_INSERT_ID();
UPDATE declaracion_comercializador SET declaracion_destinatario_id = @pla2 WHERE id = @com2;

-- ----------------------------------------------------------------------------
-- CASO 3: Húmedo, 9 días en bodega, merma 7% (ROJO: retención > 7 días)
-- Recolector -> Comercializador -> Planta
-- ----------------------------------------------------------------------------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    consumida_por_tipo
) VALUES (
    'DUMMY-CADENA-REC-CASO3', @user_rec, CURDATE() - INTERVAL 9 DAY, CURDATE() - INTERVAL 9 DAY, '10:00:00',
    'Recolector Sintético 3', 'RPA-DUMMY-3', @caleta, @comuna, @esp_huiro, @ext_tipo,
    @comp, @hum_humedo, 1000.00, 1000.00, 1.0000,
    'COMERCIALIZADOR'
);
SET @rec3 = LAST_INSERT_ID();

INSERT INTO declaracion_comercializador (
    folio_origen, usuario_id, fecha_declaracion, hora, cantidad,
    especie_id, humedad_estado_id, declaraciones_seleccionadas, consumida_por_tipo
) VALUES (
    'DUMMY-CADENA-COM-CASO3', @user_com, CURDATE() - INTERVAL 9 DAY, '15:00:00', 1000.00,
    @esp_huiro, @hum_humedo, CONCAT('RECOLECTOR:', @rec3), 'PLANTA_ABASTECIMIENTO'
);
SET @com3 = LAST_INSERT_ID();
UPDATE declaracion_recolector SET declaracion_destinatario_id = @com3 WHERE id = @rec3;

INSERT INTO declaracion_planta_abastecimiento (
    folio_origen, usuario_id, fecha_ingreso_planta, hora, cantidad, voucher_romana_numero,
    peso_romana_kg, fecha_pesaje, declaraciones_seleccionadas
) VALUES (
    'DUMMY-CADENA-PLA-CASO3', @user_pla, CURDATE(), '11:00:00', 930.00, 'VCH-CASO3-001', 930.00, CURDATE(),
    CONCAT('COMERCIALIZADOR:', @com3)
);
SET @pla3 = LAST_INSERT_ID();
UPDATE declaracion_comercializador SET declaracion_destinatario_id = @pla3 WHERE id = @com3;

-- ----------------------------------------------------------------------------
-- CASO 4: Seco, 12 días en bodega, merma 1% (Verde/Neutro: recurso estabilizado)
-- Recolector -> Comercializador -> Planta
-- ----------------------------------------------------------------------------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    consumida_por_tipo
) VALUES (
    'DUMMY-CADENA-REC-CASO4', @user_rec, CURDATE() - INTERVAL 12 DAY, CURDATE() - INTERVAL 12 DAY, '11:00:00',
    'Recolector Sintético 4', 'RPA-DUMMY-4', @caleta, @comuna, @esp_huiro, @ext_tipo,
    @comp, @hum_seco, 1000.00, 3580.00, 3.5800,
    'COMERCIALIZADOR'
);
SET @rec4 = LAST_INSERT_ID();

INSERT INTO declaracion_comercializador (
    folio_origen, usuario_id, fecha_declaracion, hora, cantidad,
    especie_id, humedad_estado_id, declaraciones_seleccionadas, consumida_por_tipo
) VALUES (
    'DUMMY-CADENA-COM-CASO4', @user_com, CURDATE() - INTERVAL 12 DAY, '16:00:00', 1000.00,
    @esp_huiro, @hum_seco, CONCAT('RECOLECTOR:', @rec4), 'PLANTA_ABASTECIMIENTO'
);
SET @com4 = LAST_INSERT_ID();
UPDATE declaracion_recolector SET declaracion_destinatario_id = @com4 WHERE id = @rec4;

INSERT INTO declaracion_planta_abastecimiento (
    folio_origen, usuario_id, fecha_ingreso_planta, hora, cantidad, voucher_romana_numero,
    peso_romana_kg, fecha_pesaje, declaraciones_seleccionadas
) VALUES (
    'DUMMY-CADENA-PLA-CASO4', @user_pla, CURDATE(), '12:00:00', 990.00, 'VCH-CASO4-001', 990.00, CURDATE(),
    CONCAT('COMERCIALIZADOR:', @com4)
);
SET @pla4 = LAST_INSERT_ID();
UPDATE declaracion_comercializador SET declaracion_destinatario_id = @pla4 WHERE id = @com4;

-- ----------------------------------------------------------------------------
-- CASO 5: Seco, 2 días, merma 9% (ROJO/ATENCIÓN: merma seca anómala > 3%)
-- Directo a planta
-- ----------------------------------------------------------------------------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    consumida_por_tipo
) VALUES (
    'DUMMY-CADENA-REC-CASO5', @user_rec, CURDATE() - INTERVAL 2 DAY, CURDATE() - INTERVAL 2 DAY, '12:00:00',
    'Recolector Sintético 5', 'RPA-DUMMY-5', @caleta, @comuna, @esp_huiro, @ext_tipo,
    @comp, @hum_seco, 1000.00, 3580.00, 3.5800,
    'PLANTA_ABASTECIMIENTO'
);
SET @rec5 = LAST_INSERT_ID();

INSERT INTO declaracion_planta_abastecimiento (
    folio_origen, usuario_id, fecha_ingreso_planta, hora, cantidad, voucher_romana_numero,
    peso_romana_kg, fecha_pesaje, declaraciones_seleccionadas
) VALUES (
    'DUMMY-CADENA-PLA-CASO5', @user_pla, CURDATE(), '13:00:00', 910.00, 'VCH-CASO5-001', 910.00, CURDATE(),
    CONCAT('RECOLECTOR:', @rec5)
);
SET @pla5 = LAST_INSERT_ID();
UPDATE declaracion_recolector SET declaracion_destinatario_id = @pla5 WHERE id = @rec5;

-- ----------------------------------------------------------------------------
-- CASO 6: Lote con marca EN_VEDA activa (ROJO por agravante de veda)
-- Directo a planta
-- ----------------------------------------------------------------------------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    consumida_por_tipo
) VALUES (
    'DUMMY-CADENA-REC-CASO6', @user_rec, CURDATE() - INTERVAL 3 DAY, CURDATE() - INTERVAL 3 DAY, '13:00:00',
    'Recolector Sintético 6', 'RPA-DUMMY-6', @caleta, @comuna, @esp_huiro, @ext_tipo,
    @comp, @hum_humedo, 1000.00, 1000.00, 1.0000,
    'PLANTA_ABASTECIMIENTO'
);
SET @last_rec_id = LAST_INSERT_ID();

INSERT INTO declaracion_marca (
    declaracion_id, declaracion_tipo, marca, detalle, resuelta, created_at
) VALUES (
    @last_rec_id, 'RECOLECTOR', 'EN_VEDA', 'Extracción realizada durante período de veda biológica activa', false, NOW()
);

INSERT INTO declaracion_planta_abastecimiento (
    folio_origen, usuario_id, fecha_ingreso_planta, hora, cantidad, voucher_romana_numero,
    peso_romana_kg, fecha_pesaje, declaraciones_seleccionadas
) VALUES (
    'DUMMY-CADENA-PLA-CASO6', @user_pla, CURDATE(), '14:00:00', 950.00, 'VCH-CASO6-001', 950.00, CURDATE(),
    CONCAT('RECOLECTOR:', @last_rec_id)
);
SET @pla6 = LAST_INSERT_ID();
UPDATE declaracion_recolector SET declaracion_destinatario_id = @pla6 WHERE id = @last_rec_id;

-- ----------------------------------------------------------------------------
-- CASO 7: Lote con pesaje de romana y voucher formal (Variación vinculante)
-- Directo a planta
-- ----------------------------------------------------------------------------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    consumida_por_tipo
) VALUES (
    'DUMMY-CADENA-REC-CASO7', @user_rec, CURDATE() - INTERVAL 1 DAY, CURDATE() - INTERVAL 1 DAY, '14:00:00',
    'Recolector Sintético 7', 'RPA-DUMMY-7', @caleta, @comuna, @esp_huiro, @ext_tipo,
    @comp, @hum_humedo, 1200.00, 1200.00, 1.0000,
    'PLANTA_ABASTECIMIENTO'
);
SET @rec7 = LAST_INSERT_ID();

INSERT INTO declaracion_planta_abastecimiento (
    folio_origen, usuario_id, fecha_ingreso_planta, hora, cantidad, voucher_romana_numero,
    voucher_romana_adjunto, peso_romana_kg, fecha_pesaje, declaraciones_seleccionadas
) VALUES (
    'DUMMY-CADENA-PLA-CASO7', @user_pla, CURDATE(), '15:00:00', 1140.00, 'VCH-OFICIAL-9942',
    'vouchers/vch_9942_romana_scan.pdf', 1140.00, CURDATE(), CONCAT('RECOLECTOR:', @rec7)
);
SET @pla7 = LAST_INSERT_ID();
UPDATE declaracion_recolector SET declaracion_destinatario_id = @pla7 WHERE id = @rec7;

-- ----------------------------------------------------------------------------
-- CASO 8: Lote sólo con conciliación documental (Sin romana ni voucher)
-- Directo a planta
-- ----------------------------------------------------------------------------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    consumida_por_tipo
) VALUES (
    'DUMMY-CADENA-REC-CASO8', @user_rec, CURDATE() - INTERVAL 2 DAY, CURDATE() - INTERVAL 2 DAY, '15:00:00',
    'Recolector Sintético 8', 'RPA-DUMMY-8', @caleta, @comuna, @esp_huiro, @ext_tipo,
    @comp, @hum_humedo, 800.00, 800.00, 1.0000,
    'PLANTA_ABASTECIMIENTO'
);
SET @rec8 = LAST_INSERT_ID();

INSERT INTO declaracion_planta_abastecimiento (
    folio_origen, usuario_id, fecha_ingreso_planta, hora, cantidad, voucher_romana_numero,
    voucher_romana_adjunto, peso_romana_kg, fecha_pesaje, declaraciones_seleccionadas
) VALUES (
    'DUMMY-CADENA-PLA-CASO8', @user_pla, CURDATE(), '16:00:00', 760.00, NULL,
    NULL, NULL, NULL, CONCAT('RECOLECTOR:', @rec8)
);
SET @pla8 = LAST_INSERT_ID();
UPDATE declaracion_recolector SET declaracion_destinatario_id = @pla8 WHERE id = @rec8;

-- ============================================================================
-- VERIFICACIÓN DE CARGA Y ENLACES
-- ============================================================================
SELECT
    r.folio_origen AS folio_recolector,
    c.folio_origen AS folio_comercializador,
    p.folio_origen AS folio_planta,
    r.desembarque AS kg_origen,
    c.cantidad AS kg_comercializador,
    COALESCE(p.peso_romana_kg, p.cantidad) AS kg_planta
FROM declaracion_recolector r
LEFT JOIN declaracion_comercializador c
    ON (r.consumida_por_tipo = 'COMERCIALIZADOR' AND c.id = r.declaracion_destinatario_id)
LEFT JOIN declaracion_planta_abastecimiento p
    ON (c.consumida_por_tipo = 'PLANTA_ABASTECIMIENTO' AND p.id = c.declaracion_destinatario_id)
    OR (r.consumida_por_tipo = 'PLANTA_ABASTECIMIENTO' AND p.id = r.declaracion_destinatario_id)
WHERE r.folio_origen LIKE 'DUMMY-CADENA-%'
ORDER BY r.id;
