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
        return registrarMarcaRetencionExcedida(tipo, declaracionId, motivo, usuarioDeclaranteId, regionId, umbralParam, valorParam, origenParam, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeclaracionMarcaModel registrarMarcaRetencionExcedida(
            String tipo,
            Long declaracionId,
            String motivo,
            Long usuarioDeclaranteId,
            Long regionId,
            String umbralParam,
            String valorParam,
            String origenParam,
            String parametroNombre
    ) {
        String tipoNorm = tipo != null ? tipo.trim().toUpperCase() : "DESCONOCIDO";

        // TR.3: Determinar umbral. Si no viene en umbralParam, intentar resolverlo desde el motivo
        String umbral = (umbralParam != null && !umbralParam.isBlank()) ? umbralParam.trim() : null;
        if (umbral == null && motivo != null) {
            Matcher m = Pattern.compile("(?:límite de bloqueo de|bloqueo automático a las|retención máxima de)\\s*(\\d+(?:\\.\\d+)?)\\s*h", Pattern.CASE_INSENSITIVE).matcher(motivo);
            if (m.find()) {
                umbral = m.group(1);
            }
        }

        if (umbral == null || umbral.isBlank()) {
            log.warn("Se intentó registrar RETENCION_EXCEDIDA para {}:{} sin umbral de bloqueo válido.", tipoNorm, declaracionId);
            return null;
        }

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
        if (umbral.length() > 40) {
            umbral = umbral.substring(0, 40);
        }

        // Origen válido (máx 20 chars para VARCHAR(20)): "BARRIDO" o "VALIDACION"
        String origen = (origenParam != null && !origenParam.isBlank()) ? origenParam.trim().toUpperCase() : "VALIDACION";
        if (origen.length() > 20) {
            origen = origen.substring(0, 20);
        }

        String paramFinal = (parametroNombre != null && !parametroNombre.isBlank())
                ? parametroNombre.trim()
                : (motivo != null && (motivo.contains("SEMI HÚMEDO") || motivo.contains("SEMI_HUMEDO"))
                        ? "retencion_bloqueo_semihumedo_horas"
                        : (motivo != null && (motivo.contains("SEMI SECO") || motivo.contains("SEMI_SECO"))
                                ? "retencion_bloqueo_semiseco_horas"
                                : "retencion_bloqueo_humedo_horas"));

        CriterioHallazgo criterio = new CriterioHallazgo(
                paramFinal,
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
