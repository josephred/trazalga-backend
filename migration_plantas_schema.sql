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
ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN nombre_planta VARCHAR(255) NULL;
ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN codigo_sernapesca VARCHAR(50) NULL;
ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN declaraciones_seleccionadas VARCHAR(1000) NULL;
ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN consumida_por_tipo VARCHAR(50) NULL;
ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN fecha_traslado DATE NULL;
ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN humedad_higrometro DECIMAL(5,2) NULL;
ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN documento_tributario_origen_tipo VARCHAR(255) NULL;
ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN documento_tributario_origen_numero VARCHAR(255) NULL;
ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN documento_tributario_origen_fecha DATE NULL;
ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN documento_tributario_destino_tipo VARCHAR(255) NULL;
ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN documento_tributario_destino_numero VARCHAR(255) NULL;
ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN documento_tributario_destino_fecha DATE NULL;
ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN vehiculo_transporte VARCHAR(255) NULL;
ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN chofer_transporte VARCHAR(255) NULL;
ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN patente VARCHAR(255) NULL;
ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN rut_chofer VARCHAR(20) NULL;
ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN placa_patente VARCHAR(20) NULL;
ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN placa_patente_carro VARCHAR(20) NULL;
ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN peso_recepcionado DOUBLE NULL;
ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN estado VARCHAR(20) NULL;
ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN resumen_documento TEXT NULL;

-- ------------------------------------------------------------
-- 2. Tabla: declaracion_planta_produccion
-- ------------------------------------------------------------
ALTER TABLE declaracion_planta_produccion ADD COLUMN nombre_planta VARCHAR(255) NULL;
ALTER TABLE declaracion_planta_produccion ADD COLUMN codigo_sernapesca VARCHAR(50) NULL;
ALTER TABLE declaracion_planta_produccion ADD COLUMN declaraciones_seleccionadas VARCHAR(1000) NULL;
ALTER TABLE declaracion_planta_produccion ADD COLUMN consumida_por_tipo VARCHAR(50) NULL;
ALTER TABLE declaracion_planta_produccion ADD COLUMN fecha_traslado DATE NULL;
ALTER TABLE declaracion_planta_produccion ADD COLUMN humedad_higrometro DECIMAL(5,2) NULL;
ALTER TABLE declaracion_planta_produccion ADD COLUMN documento_tributario_origen_tipo VARCHAR(255) NULL;
ALTER TABLE declaracion_planta_produccion ADD COLUMN documento_tributario_origen_numero VARCHAR(255) NULL;
ALTER TABLE declaracion_planta_produccion ADD COLUMN documento_tributario_origen_fecha DATE NULL;
ALTER TABLE declaracion_planta_produccion ADD COLUMN documento_tributario_destino_tipo VARCHAR(255) NULL;
ALTER TABLE declaracion_planta_produccion ADD COLUMN documento_tributario_destino_numero VARCHAR(255) NULL;
ALTER TABLE declaracion_planta_produccion ADD COLUMN documento_tributario_destino_fecha DATE NULL;
ALTER TABLE declaracion_planta_produccion ADD COLUMN vehiculo_transporte VARCHAR(255) NULL;
ALTER TABLE declaracion_planta_produccion ADD COLUMN chofer_transporte VARCHAR(255) NULL;
ALTER TABLE declaracion_planta_produccion ADD COLUMN patente VARCHAR(255) NULL;
ALTER TABLE declaracion_planta_produccion ADD COLUMN rut_chofer VARCHAR(20) NULL;
ALTER TABLE declaracion_planta_produccion ADD COLUMN placa_patente VARCHAR(20) NULL;
ALTER TABLE declaracion_planta_produccion ADD COLUMN placa_patente_carro VARCHAR(20) NULL;
ALTER TABLE declaracion_planta_produccion ADD COLUMN estado VARCHAR(20) NULL;
ALTER TABLE declaracion_planta_produccion ADD COLUMN resumen_documento TEXT NULL;

-- ------------------------------------------------------------
-- 3. Tabla: declaracion_planta_destino
-- ------------------------------------------------------------
ALTER TABLE declaracion_planta_destino ADD COLUMN nombre_planta VARCHAR(255) NULL;
ALTER TABLE declaracion_planta_destino ADD COLUMN codigo_sernapesca VARCHAR(50) NULL;
ALTER TABLE declaracion_planta_destino ADD COLUMN declaraciones_seleccionadas VARCHAR(1000) NULL;
ALTER TABLE declaracion_planta_destino ADD COLUMN consumida_por_tipo VARCHAR(50) NULL;
ALTER TABLE declaracion_planta_destino ADD COLUMN fecha_traslado DATE NULL;
ALTER TABLE declaracion_planta_destino ADD COLUMN humedad_higrometro DECIMAL(5,2) NULL;
ALTER TABLE declaracion_planta_destino ADD COLUMN documento_tributario_origen_tipo VARCHAR(255) NULL;
ALTER TABLE declaracion_planta_destino ADD COLUMN documento_tributario_origen_numero VARCHAR(255) NULL;
ALTER TABLE declaracion_planta_destino ADD COLUMN documento_tributario_origen_fecha DATE NULL;
ALTER TABLE declaracion_planta_destino ADD COLUMN vehiculo_transporte VARCHAR(255) NULL;
ALTER TABLE declaracion_planta_destino ADD COLUMN chofer_transporte VARCHAR(255) NULL;
ALTER TABLE declaracion_planta_destino ADD COLUMN rut_chofer VARCHAR(20) NULL;
ALTER TABLE declaracion_planta_destino ADD COLUMN placa_patente VARCHAR(20) NULL;
ALTER TABLE declaracion_planta_destino ADD COLUMN placa_patente_carro VARCHAR(20) NULL;
ALTER TABLE declaracion_planta_destino ADD COLUMN estado VARCHAR(20) NULL;
ALTER TABLE declaracion_planta_destino ADD COLUMN resumen_documento TEXT NULL;
-- Columnas existentes que podrían ser nullable (ajustar si ya existen):
-- ALTER TABLE declaracion_planta_destino MODIFY COLUMN nombre_destino VARCHAR(100) NULL;
-- ALTER TABLE declaracion_planta_destino MODIFY COLUMN rut_destino VARCHAR(20) NULL;
-- ALTER TABLE declaracion_planta_destino MODIFY COLUMN folio_origen VARCHAR(50) NULL;
-- ALTER TABLE declaracion_planta_destino MODIFY COLUMN folio_declaracion_destino VARCHAR(50) NULL;

