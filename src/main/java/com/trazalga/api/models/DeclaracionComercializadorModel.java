package com.trazalga.api.models;

import java.math.BigDecimal; // Importar BigDecimal
import java.util.Date;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name="declaracion_comercializador")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeclaracionComercializadorModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private UsuarioModel usuario;
    
    @Column(nullable = true)
    private String folioOrigen;
    
    @Column(nullable = true)
    private String folioDesembarqueAc;

    @Temporal(TemporalType.DATE)
    @Column(nullable = false)
    private Date fechaDeclaracion;

    @Column(nullable = false, length = 8)
    private String hora;

    @Column(nullable = true)
    private String codigoSernapesca;

    @Column(nullable = true)
    private String nombreComercializador;
    
    // MEJORA: Reemplazar 'georreferencia' por latitud y longitud para consistencia
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

    // MEJORA: Mantener BigDecimal es una buena práctica
    @Column(precision = 10, scale = 2) // Opcional: define precisión y escala para la DB
    private BigDecimal cantidad;

    // --- Documentos Tributarios (ya están correctos) ---
    private String documentoTributarioOrigenTipo;
    private String documentoTributarioOrigenNumero;
    @Temporal(TemporalType.DATE)
    private Date documentoTributarioOrigenFecha;

    private String documentoTributarioDestinoTipo;
    private String documentoTributarioDestinoNumero;
    @Temporal(TemporalType.DATE)
    private Date documentoTributarioDestinoFecha;

    // --- Datos de Transporte (ya están correctos) ---
    private String vehiculoTransporte;
    private String choferTransporte;
    private String patente;
    
    // CAMPO FALTANTE AÑADIDO (RUT del destinatario)
    @Column(nullable = false, length = 20)
    private String codigoDestinatario;
 
    // CAMPO FALTANTE AÑADIDO (Nombre del destinatario, desnormalizado)
    @Column(nullable = false)
    private String nombreDestinatario;

    @ManyToOne
    @JoinColumn(name = "usuario_destinatario_id", nullable = false)
    private UsuarioModel usuarioDestinatario;
    
    // --- Campos de Trazabilidad (ya están correctos y son clave) ---
    @Column(name = "declaracion_destinatario_id")
    private Long declaracionDestinatario;
    
    @Column(name = "declaraciones_seleccionadas", length = 1000) // Aumentar longitud si pueden ser muchos IDs
    private String declaracionesSeleccionadas;
}