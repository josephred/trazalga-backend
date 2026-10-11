package com.trazalga.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * TV.1 · Value Object y DTO con el resultado canónico de Variación de Peso Comercializador → Planta.
 * Contiene la evaluación completa entre despachos declarados y romana física neta.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultadoVariacionPesoDTO {
    private Long plantaId;
    private String folioPlanta;
    private String voucherRomanaNumero;
    private String voucherRomanaAdjunto;
    private Date fechaPesaje;
    private Date fechaIngresoPlanta;
    private String hora;
    private String plantaNombre;
    private String plantaRut;
    private Long especieId;
    private String especieNombre;

    // Tramo de abastecimiento: COMERCIALIZADOR_PLANTA | DIRECTO | MIXTO
    private String tramo;

    // Kilos físicos
    private double kgDespacho;
    private double kgRomana;
    private double deltaKg;
    private double deltaPct;

    // Tiempos de tránsito
    private double horasTransitoPromedio;
    private Double horasTransitoMin;
    private Double horasTransitoMax;
    private double diasTransito;

    // Humedad y factores
    private Long humedadDespachoId;
    private String humedadDespachoNombre;
    private Long humedadRecepcionId;
    private String humedadRecepcionNombre;
    private boolean humedadVerificada;

    // Kilos equivalentes de captura (si aplica)
    private Double capturaDespachoEq;
    private Double capturaRecepcionEq;
    private Double deltaEqPct;

    // Evaluación biológica (TV.2)
    private String semaforo; // VERDE | AMARILLO | ROJO
    private String criterio; // GANANCIA_NO_EXPLICADA | HUMEDO_SIN_MERMA | DESHIDRATACION_COHERENTE | ...
    private String descripcion;
    private boolean alerta;

    // Despachos y orígenes
    private List<DespachoVariacionDTO> despachos;
    private List<OrigenInformativoDTO> origenes;
}
