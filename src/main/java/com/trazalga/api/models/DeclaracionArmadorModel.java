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

    @ManyToOne
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

    @ManyToOne
    @JoinColumn(name = "embarcacion_id", nullable = false)
    private EmbarcacionModel embarcacion;

    // CAMPO FALTANTE AÑADIDO (para el RPA del buzo)
    @Column(name = "codigo_sernapesca_buzo", nullable = true, length = 50)
    private String codigoSernapescaBuzo;

    @ManyToOne
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

    @ManyToOne
    @JoinColumn(name = "usuario_destinatario_id", nullable = false)
    private UsuarioModel usuarioDestinatario;

    @ManyToOne
    @JoinColumn(name = "caleta_id", nullable = false)
    private CaletaModel caleta;

    // CAMPO FALTANTE AÑADIDO (para la comuna, derivada de la caleta)
    @ManyToOne
    @JoinColumn(name = "comuna_id", nullable = true)
    private ComunaModel comuna;

    @ManyToOne
    @JoinColumn(name = "especie_id", nullable = false)
    private EspecieModel especie;

    @ManyToOne
    @JoinColumn(name = "composicion_id", nullable = true)
    private ComposicionModel composicion;

    @ManyToOne
    @JoinColumn(name = "humedad_estado_id", nullable = false)
    private HumedadEstadoModel humedadEstado;

    @Column(nullable = true)
    private Double latitud;

    @Column(nullable = true)
    private Double longitud;

    @Column(name = "declaracion_destinatario_id", nullable = true)
    private Long declaracionDestinatario;

    // Campo transitorio para recibir la lista de buzos del frontend (no se persiste en esta tabla)
    @Transient
    private List<BuzoModel> buzos;
}