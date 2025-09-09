package com.trazalga.api.models;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

@Entity
@Table(name="declaracion_comercializador")
public class DeclaracionComercializadorModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private UsuarioModel usuario;
    
    @Column(nullable = true)
    private String folioOrigen;
    
    @Column(nullable = true)
    private String folioDesembarqueAc;

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

    @ManyToOne
    @JoinColumn(name = "especie_id")
    private EspecieModel especie;

    @ManyToOne
    @JoinColumn(name = "composicion_id")
    private ComposicionModel composicion;

    @ManyToOne
    @JoinColumn(name = "humedad_estado_id")
    private HumedadEstadoModel humedadEstado;

    @Column(nullable = true)
    private java.math.BigDecimal cantidad;
    // private Double cantidad;

    @Column
    private String documentoTributarioOrigenTipo;
    
    @Column
    private String documentoTributarioOrigenNumero;
    
    @Temporal(TemporalType.DATE)
    @Column
    private Date documentoTributarioOrigenFecha;

    @Column
    private String documentoTributarioDestinoTipo;
    
    @Column
    private String documentoTributarioDestinoNumero;
    
    @Temporal(TemporalType.DATE)
    @Column
    private Date documentoTributarioDestinoFecha;

    @Column(nullable = true)
    private String vehiculoTransporte;

    @Column(nullable = true)
    private String choferTransporte;

    @Column(nullable = true)
    private String codigoDestinatario;
 
    
    // @Column(nullable = true)
    // private String nombreDestinatario;
 
    // @ManyToOne
    // @JoinColumn(name = "usuario_destinatario_id")
    // private UsuarioModel nombreDestinatario;


    @Column(nullable = true)
    private String patente;

    @ManyToOne
    @JoinColumn(name = "usuario_destinatario_id")
    private UsuarioModel usuarioDestinatario;

    @Column(name = "declaracion_destinatario_id", nullable = true )
    private Long declaracionDestinatario;
    
    @Column(name = "declaraciones_seleccionadas", nullable = true )
    private String declaracionesSeleccionadas;

    public String getDeclaracionesSeleccionadas() {
        return declaracionesSeleccionadas;
    }

    public void setDeclaracionesSeleccionadas(String declaracionesSeleccionadas) {
        this.declaracionesSeleccionadas = declaracionesSeleccionadas;
    }

    public Long getDeclaracionDestinatario() {
        return declaracionDestinatario;
    }

    public void setDeclaracionDestinatario(Long declaracionDestinatario) {
        this.declaracionDestinatario = declaracionDestinatario;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UsuarioModel getUsuario() {
        return usuario;
    }

    public void setUsuario(UsuarioModel usuario) {
        this.usuario = usuario;
    }

    public String getFolioOrigen() {
        return folioOrigen;
    }

    public void setFolioOrigen(String folioOrigen) {
        this.folioOrigen = folioOrigen;
    }

    public String getFolioDesembarqueAc() {
        return folioDesembarqueAc;
    }

    public void setFolioDesembarqueAc(String folioDesembarqueAc) {
        this.folioDesembarqueAc = folioDesembarqueAc;
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

    public EspecieModel getEspecie() {
        return especie;
    }

    public void setEspecie(EspecieModel especie) {
        this.especie = especie;
    }

    public ComposicionModel getComposicion() {
        return composicion;
    }

    public void setComposicion(ComposicionModel composicion) {
        this.composicion = composicion;
    }

    public HumedadEstadoModel getHumedadEstado() {
        return humedadEstado;
    }

    public void setHumedadEstado(HumedadEstadoModel humedadEstado) {
        this.humedadEstado = humedadEstado;
    }

    public Double getCantidad() {
        return cantidad;
    }

    public void setCantidad(Double cantidad) {
        this.cantidad = cantidad;
    }

    public String getDocumentoTributarioOrigenTipo() {
        return documentoTributarioOrigenTipo;
    }

    public void setDocumentoTributarioOrigenTipo(String documentoTributarioOrigenTipo) {
        this.documentoTributarioOrigenTipo = documentoTributarioOrigenTipo;
    }

    public String getDocumentoTributarioOrigenNumero() {
        return documentoTributarioOrigenNumero;
    }

    public void setDocumentoTributarioOrigenNumero(String documentoTributarioOrigenNumero) {
        this.documentoTributarioOrigenNumero = documentoTributarioOrigenNumero;
    }

    public Date getDocumentoTributarioOrigenFecha() {
        return documentoTributarioOrigenFecha;
    }

    public void setDocumentoTributarioOrigenFecha(Date documentoTributarioOrigenFecha) {
        this.documentoTributarioOrigenFecha = documentoTributarioOrigenFecha;
    }

    public String getDocumentoTributarioDestinoTipo() {
        return documentoTributarioDestinoTipo;
    }

    public void setDocumentoTributarioDestinoTipo(String documentoTributarioDestinoTipo) {
        this.documentoTributarioDestinoTipo = documentoTributarioDestinoTipo;
    }

    public String getDocumentoTributarioDestinoNumero() {
        return documentoTributarioDestinoNumero;
    }

    public void setDocumentoTributarioDestinoNumero(String documentoTributarioDestinoNumero) {
        this.documentoTributarioDestinoNumero = documentoTributarioDestinoNumero;
    }

    public Date getDocumentoTributarioDestinoFecha() {
        return documentoTributarioDestinoFecha;
    }

    public void setDocumentoTributarioDestinoFecha(Date documentoTributarioDestinoFecha) {
        this.documentoTributarioDestinoFecha = documentoTributarioDestinoFecha;
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

    // public UsuarioModel getNombreDestinatario() {
    //     return nombreDestinatario;
    // }

    // public void setNombreDestinatario(UsuarioModel nombreDestinatario) {
    //     this.nombreDestinatario = nombreDestinatario;
    // }

    public String getPatente() {
        return patente;
        
    }

    public void setPatente(String patente) {
        this.patente = patente;
    }

    public UsuarioModel getUsuarioDestinatario() {
        return usuarioDestinatario;
    }

    public void setUsuarioDestinatario(UsuarioModel usuarioDestinatario) {
        this.usuarioDestinatario = usuarioDestinatario;
    }

        
}
