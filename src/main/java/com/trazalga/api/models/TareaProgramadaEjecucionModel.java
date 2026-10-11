package com.trazalga.api.models;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tarea_programada_ejecucion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TareaProgramadaEjecucionModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100, nullable = false)
    private String nombre;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date inicio;

    @Temporal(TemporalType.TIMESTAMP)
    private Date fin;

    @Column(length = 20, nullable = false)
    @Builder.Default
    private String estado = "OK"; // OK | ERROR

    @Column(nullable = false)
    @Builder.Default
    private Integer procesados = 0;

    @Column(columnDefinition = "TEXT")
    private String mensaje;
}
