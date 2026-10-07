-- ============================================================================
-- SCRIPT: GENERAR DUMMY DATA PARA FICHA DE TRAZABILIDAD (TX.3 / T2.3)
-- Prefijo obligatorio: DUMMY-02OCT-
--
-- Escenario normativo de aceptación:
-- - Dos recolectores (1.000 kg y 1.500 kg en húmedo) declaran el 01-10 a las
--   08:00 y a las 09:00 a nombre de un comercializador.
-- - El comercializador despacha el 01-10 a las 20:00, con guía 4455 y patente ABCD12.
-- - La planta recibe el 02-10 a las 10:00, con 2.300 kg en romana.
--
-- Resultados esperados:
-- - Primer recolector muestra el segundo como "misma carga".
-- - Comercializador con recepción 01-10 a las 08:00, despacho 01-10 20:00 y 12.0h en bodega (VERDE).
-- - Planta con 2.300 kg en romana y variación de -8.0% contra el origen total (2.500 kg).
-- ============================================================================

-- 0. Limpieza previa de datos sintéticos
DELETE FROM declaracion_marca WHERE declaracion_id IN (
    SELECT id FROM declaracion_recolector WHERE folio_origen LIKE 'DUMMY-02OCT-%'
) AND declaracion_tipo = 'RECOLECTOR';

DELETE FROM declaracion_planta_abastecimiento WHERE folio_origen LIKE 'DUMMY-02OCT-%';
DELETE FROM declaracion_comercializador WHERE folio_origen LIKE 'DUMMY-02OCT-%';
DELETE FROM declaracion_recolector WHERE folio_origen LIKE 'DUMMY-02OCT-%';

-- 1. Maestros y variables de apoyo
SET @user_rec1 = (SELECT id FROM usuario WHERE perfil = 'RECOLECTOR' ORDER BY id ASC LIMIT 1);
SET @user_rec2 = (SELECT id FROM usuario WHERE perfil = 'RECOLECTOR' ORDER BY id DESC LIMIT 1);
SET @user_com  = (SELECT id FROM usuario WHERE perfil = 'COMERCIALIZADOR' LIMIT 1);
SET @user_pla  = (SELECT id FROM usuario WHERE perfil = 'PLANTA' LIMIT 1);

SET @user_rec1 = COALESCE(@user_rec1, 1);
SET @user_rec2 = COALESCE(@user_rec2, @user_rec1);
SET @user_com  = COALESCE(@user_com, 1);
SET @user_pla  = COALESCE(@user_pla, 1);

SET @esp_huiro = (SELECT id FROM especie WHERE UPPER(nombre) LIKE '%HUIRO NEGRO%' LIMIT 1);
SET @esp_huiro = COALESCE(@esp_huiro, (SELECT id FROM especie LIMIT 1), 1);

SET @hum_humedo = (SELECT id FROM humedad_estado WHERE UPPER(nombre) LIKE '%HUMED%' LIMIT 1);
SET @hum_humedo = COALESCE(@hum_humedo, 1);

SET @caleta = (SELECT id FROM caleta LIMIT 1);
SET @caleta = COALESCE(@caleta, 1);

SET @comuna = (SELECT id FROM comuna LIMIT 1);
SET @comuna = COALESCE(@comuna, 1);

SET @ext_tipo = (SELECT id FROM extraccion_tipo LIMIT 1);
SET @ext_tipo = COALESCE(@ext_tipo, 1);

SET @comp = (SELECT id FROM composicion LIMIT 1);
SET @comp = COALESCE(@comp, 1);

-- 2. Insertar Recolector 1 (1.000 kg, 01-10-2026 08:00:00)
INSERT INTO declaracion_recolector (
    folio_origen, folio_desembarque_ro, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id,
    consumida_por_tipo, estado
) VALUES (
    'DUMMY-02OCT-REC-01', 'RO-02OCT-1001', @user_rec1, '2026-10-01', '2026-10-01', '08:00:00',
    'Recolector Ficha 1', 'RPA-02OCT-01', @caleta, @comuna, @esp_huiro, @ext_tipo,
    @comp, @hum_humedo, 1000.00, 1000.00, 1.0000,
    '76.999.888-1', 'Comercializadora Algas Ficha', @user_com,
    'COMERCIALIZADOR', 'ENVIADA'
);
SET @rec1_id = LAST_INSERT_ID();

