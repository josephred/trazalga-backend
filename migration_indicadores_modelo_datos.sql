-- ========================================================================
-- Trazalga — Migración Fase 0: Modelo de Datos para Indicadores Parametrizables
-- Base de datos: trazalga
-- Fecha: 2026-09-07
-- ========================================================================

USE trazalga;

-- ------------------------------------------------------------------------
-- 0.1 PROVINCIA COMO ENTIDAD
-- ------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS provincia (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    codigo VARCHAR(10) NULL,
    region_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_prov_region (nombre, region_id),
    CONSTRAINT fk_prov_region FOREIGN KEY (region_id) REFERENCES region(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Agregar columna provincia_id a comuna
SET @col_comuna_prov = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'trazalga' AND TABLE_NAME = 'comuna' AND COLUMN_NAME = 'provincia_id');
SET @sql_comuna_prov = IF(@col_comuna_prov = 0,
    'ALTER TABLE comuna ADD COLUMN provincia_id BIGINT NULL, ADD CONSTRAINT fk_comuna_provincia FOREIGN KEY (provincia_id) REFERENCES provincia(id);',
    'SELECT "provincia_id ya existe en comuna";');
PREPARE stmt FROM @sql_comuna_prov;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Carga inicial de provincias (Antofagasta, Atacama, Coquimbo)
INSERT IGNORE INTO provincia (nombre, codigo, region_id) VALUES
-- Región 2: Antofagasta
('Antofagasta', '021', 2),
('El Loa', '022', 2),
('Tocopilla', '023', 2),
-- Región 3: Atacama
('Copiapó', '031', 3),
('Chañaral', '032', 3),
('Huasco', '033', 3),
-- Región 4: Coquimbo
('Elqui', '041', 4),
('Limarí', '042', 4),
('Choapa', '043', 4);

-- Asignación de provincia_id a comunas existentes
-- Coquimbo (4): Elqui
UPDATE comuna c JOIN provincia p ON p.nombre = 'Elqui' AND p.region_id = 4 
SET c.provincia_id = p.id WHERE c.region_id = 4 AND c.nombre IN ('La Serena', 'Coquimbo', 'Andacollo', 'La Higuera', 'Paiguano', 'Vicuña');

-- Coquimbo (4): Limarí
UPDATE comuna c JOIN provincia p ON p.nombre = 'Limarí' AND p.region_id = 4 
SET c.provincia_id = p.id WHERE c.region_id = 4 AND c.nombre IN ('Ovalle', 'Combarbalá', 'Monte Patria', 'Punitaqui', 'Río Hurtado');

-- Coquimbo (4): Choapa
UPDATE comuna c JOIN provincia p ON p.nombre = 'Choapa' AND p.region_id = 4 
SET c.provincia_id = p.id WHERE c.region_id = 4 AND c.nombre IN ('Illapel', 'Canela', 'Los Vilos', 'Salamanca');

-- Atacama (3): Copiapó
UPDATE comuna c JOIN provincia p ON p.nombre = 'Copiapó' AND p.region_id = 3 
SET c.provincia_id = p.id WHERE c.region_id = 3 AND c.nombre IN ('Copiapó', 'Caldera', 'Tierra Amarilla');

-- Atacama (3): Chañaral
UPDATE comuna c JOIN provincia p ON p.nombre = 'Chañaral' AND p.region_id = 3 
SET c.provincia_id = p.id WHERE c.region_id = 3 AND c.nombre IN ('Chañaral', 'Diego de Almagro');

-- Atacama (3): Huasco
UPDATE comuna c JOIN provincia p ON p.nombre = 'Huasco' AND p.region_id = 3 
SET c.provincia_id = p.id WHERE c.region_id = 3 AND c.nombre IN ('Huasco', 'Freirina', 'Vallenar', 'Alto del Carmen');

-- Antofagasta (2): Antofagasta
UPDATE comuna c JOIN provincia p ON p.nombre = 'Antofagasta' AND p.region_id = 2 
SET c.provincia_id = p.id WHERE c.region_id = 2 AND c.nombre IN ('Antofagasta', 'Mejillones', 'Sierra Gorda', 'Taltal');

-- Antofagasta (2): Tocopilla
UPDATE comuna c JOIN provincia p ON p.nombre = 'Tocopilla' AND p.region_id = 2 
SET c.provincia_id = p.id WHERE c.region_id = 2 AND c.nombre IN ('Tocopilla', 'María Elena');

-- ------------------------------------------------------------------------
-- 0.2 MÉTODO DE EXTRACCIÓN EN TODAS LAS DECLARACIONES
-- ------------------------------------------------------------------------
-- declaracion_armador
SET @col_da_ext = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'trazalga' AND TABLE_NAME = 'declaracion_armador' AND COLUMN_NAME = 'extraccion_tipo_id');
SET @sql_da_ext = IF(@col_da_ext = 0,
    'ALTER TABLE declaracion_armador ADD COLUMN extraccion_tipo_id BIGINT NULL, ADD CONSTRAINT fk_da_ext_tipo FOREIGN KEY (extraccion_tipo_id) REFERENCES extraccion_tipo(id);',
    'SELECT "extraccion_tipo_id ya existe en declaracion_armador";');
