package com.trazalga.api.models;

import java.util.Date;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;

@Entity
@Table(name = "declaracion_marca")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class DeclaracionMarcaModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "declaracion_tipo", nullable = false, length = 30)
    private String declaracionTipo; // RECOLECTOR | ARMADOR | AREA

    @Column(name = "declaracion_id", nullable = false)
    private Long declaracionId;

    @Column(name = "marca", nullable = false, length = 40)
    private String marca; // EN_VEDA | LED_EXCEDIDO | CUOTA_EXCEDIDA | POSTERIOR_CIERRE | DESEMBARQUE_ATIPICO

    @Column(length = 500)
    private String detalle;

    @Column(name = "regla_id")
    private Long reglaId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean resuelta = false;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Date createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = new Date();
    }
}
