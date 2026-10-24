-- migration_transportistas.sql · TT.1 Nómina de transportistas y vehículos (reunión Sernapesca 16-oct)
-- Idempotente: se puede ejecutar múltiples veces sin error ni duplicar estructuras.

CREATE TABLE IF NOT EXISTS transportista (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  rut                 VARCHAR(12)  NULL,            -- canónico 12345678-5; NULL si no se declaró
  rut_valido          BOOLEAN      NOT NULL DEFAULT FALSE,
  nombre_canonico     VARCHAR(150) NOT NULL,
  nombre_normalizado  VARCHAR(150) NOT NULL,        -- mayúsculas, sin tildes, espacios simples
  comercializador_ref BIGINT       NULL,            -- sólo para los sin RUT: ámbito del calce por nombre
  estado              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVO',  -- ACTIVO | FUSIONADO | REVISAR
  fusionado_en_id     BIGINT       NULL,
  created_at          DATETIME     NOT NULL,
  updated_at          DATETIME     NULL,
  UNIQUE KEY ux_transportista_rut (rut),
  KEY ix_transportista_nombre (nombre_normalizado)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS transportista_alias (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  transportista_id    BIGINT NOT NULL,
  nombre_declarado    VARCHAR(150) NOT NULL,
  nombre_normalizado  VARCHAR(150) NOT NULL,
  veces               INT NOT NULL DEFAULT 1,
  primera_vez         DATETIME NOT NULL,
  ultima_vez          DATETIME NOT NULL,
  UNIQUE KEY ux_alias (transportista_id, nombre_normalizado),
  CONSTRAINT fk_alias_transportista FOREIGN KEY (transportista_id) REFERENCES transportista (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS vehiculo (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  patente             VARCHAR(10) NOT NULL,
  formato_valido      BOOLEAN NOT NULL,
  usado_como_camion   BOOLEAN NOT NULL DEFAULT FALSE,
  usado_como_rampla   BOOLEAN NOT NULL DEFAULT FALSE,
  tipo_vehiculo       VARCHAR(40) NULL,
  primera_vez         DATETIME NOT NULL,
  ultima_vez          DATETIME NOT NULL,
  UNIQUE KEY ux_vehiculo_patente (patente)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS transportista_fusion_log (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  origen_id           BIGINT NOT NULL,
  destino_id          BIGINT NOT NULL,
  usuario_id          BIGINT NOT NULL,
  motivo              VARCHAR(300) NOT NULL,
  created_at          DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Columnas FK en declaracion_comercializador y declaracion_planta_abastecimiento
SET @dbname = DATABASE();

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @dbname AND table_name = 'declaracion_comercializador' AND column_name = 'transportista_id');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE declaracion_comercializador ADD COLUMN transportista_id BIGINT NULL, ADD INDEX ix_dc_transportista (transportista_id)', 'SELECT "transportista_id already exists"');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @dbname AND table_name = 'declaracion_comercializador' AND column_name = 'vehiculo_id');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE declaracion_comercializador ADD COLUMN vehiculo_id BIGINT NULL, ADD INDEX ix_dc_vehiculo (vehiculo_id)', 'SELECT "vehiculo_id already exists"');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @dbname AND table_name = 'declaracion_comercializador' AND column_name = 'vehiculo_carro_id');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE declaracion_comercializador ADD COLUMN vehiculo_carro_id BIGINT NULL, ADD INDEX ix_dc_vehiculo_carro (vehiculo_carro_id)', 'SELECT "vehiculo_carro_id already exists"');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- declaracion_planta_abastecimiento
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @dbname AND table_name = 'declaracion_planta_abastecimiento' AND column_name = 'transportista_id');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN transportista_id BIGINT NULL, ADD INDEX ix_dpa_transportista (transportista_id)', 'SELECT "transportista_id already exists"');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @dbname AND table_name = 'declaracion_planta_abastecimiento' AND column_name = 'vehiculo_id');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN vehiculo_id BIGINT NULL, ADD INDEX ix_dpa_vehiculo (vehiculo_id)', 'SELECT "vehiculo_id already exists"');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @dbname AND table_name = 'declaracion_planta_abastecimiento' AND column_name = 'vehiculo_carro_id');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN vehiculo_carro_id BIGINT NULL, ADD INDEX ix_dpa_vehiculo_carro (vehiculo_carro_id)', 'SELECT "vehiculo_carro_id already exists"');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
