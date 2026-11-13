-- NF.1: Extiende catálogo producto con código oficial, humedad del formato, especie, estado activo y auditoría
-- Idempotente y seguro para re-ejecución en MySQL 8.0+

SET @db_name = DATABASE();

-- 1. codigo
SET @c1 = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @db_name AND table_name = 'producto' AND column_name = 'codigo');
SET @s1 = IF(@c1 = 0, 'ALTER TABLE producto ADD COLUMN codigo VARCHAR(30) NULL AFTER id', 'SELECT ''Columna codigo ya existe'' AS info');
PREPARE p1 FROM @s1; EXECUTE p1; DEALLOCATE PREPARE p1;

-- 2. unique index uk_producto_codigo
SET @i1 = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = @db_name AND table_name = 'producto' AND index_name = 'uk_producto_codigo');
SET @si1 = IF(@i1 = 0, 'CREATE UNIQUE INDEX uk_producto_codigo ON producto (codigo)', 'SELECT ''Indice uk_producto_codigo ya existe'' AS info');
PREPARE pi1 FROM @si1; EXECUTE pi1; DEALLOCATE PREPARE pi1;

-- 3. humedad_estado_id
SET @c2 = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @db_name AND table_name = 'producto' AND column_name = 'humedad_estado_id');
SET @s2 = IF(@c2 = 0, 'ALTER TABLE producto ADD COLUMN humedad_estado_id BIGINT NULL AFTER descripcion', 'SELECT ''Columna humedad_estado_id ya existe'' AS info');
PREPARE p2 FROM @s2; EXECUTE p2; DEALLOCATE PREPARE p2;

-- 4. fk_producto_humedad_estado
SET @fk1 = (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = @db_name AND table_name = 'producto' AND constraint_name = 'fk_producto_humedad_estado');
SET @sfk1 = IF(@fk1 = 0, 'ALTER TABLE producto ADD CONSTRAINT fk_producto_humedad_estado FOREIGN KEY (humedad_estado_id) REFERENCES humedad_estado(id)', 'SELECT ''FK fk_producto_humedad_estado ya existe'' AS info');
PREPARE pfk1 FROM @sfk1; EXECUTE pfk1; DEALLOCATE PREPARE pfk1;

-- 5. especie_id
SET @c3 = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @db_name AND table_name = 'producto' AND column_name = 'especie_id');
SET @s3 = IF(@c3 = 0, 'ALTER TABLE producto ADD COLUMN especie_id BIGINT NULL AFTER humedad_estado_id', 'SELECT ''Columna especie_id ya existe'' AS info');
PREPARE p3 FROM @s3; EXECUTE p3; DEALLOCATE PREPARE p3;

-- 6. fk_producto_especie
SET @fk2 = (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = @db_name AND table_name = 'producto' AND constraint_name = 'fk_producto_especie');
SET @sfk2 = IF(@fk2 = 0, 'ALTER TABLE producto ADD CONSTRAINT fk_producto_especie FOREIGN KEY (especie_id) REFERENCES especie(id)', 'SELECT ''FK fk_producto_especie ya existe'' AS info');
PREPARE pfk2 FROM @sfk2; EXECUTE pfk2; DEALLOCATE PREPARE pfk2;

-- 7. activo
SET @c4 = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @db_name AND table_name = 'producto' AND column_name = 'activo');
SET @s4 = IF(@c4 = 0, 'ALTER TABLE producto ADD COLUMN activo TINYINT(1) NOT NULL DEFAULT 1 AFTER especie_id', 'SELECT ''Columna activo ya existe'' AS info');
PREPARE p4 FROM @s4; EXECUTE p4; DEALLOCATE PREPARE p4;

-- 8. fuente
SET @c5 = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @db_name AND table_name = 'producto' AND column_name = 'fuente');
SET @s5 = IF(@c5 = 0, 'ALTER TABLE producto ADD COLUMN fuente VARCHAR(60) NULL AFTER activo', 'SELECT ''Columna fuente ya existe'' AS info');
PREPARE p5 FROM @s5; EXECUTE p5; DEALLOCATE PREPARE p5;

-- 9. actualizado_en
SET @c6 = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @db_name AND table_name = 'producto' AND column_name = 'actualizado_en');
SET @s6 = IF(@c6 = 0, 'ALTER TABLE producto ADD COLUMN actualizado_en DATETIME NULL AFTER fuente', 'SELECT ''Columna actualizado_en ya existe'' AS info');
PREPARE p6 FROM @s6; EXECUTE p6; DEALLOCATE PREPARE p6;

-- 10. actualizado_por
SET @c7 = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @db_name AND table_name = 'producto' AND column_name = 'actualizado_por');
SET @s7 = IF(@c7 = 0, 'ALTER TABLE producto ADD COLUMN actualizado_por VARCHAR(60) NULL AFTER actualizado_en', 'SELECT ''Columna actualizado_por ya existe'' AS info');
PREPARE p7 FROM @s7; EXECUTE p7; DEALLOCATE PREPARE p7;

