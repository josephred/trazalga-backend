-- =============================================================================
-- Refinamiento 06-11 · punto 1 (NL.4, D20): acumulado diario del LED con declaraciones de área
-- Idempotente. El backend también crea el parámetro al arrancar si no existe.
-- =============================================================================

INSERT INTO configuracion_general (clave, valor, descripcion, categoria)
SELECT 'led_suma_declaraciones_area', 'true',
       'El acumulado diario del LED (evaluación en línea y monitoreo del panel) suma también las declaraciones de área de manejo de la misma embarcación, buzo o usuario (refinamiento 06-11)', 'CADENA'
WHERE NOT EXISTS (SELECT 1 FROM configuracion_general WHERE clave = 'led_suma_declaraciones_area');
