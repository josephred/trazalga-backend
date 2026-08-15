package com.trazalga.api.models;

import java.math.BigDecimal;
import java.util.Date;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;

@Entity
@Table(name = "declaracion_planta_destino")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class DeclaracionPlantaDestinoModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Usuario que representa la planta que realiza la declaración (El remitente)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private UsuarioModel usuario;

    @Column(name = "folio_declaracion_abastecimiento_planta")
    private String folioDeclaracionAbastecimientoPlanta;

    @Column(nullable = true, length = 50)
    private String folioOrigen;

    @Column(nullable = true, length = 50)
    private String folioDeclaracionDestino;

    @Temporal(TemporalType.DATE)
    @Column(nullable = false)
    private Date fechaDeclaracionDestino;

    @Temporal(TemporalType.DATE)
    @Column(nullable = true)
    private Date fechaTrasladoDestino;

    @Temporal(TemporalType.DATE)
    @Column(nullable = true)
    private Date fechaTraslado;

    @Column(nullable = false, length = 8)
    private String hora;

    // --- Campos de Identificación de la Planta (Remitente) ---
    @Column(name = "nombre_planta", nullable = true)
    private String nombrePlanta;

    @Column(name = "codigo_sernapesca", nullable = true, length = 50)
    private String codigoSernapesca;

    // --- Georreferencia ---
    @Column(nullable = true)
    private Double latitud;

    @Column(nullable = true)
    private Double longitud;

    // --- Detalles del Producto ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "especie_id", nullable = false)
    private EspecieModel especie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private ProductoModel producto;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal cantidad;

    @Column(name = "humedad_higrometro", precision = 5, scale = 2)
    private BigDecimal humedadHigrometro;

    // --- Documentación Legal (Origen) ---
    private String documentoTributarioOrigenTipo;
    private String documentoTributarioOrigenNumero;
    @Temporal(TemporalType.DATE)
    private Date documentoTributarioOrigenFecha;

    // --- Documentación Legal (Destino) ---
    private String documentoTributarioTipo;
    private String documentoTributarioNumero;
    @Temporal(TemporalType.DATE)
    private Date documentoTributarioFecha;

    // --- Datos de Transporte ---
    private String vehiculoTransporte;
    private String choferTransporte;

    @Column(name = "rut_chofer")
    private String rutChofer;

    @Column(name = "placa_patente")
    private String placaPatente;

    @Column(name = "placa_patente_carro")
    private String placaPatenteCarro;

    // --- Datos del Destinatario (Cliente/Exportación) ---
    @Column(nullable = true, length = 100)
    private String nombreDestino;

    @Column(nullable = true, length = 20)
    private String rutDestino;

    @Column(nullable = true)
    private String codigoSernapescaDestino;

    // Relación opcional si el destinatario es usuario del sistema
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_destinatario_id", nullable = true)
    private UsuarioModel usuarioDestinatario;

    @Column(name = "declaracion_destinatario_id", nullable = true)
    private Long declaracionDestinatario;

    @Column(name = "consumida_por_tipo", length = 50)
    private String consumidaPorTipo;

    @Column(nullable = true, length = 20)
    private String estado;

    // --- Trazabilidad Inversa ---
    @Column(name = "declaraciones_seleccionadas", length = 1000)
    private String declaracionesSeleccionadas;

    @Column(name = "resumen_documento", columnDefinition = "TEXT")
    private String resumenDocumento;
}