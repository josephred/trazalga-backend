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
public class FichaComercializadorDTO {
    private Long id;
    private int salto;
    private boolean despachado;

    // A quién se vendió
    private String rut;
    private String nombre;

    // Recepción
    private Date fechaRecepcion;
    private String horaRecepcion;
    private String notaRecepcion; // "fecha de la declaración de origen que lo nombra destinatario"

    // Despacho y traslado
    private Date fechaDespacho;
    private String horaDespacho;
    private Date fechaTraslado;

    // Tiempo en bodega y semáforo Res. 3602
    private Double horasEnBodega;
    private String semaforo; // VERDE, AMARILLO, ROJO
    private Integer plazoMaxHoras;

    // Cantidad y transporte
    private BigDecimal cantidadKg;
    private String patenteCamion;
    private String patenteCarro;
    private String chofer;
    private String rutChofer;

    // Documentos tributarios
    private String docOrigenTipo;
    private String docOrigenNumero;
    private Date docOrigenFecha;

    private String docDestinoTipo;
    private String docDestinoNumero;
    private Date docDestinoFecha;

    // Folios
    private String folioOrigen;
    private String folioDesembarqueAc;

    // Estado, Marcas y Retenciones
    private String estado;
    @Builder.Default
    private List<FichaMarcaDTO> marcas = new ArrayList<>();
    private boolean retenida;
    private String motivoBloqueo;
}
