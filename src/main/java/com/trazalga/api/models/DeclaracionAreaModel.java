package com.trazalga.api.models;

import java.util.Date;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;

@Entity
@Table(name = "declaracion_area")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class DeclaracionAreaModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private UsuarioModel usuario;

    @Column(nullable = true, length = 50)
    private String folioOrigen;

    @Column(nullable = true, length = 50)
    private String folioDesembarqueAmerb;

    @Temporal(TemporalType.DATE)
    @Column(nullable = false)
    private Date fechaExtraccion;

    @Temporal(TemporalType.DATE)
    @Column(nullable = false)
    private Date fechaDeclaracion;

    @Column(nullable = false)
    private String hora;

    @Column(name = "codigo_sernapesca_amerb", nullable = true, length = 50)
    private String codigoSernapescaAmerb;

    // Relación con la AMERB (Área de Manejo y Explotación de Recursos Bentónicos)
    @ManyToOne
    @JoinColumn(name = "amerb_id", nullable = false)
    private AmerbModel amerb;

    @ManyToOne
    @JoinColumn(name = "caleta_id", nullable = false)
    private CaletaModel caleta;

    // Relación con la especie declarada
    @ManyToOne
    @JoinColumn(name = "especie_id", nullable = false)
    private EspecieModel especie;

    // Cantidad capturada en KG
    @Column(nullable = false)
    private Double captura;

    // Cantidad desembarcada en KG
    @Column(nullable = false)
    private Double desembarque;

    // Tipo de destinatario ("comercializador" o "planta")
    @Column(nullable = false, length = 20)
    private String tipoDestinatario;

    @Column(name = "codigo_destinatario", nullable = true, length = 20)
    private String codigoDestinatario;

    // Relación con el destinatario de la declaración
    @ManyToOne
    @JoinColumn(name = "usuario_destinatario_id", nullable = false)
    private UsuarioModel usuarioDestinatario;

    // Relación con la composición de fronda
    @ManyToOne
    @JoinColumn(name = "composicion_id", nullable = false)
    private ComposicionModel composicion;

    // Relación con el estado de humedad
    @ManyToOne
    @JoinColumn(name = "humedad_estado_id", nullable = false)
    private HumedadEstadoModel humedadEstado;

    // Georreferencia (latitud y longitud)
    @Column(nullable = true)
    private Double latitud;

    @Column(nullable = true)
    private Double longitud;

    // NUEVO CAMPO AÑADIDO
    @Column(name = "declaracion_destinatario_id", nullable = true)
    private Long declaracionDestinatario;
}