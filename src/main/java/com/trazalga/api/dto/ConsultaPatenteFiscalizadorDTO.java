package com.trazalga.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * TN.4 · Respuesta exhaustiva de consulta por patente para perfil FISCALIZADOR, ADMIN o AUDITOR
 * ({@code GET /api/consultas/patentes/{patente}?desde=&hasta=}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsultaPatenteFiscalizadorDTO {

    private String patente;
    private EstadoActualDTO estadoActual;
    private VehiculoNominaDTO vehiculo;
    @Builder.Default
    private List<MovimientoPatenteDTO> movimientos = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EstadoActualDTO {
        private String estado; // AUTORIZADO, RETENIDO, FINALIZADO, VENCIDO, SIN_REGISTROS
        private LocalDateTime fechaHoraAutorizacion;
        private LocalDateTime fechaHoraSalida;
        private String comunaDestino;
        private String tipoAlga;
        private Long declaracionId;
        private String folioDespacho;
        private String motivoRetencion;
        private Long hallazgoId;
        private String hallazgoMarca;
        private Long plantaRecepcionId;
        private String nombrePlantaRecepcion;
        private LocalDateTime fechaHoraRecepcion;
        private LocalDateTime consultadoEn;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VehiculoNominaDTO {
        private Long id;
        private String patente;
        private String tipo; // CAMION, RAMPLA, CAMIONETA, OTRO
        private String descripcionDeclarada;
        private Date primeraVezEn;
        private Date ultimaVezEn;
        private int totalComercializadores;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MovimientoPatenteDTO {
        private String tipo; // COMERCIALIZADOR o PLANTA_ABASTECIMIENTO
        private Long id;
        private String folioOrigen;
        private String folioSecundario; // AC o A-PLA
        private Date fechaDeclaracion;
        private String horaDeclaracion;
        private Date fechaTraslado;
        private LocalDateTime fechaHoraSalida;

        // Comercializador / Emisor
        private String comercializadorRut;
        private String comercializadorNombre;

        // Chofer y nómina
        private String choferDeclarado;
        private String choferRut;
        private Long transportistaId;
        private String transportistaNombreCanonico;

        // Vehículo y patentes
        private String patenteCamion;
        private String patenteCarro;
        private String tipoVehiculo;

        // Documento tributario
        private String docTipo;
        private String docNumero;
        private Date docFecha;

        // Destino
        private String destinoNombre;
        private String destinoRut;
        private String destinoComuna;

        // Recurso
        private String especies;
        private String estadoHumedad;
        private BigDecimal kilos;

        // Estado del traslado
        private String estadoMovimiento; // EN_TRANSITO, RECIBIDO, RETENIDO
        private String motivoRetencion;

        // Recepción en planta (si corresponde)
        private Long recepcionPlantaId;
        private Date recepcionFecha;
        private String recepcionHora;
        private String recepcionPlantaNombre;
        private BigDecimal recepcionPesoRomanaKg;
        private String recepcionVoucherRomana;

        // Marcas y enlace a ficha
        @Builder.Default
        private List<FichaMarcaDTO> marcas = new ArrayList<>();
        private String enlaceFicha;
    }
}
