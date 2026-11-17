-- ============================================================================
-- Anexo 2 / Parte E-G-I: Parámetros nuevos de cierre de Fase 3
-- Refinamiento 16-11-2026 (ZT.2, ZC.2, ZC.3, ZC.5, ZC.9, ZP.1, ZP.2, ZP.3, ZS.3)
-- Idempotente para re-ejecución segura en MySQL 8.0+ sin sobrescribir valores
-- ya configurados por administración.
-- ============================================================================

SET NAMES utf8mb4;

INSERT INTO configuracion_general (clave, valor, descripcion, categoria)
SELECT 'declaracion_corrige_fecha_futura',
       'true',
       'Resta un día al crear declaraciones recibidas con fecha futura por desfase UTC en el teléfono (ZT.2)',
       'INTEGRIDAD'
WHERE NOT EXISTS (
    SELECT 1 FROM configuracion_general WHERE clave = 'declaracion_corrige_fecha_futura'
);

INSERT INTO configuracion_general (clave, valor, descripcion, categoria)
SELECT 'declaracion_tolerancia_reloj_min',
       '15',
       'Tolerancia en minutos para considerar futura la fecha/hora de una declaración al crearla (ZT.2)',
       'INTEGRIDAD'
WHERE NOT EXISTS (
    SELECT 1 FROM configuracion_general WHERE clave = 'declaracion_tolerancia_reloj_min'
);

INSERT INTO configuracion_general (clave, valor, descripcion, categoria)
SELECT 'patente_traslado_max_dias',
       '1',
       'Días máximos permitidos entre fecha de declaración y fecha de traslado en despachos (ZC.2 / D51; vacío = no valida)',
       'CADENA'
WHERE NOT EXISTS (
    SELECT 1 FROM configuracion_general WHERE clave = 'patente_traslado_max_dias'
);

INSERT INTO configuracion_general (clave, valor, descripcion, categoria)
SELECT 'patente_incluye_despacho_planta',
       'true',
       'Incluye declaraciones de destino de planta como movimientos candidatos en la consulta de patentes (ZC.3 / D52)',
       'CADENA'
WHERE NOT EXISTS (
    SELECT 1 FROM configuracion_general WHERE clave = 'patente_incluye_despacho_planta'
);

INSERT INTO configuracion_general (clave, valor, descripcion, categoria)
SELECT 'origen_directo_exige_patente',
       'false',
       'Exige ingresar placa patente cuando una declaración de origen tiene como destinatario directo una planta (ZC.9 / D53)',
       'CADENA'
WHERE NOT EXISTS (
    SELECT 1 FROM configuracion_general WHERE clave = 'origen_directo_exige_patente'
);

INSERT INTO configuracion_general (clave, valor, descripcion, categoria)
SELECT 'consulta_publica_registra_denuncia',
       'true',
       'Registra en consulta_patente_log los clics ciudadanos en Fono Denuncia o WhatsApp (ZC.5 / D54)',
       'CONSULTAS'
WHERE NOT EXISTS (
    SELECT 1 FROM configuracion_general WHERE clave = 'consulta_publica_registra_denuncia'
);

INSERT INTO configuracion_general (clave, valor, descripcion, categoria)
SELECT 'consulta_patente_log_retencion_dias',
       '365',
       'Días de retención de registros en consulta_patente_log antes de su depuración automática (vacío = no borra, ZC.5)',
       'CONSULTAS'
WHERE NOT EXISTS (
    SELECT 1 FROM configuracion_general WHERE clave = 'consulta_patente_log_retencion_dias'
);

INSERT INTO configuracion_general (clave, valor, descripcion, categoria)
SELECT 'produccion_exige_lotes',
       'true',
       'Exige seleccionar al menos un lote de abastecimiento al crear o modificar una declaración de producción (ZP.3 / D57)',
       'CADENA'
WHERE NOT EXISTS (
    SELECT 1 FROM configuracion_general WHERE clave = 'produccion_exige_lotes'
);

INSERT INTO configuracion_general (clave, valor, descripcion, categoria)
SELECT 'destino_exige_producciones',
       'true',
       'Exige seleccionar al menos una declaración de producción al crear o modificar un destino de planta (ZP.1)',
       'CADENA'
WHERE NOT EXISTS (
    SELECT 1 FROM configuracion_general WHERE clave = 'destino_exige_producciones'
);

INSERT INTO configuracion_general (clave, valor, descripcion, categoria)
SELECT 'destino_tolerancia_pct',
       '1',
       'Tolerancia porcentual máxima permitida sobre la suma de kilos de las producciones seleccionadas en un destino de planta (ZP.2 / D56)',
       'CADENA'
WHERE NOT EXISTS (
    SELECT 1 FROM configuracion_general WHERE clave = 'destino_tolerancia_pct'
);

INSERT INTO configuracion_general (clave, valor, descripcion, categoria)
SELECT 'destino_exige_mismo_formato',
       'true',
       'Exige que el formato del destino coincida con el formato de las producciones seleccionadas (ZP.2 / D56)',
       'CADENA'
WHERE NOT EXISTS (
    SELECT 1 FROM configuracion_general WHERE clave = 'destino_exige_mismo_formato'
);

INSERT INTO configuracion_general (clave, valor, descripcion, categoria)
SELECT 'declaracion_lectura_exige_dueno',
       'true',
       'Restringe la lectura de declaraciones al dueño, destinatario o roles de fiscalización (ZS.3)',
       'CADENA'
WHERE NOT EXISTS (
    SELECT 1 FROM configuracion_general WHERE clave = 'declaracion_lectura_exige_dueno'
);
