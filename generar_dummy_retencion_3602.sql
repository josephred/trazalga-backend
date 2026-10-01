-- ============================================================================
-- SCRIPT: DATOS SINTÉTICOS PARA INDICADOR 7 — RETENCIÓN EN BODEGA VIRTUAL (RES. 3602)
-- Destinatario: TrazAlga Dashboard / Auditoría de Trazabilidad y Bodega
--
-- Tramos normativos de permanencia según Res. Ex. 3602/2017:
--   1. HÚMEDO:     ≤ 24 h   (Preaviso 80% = 19.2 h)
--   2. SEMIHÚMEDO: ≤ 72 h   (Preaviso 80% = 57.6 h)
--   3. SEMISECO:   ≤ 216 h  (Preaviso 80% = 172.8 h / ~7.2 días)
--   4. SECO:       Sin límite temporal (siempre VERDE)
--
-- Cobertura de casos de prueba de la tabla de aceptación:
--   - Húmedo con 18 h:               VERDE
--   - Húmedo con 22 h:               AMARILLO
--   - Húmedo con 30 h:               ROJO (> 24 h)
--   - Semihúmedo con 50 h:           VERDE
--   - Semihúmedo con 60 h:           AMARILLO (> 80% pero ≤ 72 h)
--   - Semihúmedo con 80 h:           ROJO (> 72 h)
--   - Semiseco con 150 h:            VERDE
--   - Semiseco con 8 días (192 h):   AMARILLO (> 80% pero ≤ 216 h)
--   - Semiseco con 10 días (240 h):  ROJO (> 216 h)
--   - Seco con 15 días (360 h):      VERDE (sin límite)
--
-- IDEMPOTENTE Y REVERSIBLE: Borra todo lote con prefijo 'DUMMY-RET3602-%'
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 0. LIMPIEZA IDEMPOTENTE
-- ----------------------------------------------------------------------------
DELETE FROM declaracion_planta_abastecimiento WHERE folio_origen LIKE 'DUMMY-RET3602-%';
DELETE FROM declaracion_comercializador       WHERE folio_origen LIKE 'DUMMY-RET3602-%';
DELETE FROM declaracion_recolector            WHERE folio_origen LIKE 'DUMMY-RET3602-%';

-- ----------------------------------------------------------------------------
-- 1. RESOLUCIÓN DE MAESTROS Y USUARIOS
-- ----------------------------------------------------------------------------
SET @user_rec = (SELECT u.id FROM usuario u JOIN perfil p ON u.perfil_id = p.id WHERE p.nombre LIKE '%RECOLECTOR%' ORDER BY u.id LIMIT 1);
SET @user_com = (SELECT u.id FROM usuario u JOIN perfil p ON u.perfil_id = p.id WHERE p.nombre LIKE '%COMER%' ORDER BY u.id LIMIT 1);
SET @user_pla = (SELECT u.id FROM usuario u JOIN perfil p ON u.perfil_id = p.id WHERE p.nombre LIKE '%PLANTA%' ORDER BY u.id LIMIT 1);

SET @user_rec = COALESCE(@user_rec, 11);
SET @user_com = COALESCE(@user_com, 14);
SET @user_pla = COALESCE(@user_pla, 15);

-- Estados de humedad según maestro humedad_estado
SET @hum_humedo     = COALESCE((SELECT id FROM humedad_estado WHERE UPPER(nombre) LIKE '%HUMED%' AND UPPER(nombre) NOT LIKE '%SEMI%' LIMIT 1), 1);
SET @hum_semihumedo = COALESCE((SELECT id FROM humedad_estado WHERE UPPER(nombre) LIKE '%SEMI%HUM%' OR UPPER(nombre) LIKE '%SEMIHUM%' LIMIT 1), 2);
SET @hum_semiseco   = COALESCE((SELECT id FROM humedad_estado WHERE UPPER(nombre) LIKE '%SEMI%SEC%' OR UPPER(nombre) LIKE '%SEMISEC%' LIMIT 1), 3);
SET @hum_seco       = COALESCE((SELECT id FROM humedad_estado WHERE UPPER(nombre) LIKE '%SEC%' AND UPPER(nombre) NOT LIKE '%SEMI%' LIMIT 1), 4);

