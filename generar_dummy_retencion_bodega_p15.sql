-- ============================================================================
-- SCRIPT: DATOS SINTÉTICOS PARA EL INDICADOR 7 — RETENCIÓN EN BODEGA VIRTUAL
-- Destinatario: Usuario Planta de Abastecimiento ID 15 (Planta ABAS L. H.)
--
-- Motivo: Un lote se considera en "Bodega Virtual" cuando fue adquirido por
-- un comercializador pero aún no ha sido recepcionado en Planta de Abastecimiento
-- (fecha_comercializador IS NOT NULL AND fecha_planta IS NULL).
--
-- Este script siembra 8 lotes con destino a la Planta ID 15 con antigüedades
-- relativas a CURDATE() para encender los cuatro colores del semáforo preventivo:
--   - 2 Lotes VERDES (< 3 días)
--   - 2 Lotes AMARILLOS (3 a 4 días)
--   - 2 Lotes NARANJAS (5 a 6 días)
--   - 2 Lotes ROJOS (≥ 7 días)
--
-- REVERSIBLE: Borra todo lote con prefijo DUMMY-RET-P15-%
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 0. LIMPIEZA IDEMPOTENTE
-- ----------------------------------------------------------------------------
DELETE FROM declaracion_planta_abastecimiento WHERE folio_origen LIKE 'DUMMY-RET-P15-%';
DELETE FROM declaracion_comercializador       WHERE folio_origen LIKE 'DUMMY-RET-P15-%';
DELETE FROM declaracion_recolector            WHERE folio_origen LIKE 'DUMMY-RET-P15-%';

-- ----------------------------------------------------------------------------
-- 1. RESOLUCIÓN DE ACTORES Y MAESTROS
-- ----------------------------------------------------------------------------
SET @user_rec = (SELECT u.id FROM usuario u JOIN perfil p ON u.perfil_id = p.id WHERE p.nombre LIKE '%RECOLECTOR%' ORDER BY u.id LIMIT 1);
SET @user_com = (SELECT u.id FROM usuario u JOIN perfil p ON u.perfil_id = p.id WHERE p.nombre LIKE '%COMER%' ORDER BY u.id LIMIT 1);
SET @user_pla = 15; -- Usuario Planta de Abastecimiento de Pruebas solicitada por el usuario

SET @user_rec = COALESCE(@user_rec, 11);
SET @user_com = COALESCE(@user_com, 14);

-- Datos del destinatario Planta 15
SET @pla_rut    = COALESCE((SELECT rut FROM usuario WHERE id = @user_pla), '6666');
SET @pla_nombre = COALESCE((SELECT TRIM(CONCAT(COALESCE(nombres, ''), ' ', COALESCE(apellidop, ''), ' ', COALESCE(apellidom, ''))) FROM usuario WHERE id = @user_pla), 'Planta ABAS L. H.');

-- Especies oficiales
SET @esp_palo     = COALESCE((SELECT id FROM especie WHERE UPPER(nombre) LIKE '%HUIRO PALO%' OR UPPER(nombre) LIKE '%PALO%' LIMIT 1), 1);
SET @esp_carola   = COALESCE((SELECT id FROM especie WHERE UPPER(nombre) LIKE '%CAROLA%' LIMIT 1), 2);
SET @esp_chasca   = COALESCE((SELECT id FROM especie WHERE UPPER(nombre) LIKE '%CHASCA%' LIMIT 1), 3);
SET @esp_chicorea = COALESCE((SELECT id FROM especie WHERE UPPER(nombre) LIKE '%CHICOREA%' LIMIT 1), 4);

-- Humedad: HÚMEDO (estado sujeto a semáforo por descomposición/merma biológica)
SET @hum_hum = COALESCE((SELECT id FROM humedad_estado WHERE UPPER(nombre) NOT LIKE '%SEMI%' AND UPPER(nombre) NOT LIKE '%SEC%' LIMIT 1), 1);

SET @caleta   = COALESCE((SELECT id FROM caleta ORDER BY id LIMIT 1), 1);
SET @comuna   = COALESCE((SELECT id FROM comuna ORDER BY id LIMIT 1), 30);
SET @ext_tipo = COALESCE((SELECT id FROM extraccion_tipo ORDER BY id LIMIT 1), 1);
SET @comp     = COALESCE((SELECT id FROM composicion ORDER BY id LIMIT 1), 1);

-- Factor biológico oficial húmedo
SET @f_hum = 1.1300;

