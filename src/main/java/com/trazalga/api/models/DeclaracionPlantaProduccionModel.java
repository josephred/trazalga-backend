package com.trazalga.api.models;

import java.math.BigDecimal;
import java.util.Date;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;

@Entity
@Table(name = "declaracion_planta_produccion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class DeclaracionPlantaProduccionModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Usuario que representa la planta (quien declara)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private UsuarioModel usuario;

    @Column(nullable = true, length = 50)
    private String folioOrigen;

    @Column(nullable = true, length = 50)
    private String folioDeclaracionPpla; // P-PLA

    @Temporal(TemporalType.DATE)
    @Column(nullable = false)
    private Date fechaProduccion;

    @Temporal(TemporalType.DATE)
    @Column(nullable = true)
    private Date fechaTraslado;

    @Column(nullable = false, length = 8)
    private String hora;

    // --- Campos de Identificación (Normativa) ---
    @Column(name = "nombre_planta", nullable = true)
    private String nombrePlanta;

    @Column(name = "codigo_sernapesca", nullable = true, length = 50)
    private String codigoSernapesca;

    // --- Georreferencia ---
    @Column(nullable = true)
    private Double latitud;

    @Column(nullable = true)
    private Double longitud;

    // --- ENTRADA (INPUT) ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "materia_prima_especie_id", nullable = false)
    private EspecieModel materiaPrimaEspecie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "materia_prima_producto_id", nullable = true)
    private ProductoModel materiaPrimaProducto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "humedad_estado_id", nullable = false)
    private HumedadEstadoModel humedadEstado;

    @Column(name = "humedad_higrometro", precision = 5, scale = 2)
    private BigDecimal humedadHigrometro;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal cantidadMateriaPrima;

    // --- SALIDA (OUTPUT) ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_resultante_id", nullable = false)
    private ProductoModel productoResultante;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal cantidadProducto;

    // --- Documentos Tributarios (Origen y Destino) ---
    private String documentoTributarioOrigenTipo;
    private String documentoTributarioOrigenNumero;
    @Temporal(TemporalType.DATE)
    private Date documentoTributarioOrigenFecha;

    private String documentoTributarioDestinoTipo;
    private String documentoTributarioDestinoNumero;
    @Temporal(TemporalType.DATE)
    private Date documentoTributarioDestinoFecha;

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

    // --- Trazabilidad ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_destinatario_id", nullable = true)
    private UsuarioModel usuarioDestinatario;

    @Column(name = "declaracion_destinatario_id", nullable = true)
    private Long declaracionDestinatario;

    @Column(name = "consumida_por_tipo", length = 50)
    private String consumidaPorTipo;

    @Column(nullable = true, length = 20)
    private String estado;

    @Column(name = "declaraciones_seleccionadas", length = 1000)
    private String declaracionesSeleccionadas;

    @Column(name = "resumen_documento", columnDefinition = "TEXT")
    private String resumenDocumento;
}