-- Especie oficial
SET @esp_palo = COALESCE((SELECT id FROM especie WHERE UPPER(nombre) LIKE '%PALO%' LIMIT 1), 1);
SET @esp_negro = COALESCE((SELECT id FROM especie WHERE UPPER(nombre) LIKE '%NEGRO%' LIMIT 1), 2);

SET @caleta = COALESCE((SELECT id FROM caleta ORDER BY id LIMIT 1), 1);
SET @comuna = COALESCE((SELECT id FROM comuna ORDER BY id LIMIT 1), 30);
SET @ext_tipo = COALESCE((SELECT id FROM extraccion_tipo ORDER BY id LIMIT 1), 1);
SET @comp = COALESCE((SELECT id FROM composicion ORDER BY id LIMIT 1), 1);

-- ----------------------------------------------------------------------------
-- 2. INSERCIÓN DE LOTES: HÚMEDO (Máx 24 h)
-- ----------------------------------------------------------------------------

-- Caso 1: Húmedo 18 h -> VERDE (< 19.2 h)
INSERT INTO declaracion_recolector (folio_origen, fecha_declaracion, hora, desembarque, captura, factor_aplicado, especie_id, humedad_estado_id, caleta_id, comuna_id, usuario_id, extraccion_tipo_id, composicion_id, usuario_destinatario_id)
VALUES ('DUMMY-RET3602-H18', DATE(NOW() - INTERVAL 18 HOUR), TIME(NOW() - INTERVAL 18 HOUR), 1200.0, 1356.0, 1.13, @esp_palo, @hum_humedo, @caleta, @comuna, @user_rec, @ext_tipo, @comp, @user_com);

INSERT INTO declaracion_comercializador (folio_origen, fecha_declaracion, hora, cantidad, especie_id, humedad_estado_id, usuario_id, usuario_destinatario_id)
VALUES ('DUMMY-RET3602-H18', DATE(NOW() - INTERVAL 17 HOUR), TIME(NOW() - INTERVAL 17 HOUR), 1200.0, @esp_palo, @hum_humedo, @user_com, @user_pla);

-- Caso 2: Húmedo 22 h -> AMARILLO (≥ 19.2 h y ≤ 24 h)
INSERT INTO declaracion_recolector (folio_origen, fecha_declaracion, hora, desembarque, captura, factor_aplicado, especie_id, humedad_estado_id, caleta_id, comuna_id, usuario_id, extraccion_tipo_id, composicion_id, usuario_destinatario_id)
VALUES ('DUMMY-RET3602-H22', DATE(NOW() - INTERVAL 22 HOUR), TIME(NOW() - INTERVAL 22 HOUR), 1500.0, 1695.0, 1.13, @esp_palo, @hum_humedo, @caleta, @comuna, @user_rec, @ext_tipo, @comp, @user_com);

INSERT INTO declaracion_comercializador (folio_origen, fecha_declaracion, hora, cantidad, especie_id, humedad_estado_id, usuario_id, usuario_destinatario_id)
VALUES ('DUMMY-RET3602-H22', DATE(NOW() - INTERVAL 21 HOUR), TIME(NOW() - INTERVAL 21 HOUR), 1500.0, @esp_palo, @hum_humedo, @user_com, @user_pla);

-- Caso 3: Húmedo 30 h -> ROJO (> 24 h)
INSERT INTO declaracion_recolector (folio_origen, fecha_declaracion, hora, desembarque, captura, factor_aplicado, especie_id, humedad_estado_id, caleta_id, comuna_id, usuario_id, extraccion_tipo_id, composicion_id, usuario_destinatario_id)
VALUES ('DUMMY-RET3602-H30', DATE(NOW() - INTERVAL 30 HOUR), TIME(NOW() - INTERVAL 30 HOUR), 1800.0, 2034.0, 1.13, @esp_palo, @hum_humedo, @caleta, @comuna, @user_rec, @ext_tipo, @comp, @user_com);

INSERT INTO declaracion_comercializador (folio_origen, fecha_declaracion, hora, cantidad, especie_id, humedad_estado_id, usuario_id, usuario_destinatario_id)
VALUES ('DUMMY-RET3602-H30', DATE(NOW() - INTERVAL 29 HOUR), TIME(NOW() - INTERVAL 29 HOUR), 1800.0, @esp_palo, @hum_humedo, @user_com, @user_pla);


