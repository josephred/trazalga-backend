package com.trazalga.api.models;

import java.math.BigDecimal;
import java.util.Date;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;

@Entity
@Table(name = "declaracion_planta_abastecimiento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class DeclaracionPlantaAbastecimientoModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Usuario que representa la planta que realiza la declaración (El que recibe)
    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private UsuarioModel usuario;

    @Column(nullable = true, length = 50)
    private String folioOrigen;

    @Column(nullable = true, length = 50)
    private String folioDeclaracionAPla; // A-PLA

    @Temporal(TemporalType.DATE)
    @Column(nullable = false)
    private Date fechaIngresoPlanta;

    @Column(nullable = false, length = 8)
    private String hora;

    // CAMPO FALTANTE AÑADIDO (Nombre de la planta)
    @Column(name = "nombre_planta", nullable = false)
    private String nombrePlanta;

    // CAMPO FALTANTE AÑADIDO (Código de la planta)
    @Column(name = "codigo_sernapesca", nullable = false, length = 50)
    private String codigoSernapesca;

    // Georreferencia estandarizada
    @Column(nullable = true)
    private Double latitud;

    @Column(nullable = true)
    private Double longitud;

    @ManyToOne
    @JoinColumn(name = "especie_id", nullable = false)
    private EspecieModel especie;

    @ManyToOne
    @JoinColumn(name = "composicion_id", nullable = false)
    private ComposicionModel composicion;

    @ManyToOne
    @JoinColumn(name = "humedad_estado_id", nullable = false)
    private HumedadEstadoModel humedadEstado;

    // MEJORA: BigDecimal para precisión en el pesaje
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal cantidad;

    // --- Documento Tributario (Factura de compra, guía, etc.) ---
    @Column(nullable = false)
    private String documentoTributarioTipo;

    @Column(nullable = false)
    private String documentoTributarioNumero;

    @Temporal(TemporalType.DATE)
    @Column(nullable = false)
    private Date documentoTributarioFecha;

    @Column(nullable = true)
    private String patente;

    // Relación con el usuario que proveyó el recurso (Comercializador, Recolector,
    // etc.)
    // Nota: En abastecimiento, el 'usuarioDestinatario' es la misma Planta,
    // por lo que este campo podría usarse para el PROVEEDOR si se desea,
    // o mantenerse como destinatario si la lógica de tu sistema lo requiere.
    @ManyToOne
    @JoinColumn(name = "usuario_destinatario_id", nullable = true)
    private UsuarioModel usuarioDestinatario;

    @Column(name = "declaracion_destinatario_id", nullable = true)
    private Long declaracionDestinatario;

    @Column(name = "consumida_por_tipo", length = 50)
    private String consumidaPorTipo;

    // MEJORA: Para trazabilidad, saber qué declaraciones originaron este ingreso
    // Ejemplo: IDs de declaraciones de comercializadores separadas por coma
    @Column(name = "declaraciones_seleccionadas", length = 1000)
    private String declaracionesSeleccionadas;
}