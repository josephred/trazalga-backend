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
import com.trazalga.api.models.FactorConversionModel;
import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.repositories.IEspecieRepository;
import com.trazalga.api.repositories.IFactorConversionRepository;
import com.trazalga.api.repositories.IHumedadEstadoRepository;

@Service
@Slf4j
public class FactorConversionService {

    @Autowired
    private IFactorConversionRepository repository;

    @Autowired
    private IEspecieRepository especieRepository;

    @Autowired
    private IHumedadEstadoRepository humedadEstadoRepository;

    @PostConstruct
    public void initBootstrap() {
        try {
            long totalEspecies = especieRepository.count();
            long totalHumedades = humedadEstadoRepository.count();

            if (totalEspecies == 0 || totalHumedades == 0) {
                log.warn("Catálogo de especies o estados de humedad aún no disponible. Se pospone bootstrap de factores de conversión.");
                return;
            }

            HumedadEstadoModel estadoSeco = buscarHumedadPorNombre("Seco");
            HumedadEstadoModel estadoHumedo = buscarHumedadPorNombre("Húmedo");

            if (estadoSeco == null && estadoHumedo == null) {
                log.warn("No se encontraron estados de humedad 'Seco' ni 'Húmedo' en catálogo. Se pospone bootstrap.");
                return;
            }

            Date vigencia = new java.text.SimpleDateFormat("yyyy-MM-dd").parse("2024-01-01");
            List<EspecieModel> especies = especieRepository.findAll();

            for (EspecieModel esp : especies) {
                if (estadoHumedo != null) {
                    crearFactorSiNoExiste(esp, estadoHumedo, new BigDecimal("1.0000"), vigencia,
                            "Estándar Húmedo", "Factor base para recurso húmedo recién extraído");
                }
                if (estadoSeco != null) {
                    crearFactorSiNoExiste(esp, estadoSeco, new BigDecimal("3.5800"), vigencia,
                            null, "Factor de conversión biológica seco a húmedo oficial (3.58)");
                }
            }
        } catch (Exception e) {
            log.error("Error ejecutando bootstrap de factores de conversión: {}", e.getMessage(), e);
        }
    }

    private void crearFactorSiNoExiste(EspecieModel esp, HumedadEstadoModel hum, BigDecimal factor, Date vigencia, String resolucion, String descripcion) {
        List<FactorConversionModel> existentes = repository.findByEspecieIdAndHumedadEstadoId(esp.getId(), hum.getId());
        boolean yaExiste = existentes.stream().anyMatch(f -> Boolean.TRUE.equals(f.getActivo()) &&
                f.getVigenciaInicio() != null && f.getVigenciaInicio().compareTo(vigencia) <= 0 &&
                (f.getVigenciaFin() == null || f.getVigenciaFin().compareTo(vigencia) >= 0));
        if (!yaExiste) {
            FactorConversionModel nuevo = FactorConversionModel.builder()
                    .especie(esp)
                    .humedadEstado(hum)
                    .factor(factor)
                    .vigenciaInicio(vigencia)
                    .vigenciaFin(null)
                    .resolucion(resolucion)
                    .descripcion(descripcion)
                    .activo(true)
                    .build();
            repository.save(nuevo);
            log.info("Sembrado factor de conversión oficial: Especie='{}', Humedad='{}', Factor={}",
                    esp.getNombre(), hum.getNombre(), factor);
        }
    }

    private HumedadEstadoModel buscarHumedadPorNombre(String clave) {
        String target = normalizar(clave);
        return humedadEstadoRepository.findAll().stream()
                .filter(h -> {
                    String n = normalizar(h.getNombre());
                    if ("seco".equals(target)) {
                        return n.equals("seco");
                    }
                    if ("humedo".equals(target)) {
                        return n.equals("humedo");
                    }
                    return n.contains(target);
                })
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

    public List<FactorConversionModel> getAll() {
        return repository.findAll();
    }

    public List<FactorConversionModel> getActivos() {
        return repository.findByActivoTrue();
    }

    public Optional<FactorConversionModel> getById(Long id) {
        return repository.findById(id);
    }

    public Optional<FactorConversionModel> findFactorVigente(Long especieId, Long humedadEstadoId, Date fecha) {
        return repository.findFactorVigente(especieId, humedadEstadoId, fecha != null ? fecha : new Date());
    }

    public FactorConversionModel save(FactorConversionModel model) {
        resolverReferencias(model);
        if (model.getFactor() == null || model.getFactor().doubleValue() <= 0) {
            throw new IllegalArgumentException("El factor de conversión debe ser un valor mayor a 0.");
        }
        if (model.getVigenciaInicio() == null) {
            model.setVigenciaInicio(new Date());
        }
        return repository.save(model);
    }

    public FactorConversionModel update(Long id, FactorConversionModel request) {
        FactorConversionModel existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Factor de conversión no encontrado con ID: " + id));

        resolverReferencias(request);
        if (request.getEspecie() != null) existing.setEspecie(request.getEspecie());
        if (request.getHumedadEstado() != null) existing.setHumedadEstado(request.getHumedadEstado());
        if (request.getFactor() != null) {
            if (request.getFactor().doubleValue() <= 0) {
                throw new IllegalArgumentException("El factor debe ser mayor a 0.");
            }
            existing.setFactor(request.getFactor());
        }
        if (request.getVigenciaInicio() != null) existing.setVigenciaInicio(request.getVigenciaInicio());
        existing.setVigenciaFin(request.getVigenciaFin());
        if (request.getResolucion() != null) existing.setResolucion(request.getResolucion());
        if (request.getDescripcion() != null) existing.setDescripcion(request.getDescripcion());
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

    private void resolverReferencias(FactorConversionModel model) {
        if (model.getEspecie() != null && model.getEspecie().getId() != null) {
            model.setEspecie(especieRepository.findById(model.getEspecie().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Especie no encontrada con ID: " + model.getEspecie().getId())));
        }
        if (model.getHumedadEstado() != null && model.getHumedadEstado().getId() != null) {
            model.setHumedadEstado(humedadEstadoRepository.findById(model.getHumedadEstado().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Estado de humedad no encontrado con ID: " + model.getHumedadEstado().getId())));
        }
    }
}
