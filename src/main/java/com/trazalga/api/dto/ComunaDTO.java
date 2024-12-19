package com.trazalga.api.dto;

public class ComunaDTO {

    private Long id;
    private String nombre;
    private RegionDTO region;

    public ComunaDTO() {
    }
    public ComunaDTO(String nombre, RegionDTO region) {
        this.nombre = nombre;
        this.region = region;
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
    public RegionDTO getRegion() {
        return region;
    }
    public void setRegion(RegionDTO region) {
        this.region = region;
    }

    
}
