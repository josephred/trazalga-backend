package com.trazalga.api.models;

import java.util.Date;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;

@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class UsuarioModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String rut;

    private String nombres;
    private String apellidop;
    private String apellidom;
    private String correo;
    private String estado;
    private String clave;

    // MEJORA: Nombre estándar Java y anotación para fecha y hora completa
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "fecha_creacion", updatable = false)
    private Date fechaCreacion;

    @ManyToOne
    @JoinColumn(name = "perfil_id")
    private PerfilModel perfil;

    @ManyToOne
    @JoinColumn(name = "comuna_id")
    private ComunaModel comuna;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "usuario_embarcacion",
        joinColumns = @JoinColumn(name = "usuario_id"),
        inverseJoinColumns = @JoinColumn(name = "embarcacion_id")
    )
    @com.fasterxml.jackson.annotation.JsonIgnore
    private java.util.List<EmbarcacionModel> embarcaciones = new java.util.ArrayList<>();

    // Este método se ejecuta automáticamente justo antes de insertar en la BD
    @PrePersist
    protected void onCreate() {
        if (this.fechaCreacion == null) {
            this.fechaCreacion = new Date();
        }
        if (this.estado == null) {
            this.estado = "ACTIVO";
        }
    }
}