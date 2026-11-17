-- =========================================================================
-- MIGRACIÓN: migration_origen_patente.sql (ZC.9 / D53)
-- TrazAlga - Patente de camión y carro en traslados directos origen -> planta
-- Idempotente con guarda information_schema.columns (MySQL 8.0+).
-- =========================================================================

SET NAMES utf8mb4;

SET @db_name = DATABASE();

-- 1. declaracion_recolector
SET @c1 = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @db_name AND table_name = 'declaracion_recolector' AND column_name = 'placa_patente');
SET @s1 = IF(@c1 = 0, 'ALTER TABLE declaracion_recolector ADD COLUMN placa_patente VARCHAR(20) NULL', 'SELECT ''Columna placa_patente ya existe en declaracion_recolector'' AS info');
PREPARE p1 FROM @s1; EXECUTE p1; DEALLOCATE PREPARE p1;

SET @c2 = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @db_name AND table_name = 'declaracion_recolector' AND column_name = 'placa_patente_carro');
SET @s2 = IF(@c2 = 0, 'ALTER TABLE declaracion_recolector ADD COLUMN placa_patente_carro VARCHAR(20) NULL AFTER placa_patente', 'SELECT ''Columna placa_patente_carro ya existe en declaracion_recolector'' AS info');
PREPARE p2 FROM @s2; EXECUTE p2; DEALLOCATE PREPARE p2;

-- 2. declaracion_armador
SET @c3 = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @db_name AND table_name = 'declaracion_armador' AND column_name = 'placa_patente');
SET @s3 = IF(@c3 = 0, 'ALTER TABLE declaracion_armador ADD COLUMN placa_patente VARCHAR(20) NULL', 'SELECT ''Columna placa_patente ya existe en declaracion_armador'' AS info');
PREPARE p3 FROM @s3; EXECUTE p3; DEALLOCATE PREPARE p3;

SET @c4 = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @db_name AND table_name = 'declaracion_armador' AND column_name = 'placa_patente_carro');
SET @s4 = IF(@c4 = 0, 'ALTER TABLE declaracion_armador ADD COLUMN placa_patente_carro VARCHAR(20) NULL AFTER placa_patente', 'SELECT ''Columna placa_patente_carro ya existe en declaracion_armador'' AS info');
PREPARE p4 FROM @s4; EXECUTE p4; DEALLOCATE PREPARE p4;

-- 3. declaracion_area
SET @c5 = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @db_name AND table_name = 'declaracion_area' AND column_name = 'placa_patente');
SET @s5 = IF(@c5 = 0, 'ALTER TABLE declaracion_area ADD COLUMN placa_patente VARCHAR(20) NULL', 'SELECT ''Columna placa_patente ya existe en declaracion_area'' AS info');
PREPARE p5 FROM @s5; EXECUTE p5; DEALLOCATE PREPARE p5;

SET @c6 = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @db_name AND table_name = 'declaracion_area' AND column_name = 'placa_patente_carro');
SET @s6 = IF(@c6 = 0, 'ALTER TABLE declaracion_area ADD COLUMN placa_patente_carro VARCHAR(20) NULL AFTER placa_patente', 'SELECT ''Columna placa_patente_carro ya existe en declaracion_area'' AS info');
PREPARE p6 FROM @s6; EXECUTE p6; DEALLOCATE PREPARE p6;
