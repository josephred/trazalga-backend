package com.trazalga.api.models;

import java.math.BigDecimal; // Importar BigDecimal
import java.util.Date;
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

    @ManyToOne
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

    @ManyToOne
    @JoinColumn(name = "caleta_id", nullable = false)
    private CaletaModel caleta;

    // MEJORA: Reemplazar 'georreferencia' por latitud y longitud
    @Column(nullable = true)
    private Double latitud;

    @Column(nullable = true)
    private Double longitud;

    @ManyToOne
    @JoinColumn(name = "especie_id", nullable = false)
    private EspecieModel especie;

    @ManyToOne
    @JoinColumn(name = "comuna_id", nullable = false)
    private ComunaModel comuna;

    @ManyToOne
    @JoinColumn(name = "extraccion_tipo_id", nullable = false)
    private ExtraccionTipoModel extraccionTipo;

    @ManyToOne
    @JoinColumn(name = "composicion_id", nullable = false)
    private ComposicionModel composicion;

    @ManyToOne
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

    @ManyToOne
    @JoinColumn(name = "usuario_destinatario_id", nullable = false)
    private UsuarioModel usuarioDestinatario;

    @Column(name = "declaracion_destinatario_id")
    private Long declaracionDestinatario;
}