package com.trazalga.api.models;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "declaracion_buzos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class DeclaracionBuzosModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "perfil_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private PerfilModel perfil;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buzo_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private BuzoModel buzo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "declaracion_armador_id", nullable = true)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private DeclaracionArmadorModel declaracionArmador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "declaracion_recolector_id", nullable = true)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private DeclaracionRecolectorModel declaracionRecolector;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "declaracion_area_id", nullable = true)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private DeclaracionAreaModel declaracionArea;
}
