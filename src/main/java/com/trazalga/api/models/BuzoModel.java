package com.trazalga.api.models;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "buzo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BuzoModel {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100, unique = true)
    private String nombre;

    @Column(nullable = false, length = 50, unique = true)
    private String codigo;
}