-- ----------------------------------------------------------------------------
-- 3. INSERCIÓN DE LOTES: SEMIHÚMEDO (Máx 72 h / 3 días)
-- ----------------------------------------------------------------------------

-- Caso 4: Semihúmedo 50 h -> VERDE (< 57.6 h)
INSERT INTO declaracion_recolector (folio_origen, fecha_declaracion, hora, desembarque, captura, factor_aplicado, especie_id, humedad_estado_id, caleta_id, comuna_id, usuario_id, extraccion_tipo_id, composicion_id, usuario_destinatario_id)
VALUES ('DUMMY-RET3602-SH50', DATE(NOW() - INTERVAL 50 HOUR), TIME(NOW() - INTERVAL 50 HOUR), 2000.0, 3000.0, 1.50, @esp_palo, @hum_semihumedo, @caleta, @comuna, @user_rec, @ext_tipo, @comp, @user_com);

INSERT INTO declaracion_comercializador (folio_origen, fecha_declaracion, hora, cantidad, especie_id, humedad_estado_id, usuario_id, usuario_destinatario_id)
VALUES ('DUMMY-RET3602-SH50', DATE(NOW() - INTERVAL 49 HOUR), TIME(NOW() - INTERVAL 49 HOUR), 2000.0, @esp_palo, @hum_semihumedo, @user_com, @user_pla);

-- Caso 5: Semihúmedo 60 h -> AMARILLO (> 57.6 h y ≤ 72 h)
INSERT INTO declaracion_recolector (folio_origen, fecha_declaracion, hora, desembarque, captura, factor_aplicado, especie_id, humedad_estado_id, caleta_id, comuna_id, usuario_id, extraccion_tipo_id, composicion_id, usuario_destinatario_id)
VALUES ('DUMMY-RET3602-SH60', DATE(NOW() - INTERVAL 60 HOUR), TIME(NOW() - INTERVAL 60 HOUR), 2200.0, 3300.0, 1.50, @esp_palo, @hum_semihumedo, @caleta, @comuna, @user_rec, @ext_tipo, @comp, @user_com);

INSERT INTO declaracion_comercializador (folio_origen, fecha_declaracion, hora, cantidad, especie_id, humedad_estado_id, usuario_id, usuario_destinatario_id)
VALUES ('DUMMY-RET3602-SH60', DATE(NOW() - INTERVAL 58 HOUR), TIME(NOW() - INTERVAL 58 HOUR), 2200.0, @esp_palo, @hum_semihumedo, @user_com, @user_pla);

-- Caso 6: Semihúmedo 80 h -> ROJO (> 72 h)
INSERT INTO declaracion_recolector (folio_origen, fecha_declaracion, hora, desembarque, captura, factor_aplicado, especie_id, humedad_estado_id, caleta_id, comuna_id, usuario_id, extraccion_tipo_id, composicion_id, usuario_destinatario_id)
VALUES ('DUMMY-RET3602-SH80', DATE(NOW() - INTERVAL 80 HOUR), TIME(NOW() - INTERVAL 80 HOUR), 2500.0, 3750.0, 1.50, @esp_palo, @hum_semihumedo, @caleta, @comuna, @user_rec, @ext_tipo, @comp, @user_com);

INSERT INTO declaracion_comercializador (folio_origen, fecha_declaracion, hora, cantidad, especie_id, humedad_estado_id, usuario_id, usuario_destinatario_id)
VALUES ('DUMMY-RET3602-SH80', DATE(NOW() - INTERVAL 78 HOUR), TIME(NOW() - INTERVAL 78 HOUR), 2500.0, @esp_palo, @hum_semihumedo, @user_com, @user_pla);


-- ----------------------------------------------------------------------------
-- 4. INSERCIÓN DE LOTES: SEMISECO (Máx 216 h / 9 días)
-- ----------------------------------------------------------------------------

