package com.trazalga.api.models;

import java.math.BigDecimal;
import java.util.Date;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;

@Entity
@Table(name = "factor_conversion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class FactorConversionModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "especie_id", nullable = false)
    private EspecieModel especie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "humedad_estado_id", nullable = false)
    private HumedadEstadoModel humedadEstado;

    @Column(nullable = false, precision = 8, scale = 4)
    private BigDecimal factor;

    @Temporal(TemporalType.DATE)
    @Column(name = "vigencia_inicio", nullable = false)
    private Date vigenciaInicio;

    @Temporal(TemporalType.DATE)
    @Column(name = "vigencia_fin", nullable = true)
    private Date vigenciaFin;

    @Column(length = 100)
    private String resolucion;

    @Column(length = 255)
    private String descripcion;

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
