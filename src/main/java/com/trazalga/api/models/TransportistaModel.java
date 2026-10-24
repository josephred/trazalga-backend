package com.trazalga.api.models;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "transportista")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransportistaModel {

    public static final String ESTADO_ACTIVO = "ACTIVO";
    public static final String ESTADO_FUSIONADO = "FUSIONADO";
    public static final String ESTADO_REVISAR = "REVISAR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rut", length = 12, unique = true)
    private String rut;

    @Column(name = "rut_valido", nullable = false)
    @Builder.Default
    private Boolean rutValido = false;

    @Column(name = "nombre_canonico", nullable = false, length = 150)
    private String nombreCanonico;

    @Column(name = "nombre_normalizado", nullable = false, length = 150)
    private String nombreNormalizado;

    @Column(name = "comercializador_ref")
    private Long comercializadorRef;

    @Column(name = "estado", nullable = false, length = 20)
    @Builder.Default
    private String estado = ESTADO_ACTIVO;

    @Column(name = "fusionado_en_id")
    private Long fusionadoEnId;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false)
    private Date createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_at")
    private Date updatedAt;

    @OneToMany(mappedBy = "transportista", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<TransportistaAliasModel> alias = new ArrayList<>();
}
