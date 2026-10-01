-- ============================================================================
-- MIGRACIÓN: BLOQUE 0 HABILITANTES (T0.1 y T0.3)
-- Ref: PLAN_ANTIGRAVITY_refinamiento_25sep.md / 00_BLOQUE_HABILITANTES.md
-- Idempotente: Guardas information_schema y versionado de datos legales
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Asegurar columna motivo en configuracion_auditoria
-- ----------------------------------------------------------------------------
SET @col_motivo = (SELECT COUNT(*) FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'configuracion_auditoria' AND COLUMN_NAME = 'motivo');

SET @sql_motivo = IF(@col_motivo = 0,
    'ALTER TABLE configuracion_auditoria ADD COLUMN motivo VARCHAR(500) NULL AFTER valor_nuevo;',
    'SELECT "Columna motivo ya existe en configuracion_auditoria";');

PREPARE stmt_motivo FROM @sql_motivo;
EXECUTE stmt_motivo;
DEALLOCATE PREPARE stmt_motivo;

-- ----------------------------------------------------------------------------
-- 2. T0.1: Desactivar bloqueante de recepción en planta (variacion_peso_exige_voucher)
--    Ventana de transición hasta publicación e instalación de APK con romana (T6.1)
-- ----------------------------------------------------------------------------
UPDATE configuracion_general
SET valor = 'false'
WHERE clave = 'variacion_peso_exige_voucher';

-- Registrar en auditoría de parámetros la transición con motivo explícito
INSERT INTO configuracion_auditoria (entidad, entidad_id, campo, valor_anterior, valor_nuevo, motivo, usuario_id, created_at)
SELECT 'CONFIGURACION_GENERAL', 'variacion_peso_exige_voucher', 'valor', 'true', 'false',
       'Transición hasta publicación de APK con campos de romana (T0.1 del plan 25-sep)', NULL, NOW()
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM configuracion_auditoria 
    WHERE entidad = 'CONFIGURACION_GENERAL' 
      AND entidad_id = 'variacion_peso_exige_voucher' 
      AND valor_nuevo = 'false'
);

-- ----------------------------------------------------------------------------
-- 3. T0.3: Reglas LED en modo alerta (ALERTA_FISCALIZACION)
--    Cerrar reglas existentes con BLOQUEO por vigencia (versionado legal, no sobrescritura)
-- ----------------------------------------------------------------------------
-- 3.1 Cerrar reglas activas que tengan modo BLOQUEO o BLOQUEO_DECLARACION
UPDATE limite_extraccion_diario_config
SET activo = false, vigencia_fin = CURDATE(), updated_at = NOW()
WHERE activo = true AND (modo_accion = 'BLOQUEO_DECLARACION' OR modo_accion = 'BLOQUEO');

-- 3.2 Si existía una regla oficial Huiro Palo cerrada o no existe regla activa en ALERTA_FISCALIZACION, sembrar regla en ALERTA_FISCALIZACION
INSERT INTO limite_extraccion_diario_config 
    (nombre_regla, especie_id, extraccion_tipo_id, region_id, macrozona_id, perfil_aplicable, unidad_agregacion, metrica, limite_kg, margen_tolerancia_pct, modo_accion, vigencia_inicio, vigencia_fin, activo, created_at, updated_at)
SELECT 
    'LED Oficial Huiro Palo Barreteado',
    e.id,
    ext.id,
    NULL,
    NULL,
    'ARMADOR',
    'EMBARCACION',
    'DESEMBARQUE',
    2000.00,
    0.00,
    'ALERTA_FISCALIZACION',
    CURDATE(),
    NULL,
    true,
    NOW(),
    NOW()
FROM especie e
JOIN extraccion_tipo ext ON LOWER(ext.nombre) LIKE '%barreteado%'
WHERE LOWER(e.nombre) LIKE '%huiro palo%'
  AND NOT EXISTS (
      SELECT 1 FROM limite_extraccion_diario_config r 
      WHERE r.activo = true 
        AND r.modo_accion = 'ALERTA_FISCALIZACION'
        AND r.nombre_regla = 'LED Oficial Huiro Palo Barreteado'
  )
LIMIT 1;

-- ----------------------------------------------------------------------------
-- 4. Verificación de estado final de Bloque 0
-- ----------------------------------------------------------------------------
SELECT clave, valor, descripcion FROM configuracion_general WHERE clave = 'variacion_peso_exige_voucher';
SELECT id, entidad, entidad_id, campo, valor_anterior, valor_nuevo, motivo, created_at FROM configuracion_auditoria WHERE entidad_id = 'variacion_peso_exige_voucher' ORDER BY created_at DESC LIMIT 1;
SELECT id, nombre_regla, modo_accion, activo, vigencia_inicio, vigencia_fin FROM limite_extraccion_diario_config WHERE activo = true;
