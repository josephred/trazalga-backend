package com.trazalga.api.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.Date;

/**
 * DTO para los resultados de la búsqueda puntual por folio, patente o documento (T2.2).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultadoFolioDTO {
    private String tipo;            // RECOLECTOR, ARMADOR, AREA, COMERCIALIZADOR, PLANTA_ABASTECIMIENTO
    private Long id;
    private String campo;           // Folio DA, Folio RO, Folio AMERB, Folio AC, Folio DAPLA, Folio Origen, Doc. tributario, Código de embarcación, Patente...
    private String valor;
    private Date fecha;
    private String actor;
    private String comunaOCaleta;
    private String especie;
    private BigDecimal kg;
    private String estado;
}
