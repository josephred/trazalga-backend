-- =============================================================================
-- SCRIPT DE DATOS SINTÉTICOS: DOBLE OPERACIÓN GEOTEMPORAL (T8.1 - T8.3)
-- Res. 25-sep / Indicador 8
-- Genera datos de prueba para validar la detección de trayectos imposibles
-- y la presencia de marcas oficiales DOBLE_OPERACION.
-- =============================================================================

-- 1. Limpieza de datos previos de prueba
DELETE FROM declaracion_marca WHERE marca = 'DOBLE_OPERACION';
DELETE FROM declaracion_recolector WHERE folio_origen LIKE 'DUMMY-DO-%';
DELETE FROM declaracion_armador WHERE folio_origen LIKE 'DUMMY-DO-%';

-- 2. Asegurar existencia de usuario para pruebas
INSERT INTO usuario (id, rut, nombres, apellidop, correo, estado)
VALUES (9801, '18.888.888-8', 'Roberto', 'Inconsistente', 'roberto.inconsistente@trazalga.cl', 'ACTIVO')
ON DUPLICATE KEY UPDATE nombres = 'Roberto', apellidop = 'Inconsistente';

INSERT INTO usuario (id, rut, nombres, apellidop, correo, estado)
VALUES (9802, '19.999.999-9', 'Camila', 'Coherente', 'camila.coherente@trazalga.cl', 'ACTIVO')
ON DUPLICATE KEY UPDATE nombres = 'Camila', apellidop = 'Coherente';

INSERT INTO usuario (id, rut, nombres, apellidop, correo, estado)
VALUES (9803, '17.777.777-7', 'Patricio', 'Cercano', 'patricio.cercano@trazalga.cl', 'ACTIVO')
ON DUPLICATE KEY UPDATE nombres = 'Patricio', apellidop = 'Cercano';

-- 3. CASO 1 (HALLAZGO CRÍTICO): Roberto declara en Caleta A (Coquimbo) a las 10:00
--    y a las 10:20 a 60 km de distancia (velocidad implícita 180 km/h)
INSERT INTO declaracion_recolector (
    id, usuario_id, folio_origen, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    humedad_estado_id, desembarque, captura, latitud, longitud
) VALUES (
    9811, 9801, 'DUMMY-DO-01A', CURDATE(), CURDATE(), '10:00:00',
    'Roberto Inconsistente', 'RPA-9801', 1, 1, 1, 1,
    1, 1200.00, 1200.00, -29.9533, -71.3436
);

INSERT INTO declaracion_armador (
    id, usuario_id, folio_origen, fecha_extraccion, fecha_declaracion, hora,
    embarcacion_id, buzo_id, caleta_id, especie_id, extraccion_tipo_id,
    humedad_estado_id, desembarque, captura, latitud, longitud
) VALUES (
    9812, 9801, 'DUMMY-DO-01B', CURDATE(), CURDATE(), '10:20:00',
    1, 1, 1, 1, 1,
    1, 2500.00, 2500.00, -29.4133, -71.3436
);

-- Marca asociada al Caso 1
INSERT INTO declaracion_marca (
    declaracion_tipo, declaracion_id, marca, detalle, resuelta, created_at
) VALUES (
    'ARMADOR', 9812, 'DOBLE_OPERACION',
    'Declaración DUMMY-DO-01B en (-29.4133,-71.3436) a 60.0 km de DUMMY-DO-01A con 20 min de diferencia (velocidad implícita 180.1 km/h)',
    false, NOW()
);

-- 4. CASO 2 (COHERENTE): Camila declara en Caleta A a las 10:00
--    y a las 12:00 (2h después) a 60 km (velocidad implícita 30 km/h <= 80 km/h)
INSERT INTO declaracion_recolector (
    id, usuario_id, folio_origen, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    humedad_estado_id, desembarque, captura, latitud, longitud
) VALUES (
    9821, 9802, 'DUMMY-DO-02A', CURDATE(), CURDATE(), '10:00:00',
    'Camila Coherente', 'RPA-9802', 1, 1, 1, 1,
    1, 1100.00, 1100.00, -29.9533, -71.3436
);

INSERT INTO declaracion_armador (
    id, usuario_id, folio_origen, fecha_extraccion, fecha_declaracion, hora,
    embarcacion_id, buzo_id, caleta_id, especie_id, extraccion_tipo_id,
    humedad_estado_id, desembarque, captura, latitud, longitud
) VALUES (
    9822, 9802, 'DUMMY-DO-02B', CURDATE(), CURDATE(), '12:00:00',
    1, 1, 1, 1, 1,
    1, 1800.00, 1800.00, -29.4133, -71.3436
);

-- 5. CASO 3 (BAJO DISTANCIA MÍNIMA): Patricio declara a las 10:00 y a las 10:05 a 2 km
INSERT INTO declaracion_recolector (
    id, usuario_id, folio_origen, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    humedad_estado_id, desembarque, captura, latitud, longitud
) VALUES (
    9831, 9803, 'DUMMY-DO-03A', CURDATE(), CURDATE(), '10:00:00',
    'Patricio Cercano', 'RPA-9803', 1, 1, 1, 1,
    1, 800.00, 800.00, -29.9533, -71.3436
);

INSERT INTO declaracion_recolector (
    id, usuario_id, folio_origen, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    humedad_estado_id, desembarque, captura, latitud, longitud
) VALUES (
    9832, 9803, 'DUMMY-DO-03B', CURDATE(), CURDATE(), '10:05:00',
    'Patricio Cercano', 'RPA-9803', 1, 1, 1, 1,
    1, 950.00, 950.00, -29.9713, -71.3436
);

-- 6. CASO 4 (SIN COORDENADAS): Declaraciones sin geolocalización
INSERT INTO declaracion_recolector (
    id, usuario_id, folio_origen, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    humedad_estado_id, desembarque, captura, latitud, longitud
) VALUES (
    9841, 9801, 'DUMMY-DO-04A', CURDATE(), CURDATE(), '16:00:00',
    'Roberto Inconsistente', 'RPA-9801', 1, 1, 1, 1,
    1, 600.00, 600.00, NULL, NULL
);
