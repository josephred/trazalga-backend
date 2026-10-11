package com.trazalga.api.services.cuotas;

import java.util.LinkedHashSet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.models.ProvinciaModel;
import com.trazalga.api.repositories.IProvinciaRepository;

@Component("alcanceProvincial")
public class AlcanceProvincial implements AlcanceCuota {

    public static final String CLAVE = "PROVINCIA";

    @Autowired
    @Lazy
    private IProvinciaRepository provinciaRepository;

    public AlcanceProvincial() {}

    public AlcanceProvincial(IProvinciaRepository provinciaRepository) {
        this.provinciaRepository = provinciaRepository;
    }

    @Override
    public String clave() {
        return CLAVE;
    }

    @Override
    public void validarYNormalizar(CuotaExtraccionModel c) {
        if (c.getProvincia() == null || (c.getProvincia().getId() == null && c.getProvincia().getNombre() == null)) {
            throw new IllegalArgumentException("Las cuotas de nivel PROVINCIA deben especificar una provincia.");
        }

        // Cargar entidad completa de provincia si sólo viene el ID
        ProvinciaModel prov = c.getProvincia();
        if (prov.getRegion() == null && prov.getId() != null && provinciaRepository != null) {
            prov = provinciaRepository.findById(prov.getId()).orElse(prov);
            c.setProvincia(prov);
        }

        if (prov.getRegion() != null) {
            c.setRegion(prov.getRegion());
        }

        // Limpiar comunas, comuna cabecera y macrozona para evitar inconsistencias
        c.setComuna(null);
        if (c.getComunas() != null) {
            c.getComunas().clear();
        } else {
            c.setComunas(new LinkedHashSet<>());
        }
        c.setMacrozona(null);
        c.setEsPlantilla(false);
        c.setNivelAgregacion(CLAVE);
    }

    @Override
    public boolean mismoAlcance(CuotaExtraccionModel a, CuotaExtraccionModel b) {
        if (a == null || b == null || a.getProvincia() == null || b.getProvincia() == null) {
            return false;
        }
        Long aId = a.getProvincia().getId();
        Long bId = b.getProvincia().getId();
        if (aId != null && bId != null) {
            return aId.equals(bId);
        }
        String aNom = a.getProvincia().getNombre();
        String bNom = b.getProvincia().getNombre();
        return aNom != null && aNom.equalsIgnoreCase(bNom);
    }

    @Override
    public String describir(CuotaExtraccionModel c) {
        if (c.getProvincia() != null) {
            String nom = c.getProvincia().getNombre();
            return (nom != null && !nom.isBlank()) ? "Provincia " + nom : "Provincia " + c.getProvincia().getId();
        }
        return "Provincia no especificada";
    }

    @Override
    public String describirConflicto(CuotaExtraccionModel c, CuotaExtraccionModel otra) {
        if (otra.getProvincia() != null) {
            String nom = otra.getProvincia().getNombre();
            return (nom != null && !nom.isBlank()) ? "La provincia " + nom : "La provincia " + otra.getProvincia().getId();
        }
        return "La provincia";
    }

    @Override
    public boolean comparableEnJerarquia() {
        return true;
    }
}
