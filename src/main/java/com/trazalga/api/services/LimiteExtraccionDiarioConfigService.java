package com.trazalga.api.services;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.ExtraccionTipoModel;
import com.trazalga.api.models.LimiteExtraccionDiarioConfigModel;
import com.trazalga.api.repositories.IEspecieRepository;
import com.trazalga.api.repositories.IExtraccionTipoRepository;
import com.trazalga.api.repositories.ILimiteExtraccionDiarioConfigRepository;
import com.trazalga.api.repositories.IRegionRepository;

@Service
@Slf4j
public class LimiteExtraccionDiarioConfigService {

    @Autowired
    private ILimiteExtraccionDiarioConfigRepository repository;

    @Autowired
    private IEspecieRepository especieRepository;

    @Autowired
    private IExtraccionTipoRepository extraccionTipoRepository;

    @Autowired
    private IRegionRepository regionRepository;

    @PostConstruct
    public void initBootstrap() {
        try {
            long totalEspecies = especieRepository.count();
            long totalTipos = extraccionTipoRepository.count();

            if (totalEspecies == 0 || totalTipos == 0) {
                log.warn("Catálogo de especies o tipos de extracción no disponible aún. Se pospone bootstrap de reglas LED.");
                return;
            }

            EspecieModel huiroPalo = buscarEspeciePorNombre("Huiro palo");
            ExtraccionTipoModel barreteado = buscarExtraccionTipoPorNombre("Barreteado");

            if (huiroPalo == null || barreteado == null) {
                log.warn("No se encontró especie 'Huiro palo' o tipo 'Barreteado' para regla LED oficial. Se pospone.");
                return;
            }

            boolean yaExiste = repository.findAll().stream().anyMatch(r ->
                    "LED Oficial Huiro Palo Barreteado".equalsIgnoreCase(r.getNombreRegla()) ||
                    (Boolean.TRUE.equals(r.getActivo()) &&
                     r.getEspecie() != null && huiroPalo.getId().equals(r.getEspecie().getId()) &&
                     r.getExtraccionTipo() != null && barreteado.getId().equals(r.getExtraccionTipo().getId()) &&
                     r.getRegion() == null &&
                     "ARMADOR".equalsIgnoreCase(r.getPerfilAplicable())));

            if (!yaExiste) {
                LimiteExtraccionDiarioConfigModel regla = LimiteExtraccionDiarioConfigModel.builder()
                        .nombreRegla("LED Oficial Huiro Palo Barreteado")
                        .especie(huiroPalo)
                        .extraccionTipo(barreteado)
                        .region(null)
                        .macrozona(null)
                        .perfilAplicable("ARMADOR")
                        .unidadAgregacion("EMBARCACION")
                        .metrica("DESEMBARQUE")
                        .limiteKg(new BigDecimal("2000.00"))
                        .margenToleranciaPct(BigDecimal.ZERO)
                        .modoAccion("SOLO_ALERTA")
                        .activo(true)
                        .build();

                repository.save(regla);
                log.info("Sembrada regla LED oficial: '{}' (2000 kg/día, Embarcación, Desembarque)", regla.getNombreRegla());
            }
        } catch (Exception e) {
            log.error("Error ejecutando bootstrap de regla LED oficial: {}", e.getMessage(), e);
        }
    }

    private EspecieModel buscarEspeciePorNombre(String clave) {
        String target = normalizar(clave);
        return especieRepository.findAll().stream()
                .filter(e -> normalizar(e.getNombre()).contains(target))
                .findFirst()
                .orElse(null);
    }

    private ExtraccionTipoModel buscarExtraccionTipoPorNombre(String clave) {
        String target = normalizar(clave);
        return extraccionTipoRepository.findAll().stream()
                .filter(ext -> normalizar(ext.getNombre()).contains(target))
                .findFirst()
                .orElse(null);
    }

    private String normalizar(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .toLowerCase();
    }

