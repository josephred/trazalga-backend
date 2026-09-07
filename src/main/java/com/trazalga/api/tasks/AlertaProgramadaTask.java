package com.trazalga.api.tasks;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.models.VedaEspecieModel;
import com.trazalga.api.repositories.ICuotaExtraccionRepository;
import com.trazalga.api.repositories.IUsuarioRepository;
import com.trazalga.api.repositories.VedaEspecieRepository;
import com.trazalga.api.services.ConfiguracionGeneralService;
import com.trazalga.api.services.CuotaExtraccionService;
import com.trazalga.api.services.NotificationService;

/**
 * Tarea programada diaria que evalúa:
 * 1. Cuotas cuyo saldo disponible cae bajo cuota_umbral_restante_pct o se han agotado.
 * 2. Cuotas próximas a expirar en cuota_dias_previos_expiracion días.
 * 3. Vedas que iniciarán en veda_dias_aviso_previo días.
 */
@Component
public class AlertaProgramadaTask {

    private static final Logger log = LoggerFactory.getLogger(AlertaProgramadaTask.class);

    @Autowired
    private ICuotaExtraccionRepository cuotaRepository;

    @Autowired
    private CuotaExtraccionService cuotaExtraccionService;

    @Autowired
    private VedaEspecieRepository vedaRepository;

    @Autowired
    private ConfiguracionGeneralService configuracionGeneralService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private IUsuarioRepository usuarioRepository;

    @Scheduled(cron = "${trazalga.alertas.cron:0 0 6 * * *}")
    public void ejecutarRevisionDiaria() {
        log.info("Iniciando revisión programada diaria de cuotas y vedas...");
        Date hoy = new Date();
        LocalDate hoyLocal = hoy.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

        revisarCuotas(hoy, hoyLocal);
        revisarVedas(hoyLocal);
        log.info("Finalizada revisión programada diaria.");
    }

