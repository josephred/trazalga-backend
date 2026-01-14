package com.trazalga.api.models;

import java.math.BigDecimal;
import java.util.Date;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;

@Entity
@Table(name = "declaracion_planta_produccion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class DeclaracionPlantaProduccionModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Usuario que representa la planta (quien declara)
    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private UsuarioModel usuario;

    @Column(nullable = false, length = 50)
    private String folioOrigen;

    @Column(nullable = false, unique = true, length = 50)
    private String folioDeclaracionPpla; // P-PLA

    @Temporal(TemporalType.DATE)
    @Column(nullable = false)
    private Date fechaProduccion;

    @Column(nullable = false, length = 8)
    private String hora;

    // --- Campos de Identificación (Normativa) ---
    @Column(name = "nombre_planta", nullable = false)
    private String nombrePlanta;

    @Column(name = "codigo_sernapesca", nullable = false, length = 50)
    private String codigoSernapesca;

    // --- Georreferencia ---
    @Column(nullable = true)
    private Double latitud;

    @Column(nullable = true)
    private Double longitud;

    // --- ENTRADA (INPUT) ---
    // Qué entra a la máquina
    @ManyToOne
    @JoinColumn(name = "materia_prima_especie_id", nullable = false)
    private EspecieModel materiaPrimaEspecie;

    // Si la materia prima ya era un producto intermedio (opcional)
    @ManyToOne
    @JoinColumn(name = "materia_prima_producto_id", nullable = true)
    private ProductoModel materiaPrimaProducto;

    @ManyToOne
    @JoinColumn(name = "humedad_estado_id", nullable = false)
    private HumedadEstadoModel humedadEstado; // Humedad con la que entra a proceso

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal cantidadMateriaPrima; // Cuántos Kg entran

    // --- SALIDA (OUTPUT) ---
    // Qué sale de la máquina (MEJORA CRÍTICA)
    @ManyToOne
    @JoinColumn(name = "producto_resultante_id", nullable = false)
    private ProductoModel productoResultante;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal cantidadProducto; // Cuántos Kg salen

    // --- Trazabilidad ---
    @ManyToOne
    @JoinColumn(name = "usuario_destinatario_id", nullable = true)
    private UsuarioModel usuarioDestinatario;

    @Column(name = "declaracion_destinatario_id", nullable = true)
    private Long declaracionDestinatario;

    // IDs de las declaraciones de Abastecimiento que se usaron para esta producción
    @Column(name = "declaraciones_abastecimiento_ids", length = 1000)
    private String declaracionesAbastecimientoIds;
}