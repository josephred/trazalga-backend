package com.trazalga.api.tasks;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.trazalga.api.services.TareaProgramadaService;
import com.trazalga.api.services.retencion.RetencionCustodiaService;

/**
 * Tarea programada periódica cada 15 minutos para evaluación de retención y bloqueo (TR.3 / TA.6).
 * No depende de repositorios (TA.1 / ArchUnit) y delega la ejecución en RetencionCustodiaService,
 * registrando auditoría en tarea_programada_ejecucion (TA.6).
 */
@Component
public class RetencionProgramadaTask {

    private static final Logger log = LoggerFactory.getLogger(RetencionProgramadaTask.class);

    @Autowired
    private RetencionCustodiaService retencionCustodiaService;

    @Autowired
    private TareaProgramadaService tareaProgramadaService;

    @Scheduled(cron = "${trazalga.retencion.cron:0 */15 * * * *}")
    public void ejecutarBarridoRetencion() {
        log.info("Iniciando barrido periódico de retención en bodega virtual y bloqueo automático...");
        Long idEjecucion = null;
        if (tareaProgramadaService != null) {
            idEjecucion = tareaProgramadaService.registrarInicio("BARRIDO_RETENCION_BODEGA");
        }

        try {
            // Establecer contexto de seguridad para tarea del sistema
            UsernamePasswordAuthenticationToken sysAuth = new UsernamePasswordAuthenticationToken(
                    "SYSTEM_TASK", null,
                    List.of(
                            new SimpleGrantedAuthority("ROLE_ADMIN"),
                            new SimpleGrantedAuthority("ROLE_SYSTEM")
                    )
            );
            SecurityContextHolder.getContext().setAuthentication(sysAuth);

            RetencionCustodiaService.ResumenBarridoRetencion resumen =
                    retencionCustodiaService.barrer(LocalDateTime.now());

            if (tareaProgramadaService != null && idEjecucion != null) {
                String detalle = String.format("Barrido completado: %d evaluados, %d bloqueos, %d preavisos",
                        resumen.lotesEvaluados(), resumen.bloqueosAplicados(), resumen.preavisosEmitidos());
                tareaProgramadaService.registrarExito(idEjecucion, resumen.lotesEvaluados(), detalle);
            }
            log.info("Barrido periódico de retención finalizado con éxito.");
        } catch (Exception e) {
            log.error("Error en barrido periódico de retención: {}", e.getMessage(), e);
            if (tareaProgramadaService != null && idEjecucion != null) {
                tareaProgramadaService.registrarError(idEjecucion, e.getMessage());
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