    private void revisarCuotas(Date hoy, LocalDate hoyLocal) {
        double umbralRestantePct = parseDouble(configuracionGeneralService.getValor("cuota_umbral_restante_pct", "10.0"), 10.0);
        int diasPreviosExpiracion = parseInt(configuracionGeneralService.getValor("cuota_dias_previos_expiracion", "5"), 5);

        List<CuotaExtraccionModel> cuotasActivas = cuotaRepository.findByActivoTrue();

        for (CuotaExtraccionModel cuota : cuotasActivas) {
            // Ignorar cuotas plantilla no asignadas
            if (Boolean.TRUE.equals(cuota.getEsPlantilla()) && cuota.getUsuario() == null) {
                continue;
            }

            BigDecimal limite = cuotaExtraccionService.calcularLimiteEfectivo(cuota, hoy);
            if (limite == null || limite.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal consumo = cuotaExtraccionService.calcularConsumoAcumulado(cuota, hoy);
            BigDecimal saldo = limite.subtract(consumo);

            String alcance = cuotaExtraccionService.describirAlcance(cuota);
            String especieNombre = (cuota.getEspecie() != null) ? cuota.getEspecie().getNombre() : "General";

            // 1. Umbral de saldo restante
            if (saldo.compareTo(BigDecimal.ZERO) <= 0) {
                String titulo = "Alerta Crítica: Cuota Agotada";
                String mensaje = String.format("La cuota de %s (%s) ha alcanzado o superado su límite: %.2f kg de %.2f kg.",
                        alcance, especieNombre, consumo, limite);
                notificar(cuota.getUsuario() != null ? cuota.getUsuario().getId() : null, titulo, mensaje);
            } else {
                double pctRestante = saldo.divide(limite, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue();
                if (pctRestante <= umbralRestantePct) {
                    String titulo = "Aviso de Cuota Próxima al Límite";
                    String mensaje = String.format("La cuota de %s (%s) tiene solo %.1f%% de saldo restante (%.2f kg disponibles de %.2f kg).",
                            alcance, especieNombre, pctRestante, saldo, limite);
                    notificar(cuota.getUsuario() != null ? cuota.getUsuario().getId() : null, titulo, mensaje);
                }
            }

            // 2. Vigencia / Expiración próxima
            if (cuota.getFechaFin() != null) {
                LocalDate fechaFinLocal = cuota.getFechaFin().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                long diasRestantes = ChronoUnit.DAYS.between(hoyLocal, fechaFinLocal);
                if (diasRestantes >= 0 && diasRestantes <= diasPreviosExpiracion) {
                    String titulo = "Aviso de Expiración de Cuota";
                    String mensaje = String.format("La cuota de %s (%s) vencerá en %d día(s) (fecha de término: %s).",
                            alcance, especieNombre, diasRestantes, cuota.getFechaFin());
                    notificar(cuota.getUsuario() != null ? cuota.getUsuario().getId() : null, titulo, mensaje);
                }
            }
        }
    }

    private void revisarVedas(LocalDate hoyLocal) {
        int diasAvisoVeda = parseInt(configuracionGeneralService.getValor("veda_dias_aviso_previo", "7"), 7);
        List<VedaEspecieModel> vedasActivas = vedaRepository.findByActivoTrue();

        for (VedaEspecieModel veda : vedasActivas) {
            String especieNombre = (veda.getEspecie() != null) ? veda.getEspecie().getNombre() : "Especie no especificada";

            // Veda puntual por rango de fecha
            if (!Boolean.TRUE.equals(veda.getRecurrenciaAnual()) && veda.getFechaInicio() != null) {
                LocalDate fechaInicioLocal = veda.getFechaInicio().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                long diasParaInicio = ChronoUnit.DAYS.between(hoyLocal, fechaInicioLocal);
                if (diasParaInicio >= 0 && diasParaInicio <= diasAvisoVeda) {
                    String titulo = "Aviso Previo de Inicio de Veda";
                    String resInfo = (veda.getResolucion() != null && !veda.getResolucion().trim().isEmpty())
                            ? " según Res. " + veda.getResolucion() : "";
                    String mensaje = String.format("La veda para %s entrará en vigor en %d día(s) (%s)%s.",
                            especieNombre, diasParaInicio, veda.getFechaInicio(), resInfo);
                    notificarSoloAdmins(titulo, mensaje);
                }
            }

            // Veda con recurrencia anual: aviso previo al cambio de mes
            if (Boolean.TRUE.equals(veda.getRecurrenciaAnual()) && veda.getMesesVeda() != null && !veda.getMesesVeda().trim().isEmpty()) {
                LocalDate proximoMes = hoyLocal.plusMonths(1).withDayOfMonth(1);
                long diasHastaProximoMes = ChronoUnit.DAYS.between(hoyLocal, proximoMes);
                if (diasHastaProximoMes >= 0 && diasHastaProximoMes <= diasAvisoVeda) {
                    int numProximoMes = proximoMes.getMonthValue();
                    boolean iniciaVeda = Arrays.stream(veda.getMesesVeda().split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .anyMatch(s -> s.equals(String.valueOf(numProximoMes)));

                    if (iniciaVeda) {
                        String titulo = "Aviso Previo de Inicio de Veda Anual";
                        String mensaje = String.format("La veda anual para %s iniciará el %s (en %d días).",
                                especieNombre, proximoMes, diasHastaProximoMes);
                        notificarSoloAdmins(titulo, mensaje);
                    }
                }
            }
        }
    }

    private void notificar(Long usuarioId, String titulo, String mensaje) {
        if (usuarioId != null) {
            notificationService.sendPushNotificationToUser(usuarioId, titulo, mensaje);
        }
        notificarSoloAdmins(titulo, mensaje);
    }

    private void notificarSoloAdmins(String titulo, String mensaje) {
        try {
            List<UsuarioModel> admins = usuarioRepository.findByPerfilId(1L);
            if (admins != null) {
                for (UsuarioModel admin : admins) {
                    notificationService.sendPushNotificationToUser(admin.getId(), titulo, mensaje);
                }
            }
        } catch (Exception e) {
            log.error("Error despachando notificación programada a administradores: {}", e.getMessage());
        }
    }

    private double parseDouble(String str, double def) {
        try {
            return str != null ? Double.parseDouble(str.trim()) : def;
        } catch (Exception e) {
            return def;
        }
    }

    private int parseInt(String str, int def) {
        try {
            return str != null ? Integer.parseInt(str.trim()) : def;
        } catch (Exception e) {
            return def;
        }
    }
}
