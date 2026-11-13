-- NA.2: Agregar lugar_acopio a declaracion_planta_abastecimiento (idempotente)
-- Ejecutar contra la base MySQL de TrazAlga antes o junto al despliegue del backend NA.2.

SET @db_name := DATABASE();

SELECT COUNT(*) INTO @col_exists
FROM information_schema.columns
WHERE table_schema = @db_name
  AND table_name = 'declaracion_planta_abastecimiento'
  AND column_name = 'lugar_acopio';

SET @sql_stmt := IF(
    @col_exists = 0,
    'ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN lugar_acopio VARCHAR(120) NULL AFTER nombre_planta',
    'SELECT ''Columna lugar_acopio ya existe en declaracion_planta_abastecimiento'' AS info'
);

PREPARE stmt FROM @sql_stmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Verificacion
SELECT
    COUNT(*) AS total_abastecimientos,
    SUM(CASE WHEN lugar_acopio IS NOT NULL AND TRIM(lugar_acopio) <> '' THEN 1 ELSE 0 END) AS con_lugar_acopio
FROM declaracion_planta_abastecimiento;