PREPARE stmt FROM @sql_da_ext;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- declaracion_area
SET @col_dar_ext = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'trazalga' AND TABLE_NAME = 'declaracion_area' AND COLUMN_NAME = 'extraccion_tipo_id');
SET @sql_dar_ext = IF(@col_dar_ext = 0,
    'ALTER TABLE declaracion_area ADD COLUMN extraccion_tipo_id BIGINT NULL, ADD CONSTRAINT fk_dar_ext_tipo FOREIGN KEY (extraccion_tipo_id) REFERENCES extraccion_tipo(id);',
    'SELECT "extraccion_tipo_id ya existe en declaracion_area";');
PREPARE stmt FROM @sql_dar_ext;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------------------
-- 0.3 FACTORES DE CONVERSIÓN CON VIGENCIA
-- ------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS factor_conversion (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    especie_id BIGINT NOT NULL,
    humedad_estado_id BIGINT NOT NULL,
    factor DECIMAL(8,4) NOT NULL,
    vigencia_inicio DATE NOT NULL,
    vigencia_fin DATE NULL,
    resolucion VARCHAR(100) NULL,
    descripcion VARCHAR(255) NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_fc_esp_hum_vig (especie_id, humedad_estado_id, vigencia_inicio),
    CONSTRAINT fk_fc_esp FOREIGN KEY (especie_id) REFERENCES especie(id),
    CONSTRAINT fk_fc_hum FOREIGN KEY (humedad_estado_id) REFERENCES humedad_estado(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Poblado inicial de factores de conversión
-- Factor 1.0000 para estado Húmedo (id = 1) en todas las especies
INSERT IGNORE INTO factor_conversion (especie_id, humedad_estado_id, factor, vigencia_inicio, resolucion, descripcion, activo)
SELECT e.id, 1, 1.0000, '2024-01-01', 'Estándar Húmedo', 'Factor base para recurso húmedo recién extraído', TRUE
FROM especie e;

-- Factor 3.5800 para estado Seco (id = 4) en todas las especies de huiro
INSERT IGNORE INTO factor_conversion (especie_id, humedad_estado_id, factor, vigencia_inicio, resolucion, descripcion, activo)
SELECT e.id, 4, 3.5800, '2024-01-01', 'Res. Ex. Subpesca', 'Factor de conversión biológica seco a húmedo', TRUE
FROM especie e;

-- ------------------------------------------------------------------------
-- 0.4 CONGELAMIENTO DEL FACTOR EN DECLARACIONES
-- ------------------------------------------------------------------------
-- declaracion_recolector
SET @col_dr_fac = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'trazalga' AND TABLE_NAME = 'declaracion_recolector' AND COLUMN_NAME = 'factor_aplicado');
SET @sql_dr_fac = IF(@col_dr_fac = 0,
    'ALTER TABLE declaracion_recolector ADD COLUMN factor_aplicado DECIMAL(8,4) NULL, ADD COLUMN factor_conversion_id BIGINT NULL, ADD CONSTRAINT fk_dr_fc FOREIGN KEY (factor_conversion_id) REFERENCES factor_conversion(id);',
    'SELECT "factor_aplicado ya existe en declaracion_recolector";');
PREPARE stmt FROM @sql_dr_fac;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- declaracion_armador
SET @col_da_fac = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'trazalga' AND TABLE_NAME = 'declaracion_armador' AND COLUMN_NAME = 'factor_aplicado');
SET @sql_da_fac = IF(@col_da_fac = 0,
    'ALTER TABLE declaracion_armador ADD COLUMN factor_aplicado DECIMAL(8,4) NULL, ADD COLUMN factor_conversion_id BIGINT NULL, ADD CONSTRAINT fk_da_fc FOREIGN KEY (factor_conversion_id) REFERENCES factor_conversion(id);',
    'SELECT "factor_aplicado ya existe en declaracion_armador";');
