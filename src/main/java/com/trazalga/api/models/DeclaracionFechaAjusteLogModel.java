package com.trazalga.api.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * ZT.2 · Registro de ajustes (o detección de reloj inconsistente) cuando una
 * declaración llega con fecha futura por desfase UTC en el dispositivo móvil.
 */
@Entity
@Table(name = "declaracion_fecha_ajuste_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeclaracionFechaAjusteLogModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "declaracion_tipo", nullable = false, length = 40)
    private String declaracionTipo;

    @Column(name = "declaracion_id")
    private Long declaracionId;

    @Temporal(TemporalType.DATE)
    @Column(name = "fecha_original", nullable = false)
    private Date fechaOriginal;

    @Temporal(TemporalType.DATE)
    @Column(name = "fecha_guardada", nullable = false)
    private Date fechaGuardada;

    @Column(name = "hora_declarada", length = 16)
    private String horaDeclarada;

    @Column(name = "motivo", nullable = false, length = 60)
    private String motivo;

    @Column(name = "fecha_servidor", nullable = false)
    private LocalDateTime fechaServidor;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    void prePersist() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
    }
}
