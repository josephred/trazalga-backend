package com.trazalga.api.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.*;
import lombok.experimental.Accessors;

@Entity
@Table(name = "varadero")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class VaraderoModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String nombre; // Equivalente a la columna "Varadero" en el excel

    @Column(name = "lat_gra")
    private Integer latGra;

    @Column(name = "lat_min")
    private Integer latMin;

    @Column(name = "lat_seg")
    private Double latSeg;

    @Column(name = "long_gra")
    private Integer longGra;

    @Column(name = "long_min")
    private Integer longMin;

    @Column(name = "long_seg")
    private Double longSeg;

    @Column
    private Double latitud; // Equivalente a la columna "LAT" en el excel

    @Column
    private Double longitud; // Equivalente a la columna "LONG" en el excel

    // Relación numérica con la tabla de comunas
    @ManyToOne
    @JoinColumn(name = "comuna_id")
    private ComunaModel comuna;

}