PREPARE stmt FROM @sql_da_fac;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- declaracion_area
SET @col_dar_fac = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'trazalga' AND TABLE_NAME = 'declaracion_area' AND COLUMN_NAME = 'factor_aplicado');
SET @sql_dar_fac = IF(@col_dar_fac = 0,
    'ALTER TABLE declaracion_area ADD COLUMN factor_aplicado DECIMAL(8,4) NULL, ADD COLUMN factor_conversion_id BIGINT NULL, ADD CONSTRAINT fk_dar_fc FOREIGN KEY (factor_conversion_id) REFERENCES factor_conversion(id);',
    'SELECT "factor_aplicado ya existe en declaracion_area";');
PREPARE stmt FROM @sql_dar_fac;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------------------
-- 0.5 MARCAS DE FISCALIZACIÓN
-- ------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS declaracion_marca (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    declaracion_tipo VARCHAR(30) NOT NULL,   -- RECOLECTOR | ARMADOR | AREA
    declaracion_id BIGINT NOT NULL,
    marca VARCHAR(40) NOT NULL,              -- EN_VEDA | LED_EXCEDIDO | CUOTA_EXCEDIDA | POSTERIOR_CIERRE | DESEMBARQUE_ATIPICO
    detalle VARCHAR(500) NULL,
    regla_id BIGINT NULL,
    resuelta BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY ix_dm_decl (declaracion_tipo, declaracion_id),
    KEY ix_dm_marca (marca, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------------------
-- 0.6 CONFIGURACIÓN: CATEGORÍA Y PAYLOADS
-- ------------------------------------------------------------------------
SET @col_cg_cat = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'trazalga' AND TABLE_NAME = 'configuracion_general' AND COLUMN_NAME = 'categoria');
SET @sql_cg_cat = IF(@col_cg_cat = 0,
    'ALTER TABLE configuracion_general ADD COLUMN categoria VARCHAR(50) NULL, MODIFY COLUMN valor VARCHAR(1000) NOT NULL;',
    'ALTER TABLE configuracion_general MODIFY COLUMN valor VARCHAR(1000) NOT NULL;');
PREPARE stmt FROM @sql_cg_cat;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_ca_param = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'trazalga' AND TABLE_NAME = 'configuracion_alerta' AND COLUMN_NAME = 'parametros_json');
SET @sql_ca_param = IF(@col_ca_param = 0,
    'ALTER TABLE configuracion_alerta ADD COLUMN parametros_json TEXT NULL;',
    'SELECT "parametros_json ya existe en configuracion_alerta";');
