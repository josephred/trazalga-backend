package com.trazalga.api.models;

import java.util.Date;
import java.util.List;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "clave", "fechaCreacion", "correo", "estado"})
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
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private AmerbModel amerb;

    @ManyToOne
    @JoinColumn(name = "caleta_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private CaletaModel caleta;

    // Relación con la especie declarada
    @ManyToOne
    @JoinColumn(name = "especie_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
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
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "clave", "fechaCreacion", "correo", "estado"})
    private UsuarioModel usuarioDestinatario;

    // Relación con la composición de fronda
    @ManyToOne
    @JoinColumn(name = "composicion_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private ComposicionModel composicion;

    // Relación con el estado de humedad
    @ManyToOne
    @JoinColumn(name = "humedad_estado_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private HumedadEstadoModel humedadEstado;

    // Georreferencia (latitud y longitud)
    @Column(nullable = true)
    private Double latitud;

    @Column(nullable = true)
    private Double longitud;

    @ManyToOne
    @JoinColumn(name = "embarcacion_id", nullable = true)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private EmbarcacionModel embarcacion;

    @ManyToOne
    @JoinColumn(name = "buzo_id", nullable = true)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private BuzoModel buzo;

    // NUEVO CAMPO AÑADIDO
    @Column(name = "declaracion_destinatario_id", nullable = true)
    private Long declaracionDestinatario;

    // Campo transitorio para recibir la lista de buzos del frontend
    @Transient
    private List<BuzoModel> buzos;
}