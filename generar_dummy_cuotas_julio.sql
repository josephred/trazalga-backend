-- =========================================================================
-- Script SQL: Inserción de declaraciones dummy de Recolector para Julio 2026
-- Objetivo: Dotar de volumen y gráficos visibles al widget "Control de Cuotas"
--           (Perfil: RECOLECTOR, Periodo: DIARIO) para CAROLA, CHASCA y CHICOREA DE MAR.
-- =========================================================================

-- Limpiar declaraciones sintéticas previas de prueba si existen
DELETE FROM declaracion_recolector 
WHERE folio_origen LIKE 'FOL-REC-CAROLA-%' 
   OR folio_origen LIKE 'FOL-REC-CHASCA-%' 
   OR folio_origen LIKE 'FOL-REC-CHICOREA-%';

-- Parámetros y claves foráneas dinámicas según el entorno
SET @v_usuario_id = COALESCE((SELECT id FROM usuario WHERE perfil_id IN (SELECT id FROM perfil WHERE nombre LIKE '%RECOLECTOR%') ORDER BY id ASC LIMIT 1), 11);
SET @v_usuario_dest_id = COALESCE((SELECT id FROM usuario WHERE perfil_id IN (SELECT id FROM perfil WHERE nombre LIKE '%COMER%') ORDER BY id ASC LIMIT 1), 14);
SET @v_caleta_id = COALESCE((SELECT id FROM caleta ORDER BY id ASC LIMIT 1), 1);
SET @v_comuna_id = COALESCE((SELECT id FROM comuna ORDER BY id ASC LIMIT 1), 30);
SET @v_ext_id = COALESCE((SELECT id FROM extraccion_tipo ORDER BY id ASC LIMIT 1), 1);
SET @v_comp_id = COALESCE((SELECT id FROM composicion ORDER BY id ASC LIMIT 1), 1);
SET @v_hum_id = COALESCE((SELECT id FROM humedad_estado ORDER BY id ASC LIMIT 1), 1);

-- -------------------------------------------------------------------------
-- 1. Declaraciones del 1 de Julio de 2026 (2026-07-01) - Apertura de Julio
-- -------------------------------------------------------------------------

-- CAROLA (especie 2, límite 40.500 kg): 24.300 kg (60.00%), Saldo: 16.200 kg
INSERT INTO declaracion_recolector (
    usuario_id, folio_origen, folio_desembarque_ro, fecha_extraccion, 
    periodo_extraccion_inicio, periodo_extraccion_fin, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, 
    extraccion_tipo_id, composicion_id, humedad_estado_id, desembarque, captura, 
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id, tasa_diaria_recoleccion
) VALUES (
    @v_usuario_id, 'FOL-REC-CAROLA-260701', 'RO-REC-CAROLA-260701', '2026-07-01',
    '2026-07-01', '2026-07-01', '2026-07-01', '10:00:00',
    'BERNARDINO ADOLFO SANTANDER FARIAS', 'RPA-118059', @v_caleta_id, @v_comuna_id, 2,
    @v_ext_id, @v_comp_id, @v_hum_id, 24300.00, 24300.00,
    '76719799', 'NICOLAS VALERIA ASTUDILLO Y COMPAÑIA LTDA', @v_usuario_dest_id, 24300.000
);

