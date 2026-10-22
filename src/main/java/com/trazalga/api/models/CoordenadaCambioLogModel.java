package com.trazalga.api.models;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Entity
@Table(name = "coordenada_cambio_log", indexes = {
    @Index(name = "idx_coordenada_log_entidad", columnList = "entidad_tipo, entidad_id"),
    @Index(name = "idx_coordenada_log_fecha", columnList = "actualizado_en")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class CoordenadaCambioLogModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entidad_tipo", nullable = false, length = 20)
    private String entidadTipo; // CALETA, VARADERO, AMERB

    @Column(name = "entidad_id", nullable = false)
    private Long entidadId;

    @Column(name = "entidad_nombre", length = 150)
    private String entidadNombre;

    @Column(name = "latitud_anterior")
    private Double latitudAnterior;

    @Column(name = "longitud_anterior")
    private Double longitudAnterior;

    @Column(name = "fuente_anterior", length = 100)
    private String fuenteAnterior;

    @Column(name = "latitud_nueva", nullable = false)
    private Double latitudNueva;

    @Column(name = "longitud_nueva", nullable = false)
    private Double longitudNueva;

    @Column(name = "fuente_nueva", nullable = false, length = 100)
    private String fuenteNueva;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    @Column(name = "actualizado_por", length = 100)
    private String actualizadoPor;
}
