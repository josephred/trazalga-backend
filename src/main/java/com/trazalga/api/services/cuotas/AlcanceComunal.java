package com.trazalga.api.services.cuotas;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.models.RegionModel;
import com.trazalga.api.repositories.IComunaRepository;

@Component("alcanceComunal")
public class AlcanceComunal implements AlcanceCuota {

    public static final String CLAVE = "COMUNA";

    @Autowired
    @Lazy
    private IComunaRepository comunaRepository;

    public AlcanceComunal() {}

    public AlcanceComunal(IComunaRepository comunaRepository) {
        this.comunaRepository = comunaRepository;
    }

    @Override
    public String clave() {
        return CLAVE;
    }

    @Override
    public void validarYNormalizar(CuotaExtraccionModel c) {
        Set<Long> cIds = idsComunas(c);
        if (cIds.isEmpty()) {
            throw new IllegalArgumentException("Las cuotas de nivel COMUNA deben tener al menos una comuna asociada.");
        }

        // Validar que todas las comunas pertenezcan a la misma región
        Long regId = null;
        RegionModel regObj = null;

        if (c.getComunas() != null && !c.getComunas().isEmpty()) {
            for (ComunaModel com : c.getComunas()) {
                RegionModel r = com.getRegion();
                if (r == null && com.getId() != null && comunaRepository != null) {
                    r = comunaRepository.findById(com.getId()).map(ComunaModel::getRegion).orElse(null);
                }
                if (r != null) {
                    if (regId == null) {
                        regId = r.getId();
                        regObj = r;
                    } else if (!regId.equals(r.getId())) {
                        throw new IllegalArgumentException("Todas las comunas seleccionadas deben pertenecer a la misma región.");
                    }
                }
            }
            if (regObj != null) {
                c.setRegion(regObj);
            }
            c.setProvincia(null);
            c.setMacrozona(null);
            if (c.getComuna() == null && !c.getComunas().isEmpty()) {
                c.setComuna(c.getComunas().iterator().next());
            }
        } else if (c.getComuna() != null) {
            if (c.getComuna().getRegion() != null) {
                c.setRegion(c.getComuna().getRegion());
            } else if (c.getComuna().getId() != null && comunaRepository != null) {
                comunaRepository.findById(c.getComuna().getId())
                        .map(ComunaModel::getRegion)
                        .ifPresent(c::setRegion);
            }
            c.setProvincia(null);
            c.setMacrozona(null);
            if (c.getComunas() == null || c.getComunas().isEmpty()) {
                Set<ComunaModel> set = new LinkedHashSet<>();
                set.add(c.getComuna());
                c.setComunas(set);
            }
        }
    }

    @Override
    public boolean mismoAlcance(CuotaExtraccionModel a, CuotaExtraccionModel b) {
        Set<Long> aIds = idsComunas(a);
        Set<Long> bIds = idsComunas(b);
        Set<Long> inter = new LinkedHashSet<>(aIds);
        inter.retainAll(bIds);
        return !inter.isEmpty();
    }

    @Override
    public String describir(CuotaExtraccionModel c) {
        if (c.getComunas() != null && c.getComunas().size() > 1) {
            String nombres = c.getComunas().stream()
                    .map(cm -> cm.getNombre() != null ? cm.getNombre() : String.valueOf(cm.getId()))
                    .collect(Collectors.joining(" + "));
            return "Comunas " + nombres;
        }
        if (c.getComuna() != null) {
            String nombre = c.getComuna().getNombre();
            return (nombre != null && !nombre.isBlank()) ? "Comuna " + nombre : "Comuna " + c.getComuna().getId();
        }
        Set<Long> cIds = idsComunas(c);
        if (!cIds.isEmpty()) {
            return "Comunas " + cIds.stream().map(String::valueOf).collect(Collectors.joining(" + "));
        }
        return "Comuna no especificada";
    }

    @Override
    public String describirConflicto(CuotaExtraccionModel c, CuotaExtraccionModel otra) {
        Set<Long> aIds = idsComunas(c);
        Set<Long> bIds = idsComunas(otra);
        Set<Long> inter = new LinkedHashSet<>(aIds);
        inter.retainAll(bIds);
        if (!inter.isEmpty()) {
            Long cId = inter.iterator().next();
            String nombre = null;
            if (c.getComunas() != null) {
                for (ComunaModel cm : c.getComunas()) {
                    if (cm != null && cId.equals(cm.getId()) && cm.getNombre() != null) {
                        nombre = cm.getNombre();
                        break;
                    }
                }
            }
            if (nombre == null && otra.getComunas() != null) {
                for (ComunaModel cm : otra.getComunas()) {
                    if (cm != null && cId.equals(cm.getId()) && cm.getNombre() != null) {
                        nombre = cm.getNombre();
                        break;
                    }
                }
            }
            if (nombre == null && c.getComuna() != null && cId.equals(c.getComuna().getId())) {
                nombre = c.getComuna().getNombre();
            }
            if (nombre == null && otra.getComuna() != null && cId.equals(otra.getComuna().getId())) {
                nombre = otra.getComuna().getNombre();
            }
            return "La comuna " + (nombre != null ? nombre : ("ID " + cId));
        }
        return describir(otra);
    }

    @Override
    public boolean comparableEnJerarquia() {
        return true;
    }

    public static Set<Long> idsComunas(CuotaExtraccionModel c) {
        if (c == null) return Collections.emptySet();
        Set<Long> ids = new LinkedHashSet<>();
        if (c.getComunas() != null && !c.getComunas().isEmpty()) {
            for (ComunaModel com : c.getComunas()) {
                if (com != null && com.getId() != null) {
                    ids.add(com.getId());
                }
            }
        }
        if (ids.isEmpty() && c.getComuna() != null && c.getComuna().getId() != null) {
            ids.add(c.getComuna().getId());
        }
        return ids;
    }
}
