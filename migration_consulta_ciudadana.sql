-- ============================================================================
-- QC.1: Parámetros de consulta y denuncia ciudadana + índice en log de patentes
-- Refinamiento 13-11-2026 · Parte H (QC.1 / D37, D38, D39, D42)
-- Idempotente para re-ejecución segura en MySQL 8.0+ sin sobrescribir valores
-- ya configurados por administración.
-- ============================================================================

SET NAMES utf8mb4;
SET @db_name = DATABASE();

-- 1. Asegurar existencia de tabla consulta_patente_log (T10.1 / QC.4)
CREATE TABLE IF NOT EXISTS consulta_patente_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patente VARCHAR(20) NOT NULL,
    ip_hash VARCHAR(64) NOT NULL,
    fecha DATETIME(6) NOT NULL,
    resultado VARCHAR(20) NOT NULL,
    INDEX idx_patente_log_fecha (fecha),
    INDEX idx_patente_log_patente (patente)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Índice compuesto (fecha, resultado, patente) para bandeja de consultas ciudadanas (QC.4)
SET @idx_qc = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = @db_name
      AND table_name = 'consulta_patente_log'
      AND index_name = 'idx_patente_log_fecha_resultado_patente'
);
SET @sql_idx_qc = IF(
    @idx_qc = 0,
    'CREATE INDEX idx_patente_log_fecha_resultado_patente ON consulta_patente_log (fecha, resultado, patente)',
    'SELECT ''Indice idx_patente_log_fecha_resultado_patente ya existe'' AS info'
);
PREPARE stmt_idx_qc FROM @sql_idx_qc;
EXECUTE stmt_idx_qc;
DEALLOCATE PREPARE stmt_idx_qc;

-- 3. Parámetros configurables QC.1 en configuracion_general (sin sobreescribir si ya existen)

INSERT INTO configuracion_general (clave, valor, descripcion, categoria)
SELECT 'consulta_publica_texto_proteccion',
       'Las algas pardas son un recurso con medidas de protección. Su extracción y traslado requieren autorización de Sernapesca.',
       'Leyenda pública sobre protección del recurso expuesta en consulta ciudadana de patentes (vacío = oculta, D42)',
       'CONSULTAS'
WHERE NOT EXISTS (
    SELECT 1 FROM configuracion_general WHERE clave = 'consulta_publica_texto_proteccion'
);

INSERT INTO configuracion_general (clave, valor, descripcion, categoria)
SELECT 'consulta_publica_grafica',
       '',
       'Ruta relativa o URL pública de la gráfica informativa en consulta ciudadana de patentes (vacío = oculta)',
       'CONSULTAS'
WHERE NOT EXISTS (
    SELECT 1 FROM configuracion_general WHERE clave = 'consulta_publica_grafica'
);

INSERT INTO configuracion_general (clave, valor, descripcion, categoria)
SELECT 'consulta_publica_fono_denuncia',
       '',
       'Teléfono de Fono Denuncia expuesto en consulta pública cuando no hay autorización vigente (vacío = oculto, D38)',
       'CONSULTAS'
WHERE NOT EXISTS (
    SELECT 1 FROM configuracion_general WHERE clave = 'consulta_publica_fono_denuncia'
);

INSERT INTO configuracion_general (clave, valor, descripcion, categoria)
SELECT 'consulta_publica_whatsapp',
       '',
       'Número de WhatsApp (código país + número) para denuncia ciudadana sin autorización vigente (vacío = oculto, D38)',
       'CONSULTAS'
WHERE NOT EXISTS (
    SELECT 1 FROM configuracion_general WHERE clave = 'consulta_publica_whatsapp'
);

INSERT INTO configuracion_general (clave, valor, descripcion, categoria)
SELECT 'consulta_publica_muestra_comuna',
       'true',
       'Si es true, incluye comunaDestino en respuestas públicas AUTORIZADO; false la omite (D37)',
       'CONSULTAS'
WHERE NOT EXISTS (
    SELECT 1 FROM configuracion_general WHERE clave = 'consulta_publica_muestra_comuna'
);

INSERT INTO configuracion_general (clave, valor, descripcion, categoria)
SELECT 'consulta_ciudadana_resultados_anomalos',
       'VENCIDO,SIN_REGISTROS,RETENIDO',
       'Resultados de consulta ciudadana considerados anómalos para la bandeja de fiscalización (D39)',
       'CONSULTAS'
WHERE NOT EXISTS (
    SELECT 1 FROM configuracion_general WHERE clave = 'consulta_ciudadana_resultados_anomalos'
);
