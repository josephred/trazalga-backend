package com.trazalga.api.services.cuotas;

import java.util.Date;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.trazalga.api.events.CuotaUmbralAlcanzado;
import com.trazalga.api.models.AvisoEnviadoModel;
import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.repositories.IAvisoEnviadoRepository;

/**
 * Servicio transaccional dedicado para el registro de avisos y eventos de cuota (TC.6 / 5.2).
 * Garantiza persistencia inmediata (REQUIRES_NEW) y una clave de unicidad unificada con la revisión diaria.
 */
@Service
public class AvisoCuotaService {

    private final IAvisoEnviadoRepository avisoEnviadoRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AvisoCuotaService(IAvisoEnviadoRepository avisoEnviadoRepository, ApplicationEventPublisher eventPublisher) {
        this.avisoEnviadoRepository = avisoEnviadoRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Construye la clave canónica de umbral de cuota:
     * - Para cuotas individuales o plantillas: usa el ID del titular o declarante.
     * - Para cuotas territoriales (comunal, provincial, regional, macrozona): usa 0.
     */
    public static String construirClaveUmbral(CuotaExtraccionModel c, Long usuarioId) {
        Long personaId = resolverPersonaId(c, usuarioId);
        return String.format("CUOTA:%d:UMBRAL:%d", c.getId(), personaId);
    }

    public static Long resolverPersonaId(CuotaExtraccionModel c, Long usuarioId) {
        if (c == null) return 0L;
        boolean esIndividualOPlantilla = c.getUsuario() != null || Boolean.TRUE.equals(c.getEsPlantilla()) || "INDIVIDUAL".equalsIgnoreCase(c.getNivelAgregacion());
        if (esIndividualOPlantilla) {
            return c.getUsuario() != null ? c.getUsuario().getId() : (usuarioId != null ? usuarioId : 0L);
        }
        return 0L;
    }

    /**
     * Registra el aviso en aviso_enviado y publica el evento CuotaUmbralAlcanzado si la clave no existía previamente.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean registrarAvisoUmbralSiNoExiste(CuotaExtraccionModel cuota, Long usuarioId, double pct) {
        if (avisoEnviadoRepository == null || cuota == null) {
            return false;
        }

        String claveAviso = construirClaveUmbral(cuota, usuarioId);
        if (!avisoEnviadoRepository.existsById(claveAviso)) {
            avisoEnviadoRepository.saveAndFlush(new AvisoEnviadoModel(claveAviso, new Date()));
            Long personaId = resolverPersonaId(cuota, usuarioId);
            if (eventPublisher != null) {
                eventPublisher.publishEvent(new CuotaUmbralAlcanzado(cuota.getId(), personaId, pct));
            }
            return true;
        }
        return false;
    }
}