-- ----------------------------------------------------------------------------
-- 2. INSERCIÓN DE LOTES RETENIDOS EN BODEGA VIRTUAL
-- ----------------------------------------------------------------------------

-- ============================================================================
-- BLOQUE A: SEMÁFORO VERDE (< 3 días en bodega)
-- ============================================================================

-- ---- Lote 1: 1 día en bodega (Ayer) ----------------------------------------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-RET-P15-01', @user_rec, DATE_SUB(CURDATE(), INTERVAL 2 DAY), DATE_SUB(CURDATE(), INTERVAL 2 DAY), '08:30:00',
    'Recolector Sintético', 'RPA-RET-01', @caleta, @comuna, @esp_palo, @ext_tipo,
    @comp, @hum_hum, 1250.00, 1250.00 * @f_hum, @f_hum,
    '5555', 'Carmen Comer A.', @user_com
);

INSERT INTO declaracion_comercializador (
    folio_desembarque_ac, folio_origen, usuario_id, fecha_declaracion, fecha_traslado, hora,
    cantidad, especie_id, humedad_estado_id, composicion_id,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id,
    patente, placa_patente, vehiculo_transporte, chofer_transporte, rut_chofer, estado
) VALUES (
    'DC-RET-P15-01', 'DUMMY-RET-P15-01', @user_com, DATE_SUB(CURDATE(), INTERVAL 1 DAY), DATE_SUB(CURDATE(), INTERVAL 1 DAY), '11:00:00',
    1250.00, @esp_palo, @hum_hum, @comp,
    @pla_rut, @pla_nombre, @user_pla,
    'AB-CD-11', 'AB-CD-11', 'CAMIÓN', 'Esteban Chofer', '15234567-8', 'ENVIADA'
);

-- ---- Lote 2: 2 días en bodega ----------------------------------------------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-RET-P15-02', @user_rec, DATE_SUB(CURDATE(), INTERVAL 3 DAY), DATE_SUB(CURDATE(), INTERVAL 3 DAY), '09:15:00',
    'Recolector Sintético', 'RPA-RET-02', @caleta, @comuna, @esp_carola, @ext_tipo,
    @comp, @hum_hum, 980.00, 980.00 * @f_hum, @f_hum,
    '5555', 'Carmen Comer A.', @user_com
);

INSERT INTO declaracion_comercializador (
    folio_desembarque_ac, folio_origen, usuario_id, fecha_declaracion, fecha_traslado, hora,
    cantidad, especie_id, humedad_estado_id, composicion_id,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id,
    patente, placa_patente, vehiculo_transporte, chofer_transporte, rut_chofer, estado
) VALUES (
    'DC-RET-P15-02', 'DUMMY-RET-P15-02', @user_com, DATE_SUB(CURDATE(), INTERVAL 2 DAY), DATE_SUB(CURDATE(), INTERVAL 2 DAY), '14:20:00',
    980.00, @esp_carola, @hum_hum, @comp,
    @pla_rut, @pla_nombre, @user_pla,
    'BC-DE-22', 'BC-DE-22', 'CAMIÓN', 'Mario Chofer', '16345678-9', 'ENVIADA'
);

-- ============================================================================
-- BLOQUE B: SEMÁFORO AMARILLO (3 a 4 días en bodega)
-- ============================================================================

-- ---- Lote 3: 3 días en bodega ----------------------------------------------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-RET-P15-03', @user_rec, DATE_SUB(CURDATE(), INTERVAL 4 DAY), DATE_SUB(CURDATE(), INTERVAL 4 DAY), '07:50:00',
    'Recolector Sintético', 'RPA-RET-03', @caleta, @comuna, @esp_chasca, @ext_tipo,
    @comp, @hum_hum, 1500.00, 1500.00 * @f_hum, @f_hum,
    '5555', 'Carmen Comer A.', @user_com
);

INSERT INTO declaracion_comercializador (
    folio_desembarque_ac, folio_origen, usuario_id, fecha_declaracion, fecha_traslado, hora,
    cantidad, especie_id, humedad_estado_id, composicion_id,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id,
    patente, placa_patente, vehiculo_transporte, chofer_transporte, rut_chofer, estado
) VALUES (
    'DC-RET-P15-03', 'DUMMY-RET-P15-03', @user_com, DATE_SUB(CURDATE(), INTERVAL 3 DAY), DATE_SUB(CURDATE(), INTERVAL 3 DAY), '10:30:00',
    1500.00, @esp_chasca, @hum_hum, @comp,
    @pla_rut, @pla_nombre, @user_pla,
    'CD-EF-33', 'CD-EF-33', 'CAMIÓN', 'Luis Chofer', '17456789-0', 'ENVIADA'
);

