-- =========================================================================
-- MIGRACIÓN: migration_indices_folio.sql
-- TrazAlga - Prioridad 2 / T2.2 Búsqueda por Folio
-- Índices para optimización de consultas por folios y documentos
-- Idempotente conforme al estándar de ingeniería Trazalga
-- =========================================================================

-- 1. declaracion_recolector
-- Índice: folio_origen
SET @idx1 = (SELECT COUNT(*) FROM information_schema.STATISTICS 
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_recolector' AND INDEX_NAME = 'idx_recolector_folio_origen');
SET @s1 = IF(@idx1 = 0, 'CREATE INDEX idx_recolector_folio_origen ON declaracion_recolector (folio_origen(50))', 'SELECT 1');
PREPARE stmt1 FROM @s1; EXECUTE stmt1; DEALLOCATE PREPARE stmt1;

-- Índice: folio_desembarque_ro
SET @idx2 = (SELECT COUNT(*) FROM information_schema.STATISTICS 
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_recolector' AND INDEX_NAME = 'idx_recolector_folio_desemb_ro');
SET @s2 = IF(@idx2 = 0, 'CREATE INDEX idx_recolector_folio_desemb_ro ON declaracion_recolector (folio_desembarque_ro(50))', 'SELECT 1');
PREPARE stmt2 FROM @s2; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;

-- 2. declaracion_armador
-- Índice: folio_origen
SET @idx3 = (SELECT COUNT(*) FROM information_schema.STATISTICS 
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_armador' AND INDEX_NAME = 'idx_armador_folio_origen');
SET @s3 = IF(@idx3 = 0, 'CREATE INDEX idx_armador_folio_origen ON declaracion_armador (folio_origen(50))', 'SELECT 1');
PREPARE stmt3 FROM @s3; EXECUTE stmt3; DEALLOCATE PREPARE stmt3;

-- Índice: folio_desembarque_da
SET @idx4 = (SELECT COUNT(*) FROM information_schema.STATISTICS 
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_armador' AND INDEX_NAME = 'idx_armador_folio_desemb_da');
SET @s4 = IF(@idx4 = 0, 'CREATE INDEX idx_armador_folio_desemb_da ON declaracion_armador (folio_desembarque_da(50))', 'SELECT 1');
PREPARE stmt4 FROM @s4; EXECUTE stmt4; DEALLOCATE PREPARE stmt4;

-- 3. declaracion_area
-- Índice: folio_origen
SET @idx5 = (SELECT COUNT(*) FROM information_schema.STATISTICS 
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_area' AND INDEX_NAME = 'idx_area_folio_origen');
SET @s5 = IF(@idx5 = 0, 'CREATE INDEX idx_area_folio_origen ON declaracion_area (folio_origen(50))', 'SELECT 1');
PREPARE stmt5 FROM @s5; EXECUTE stmt5; DEALLOCATE PREPARE stmt5;

-- Índice: folio_desembarque_amerb
SET @idx6 = (SELECT COUNT(*) FROM information_schema.STATISTICS 
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_area' AND INDEX_NAME = 'idx_area_folio_desemb_amerb');
SET @s6 = IF(@idx6 = 0, 'CREATE INDEX idx_area_folio_desemb_amerb ON declaracion_area (folio_desembarque_amerb(50))', 'SELECT 1');
PREPARE stmt6 FROM @s6; EXECUTE stmt6; DEALLOCATE PREPARE stmt6;

-- 4. declaracion_comercializador
-- Índice: folio_origen
SET @idx7 = (SELECT COUNT(*) FROM information_schema.STATISTICS 
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_comercializador' AND INDEX_NAME = 'idx_comercializador_folio_origen');
SET @s7 = IF(@idx7 = 0, 'CREATE INDEX idx_comercializador_folio_origen ON declaracion_comercializador (folio_origen(50))', 'SELECT 1');
PREPARE stmt7 FROM @s7; EXECUTE stmt7; DEALLOCATE PREPARE stmt7;

-- Índice: folio_desembarque_ac
SET @idx8 = (SELECT COUNT(*) FROM information_schema.STATISTICS 
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_comercializador' AND INDEX_NAME = 'idx_comercializador_folio_desemb_ac');
SET @s8 = IF(@idx8 = 0, 'CREATE INDEX idx_comercializador_folio_desemb_ac ON declaracion_comercializador (folio_desembarque_ac(50))', 'SELECT 1');
PREPARE stmt8 FROM @s8; EXECUTE stmt8; DEALLOCATE PREPARE stmt8;

-- Índice: documento_tributario_origen_numero
SET @idx9 = (SELECT COUNT(*) FROM information_schema.STATISTICS 
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_comercializador' AND INDEX_NAME = 'idx_comercializador_doc_orig');
SET @s9 = IF(@idx9 = 0, 'CREATE INDEX idx_comercializador_doc_orig ON declaracion_comercializador (documento_tributario_origen_numero(50))', 'SELECT 1');
PREPARE stmt9 FROM @s9; EXECUTE stmt9; DEALLOCATE PREPARE stmt9;

-- Índice: documento_tributario_destino_numero
SET @idx10 = (SELECT COUNT(*) FROM information_schema.STATISTICS 
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_comercializador' AND INDEX_NAME = 'idx_comercializador_doc_dest');
SET @s10 = IF(@idx10 = 0, 'CREATE INDEX idx_comercializador_doc_dest ON declaracion_comercializador (documento_tributario_destino_numero(50))', 'SELECT 1');
PREPARE stmt10 FROM @s10; EXECUTE stmt10; DEALLOCATE PREPARE stmt10;

-- 5. declaracion_planta_abastecimiento
-- Índice: folio_origen
SET @idx11 = (SELECT COUNT(*) FROM information_schema.STATISTICS 
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_planta_abastecimiento' AND INDEX_NAME = 'idx_planta_folio_origen');
SET @s11 = IF(@idx11 = 0, 'CREATE INDEX idx_planta_folio_origen ON declaracion_planta_abastecimiento (folio_origen(50))', 'SELECT 1');
PREPARE stmt11 FROM @s11; EXECUTE stmt11; DEALLOCATE PREPARE stmt11;

-- Índice: folio_declaracion_a_pla
SET @idx12 = (SELECT COUNT(*) FROM information_schema.STATISTICS 
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_planta_abastecimiento' AND INDEX_NAME = 'idx_planta_folio_a_pla');
SET @s12 = IF(@idx12 = 0, 'CREATE INDEX idx_planta_folio_a_pla ON declaracion_planta_abastecimiento (folio_declaracion_a_pla(50))', 'SELECT 1');
PREPARE stmt12 FROM @s12; EXECUTE stmt12; DEALLOCATE PREPARE stmt12;

-- Índice: documento_tributario_origen_numero
SET @idx13 = (SELECT COUNT(*) FROM information_schema.STATISTICS 
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_planta_abastecimiento' AND INDEX_NAME = 'idx_planta_doc_orig');
SET @s13 = IF(@idx13 = 0, 'CREATE INDEX idx_planta_doc_orig ON declaracion_planta_abastecimiento (documento_tributario_origen_numero(50))', 'SELECT 1');
PREPARE stmt13 FROM @s13; EXECUTE stmt13; DEALLOCATE PREPARE stmt13;

-- Índice: documento_tributario_destino_numero
SET @idx14 = (SELECT COUNT(*) FROM information_schema.STATISTICS 
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_planta_abastecimiento' AND INDEX_NAME = 'idx_planta_doc_dest');
SET @s14 = IF(@idx14 = 0, 'CREATE INDEX idx_planta_doc_dest ON declaracion_planta_abastecimiento (documento_tributario_destino_numero(50))', 'SELECT 1');
PREPARE stmt14 FROM @s14; EXECUTE stmt14; DEALLOCATE PREPARE stmt14;

-- Índice: documento_tributario_numero
SET @idx15 = (SELECT COUNT(*) FROM information_schema.STATISTICS 
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'declaracion_planta_abastecimiento' AND INDEX_NAME = 'idx_planta_doc_num');
SET @s15 = IF(@idx15 = 0, 'CREATE INDEX idx_planta_doc_num ON declaracion_planta_abastecimiento (documento_tributario_numero(50))', 'SELECT 1');
PREPARE stmt15 FROM @s15; EXECUTE stmt15; DEALLOCATE PREPARE stmt15;
