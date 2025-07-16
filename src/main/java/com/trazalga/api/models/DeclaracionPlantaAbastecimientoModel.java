package com.trazalga.api.models;

import java.util.Date;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "declaracion_planta_abastecimiento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeclaracionPlantaAbastecimientoModel {

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
    private String folioDeclaracionAPla;

    @Temporal(TemporalType.DATE)
    @Column(nullable = false)
    private Date fechaIngresoPlanta;

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
    @JoinColumn(name = "composicion_id", nullable = false)
    private ComposicionModel composicion;

    @ManyToOne
    @JoinColumn(name = "humedad_estado_id", nullable = false)
    private HumedadEstadoModel humedadEstado;

    @Column(nullable = false)
    private Double cantidad;

    private String documentoTributarioTipo;
    private String documentoTributarioNumero;
    private Date documentoTributarioFecha;
    private String patente;
}
