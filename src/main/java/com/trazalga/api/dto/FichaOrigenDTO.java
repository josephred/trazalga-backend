package com.trazalga.api.dto;

import com.trazalga.api.dto.geo.GeoDeclaracionDTO;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FichaOrigenDTO {
    private Long id;
    private String tipo; // RECOLECTOR, ARMADOR, AREA
    private boolean consultada;
    private boolean mismaCarga;

    // Quién extrajo
    private String rut;
    private String nombre;
    private String perfil;

    // Si es armador
    private String embarcacionNombre;
    private String embarcacionCodigo; // RPA
    private String buzoNombre;
    private String buzoCodigo;

    // Método y especie
    private String metodoExtraccion;
    /** Refinamiento 23-10: método cuya cuota recibe la captura por imputación cruzada por veda (nulo = el declarado). */
    private String metodoImputacionCuota;
    private String especie;
    private String estadoHumedad;
    private String humedadHigrometro;

    // Ubicación
    private String caleta;
    private String comuna;
    private String region;
    private String varaderoOAmerb;

    // Fechas y cantidades
    private Date fechaExtraccion;
    private Date fechaDeclaracion;
    private String hora;
    private BigDecimal desembarqueKg;
    private BigDecimal capturaKg;

    // Folios
    private String folioOrigen;
    private String folioDesembarque; // RO, DA o AMERB

    // Estado, Marcas y Retenciones
    private String estado;
    @Builder.Default
    private List<FichaMarcaDTO> marcas = new ArrayList<>();
    private boolean retenida;
    private String motivoBloqueo;

    // TG.3: Doble georreferenciación
    private GeoDeclaracionDTO geo;

    // TH.5: Coherencia de humedad según edad desde extracción (Res. 3602)
    private Double edadExtraccionHoras;
    private String estadoEsperadoHumedad;
    private String resultadoCoherenciaHumedad;
}
