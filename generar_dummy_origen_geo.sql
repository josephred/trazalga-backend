-- =========================================================================
-- DATOS SINTÉTICOS: Origen Real vs GPS y Detección de Patrones (Punto 9 / TX.3)
-- Prefijo obligatorio: DUMMY-25SEP-%
-- Ref: PLAN_ANTIGRAVITY_refinamiento_25sep.md / 09_ORIGEN_VS_GPS.md
-- =========================================================================

-- Limpieza de ejecuciones previas
DELETE FROM declaracion_marca WHERE motivo LIKE '%DUMMY-25SEP-GEO%';
DELETE FROM declaracion_recolector WHERE folio_origen LIKE 'DUMMY-25SEP-GEO%';

-- IDs base para pruebas (asumiendo existencia de datos maestros o asignando IDs canónicos)
SET @usuario_patron = (SELECT id FROM usuario LIMIT 1);
SET @especie_id = (SELECT id FROM especie LIMIT 1);
SET @comuna_id = (SELECT id FROM comuna LIMIT 1);
SET @caleta_coquimbo = (SELECT id FROM caleta WHERE latitud IS NOT NULL LIMIT 1);
SET @humedad_id = (SELECT id FROM humedad_estado LIMIT 1);
SET @extraccion_id = (SELECT id FROM extraccion_tipo LIMIT 1);

-- Si no hay caleta con coordenadas, asegurar coordenadas para la primera caleta
UPDATE caleta SET latitud = -29.9533, longitud = -71.3395 WHERE id = @caleta_coquimbo;

-- 1. Declaración incoherente por distancia extrema (~400 km Santiago vs Coquimbo)
-- GPS en Santiago (-33.4489, -70.6693), Caleta en Coquimbo (-29.9533, -71.3395)
INSERT INTO declaracion_recolector (
    usuario_id, folio_origen, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    humedad_estado_id, desembarque, captura, latitud, longitud, precision_gps_m,
    gps_capturado_en, envio_offline, codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    @usuario_patron, 'DUMMY-25SEP-GEO-001', '2026-09-28', '2026-09-28', '10:00:00',
    'Recolector Dummy Geo', 'RPA-DUMMY-01', @caleta_coquimbo, @comuna_id, @especie_id, @extraccion_id,
    @humedad_id, 300.0, 300.0, -33.4489, -70.6693, 15.0,
    '2026-09-28 10:00:00', FALSE, 'DEST-01', 'Destinatario Dummy', @usuario_patron
);

-- 2. Declaración coherente (a 2.5 km de la caleta)
INSERT INTO declaracion_recolector (
    usuario_id, folio_origen, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    humedad_estado_id, desembarque, captura, latitud, longitud, precision_gps_m,
    gps_capturado_en, envio_offline, codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    @usuario_patron, 'DUMMY-25SEP-GEO-002', '2026-09-28', '2026-09-28', '11:00:00',
    'Recolector Dummy Geo', 'RPA-DUMMY-01', @caleta_coquimbo, @comuna_id, @especie_id, @extraccion_id,
    @humedad_id, 250.0, 250.0, -29.9650, -71.3500, 20.0,
    '2026-09-28 11:00:00', FALSE, 'DEST-01', 'Destinatario Dummy', @usuario_patron
);

-- 3. Declaración lejana pero con precisión GPS inaceptable (>500m -> 850m)
-- Debe reportarse en el dashboard pero NO generar marca
INSERT INTO declaracion_recolector (
    usuario_id, folio_origen, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    humedad_estado_id, desembarque, captura, latitud, longitud, precision_gps_m,
    gps_capturado_en, envio_offline, codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    @usuario_patron, 'DUMMY-25SEP-GEO-003', '2026-09-29', '2026-09-29', '09:00:00',
    'Recolector Dummy Geo', 'RPA-DUMMY-01', @caleta_coquimbo, @comuna_id, @especie_id, @extraccion_id,
    @humedad_id, 400.0, 400.0, -32.5000, -71.0000, 850.0,
    '2026-09-29 09:00:00', TRUE, 'DEST-01', 'Destinatario Dummy', @usuario_patron
);

-- 4 y 5. Declaraciones lejanas adicionales del mismo usuario para configurar PATRÓN SOSPECHOSO (>50% de >=3)
INSERT INTO declaracion_recolector (
    usuario_id, folio_origen, fecha_extraccion, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id,
    humedad_estado_id, desembarque, captura, latitud, longitud, precision_gps_m,
    gps_capturado_en, envio_offline, codigo_destinatario, nombre_destinatario, usuario_destinatario_id
) VALUES (
    @usuario_patron, 'DUMMY-25SEP-GEO-004', '2026-09-29', '2026-09-29', '14:00:00',
    'Recolector Dummy Geo', 'RPA-DUMMY-01', @caleta_coquimbo, @comuna_id, @especie_id, @extraccion_id,
    @humedad_id, 280.0, 280.0, -33.0245, -71.5518, 25.0,
    '2026-09-29 14:00:00', FALSE, 'DEST-01', 'Destinatario Dummy', @usuario_patron
), (
    @usuario_patron, 'DUMMY-25SEP-GEO-005', '2026-09-30', '2026-09-30', '08:30:00',
    'Recolector Dummy Geo', 'RPA-DUMMY-01', @caleta_coquimbo, @comuna_id, @especie_id, @extraccion_id,
    @humedad_id, 350.0, 350.0, -33.4500, -70.6600, 18.0,
    '2026-09-30 08:30:00', FALSE, 'DEST-01', 'Destinatario Dummy', @usuario_patron
);
