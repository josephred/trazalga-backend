package com.trazalga.api.models;

import java.util.Date;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "declaracion_planta_produccion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeclaracionPlantaProduccionModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Usuario que representa la planta que realiza la declaración
    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private UsuarioModel usuario;

    @Column(nullable = false, length = 50)
    private String folioOrigen;

    @Column(nullable = false, unique = true, length = 50)
    private String folioDeclaracionPpla;

    @Temporal(TemporalType.DATE)
    @Column(nullable = false)
    private Date fechaProduccion;

    @Column(nullable = false, length = 5)
    private String hora;

    @Column(nullable = true)
    private Double latitud;

    @Column(nullable = true)
    private Double longitud;

    @ManyToOne
    @JoinColumn(name = "materia_prima_especie_id", nullable = false)
    private EspecieModel materiaPrimaEspecie;

    @ManyToOne
    @JoinColumn(name = "materia_prima_producto_id", nullable = true) // Permitir nulo para cuando no es un producto
    private ProductoModel materiaPrimaProducto;

    @ManyToOne
    @JoinColumn(name = "humedad_estado_id", nullable = false)
    private HumedadEstadoModel humedadEstado;

    @Column(nullable = false)
    private Double cantidadMateriaPrima;

    @Column(nullable = false)
    private Double cantidadProducto;
}