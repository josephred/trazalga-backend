package com.trazalga.api.dto;

public class RegionDTO {

    private Long id;
    private String nombre;
    
    private String codigo;
    
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
    public String getCodigo() {
        return codigo;
    }
    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }
}
