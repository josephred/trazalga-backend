package com.trazalga.api.models;

import java.util.Date;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "declaracion_armador")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
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
    @Column(name = "codigo_sernapesca_embarcacion", nullable = false, length = 50)
    private String codigoSernapescaEmbarcacion;

    @ManyToOne
    @JoinColumn(name = "embarcacion_id", nullable = false)
    private EmbarcacionModel embarcacion;

    // CAMPO FALTANTE AÑADIDO (para el RPA del buzo)
    @Column(name = "codigo_sernapesca_buzo", nullable = false, length = 50)
    private String codigoSernapescaBuzo;

    @ManyToOne
    @JoinColumn(name = "buzo_id", nullable = false)
    private BuzoModel buzo;

    @Column(nullable = false)
    private Double desembarque;

    @Column(nullable = false)
    private Double captura;

    @Builder.Default
    @Column(nullable = false, length = 20)
    private String tipoDestinatario = "comercializador";
    
    // CAMPO FALTANTE AÑADIDO (para el RUT del destinatario)
    @Column(name = "codigo_destinatario", nullable = false, length = 20)
    private String codigoDestinatario;

    @ManyToOne
    @JoinColumn(name = "usuario_destinatario_id", nullable = false)
    private UsuarioModel usuarioDestinatario;

    @ManyToOne
    @JoinColumn(name = "caleta_id", nullable = false)
    private CaletaModel caleta;

    // CAMPO FALTANTE AÑADIDO (para la comuna, derivada de la caleta)
    @ManyToOne
    @JoinColumn(name = "comuna_id", nullable = false)
    private ComunaModel comuna;

    @ManyToOne
    @JoinColumn(name = "especie_id", nullable = false)
    private EspecieModel especie;

    @ManyToOne
    @JoinColumn(name = "composicion_id", nullable = false)
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
}