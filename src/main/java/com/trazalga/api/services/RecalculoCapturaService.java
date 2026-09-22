package com.trazalga.api.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trazalga.api.dto.RecalculoResumenDTO;
import com.trazalga.api.dto.RecalculoResumenDTO.ItemRegularizacionDTO;
import com.trazalga.api.models.DeclaracionAreaModel;
import com.trazalga.api.models.DeclaracionArmadorModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.models.FactorConversionModel;
import com.trazalga.api.repositories.IDeclaracionAreaRepository;
import com.trazalga.api.repositories.IDeclaracionArmadorRepository;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class RecalculoCapturaService {

    @Autowired
    private IDeclaracionRecolectorRepository declaracionRecolectorRepository;

    @Autowired
    private IDeclaracionArmadorRepository declaracionArmadorRepository;

    @Autowired
    private IDeclaracionAreaRepository declaracionAreaRepository;

    @Autowired
    private FactorConversionService factorConversionService;

    @Autowired
    private ConfiguracionAuditoriaService configuracionAuditoriaService;

    /**
     * Ejecuta el recálculo masivo histórico de capturas para recolectores, armadores y áreas de manejo.
     * @param dryRun Si es true, simula el cálculo sin escribir en base de datos.
     * @return Resumen con métricas y lista de regularización.
     */
    public RecalculoResumenDTO recalcularHistorico(boolean dryRun) {
        log.info("Iniciando recálculo histórico de capturas biológicas (dryRun={})...", dryRun);

        long procesadas = 0;
        long actualizadas = 0;
        long omitidas = 0;
        BigDecimal totDesembarque = BigDecimal.ZERO;
        BigDecimal totCapturaAnt = BigDecimal.ZERO;
        BigDecimal totCapturaNue = BigDecimal.ZERO;
        List<ItemRegularizacionDTO> regularizaciones = new ArrayList<>();

        // 1. Declaraciones de Recolector
        List<DeclaracionRecolectorModel> recolectores = declaracionRecolectorRepository.findAll();
        for (DeclaracionRecolectorModel dr : recolectores) {
            procesadas++;
            if (dr.getEspecie() == null || dr.getHumedadEstado() == null) {
                omitidas++;
                regularizaciones.add(ItemRegularizacionDTO.builder()
                        .tipoDeclaracion("RECOLECTOR")
                        .id(dr.getId())
                        .folio(dr.getFolioOrigen())
                        .fecha(dr.getFechaDeclaracion() != null ? dr.getFechaDeclaracion().toString() : "N/A")
                        .motivo("Falta especie o estado de humedad en la declaración")
                        .build());
                continue;
            }

            BigDecimal desKg = dr.getDesembarque() != null ? dr.getDesembarque() : BigDecimal.ZERO;
            Date fechaExt = dr.getFechaExtraccion() != null ? dr.getFechaExtraccion() : dr.getFechaDeclaracion();
            Optional<FactorConversionModel> factorOpt = factorConversionService.findFactorVigente(
                    dr.getEspecie().getId(), dr.getHumedadEstado().getId(), fechaExt);

            if (factorOpt.isEmpty()) {
                omitidas++;
                regularizaciones.add(ItemRegularizacionDTO.builder()
                        .tipoDeclaracion("RECOLECTOR")
                        .id(dr.getId())
                        .folio(dr.getFolioOrigen())
                        .fecha(fechaExt != null ? fechaExt.toString() : "N/A")
                        .motivo(String.format("Sin factor vigente para especie %s y humedad %s a fecha %s",
                                dr.getEspecie().getNombre(), dr.getHumedadEstado().getNombre(), fechaExt))
                        .build());
                continue;
            }

            FactorConversionModel fc = factorOpt.get();
            BigDecimal factor = fc.getFactor();
            Long factorId = fc.getId();
            BigDecimal capAnt = dr.getCaptura() != null ? dr.getCaptura() : desKg;
            BigDecimal capNue = desKg.multiply(factor).setScale(2, RoundingMode.HALF_UP);

            totDesembarque = totDesembarque.add(desKg);
            totCapturaAnt = totCapturaAnt.add(capAnt);
            totCapturaNue = totCapturaNue.add(capNue);
            actualizadas++;

            if (!dryRun) {
                BigDecimal factorAnt = dr.getFactorAplicado();
                dr.setCaptura(capNue);
                dr.setFactorAplicado(factor);
                dr.setFactorConversionId(factorId);
                declaracionRecolectorRepository.save(dr);

                configuracionAuditoriaService.registrar(
                        "DECLARACION_RECOLECTOR",
                        String.valueOf(dr.getId()),
                        "captura_biologica",
                        String.format("captura=%s, factor=%s", capAnt, factorAnt),
                        String.format("captura=%s, factor=%s, factor_id=%s", capNue, factor, factorId),
                        null);
            }
        }

        // 2. Declaraciones de Armador
        List<DeclaracionArmadorModel> armadores = declaracionArmadorRepository.findAll();
        for (DeclaracionArmadorModel da : armadores) {
            procesadas++;
            if (da.getEspecie() == null || da.getHumedadEstado() == null) {
                omitidas++;
                regularizaciones.add(ItemRegularizacionDTO.builder()
                        .tipoDeclaracion("ARMADOR")
                        .id(da.getId())
                        .folio(da.getFolioOrigen())
                        .fecha(da.getFechaDeclaracion() != null ? da.getFechaDeclaracion().toString() : "N/A")
                        .motivo("Falta especie o estado de humedad en la declaración")
                        .build());
                continue;
            }

            BigDecimal desKg = da.getDesembarque() != null ? da.getDesembarque() : BigDecimal.ZERO;
            Date fechaExt = da.getFechaExtraccion() != null ? da.getFechaExtraccion() : da.getFechaDeclaracion();
            Optional<FactorConversionModel> factorOpt = factorConversionService.findFactorVigente(
                    da.getEspecie().getId(), da.getHumedadEstado().getId(), fechaExt);

            if (factorOpt.isEmpty()) {
                omitidas++;
                regularizaciones.add(ItemRegularizacionDTO.builder()
                        .tipoDeclaracion("ARMADOR")
                        .id(da.getId())
                        .folio(da.getFolioOrigen())
                        .fecha(fechaExt != null ? fechaExt.toString() : "N/A")
                        .motivo(String.format("Sin factor vigente para especie %s y humedad %s a fecha %s",
                                da.getEspecie().getNombre(), da.getHumedadEstado().getNombre(), fechaExt))
                        .build());
                continue;
            }

            FactorConversionModel fc = factorOpt.get();
            BigDecimal factor = fc.getFactor();
            Long factorId = fc.getId();
            BigDecimal capAnt = da.getCaptura() != null ? BigDecimal.valueOf(da.getCaptura()).setScale(2, RoundingMode.HALF_UP) : desKg;
            BigDecimal capNue = desKg.multiply(factor).setScale(2, RoundingMode.HALF_UP);

            totDesembarque = totDesembarque.add(desKg);
            totCapturaAnt = totCapturaAnt.add(capAnt);
            totCapturaNue = totCapturaNue.add(capNue);
            actualizadas++;

            if (!dryRun) {
                BigDecimal factorAnt = da.getFactorAplicado();
                da.setCaptura(capNue.doubleValue());
                da.setFactorAplicado(factor);
                da.setFactorConversionId(factorId);
                declaracionArmadorRepository.save(da);

                configuracionAuditoriaService.registrar(
                        "DECLARACION_ARMADOR",
                        String.valueOf(da.getId()),
                        "captura_biologica",
                        String.format("captura=%s, factor=%s", capAnt, factorAnt),
                        String.format("captura=%s, factor=%s, factor_id=%s", capNue, factor, factorId),
                        null);
            }
        }

        // 3. Declaraciones de Área de Manejo
        List<DeclaracionAreaModel> areas = declaracionAreaRepository.findAll();
        for (DeclaracionAreaModel dar : areas) {
            procesadas++;
            if (dar.getEspecie() == null || dar.getHumedadEstado() == null) {
                omitidas++;
                regularizaciones.add(ItemRegularizacionDTO.builder()
                        .tipoDeclaracion("AREA")
                        .id(dar.getId())
                        .folio(dar.getFolioOrigen())
                        .fecha(dar.getFechaDeclaracion() != null ? dar.getFechaDeclaracion().toString() : "N/A")
                        .motivo("Falta especie o estado de humedad en la declaración")
                        .build());
                continue;
            }

            BigDecimal desKg = dar.getDesembarque() != null ? BigDecimal.valueOf(dar.getDesembarque()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            Date fechaExt = dar.getFechaExtraccion() != null ? dar.getFechaExtraccion() : dar.getFechaDeclaracion();
            Optional<FactorConversionModel> factorOpt = factorConversionService.findFactorVigente(
                    dar.getEspecie().getId(), dar.getHumedadEstado().getId(), fechaExt);

            if (factorOpt.isEmpty()) {
                omitidas++;
                regularizaciones.add(ItemRegularizacionDTO.builder()
                        .tipoDeclaracion("AREA")
                        .id(dar.getId())
                        .folio(dar.getFolioOrigen())
                        .fecha(fechaExt != null ? fechaExt.toString() : "N/A")
                        .motivo(String.format("Sin factor vigente para especie %s y humedad %s a fecha %s",
                                dar.getEspecie().getNombre(), dar.getHumedadEstado().getNombre(), fechaExt))
                        .build());
                continue;
            }

            FactorConversionModel fc = factorOpt.get();
            BigDecimal factor = fc.getFactor();
            Long factorId = fc.getId();
            BigDecimal capAnt = dar.getCaptura() != null ? BigDecimal.valueOf(dar.getCaptura()).setScale(2, RoundingMode.HALF_UP) : desKg;
            BigDecimal capNue = desKg.multiply(factor).setScale(2, RoundingMode.HALF_UP);

            totDesembarque = totDesembarque.add(desKg);
            totCapturaAnt = totCapturaAnt.add(capAnt);
            totCapturaNue = totCapturaNue.add(capNue);
            actualizadas++;

            if (!dryRun) {
                BigDecimal factorAnt = dar.getFactorAplicado();
                dar.setCaptura(capNue.doubleValue());
                dar.setFactorAplicado(factor);
                dar.setFactorConversionId(factorId);
                declaracionAreaRepository.save(dar);

                configuracionAuditoriaService.registrar(
                        "DECLARACION_AREA",
                        String.valueOf(dar.getId()),
                        "captura_biologica",
                        String.format("captura=%s, factor=%s", capAnt, factorAnt),
                        String.format("captura=%s, factor=%s, factor_id=%s", capNue, factor, factorId),
                        null);
            }
        }

        BigDecimal variacionTotal = totCapturaNue.subtract(totCapturaAnt);
        log.info("Recálculo completado (dryRun={}): procesadas={}, actualizadas={}, omitidas={}, variacionKg={}",
                dryRun, procesadas, actualizadas, omitidas, variacionTotal);

        return RecalculoResumenDTO.builder()
                .dryRun(dryRun)
                .totalProcesadas(procesadas)
                .totalActualizadas(actualizadas)
                .totalOmitidas(omitidas)
                .totalDesembarqueKg(totDesembarque)
                .totalCapturaAnteriorKg(totCapturaAnt)
                .totalCapturaNuevaKg(totCapturaNue)
                .variacionTotalKg(variacionTotal)
                .regularizaciones(regularizaciones)
                .build();
    }
}
