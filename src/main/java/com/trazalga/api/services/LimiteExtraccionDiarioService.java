package com.trazalga.api.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.models.LimiteExtraccionDiarioConfigModel;
import com.trazalga.api.repositories.IDeclaracionArmadorRepository;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;
import com.trazalga.api.repositories.ILimiteExtraccionDiarioConfigRepository;

@Service
public class LimiteExtraccionDiarioService {

    @Autowired
    private ILimiteExtraccionDiarioConfigRepository ledConfigRepository;

    @Autowired
    private IDeclaracionArmadorRepository declaracionArmadorRepository;

    @Autowired
    private IDeclaracionRecolectorRepository declaracionRecolectorRepository;

    public static class EvaluacionLedResult {
        private final boolean excede;
        private final boolean bloquear;
        private final BigDecimal totalAcumulado;
        private final BigDecimal limiteConTolerancia;
        private final String mensaje;
        private final LimiteExtraccionDiarioConfigModel reglaAplicada;

        public EvaluacionLedResult(boolean excede, boolean bloquear, BigDecimal totalAcumulado,
                                   BigDecimal limiteConTolerancia, String mensaje,
                                   LimiteExtraccionDiarioConfigModel reglaAplicada) {
            this.excede = excede;
            this.bloquear = bloquear;
            this.totalAcumulado = totalAcumulado;
            this.limiteConTolerancia = limiteConTolerancia;
            this.mensaje = mensaje;
            this.reglaAplicada = reglaAplicada;
        }

        public boolean isExcede() { return excede; }
        public boolean isBloquear() { return bloquear; }
        public BigDecimal getTotalAcumulado() { return totalAcumulado; }
        public BigDecimal getLimiteConTolerancia() { return limiteConTolerancia; }
        public String getMensaje() { return mensaje; }
        public LimiteExtraccionDiarioConfigModel getReglaAplicada() { return reglaAplicada; }
    }