-- ---- Lote 4: 4 días en bodega ----------------------------------------------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-RET-P15-04', @user_rec, DATE_SUB(CURDATE(), INTERVAL 5 DAY), DATE_SUB(CURDATE(), INTERVAL 5 DAY), '08:10:00',
    'Recolector Sintético', 'RPA-RET-04', @caleta, @comuna, @esp_chicorea, @ext_tipo,
    @comp, @hum_hum, 820.00, 820.00 * @f_hum, @f_hum,
    '5555', 'Carmen Comer A.', @user_com
);

INSERT INTO declaracion_comercializador (
    folio_desembarque_ac, folio_origen, usuario_id, fecha_declaracion, fecha_traslado, hora,
    cantidad, especie_id, humedad_estado_id, composicion_id,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id,
    patente, placa_patente, vehiculo_transporte, chofer_transporte, rut_chofer, estado
) VALUES (
    'DC-RET-P15-04', 'DUMMY-RET-P15-04', @user_com, DATE_SUB(CURDATE(), INTERVAL 4 DAY), DATE_SUB(CURDATE(), INTERVAL 4 DAY), '12:00:00',
    820.00, @esp_chicorea, @hum_hum, @comp,
    @pla_rut, @pla_nombre, @user_pla,
    'DE-FG-44', 'DE-FG-44', 'CAMIÓN', 'Roberto Chofer', '18567890-1', 'ENVIADA'
);

-- ============================================================================
-- BLOQUE C: SEMÁFORO NARANJA (5 a 6 días en bodega)
-- ============================================================================

-- ---- Lote 5: 5 días en bodega ----------------------------------------------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-RET-P15-05', @user_rec, DATE_SUB(CURDATE(), INTERVAL 6 DAY), DATE_SUB(CURDATE(), INTERVAL 6 DAY), '06:40:00',
    'Recolector Sintético', 'RPA-RET-05', @caleta, @comuna, @esp_palo, @ext_tipo,
    @comp, @hum_hum, 1800.00, 1800.00 * @f_hum, @f_hum,
    '5555', 'Carmen Comer A.', @user_com
);

INSERT INTO declaracion_comercializador (
    folio_desembarque_ac, folio_origen, usuario_id, fecha_declaracion, fecha_traslado, hora,
    cantidad, especie_id, humedad_estado_id, composicion_id,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id,
    patente, placa_patente, vehiculo_transporte, chofer_transporte, rut_chofer, estado
) VALUES (
    'DC-RET-P15-05', 'DUMMY-RET-P15-05', @user_com, DATE_SUB(CURDATE(), INTERVAL 5 DAY), DATE_SUB(CURDATE(), INTERVAL 5 DAY), '09:45:00',
    1800.00, @esp_palo, @hum_hum, @comp,
    @pla_rut, @pla_nombre, @user_pla,
    'EF-GH-55', 'EF-GH-55', 'CAMIÓN', 'Gabriel Chofer', '19678901-2', 'ENVIADA'
);

-- ---- Lote 6: 6 días en bodega ----------------------------------------------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-RET-P15-06', @user_rec, DATE_SUB(CURDATE(), INTERVAL 7 DAY), DATE_SUB(CURDATE(), INTERVAL 7 DAY), '07:15:00',
    'Recolector Sintético', 'RPA-RET-06', @caleta, @comuna, @esp_carola, @ext_tipo,
    @comp, @hum_hum, 1100.00, 1100.00 * @f_hum, @f_hum,
    '5555', 'Carmen Comer A.', @user_com
);

INSERT INTO declaracion_comercializador (
    folio_desembarque_ac, folio_origen, usuario_id, fecha_declaracion, fecha_traslado, hora,
    cantidad, especie_id, humedad_estado_id, composicion_id,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id,
    patente, placa_patente, vehiculo_transporte, chofer_transporte, rut_chofer, estado
) VALUES (
    'DC-RET-P15-06', 'DUMMY-RET-P15-06', @user_com, DATE_SUB(CURDATE(), INTERVAL 6 DAY), DATE_SUB(CURDATE(), INTERVAL 6 DAY), '11:15:00',
    1100.00, @esp_carola, @hum_hum, @comp,
    @pla_rut, @pla_nombre, @user_pla,
    'FG-HI-66', 'FG-HI-66', 'CAMIÓN', 'Héctor Chofer', '10789012-3', 'ENVIADA'
);

