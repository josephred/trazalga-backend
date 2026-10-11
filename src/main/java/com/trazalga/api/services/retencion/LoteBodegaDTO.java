package com.trazalga.api.services.retencion;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representación canónica de un lote evaluado en bodega virtual (TR.1, TR.2).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoteBodegaDTO {
    private Long id;
    private String tipo;
    private String folio;
    private Long especieId;
    private String especie;
    private String estadoDeclarado;
    private String tramoReal;
    private Double kg;
    private Date fechaDeclaracion;
    private String horaDeclaracion;
    private Double horasTranscurridas;
    private Integer diasTranscurridos;
    private Integer plazoMaxHoras;
    private String semaforo;
    private Boolean enBodega;
    private Boolean destinado;
    private Boolean inconsistenciaHumedad;
    private Long comercializadorId;
    private String actorComercializador;
    private String rutComercializador;
    private Long origenId;
    private String actorOrigen;
    private String rutOrigen;
    private String embarcacion;
    private Boolean bloqueada;
    private String motivoBloqueo;
    private Long marcaId;
    private String marcasActivas;
}
