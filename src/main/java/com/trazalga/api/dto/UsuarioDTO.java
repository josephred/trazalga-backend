package com.trazalga.api.dto;

import java.util.Date;

public class UsuarioDTO {
 
    private Long id;
    private String rut;
    private String nombres;
    private String apellidop;
    private String apellidom;
    private String correo;
    private Date fechaCreacion;
    private String estado;
    private String clave;
    private ComunaDTO comuna;
    private PerfilDTO perfil;

    public UsuarioDTO(String rut, String nombres, String apellidop, String apellidom, String correo, Date fechaCreacion,
            String estado, String clave, ComunaDTO comuna, PerfilDTO perfil) {
        this.rut = rut;
        this.nombres = nombres;
        this.apellidop = apellidop;
        this.apellidom = apellidom;
        this.correo = correo;
        this.fechaCreacion = fechaCreacion;
        this.estado = estado;
        this.clave = clave;
        this.comuna = comuna;
        this.perfil = perfil;
    }

    public UsuarioDTO() {
    }
    
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getRut() {
        return rut;
    }
    public void setRut(String rut) {
        this.rut = rut;
    }
    public String getNombres() {
        return nombres;
    }
    public void setNombres(String nombres) {
        this.nombres = nombres;
    }
    public String getApellidop() {
        return apellidop;
    }
    public void setApellidop(String apellidop) {
        this.apellidop = apellidop;
    }
    public String getApellidom() {
        return apellidom;
    }
    public void setApellidom(String apellidom) {
        this.apellidom = apellidom;
    }
    public String getCorreo() {
        return correo;
    }
    public void setCorreo(String correo) {
        this.correo = correo;
    }
    public Date getFechaCreacion() {
        return fechaCreacion;
    }
    public void setFechaCreacion(Date fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }
    public String getEstado() {
        return estado;
    }
    public void setEstado(String estado) {
        this.estado = estado;
    }
    public String getClave() {
        return clave;
    }
    public void setClave(String clave) {
        this.clave = clave;
    }
    public ComunaDTO getComuna() {
        return comuna;
    }
    public void setComuna(ComunaDTO comuna) {
        this.comuna = comuna;
    }
    public PerfilDTO getPerfil() {
        return perfil;
    }
    public void setPerfil(PerfilDTO perfil) {
        this.perfil = perfil;
    }
    
    
}
