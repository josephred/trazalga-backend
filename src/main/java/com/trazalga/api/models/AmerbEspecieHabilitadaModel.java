package com.trazalga.api.models;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;

@Entity
@Table(name = "amerb_especie_habilitada")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class AmerbEspecieHabilitadaModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "amerb_id", nullable = false)
    private AmerbModel amerb;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "especie_id", nullable = false)
    private EspecieModel especie;

    @Column(length = 100)
    private String resolucion;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

}
