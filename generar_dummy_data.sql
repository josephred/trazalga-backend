DROP PROCEDURE IF EXISTS sp_generar_dummy_declaraciones;

DELIMITER //

CREATE PROCEDURE sp_generar_dummy_declaraciones(IN p_start_date DATE, IN p_end_date DATE)
BEGIN
    DECLARE v_current_date DATE;
    DECLARE v_recolector_count INT;
    DECLARE v_armador_count INT;
    DECLARE v_area_count INT;
    DECLARE i INT;
    
    DECLARE v_usuario_id BIGINT;
    DECLARE v_usuario_dest_id BIGINT;
    DECLARE v_caleta_id BIGINT;
    DECLARE v_comuna_id BIGINT;
    DECLARE v_especie_id BIGINT;
    DECLARE v_extraccion_tipo_id BIGINT;
    DECLARE v_composicion_id BIGINT;
    DECLARE v_humedad_id BIGINT;
    DECLARE v_embarcacion_id BIGINT;
    DECLARE v_buzo_id BIGINT;
    DECLARE v_amerb_id BIGINT;

    SET v_current_date = p_start_date;
    
    WHILE v_current_date <= p_end_date DO
        
        -- 1. Insertar declaraciones de RECOLECTOR
        SET v_recolector_count = FLOOR(5 + (RAND() * 46));
        SET i = 1;
        WHILE i <= v_recolector_count DO
            SELECT id INTO v_usuario_id FROM usuario ORDER BY RAND() LIMIT 1;
            SELECT id INTO v_usuario_dest_id FROM usuario ORDER BY RAND() LIMIT 1;
            SELECT id INTO v_caleta_id FROM caleta ORDER BY RAND() LIMIT 1;
            SELECT id INTO v_comuna_id FROM comuna ORDER BY RAND() LIMIT 1;
            SELECT id INTO v_especie_id FROM especie ORDER BY RAND() LIMIT 1;
            SELECT id INTO v_extraccion_tipo_id FROM extraccion_tipo ORDER BY RAND() LIMIT 1;
            SELECT id INTO v_composicion_id FROM composicion ORDER BY RAND() LIMIT 1;
            SELECT id INTO v_humedad_id FROM humedad_estado ORDER BY RAND() LIMIT 1;
            
            SET v_usuario_id = COALESCE(v_usuario_id, 1);
            SET v_usuario_dest_id = COALESCE(v_usuario_dest_id, 1);
            SET v_caleta_id = COALESCE(v_caleta_id, 1);
            SET v_comuna_id = COALESCE(v_comuna_id, 1);
            SET v_especie_id = COALESCE(v_especie_id, 1);
            SET v_extraccion_tipo_id = COALESCE(v_extraccion_tipo_id, 1);
            SET v_composicion_id = COALESCE(v_composicion_id, 1);
            SET v_humedad_id = COALESCE(v_humedad_id, 1);
            
            INSERT INTO declaracion_recolector (
                usuario_id, folio_origen, fecha_extraccion, fecha_declaracion, hora, 
                nombre, codigo_sernapesca, caleta_id, comuna_id, especie_id, extraccion_tipo_id, composicion_id, humedad_estado_id,
                desembarque, captura, codigo_destinatario, nombre_destinatario, usuario_destinatario_id
            ) VALUES (
                v_usuario_id, CONCAT('FOL-REC-', FLOOR(RAND()*100000)), v_current_date, v_current_date, '10:00:00',
                'Recolector Dummy', 'RPA-123', v_caleta_id, v_comuna_id, v_especie_id, v_extraccion_tipo_id, v_composicion_id, v_humedad_id,
                FLOOR(100 + (RAND() * 900)), FLOOR(100 + (RAND() * 900)), 'RUT-DEST', 'Comercializadora Dummy', v_usuario_dest_id
            );
            SET i = i + 1;
        END WHILE;

        -- 2. Insertar declaraciones de ARMADOR
        SET v_armador_count = FLOOR(5 + (RAND() * 46));
        SET i = 1;
        WHILE i <= v_armador_count DO
            SELECT id INTO v_usuario_id FROM usuario ORDER BY RAND() LIMIT 1;
            SELECT id INTO v_usuario_dest_id FROM usuario ORDER BY RAND() LIMIT 1;
            SELECT id INTO v_caleta_id FROM caleta ORDER BY RAND() LIMIT 1;
            SELECT id INTO v_especie_id FROM especie ORDER BY RAND() LIMIT 1;
            SELECT id INTO v_composicion_id FROM composicion ORDER BY RAND() LIMIT 1;
            SELECT id INTO v_humedad_id FROM humedad_estado ORDER BY RAND() LIMIT 1;
            SELECT id INTO v_embarcacion_id FROM embarcacion ORDER BY RAND() LIMIT 1;
            SELECT id INTO v_buzo_id FROM buzo ORDER BY RAND() LIMIT 1;

            SET v_usuario_id = COALESCE(v_usuario_id, 1);
            SET v_usuario_dest_id = COALESCE(v_usuario_dest_id, 1);
            SET v_caleta_id = COALESCE(v_caleta_id, 1);
            SET v_especie_id = COALESCE(v_especie_id, 1);
            SET v_composicion_id = COALESCE(v_composicion_id, 1);
            SET v_humedad_id = COALESCE(v_humedad_id, 1);
            SET v_embarcacion_id = COALESCE(v_embarcacion_id, 1);
            SET v_buzo_id = COALESCE(v_buzo_id, 1);

            INSERT INTO declaracion_armador (
                usuario_id, folio_origen, fecha_extraccion, fecha_declaracion, hora,
                embarcacion_id, buzo_id, desembarque, captura, tipo_destinatario, usuario_destinatario_id,
                caleta_id, especie_id, composicion_id, humedad_estado_id
            ) VALUES (
                v_usuario_id, CONCAT('FOL-ARM-', FLOOR(RAND()*100000)), v_current_date, v_current_date, '11:00:00',
                v_embarcacion_id, v_buzo_id, FLOOR(500 + (RAND() * 1500)), FLOOR(500 + (RAND() * 1500)), 'comercializador', v_usuario_dest_id,
                v_caleta_id, v_especie_id, v_composicion_id, v_humedad_id
            );
            SET i = i + 1;
        END WHILE;

        -- 3. Insertar declaraciones de AREA
        SET v_area_count = FLOOR(5 + (RAND() * 46));
        SET i = 1;
        WHILE i <= v_area_count DO
            SELECT id INTO v_usuario_id FROM usuario ORDER BY RAND() LIMIT 1;
            SELECT id INTO v_usuario_dest_id FROM usuario ORDER BY RAND() LIMIT 1;
            SELECT id INTO v_caleta_id FROM caleta ORDER BY RAND() LIMIT 1;
            SELECT id INTO v_especie_id FROM especie ORDER BY RAND() LIMIT 1;
            SELECT id INTO v_composicion_id FROM composicion ORDER BY RAND() LIMIT 1;
            SELECT id INTO v_humedad_id FROM humedad_estado ORDER BY RAND() LIMIT 1;
            SELECT id INTO v_amerb_id FROM amerb ORDER BY RAND() LIMIT 1;

            SET v_usuario_id = COALESCE(v_usuario_id, 1);
            SET v_usuario_dest_id = COALESCE(v_usuario_dest_id, 1);
            SET v_caleta_id = COALESCE(v_caleta_id, 1);
            SET v_especie_id = COALESCE(v_especie_id, 1);
            SET v_composicion_id = COALESCE(v_composicion_id, 1);
            SET v_humedad_id = COALESCE(v_humedad_id, 1);
            SET v_amerb_id = COALESCE(v_amerb_id, 1);

            INSERT INTO declaracion_area (
                usuario_id, folio_origen, fecha_extraccion, fecha_declaracion, hora,
                amerb_id, caleta_id, especie_id, captura, desembarque,
                tipo_destinatario, usuario_destinatario_id, composicion_id, humedad_estado_id
            ) VALUES (
                v_usuario_id, CONCAT('FOL-ARE-', FLOOR(RAND()*100000)), v_current_date, v_current_date, '12:00:00',
                v_amerb_id, v_caleta_id, v_especie_id, FLOOR(1000 + (RAND() * 2000)), FLOOR(1000 + (RAND() * 2000)),
                'planta', v_usuario_dest_id, v_composicion_id, v_humedad_id
            );
            SET i = i + 1;
        END WHILE;

        SET v_current_date = DATE_ADD(v_current_date, INTERVAL 1 DAY);
    END WHILE;
END //

DELIMITER ;
