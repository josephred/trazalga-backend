package com.trazalga.api.services.hallazgos;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trazalga.api.events.HallazgoRegistrado;
import com.trazalga.api.models.DeclaracionMarcaModel;
import com.trazalga.api.repositories.IDeclaracionMarcaRepository;

/**
 * Servicio de registro y gestión de hallazgos normativos (TA.3 / TA.5).
 * Garantiza idempotencia mediante clave única TIPO:ID:MARCA:REGLA y
 * publica eventos de dominio para notificaciones AFTER_COMMIT.
 */
@Service
@Transactional
public class HallazgoService {

    private static final Logger log = LoggerFactory.getLogger(HallazgoService.class);

    @Autowired
    private IDeclaracionMarcaRepository declaracionMarcaRepository;

    @Autowired
    private HallazgoFactory hallazgoFactory;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /**
     * Registra un hallazgo normativo de forma estrictamente idempotente.
     * Si la clave ya existe, retorna la entidad existente sin duplicarla ni re-notificar.
     */
    public DeclaracionMarcaModel registrar(
            String tipo,
            Long declaracionId,
            String marca,
            String regla,
            CriterioHallazgo criterio,
            String detalleFallback,
            String origen,
            Long usuarioDeclaranteId,
            Long regionId
    ) {
        String clave = hallazgoFactory.generarClaveIdempotencia(tipo, declaracionId, marca, regla);

        // 1. Verificación de idempotencia
        Optional<DeclaracionMarcaModel> existente = declaracionMarcaRepository.findByClaveIdempotencia(clave);
        if (existente.isPresent()) {
            log.info("Hallazgo ya registrado previamente con clave {}. Retornando registro existente.", clave);
            return existente.get();
        }

        // 2. Construcción y persistencia de la nueva marca
        DeclaracionMarcaModel nuevo = hallazgoFactory.crear(
                tipo,
                declaracionId,
                marca,
                regla,
                criterio,
                detalleFallback,
                origen
        );

        DeclaracionMarcaModel guardado = declaracionMarcaRepository.save(nuevo);
        log.info("Nuevo hallazgo registrado: ID #{} | Clave {} | Marca {}",
                guardado.getId(), guardado.getClaveIdempotencia(), guardado.getMarca());

        // 3. Despacho del evento de dominio para notificación AFTER_COMMIT
        eventPublisher.publishEvent(new HallazgoRegistrado(
                guardado.getId(),
                guardado.getMarca(),
                guardado.getDeclaracionTipo(),
                guardado.getDeclaracionId(),
                usuarioDeclaranteId,
                regionId
        ));

        return guardado;
    }

    /**
     * Sobrecarga conveniente para registro con criterio y origen.
     */
    public DeclaracionMarcaModel registrar(
            String tipo,
            Long declaracionId,
            String marca,
            String regla,
            CriterioHallazgo criterio,
            String detalleFallback,
            String origen
    ) {
        return registrar(tipo, declaracionId, marca, regla, criterio, detalleFallback, origen, null, null);
    }

    /**
     * Sobrecarga conveniente para validación estándar.
     */
    public DeclaracionMarcaModel registrar(
            String tipo,
            Long declaracionId,
            String marca,
            String regla,
            CriterioHallazgo criterio
    ) {
        return registrar(tipo, declaracionId, marca, regla, criterio, null, "VALIDACION", null, null);
    }

    /**
     * Sobrecarga para compatibilidad con llamadas existentes por reglaId numérico.
     */
    public DeclaracionMarcaModel registrar(
            String tipo,
            Long declaracionId,
            String marca,
            Long reglaId,
            String detalleFallback,
            String origen
    ) {
        String reglaStr = reglaId != null ? String.valueOf(reglaId) : "GENERAL";
        return registrar(tipo, declaracionId, marca, reglaStr, null, detalleFallback, origen, null, null);
    }
}
