package com.trazalga.api.services.cuotas;

import org.springframework.stereotype.Component;

import com.trazalga.api.models.CuotaExtraccionModel;

@Component("alcanceMacrozona")
public class AlcanceMacrozona implements AlcanceCuota {

    public static final String CLAVE = "MACROZONA";

    @Override
    public String clave() {
        return CLAVE;
    }

    @Override
    public void validarYNormalizar(CuotaExtraccionModel c) {
        if (c.getMacrozona() == null) {
            throw new IllegalArgumentException("Las cuotas de nivel MACROZONA deben especificar una macrozona.");
        }
        c.setNivelAgregacion("MACROZONA");
    }

    @Override
    public boolean mismoAlcance(CuotaExtraccionModel a, CuotaExtraccionModel b) {
        if (a == null || b == null || a.getMacrozona() == null || b.getMacrozona() == null) {
            return false;
        }
        return a.getMacrozona().getId() != null && a.getMacrozona().getId().equals(b.getMacrozona().getId());
    }

    @Override
    public String describir(CuotaExtraccionModel c) {
        if (c.getMacrozona() != null) {
            String nom = c.getMacrozona().getNombre();
            return (nom != null && !nom.isBlank()) ? "Macrozona " + nom : "Macrozona " + c.getMacrozona().getId();
        }
        return "Macrozona";
    }

    @Override
    public String describirConflicto(CuotaExtraccionModel c, CuotaExtraccionModel otra) {
        String base = describir(otra);
        return "La macrozona " + (base.startsWith("Macrozona ") ? base.substring(10) : base);
    }

    @Override
    public boolean comparableEnJerarquia() {
        return true;
    }
}
