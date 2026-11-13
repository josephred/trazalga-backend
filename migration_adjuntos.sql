-- Migración idempotente NA.4: respaldo fotográfico/PDF de voucher de romana y guía de despacho en disco

CREATE TABLE IF NOT EXISTS adjunto (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(64) NOT NULL,
    nombre_original VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    tamano_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    ruta_relativa VARCHAR(255) NOT NULL,
    tipo_documento VARCHAR(40) NULL,
    subido_por_usuario_id BIGINT NULL,
    creado_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_adjunto_uuid (uuid),
    KEY idx_adjunto_sha256 (sha256)
);

SET @db = DATABASE();

SET @q = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=@db AND TABLE_NAME='adjunto' AND CONSTRAINT_NAME='fk_adjunto_usuario'),
    'SELECT ''FK fk_adjunto_usuario ya existe''',
    'ALTER TABLE adjunto ADD CONSTRAINT fk_adjunto_usuario FOREIGN KEY (subido_por_usuario_id) REFERENCES usuario(id)'
));
PREPARE s FROM @q; EXECUTE s; DEALLOCATE PREPARE s;

SET @q = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='declaracion_planta_abastecimiento' AND COLUMN_NAME='guia_despacho_adjunto'),
    'SELECT ''Columna guia_despacho_adjunto ya existe''',
    'ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN guia_despacho_adjunto VARCHAR(255) NULL'
));
PREPARE s FROM @q; EXECUTE s; DEALLOCATE PREPARE s;

SET @q = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='declaracion_planta_abastecimiento' AND COLUMN_NAME='voucher_romana_adjunto_id'),
    'SELECT ''Columna voucher_romana_adjunto_id ya existe''',
    'ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN voucher_romana_adjunto_id BIGINT NULL'
));
PREPARE s FROM @q; EXECUTE s; DEALLOCATE PREPARE s;

SET @q = (SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='declaracion_planta_abastecimiento' AND COLUMN_NAME='guia_despacho_adjunto_id'),
    'SELECT ''Columna guia_despacho_adjunto_id ya existe''',
    'ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN guia_despacho_adjunto_id BIGINT NULL'
));
PREPARE s FROM @q; EXECUTE s; DEALLOCATE PREPARE s;
