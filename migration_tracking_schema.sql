-- ============================================================
-- Migración: Sistema de Registro y Traceabilidad de Ubicación
-- Tablas: configuracion_general, historial_ubicacion
-- ============================================================

CREATE TABLE IF NOT EXISTS configuracion_general (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    clave VARCHAR(100) NOT NULL UNIQUE,
    valor VARCHAR(255) NOT NULL,
    descripcion VARCHAR(255) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS historial_ubicacion (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    latitud DOUBLE NOT NULL,
    longitud DOUBLE NOT NULL,
    fecha_registro DATETIME NOT NULL,
    precision_gps DOUBLE NULL,
    velocidad DOUBLE NULL,
    offline BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE CASCADE,
    INDEX idx_usuario_fecha (usuario_id, fecha_registro)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Insertar configuraciones iniciales por defecto
INSERT INTO configuracion_general (clave, valor, descripcion) VALUES
('intervalo_rastreo_minutos', '5', 'Intervalo en minutos para el registro de ubicación del usuario en la app móvil'),
('rastreo_activo', 'true', 'Habilita o deshabilita globalmente el rastreo de ubicación en la aplicación móvil')
ON DUPLICATE KEY UPDATE valor = valor;
