-- =========================================================================
-- MIGRACIÓN: migration_indices_custodia.sql (TR.1, TV.1)
-- Optimización de índices para bodega, custodia y trazabilidad
-- Idempotente conforme al estándar de ingeniería TrazAlga
-- =========================================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS sp_crear_indice_si_no_existe $$
CREATE PROCEDURE sp_crear_indice_si_no_existe(
    IN p_tabla VARCHAR(64),
    IN p_indice VARCHAR(64),
    IN p_columnas VARCHAR(255)
)
BEGIN
    DECLARE v_existe INT DEFAULT 0;
    SELECT COUNT(*) INTO v_existe
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = p_tabla
      AND index_name = p_indice;

    IF v_existe = 0 THEN
        SET @sql = CONCAT('CREATE INDEX ', p_indice, ' ON ', p_tabla, ' (', p_columnas, ')');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

DELIMITER ;

-- 1. Índices (usuario_destinatario_id, declaracion_destinatario_id)
CALL sp_crear_indice_si_no_existe('declaracion_recolector', 'ix_dr_usr_decl_dest', 'usuario_destinatario_id, declaracion_destinatario_id');
CALL sp_crear_indice_si_no_existe('declaracion_armador', 'ix_da_usr_decl_dest', 'usuario_destinatario_id, declaracion_destinatario_id');
CALL sp_crear_indice_si_no_existe('declaracion_area', 'ix_dma_usr_decl_dest', 'usuario_destinatario_id, declaracion_destinatario_id');
CALL sp_crear_indice_si_no_existe('declaracion_comercializador', 'ix_dc_usr_decl_dest', 'usuario_destinatario_id, declaracion_destinatario_id');

-- 2. Índices (consumida_por_tipo, declaracion_destinatario_id)
CALL sp_crear_indice_si_no_existe('declaracion_recolector', 'ix_dr_tipo_decl_dest', 'consumida_por_tipo, declaracion_destinatario_id');
CALL sp_crear_indice_si_no_existe('declaracion_armador', 'ix_da_tipo_decl_dest', 'consumida_por_tipo, declaracion_destinatario_id');
CALL sp_crear_indice_si_no_existe('declaracion_area', 'ix_dma_tipo_decl_dest', 'consumida_por_tipo, declaracion_destinatario_id');
CALL sp_crear_indice_si_no_existe('declaracion_comercializador', 'ix_dc_tipo_decl_dest', 'consumida_por_tipo, declaracion_destinatario_id');

DROP PROCEDURE IF EXISTS sp_crear_indice_si_no_existe;
