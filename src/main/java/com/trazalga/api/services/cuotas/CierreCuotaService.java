package com.trazalga.api.services.cuotas;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.repositories.ICuotaExtraccionRepository;

import lombok.extern.slf4j.Slf4j;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Servicio transaccional dedicado al ciclo de cierre y reapertura de cuotas (TC.1, TC.2, TX.1).
 */
@Service
@Slf4j
@Transactional(readOnly = true)
public class CierreCuotaService {

    @Autowired
    private ICuotaExtraccionRepository cuotaRepository;

    @Autowired
    private com.trazalga.api.repositories.ICuotaExtraccionEventoRepository cuotaEventoRepository;

    @Autowired
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'FISCALIZADOR') or hasRole('SYSTEM') or (authentication == null)")
    public CuotaExtraccionModel cerrar(Long id, String motivo, String observacion, Long usuarioId) {
        CuotaExtraccionModel c = cuotaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cuota no encontrada con id: " + id));

        String motivoFinal = (motivo != null && !motivo.isBlank()) ? motivo.trim().toUpperCase() : "ADMINISTRATIVO";
        c.cerrar(motivoFinal, new Date(), usuarioId);

        CuotaExtraccionModel guardada = cuotaRepository.save(c);

        // Registrar evento de auditoría de ciclo de vida (TC.2)
        com.trazalga.api.models.CuotaExtraccionEventoModel evento = com.trazalga.api.models.CuotaExtraccionEventoModel.builder()
                .cuota(guardada)
                .tipo("CERRADA")
                .motivo(motivoFinal)
                .detalle(observacion != null && !observacion.isBlank() ? observacion.trim() : "Cierre de cuota")
                .usuarioId(usuarioId)
                .createdAt(new Date())
                .build();
        cuotaEventoRepository.save(evento);

        // Publicar evento de dominio (TA.5)
        eventPublisher.publishEvent(new com.trazalga.api.events.CuotaCerrada(id, motivoFinal));

        log.info("Cuota ID {} cerrada exitosamente con motivo {} por usuario {}", id, motivoFinal, usuarioId);
        return guardada;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'FISCALIZADOR') or hasRole('SYSTEM') or (authentication == null)")
    public CuotaExtraccionModel reabrir(Long id, String motivo, Long usuarioId) {
        CuotaExtraccionModel c = cuotaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cuota no encontrada con id: " + id));

        c.reabrir(motivo, usuarioId);

        CuotaExtraccionModel guardada = cuotaRepository.save(c);

        // Registrar evento de auditoría de ciclo de vida (TC.2)
        com.trazalga.api.models.CuotaExtraccionEventoModel evento = com.trazalga.api.models.CuotaExtraccionEventoModel.builder()
                .cuota(guardada)
                .tipo("REABIERTA")
                .motivo(motivo != null ? motivo.trim() : null)
                .detalle("Reapertura de cuota: " + (motivo != null ? motivo.trim() : ""))
                .usuarioId(usuarioId)
                .createdAt(new Date())
                .build();
        cuotaEventoRepository.save(evento);

        log.info("Cuota ID {} reabierta exitosamente con motivo {} por usuario {}", id, motivo, usuarioId);
        return guardada;
    }
}

