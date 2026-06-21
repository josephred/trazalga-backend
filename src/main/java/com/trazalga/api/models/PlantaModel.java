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
}
