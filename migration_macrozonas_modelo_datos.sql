-- ========================================================================
-- Trazalga — Migración Etapa B: Modelo de Datos para Macrozonas y Cuota Nacional
-- Base de datos: trazalga
-- Fecha: 2026-09-09
-- ========================================================================

USE trazalga;

-- ------------------------------------------------------------------------
-- B1.1 TABLA MACROZONA
-- ------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS macrozona (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    codigo VARCHAR(20) NULL,
    descripcion VARCHAR(255) NULL,
    es_nacional BOOLEAN NOT NULL DEFAULT FALSE,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_macrozona_nombre (nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------------------
-- B1.2 TABLA PUENTE MACROZONA_REGION (N:M CON VIGENCIA TEMPORAL)
-- ------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS macrozona_region (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    macrozona_id BIGINT NOT NULL,
    region_id BIGINT NOT NULL,
    vigencia_inicio DATE NULL,
    vigencia_fin DATE NULL,
    UNIQUE KEY uq_mz_region (macrozona_id, region_id, vigencia_inicio),
    KEY ix_mzr_region (region_id, macrozona_id),
    CONSTRAINT fk_mzr_macrozona FOREIGN KEY (macrozona_id) REFERENCES macrozona(id) ON DELETE CASCADE,
    CONSTRAINT fk_mzr_region FOREIGN KEY (region_id) REFERENCES region(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------------------
-- B1.3 COLUMNA CODIGO EN REGION
-- ------------------------------------------------------------------------
SET @col_region_cod = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'region' AND COLUMN_NAME = 'codigo');
SET @sql_region_cod = IF(@col_region_cod = 0,
    'ALTER TABLE region ADD COLUMN codigo VARCHAR(20) NULL;',
    'SELECT "codigo ya existe en region";');
PREPARE stmt_reg FROM @sql_region_cod;
EXECUTE stmt_reg;
DEALLOCATE PREPARE stmt_reg;

-- ------------------------------------------------------------------------
-- B1.4 MACROZONA_ID EN CUOTA_EXTRACCION
-- ------------------------------------------------------------------------
SET @col_cuota_mz = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cuota_extraccion' AND COLUMN_NAME = 'macrozona_id');
SET @sql_cuota_mz = IF(@col_cuota_mz = 0,
    'ALTER TABLE cuota_extraccion ADD COLUMN macrozona_id BIGINT NULL, ADD CONSTRAINT fk_cuota_macrozona FOREIGN KEY (macrozona_id) REFERENCES macrozona(id);',
    'SELECT "macrozona_id ya existe en cuota_extraccion";');
PREPARE stmt_cuota FROM @sql_cuota_mz;
EXECUTE stmt_cuota;
DEALLOCATE PREPARE stmt_cuota;

-- ------------------------------------------------------------------------
-- B1.5 MACROZONA_ID EN VEDA_ESPECIE
-- ------------------------------------------------------------------------
SET @col_veda_mz = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'veda_especie' AND COLUMN_NAME = 'macrozona_id');
SET @sql_veda_mz = IF(@col_veda_mz = 0,
    'ALTER TABLE veda_especie ADD COLUMN macrozona_id BIGINT NULL, ADD CONSTRAINT fk_veda_macrozona FOREIGN KEY (macrozona_id) REFERENCES macrozona(id);',
    'SELECT "macrozona_id ya existe en veda_especie";');
PREPARE stmt_veda FROM @sql_veda_mz;
EXECUTE stmt_veda;
DEALLOCATE PREPARE stmt_veda;

-- ------------------------------------------------------------------------
-- B1.6 MACROZONA_ID EN LIMITE_EXTRACCION_DIARIO_CONFIG
-- ------------------------------------------------------------------------
SET @col_led_mz = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'limite_extraccion_diario_config' AND COLUMN_NAME = 'macrozona_id');
SET @sql_led_mz = IF(@col_led_mz = 0,
    'ALTER TABLE limite_extraccion_diario_config ADD COLUMN macrozona_id BIGINT NULL, ADD CONSTRAINT fk_led_macrozona FOREIGN KEY (macrozona_id) REFERENCES macrozona(id);',
    'SELECT "macrozona_id ya existe en limite_extraccion_diario_config";');
PREPARE stmt_led FROM @sql_led_mz;
EXECUTE stmt_led;
DEALLOCATE PREPARE stmt_led;

-- ------------------------------------------------------------------------
-- B1.7 POBLAMIENTO INICIAL DE MACROZONAS CANÓNICAS
-- ------------------------------------------------------------------------
INSERT IGNORE INTO macrozona (nombre, codigo, descripcion, es_nacional, activo) VALUES
('Macrozona Norte', 'MZ-NORTE', 'Arica y Parinacota a Coquimbo (Regiones XV, I, II, III, IV)', FALSE, TRUE),
('Macrozona Centro-Sur', 'MZ-CENTROSUR', 'Valparaíso a Biobío (Regiones V, RM, VI, VII, XVI, VIII)', FALSE, TRUE),
('Macrozona Sur-Austral', 'MZ-SURASTRAL', 'La Araucanía a Magallanes (Regiones IX, XIV, X, XI, XII)', FALSE, TRUE),
('Macrozona Nacional', 'MZ-NACIONAL', 'Alcance Nacional — Todas las regiones de Chile', TRUE, TRUE);

-- Asignación inicial de regiones a macrozonas según ID/Nombre existentes
-- 1. Macrozona Norte (XV, I, II, III, IV)
INSERT IGNORE INTO macrozona_region (macrozona_id, region_id, vigencia_inicio)
SELECT m.id, r.id, '2020-01-01'
FROM macrozona m
JOIN region r ON r.nombre LIKE '%Arica%' OR r.nombre LIKE '%Tarapac%' OR r.nombre LIKE '%Antofagasta%' OR r.nombre LIKE '%Atacama%' OR r.nombre LIKE '%Coquimbo%'
WHERE m.codigo = 'MZ-NORTE';

-- 2. Macrozona Centro-Sur (V, RM, VI, VII, XVI, VIII)
INSERT IGNORE INTO macrozona_region (macrozona_id, region_id, vigencia_inicio)
SELECT m.id, r.id, '2020-01-01'
FROM macrozona m
JOIN region r ON r.nombre LIKE '%Valpara%' OR r.nombre LIKE '%Metropolitana%' OR r.nombre LIKE '%O\'Higgins%' OR r.nombre LIKE '%Maule%' OR r.nombre LIKE '%uble%' OR r.nombre LIKE '%Biob%'
WHERE m.codigo = 'MZ-CENTROSUR';

-- 3. Macrozona Sur-Austral (IX, XIV, X, XI, XII)
INSERT IGNORE INTO macrozona_region (macrozona_id, region_id, vigencia_inicio)
SELECT m.id, r.id, '2020-01-01'
FROM macrozona m
JOIN region r ON r.nombre LIKE '%Araucan%' OR r.nombre LIKE '%Ríos%' OR r.nombre LIKE '%Lagos%' OR r.nombre LIKE '%Ays%' OR r.nombre LIKE '%Magallanes%'
WHERE m.codigo = 'MZ-SURASTRAL';

-- 4. Macrozona Nacional (Todas las 16 regiones)
INSERT IGNORE INTO macrozona_region (macrozona_id, region_id, vigencia_inicio)
SELECT m.id, r.id, '2020-01-01'
FROM macrozona m
CROSS JOIN region r
WHERE m.codigo = 'MZ-NACIONAL';
