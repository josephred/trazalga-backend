-- ============================================================================
-- QF.1: Ampliar modelo de producto para el catálogo oficial Sernapesca y código
-- Sernapesca en maestro de especies (Anexo 4 #2 / refinamiento 13-11-2026)
-- Idempotente y segura para re-ejecución en MySQL 8.0+.
-- ============================================================================

SET @db_name = DATABASE();

-- 1. Ampliar producto.nombre de 100 a 150 caracteres (mantiene índice único)
SET @len_nombre = (
    SELECT CHARACTER_MAXIMUM_LENGTH
    FROM information_schema.columns
    WHERE table_schema = @db_name
      AND table_name = 'producto'
      AND column_name = 'nombre'
);
SET @s_nombre = IF(
    @len_nombre IS NOT NULL AND @len_nombre < 150,
    'ALTER TABLE producto MODIFY COLUMN nombre VARCHAR(150) NOT NULL',
    'SELECT ''Columna producto.nombre ya tiene largo >= 150'' AS info'
);
PREPARE p_nombre FROM @s_nombre; EXECUTE p_nombre; DEALLOCATE PREPARE p_nombre;

-- 2. Columna producto.presentacion VARCHAR(60) NULL
SET @c_pres = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db_name AND table_name = 'producto' AND column_name = 'presentacion'
);
SET @s_pres = IF(
    @c_pres = 0,
    'ALTER TABLE producto ADD COLUMN presentacion VARCHAR(60) NULL AFTER descripcion',
    'SELECT ''Columna producto.presentacion ya existe'' AS info'
);
PREPARE p_pres FROM @s_pres; EXECUTE p_pres; DEALLOCATE PREPARE p_pres;

-- 3. Columna producto.calibre VARCHAR(40) NULL
SET @c_cal = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db_name AND table_name = 'producto' AND column_name = 'calibre'
);
SET @s_cal = IF(
    @c_cal = 0,
    'ALTER TABLE producto ADD COLUMN calibre VARCHAR(40) NULL AFTER presentacion',
    'SELECT ''Columna producto.calibre ya existe'' AS info'
);
PREPARE p_cal FROM @s_cal; EXECUTE p_cal; DEALLOCATE PREPARE p_cal;

-- 4. Columna especie.codigo_sernapesca VARCHAR(30) NULL
SET @c_esp = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db_name AND table_name = 'especie' AND column_name = 'codigo_sernapesca'
);
SET @s_esp = IF(
    @c_esp = 0,
    'ALTER TABLE especie ADD COLUMN codigo_sernapesca VARCHAR(30) NULL AFTER nombre',
    'SELECT ''Columna especie.codigo_sernapesca ya existe'' AS info'
);
PREPARE p_esp FROM @s_esp; EXECUTE p_esp; DEALLOCATE PREPARE p_esp;

-- 5. Índice único uk_especie_codigo_sernapesca (permite múltiples NULL en MySQL)
SET @i_esp = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = @db_name AND table_name = 'especie' AND index_name = 'uk_especie_codigo_sernapesca'
);
SET @si_esp = IF(
    @i_esp = 0,
    'CREATE UNIQUE INDEX uk_especie_codigo_sernapesca ON especie (codigo_sernapesca)',
    'SELECT ''Indice uk_especie_codigo_sernapesca ya existe'' AS info'
);
PREPARE pi_esp FROM @si_esp; EXECUTE pi_esp; DEALLOCATE PREPARE pi_esp;

-- 6. Cargar códigos oficiales Sernapesca para las 3 especies del catálogo (141, 142, 145)
SET @has_141 = (SELECT COUNT(*) FROM especie WHERE codigo_sernapesca = '141');
UPDATE especie
SET codigo_sernapesca = '141'
WHERE @has_141 = 0
  AND codigo_sernapesca IS NULL
  AND UPPER(TRIM(nombre)) IN ('HUIRO NEGRO O CHASCON', 'CHASCON O HUIRO NEGRO', 'HUIRO NEGRO')
ORDER BY id ASC
LIMIT 1;

SET @has_142 = (SELECT COUNT(*) FROM especie WHERE codigo_sernapesca = '142');
UPDATE especie
SET codigo_sernapesca = '142'
WHERE @has_142 = 0
  AND codigo_sernapesca IS NULL
  AND UPPER(TRIM(nombre)) = 'HUIRO PALO'
ORDER BY id ASC
LIMIT 1;

SET @has_145 = (SELECT COUNT(*) FROM especie WHERE codigo_sernapesca = '145');
UPDATE especie
SET codigo_sernapesca = '145'
WHERE @has_145 = 0
  AND codigo_sernapesca IS NULL
  AND UPPER(TRIM(nombre)) = 'HUIRO'
ORDER BY id ASC
LIMIT 1;
