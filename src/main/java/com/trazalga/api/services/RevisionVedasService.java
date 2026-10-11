package com.trazalga.api.services;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.models.VedaEspecieModel;
import com.trazalga.api.repositories.IUsuarioRepository;
import com.trazalga.api.repositories.VedaEspecieRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Servicio transaccional para la revisión diaria de vedas biológicas (TC.6).
 */
@Service
@Slf4j
@Transactional(readOnly = false)
public class RevisionVedasService {

    @Autowired
    private VedaEspecieRepository vedaRepository;

    @Autowired
    private ConfiguracionGeneralService configuracionGeneralService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private IUsuarioRepository usuarioRepository;

    public int revisar(LocalDate hoyLocal) {
        log.info("Iniciando revisión transaccional de vedas para fecha: {}", hoyLocal);
        int diasAvisoVeda = parseInt(configuracionGeneralService.getValor("veda_dias_aviso_previo", "7"), 7);
        List<VedaEspecieModel> vedasActivas = vedaRepository.findByActivoTrue();
        int procesadas = 0;

        for (VedaEspecieModel veda : vedasActivas) {
            String especieNombre = (veda.getEspecie() != null) ? veda.getEspecie().getNombre() : "Especie no especificada";

            // Veda puntual por rango de fecha
            if (!Boolean.TRUE.equals(veda.getRecurrenciaAnual()) && veda.getFechaInicio() != null) {
                LocalDate fechaInicioLocal = CuotaExtraccionService.toLocalDateSafe(veda.getFechaInicio());
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
                    int mesActual = hoyLocal.getMonthValue();
                    int numProximoMes = proximoMes.getMonthValue();

                    boolean mesActualEnVeda = Arrays.stream(veda.getMesesVeda().split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .anyMatch(s -> s.equals(String.valueOf(mesActual)));

                    boolean iniciaVeda = Arrays.stream(veda.getMesesVeda().split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .anyMatch(s -> s.equals(String.valueOf(numProximoMes)));

                    if (!mesActualEnVeda && iniciaVeda) {
                        String titulo = "Aviso Previo de Inicio de Veda Anual";
                        String resInfo = (veda.getResolucion() != null && !veda.getResolucion().trim().isEmpty())
                                ? " según Res. " + veda.getResolucion() : "";
                        String metodoInfo = (veda.getExtraccionTipo() != null)
                                ? " para " + veda.getExtraccionTipo().getNombre() : "";
                        String mensaje = String.format("La veda anual para %s%s iniciará el %s (en %d días)%s.",
                                especieNombre, metodoInfo, proximoMes, diasHastaProximoMes, resInfo);
                        notificarSoloAdmins(titulo, mensaje);
                    }
                }
            }
            procesadas++;
        }

        log.info("Finalizada revisión transaccional de vedas. Procesadas: {}", procesadas);
        return procesadas;
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

    private int parseInt(String str, int def) {
        try {
            return str != null ? Integer.parseInt(str.trim()) : def;
        } catch (Exception e) {
            return def;
        }
    }
}
