package com.trazalga.api.models;

import java.util.Date;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;

@Entity
@Table(name = "configuracion_auditoria")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class ConfiguracionAuditoriaModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String entidad; // CONFIGURACION_GENERAL | FACTOR_CONVERSION | CUOTA | VEDA | LED

    @Column(name = "entidad_id", length = 100)
    private String entidadId;

    @Column(length = 100)
    private String campo;

    @Column(name = "valor_anterior", length = 1000)
    private String valorAnterior;

    @Column(name = "valor_nuevo", length = 1000)
    private String valorNuevo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private UsuarioModel usuario;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Date createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = new Date();
    }
}
