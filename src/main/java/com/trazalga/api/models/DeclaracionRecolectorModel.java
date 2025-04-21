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
@Table(name = "declaracion_recolector")
public class DeclaracionRecolectorModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private UsuarioModel usuario;
 
    @Column(nullable = true)
    private String folioOrigen;
    
    @Column(nullable = true)
    private String folioDesembarqueRo;
    
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
    
    @ManyToOne
    @JoinColumn(name = "caleta_id")
    private CaletaModel caleta;
    
    @Column(nullable = true)
    private String georreferencia;
    
    @ManyToOne
    @JoinColumn(name = "especie_id")
    private EspecieModel especie;
    
    @ManyToOne
    @JoinColumn(name = "comuna_id")
    private ComunaModel comuna;
    
    @ManyToOne
    @JoinColumn(name = "extraccion_tipo_id")
    private ExtraccionTipoModel extraccionTipo;
    
    @ManyToOne
    @JoinColumn(name = "composicion_id")
    private ComposicionModel composicion;
    
    @ManyToOne
    @JoinColumn(name = "humedad_estado_id")
    private HumedadEstadoModel humedadEstado;

    @Column(nullable = true)
    private String humedad;
    
    @Column(nullable = true)
    private Double desembarque;
    
    @Column(nullable = true)
    private Double captura;
    
    @Column(nullable = true)
    private String codigoDestinatario;
    
    @ManyToOne
    @JoinColumn(name = "usuario_destinatario_id")
    private UsuarioModel usuarioDestinatario;

    @Column(name = "declaracion_destinatario_id", nullable = true )
    private Long declaracionDestinatario;

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

    public String getFolioDesembarqueRo() {
        return folioDesembarqueRo;
    }

    public void setFolioDesembarqueRo(String folioDesembarqueRo) {
        this.folioDesembarqueRo = folioDesembarqueRo;
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

    public CaletaModel getCaleta() {
        return caleta;
    }

    public void setCaleta(CaletaModel caleta) {
        this.caleta = caleta;
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

    public ComunaModel getComuna() {
        return comuna;
    }

    public void setComuna(ComunaModel comuna) {
        this.comuna = comuna;
    }

    public ExtraccionTipoModel getExtraccionTipo() {
        return extraccionTipo;
    }

    public void setExtraccionTipo(ExtraccionTipoModel extraccionTipo) {
        this.extraccionTipo = extraccionTipo;
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

    public String getHumedad() {
        return humedad;
    }

    public void setHumedad(String humedad) {
        this.humedad = humedad;
    }

    public Double getDesembarque() {
        return desembarque;
    }

    public void setDesembarque(Double desembarque) {
        this.desembarque = desembarque;
    }

    public Double getCaptura() {
        return captura;
    }

    public void setCaptura(Double captura) {
        this.captura = captura;
    }

    public String getCodigoDestinatario() {
        return codigoDestinatario;
    }

    public void setCodigoDestinatario(String codigoDestinatario) {
        this.codigoDestinatario = codigoDestinatario;
    }

    public UsuarioModel getUsuarioDestinatario() {
        return usuarioDestinatario;
    }

    public void setUsuarioDestinatario(UsuarioModel usuarioDestinatario) {
        this.usuarioDestinatario = usuarioDestinatario;
    }

    public Long getDeclaracionDestinatario() {
        return declaracionDestinatario;
    }

    public void setDeclaracionDestinatario(Long declaracionDestinatario) {
        this.declaracionDestinatario = declaracionDestinatario;
    }



}
