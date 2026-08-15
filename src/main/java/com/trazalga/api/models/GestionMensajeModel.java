package com.trazalga.api.models;

import java.util.Date;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "gestion_mensaje")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class GestionMensajeModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Tipo de declaración referenciada: RECOLECTOR, ARMADOR, AREA,
     * COMERCIALIZADOR, PLANTA_ABASTECIMIENTO, PLANTA_PRODUCCION, etc.
     * Se usa en combinación con declaracionId para identificar la declaración
     * de forma unívoca entre tablas (mismo patrón que seleccionDeclaraciones.js).
     */
    @Column(name = "declaracion_tipo", nullable = false, length = 50)
    private String declaracionTipo;

    /** ID de la declaración en su tabla de origen. */
    @Column(name = "declaracion_id", nullable = false)
    private Long declaracionId;

    /** Usuario que envía el mensaje. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emisor_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "clave", "fechaCreacion", "correo", "estado"})
    private UsuarioModel emisor;

    /** Usuario que recibe el mensaje. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receptor_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "clave", "fechaCreacion", "correo", "estado"})
    private UsuarioModel receptor;

    /** Contenido del mensaje. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String mensaje;

    /** Fecha y hora de envío. */
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "fecha_envio", nullable = false)
    private Date fechaEnvio;

    /** Si el receptor ya leyó el mensaje. */
    @Builder.Default
    @Column(nullable = false)
    private Boolean leido = false;
}
