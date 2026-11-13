package com.trazalga.api.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Entity
@Table(name = "producto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class ProductoModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 30)
    private String codigo;

    @Column(nullable = false, unique = true, length = 100)
    private String nombre;

    @Column(nullable = true)
    private String descripcion;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "humedad_estado_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private HumedadEstadoModel humedadEstado;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "especie_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private EspecieModel especie;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @Column(length = 60)
    private String fuente;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    @Column(name = "actualizado_por", length = 60)
    private String actualizadoPor;
}
