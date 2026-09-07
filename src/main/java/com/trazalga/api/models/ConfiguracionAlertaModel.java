package com.trazalga.api.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "configuracion_alerta")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfiguracionAlertaModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tipo_alerta", unique = true, nullable = false)
    private String tipoAlerta; // ej: EXTRACCION_VEDA, LIMITE_CUOTA

    @Column(name = "titulo", nullable = false)
    private String titulo;

    @Column(name = "umbral")
    private Double umbral; // ej: 90.0 para porcentaje

    @Column(name = "activo", nullable = false)
    private Boolean activo;

    @Column(name = "parametros_json", columnDefinition = "TEXT")
    private String parametrosJson;
}
