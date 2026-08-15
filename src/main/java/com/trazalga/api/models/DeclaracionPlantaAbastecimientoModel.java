package com.trazalga.api.models;

import java.math.BigDecimal;
import java.util.Date;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;

@Entity
@Table(name = "declaracion_planta_abastecimiento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class DeclaracionPlantaAbastecimientoModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Usuario que representa la planta que realiza la declaración (El que recibe)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private UsuarioModel usuario;

    @Column(nullable = true, length = 50)
    private String folioOrigen;

    @Column(nullable = true, length = 50)
    private String folioDeclaracionAPla; // A-PLA

    @Temporal(TemporalType.DATE)
    @Column(nullable = false)
    private Date fechaIngresoPlanta;

    @Temporal(TemporalType.DATE)
    @Column(nullable = true)
    private Date fechaTraslado;

    @Column(nullable = false, length = 8)
    private String hora;

    // Nombre de la planta
    @Column(name = "nombre_planta", nullable = true)
    private String nombrePlanta;

    // Código de la planta
    @Column(name = "codigo_sernapesca", nullable = true, length = 50)
    private String codigoSernapesca;

    // Georreferencia estandarizada
    @Column(nullable = true)
    private Double latitud;

    @Column(nullable = true)
    private Double longitud;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "especie_id", nullable = false)
    private EspecieModel especie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "composicion_id", nullable = true)
    private ComposicionModel composicion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "humedad_estado_id", nullable = false)
    private HumedadEstadoModel humedadEstado;

    @Column(name = "humedad_higrometro", precision = 5, scale = 2)
    private BigDecimal humedadHigrometro;

    // MEJORA: BigDecimal para precisión en el pesaje
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal cantidad;

    // --- Documentos Tributarios (Origen y Destino) ---
    private String documentoTributarioOrigenTipo;
    private String documentoTributarioOrigenNumero;
    @Temporal(TemporalType.DATE)
    private Date documentoTributarioOrigenFecha;

    private String documentoTributarioDestinoTipo;
    private String documentoTributarioDestinoNumero;
    @Temporal(TemporalType.DATE)
    private Date documentoTributarioDestinoFecha;

    // Campos retrocompatibles de documento tributario
    @Column(nullable = true)
    private String documentoTributarioTipo;

    @Column(nullable = true)
    private String documentoTributarioNumero;

    @Temporal(TemporalType.DATE)
    @Column(nullable = true)
    private Date documentoTributarioFecha;

    // --- Datos de Transporte ---
    private String vehiculoTransporte;
    private String choferTransporte;
    private String patente;

    @Column(name = "rut_chofer")
    private String rutChofer;

    @Column(name = "placa_patente")
    private String placaPatente;

    @Column(name = "placa_patente_carro")
    private String placaPatenteCarro;

    // Relación con el usuario destinatario (Comercializador, Planta, etc.)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_destinatario_id", nullable = true)
    private UsuarioModel usuarioDestinatario;

    @Column(name = "declaracion_destinatario_id", nullable = true)
    private Long declaracionDestinatario;

    @Column(name = "consumida_por_tipo", length = 50)
    private String consumidaPorTipo;

    @Column(name = "peso_recepcionado", nullable = true)
    private Double pesoRecepcionado;

    @Column(nullable = true, length = 20)
    private String estado;

    @Column(name = "declaraciones_seleccionadas", length = 1000)
    private String declaracionesSeleccionadas;

    @Column(name = "resumen_documento", columnDefinition = "TEXT")
    private String resumenDocumento;
}