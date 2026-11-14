package com.trazalga.api.services.indicadores;

import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import com.trazalga.api.models.DeclaracionPlantaProduccionModel;
import com.trazalga.api.repositories.IDeclaracionPlantaAbastecimientoRepository;
import com.trazalga.api.repositories.IDeclaracionPlantaProduccionRepository;
import com.trazalga.api.services.trazabilidad.SeleccionTokens;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Recálculo histórico de captura biológica de origen y rendimiento final sobre declaraciones
 * de producción existentes (QR.4 / D36).
 *
 * Pobla únicamente `captura_origen_kg`, `captura_origen_completa` y `rendimiento_final_pct`,
 * preservando intacto `rendimiento_pct` y SIN generar marcas ni bloquear destinos históricos.
 */
@Service
public class RecalculoRendimientoFinalService {

    @Autowired private IDeclaracionPlantaProduccionRepository produccionRepository;
    @Autowired private IDeclaracionPlantaAbastecimientoRepository abastecimientoRepository;
    @Autowired private CapturaOrigenProduccion capturaOrigenProduccion;
    @Autowired private RendimientoPlantaService rendimientoPlantaService;

    @Transactional(readOnly = false)
    public Map<String, Object> recalcularHistorico(boolean aplicar) {
        List<DeclaracionPlantaProduccionModel> todas = produccionRepository.findAll();
        Date ahora = new Date();

        long evaluadas = 0;
        long actualizadas = 0;
        long cadenasCompletas = 0;
        long cadenasIncompletas = 0;
        List<Map<String, Object>> habrianSidoAnomalas = new ArrayList<>();

        for (DeclaracionPlantaProduccionModel decl : todas) {
            if (decl.getEstado() != null && "ANULADA".equalsIgnoreCase(decl.getEstado().trim())) {
                continue;
            }
            evaluadas++;

            List<Long> loteIds = SeleccionTokens.idsParaTipo(
                    SeleccionTokens.parse(decl.getDeclaracionesSeleccionadas()), "PLANTA_ABASTECIMIENTO");
            List<DeclaracionPlantaAbastecimientoModel> lotes = loteIds.isEmpty()
                    ? Collections.emptyList()
                    : abastecimientoRepository.findAllById(loteIds);

            CapturaOrigenProduccion.ResultadoCapturaOrigen capRes =
                    capturaOrigenProduccion.resolverParaLotes(decl, lotes);
            RendimientoPlantaService.EvaluacionOrigenPreview preview =
                    rendimientoPlantaService.evaluarOrigenSinEfectos(decl, lotes, capRes, ahora);

            if (preview.capturaOrigenCompleta()) {
                cadenasCompletas++;
            } else {
                cadenasIncompletas++;
            }

            if (aplicar) {
                decl.setCapturaOrigenKg(preview.capturaOrigenKg());
                decl.setCapturaOrigenCompleta(preview.capturaOrigenCompleta());
                decl.setRendimientoFinalPct(preview.rendimientoFinalPct());
                produccionRepository.save(decl);
                actualizadas++;
            }

            if (preview.habriaSidoAnomala()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", decl.getId());
                row.put("folio", decl.getFolioDeclaracionPpla());
                row.put("fechaProduccion", decl.getFechaProduccion());
                row.put("cantidadProductoKg", decl.getCantidadProducto());
                row.put("capturaOrigenKg", preview.capturaOrigenKg());
                row.put("rendimientoProcesoPct", decl.getRendimientoPct());
                row.put("rendimientoFinalPct", preview.rendimientoFinalPct());
                row.put("rendimientoMaxTeoricoOrigenPct", preview.rendimientoMaxTeoricoOrigenPct());
                row.put("limiteSuperiorConToleranciaPct", preview.limiteSuperiorPct());
                row.put("fuenteOrigen", preview.fuenteOrigen());
                row.put("tipoAnomalia", preview.tipoAnomalia());
                row.put("detalle", preview.detalleAnomalia());
                habrianSidoAnomalas.add(row);
            }
        }

        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("aplicado", aplicar);
        resumen.put("totalEvaluadas", evaluadas);
        resumen.put("actualizadas", actualizadas);
        resumen.put("cadenasCompletas", cadenasCompletas);
        resumen.put("cadenasIncompletas", cadenasIncompletas);
        resumen.put("habrianSidoAnomalasCount", habrianSidoAnomalas.size());
        resumen.put("habrianSidoAnomalas", habrianSidoAnomalas);
        resumen.put("marcasCreadas", 0);
        resumen.put("destinosBloqueados", 0);
        return resumen;
    }
}