PREPARE stmt FROM @sql_ca_param;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------------------
-- 0.7 CUOTAS DE EXTRACCIÓN AMPLIADAS
-- ------------------------------------------------------------------------
SET @col_ce_prov = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'trazalga' AND TABLE_NAME = 'cuota_extraccion' AND COLUMN_NAME = 'provincia_id');
SET @sql_ce_prov = IF(@col_ce_prov = 0,
    'ALTER TABLE cuota_extraccion
        ADD COLUMN provincia_id BIGINT NULL,
        ADD COLUMN comuna_id BIGINT NULL,
        ADD COLUMN extraccion_tipo_id BIGINT NULL,
        ADD COLUMN humedad_estado_id BIGINT NULL,
        ADD COLUMN nivel_agregacion VARCHAR(20) NOT NULL DEFAULT "COMUNA",
        ADD COLUMN metrica VARCHAR(20) NOT NULL DEFAULT "CAPTURA",
        ADD COLUMN es_plantilla BOOLEAN NOT NULL DEFAULT FALSE,
        ADD COLUMN fecha_inicio DATE NULL,
        ADD COLUMN fecha_fin DATE NULL,
        ADD COLUMN resolucion VARCHAR(100) NULL,
        ADD COLUMN estado VARCHAR(20) NOT NULL DEFAULT "ABIERTA",
        ADD COLUMN fecha_cierre DATE NULL,
        ADD CONSTRAINT fk_cuota_provincia FOREIGN KEY (provincia_id) REFERENCES provincia(id),
        ADD CONSTRAINT fk_cuota_comuna FOREIGN KEY (comuna_id) REFERENCES comuna(id),
        ADD CONSTRAINT fk_cuota_ext_tipo FOREIGN KEY (extraccion_tipo_id) REFERENCES extraccion_tipo(id),
        ADD CONSTRAINT fk_cuota_humedad FOREIGN KEY (humedad_estado_id) REFERENCES humedad_estado(id);',
    'SELECT "Campos de cuota_extraccion ya existen";');
PREPARE stmt FROM @sql_ce_prov;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------------------
-- 0.8 REGLAS DE LÍMITE DE EXTRACCIÓN DIARIO (LED)
-- ------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS limite_extraccion_diario_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre_regla VARCHAR(100) NOT NULL,
    especie_id BIGINT NULL,
    extraccion_tipo_id BIGINT NULL,
    region_id BIGINT NULL,
    perfil_aplicable VARCHAR(50) NOT NULL DEFAULT 'ARMADOR',
    unidad_agregacion VARCHAR(20) NOT NULL DEFAULT 'EMBARCACION',
    metrica VARCHAR(20) NOT NULL DEFAULT 'DESEMBARQUE',
    limite_kg DECIMAL(10,2) NOT NULL DEFAULT 2000.00,
    margen_tolerancia_pct DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    modo_accion VARCHAR(50) NOT NULL DEFAULT 'SOLO_ALERTA',
    vigencia_inicio DATE NULL,
    vigencia_fin DATE NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_led_esp FOREIGN KEY (especie_id) REFERENCES especie(id),
    CONSTRAINT fk_led_ext FOREIGN KEY (extraccion_tipo_id) REFERENCES extraccion_tipo(id),
    CONSTRAINT fk_led_reg FOREIGN KEY (region_id) REFERENCES region(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Regla inicial por defecto para Huiro Palo Barreteado
INSERT IGNORE INTO limite_extraccion_diario_config 
(nombre_regla, especie_id, extraccion_tipo_id, region_id, perfil_aplicable, unidad_agregacion, metrica, limite_kg, margen_tolerancia_pct, modo_accion, activo)
SELECT 'LED Oficial Huiro Palo Barreteado', e.id, 2, NULL, 'ARMADOR', 'EMBARCACION', 'DESEMBARQUE', 2000.00, 0.00, 'SOLO_ALERTA', TRUE
FROM especie e WHERE UPPER(e.nombre) LIKE '%PALO%' LIMIT 1;

-- ------------------------------------------------------------------------
-- 0.9 VEDAS CON RECURRENCIA ANUAL
-- ------------------------------------------------------------------------
SET @col_ve_ext = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'trazalga' AND TABLE_NAME = 'veda_especie' AND COLUMN_NAME = 'extraccion_tipo_id');
SET @sql_ve_ext = IF(@col_ve_ext = 0,
    'ALTER TABLE veda_especie
        ADD COLUMN extraccion_tipo_id BIGINT NULL,
        ADD COLUMN recurrencia_anual BOOLEAN NOT NULL DEFAULT FALSE,
        ADD COLUMN meses_veda VARCHAR(40) NULL,
        ADD COLUMN activo BOOLEAN NOT NULL DEFAULT TRUE,
        MODIFY COLUMN fecha_inicio DATE NULL,
        MODIFY COLUMN fecha_fin DATE NULL,
        ADD CONSTRAINT fk_veda_ext_tipo FOREIGN KEY (extraccion_tipo_id) REFERENCES extraccion_tipo(id);',
    'SELECT "Campos de veda_especie ya existen";');
