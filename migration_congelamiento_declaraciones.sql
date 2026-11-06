-- =============================================================================
-- Refinamiento 30-10 · punto 3: declaraciones congeladas por alerta de fiscalización
-- Idempotente. Con spring.jpa.hibernate.ddl-auto=update Hibernate crea la tabla al arrancar;
-- este script la deja igual en ambientes con ddl-auto=validate y siembra los parámetros.
-- =============================================================================

CREATE TABLE IF NOT EXISTS declaracion_autorizacion (
  id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
  declaracion_tipo        VARCHAR(30)  NOT NULL,      -- RECOLECTOR | ARMADOR | AREA
  declaracion_id          BIGINT       NOT NULL,
  tipo                    VARCHAR(20)  NOT NULL,      -- EDICION | ELIMINACION
  motivo                  VARCHAR(500) NOT NULL,
  acta_verificacion       VARCHAR(120) NOT NULL,      -- n.º o referencia del acta de verificación física
  autorizado_por_usuario_id BIGINT     NULL,
  autorizado_por_rut      VARCHAR(20)  NULL,
  autorizado_en           DATETIME(6)  NOT NULL,
  vence_en                DATETIME(6)  NULL,          -- EDICION: plazo para que el dueño edite
  usada_en                DATETIME(6)  NULL,          -- EDICION: se consume con la primera edición
  snapshot                TEXT         NULL,          -- ELIMINACION: declaración y marcas en JSON
  KEY ix_decl_aut_decl (declaracion_tipo, declaracion_id)
);

-- Parámetros (el backend también los crea al arrancar si no existen)
INSERT INTO configuracion_general (clave, valor, descripcion, categoria)
SELECT 'declaracion_congelar_activo', 'true',
       'Una declaración de origen con alerta de la lista no puede ser modificada ni eliminada por su dueño (refinamiento 30-10)', 'CADENA'
WHERE NOT EXISTS (SELECT 1 FROM configuracion_general WHERE clave = 'declaracion_congelar_activo');

INSERT INTO configuracion_general (clave, valor, descripcion, categoria)
SELECT 'declaracion_congelar_marcas', 'EN_VEDA,CUOTA_EXCEDIDA,POSTERIOR_CIERRE',
       'Marcas que congelan una declaración de recolector, armador o área (separadas por coma)', 'CADENA'
WHERE NOT EXISTS (SELECT 1 FROM configuracion_general WHERE clave = 'declaracion_congelar_marcas');

INSERT INTO configuracion_general (clave, valor, descripcion, categoria)
SELECT 'declaracion_autorizacion_horas', '48',
       'Horas que tiene el dueño para usar una autorización de edición dada por un fiscalizador', 'CADENA'
WHERE NOT EXISTS (SELECT 1 FROM configuracion_general WHERE clave = 'declaracion_autorizacion_horas');

INSERT INTO configuracion_general (clave, valor, descripcion, categoria)
SELECT 'declaracion_escritura_exige_sesion', 'false',
       'Exige sesión para modificar o eliminar declaraciones de origen. Encender cuando la app 2.5.0 esté instalada', 'CADENA'
WHERE NOT EXISTS (SELECT 1 FROM configuracion_general WHERE clave = 'declaracion_escritura_exige_sesion');