    /**
     * Evalúa el Límite de Extracción Diario (LED) para una declaración.
     */
    public EvaluacionLedResult evaluar(
            String perfil,
            Long embarcacionId,
            Long usuarioId,
            Long buzoId,
            Long especieId,
            Long extraccionTipoId,
            Long regionId,
            Date fechaDeclaracion,
            BigDecimal desembarqueKg,
            BigDecimal capturaKg) {

        Date fecha = (fechaDeclaracion != null) ? fechaDeclaracion : new Date();

        // 1. Obtener todas las reglas vigentes para este perfil o globales
        List<LimiteExtraccionDiarioConfigModel> todasReglas = ledConfigRepository.findReglasVigentes(null, fecha);

        List<LimiteExtraccionDiarioConfigModel> aplicables = todasReglas.stream()
                .filter(r -> r.getPerfilAplicable() == null
                        || "TODOS".equalsIgnoreCase(r.getPerfilAplicable())
                        || (perfil != null && perfil.equalsIgnoreCase(r.getPerfilAplicable())))
                .filter(r -> r.getEspecie() == null || (especieId != null && r.getEspecie().getId().equals(especieId)))
                .filter(r -> r.getExtraccionTipo() == null || (extraccionTipoId != null && r.getExtraccionTipo().getId().equals(extraccionTipoId)))
                .filter(r -> r.getRegion() == null || (regionId != null && r.getRegion().getId().equals(regionId)))
                .sorted(Comparator.comparingInt(this::calcularEspecificidad).reversed())
                .collect(Collectors.toList());

        if (aplicables.isEmpty()) {
            return new EvaluacionLedResult(false, false, BigDecimal.ZERO, BigDecimal.ZERO, "No aplica regla LED.", null);
        }

        // La regla con mayor especificidad manda
        LimiteExtraccionDiarioConfigModel regla = aplicables.get(0);

        // 2. Determinar tolerancia y límite
        BigDecimal toleranciaPct = regla.getMargenToleranciaPct() != null ? regla.getMargenToleranciaPct() : BigDecimal.ZERO;
        BigDecimal multTolerancia = BigDecimal.ONE.add(toleranciaPct.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        BigDecimal limiteConTolerancia = regla.getLimiteKg().multiply(multTolerancia).setScale(2, RoundingMode.HALF_UP);

        // 3. Sumar el acumulado previo del día según unidadAgregacion y metrica
        String unidad = regla.getUnidadAgregacion() != null ? regla.getUnidadAgregacion().toUpperCase() : "EMBARCACION";
        boolean esCaptura = "CAPTURA".equalsIgnoreCase(regla.getMetrica());

        BigDecimal acumuladoPrevio = BigDecimal.ZERO;
        Long espId = regla.getEspecie() != null ? regla.getEspecie().getId() : especieId;

        if ("EMBARCACION".equals(unidad) && embarcacionId != null) {
            if (esCaptura) {
                Double sumCap = declaracionArmadorRepository.sumCapturaByEmbarcacionAndFecha(embarcacionId, espId, fecha);
                acumuladoPrevio = BigDecimal.valueOf(sumCap != null ? sumCap : 0.0);
            } else {
                acumuladoPrevio = declaracionArmadorRepository.sumDesembarqueByEmbarcacionAndFecha(embarcacionId, espId, fecha);
            }
        } else if ("BUZO".equals(unidad) && buzoId != null) {
            if (esCaptura) {
                Double sumCap = declaracionArmadorRepository.sumCapturaByBuzoAndFecha(buzoId, espId, fecha);
                acumuladoPrevio = BigDecimal.valueOf(sumCap != null ? sumCap : 0.0);
            } else {
                acumuladoPrevio = declaracionArmadorRepository.sumDesembarqueByBuzoAndFecha(buzoId, espId, fecha);
            }
        } else if ("USUARIO".equals(unidad) && usuarioId != null) {
            if ("ARMADOR".equalsIgnoreCase(perfil)) {
                if (esCaptura) {
                    Double sumCap = declaracionArmadorRepository.sumCapturaByUsuarioAndFecha(usuarioId, espId, fecha);
                    acumuladoPrevio = BigDecimal.valueOf(sumCap != null ? sumCap : 0.0);
                } else {
                    acumuladoPrevio = declaracionArmadorRepository.sumDesembarqueByUsuarioAndFecha(usuarioId, espId, fecha);
                }
            } else if ("RECOLECTOR".equalsIgnoreCase(perfil)) {
                if (esCaptura) {
                    acumuladoPrevio = declaracionRecolectorRepository.sumCapturaByUsuarioAndFecha(usuarioId, espId, fecha);
                } else {
                    acumuladoPrevio = declaracionRecolectorRepository.sumDesembarqueByUsuarioAndFecha(usuarioId, espId, fecha);
                }
            }
        }

        if (acumuladoPrevio == null) {
            acumuladoPrevio = BigDecimal.ZERO;
        }

        // 4. Agregar el monto de la declaración actual
        BigDecimal nuevoMonto = esCaptura ? (capturaKg != null ? capturaKg : BigDecimal.ZERO)
                                          : (desembarqueKg != null ? desembarqueKg : BigDecimal.ZERO);
        BigDecimal total = acumuladoPrevio.add(nuevoMonto);

        // 5. Comparar
        if (total.compareTo(limiteConTolerancia) > 0) {
            String modo = regla.getModoAccion() != null ? regla.getModoAccion().toUpperCase() : "SOLO_ALERTA";
            boolean bloquear = modo.contains("BLOQUEO");
            String metricaStr = esCaptura ? "captura corregida" : "desembarque físico";
            String msg = String.format("El límite diario de extracción (%s) de %.2f kg (%s) por %s ha sido superado. " +
                            "Total acumulado para hoy: %.2f kg (intentando declarar %.2f kg, límite con tolerancia: %.2f kg).",
                    regla.getNombreRegla(), regla.getLimiteKg(), metricaStr, unidad, total, nuevoMonto, limiteConTolerancia);

            return new EvaluacionLedResult(true, bloquear, total, limiteConTolerancia, msg, regla);
        }

        return new EvaluacionLedResult(false, false, total, limiteConTolerancia, "Declaración dentro del límite diario (LED).", regla);
    }

    private int calcularEspecificidad(LimiteExtraccionDiarioConfigModel r) {
        int score = 0;
        if (r.getEspecie() != null) score += 4;
        if (r.getExtraccionTipo() != null) score += 2;
        if (r.getRegion() != null) score += 1;
        if (r.getPerfilAplicable() != null && !"TODOS".equalsIgnoreCase(r.getPerfilAplicable())) score += 1;
        return score;
    }
}