PREPARE stmt FROM @sql_ve_ext;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Configurar veda inicial de Huiro Negro Barreteado (9 meses veda, libre en 3, 9, 12)
INSERT IGNORE INTO veda_especie (especie_id, region_id, extraccion_tipo_id, recurrencia_anual, meses_veda, resolucion, observacion, activo)
SELECT e.id, NULL, 2, TRUE, '1,2,4,5,6,7,8,10,11', 'Res. Ex. Huiro Negro', 'Habilitado solo en marzo, septiembre y diciembre para barreteado', TRUE
FROM especie e WHERE UPPER(e.nombre) LIKE '%NEGRO%' LIMIT 1;

-- ------------------------------------------------------------------------
-- 0.10 AMERB
-- ------------------------------------------------------------------------
SET @col_am_reg = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'trazalga' AND TABLE_NAME = 'amerb' AND COLUMN_NAME = 'region_id');
SET @sql_am_reg = IF(@col_am_reg = 0,
    'ALTER TABLE amerb ADD COLUMN region_id BIGINT NULL, ADD COLUMN comuna_id BIGINT NULL,
     ADD CONSTRAINT fk_amerb_region FOREIGN KEY (region_id) REFERENCES region(id),
     ADD CONSTRAINT fk_amerb_comuna FOREIGN KEY (comuna_id) REFERENCES comuna(id);',
    'SELECT "region_id ya existe en amerb";');
