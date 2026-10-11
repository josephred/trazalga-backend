package com.trazalga.api.models;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entidad de persistencia para el historial de eventos del ciclo de vida de cuotas (TC.2).
 * Registra transiciones: CREADA, EDITADA, CERRADA, REABIERTA, ACTIVADA, DESACTIVADA, RECALCULADA.
 */
@Entity
@Table(name = "cuota_extraccion_evento", indexes = {
    @Index(name = "idx_cee_cuota", columnList = "cuota_id, created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CuotaExtraccionEventoModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuota_id", nullable = false)
    private CuotaExtraccionModel cuota;

    @Column(nullable = false, length = 20)
    private String tipo; // CREADA | EDITADA | CERRADA | REABIERTA | ACTIVADA | DESACTIVADA | RECALCULADA

    @Column(nullable = true, length = 30)
    private String motivo; // AGOTAMIENTO | ADMINISTRATIVO | VENCIMIENTO | o texto libre

    @Column(columnDefinition = "TEXT", nullable = true)
    private String detalle; // JSON o descripción de cambios

    @Column(name = "usuario_id", nullable = true)
    private Long usuarioId;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Date createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = new Date();
        }
    }
}
