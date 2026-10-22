package com.trazalga.api.dto.geo;

/**
 * Los cinco estados de evaluación de una fila de la planilla de coordenadas (TG.1).
 */
public enum EstadoFilaCoordenada {
    CALZADA,         // Calce unívoco con registro maestro y lista para actualizar
    AMBIGUA,         // Múltiples registros maestros coinciden (ej. caleta homónima sin comuna)
    SIN_CALCE,       // No se encontró ningún registro maestro con ese código o nombre/comuna
    FUERA_DE_RANGO,  // Coordenada fuera de Chile continental (-56 a -17.4 lat, -76 a -66.4 lon) o datum no WGS84
    EN_CONFLICTO     // Coordenada actual tiene fuente MANUAL y difiere de la planilla. Requiere forzar=true
}