PREPARE stmt FROM @sql_am_reg;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS amerb_especie_habilitada (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    amerb_id BIGINT NOT NULL,
    especie_id BIGINT NOT NULL,
    resolucion VARCHAR(100) NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_amerb_especie (amerb_id, especie_id),
    CONSTRAINT fk_aeh_amerb FOREIGN KEY (amerb_id) REFERENCES amerb(id),
    CONSTRAINT fk_aeh_especie FOREIGN KEY (especie_id) REFERENCES especie(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------------------
-- 0.11 AUDITORÍA DE PARÁMETROS
-- ------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS configuracion_auditoria (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entidad VARCHAR(50) NOT NULL,        -- CONFIGURACION_GENERAL | FACTOR_CONVERSION | CUOTA | VEDA | LED
    entidad_id VARCHAR(100) NULL,
    campo VARCHAR(100) NULL,
    valor_anterior VARCHAR(1000) NULL,
    valor_nuevo VARCHAR(1000) NULL,
    usuario_id BIGINT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY ix_ca_entidad (entidad, entidad_id, created_at),
    CONSTRAINT fk_ca_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------------------
-- 0.12 ÍNDICES DE RENDIMIENTO PARA EL MOTOR DE REGLAS
-- ------------------------------------------------------------------------
SET @idx_dr = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = 'trazalga' AND TABLE_NAME = 'declaracion_recolector' AND INDEX_NAME = 'ix_dr_usu_esp_fecha');
SET @sql_idx_dr = IF(@idx_dr = 0, 'CREATE INDEX ix_dr_usu_esp_fecha ON declaracion_recolector (usuario_id, especie_id, fecha_declaracion);', 'SELECT 1;');
PREPARE stmt FROM @sql_idx_dr; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_da_emb = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = 'trazalga' AND TABLE_NAME = 'declaracion_armador' AND INDEX_NAME = 'ix_da_emb_esp_fecha');
SET @sql_idx_da_emb = IF(@idx_da_emb = 0, 'CREATE INDEX ix_da_emb_esp_fecha ON declaracion_armador (embarcacion_id, especie_id, fecha_declaracion);', 'SELECT 1;');
PREPARE stmt FROM @sql_idx_da_emb; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_da_usu = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = 'trazalga' AND TABLE_NAME = 'declaracion_armador' AND INDEX_NAME = 'ix_da_usu_esp_fecha');
SET @sql_idx_da_usu = IF(@idx_da_usu = 0, 'CREATE INDEX ix_da_usu_esp_fecha ON declaracion_armador (usuario_id, especie_id, fecha_declaracion);', 'SELECT 1;');
PREPARE stmt FROM @sql_idx_da_usu; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_dr_fol = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = 'trazalga' AND TABLE_NAME = 'declaracion_recolector' AND INDEX_NAME = 'ix_dr_folio');
SET @sql_idx_dr_fol = IF(@idx_dr_fol = 0, 'CREATE INDEX ix_dr_folio ON declaracion_recolector (folio_origen);', 'SELECT 1;');
PREPARE stmt FROM @sql_idx_dr_fol; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_da_fol = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = 'trazalga' AND TABLE_NAME = 'declaracion_armador' AND INDEX_NAME = 'ix_da_folio');
SET @sql_idx_da_fol = IF(@idx_da_fol = 0, 'CREATE INDEX ix_da_folio ON declaracion_armador (folio_origen);', 'SELECT 1;');
PREPARE stmt FROM @sql_idx_da_fol; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_dar_fol = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = 'trazalga' AND TABLE_NAME = 'declaracion_area' AND INDEX_NAME = 'ix_dar_folio');
SET @sql_idx_dar_fol = IF(@idx_dar_fol = 0, 'CREATE INDEX ix_dar_folio ON declaracion_area (folio_origen);', 'SELECT 1;');
PREPARE stmt FROM @sql_idx_dar_fol; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_dc_fol = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = 'trazalga' AND TABLE_NAME = 'declaracion_comercializador' AND INDEX_NAME = 'ix_dc_folio');
SET @sql_idx_dc_fol = IF(@idx_dc_fol = 0, 'CREATE INDEX ix_dc_folio ON declaracion_comercializador (folio_origen);', 'SELECT 1;');
PREPARE stmt FROM @sql_idx_dc_fol; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_dpa_fol = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = 'trazalga' AND TABLE_NAME = 'declaracion_planta_abastecimiento' AND INDEX_NAME = 'ix_dpa_folio');
SET @sql_idx_dpa_fol = IF(@idx_dpa_fol = 0, 'CREATE INDEX ix_dpa_folio ON declaracion_planta_abastecimiento (folio_origen);', 'SELECT 1;');
PREPARE stmt FROM @sql_idx_dpa_fol; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Índices para agrupaciones y filtros de caleta (T7)
SET @idx_dr_cal = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = 'trazalga' AND TABLE_NAME = 'declaracion_recolector' AND INDEX_NAME = 'ix_dr_caleta_fecha');
SET @sql_idx_dr_cal = IF(@idx_dr_cal = 0, 'CREATE INDEX ix_dr_caleta_fecha ON declaracion_recolector (caleta_id, fecha_declaracion);', 'SELECT 1;');
PREPARE stmt FROM @sql_idx_dr_cal; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_da_cal = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = 'trazalga' AND TABLE_NAME = 'declaracion_armador' AND INDEX_NAME = 'ix_da_caleta_fecha');
SET @sql_idx_da_cal = IF(@idx_da_cal = 0, 'CREATE INDEX ix_da_caleta_fecha ON declaracion_armador (caleta_id, fecha_declaracion);', 'SELECT 1;');
PREPARE stmt FROM @sql_idx_da_cal; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_dar_cal = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = 'trazalga' AND TABLE_NAME = 'declaracion_area' AND INDEX_NAME = 'ix_dar_caleta_fecha');
SET @sql_idx_dar_cal = IF(@idx_dar_cal = 0, 'CREATE INDEX ix_dar_caleta_fecha ON declaracion_area (caleta_id, fecha_declaracion);', 'SELECT 1;');
PREPARE stmt FROM @sql_idx_dar_cal; EXECUTE stmt; DEALLOCATE PREPARE stmt;

