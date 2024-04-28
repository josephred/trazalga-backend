package com.trazalga.api.models;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

@Entity
@Table(name = "declaracion_recolector")
public class DeclaracionRecolectorModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String codigoLote;
    
    @Column(nullable = true)
    private String folioOrigen;
    
    @Column(nullable = true)
    private String folioDesembarqueRO;
    
    @Temporal(TemporalType.DATE)
    @Column(nullable = true)
    private Date fechaExtraccion;
    
    @Temporal(TemporalType.DATE)
    @Column(nullable = true)
    private Date fechaDeclaracion;
    
    @Column(nullable = true)
    private String hora;
    
    @Column(nullable = true)
    private String nombre;
    
    @Column(nullable = true)
    private String codigoSernapesca;
    
    @Column(nullable = true)
    private String varadero;
    
    @Column(nullable = true)
    private String caleta;
    
    @Column(nullable = true)
    private String georreferencia;
    
    @Column(nullable = true)
    private String especie;
    
    @Column(nullable = true)
    private String comuna;
    
    @Column(nullable = true)
    private String tipoExtraccion;
    
    @Column(nullable = true)
    private String composicion;
    
    @Column(nullable = true)
    private String estadoHumedad;
    
    @Column(nullable = true)
    private double desembarque;
    
    @Column(nullable = true)
    private double captura;
    
    @Column(nullable = true)
    private String codigoDestinatario;
    
    @Column(nullable = true)
    private String nombreDestinatario;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodigoLote() {
        return codigoLote;
    }

    public void setCodigoLote(String codigoLote) {
        this.codigoLote = codigoLote;
    }

    public String getFolioOrigen() {
        return folioOrigen;
    }

    public void setFolioOrigen(String folioOrigen) {
        this.folioOrigen = folioOrigen;
    }

    public String getFolioDesembarqueRO() {
        return folioDesembarqueRO;
    }

    public void setFolioDesembarqueRO(String folioDesembarqueRO) {
        this.folioDesembarqueRO = folioDesembarqueRO;
    }

    public Date getFechaExtraccion() {
        return fechaExtraccion;
    }

    public void setFechaExtraccion(Date fechaExtraccion) {
        this.fechaExtraccion = fechaExtraccion;
    }

    public Date getFechaDeclaracion() {
        return fechaDeclaracion;
    }

    public void setFechaDeclaracion(Date fechaDeclaracion) {
        this.fechaDeclaracion = fechaDeclaracion;
    }

    public String getHora() {
        return hora;
    }

    public void setHora(String hora) {
        this.hora = hora;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getCodigoSernapesca() {
        return codigoSernapesca;
    }

    public void setCodigoSernapesca(String codigoSernapesca) {
        this.codigoSernapesca = codigoSernapesca;
    }

    public String getVaradero() {
        return varadero;
    }

    public void setVaradero(String varadero) {
        this.varadero = varadero;
    }

    public String getCaleta() {
        return caleta;
    }

    public void setCaleta(String caleta) {
        this.caleta = caleta;
    }

    public String getGeorreferencia() {
        return georreferencia;
    }

    public void setGeorreferencia(String georreferencia) {
        this.georreferencia = georreferencia;
    }

    public String getEspecie() {
        return especie;
    }

    public void setEspecie(String especie) {
        this.especie = especie;
    }

    public String getComuna() {
        return comuna;
    }

    public void setComuna(String comuna) {
        this.comuna = comuna;
    }

    public String getTipoExtraccion() {
        return tipoExtraccion;
    }

    public void setTipoExtraccion(String tipoExtraccion) {
        this.tipoExtraccion = tipoExtraccion;
    }

    public String getComposicion() {
        return composicion;
    }

    public void setComposicion(String composicion) {
        this.composicion = composicion;
    }

    public String getEstadoHumedad() {
        return estadoHumedad;
    }

    public void setEstadoHumedad(String estadoHumedad) {
        this.estadoHumedad = estadoHumedad;
    }

    public double getDesembarque() {
        return desembarque;
    }

    public void setDesembarque(double desembarque) {
        this.desembarque = desembarque;
    }

    public double getCaptura() {
        return captura;
    }

    public void setCaptura(double captura) {
        this.captura = captura;
    }

    public String getCodigoDestinatario() {
        return codigoDestinatario;
    }

    public void setCodigoDestinatario(String codigoDestinatario) {
        this.codigoDestinatario = codigoDestinatario;
    }

    public String getNombreDestinatario() {
        return nombreDestinatario;
    }

    public void setNombreDestinatario(String nombreDestinatario) {
        this.nombreDestinatario = nombreDestinatario;
    }

}
