package com.trazalga.api.models;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

@Entity
@Table(name = "transportista_alias", uniqueConstraints = {
        @UniqueConstraint(name = "ux_alias", columnNames = {"transportista_id", "nombre_normalizado"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransportistaAliasModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transportista_id", nullable = false)
    private TransportistaModel transportista;

    @Column(name = "nombre_declarado", nullable = false, length = 150)
    private String nombreDeclarado;

    @Column(name = "nombre_normalizado", nullable = false, length = 150)
    private String nombreNormalizado;

    @Column(name = "veces", nullable = false)
    @Builder.Default
    private Integer veces = 1;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "primera_vez", nullable = false)
    private Date primeraVez;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "ultima_vez", nullable = false)
    private Date ultimaVez;
}
