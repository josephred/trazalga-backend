package com.trazalga.api.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.Accessors;

@Entity
@Table(name = "planta")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class PlantaModel {

    @Id
    private Long id;

    @Column
    private String rut;

    @Column
    private String nombre;

    @Column
    private Integer codigo;

    @Column
    private String direccion;

    @Column(length = 20, nullable = true)
    private String tipo;

    @Column(nullable = true)
    private Double latitud;

    @Column(nullable = true)
    private Double longitud;

    @Column(name = "fuente_coordenada", length = 100, nullable = true)
    private String fuenteCoordenada;

    @Column(name = "coordenada_actualizada_en", nullable = true)
    private java.time.LocalDateTime coordenadaActualizadaEn;

    @Column(name = "coordenada_actualizada_por", length = 100, nullable = true)
    private String coordenadaActualizadaPor;
}
