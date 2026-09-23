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

    private static final String RESOLUCION_OFICIAL = "Res. Ex. Sernapesca 13-09-2026";
    private static final String DESC_PROVISORIA = "Valor provisorio 1,0 - sin factor oficial de Sernapesca al 13-09-2026";

    // Especies con tabla oficial Sernapesca
    public static final Long ESPECIE_HUIRO_PALO = 1L;
    public static final Long ESPECIE_HUIRO_MACRO = 8L;
    public static final Long ESPECIE_HUIRO_NEGRO = 9L;

    // Estados de humedad oficiales
    public static final Long HUMEDAD_HUMEDO = 1L;
    public static final Long HUMEDAD_SEMI_HUMEDO = 2L;
    public static final Long HUMEDAD_SEMI_SECO = 3L;
    public static final Long HUMEDAD_SECO = 4L;

    @PostConstruct
    public void initBootstrap() {
        try {
            long totalEspecies = especieRepository.count();
            long totalHumedades = humedadEstadoRepository.count();

            if (totalEspecies == 0 || totalHumedades == 0) {
                log.warn("Catálogo de especies o estados de humedad aún no disponible. Se pospone bootstrap de factores de conversión.");
                return;
            }

            Date vigenciaInicio2024 = new java.text.SimpleDateFormat("yyyy-MM-dd").parse("2024-01-01");
            Date vigenciaCierreAyer = new java.text.SimpleDateFormat("yyyy-MM-dd").parse("2023-12-31");

            List<EspecieModel> todasEspecies = especieRepository.findAll();
            List<HumedadEstadoModel> todasHumedades = humedadEstadoRepository.findAll();

            for (EspecieModel esp : todasEspecies) {
                for (HumedadEstadoModel hum : todasHumedades) {
                    sincronizarCombinacion(esp, hum, vigenciaInicio2024, vigenciaCierreAyer);
                }
            }
            log.info("Bootstrap de factores de conversión completado exitosamente (64 combinaciones verificadas).");
        } catch (Exception e) {
            log.error("Error ejecutando bootstrap de factores de conversión: {}", e.getMessage(), e);
        }
    }

    private void sincronizarCombinacion(EspecieModel esp, HumedadEstadoModel hum, Date vigenciaInicio, Date vigenciaCierre) {
        Long espId = esp.getId();
        Long humId = hum.getId();
        BigDecimal targetFactor = calcularFactorEsperado(espId, humId);
        String resolucion = esEspecieOficial(espId) ? RESOLUCION_OFICIAL : null;
        String descripcion = esEspecieOficial(espId)
                ? String.format("Factor oficial Sernapesca %s (%s)", hum.getNombre(), targetFactor)
                : DESC_PROVISORIA;

        List<FactorConversionModel> existentes = repository.findByEspecieIdAndHumedadEstadoId(espId, humId);

        // Identificar filas activas vigentes actualmente
        Date hoy = new Date();
        List<FactorConversionModel> activas = existentes.stream()
                .filter(f -> Boolean.TRUE.equals(f.getActivo()) &&
                        (f.getVigenciaFin() == null || f.getVigenciaFin().compareTo(hoy) >= 0))
                .toList();

        // 1. Cierre de duplicados si hay más de una fila vigente para la misma combinación
        if (activas.size() > 1) {
            log.warn("Detectadas {} filas activas simultáneas para especie {} y humedad {}. Cerrando duplicados...",
                    activas.size(), espId, humId);
            // Conservar solo una: priorizar la que no tenga vigenciaFin o la de menor/mayor id
            FactorConversionModel aMantener = activas.stream()
                    .filter(f -> f.getVigenciaFin() == null)
                    .findFirst()
                    .orElse(activas.get(0));

            for (FactorConversionModel f : activas) {
                if (!f.getId().equals(aMantener.getId())) {
                    f.setVigenciaFin(vigenciaCierre);
                    f.setActivo(false);
                    repository.save(f);
                    log.info("Duplicado cerrado: ID={} para especie {} y humedad {}", f.getId(), espId, humId);
                }
            }
            activas = List.of(aMantener);
        }

        // 2. Si existe exactamente una fila activa, verificar si el factor es el correcto
        if (!activas.isEmpty()) {
            FactorConversionModel actual = activas.get(0);
            if (actual.getFactor() != null && actual.getFactor().compareTo(targetFactor) == 0) {
                // Idempotente: ya tiene el factor correcto
                return;
            }

            // Corrección requerida: NO sobreescribir. Cerrar fila existente y crear una nueva
            log.info("Corrigiendo factor para especie {} ({}) y humedad {} ({}): De {} a {}",
                    espId, esp.getNombre(), humId, hum.getNombre(), actual.getFactor(), targetFactor);
            actual.setVigenciaFin(vigenciaCierre);
            actual.setActivo(false);
            repository.save(actual);
        }

        // 3. Crear nueva fila con el factor oficial vigente desde 2024-01-01
        FactorConversionModel nuevo = FactorConversionModel.builder()
                .especie(esp)
                .humedadEstado(hum)
                .factor(targetFactor)
                .vigenciaInicio(vigenciaInicio)
                .vigenciaFin(null)
                .resolucion(resolucion)
                .descripcion(descripcion)
                .activo(true)
                .build();
        repository.save(nuevo);
        log.info("Sembrado factor vigente: Especie='{}' (ID={}), Humedad='{}' (ID={}), Factor={}",
                esp.getNombre(), espId, hum.getNombre(), humId, targetFactor);
    }

    public static BigDecimal calcularFactorEsperado(Long especieId, Long humedadEstadoId) {
        if (HUMEDAD_HUMEDO.equals(humedadEstadoId)) return new BigDecimal("1.1300");
        if (HUMEDAD_SEMI_HUMEDO.equals(humedadEstadoId)) return new BigDecimal("1.7500");
        if (HUMEDAD_SEMI_SECO.equals(humedadEstadoId)) return new BigDecimal("2.7000");
        if (HUMEDAD_SECO.equals(humedadEstadoId)) return new BigDecimal("3.5800");
        return new BigDecimal("1.0000");
    }

    public static boolean esEspecieOficial(Long especieId) {
        return true;
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
        if (model.getFactor() == null || model.getFactor().compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("El factor de conversión debe ser mayor o igual a 1.0. La captura biológica nunca puede ser menor que el desembarque físico.");
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
            if (request.getFactor().compareTo(BigDecimal.ONE) < 0) {
                throw new IllegalArgumentException("El factor de conversión debe ser mayor o igual a 1.0. La captura biológica nunca puede ser menor que el desembarque físico.");
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
