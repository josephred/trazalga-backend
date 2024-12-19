package com.trazalga.api.models;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "usuario")
public class UsuarioModel {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = true)
    private String rut;

    @Column(nullable = true)
    private String nombres;

    @Column(nullable = true)
    private String apellidop;

    @Column(nullable = true)
    private String apellidom;

    @Column(nullable = true)
    private String correo;

    // @Temporal(TemporalType.DATE)
    @Column(nullable = true)
    private Date fecha_creacion;

    @Column(nullable = true)
    private String estado;
    
    @ManyToOne
    @JoinColumn(name = "perfil_id") // Nombre de la columna que almacenará la clave foránea
    private PerfilModel perfil;

    @Column
    private String clave;

        
    @PrePersist
    protected void onCreate() {
        fecha_creacion = new Date();
    }

    // @ManyToOne(fetch = FetchType.EAGER)
    // @JsonIgnore
    // @ManyToOne(fetch = FetchType.LAZY)
    // @ManyToOne(fetch = FetchType.EAGER)
    @ManyToOne
    @JoinColumn(name = "comuna_id")
    private ComunaModel comuna;

    

    public ComunaModel getComuna() {
        return comuna;
    }


    public void setComuna(ComunaModel comuna) {
        this.comuna = comuna;
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


    public Date getFecha_creacion() {
        return fecha_creacion;
    }


    public void setFecha_creacion(Date fecha_creacion) {
        this.fecha_creacion = fecha_creacion;
    }


    public String getEstado() {
        return estado;
    }


    public void setEstado(String estado) {
        this.estado = estado;
    }


    public PerfilModel getPerfil() {
        return perfil;
    }


    public void setPerfil(PerfilModel perfil) {
        this.perfil = perfil;
    }


    public String getClave() {
        return clave;
    }


    public void setClave(String clave) {
        this.clave = clave;
    }

    
}
