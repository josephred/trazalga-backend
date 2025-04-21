package com.trazalga.api.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "humedad")
public class HumedadEstadoModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String nombre;

    @Column
    private int rangoInicio;

    @Column
    private int rangoFin;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public int getRangoInicio() {
        return rangoInicio;
    }

    public void setRangoInicio(int rangoInicio) {
        this.rangoInicio = rangoInicio;
    }

    public int getRangoFin() {
        return rangoFin;
    }

    public void setRangoFin(int rangoFin) {
        this.rangoFin = rangoFin;
    }

    
}
