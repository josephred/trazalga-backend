-- NP.3: Campos de rendimiento en declaracion_planta_produccion y tabla rendimiento_rango (D31)
-- Idempotente y seguro para re-ejecución en MySQL 8.0+

SET @db_name = DATABASE();

-- 1. masa_entrada_equivalente_kg
SET @c1 = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @db_name AND table_name = 'declaracion_planta_produccion' AND column_name = 'masa_entrada_equivalente_kg');
SET @s1 = IF(@c1 = 0, 'ALTER TABLE declaracion_planta_produccion ADD COLUMN masa_entrada_equivalente_kg DECIMAL(14,2) NULL AFTER cantidad_producto', 'SELECT ''Columna masa_entrada_equivalente_kg ya existe'' AS info');
PREPARE p1 FROM @s1; EXECUTE p1; DEALLOCATE PREPARE p1;

-- 2. masa_producto_equivalente_kg
SET @c2 = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @db_name AND table_name = 'declaracion_planta_produccion' AND column_name = 'masa_producto_equivalente_kg');
SET @s2 = IF(@c2 = 0, 'ALTER TABLE declaracion_planta_produccion ADD COLUMN masa_producto_equivalente_kg DECIMAL(14,2) NULL AFTER masa_entrada_equivalente_kg', 'SELECT ''Columna masa_producto_equivalente_kg ya existe'' AS info');
PREPARE p2 FROM @s2; EXECUTE p2; DEALLOCATE PREPARE p2;

-- 3. rendimiento_pct
SET @c3 = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @db_name AND table_name = 'declaracion_planta_produccion' AND column_name = 'rendimiento_pct');
SET @s3 = IF(@c3 = 0, 'ALTER TABLE declaracion_planta_produccion ADD COLUMN rendimiento_pct DECIMAL(7,2) NULL AFTER masa_producto_equivalente_kg', 'SELECT ''Columna rendimiento_pct ya existe'' AS info');
PREPARE p3 FROM @s3; EXECUTE p3; DEALLOCATE PREPARE p3;

-- 4. rendimiento_max_teorico_pct
SET @c4 = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @db_name AND table_name = 'declaracion_planta_produccion' AND column_name = 'rendimiento_max_teorico_pct');
SET @s4 = IF(@c4 = 0, 'ALTER TABLE declaracion_planta_produccion ADD COLUMN rendimiento_max_teorico_pct DECIMAL(7,2) NULL AFTER rendimiento_pct', 'SELECT ''Columna rendimiento_max_teorico_pct ya existe'' AS info');
PREPARE p4 FROM @s4; EXECUTE p4; DEALLOCATE PREPARE p4;

-- 5. rendimiento_fuente
SET @c5 = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @db_name AND table_name = 'declaracion_planta_produccion' AND column_name = 'rendimiento_fuente');
SET @s5 = IF(@c5 = 0, 'ALTER TABLE declaracion_planta_produccion ADD COLUMN rendimiento_fuente VARCHAR(20) NULL AFTER rendimiento_max_teorico_pct', 'SELECT ''Columna rendimiento_fuente ya existe'' AS info');
PREPARE p5 FROM @s5; EXECUTE p5; DEALLOCATE PREPARE p5;

-- 6. rendimiento_evaluado_en
SET @c6 = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = @db_name AND table_name = 'declaracion_planta_produccion' AND column_name = 'rendimiento_evaluado_en');
SET @s6 = IF(@c6 = 0, 'ALTER TABLE declaracion_planta_produccion ADD COLUMN rendimiento_evaluado_en DATETIME NULL AFTER rendimiento_fuente', 'SELECT ''Columna rendimiento_evaluado_en ya existe'' AS info');
PREPARE p6 FROM @s6; EXECUTE p6; DEALLOCATE PREPARE p6;

-- 7. Tabla rendimiento_rango (D31)
CREATE TABLE IF NOT EXISTS `rendimiento_rango` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `especie_id` bigint NOT NULL,
  `humedad_entrada_id` bigint NOT NULL,
  `producto_id` bigint NOT NULL,
  `min_pct` decimal(7,2) DEFAULT NULL,
  `max_pct` decimal(7,2) DEFAULT NULL,
  `resolucion` varchar(100) DEFAULT NULL,
  `vigencia_inicio` date NOT NULL,
  `vigencia_fin` date DEFAULT NULL,
  `activo` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_rr_lookup` (`especie_id`, `humedad_entrada_id`, `producto_id`, `activo`, `vigencia_inicio`),
  CONSTRAINT `fk_rr_especie` FOREIGN KEY (`especie_id`) REFERENCES `especie` (`id`),
  CONSTRAINT `fk_rr_humedad_entrada` FOREIGN KEY (`humedad_entrada_id`) REFERENCES `humedad_estado` (`id`),
  CONSTRAINT `fk_rr_producto` FOREIGN KEY (`producto_id`) REFERENCES `producto` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

