package com.trazalga.api.dto; // Puedes crear un paquete para los DTOs

import java.math.BigDecimal;
import java.util.Date;
import lombok.Data; // Lombok es ideal para esto

import com.trazalga.api.models.DeclaracionAreaModel;
import com.trazalga.api.models.DeclaracionArmadorModel;
import com.trazalga.api.models.DeclaracionComercializadorModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;

@Data // Genera getters, setters, toString, etc. automáticamente
public class DeclaracionResumenDTO {

    private Long id;
    private String tipoDeclaracion; // "RECOLECTOR", "ARMADOR", "AREA", "COMERCIALIZADOR"
    private String folioPrincipal; // Usaremos el folio de desembarque o similar
    private Date fechaDeclaracion;
    private String nombreOriginador; // Nombre de quien hizo la declaración (Recolector, Armador, etc.)
    private String perfilOriginador; // "Recolector", "Armador", etc.
    private BigDecimal cantidad; // Cantidad en Kg (usaremos 'desembarque' o 'captura')
    private String nombreEspecie;
    private boolean isEditable; // Lógica para determinar si se puede editar

    // Constructor para mapear desde DeclaracionRecolectorModel
    public DeclaracionResumenDTO(DeclaracionRecolectorModel model) {
        this.id = model.getId();
        this.tipoDeclaracion = "RECOLECTOR";
        this.folioPrincipal = model.getFolioDesembarqueRo();
        this.fechaDeclaracion = model.getFechaDeclaracion();
        if (model.getUsuario() != null) {
            this.nombreOriginador = model.getUsuario().getNombres() + " " + model.getUsuario().getApellidop();
            if (model.getUsuario().getPerfil() != null) {
                 this.perfilOriginador = model.getUsuario().getPerfil().getNombre();
            }
        }
        this.cantidad = model.getDesembarque();
        if (model.getEspecie() != null) {
            this.nombreEspecie = model.getEspecie().getNombre();
        }
        this.isEditable = true; // Define tu lógica aquí
    }

    // Constructor para mapear desde DeclaracionArmadorModel
    public DeclaracionResumenDTO(DeclaracionArmadorModel model) {
        this.id = model.getId();
        this.tipoDeclaracion = "ARMADOR";
        this.folioPrincipal = model.getFolioDesembarqueDa();
        this.fechaDeclaracion = model.getFechaDeclaracion();
        if (model.getUsuario() != null) {
            this.nombreOriginador = model.getUsuario().getNombres() + " " + model.getUsuario().getApellidop();
             if (model.getUsuario().getPerfil() != null) {
                 this.perfilOriginador = model.getUsuario().getPerfil().getNombre();
            }
        }
        this.cantidad = model.getDesembarque();
        if (model.getEspecie() != null) {
            this.nombreEspecie = model.getEspecie().getNombre();
        }
        this.isEditable = true;
    }
    
    // Agrega constructores similares para DeclaracionAreaModel y DeclaracionComercializadorModel...
    // Ejemplo para DeclaracionAreaModel:
    public DeclaracionResumenDTO(DeclaracionAreaModel model) {
        this.id = model.getId();
        this.tipoDeclaracion = "AREA";
        this.folioPrincipal = model.getFolioDesembarqueAmerb();
        // ... completar el resto de campos
    }
    
    // Ejemplo para DeclaracionComercializadorModel
    public DeclaracionResumenDTO(DeclaracionComercializadorModel model) {
        this.id = model.getId();
        this.tipoDeclaracion = "COMERCIALIZADOR";
        this.folioPrincipal = model.getFolioDesembarqueAc();
        // ... completar el resto de campos
    }
}