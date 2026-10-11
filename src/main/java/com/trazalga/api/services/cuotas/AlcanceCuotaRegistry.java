package com.trazalga.api.services.cuotas;

import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.trazalga.api.models.CuotaExtraccionModel;

/**
 * Registro y resolutor de estrategias de alcance de cuotas (TM.1).
 * Inyecta todas las implementaciones de AlcanceCuota y resuelve
 * la estrategia correspondiente para cualquier CuotaExtraccionModel.
 */
@Component
public class AlcanceCuotaRegistry {

    private final Map<String, AlcanceCuota> estrategias = new LinkedHashMap<>();

    @Autowired
    public AlcanceCuotaRegistry(List<AlcanceCuota> lista) {
        if (lista != null) {
            for (AlcanceCuota a : lista) {
                estrategias.put(a.clave(), a);
            }
        }
    }

    public static AlcanceCuotaRegistry crearPorDefecto(
            com.trazalga.api.repositories.IComunaRepository comunaRepo,
            com.trazalga.api.repositories.IProvinciaRepository provRepo,
            com.trazalga.api.repositories.IUsuarioRepository usuRepo) {
        return new AlcanceCuotaRegistry(List.of(
                new AlcanceComunal(comunaRepo),
                new AlcanceProvincial(provRepo),
                new AlcanceRegional(),
                new AlcanceIndividualPlantilla(provRepo),
                new AlcanceIndividualPersona(usuRepo),
                new AlcanceAmerb(),
                new AlcanceMacrozona(),
                new AlcanceNacional()
        ));
    }

    /**
     * Resuelve la estrategia de alcance para una cuota dada.
     */
    public AlcanceCuota resolver(CuotaExtraccionModel c) {
        if (c == null) {
            return estrategias.get(AlcanceComunal.CLAVE);
        }

        if (Boolean.TRUE.equals(c.getEsPlantilla())) {
            return estrategias.get(AlcanceIndividualPlantilla.CLAVE);
        }

        if (c.getUsuario() != null) {
            return estrategias.get(AlcanceIndividualPersona.CLAVE);
        }

        if (c.getAmerb() != null || "AREA".equalsIgnoreCase(c.getPerfil())) {
            return estrategias.get(AlcanceAmerb.CLAVE);
        }

        // Si tiene macrozona explícita y no tiene comunas ni provincia asignadas
        if (c.getMacrozona() != null && AlcanceComunal.idsComunas(c).isEmpty() && c.getProvincia() == null) {
            return Boolean.TRUE.equals(c.getMacrozona().getEsNacional())
                    ? estrategias.get(AlcanceNacional.CLAVE)
                    : estrategias.get(AlcanceMacrozona.CLAVE);
        }

        String nivel = c.getNivelAgregacion() != null ? c.getNivelAgregacion().trim().toUpperCase() : "";
        if ("INDIVIDUAL".equals(nivel)) {
            if (Boolean.TRUE.equals(c.getEsPlantilla())) {
                return estrategias.get(AlcanceIndividualPlantilla.CLAVE);
            }
            return estrategias.get(AlcanceIndividualPersona.CLAVE);
        }

        if ("MACROZONA".equals(nivel) || "NACIONAL".equals(nivel)) {
            if (c.getMacrozona() != null && Boolean.TRUE.equals(c.getMacrozona().getEsNacional())) {
                return estrategias.get(AlcanceNacional.CLAVE);
            }
            return estrategias.get(nivel);
        }

        if ("PROVINCIA".equals(nivel)) {
            return estrategias.get(AlcanceProvincial.CLAVE);
        }

        if ("REGION".equals(nivel)) {
            return estrategias.get(AlcanceRegional.CLAVE);
        }

        if (estrategias.containsKey(nivel) && !"COMUNA".equals(nivel)) {
            return estrategias.get(nivel);
        }

        // Si nivel es COMUNA (o default) pero no tiene comunas asociadas, inferir por otros campos
        if (AlcanceComunal.idsComunas(c).isEmpty()) {
            if (c.getProvincia() != null) {
                return estrategias.get(AlcanceProvincial.CLAVE);
            }
            if (c.getRegion() != null) {
                return estrategias.get(AlcanceRegional.CLAVE);
            }
            if (c.getMacrozona() != null) {
                return Boolean.TRUE.equals(c.getMacrozona().getEsNacional())
                        ? estrategias.get(AlcanceNacional.CLAVE)
                        : estrategias.get(AlcanceMacrozona.CLAVE);
            }
        }

        return estrategias.getOrDefault(AlcanceComunal.CLAVE, estrategias.values().iterator().next());
    }

    public AlcanceCuota getPorClave(String clave) {
        return estrategias.get(clave);
    }

    public Map<String, AlcanceCuota> getEstrategias() {
        return Collections.unmodifiableMap(estrategias);
    }
}