-- CHASCA (especie 3, límite 80.500 kg): 58.765 kg (73.00%), Saldo: 21.735 kg
INSERT INTO declaracion_recolector (
    usuario_id, folio_origen, folio_desembarque_ro, fecha_extraccion, 
    periodo_extraccion_inicio, periodo_extraccion_fin, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, 
    extraccion_tipo_id, composicion_id, humedad_estado_id, desembarque, captura, 
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id, tasa_diaria_recoleccion
) VALUES 
(
    @v_usuario_id, 'FOL-REC-CHASCA-260701-A', 'RO-REC-CHASCA-260701-A', '2026-07-01',
    '2026-07-01', '2026-07-01', '2026-07-01', '09:30:00',
    'BERNARDINO ADOLFO SANTANDER FARIAS', 'RPA-118059', @v_caleta_id, @v_comuna_id, 3,
    @v_ext_id, @v_comp_id, @v_hum_id, 30000.00, 30000.00,
    '76719799', 'NICOLAS VALERIA ASTUDILLO Y COMPAÑIA LTDA', @v_usuario_dest_id, 30000.000
),
(
    @v_usuario_id, 'FOL-REC-CHASCA-260701-B', 'RO-REC-CHASCA-260701-B', '2026-07-01',
    '2026-07-01', '2026-07-01', '2026-07-01', '11:15:00',
    'BERNARDINO ADOLFO SANTANDER FARIAS', 'RPA-118059', @v_caleta_id, @v_comuna_id, 3,
    @v_ext_id, @v_comp_id, @v_hum_id, 28765.00, 28765.00,
    '76719799', 'NICOLAS VALERIA ASTUDILLO Y COMPAÑIA LTDA', @v_usuario_dest_id, 28765.000
);

-- CHICOREA DE MAR (especie 4, límite 25.500 kg): 17.850 kg (70.00%), Saldo: 7.650 kg
INSERT INTO declaracion_recolector (
    usuario_id, folio_origen, folio_desembarque_ro, fecha_extraccion, 
    periodo_extraccion_inicio, periodo_extraccion_fin, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, 
    extraccion_tipo_id, composicion_id, humedad_estado_id, desembarque, captura, 
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id, tasa_diaria_recoleccion
) VALUES 
(
    @v_usuario_id, 'FOL-REC-CHICOREA-260701-A', 'RO-REC-CHICOREA-260701-A', '2026-07-01',
    '2026-07-01', '2026-07-01', '2026-07-01', '08:45:00',
    'BERNARDINO ADOLFO SANTANDER FARIAS', 'RPA-118059', @v_caleta_id, @v_comuna_id, 4,
    @v_ext_id, @v_comp_id, @v_hum_id, 10000.00, 10000.00,
    '76719799', 'NICOLAS VALERIA ASTUDILLO Y COMPAÑIA LTDA', @v_usuario_dest_id, 10000.000
),
(
    @v_usuario_id, 'FOL-REC-CHICOREA-260701-B', 'RO-REC-CHICOREA-260701-B', '2026-07-01',
    '2026-07-01', '2026-07-01', '2026-07-01', '10:30:00',
    'BERNARDINO ADOLFO SANTANDER FARIAS', 'RPA-118059', @v_caleta_id, @v_comuna_id, 4,
    @v_ext_id, @v_comp_id, @v_hum_id, 7850.00, 7850.00,
    '76719799', 'NICOLAS VALERIA ASTUDILLO Y COMPAÑIA LTDA', @v_usuario_dest_id, 7850.000
);

