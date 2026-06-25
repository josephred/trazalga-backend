package com.trazalga.api.models;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;

@Entity
@Table(name = "buzo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class BuzoModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100, unique = true)
    private String nombre;

    @Column(nullable = true, length = 50, unique = true)
    private String codigo;

    /** Código de región Sernapesca al que pertenece el buzo (ej. 4 = Coquimbo). */
    @Column(name = "codigo_region", nullable = true)
    private Integer codigoRegion;
}