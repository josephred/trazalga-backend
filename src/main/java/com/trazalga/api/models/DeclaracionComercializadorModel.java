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
@Table(name="declaracion_comercializador")
public class DeclaracionComercializadorModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = true)
    private String folioOrigen;
    
    @Column(nullable = true)
    private String folioDeclaracionAC;

    @Temporal(TemporalType.DATE)
    @Column(nullable = true)
    private Date fechaDeclaracion;

    @Column(nullable = true)
    private String hora;

    @Column(nullable = true)
    private String codigoSernapesca;

    @Column(nullable = true)
    private String nombreComercializador;

    @Column(nullable = true)
    private String georreferencia;

    @Column(nullable = true)
    private String especie;

    @Column(nullable = true)
    private String composicion;

    @Column(nullable = true)
    private String estadoHumedad;

    @Column(nullable = true)
    private String cantidad;

    @Column(nullable = true)
    private String documentoTributarioOrigen;

    @Column(nullable = true)
    private String documentoTributarioDestino;

    @Column(nullable = true)
    private String vehiculoTransporte;

    @Column(nullable = true)
    private String choferTransporte;

    @Column(nullable = true)
    private String codigoDestinatario;
 
    
    @Column(nullable = true)
    private String nombreDestinatario;
 
    @Column(nullable = true)
    private String patente;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFolioOrigen() {
        return folioOrigen;
    }

    public void setFolioOrigen(String folioOrigen) {
        this.folioOrigen = folioOrigen;
    }

    public String getFolioDeclaracionAC() {
        return folioDeclaracionAC;
    }

    public void setFolioDeclaracionAC(String folioDeclaracionAC) {
        this.folioDeclaracionAC = folioDeclaracionAC;
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

    public String getCodigoSernapesca() {
        return codigoSernapesca;
    }

    public void setCodigoSernapesca(String codigoSernapesca) {
        this.codigoSernapesca = codigoSernapesca;
    }

    public String getNombreComercializador() {
        return nombreComercializador;
    }

    public void setNombreComercializador(String nombreComercializador) {
        this.nombreComercializador = nombreComercializador;
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

    public String getCantidad() {
        return cantidad;
    }

    public void setCantidad(String cantidad) {
        this.cantidad = cantidad;
    }

    public String getDocumentoTributarioOrigen() {
        return documentoTributarioOrigen;
    }

    public void setDocumentoTributarioOrigen(String documentoTributarioOrigen) {
        this.documentoTributarioOrigen = documentoTributarioOrigen;
    }

    public String getDocumentoTributarioDestino() {
        return documentoTributarioDestino;
    }

    public void setDocumentoTributarioDestino(String documentoTributarioDestino) {
        this.documentoTributarioDestino = documentoTributarioDestino;
    }

    public String getVehiculoTransporte() {
        return vehiculoTransporte;
    }

    public void setVehiculoTransporte(String vehiculoTransporte) {
        this.vehiculoTransporte = vehiculoTransporte;
    }

    public String getChoferTransporte() {
        return choferTransporte;
    }

    public void setChoferTransporte(String choferTransporte) {
        this.choferTransporte = choferTransporte;
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

    public String getPatente() {
        return patente;
    }

    public void setPatente(String patente) {
        this.patente = patente;
    }

    
}