-- 3. Insertar Recolector 2 (1.500 kg, 01-10-2026 09:00:00)
INSERT INTO declaracion_recolector (
    folio_origen, folio_desembarque_ro, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id,
    consumida_por_tipo, estado
) VALUES (
    'DUMMY-02OCT-REC-02', 'RO-02OCT-1002', @user_rec2, '2026-10-01', '2026-10-01', '09:00:00',
    'Recolector Ficha 2', 'RPA-02OCT-02', @caleta, @comuna, @esp_huiro, @ext_tipo,
    @comp, @hum_humedo, 1500.00, 1500.00, 1.0000,
    '76.999.888-1', 'Comercializadora Algas Ficha', @user_com,
    'COMERCIALIZADOR', 'ENVIADA'
);
SET @rec2_id = LAST_INSERT_ID();

-- 4. Insertar Comercializador (2.500 kg, 01-10-2026 20:00:00, Guía 4455, Patente ABCD12)
INSERT INTO declaracion_comercializador (
    folio_origen, folio_desembarque_ac, usuario_id, fecha_declaracion, fecha_traslado, hora,
    codigo_sernapesca, nombre_comercializador, especie_id, composicion_id, humedad_estado_id,
    cantidad, documento_tributario_origen_tipo, documento_tributario_origen_numero, documento_tributario_origen_fecha,
    documento_tributario_destino_tipo, documento_tributario_destino_numero, documento_tributario_destino_fecha,
    placa_patente, vehiculoTransporte, choferTransporte, rut_chofer,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id,
    declaraciones_seleccionadas, consumida_por_tipo, estado
) VALUES (
    'DUMMY-02OCT-COM-01', 'AC-02OCT-2001', @user_com, '2026-10-01', '2026-10-01', '20:00:00',
    'COM-02OCT-99', 'Comercializadora Algas Ficha', @esp_huiro, @comp, @hum_humedo,
    2500.00, 'GUIA_DESPACHO', '4455', '2026-10-01',
    'GUIA_DESPACHO', '4455', '2026-10-01',
    'ABCD12', 'Camión Tolva', 'Chofer Ficha', '15.555.555-5',
    '77.111.222-3', 'Planta Biopacífico Ficha', @user_pla,
    CONCAT('RECOLECTOR:', @rec1_id, ',RECOLECTOR:', @rec2_id), 'PLANTA_ABASTECIMIENTO', 'ENVIADA'
);
SET @com_id = LAST_INSERT_ID();

-- Actualizar enlace downstream en los orígenes
UPDATE declaracion_recolector SET declaracion_destinatario_id = @com_id WHERE id IN (@rec1_id, @rec2_id);

-- 5. Insertar Planta Abastecimiento (2.300 kg en romana, 02-10-2026 10:00:00)
INSERT INTO declaracion_planta_abastecimiento (
    folio_origen, folio_declaracion_a_pla, usuario_id, fecha_ingreso_planta, fecha_traslado, hora,
    nombre_planta, codigo_sernapesca, especie_id, composicion_id, humedad_estado_id,
    cantidad, peso_romana_kg, voucher_romana_numero, fecha_pesaje,
    documento_tributario_origen_tipo, documento_tributario_origen_numero, documento_tributario_origen_fecha,
    placa_patente, declaraciones_seleccionadas, estado
) VALUES (
    'DUMMY-02OCT-PLA-01', 'DAPLA-02OCT-3001', @user_pla, '2026-10-02', '2026-10-02', '10:00:00',
    'Planta Biopacífico Ficha', 'PLA-02OCT-88', @esp_huiro, @comp, @hum_humedo,
    2300.00, 2300.00, 'VCH-02OCT-9988', '2026-10-02',
    'GUIA_DESPACHO', '4455', '2026-10-01',
    'ABCD12', CONCAT('COMERCIALIZADOR:', @com_id), 'ENVIADA'
);
SET @pla_id = LAST_INSERT_ID();

-- Actualizar enlace downstream en el comercializador
UPDATE declaracion_comercializador SET declaracion_destinatario_id = @pla_id WHERE id = @com_id;
