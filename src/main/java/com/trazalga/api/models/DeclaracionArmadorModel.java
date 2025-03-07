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

    // Relación con el usuario que realiza la declaración
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

    @Column(nullable = false)
    private String hora;

    // Relación con la embarcación seleccionada
    @ManyToOne
    @JoinColumn(name = "embarcacion_id", nullable = false)
    private EmbarcacionModel embarcacion;

    // Relación con el buzo seleccionado
    @ManyToOne
    @JoinColumn(name = "buzo_id", nullable = false)
    private BuzoModel buzo;

    // Cantidad desembarcada en KG
    @Column(nullable = false)
    private Double desembarque;

    // Cantidad capturada en KG
    @Column(nullable = false)
    private Double captura;

    // Tipo de destinatario ("comercializador" o "planta")
    @Builder.Default
    @Column(nullable = false)
    private String tipoDestinatario = "comercializador";

    // Relación con el destinatario
    @ManyToOne
    @JoinColumn(name = "usuario_destinatario_id", nullable = false)
    private UsuarioModel usuarioDestinatario;

    // Relación con la caleta
    @ManyToOne
    @JoinColumn(name = "caleta_id", nullable = false)
    private CaletaModel caleta;

    // Relación con la especie
    @ManyToOne
    @JoinColumn(name = "especie_id", nullable = false)
    private EspecieModel especie;

    // Relación con la composición de fronda
    @ManyToOne
    @JoinColumn(name = "composicion_id", nullable = false)
    private ComposicionModel composicion;

    // Relación con ells -l estado de humedad
    @ManyToOne
    @JoinColumn(name = "humedad_estado_id", nullable = false)
    private HumedadEstadoModel humedadEstado;

    // Georreferencia (latitud y longitud)
    @Column(nullable = true)
    private Double latitud;

    @Column(nullable = true)
    private Double longitud;
}
