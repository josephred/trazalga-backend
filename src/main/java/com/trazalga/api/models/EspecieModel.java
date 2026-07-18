package com.trazalga.api.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.*;
import lombok.experimental.Accessors;

@Entity
@Table(name = "especie")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class EspecieModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String nombre;

    @Column
    private String descripcion;

    /**
     * Visibilidad en los selectores de la app. NULL o true = visible.
     * Las especies/composiciones no vigentes se conservan en BD (por si el
     * proyecto escala a otras pesquerías) pero ocultas de la vista.
     */
    @Column(nullable = true)
    private Boolean activo;


}
