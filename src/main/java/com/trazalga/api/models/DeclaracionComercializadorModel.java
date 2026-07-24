package com.trazalga.api.models;

import java.math.BigDecimal; // Importar BigDecimal
import java.util.Date;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;

@Entity
@Table(name = "declaracion_comercializador")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class DeclaracionComercializadorModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private UsuarioModel usuario;

    @Column(nullable = true)
    private String folioOrigen;

    @Column(nullable = true)
    private String folioDesembarqueAc;

    @Temporal(TemporalType.DATE)
    @Column(nullable = false)
    private Date fechaDeclaracion;

    // Fecha en que ocurre el traslado físico de la carga. Nullable: si no viene
    // (declaraciones antiguas o versiones previas de la app) se asume que el
    // movimiento ocurre al momento de la declaración, como hasta ahora.
    @Temporal(TemporalType.DATE)
    @Column(nullable = true)
    private Date fechaTraslado;

    @Column(nullable = false, length = 8)
    private String hora;

    @Column(nullable = true)
    private String codigoSernapesca;

    @Column(nullable = true)
    private String nombreComercializador;

    // MEJORA: Reemplazar 'georreferencia' por latitud y longitud para consistencia
    @Column(nullable = true)
    private Double latitud;

    @Column(nullable = true)
    private Double longitud;

    @ManyToOne
    @JoinColumn(name = "especie_id", nullable = false)
    private EspecieModel especie;

    @ManyToOne
    @JoinColumn(name = "composicion_id", nullable = true)
    private ComposicionModel composicion;

    @ManyToOne
    @JoinColumn(name = "humedad_estado_id", nullable = false)
    private HumedadEstadoModel humedadEstado;

    @Column(name = "humedad_higrometro", precision = 5, scale = 2)
    private BigDecimal humedadHigrometro;

    // MEJORA: Mantener BigDecimal es una buena práctica
    @Column(precision = 10, scale = 2) // Opcional: define precisión y escala para la DB
    private BigDecimal cantidad;

    // --- Documentos Tributarios (ya están correctos) ---
    private String documentoTributarioOrigenTipo;
    private String documentoTributarioOrigenNumero;
    @Temporal(TemporalType.DATE)
    private Date documentoTributarioOrigenFecha;

    private String documentoTributarioDestinoTipo;
    private String documentoTributarioDestinoNumero;
    @Temporal(TemporalType.DATE)
    private Date documentoTributarioDestinoFecha;

    // --- Datos de Transporte (ya están correctos) ---
    private String vehiculoTransporte;
    private String choferTransporte;
    private String patente;

    @Column(name = "rut_chofer")
    private String rutChofer;

    @Column(name = "placa_patente")
    private String placaPatente;

    @Column(name = "placa_patente_carro")
    private String placaPatenteCarro;

    // CAMPO FALTANTE AÑADIDO (RUT del destinatario)
    @Column(nullable = false, length = 20)
    private String codigoDestinatario;

    // CAMPO FALTANTE AÑADIDO (Nombre del destinatario, desnormalizado)
    @Column(nullable = false)
    private String nombreDestinatario;

    @ManyToOne
    @JoinColumn(name = "usuario_destinatario_id", nullable = false)
    private UsuarioModel usuarioDestinatario;

    // --- Campos de Trazabilidad (ya están correctos y son clave) ---
    @Column(name = "declaracion_destinatario_id")
    private Long declaracionDestinatario;

    @Column(name = "consumida_por_tipo", length = 50)
    private String consumidaPorTipo;

    // Peso verificado por el receptor al recibir esta declaración (kg).
    // Permite conciliar contra lo declarado en origen (indicador variación de peso).
    @Column(name = "peso_recepcionado", nullable = true)
    private Double pesoRecepcionado;

    /**
     * Estado del ciclo de negociación de la declaración:
     *  ENVIADA (default) -> NEGOCIACION (al primer mensaje de gestión)
     *  -> RECHAZADA (el destinatario la rechaza; sale de sus seleccionables)
     *  -> NEGOCIACION (el emisor la corrige y la re-envía).
     * El estado "consumida/procesada" NO vive aquí: se deriva de declaracionDestinatario.
     */
    @Column(nullable = true, length = 20)
    private String estado;

    @Column(name = "declaraciones_seleccionadas", length = 1000) // Aumentar longitud si pueden ser muchos IDs
    private String declaracionesSeleccionadas;

    // Snapshot (JSON) del resumen consolidado del documento (líneas por especie +
    // humedad + composición con sus totales), calculado y congelado al momento de
    // guardar/editar la declaración. Es un respaldo histórico: si más tarde se edita
    // o anula una declaración de origen que este documento consumió, este resumen NO
    // cambia (a diferencia del cálculo en vivo). Nullable: declaraciones antiguas sin
    // snapshot recurren al cálculo en vivo (ver DeclaracionComercializadorService).
    @Column(name = "resumen_documento", columnDefinition = "TEXT")
    private String resumenDocumento;
}