    public List<LimiteExtraccionDiarioConfigModel> getAll() {
        return repository.findAll();
    }

    public List<LimiteExtraccionDiarioConfigModel> getActivos() {
        return repository.findByActivoTrue();
    }

    public Optional<LimiteExtraccionDiarioConfigModel> getById(Long id) {
        return repository.findById(id);
    }

    public List<LimiteExtraccionDiarioConfigModel> findReglasVigentes(String perfil, Date fecha) {
        return repository.findReglasVigentes(perfil, fecha != null ? fecha : new Date());
    }

    public LimiteExtraccionDiarioConfigModel save(LimiteExtraccionDiarioConfigModel model) {
        resolverReferencias(model);
        if (model.getLimiteKg() == null || model.getLimiteKg().doubleValue() <= 0) {
            throw new IllegalArgumentException("El límite en kg debe ser mayor a 0.");
        }
        if (model.getPerfilAplicable() == null || model.getPerfilAplicable().isBlank()) {
            model.setPerfilAplicable("ARMADOR");
        }
        if (model.getUnidadAgregacion() == null || model.getUnidadAgregacion().isBlank()) {
            model.setUnidadAgregacion("EMBARCACION");
        }
        if (model.getMetrica() == null || model.getMetrica().isBlank()) {
            model.setMetrica("DESEMBARQUE");
        }
        if (model.getModoAccion() == null || model.getModoAccion().isBlank()) {
            model.setModoAccion("SOLO_ALERTA");
        }
        return repository.save(model);
    }

    public LimiteExtraccionDiarioConfigModel update(Long id, LimiteExtraccionDiarioConfigModel request) {
        LimiteExtraccionDiarioConfigModel existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Regla LED no encontrada con ID: " + id));

        resolverReferencias(request);
        if (request.getNombreRegla() != null) existing.setNombreRegla(request.getNombreRegla());
        existing.setEspecie(request.getEspecie());
        existing.setExtraccionTipo(request.getExtraccionTipo());
        existing.setRegion(request.getRegion());
        if (request.getPerfilAplicable() != null) existing.setPerfilAplicable(request.getPerfilAplicable());
        if (request.getUnidadAgregacion() != null) existing.setUnidadAgregacion(request.getUnidadAgregacion());
        if (request.getMetrica() != null) existing.setMetrica(request.getMetrica());
        if (request.getLimiteKg() != null) {
            if (request.getLimiteKg().doubleValue() <= 0) {
                throw new IllegalArgumentException("El límite en kg debe ser mayor a 0.");
            }
            existing.setLimiteKg(request.getLimiteKg());
        }
        if (request.getMargenToleranciaPct() != null) existing.setMargenToleranciaPct(request.getMargenToleranciaPct());
        if (request.getModoAccion() != null) existing.setModoAccion(request.getModoAccion());
        existing.setVigenciaInicio(request.getVigenciaInicio());
        existing.setVigenciaFin(request.getVigenciaFin());
        if (request.getActivo() != null) existing.setActivo(request.getActivo());

        return repository.save(existing);
    }

    public boolean delete(Long id) {
        try {
            repository.deleteById(id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void resolverReferencias(LimiteExtraccionDiarioConfigModel model) {
        if (model.getEspecie() != null && model.getEspecie().getId() != null) {
            model.setEspecie(especieRepository.findById(model.getEspecie().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Especie no encontrada con ID: " + model.getEspecie().getId())));
        }
        if (model.getExtraccionTipo() != null && model.getExtraccionTipo().getId() != null) {
            model.setExtraccionTipo(extraccionTipoRepository.findById(model.getExtraccionTipo().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Método de extracción no encontrado con ID: " + model.getExtraccionTipo().getId())));
        }
        if (model.getRegion() != null && model.getRegion().getId() != null) {
            model.setRegion(regionRepository.findById(model.getRegion().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Región no encontrada con ID: " + model.getRegion().getId())));
        }
    }
}
