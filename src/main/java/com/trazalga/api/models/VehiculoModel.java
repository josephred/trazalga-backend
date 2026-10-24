package com.trazalga.api.models;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

@Entity
@Table(name = "vehiculo", uniqueConstraints = {
        @UniqueConstraint(name = "ux_vehiculo_patente", columnNames = {"patente"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehiculoModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patente", nullable = false, length = 10, unique = true)
    private String patente;

    @Column(name = "formato_valido", nullable = false)
    @Builder.Default
    private Boolean formatoValido = false;

    @Column(name = "usado_como_camion", nullable = false)
    @Builder.Default
    private Boolean usadoComoCamion = false;

    @Column(name = "usado_como_rampla", nullable = false)
    @Builder.Default
    private Boolean usadoComoRampla = false;

    @Column(name = "tipo_vehiculo", length = 40)
    private String tipoVehiculo;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "primera_vez", nullable = false)
    private Date primeraVez;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "ultima_vez", nullable = false)
    private Date ultimaVez;
}
