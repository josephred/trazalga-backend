package com.trazalga.api.services.cuotas;

import java.util.LinkedHashSet;

import org.springframework.stereotype.Component;

import com.trazalga.api.models.CuotaExtraccionModel;

@Component("alcanceRegional")
public class AlcanceRegional implements AlcanceCuota {

    public static final String CLAVE = "REGION";

    @Override
    public String clave() {
        return CLAVE;
    }

    @Override
    public void validarYNormalizar(CuotaExtraccionModel c) {
        if (c.getRegion() == null || (c.getRegion().getId() == null && c.getRegion().getNombre() == null)) {
            throw new IllegalArgumentException("Las cuotas de nivel REGION deben especificar una región.");
        }
        if (!AlcanceComunal.idsComunas(c).isEmpty()) {
            throw new IllegalArgumentException("Las cuotas de nivel REGION no deben tener comunas asociadas.");
        }

        c.setComuna(null);
        if (c.getComunas() != null) {
            c.getComunas().clear();
        } else {
            c.setComunas(new LinkedHashSet<>());
        }
        c.setProvincia(null);
        c.setMacrozona(null);
        c.setEsPlantilla(false);
        c.setNivelAgregacion(CLAVE);
    }

    @Override
    public boolean mismoAlcance(CuotaExtraccionModel a, CuotaExtraccionModel b) {
        if (a == null || b == null || a.getRegion() == null || b.getRegion() == null) {
            return false;
        }
        Long aId = a.getRegion().getId();
        Long bId = b.getRegion().getId();
        if (aId != null && bId != null) {
            return aId.equals(bId);
        }
        String aNom = a.getRegion().getNombre();
        String bNom = b.getRegion().getNombre();
        return aNom != null && aNom.equalsIgnoreCase(bNom);
    }

    @Override
    public String describir(CuotaExtraccionModel c) {
        if (c.getRegion() != null) {
            String nom = c.getRegion().getNombre();
            return (nom != null && !nom.isBlank()) ? "Región " + nom : "Región " + c.getRegion().getId();
        }
        return "Región no especificada";
    }

    @Override
    public String describirConflicto(CuotaExtraccionModel c, CuotaExtraccionModel otra) {
        if (otra.getRegion() != null) {
            String nom = otra.getRegion().getNombre();
            return (nom != null && !nom.isBlank()) ? "La región " + nom : "La región " + otra.getRegion().getId();
        }
        return "La región";
    }

    @Override
    public boolean comparableEnJerarquia() {
        return true;
    }
}
