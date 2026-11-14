-- ============================================================================
-- QR.2: Rendimiento final sobre captura biológica de origen (punto 3.3 / D34-D36)
-- - Agrega captura_origen_kg, captura_origen_completa y rendimiento_final_pct en
--   declaracion_planta_produccion (preservando rendimiento_pct como rendimiento de proceso).
-- - Amplía declaracion_planta_produccion.rendimiento_fuente a VARCHAR(40) para registrar
--   la base aplicada (CAPTURA_ORIGEN vs respaldo MASA_RECIBIDA).
-- - Agrega columna base en rendimiento_rango ('MASA_RECIBIDA' | 'CAPTURA_ORIGEN'),
--   marcando las filas existentes como 'MASA_RECIBIDA'.
-- Idempotente para re-ejecución en MySQL 8.0+.
-- ============================================================================

SET @db_name = DATABASE();

-- 1. declaracion_planta_produccion.captura_origen_kg DECIMAL(14,2) NULL
SET @c_cap_kg = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db_name
      AND table_name = 'declaracion_planta_produccion'
      AND column_name = 'captura_origen_kg'
);
SET @s_cap_kg = IF(
    @c_cap_kg = 0,
    'ALTER TABLE declaracion_planta_produccion ADD COLUMN captura_origen_kg DECIMAL(14,2) NULL AFTER masa_producto_equivalente_kg',
    'SELECT ''Columna declaracion_planta_produccion.captura_origen_kg ya existe'' AS info'
);
PREPARE p_cap_kg FROM @s_cap_kg; EXECUTE p_cap_kg; DEALLOCATE PREPARE p_cap_kg;

-- 2. declaracion_planta_produccion.captura_origen_completa TINYINT(1) NULL
SET @c_cap_comp = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db_name
      AND table_name = 'declaracion_planta_produccion'
      AND column_name = 'captura_origen_completa'
);
SET @s_cap_comp = IF(
    @c_cap_comp = 0,
    'ALTER TABLE declaracion_planta_produccion ADD COLUMN captura_origen_completa TINYINT(1) NULL AFTER captura_origen_kg',
    'SELECT ''Columna declaracion_planta_produccion.captura_origen_completa ya existe'' AS info'
);
PREPARE p_cap_comp FROM @s_cap_comp; EXECUTE p_cap_comp; DEALLOCATE PREPARE p_cap_comp;

-- 3. declaracion_planta_produccion.rendimiento_final_pct DECIMAL(7,2) NULL
SET @c_rend_fin = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db_name
      AND table_name = 'declaracion_planta_produccion'
      AND column_name = 'rendimiento_final_pct'
);
SET @s_rend_fin = IF(
    @c_rend_fin = 0,
    'ALTER TABLE declaracion_planta_produccion ADD COLUMN rendimiento_final_pct DECIMAL(7,2) NULL AFTER rendimiento_pct',
    'SELECT ''Columna declaracion_planta_produccion.rendimiento_final_pct ya existe'' AS info'
);
PREPARE p_rend_fin FROM @s_rend_fin; EXECUTE p_rend_fin; DEALLOCATE PREPARE p_rend_fin;

-- 4. Ampliar declaracion_planta_produccion.rendimiento_fuente de 20 a 40 caracteres
SET @len_fuente = (
    SELECT CHARACTER_MAXIMUM_LENGTH
    FROM information_schema.columns
    WHERE table_schema = @db_name
      AND table_name = 'declaracion_planta_produccion'
      AND column_name = 'rendimiento_fuente'
);
SET @s_fuente = IF(
    @len_fuente IS NOT NULL AND @len_fuente < 40,
    'ALTER TABLE declaracion_planta_produccion MODIFY COLUMN rendimiento_fuente VARCHAR(40) NULL',
    'SELECT ''Columna declaracion_planta_produccion.rendimiento_fuente ya tiene largo >= 40'' AS info'
);
PREPARE p_fuente FROM @s_fuente; EXECUTE p_fuente; DEALLOCATE PREPARE p_fuente;

-- 5. rendimiento_rango.base VARCHAR(20) NOT NULL DEFAULT 'MASA_RECIBIDA'
SET @c_base = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db_name
      AND table_name = 'rendimiento_rango'
      AND column_name = 'base'
);
SET @s_base = IF(
    @c_base = 0,
    'ALTER TABLE rendimiento_rango ADD COLUMN base VARCHAR(20) NOT NULL DEFAULT ''MASA_RECIBIDA'' AFTER producto_id',
    'SELECT ''Columna rendimiento_rango.base ya existe'' AS info'
);
PREPARE p_base FROM @s_base; EXECUTE p_base; DEALLOCATE PREPARE p_base;

-- Asegurar que filas previas queden explícitamente en MASA_RECIBIDA si tenían nulo/vacío
UPDATE rendimiento_rango
SET base = 'MASA_RECIBIDA'
WHERE base IS NULL OR TRIM(base) = '';

-- 6. Índice de consulta por vigencia y base en rendimiento_rango
SET @i_rango_base = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = @db_name
      AND table_name = 'rendimiento_rango'
      AND index_name = 'idx_rendimiento_rango_vigencia_base'
);
SET @si_rango_base = IF(
    @i_rango_base = 0,
    'CREATE INDEX idx_rendimiento_rango_vigencia_base ON rendimiento_rango (especie_id, humedad_entrada_id, producto_id, base, activo, vigencia_inicio)',
    'SELECT ''Indice idx_rendimiento_rango_vigencia_base ya existe'' AS info'
);
PREPARE pi_rango_base FROM @si_rango_base; EXECUTE pi_rango_base; DEALLOCATE PREPARE pi_rango_base;
