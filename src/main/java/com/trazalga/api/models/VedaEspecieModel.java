package com.trazalga.api.models;

import jakarta.persistence.FetchType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import java.util.Date;

import lombok.*;
import lombok.experimental.Accessors;

@Entity
@Table(name = "veda_especie")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class VedaEspecieModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "especie_id", nullable = false)
    private EspecieModel especie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id", nullable = true)
    private RegionModel region;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "extraccion_tipo_id", nullable = true)
    private ExtraccionTipoModel extraccionTipo;

    @Column(name = "recurrencia_anual", nullable = false)
    @Builder.Default
    private Boolean recurrenciaAnual = false;

    @Column(name = "meses_veda", length = 40)
    private String mesesVeda;

    @Column(name = "fecha_inicio", nullable = true)
    @Temporal(TemporalType.DATE)
    private Date fechaInicio;

    @Column(name = "fecha_fin", nullable = true)
    @Temporal(TemporalType.DATE)
    private Date fechaFin;

    @Column(name = "resolucion")
    private String resolucion;

    @Column(name = "activo", nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @Column(name = "observacion", columnDefinition = "TEXT")
    private String observacion;

}
