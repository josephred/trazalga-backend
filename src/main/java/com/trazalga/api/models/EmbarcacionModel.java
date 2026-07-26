package com.trazalga.api.models;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;

@Entity
@Table(name = "embarcacion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class EmbarcacionModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // El nombre de nave NO es único a nivel nacional: dos embarcaciones de regiones
    // distintas pueden compartirlo. El identificador confiable es "codigo" (folioRpa).
    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = true, length = 50, unique = true)
    private String codigo;

    /** Código de región Sernapesca al que pertenece la embarcación (ej. 4 = Coquimbo). */
    @Column(name = "codigo_region", nullable = true)
    private Integer codigoRegion;
}