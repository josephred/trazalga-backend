package com.trazalga.api.tasks;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.trazalga.api.services.RevisionVedasService;
import com.trazalga.api.services.TareaProgramadaService;
import com.trazalga.api.services.cuotas.RevisionCuotasService;

/**
 * Tarea programada diaria a las 06:00 (TC.6).
 * No depende de repositorios (TA.1 / ArchUnit) y delega la ejecución en servicios
 * transaccionales dedicados, registrando auditoría en tarea_programada_ejecucion (TA.6).
 */
@Component
public class AlertaProgramadaTask {

    private static final Logger log = LoggerFactory.getLogger(AlertaProgramadaTask.class);

    @Autowired
    private RevisionCuotasService revisionCuotasService;

    @Autowired
    private RevisionVedasService revisionVedasService;

    @Autowired
    private TareaProgramadaService tareaProgramadaService;

    @Scheduled(cron = "${trazalga.alertas.cron:0 0 6 * * *}")
    public void ejecutarRevisionDiaria() {
        log.info("Iniciando revisión programada diaria de cuotas y vedas...");
        Date hoy = new Date();
        LocalDate hoyLocal = hoy.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

        ejecutarRevisionCuotas(hoy, hoyLocal);
        ejecutarRevisionVedas(hoyLocal);

        log.info("Finalizada revisión programada diaria.");
    }

    private void ejecutarRevisionCuotas(Date hoy, LocalDate hoyLocal) {
        Long idEjecucion = null;
        if (tareaProgramadaService != null) {
            idEjecucion = tareaProgramadaService.registrarInicio("REVISION_CUOTAS");
        }
        try {
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken sysAuth =
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            "SYSTEM_TASK", null,
                            java.util.List.of(
                                    new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"),
                                    new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_SYSTEM")));
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(sysAuth);

            int procesadas = revisionCuotasService.revisar(hoyLocal, hoy);
            if (tareaProgramadaService != null && idEjecucion != null) {
                tareaProgramadaService.registrarExito(idEjecucion, procesadas, "Revisión de cuotas ejecutada correctamente");
            }
        } catch (Exception e) {
            log.error("Error en revisión programada de cuotas: {}", e.getMessage(), e);
            if (tareaProgramadaService != null && idEjecucion != null) {
                tareaProgramadaService.registrarError(idEjecucion, e.getMessage());
            }
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    private void ejecutarRevisionVedas(LocalDate hoyLocal) {
        Long idEjecucion = null;
        if (tareaProgramadaService != null) {
            idEjecucion = tareaProgramadaService.registrarInicio("REVISION_VEDAS");
        }
        try {
            int procesadas = revisionVedasService.revisar(hoyLocal);
            if (tareaProgramadaService != null && idEjecucion != null) {
                tareaProgramadaService.registrarExito(idEjecucion, procesadas, "Revisión de vedas ejecutada correctamente");
            }
        } catch (Exception e) {
            log.error("Error en revisión programada de vedas: {}", e.getMessage(), e);
            if (tareaProgramadaService != null && idEjecucion != null) {
                tareaProgramadaService.registrarError(idEjecucion, e.getMessage());
            }
        }
    }
}
