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

@Entity
@Table(name = "cuota_extraccion")
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

    // Periodo: "DIARIO" o "MENSUAL"
    @Column(nullable = false)
    private String periodo;

    // Límite en kilogramos
    @Column(nullable = false)
    private Double limiteKg;

    @Column(nullable = false)
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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPerfil() {
        return perfil;
    }

    public void setPerfil(String perfil) {
        this.perfil = perfil;
    }

    public EspecieModel getEspecie() {
        return especie;
    }

    public void setEspecie(EspecieModel especie) {
        this.especie = especie;
    }

    public RegionModel getRegion() {
        return region;
    }

    public void setRegion(RegionModel region) {
        this.region = region;
    }

    public String getPeriodo() {
        return periodo;
    }

    public void setPeriodo(String periodo) {
        this.periodo = periodo;
    }

    public Double getLimiteKg() {
        return limiteKg;
    }

    public void setLimiteKg(Double limiteKg) {
        this.limiteKg = limiteKg;
    }

    public Boolean getActivo() {
        return activo;
    }

    public void setActivo(Boolean activo) {
        this.activo = activo;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

}
