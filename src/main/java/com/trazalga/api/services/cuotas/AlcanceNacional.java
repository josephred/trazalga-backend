package com.trazalga.api.services.cuotas;

import org.springframework.stereotype.Component;

import com.trazalga.api.models.CuotaExtraccionModel;

@Component("alcanceNacional")
public class AlcanceNacional implements AlcanceCuota {

    public static final String CLAVE = "NACIONAL";

    @Override
    public String clave() {
        return CLAVE;
    }

    @Override
    public void validarYNormalizar(CuotaExtraccionModel c) {
        c.setNivelAgregacion("NACIONAL");
    }

    @Override
    public boolean mismoAlcance(CuotaExtraccionModel a, CuotaExtraccionModel b) {
        return true;
    }

    @Override
    public String describir(CuotaExtraccionModel c) {
        return "Nacional";
    }

    @Override
    public String describirConflicto(CuotaExtraccionModel c, CuotaExtraccionModel otra) {
        return "El ámbito nacional";
    }

    @Override
    public boolean comparableEnJerarquia() {
        return true;
    }
}
