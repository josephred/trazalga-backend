package com.trazalga.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidatoFusionDTO {
    private Long idA;
    private String nombreCanonicoA;
    private String rutA;
    private String estadoA;
    private Long comercializadorRefA;

    private Long idB;
    private String nombreCanonicoB;
    private String rutB;
    private String estadoB;
    private Long comercializadorRefB;

    private double similitud;
    private String motivoCandidatura; // SIMILITUD_JARO_WINKLER, MISMO_NOMBRE_RUT_DISTINTO, MISMO_NOMBRE_CON_Y_SIN_RUT
}
