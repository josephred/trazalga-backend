package com.trazalga.api.models;

import java.math.BigDecimal;
import java.util.Date;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;

@Entity
@Table(name = "declaracion_planta_destino")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class DeclaracionPlantaDestinoModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Usuario que representa la planta que realiza la declaración (El remitente)
    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private UsuarioModel usuario;

    @Column(name = "folio_declaracion_abastecimiento_planta")
    private String folioDeclaracionAbastecimientoPlanta;

    @Column(nullable = false, length = 50)
    private String folioOrigen;

    // Nota: La guía a veces reutiliza nombres, aquí suele ser un correlativo de
    // salida.
    @Column(nullable = false, unique = true, length = 50)
    private String folioDeclaracionDestino;

    @Temporal(TemporalType.DATE)
    @Column(nullable = false)
    private Date fechaDeclaracionDestino;

    @Temporal(TemporalType.DATE)
    @Column(nullable = false)
    private Date fechaTrasladoDestino;

    @Column(nullable = false, length = 8) // Aumentado a 8 para HH:mm:ss
    private String hora;

    // --- Campos de Identificación de la Planta (Remitente) ---
    // FALTANTES EN TU MODELO ORIGINAL
    @Column(name = "nombre_planta", nullable = false)
    private String nombrePlanta;

    @Column(name = "codigo_sernapesca", nullable = false, length = 50)
    private String codigoSernapesca;

    // --- Georreferencia ---
    @Column(nullable = true)
    private Double latitud;

    @Column(nullable = true)
    private Double longitud;

    // --- Detalles del Producto ---
    @ManyToOne
    @JoinColumn(name = "especie_id", nullable = false)
    private EspecieModel especie;

    @ManyToOne
    @JoinColumn(name = "producto_id", nullable = false)
    private ProductoModel producto; // Tipo y formato del producto

    // MEJORA: BigDecimal para precisión financiera/inventario
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal cantidad;

    // --- Documentación Legal ---
    @Column(nullable = false)
    private String documentoTributarioTipo;

    @Column(nullable = false)
    private String documentoTributarioNumero;

    @Temporal(TemporalType.DATE)
    private Date documentoTributarioFecha;

    // --- Datos del Destinatario (Cliente/Exportación) ---
    @Column(nullable = false, length = 100)
    private String nombreDestino;

    @Column(nullable = false, length = 20)
    private String rutDestino;

    @Column(nullable = true)
    private String codigoSernapescaDestino; // Si el destino es otra planta o comercializador

    // Relación opcional si el destinatario es usuario del sistema
    @ManyToOne
    @JoinColumn(name = "usuario_destinatario_id", nullable = true)
    private UsuarioModel usuarioDestinatario;

    @Column(name = "declaracion_destinatario_id", nullable = true)
    private Long declaracionDestinatario;

    // --- Trazabilidad Inversa ---
    // IDs de las declaraciones de PRODUCCIÓN que componen este envío
    @Column(name = "declaraciones_produccion_ids", length = 1000)
    private String declaracionesProduccionIds;
}