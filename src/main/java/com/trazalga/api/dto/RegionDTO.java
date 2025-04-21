package com.trazalga.api.dto;

public class RegionDTO {

    private Long id;
    private String nombre;
    
    public RegionDTO(String nombre) {
        this.nombre = nombre;
    }
    public RegionDTO() {
    }
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
    
}
