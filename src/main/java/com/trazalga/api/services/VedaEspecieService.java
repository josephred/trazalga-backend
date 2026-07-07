package com.trazalga.api.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.models.VedaEspecieModel;
import com.trazalga.api.repositories.IEspecieRepository;
import com.trazalga.api.repositories.IRegionRepository;
import com.trazalga.api.repositories.VedaEspecieRepository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class VedaEspecieService {

    @Autowired
    private VedaEspecieRepository vedaRepository;

    @Autowired
    private IEspecieRepository especieRepository;

    @Autowired
    private IRegionRepository regionRepository;

    public List<VedaEspecieModel> getAll() {
        return vedaRepository.findAll();
    }

    public Optional<VedaEspecieModel> getById(Long id) {
        return vedaRepository.findById(id);
    }

    public VedaEspecieModel save(VedaEspecieModel veda) {
        resolverReferencias(veda);
        validar(veda);
        return vedaRepository.save(veda);
    }

    /** El frontend envía referencias como {id: n}; se recargan completas para validar y responder con nombres. */
    private void resolverReferencias(VedaEspecieModel veda) {
        if (veda.getEspecie() != null && veda.getEspecie().getId() != null) {
            veda.setEspecie(especieRepository.findById(veda.getEspecie().getId())
                .orElseThrow(() -> new IllegalArgumentException("La especie indicada no existe.")));
        }
        if (veda.getRegion() != null && veda.getRegion().getId() != null) {
            veda.setRegion(regionRepository.findById(veda.getRegion().getId())
                .orElseThrow(() -> new IllegalArgumentException("La región indicada no existe.")));
        }
    }

    /**
     * Reglas de configuración: especie y rango de fechas obligatorios, y el rango
     * debe ser coherente (una veda con inicio > fin jamás coincidiría en las
     * consultas de vigencia y quedaría "muda" en el sistema).
     */
    private void validar(VedaEspecieModel veda) {
        if (veda.getEspecie() == null || veda.getEspecie().getId() == null) {
            throw new IllegalArgumentException("La veda debe indicar la especie afectada.");
        }
        if (veda.getFechaInicio() == null || veda.getFechaFin() == null) {
            throw new IllegalArgumentException("La veda debe indicar fecha de inicio y fecha de término.");
        }
        if (veda.getFechaInicio().after(veda.getFechaFin())) {
            throw new IllegalArgumentException("La fecha de inicio de la veda no puede ser posterior a la fecha de término.");
        }
    }

    /** Datos maestros recortados (especies y regiones) para los selects del mantenedor de vedas. */
    public java.util.Map<String, Object> getMaestros() {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("especies", especieRepository.findAll().stream()
            .map(e -> java.util.Map.of("id", e.getId(), "nombre", e.getNombre() != null ? e.getNombre() : ""))
            .toList());
        out.put("regiones", regionRepository.findAll().stream()
            .map(r -> java.util.Map.of("id", r.getId(), "nombre", r.getNombre() != null ? r.getNombre() : ""))
            .toList());
        return out;
    }

    public boolean delete(Long id) {
        try {
            vedaRepository.deleteById(id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isEspecieEnVeda(Long especieId, Long regionId, Date fecha) {
        List<VedaEspecieModel> vedas = vedaRepository.findVedasActivasPorEspecieYRegionYFecha(especieId, regionId, fecha);
        return !vedas.isEmpty();
    }
}
