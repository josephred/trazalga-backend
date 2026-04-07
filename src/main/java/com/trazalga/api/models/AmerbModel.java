package com.trazalga.api.models;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;

@Entity
@Table(name = "amerb")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class AmerbModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 50)
    private String region;

    @Column(nullable = true, length = 255)
    private String ubicacion; // Puede ser una referencia a coordenadas o una dirección.

    @Column(nullable = true)
    private Double superficieHectareas; // Tamaño del área en hectáreas.

    @Column(nullable = true, length = 100)
    private String titular; // Titular de la concesión.

    @Column(nullable = true, length = 100)
    private String estado; // Estado de la AMERB (vigente, caducada, en trámite).

    @Column(nullable = true, length = 50)
    private String codigoSernapesca; // Código de identificación oficial.

    @Column(name = "folio_organizacion", nullable = true)
    private Integer folioOrganizacion;

}
