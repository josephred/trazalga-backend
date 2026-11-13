package com.trazalga.api.models;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Rango teórico/normativo oficial de rendimiento en planta (NP.3 / D31).
 */
@Entity
@Table(name = "rendimiento_rango")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class RendimientoRangoModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "especie_id", nullable = false)
    private EspecieModel especie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "humedad_entrada_id", nullable = false)
    private HumedadEstadoModel humedadEntrada;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private ProductoModel producto;

    @Column(name = "min_pct", precision = 7, scale = 2)
    private BigDecimal minPct;

    @Column(name = "max_pct", precision = 7, scale = 2)
    private BigDecimal maxPct;

    @Column(length = 100)
    private String resolucion;

    @Temporal(TemporalType.DATE)
    @Column(name = "vigencia_inicio", nullable = false)
    private Date vigenciaInicio;

    @Temporal(TemporalType.DATE)
    @Column(name = "vigencia_fin")
    private Date vigenciaFin;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;
}

