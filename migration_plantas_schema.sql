-- ============================================================
-- Migración: Columnas Faltantes para Declaraciones de Plantas
-- Tablas: declaracion_planta_abastecimiento, 
--         declaracion_planta_produccion, 
--         declaracion_planta_destino
-- 
-- Ejecutar manualmente en la base de datos de producción/QA.
-- ============================================================

-- ------------------------------------------------------------
-- 1. Tabla: declaracion_planta_abastecimiento
-- ------------------------------------------------------------
ALTER TABLE declaracion_planta_abastecimiento
  ADD COLUMN IF NOT EXISTS nombre_planta VARCHAR(255) NOT NULL DEFAULT 'Sin Nombre',
  ADD COLUMN IF NOT EXISTS codigo_sernapesca VARCHAR(50) NOT NULL DEFAULT 'Sin Código',
  ADD COLUMN IF NOT EXISTS declaraciones_seleccionadas VARCHAR(1000) NULL,
  ADD COLUMN IF NOT EXISTS consumida_por_tipo VARCHAR(50) NULL;

-- ------------------------------------------------------------
-- 2. Tabla: declaracion_planta_produccion
-- ------------------------------------------------------------
ALTER TABLE declaracion_planta_produccion
  ADD COLUMN IF NOT EXISTS nombre_planta VARCHAR(255) NOT NULL DEFAULT 'Sin Nombre',
  ADD COLUMN IF NOT EXISTS codigo_sernapesca VARCHAR(50) NOT NULL DEFAULT 'Sin Código',
  ADD COLUMN IF NOT EXISTS declaraciones_seleccionadas VARCHAR(1000) NULL,
  ADD COLUMN IF NOT EXISTS consumida_por_tipo VARCHAR(50) NULL;

-- ------------------------------------------------------------
-- 3. Tabla: declaracion_planta_destino
-- ------------------------------------------------------------
ALTER TABLE declaracion_planta_destino
  ADD COLUMN IF NOT EXISTS nombre_planta VARCHAR(255) NOT NULL DEFAULT 'Sin Nombre',
  ADD COLUMN IF NOT EXISTS codigo_sernapesca VARCHAR(50) NOT NULL DEFAULT 'Sin Código',
  ADD COLUMN IF NOT EXISTS declaraciones_seleccionadas VARCHAR(1000) NULL;
