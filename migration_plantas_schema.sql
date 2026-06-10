-- ============================================================
-- Migración: Columnas Faltantes para Declaraciones de Plantas
-- Tablas: declaracion_planta_abastecimiento, 
--         declaracion_planta_produccion, 
--         declaracion_planta_destino
-- 
-- IMPORTANTE: Ejecuta estas sentencias una por una.
-- Si alguna columna ya existe, recibirás un error "Duplicate column name",
-- lo cual es normal y significa que esa columna ya estaba creada.
-- Puedes ignorar ese error y continuar con la siguiente sentencia.
-- ============================================================

-- ------------------------------------------------------------
-- 1. Tabla: declaracion_planta_abastecimiento
-- ------------------------------------------------------------
ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN nombre_planta VARCHAR(255) NOT NULL DEFAULT 'Sin Nombre';
ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN codigo_sernapesca VARCHAR(50) NOT NULL DEFAULT 'Sin Código';
ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN declaraciones_seleccionadas VARCHAR(1000) NULL;
ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN consumida_por_tipo VARCHAR(50) NULL;

-- ------------------------------------------------------------
-- 2. Tabla: declaracion_planta_produccion
-- ------------------------------------------------------------
ALTER TABLE declaracion_planta_produccion ADD COLUMN nombre_planta VARCHAR(255) NOT NULL DEFAULT 'Sin Nombre';
ALTER TABLE declaracion_planta_produccion ADD COLUMN codigo_sernapesca VARCHAR(50) NOT NULL DEFAULT 'Sin Código';
ALTER TABLE declaracion_planta_produccion ADD COLUMN declaraciones_seleccionadas VARCHAR(1000) NULL;
ALTER TABLE declaracion_planta_produccion ADD COLUMN consumida_por_tipo VARCHAR(50) NULL;

-- ------------------------------------------------------------
-- 3. Tabla: declaracion_planta_destino
-- ------------------------------------------------------------
ALTER TABLE declaracion_planta_destino ADD COLUMN nombre_planta VARCHAR(255) NOT NULL DEFAULT 'Sin Nombre';
ALTER TABLE declaracion_planta_destino ADD COLUMN codigo_sernapesca VARCHAR(50) NOT NULL DEFAULT 'Sin Código';
ALTER TABLE declaracion_planta_destino ADD COLUMN declaraciones_seleccionadas VARCHAR(1000) NULL;
