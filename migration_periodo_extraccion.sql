-- ============================================================
-- Migración: Periodo_Extracción y Tasa_diaria_Recolección
-- Tabla: declaracion_recolector
-- Ejecutar manualmente en MySQL si es necesario.
-- Nota: Con ddl-auto=update, Hibernate crea las columnas
-- automáticamente. Este script es solo de referencia y para
-- migrar los datos históricos.
-- ============================================================

-- 1. Agregar nuevas columnas (si Hibernate no las creó)
ALTER TABLE declaracion_recolector
  ADD COLUMN IF NOT EXISTS periodo_extraccion_inicio DATE NULL
    AFTER fecha_extraccion,
  ADD COLUMN IF NOT EXISTS periodo_extraccion_fin DATE NULL
    AFTER periodo_extraccion_inicio,
  ADD COLUMN IF NOT EXISTS tasa_diaria_recoleccion DECIMAL(10,3) NULL
    AFTER captura;

-- 2. Migración de datos históricos:
--    Los registros existentes tienen solo fecha_extraccion (un día).
--    Se copia a ambos campos del periodo (periodo de 1 día).
UPDATE declaracion_recolector
SET periodo_extraccion_inicio = fecha_extraccion,
    periodo_extraccion_fin    = fecha_extraccion
WHERE periodo_extraccion_inicio IS NULL
  AND fecha_extraccion IS NOT NULL;

-- 3. Calcular tasa diaria para registros históricos:
--    Como el periodo es de 1 día, tasa = desembarque / 1 = desembarque.
UPDATE declaracion_recolector
SET tasa_diaria_recoleccion = desembarque
WHERE tasa_diaria_recoleccion IS NULL
  AND desembarque IS NOT NULL
  AND periodo_extraccion_inicio IS NOT NULL;

-- 4. Verificación
SELECT
  id,
  fecha_extraccion,
  periodo_extraccion_inicio,
  periodo_extraccion_fin,
  DATEDIFF(periodo_extraccion_fin, periodo_extraccion_inicio) + 1 AS dias_periodo,
  desembarque,
  tasa_diaria_recoleccion
FROM declaracion_recolector
ORDER BY id DESC
LIMIT 10;
