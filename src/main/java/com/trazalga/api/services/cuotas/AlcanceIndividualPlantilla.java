package com.trazalga.api.services.cuotas;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.repositories.IProvinciaRepository;

@Component("alcanceIndividualPlantilla")
public class AlcanceIndividualPlantilla implements AlcanceCuota {

    public static final String CLAVE = "INDIVIDUAL_PLANTILLA";

    @Autowired
    @Lazy
    private IProvinciaRepository provinciaRepository;

    public AlcanceIndividualPlantilla() {}

    public AlcanceIndividualPlantilla(IProvinciaRepository provinciaRepository) {
        this.provinciaRepository = provinciaRepository;
    }

    @Override
    public String clave() {
        return CLAVE;
    }

    @Override
    public void validarYNormalizar(CuotaExtraccionModel c) {
        if (c.getUsuario() != null) {
            throw new IllegalArgumentException("Una plantilla individual (tope por persona) no debe tener un usuario específico asignado.");
        }

        boolean tieneRegion = c.getRegion() != null;
        boolean tieneProvincia = c.getProvincia() != null;
        boolean tieneComunas = !AlcanceComunal.idsComunas(c).isEmpty();

        if (!tieneRegion && !tieneProvincia && !tieneComunas) {
            throw new IllegalArgumentException("Las plantillas individuales deben especificar un territorio de inscripción (región, provincia o comunas).");
        }

        // Si tiene provincia pero no región cargada, normalizar región
        if (c.getProvincia() != null) {
            if (c.getProvincia().getRegion() != null) {
                c.setRegion(c.getProvincia().getRegion());
            } else if (c.getProvincia().getId() != null && provinciaRepository != null) {
                provinciaRepository.findById(c.getProvincia().getId())
                        .map(p -> p.getRegion())
                        .ifPresent(c::setRegion);
            }
        }

        c.setNivelAgregacion("INDIVIDUAL");
        c.setEsPlantilla(true);
        c.setMacrozona(null);
    }

    @Override
    public boolean mismoAlcance(CuotaExtraccionModel a, CuotaExtraccionModel b) {
        if (a == null || b == null) return false;
        if (!Boolean.TRUE.equals(a.getEsPlantilla()) || !Boolean.TRUE.equals(b.getEsPlantilla())) {
            return false;
        }
        if (a.getUsuario() != null || b.getUsuario() != null) {
            return false;
        }

        // Misma provincia
        if (a.getProvincia() != null && b.getProvincia() != null) {
            return a.getProvincia().getId() != null && a.getProvincia().getId().equals(b.getProvincia().getId());
        }

        // Comunas compartidas
        Set<Long> aComunas = AlcanceComunal.idsComunas(a);
        Set<Long> bComunas = AlcanceComunal.idsComunas(b);
        if (!aComunas.isEmpty() && !bComunas.isEmpty()) {
            Set<Long> inter = new LinkedHashSet<>(aComunas);
            inter.retainAll(bComunas);
            return !inter.isEmpty();
        }

        // Misma región
        if (a.getRegion() != null && b.getRegion() != null) {
            return a.getRegion().getId() != null && a.getRegion().getId().equals(b.getRegion().getId());
        }

        return false;
    }

    @Override
    public String describir(CuotaExtraccionModel c) {
        String territorio;
        if (!AlcanceComunal.idsComunas(c).isEmpty()) {
            if (c.getComunas() != null && c.getComunas().size() > 1) {
                territorio = "Comunas " + c.getComunas().stream()
                        .map(cm -> cm.getNombre() != null ? cm.getNombre() : String.valueOf(cm.getId()))
                        .collect(Collectors.joining(" + "));
            } else if (c.getComuna() != null) {
                territorio = "Comuna " + c.getComuna().getNombre();
            } else {
                territorio = "Comunas";
            }
        } else if (c.getProvincia() != null) {
            territorio = "Provincia " + (c.getProvincia().getNombre() != null ? c.getProvincia().getNombre() : c.getProvincia().getId());
        } else if (c.getRegion() != null) {
            territorio = "Región " + (c.getRegion().getNombre() != null ? c.getRegion().getNombre() : c.getRegion().getId());
        } else {
            territorio = "territorio general";
        }

        return "Por persona (tope general) - " + territorio;
    }

    @Override
    public boolean comparableEnJerarquia() {
        return false;
    }
}
