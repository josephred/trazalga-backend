package com.trazalga.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportDTO {
    private Long id;
    private String folio;
    private Date fecha;
    private String hora;
    private String emisorNombre;
    private String emisorRut;
    private String receptorNombre;
    private String receptorRut;
    private BigDecimal cantidad;
    private String especie;
    private String tipoReporte;
    private String folioRelacionado; // Para el caso recolector -> comercializador
}
