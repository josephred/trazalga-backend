package com.trazalga.api.models;

import java.math.BigDecimal;
import java.util.Date;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;

@Entity
@Table(name = "limite_extraccion_diario_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class LimiteExtraccionDiarioConfigModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre_regla", nullable = false, length = 100)
    private String nombreRegla;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "especie_id", nullable = true)
    private EspecieModel especie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "extraccion_tipo_id", nullable = true)
    private ExtraccionTipoModel extraccionTipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id", nullable = true)
    private RegionModel region;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "macrozona_id", nullable = true)
    private MacrozonaModel macrozona;

    @Column(name = "perfil_aplicable", nullable = false, length = 50)
    @Builder.Default
    private String perfilAplicable = "ARMADOR";

    @Column(name = "unidad_agregacion", nullable = false, length = 20)
    @Builder.Default
    private String unidadAgregacion = "EMBARCACION"; // EMBARCACION | USUARIO | BUZO

    @Column(name = "metrica", nullable = false, length = 20)
    @Builder.Default
    private String metrica = "DESEMBARQUE"; // DESEMBARQUE | CAPTURA

    @Column(name = "limite_kg", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal limiteKg = new BigDecimal("2000.00");

    @Column(name = "margen_tolerancia_pct", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal margenToleranciaPct = BigDecimal.ZERO;

    @Column(name = "modo_accion", nullable = false, length = 50)
    @Builder.Default
    private String modoAccion = "SOLO_ALERTA"; // SOLO_ALERTA | BLOQUEO

    @Temporal(TemporalType.DATE)
    @Column(name = "vigencia_inicio", nullable = true)
    private Date vigenciaInicio;

    @Temporal(TemporalType.DATE)
    @Column(name = "vigencia_fin", nullable = true)
    private Date vigenciaFin;

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
