package com.trazalga.api.models;

import java.util.Date;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "declaracion_planta_destino")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeclaracionPlantaDestinoModel {

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
    private String folioDeclaracionAbastecimientoPlanta;

    @Temporal(TemporalType.DATE)
    @Column(nullable = false)
    private Date fechaDeclaracionDestino;

    @Temporal(TemporalType.DATE)
    @Column(nullable = false)
    private Date fechaTrasladoDestino;

    @Column(nullable = false, length = 5)
    private String hora;

    @Column(nullable = true)
    private Double latitud;

    @Column(nullable = true)
    private Double longitud;

    @ManyToOne
    @JoinColumn(name = "especie_id", nullable = false)
    private EspecieModel especie;

    @ManyToOne
    @JoinColumn(name = "producto_id", nullable = false)
    private ProductoModel producto;

    @Column(nullable = false)
    private Double cantidad;

    private String documentoTributarioTipo;
    private String documentoTributarioNumero;
    private Date documentoTributarioFecha;

    @Column(nullable = false, length = 100)
    private String nombreDestino;

    @Column(nullable = false, length = 20)
    private String rutDestino;

    private String codigoSernapescaDestino;
}