package com.trazalga.api.dto;

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
public class FichaPlantaDTO {
    private Long id;
    private boolean recepcionada;
    private String mensajeEstado; // "Recepcionada en planta" o "Aún no recepcionada en planta"

    // Planta y ubicación
    private String nombrePlanta;
    private String codigoSernapesca;
    private String comuna;
    private String region;

    // Llegada y traslado
    private Date fechaLlegada; // fecha_ingreso_planta
    private String hora;
    private Date fechaTraslado;

    // Pesaje de entrada (Romana)
    private boolean conRomana;
    private BigDecimal pesoRomanaKg;
    private String numeroVoucherRomana;
    private Date fechaPesajeRomana;
    private BigDecimal cantidadDeclarada;
    private String rotuloPesaje; // "Pesaje en romana" o "Sin pesaje en romana"

    // Humedad y variación
    private String humedadEstadoRecepcion;
    private BigDecimal humedadHigrometro;
    private Double variacionPct; // ej: -8.0
    private String rotuloVariacion; // "Variación de la recepción completa"

    // Documentos tributarios y transporte
    private String docOrigenTipo;
    private String docOrigenNumero;
    private Date docOrigenFecha;

    private String docDestinoTipo;
    private String docDestinoNumero;
    private Date docDestinoFecha;

    private String docNumero;
    private String docTipo;
    private Date docFecha;

    private String patenteCamion;
    private String patenteCarro;

    // Folios
    private String folioOrigen;
    private String folioDeclaracionAPla;

    // Estado, Marcas y Retenciones
    private String estado;
    @Builder.Default
    private List<FichaMarcaDTO> marcas = new ArrayList<>();
    private boolean retenida;
    private String motivoBloqueo;
}
