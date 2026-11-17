package com.trazalga.api.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comuna_id", nullable = true)
    @JsonIgnoreProperties({"provincia", "hibernateLazyInitializer", "handler"})
    private ComunaModel comuna;

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
