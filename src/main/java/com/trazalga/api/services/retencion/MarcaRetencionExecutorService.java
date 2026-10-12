package com.trazalga.api.services.retencion;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.trazalga.api.models.DeclaracionMarcaModel;
import com.trazalga.api.repositories.IDeclaracionMarcaRepository;
import com.trazalga.api.services.hallazgos.CriterioHallazgo;
import com.trazalga.api.services.hallazgos.HallazgoService;

/**
 * TR.3 / TA.5: Ejecutor transaccional independiente para persistir marcas de retención
 * con semántica Propagation.REQUIRES_NEW a través de un proxy Spring real.
 */
@Service
public class MarcaRetencionExecutorService {

    private static final Logger log = LoggerFactory.getLogger(MarcaRetencionExecutorService.class);
    private static final Pattern HORAS_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*h", Pattern.CASE_INSENSITIVE);

    @Autowired
    private HallazgoService hallazgoService;

    @Autowired
    private IDeclaracionMarcaRepository marcaRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeclaracionMarcaModel registrarMarcaRetencionExcedida(
            String tipo,
            Long declaracionId,
            String motivo,
            Long usuarioDeclaranteId,
            Long regionId,
            String umbralParam,
            String valorParam,
            String origenParam
    ) {
        String tipoNorm = tipo != null ? tipo.trim().toUpperCase() : "DESCONOCIDO";

        // Verificar si existe una marca previa resuelta como LIBERADA
        List<DeclaracionMarcaModel> marcasPrevias = marcaRepository.findByDeclaracionTipoAndDeclaracionId(tipoNorm, declaracionId);
        boolean yaLiberada = marcasPrevias.stream()
                .anyMatch(m -> "RETENCION_EXCEDIDA".equalsIgnoreCase(m.getMarca()) && "LIBERADA".equalsIgnoreCase(m.getResolucionTipo()));

        if (yaLiberada) {
            log.info("Carga {}:{} ya fue formalmente LIBERADA por fiscalizador. Se omite re-bloqueo.", tipoNorm, declaracionId);
            return marcasPrevias.stream()
                    .filter(m -> "RETENCION_EXCEDIDA".equalsIgnoreCase(m.getMarca()))
                    .findFirst()
                    .orElse(null);
        }

        // Determinar valor numérico observado (máx 40 chars para VARCHAR(40))
        String valorObs = valorParam;
        if (valorObs == null || valorObs.isBlank()) {
            if (motivo != null) {
                Matcher matcher = HORAS_PATTERN.matcher(motivo);
                if (matcher.find()) {
                    valorObs = matcher.group(1);
                } else {
                    valorObs = motivo.length() <= 40 ? motivo : motivo.substring(0, 40);
                }
            } else {
                valorObs = "120+";
            }
        }
        if (valorObs.length() > 40) {
            valorObs = valorObs.substring(0, 40);
        }

        // Determinar umbral (máx 40 chars)
        String umbral = (umbralParam != null && !umbralParam.isBlank()) ? umbralParam : "120";
        if (umbral.length() > 40) {
            umbral = umbral.substring(0, 40);
        }

        // Origen válido (máx 20 chars para VARCHAR(20)): "BARRIDO" o "VALIDACION"
        String origen = (origenParam != null && !origenParam.isBlank()) ? origenParam.trim().toUpperCase() : "VALIDACION";
        if (origen.length() > 20) {
            origen = origen.substring(0, 20);
        }

        CriterioHallazgo criterio = new CriterioHallazgo(
                "retencion_horas",
                umbral,
                valorObs,
                "h"
        );

        return hallazgoService.registrar(
                tipoNorm,
                declaracionId,
                "RETENCION_EXCEDIDA",
                "D3",
                criterio,
                motivo,
                origen,
                usuarioDeclaranteId,
                regionId
        );
    }
}