-- Caso 7: Semiseco 150 h -> VERDE (< 172.8 h)
INSERT INTO declaracion_recolector (folio_origen, fecha_declaracion, hora, desembarque, captura, factor_aplicado, especie_id, humedad_estado_id, caleta_id, comuna_id, usuario_id, extraccion_tipo_id, composicion_id, usuario_destinatario_id)
VALUES ('DUMMY-RET3602-SS150', DATE(NOW() - INTERVAL 150 HOUR), TIME(NOW() - INTERVAL 150 HOUR), 3000.0, 6000.0, 2.00, @esp_palo, @hum_semiseco, @caleta, @comuna, @user_rec, @ext_tipo, @comp, @user_com);

INSERT INTO declaracion_comercializador (folio_origen, fecha_declaracion, hora, cantidad, especie_id, humedad_estado_id, usuario_id, usuario_destinatario_id)
VALUES ('DUMMY-RET3602-SS150', DATE(NOW() - INTERVAL 148 HOUR), TIME(NOW() - INTERVAL 148 HOUR), 3000.0, @esp_palo, @hum_semiseco, @user_com, @user_pla);

-- Caso 8: Semiseco 8 días (192 h) -> AMARILLO (> 172.8 h y ≤ 216 h)
INSERT INTO declaracion_recolector (folio_origen, fecha_declaracion, hora, desembarque, captura, factor_aplicado, especie_id, humedad_estado_id, caleta_id, comuna_id, usuario_id, extraccion_tipo_id, composicion_id, usuario_destinatario_id)
VALUES ('DUMMY-RET3602-SS192', DATE(NOW() - INTERVAL 192 HOUR), TIME(NOW() - INTERVAL 192 HOUR), 3200.0, 6400.0, 2.00, @esp_palo, @hum_semiseco, @caleta, @comuna, @user_rec, @ext_tipo, @comp, @user_com);

INSERT INTO declaracion_comercializador (folio_origen, fecha_declaracion, hora, cantidad, especie_id, humedad_estado_id, usuario_id, usuario_destinatario_id)
VALUES ('DUMMY-RET3602-SS192', DATE(NOW() - INTERVAL 190 HOUR), TIME(NOW() - INTERVAL 190 HOUR), 3200.0, @esp_palo, @hum_semiseco, @user_com, @user_pla);

-- Caso 9: Semiseco 10 días (240 h) -> ROJO (> 216 h)
INSERT INTO declaracion_recolector (folio_origen, fecha_declaracion, hora, desembarque, captura, factor_aplicado, especie_id, humedad_estado_id, caleta_id, comuna_id, usuario_id, extraccion_tipo_id, composicion_id, usuario_destinatario_id)
VALUES ('DUMMY-RET3602-SS240', DATE(NOW() - INTERVAL 240 HOUR), TIME(NOW() - INTERVAL 240 HOUR), 3500.0, 7000.0, 2.00, @esp_palo, @hum_semiseco, @caleta, @comuna, @user_rec, @ext_tipo, @comp, @user_com);

INSERT INTO declaracion_comercializador (folio_origen, fecha_declaracion, hora, cantidad, especie_id, humedad_estado_id, usuario_id, usuario_destinatario_id)
VALUES ('DUMMY-RET3602-SS240', DATE(NOW() - INTERVAL 238 HOUR), TIME(NOW() - INTERVAL 238 HOUR), 3500.0, @esp_palo, @hum_semiseco, @user_com, @user_pla);


-- ----------------------------------------------------------------------------
-- 5. INSERCIÓN DE LOTES: SECO (Sin límite temporal)
-- ----------------------------------------------------------------------------

-- Caso 10: Seco 15 días (360 h) -> VERDE (sin límite)
INSERT INTO declaracion_recolector (folio_origen, fecha_declaracion, hora, desembarque, captura, factor_aplicado, especie_id, humedad_estado_id, caleta_id, comuna_id, usuario_id, extraccion_tipo_id, composicion_id, usuario_destinatario_id)
VALUES ('DUMMY-RET3602-S360', DATE(NOW() - INTERVAL 360 HOUR), TIME(NOW() - INTERVAL 360 HOUR), 4000.0, 14320.0, 3.58, @esp_palo, @hum_seco, @caleta, @comuna, @user_rec, @ext_tipo, @comp, @user_com);

