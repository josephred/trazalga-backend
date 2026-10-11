package com.trazalga.api.services.cuotas;

import org.springframework.stereotype.Component;

import com.trazalga.api.models.CuotaExtraccionModel;

@Component("alcanceAmerb")
public class AlcanceAmerb implements AlcanceCuota {

    public static final String CLAVE = "AREA";

    @Override
    public String clave() {
        return CLAVE;
    }

    @Override
    public void validarYNormalizar(CuotaExtraccionModel c) {
        if (c.getAmerb() == null) {
            throw new IllegalArgumentException("Las cuotas de ámbito AMERB deben especificar un área de manejo.");
        }
        c.setNivelAgregacion("AREA");
        c.setAmbito("AMERB");
        if (c.getAmerb().getRegionModel() != null) {
            c.setRegion(c.getAmerb().getRegionModel());
        }
        if (c.getAmerb().getComuna() != null) {
            c.setComuna(c.getAmerb().getComuna());
        }
    }

    @Override
    public boolean mismoAlcance(CuotaExtraccionModel a, CuotaExtraccionModel b) {
        if (a == null || b == null || a.getAmerb() == null || b.getAmerb() == null) {
            return false;
        }
        return a.getAmerb().getId() != null && a.getAmerb().getId().equals(b.getAmerb().getId());
    }

    @Override
    public String describir(CuotaExtraccionModel c) {
        if (c.getAmerb() != null) {
            String nombre = c.getAmerb().getNombre();
            if (nombre == null || nombre.trim().isEmpty()) return "AMERB " + c.getAmerb().getId();
            return nombre.toUpperCase().startsWith("AMERB") ? nombre : "AMERB " + nombre;
        }
        return "Área de Manejo";
    }

    @Override
    public String describirConflicto(CuotaExtraccionModel c, CuotaExtraccionModel otra) {
        String base = describir(otra);
        return "El área de manejo " + (base.startsWith("AMERB ") ? base.substring(6) : base);
    }

    @Override
    public boolean comparableEnJerarquia() {
        return true;
    }
}
