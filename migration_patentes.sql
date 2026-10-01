-- =========================================================================
-- MIGRACIÓN: migration_patentes.sql
-- TrazAlga - Refinamiento 25-sep / T10.1 Normalizar e Indexar Patentes
-- 1. Normalización de placas patentes (camión y carro) a mayúsculas sin espacios ni guiones
-- 2. Creación de índices compuestos para optimización de búsqueda por patente y fecha
-- 3. Creación de tabla de auditoría para registro de consultas públicas ciudadanas
-- Idempotente conforme al estándar de ingeniería Trazalga
-- =========================================================================

-- 1. Normalización de patentes existentes en declaracion_comercializador
UPDATE declaracion_comercializador
SET placa_patente = UPPER(REPLACE(REPLACE(placa_patente, ' ', ''), '-', ''))
WHERE placa_patente IS NOT NULL 
  AND placa_patente != UPPER(REPLACE(REPLACE(placa_patente, ' ', ''), '-', ''));

UPDATE declaracion_comercializador
SET placa_patente_carro = UPPER(REPLACE(REPLACE(placa_patente_carro, ' ', ''), '-', ''))
WHERE placa_patente_carro IS NOT NULL 
  AND placa_patente_carro != UPPER(REPLACE(REPLACE(placa_patente_carro, ' ', ''), '-', ''));

-- 2. Normalización de patentes existentes en declaracion_planta_abastecimiento
UPDATE declaracion_planta_abastecimiento
SET placa_patente = UPPER(REPLACE(REPLACE(placa_patente, ' ', ''), '-', ''))
WHERE placa_patente IS NOT NULL 
  AND placa_patente != UPPER(REPLACE(REPLACE(placa_patente, ' ', ''), '-', ''));

UPDATE declaracion_planta_abastecimiento
SET placa_patente_carro = UPPER(REPLACE(REPLACE(placa_patente_carro, ' ', ''), '-', ''))
WHERE placa_patente_carro IS NOT NULL 
  AND placa_patente_carro != UPPER(REPLACE(REPLACE(placa_patente_carro, ' ', ''), '-', ''));

-- 3. Índices compuestos para búsqueda de patentes por fecha de traslado
-- Índice 1: declaracion_comercializador (placa_patente, fecha_traslado)
SET @idx1 = (SELECT COUNT(*) FROM information_schema.STATISTICS 
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_comercializador' AND INDEX_NAME = 'idx_comercializador_patente_fecha');
SET @s1 = IF(@idx1 = 0, 'CREATE INDEX idx_comercializador_patente_fecha ON declaracion_comercializador (placa_patente, fecha_traslado)', 'SELECT 1');
PREPARE stmt1 FROM @s1; EXECUTE stmt1; DEALLOCATE PREPARE stmt1;

-- Índice 2: declaracion_comercializador (placa_patente_carro, fecha_traslado)
SET @idx2 = (SELECT COUNT(*) FROM information_schema.STATISTICS 
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_comercializador' AND INDEX_NAME = 'idx_comercializador_carro_fecha');
SET @s2 = IF(@idx2 = 0, 'CREATE INDEX idx_comercializador_carro_fecha ON declaracion_comercializador (placa_patente_carro, fecha_traslado)', 'SELECT 1');
PREPARE stmt2 FROM @s2; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;

-- Índice 3: declaracion_planta_abastecimiento (placa_patente, fecha_traslado)
SET @idx3 = (SELECT COUNT(*) FROM information_schema.STATISTICS 
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_planta_abastecimiento' AND INDEX_NAME = 'idx_planta_patente_fecha');
SET @s3 = IF(@idx3 = 0, 'CREATE INDEX idx_planta_patente_fecha ON declaracion_planta_abastecimiento (placa_patente, fecha_traslado)', 'SELECT 1');
PREPARE stmt3 FROM @s3; EXECUTE stmt3; DEALLOCATE PREPARE stmt3;

-- Índice 4: declaracion_planta_abastecimiento (placa_patente_carro, fecha_traslado)
SET @idx4 = (SELECT COUNT(*) FROM information_schema.STATISTICS 
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_planta_abastecimiento' AND INDEX_NAME = 'idx_planta_carro_fecha');
SET @s4 = IF(@idx4 = 0, 'CREATE INDEX idx_planta_carro_fecha ON declaracion_planta_abastecimiento (placa_patente_carro, fecha_traslado)', 'SELECT 1');
PREPARE stmt4 FROM @s4; EXECUTE stmt4; DEALLOCATE PREPARE stmt4;

-- 4. Creación de tabla de auditoría para registro de consultas públicas ciudadanas
CREATE TABLE IF NOT EXISTS consulta_patente_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  patente VARCHAR(20) NOT NULL,
  ip_hash VARCHAR(64) NOT NULL,
  fecha DATETIME NOT NULL,
  resultado VARCHAR(20) NOT NULL,
  INDEX idx_patente_log_fecha (fecha),
  INDEX idx_patente_log_patente (patente)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
