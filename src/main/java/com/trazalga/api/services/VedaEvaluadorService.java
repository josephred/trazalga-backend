package com.trazalga.api.services;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.models.VedaEspecieModel;
import com.trazalga.api.repositories.VedaEspecieRepository;

@Service
public class VedaEvaluadorService {

    @Autowired
    private VedaEspecieRepository vedaRepository;

    @Autowired
    private ConfiguracionGeneralService configuracionGeneralService;

    public static class EvaluacionVedaResult {
        private final boolean enVeda;
        private final boolean bloquear;
        private final String mensaje;
        private final VedaEspecieModel vedaAplicada;

        public EvaluacionVedaResult(boolean enVeda, boolean bloquear, String mensaje, VedaEspecieModel vedaAplicada) {
            this.enVeda = enVeda;
            this.bloquear = bloquear;
            this.mensaje = mensaje;
            this.vedaAplicada = vedaAplicada;
        }

        public boolean isEnVeda() { return enVeda; }
        public boolean isBloquear() { return bloquear; }
        public String getMensaje() { return mensaje; }
        public VedaEspecieModel getVedaAplicada() { return vedaAplicada; }
    }

    /**
     * Evalúa si una extracción se encuentra en periodo de veda, contrastando la fecha de extracción.
     */
    public EvaluacionVedaResult evaluar(Long especieId, Long extraccionTipoId, Long regionId, Date fechaExtraccion) {
        if (especieId == null) {
            return new EvaluacionVedaResult(false, false, "Especie no especificada", null);
        }

        Date fecha = (fechaExtraccion != null) ? fechaExtraccion : new Date();
        LocalDate localFecha = fecha.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        int mesExtraccion = localFecha.getMonthValue(); // 1 a 12

        List<VedaEspecieModel> vedasActivas = vedaRepository.findByActivoTrue();

        for (VedaEspecieModel veda : vedasActivas) {
            if (veda.getEspecie() == null || !veda.getEspecie().getId().equals(especieId)) {
                continue;
            }

            // Región: NULL aplica a todas
            if (veda.getRegion() != null && regionId != null && !veda.getRegion().getId().equals(regionId)) {
                continue;
            }

            // Método de extracción: NULL aplica a todos
            if (veda.getExtraccionTipo() != null && extraccionTipoId != null && !veda.getExtraccionTipo().getId().equals(extraccionTipoId)) {
                continue;
            }

            boolean coincideVeda = false;

            if (Boolean.TRUE.equals(veda.getRecurrenciaAnual())) {
                if (veda.getMesesVeda() != null && !veda.getMesesVeda().isBlank()) {
                    String[] meses = veda.getMesesVeda().split(",");
                    for (String m : meses) {
                        try {
                            if (Integer.parseInt(m.trim()) == mesExtraccion) {
                                coincideVeda = true;
                                break;
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            } else {
                if (veda.getFechaInicio() != null && veda.getFechaFin() != null) {
                    // Rango de fechas
                    LocalDate inicio = veda.getFechaInicio().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                    LocalDate fin = veda.getFechaFin().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                    if (!localFecha.isBefore(inicio) && !localFecha.isAfter(fin)) {
                        coincideVeda = true;
                    }
                }
            }

            if (coincideVeda) {
                String modo = configuracionGeneralService.getValor("veda_modo_operacion", "BLOQUEO_ESTRICTO");
                boolean bloquear = "BLOQUEO_ESTRICTO".equalsIgnoreCase(modo);
                String resInfo = veda.getResolucion() != null ? " (" + veda.getResolucion() + ")" : "";
                String detalle = "El recurso '" + (veda.getEspecie() != null ? veda.getEspecie().getNombre() : "declarado") +
                        "' se encuentra en periodo de VEDA oficial para la fecha " + localFecha + resInfo + ".";
                return new EvaluacionVedaResult(true, bloquear, detalle, veda);
            }
        }

        return new EvaluacionVedaResult(false, false, "Recurso no se encuentra en veda.", null);
    }
}
