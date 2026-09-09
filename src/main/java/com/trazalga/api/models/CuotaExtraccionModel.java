package com.trazalga.api.models;

import java.util.Date;

import jakarta.persistence.FetchType;
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

    // Perfil: e.g. "RECOLECTOR", "ARMADOR" o "AREA"
    @Column(nullable = false)
    private String perfil;

    // Especie afectada (nullable = null means applies to any especie)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "especie_id", nullable = true)
    private EspecieModel especie;

    // Región (nullable = null means applies to any region)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id", nullable = true)
    private RegionModel region;

    // Macrozona (nullable = null means applies to any macrozona)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "macrozona_id", nullable = true)
    private MacrozonaModel macrozona;

    // Provincia (nullable = null means applies to any provincia)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provincia_id", nullable = true)
    private ProvinciaModel provincia;

    // Comuna (nullable = null means applies to any comuna)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comuna_id", nullable = true)
    private ComunaModel comuna;

    // Actor específico (nullable = null means applies to any actor of the perfil)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private UsuarioModel usuario;

    // Área de manejo específica (nullable = null means applies to any AMERB; only relevant for perfil AREA)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "amerb_id", nullable = true)
    private AmerbModel amerb;

    // Método de extracción específico
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "extraccion_tipo_id", nullable = true)
    private ExtraccionTipoModel extraccionTipo;

    // Estado de humedad en que está expresado el límite (nullable = ya expresado en metrica)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "humedad_estado_id", nullable = true)
    private HumedadEstadoModel humedadEstado;

    // Nivel de agregación territorial / personal: COMUNA | PROVINCIA | REGION | INDIVIDUAL
    @Column(name = "nivel_agregacion", nullable = false, length = 20)
    @Builder.Default
    private String nivelAgregacion = "COMUNA";

    // Métrica evaluada: CAPTURA | DESEMBARQUE
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String metrica = "CAPTURA";

    // Si es plantilla para asignación individual
    @Column(name = "es_plantilla", nullable = false)
    @Builder.Default
    private Boolean esPlantilla = false;

    // Periodo: "DIARIO", "MENSUAL", "ANUAL", etc.
    @Column(nullable = false)
    private String periodo;

    // Límite en kilogramos
    @Column(name = "limite_kg", nullable = false)
    private Double limiteKg;

    @Temporal(TemporalType.DATE)
    @Column(name = "fecha_inicio", nullable = true)
    private Date fechaInicio;

    @Temporal(TemporalType.DATE)
    @Column(name = "fecha_fin", nullable = true)
    private Date fechaFin;

    @Column(length = 100)
    private String resolucion;

    // Estado administrativo de la cuota: ABIERTA | CERRADA
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String estado = "ABIERTA";

    @Temporal(TemporalType.DATE)
    @Column(name = "fecha_cierre", nullable = true)
    private Date fechaCierre;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Date createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_at", nullable = false)
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
