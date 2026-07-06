package com.trazalga.api.models;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import lombok.*;
import lombok.experimental.Accessors;

@Entity
@Table(name = "cuota_extraccion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class CuotaExtraccionModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Perfil: e.g. "RECOLECTOR" or "ARMADOR"
    @Column(nullable = false)
    private String perfil;

    // Especie afectada (nullable = null means applies to any especie)
    @ManyToOne
    @JoinColumn(name = "especie_id", nullable = true)
    private EspecieModel especie;

    // Región (nullable = null means applies to any region)
    @ManyToOne
    @JoinColumn(name = "region_id", nullable = true)
    private RegionModel region;

    // Actor específico (nullable = null means applies to any actor of the perfil)
    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = true)
    private UsuarioModel usuario;

    // Área de manejo específica (nullable = null means applies to any AMERB; only relevant for perfil AREA)
    @ManyToOne
    @JoinColumn(name = "amerb_id", nullable = true)
    private AmerbModel amerb;

    // Periodo: "DIARIO" o "MENSUAL"
    @Column(nullable = false)
    private String periodo;

    // Límite en kilogramos
    @Column(nullable = false)
    private Double limiteKg;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date updatedAt;

    @PrePersist
    protected void onCreate() {
        Date now = new Date();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = new Date();
    }

}
