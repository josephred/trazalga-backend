package com.trazalga.api.models;

import java.math.BigDecimal; // Importar BigDecimal
import java.util.Date;
import java.util.List;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;

@Entity
@Table(name = "declaracion_recolector")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class DeclaracionRecolectorModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private UsuarioModel usuario;

    @Column
    private String folioOrigen;

    @Column
    private String folioDesembarqueRo;

    @Temporal(TemporalType.DATE)
    @Column(nullable = false)
    private Date fechaExtraccion;

    @Temporal(TemporalType.DATE)
    @Column(name = "periodo_extraccion_inicio")
    private Date periodoExtraccionInicio;

    @Temporal(TemporalType.DATE)
    @Column(name = "periodo_extraccion_fin")
    private Date periodoExtraccionFin;

    @Temporal(TemporalType.DATE)
    @Column(nullable = false)
    private Date fechaDeclaracion;

    @Column(nullable = false, length = 8)
    private String hora;

    @Column(nullable = false)
    private String nombre; // Nombre del Recolector que declara

    @Column(nullable = false)
    private String codigoSernapesca; // RPA del Recolector

    @Column
    private String varadero;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caleta_id", nullable = false)
    private CaletaModel caleta;

    // MEJORA: Reemplazar 'georreferencia' por latitud y longitud
    @Column(nullable = true)
    private Double latitud;

    @Column(nullable = true)
    private Double longitud;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "especie_id", nullable = false)
    private EspecieModel especie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comuna_id", nullable = false)
    private ComunaModel comuna;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "extraccion_tipo_id", nullable = false)
    private ExtraccionTipoModel extraccionTipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "composicion_id", nullable = true)
    private ComposicionModel composicion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "humedad_estado_id", nullable = false)
    private HumedadEstadoModel humedadEstado;

    @Column
    private String humedad; // Valor del higrómetro, opcional

    // MEJORA: Usar BigDecimal para precisión
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal desembarque;

    @Column(precision = 10, scale = 2)
    private BigDecimal captura; // Calculado por el backend

    @Column(name = "tasa_diaria_recoleccion", precision = 10, scale = 3)
    private BigDecimal tasaDiariaRecoleccion;

    @Column(nullable = false, length = 20)
    private String codigoDestinatario;

    // CAMPO FALTANTE AÑADIDO (Nombre del destinatario)
    @Column(name = "nombre_destinatario", nullable = false)
    private String nombreDestinatario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_destinatario_id", nullable = false)
    private UsuarioModel usuarioDestinatario;

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

    @Transient
    private List<BuzoModel> buzos;
}