package com.trazalga.api.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "configuracion_general")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfiguracionGeneralModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "clave", unique = true, nullable = false, length = 100)
    private String clave;

    @Column(name = "valor", nullable = false, length = 1000)
    private String valor;

    @Column(name = "descripcion", length = 255)
    private String descripcion;

    @Column(name = "categoria", length = 50)
    private String categoria;
}