-- ============================================================================
-- BLOQUE D: SEMÁFORO ROJO (≥ 7 días en bodega)
-- ============================================================================

-- ---- Lote 7: 8 días en bodega ----------------------------------------------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-RET-P15-07', @user_rec, DATE_SUB(CURDATE(), INTERVAL 9 DAY), DATE_SUB(CURDATE(), INTERVAL 9 DAY), '08:00:00',
    'Recolector Sintético', 'RPA-RET-07', @caleta, @comuna, @esp_chasca, @ext_tipo,
    @comp, @hum_hum, 1400.00, 1400.00 * @f_hum, @f_hum,
    '5555', 'Carmen Comer A.', @user_com
);

INSERT INTO declaracion_comercializador (
    folio_desembarque_ac, folio_origen, usuario_id, fecha_declaracion, fecha_traslado, hora,
    cantidad, especie_id, humedad_estado_id, composicion_id,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id,
    patente, placa_patente, vehiculo_transporte, chofer_transporte, rut_chofer, estado
) VALUES (
    'DC-RET-P15-07', 'DUMMY-RET-P15-07', @user_com, DATE_SUB(CURDATE(), INTERVAL 8 DAY), DATE_SUB(CURDATE(), INTERVAL 8 DAY), '15:30:00',
    1400.00, @esp_chasca, @hum_hum, @comp,
    @pla_rut, @pla_nombre, @user_pla,
    'GH-IJ-77', 'GH-IJ-77', 'CAMIÓN', 'Ignacio Chofer', '11890123-4', 'ENVIADA'
);

-- ---- Lote 8: 12 días en bodega ---------------------------------------------
INSERT INTO declaracion_recolector (
    folio_origen, usuario_id, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    composicion_id, humedad_estado_id, desembarque, captura, factor_aplicado,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    'DUMMY-RET-P15-08', @user_rec, DATE_SUB(CURDATE(), INTERVAL 14 DAY), DATE_SUB(CURDATE(), INTERVAL 14 DAY), '07:30:00',
    'Recolector Sintético', 'RPA-RET-08', @caleta, @comuna, @esp_palo, @ext_tipo,
    @comp, @hum_hum, 2100.00, 2100.00 * @f_hum, @f_hum,
    '5555', 'Carmen Comer A.', @user_com
);

INSERT INTO declaracion_comercializador (
    folio_desembarque_ac, folio_origen, usuario_id, fecha_declaracion, fecha_traslado, hora,
    cantidad, especie_id, humedad_estado_id, composicion_id,
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id,
    patente, placa_patente, vehiculo_transporte, chofer_transporte, rut_chofer, estado
) VALUES (
    'DC-RET-P15-08', 'DUMMY-RET-P15-08', @user_com, DATE_SUB(CURDATE(), INTERVAL 12 DAY), DATE_SUB(CURDATE(), INTERVAL 12 DAY), '10:00:00',
    2100.00, @esp_palo, @hum_hum, @comp,
    @pla_rut, @pla_nombre, @user_pla,
    'HI-JK-88', 'HI-JK-88', 'CAMIÓN', 'Jorge Chofer', '12901234-5', 'ENVIADA'
);

-- ----------------------------------------------------------------------------
-- 3. VERIFICACIÓN IN SITU
-- ----------------------------------------------------------------------------
SELECT 
    dc.folio_origen,
    e.nombre AS especie,
    dc.cantidad AS kg,
    dc.fecha_declaracion,
    DATEDIFF(CURDATE(), dc.fecha_declaracion) AS dias_bodega,
    dc.nombre_destinatario AS destino_planta,
    CASE 
        WHEN DATEDIFF(CURDATE(), dc.fecha_declaracion) >= 7 THEN 'ROJA'
        WHEN DATEDIFF(CURDATE(), dc.fecha_declaracion) >= 5 THEN 'NARANJA'
        WHEN DATEDIFF(CURDATE(), dc.fecha_declaracion) >= 3 THEN 'AMARILLA'
        ELSE 'VERDE'
    END AS semaforo
FROM declaracion_comercializador dc
JOIN especie e ON dc.especie_id = e.id
WHERE dc.folio_origen LIKE 'DUMMY-RET-P15-%'
ORDER BY dias_bodega ASC;
