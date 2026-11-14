package com.trazalga.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * TV.3 · Parámetros de filtro para consultas de recepciones en planta y variación de peso.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiltroRecepcionesVariacion {
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date startDate;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date endDate;

    private String semaforo; // VERDE | AMARILLO | ROJO | TODOS
    private String tramo;    // COMERCIALIZADOR_PLANTA | DIRECTO | MIXTO | TODOS
    private Long especieId;
    private Long plantaId;
}