-- -------------------------------------------------------------------------
-- 2. Declaraciones para el 15 de Julio de 2026 (2026-07-15)
-- -------------------------------------------------------------------------
INSERT INTO declaracion_recolector (
    usuario_id, folio_origen, folio_desembarque_ro, fecha_extraccion, 
    periodo_extraccion_inicio, periodo_extraccion_fin, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, 
    extraccion_tipo_id, composicion_id, humedad_estado_id, desembarque, captura, 
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id, tasa_diaria_recoleccion
) VALUES 
(
    @v_usuario_id, 'FOL-REC-CAROLA-260715', 'RO-REC-CAROLA-260715', '2026-07-15',
    '2026-07-15', '2026-07-15', '2026-07-15', '11:00:00',
    'BERNARDINO ADOLFO SANTANDER FARIAS', 'RPA-118059', @v_caleta_id, @v_comuna_id, 2,
    @v_ext_id, @v_comp_id, @v_hum_id, 20250.00, 20250.00,
    '76719799', 'NICOLAS VALERIA ASTUDILLO Y COMPAÑIA LTDA', @v_usuario_dest_id, 20250.000
),
(
    @v_usuario_id, 'FOL-REC-CHASCA-260715', 'RO-REC-CHASCA-260715', '2026-07-15',
    '2026-07-15', '2026-07-15', '2026-07-15', '12:00:00',
    'BERNARDINO ADOLFO SANTANDER FARIAS', 'RPA-118059', @v_caleta_id, @v_comuna_id, 3,
    @v_ext_id, @v_comp_id, @v_hum_id, 52325.00, 52325.00,
    '76719799', 'NICOLAS VALERIA ASTUDILLO Y COMPAÑIA LTDA', @v_usuario_dest_id, 52325.000
),
(
    @v_usuario_id, 'FOL-REC-CHICOREA-260715', 'RO-REC-CHICOREA-260715', '2026-07-15',
    '2026-07-15', '2026-07-15', '2026-07-15', '09:00:00',
    'BERNARDINO ADOLFO SANTANDER FARIAS', 'RPA-118059', @v_caleta_id, @v_comuna_id, 4,
    @v_ext_id, @v_comp_id, @v_hum_id, 16575.00, 16575.00,
    '76719799', 'NICOLAS VALERIA ASTUDILLO Y COMPAÑIA LTDA', @v_usuario_dest_id, 16575.000
);

-- -------------------------------------------------------------------------
-- 3. Declaraciones para el 31 de Julio de 2026 (2026-07-31)
-- -------------------------------------------------------------------------
INSERT INTO declaracion_recolector (
    usuario_id, folio_origen, folio_desembarque_ro, fecha_extraccion, 
    periodo_extraccion_inicio, periodo_extraccion_fin, fecha_declaracion, hora,
    nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, 
    extraccion_tipo_id, composicion_id, humedad_estado_id, desembarque, captura, 
    codigo_destinatario, nombre_destinatario, usuario_destinatario_id, tasa_diaria_recoleccion
) VALUES 
(
    @v_usuario_id, 'FOL-REC-CAROLA-260731', 'RO-REC-CAROLA-260731', '2026-07-31',
    '2026-07-31', '2026-07-31', '2026-07-31', '10:00:00',
    'BERNARDINO ADOLFO SANTANDER FARIAS', 'RPA-118059', @v_caleta_id, @v_comuna_id, 2,
    @v_ext_id, @v_comp_id, @v_hum_id, 25000.00, 25000.00,
    '76719799', 'NICOLAS VALERIA ASTUDILLO Y COMPAÑIA LTDA', @v_usuario_dest_id, 25000.000
),
(
    @v_usuario_id, 'FOL-REC-CHASCA-260731', 'RO-REC-CHASCA-260731', '2026-07-31',
    '2026-07-31', '2026-07-31', '2026-07-31', '11:30:00',
    'BERNARDINO ADOLFO SANTANDER FARIAS', 'RPA-118059', @v_caleta_id, @v_comuna_id, 3,
    @v_ext_id, @v_comp_id, @v_hum_id, 60000.00, 60000.00,
    '76719799', 'NICOLAS VALERIA ASTUDILLO Y COMPAÑIA LTDA', @v_usuario_dest_id, 60000.000
),
(
    @v_usuario_id, 'FOL-REC-CHICOREA-260731', 'RO-REC-CHICOREA-260731', '2026-07-31',
    '2026-07-31', '2026-07-31', '2026-07-31', '08:30:00',
    'BERNARDINO ADOLFO SANTANDER FARIAS', 'RPA-118059', @v_caleta_id, @v_comuna_id, 4,
    @v_ext_id, @v_comp_id, @v_hum_id, 18000.00, 18000.00,
    '76719799', 'NICOLAS VALERIA ASTUDILLO Y COMPAÑIA LTDA', @v_usuario_dest_id, 18000.000
);
