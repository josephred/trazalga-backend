package com.trazalga.api.models;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;

@Entity
@Table(name = "declaracion_armador")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class DeclaracionArmadorModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private UsuarioModel usuario;

    @Column(nullable = true, length = 50)
    private String folioOrigen;

    @Column(nullable = true, length = 50)
    private String folioDesembarqueDa;

    @Temporal(TemporalType.DATE)
    @Column(nullable = false)
    private Date fechaExtraccion;

    @Temporal(TemporalType.DATE)
    @Column(nullable = false)
    private Date fechaDeclaracion;

    @Column(nullable = false, length = 8)
    private String hora;

    // CAMPO FALTANTE AÑADIDO (para el RPA de la embarcación)
    @Column(name = "codigo_sernapesca_embarcacion", nullable = true, length = 50)
    private String codigoSernapescaEmbarcacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "embarcacion_id", nullable = false)
    private EmbarcacionModel embarcacion;

    // CAMPO FALTANTE AÑADIDO (para el RPA del buzo)
    @Column(name = "codigo_sernapesca_buzo", nullable = true, length = 50)
    private String codigoSernapescaBuzo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buzo_id", nullable = false)
    private BuzoModel buzo;

    @Column(nullable = false)
    private BigDecimal desembarque;

    @Column(nullable = false)
    private Double captura;

    @Builder.Default
    @Column(nullable = false, length = 20)
    private String tipoDestinatario = "comercializador";

    // CAMPO FALTANTE AÑADIDO (para el RUT del destinatario)
    @Column(name = "codigo_destinatario", nullable = true, length = 20)
    private String codigoDestinatario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_destinatario_id", nullable = false)
    private UsuarioModel usuarioDestinatario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caleta_id", nullable = false)
    private CaletaModel caleta;

    // CAMPO FALTANTE AÑADIDO (para la comuna, derivada de la caleta)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comuna_id", nullable = true)
    private ComunaModel comuna;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "especie_id", nullable = false)
    private EspecieModel especie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "composicion_id", nullable = true)
    private ComposicionModel composicion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "humedad_estado_id", nullable = false)
    private HumedadEstadoModel humedadEstado;

    @Column(nullable = true)
    private Double latitud;

    @Column(nullable = true)
    private Double longitud;

    @Column(name = "declaracion_destinatario_id", nullable = true)
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

    // Campo transitorio para recibir la lista de buzos del frontend (no se persiste en esta tabla)
    @Transient
    private List<BuzoModel> buzos;
}