INSERT INTO declaracion_comercializador (folio_origen, fecha_declaracion, hora, cantidad, especie_id, humedad_estado_id, usuario_id, usuario_destinatario_id)
VALUES ('DUMMY-RET3602-S360', DATE(NOW() - INTERVAL 350 HOUR), TIME(NOW() - INTERVAL 350 HOUR), 4000.0, @esp_palo, @hum_seco, @user_com, @user_pla);

-- Lote Destinado Fuera de Plazo (Semihúmedo despachado a planta con 90 h de tránsito)
INSERT INTO declaracion_recolector (folio_origen, fecha_declaracion, hora, desembarque, captura, factor_aplicado, especie_id, humedad_estado_id, caleta_id, comuna_id, usuario_id, extraccion_tipo_id, composicion_id, usuario_destinatario_id)
VALUES ('DUMMY-RET3602-DEST-ROJO', DATE(NOW() - INTERVAL 120 HOUR), TIME(NOW() - INTERVAL 120 HOUR), 1600.0, 2400.0, 1.50, @esp_negro, @hum_semihumedo, @caleta, @comuna, @user_rec, @ext_tipo, @comp, @user_com);

INSERT INTO declaracion_comercializador (folio_origen, fecha_declaracion, hora, cantidad, especie_id, humedad_estado_id, usuario_id, usuario_destinatario_id)
VALUES ('DUMMY-RET3602-DEST-ROJO', DATE(NOW() - INTERVAL 115 HOUR), TIME(NOW() - INTERVAL 115 HOUR), 1600.0, @esp_negro, @hum_semihumedo, @user_com, @user_pla);

INSERT INTO declaracion_planta_abastecimiento (folio_origen, fecha_ingreso_planta, cantidad, especie_id, humedad_estado_id, usuario_id, usuario_destinatario_id)
VALUES ('DUMMY-RET3602-DEST-ROJO', DATE(NOW() - INTERVAL 25 HOUR), 1600.0, @esp_negro, @hum_semihumedo, @user_pla, @user_pla);

-- ----------------------------------------------------------------------------
-- 6. VERIFICACIÓN FINAL
-- ----------------------------------------------------------------------------
SELECT 
    dr.folio_origen,
    dr.fecha_declaracion,
    h.nombre as humedad_declarada,
    TIMESTAMPDIFF(HOUR, dr.fecha_declaracion, NOW()) as horas_transcurridas,
    CASE 
        WHEN UPPER(h.nombre) LIKE '%SEC%' AND UPPER(h.nombre) NOT LIKE '%SEMI%' THEN 'VERDE (Sin límite)'
        WHEN UPPER(h.nombre) LIKE '%SEMI%SEC%' AND TIMESTAMPDIFF(HOUR, dr.fecha_declaracion, NOW()) > 216 THEN 'ROJO (> 216h)'
        WHEN UPPER(h.nombre) LIKE '%SEMI%SEC%' AND TIMESTAMPDIFF(HOUR, dr.fecha_declaracion, NOW()) >= 173 THEN 'AMARILLO (≥ 173h)'
        WHEN UPPER(h.nombre) LIKE '%SEMI%SEC%' THEN 'VERDE (≤ 172h)'
        WHEN UPPER(h.nombre) LIKE '%SEMI%HUM%' AND TIMESTAMPDIFF(HOUR, dr.fecha_declaracion, NOW()) > 72 THEN 'ROJO (> 72h)'
        WHEN UPPER(h.nombre) LIKE '%SEMI%HUM%' AND TIMESTAMPDIFF(HOUR, dr.fecha_declaracion, NOW()) >= 58 THEN 'AMARILLO (≥ 58h)'
        WHEN UPPER(h.nombre) LIKE '%SEMI%HUM%' THEN 'VERDE (≤ 57h)'
        WHEN TIMESTAMPDIFF(HOUR, dr.fecha_declaracion, NOW()) > 24 THEN 'ROJO (> 24h)'
        WHEN TIMESTAMPDIFF(HOUR, dr.fecha_declaracion, NOW()) >= 19 THEN 'AMARILLO (≥ 19h)'
        ELSE 'VERDE (≤ 18h)'
    END as semaforo_esperado
FROM declaracion_recolector dr
JOIN humedad_estado h ON dr.humedad_estado_id = h.id
WHERE dr.folio_origen LIKE 'DUMMY-RET3602-%'
ORDER BY horas_transcurridas ASC;
