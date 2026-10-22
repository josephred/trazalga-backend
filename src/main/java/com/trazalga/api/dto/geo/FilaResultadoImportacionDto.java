package com.trazalga.api.dto.geo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilaResultadoImportacionDto {
    private int numeroFila;
    private String tipo;           // CALETA, VARADERO, AMERB
    private String codigo;         // Código Sernapesca (AMERB)
    private String nombre;         // Nombre según planilla
    private String comuna;         // Comuna según planilla
    private String region;         // Región según planilla
    private String latitudRaw;     // Texto original de latitud
    private String longitudRaw;    // Texto original de longitud
    private String datum;          // Datum indicado
    private String fuente;         // Fuente indicada en la fila

    private Double latitudParsed;  // Latitud normalizada decimal
    private Double longitudParsed; // Longitud normalizada decimal

    private EstadoFilaCoordenada estado; // CALZADA, AMBIGUA, SIN_CALCE, FUERA_DE_RANGO, EN_CONFLICTO
    private String detalleEstado;        // Explicación humana del estado

    // Datos del registro maestro asociado (si hubo calce o conflicto)
    private Long entidadId;
    private String entidadNombre;
    private String entidadComuna;
    private String entidadRegion;
    private Double latitudActual;
    private Double longitudActual;
    private String fuenteActual;

    private boolean aplicada;            // true si fue efectivamente escrita en BD
}
