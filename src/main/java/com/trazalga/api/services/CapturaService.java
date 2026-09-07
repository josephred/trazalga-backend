package com.trazalga.api.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.dto.CalculoCapturaResult;
import com.trazalga.api.models.FactorConversionModel;

@Service
public class CapturaService {

    @Autowired
    private FactorConversionService factorConversionService;

    @Autowired
    private ConfiguracionGeneralService configuracionGeneralService;

    /**
     * Calcula la captura corregida según la especie, estado de humedad y fecha de extracción.
     * captura = desembarque × factor_vigente
     */
    public CalculoCapturaResult calcular(Long especieId, Long humedadEstadoId, Date fechaExtraccion, BigDecimal desembarqueKg) {
        if (desembarqueKg == null || desembarqueKg.compareTo(BigDecimal.ZERO) < 0) {
            return CalculoCapturaResult.builder()
                    .exitoso(false)
                    .mensaje("El desembarque físico en kilogramos debe ser mayor o igual a 0.")
                    .build();
        }

        Date fecha = (fechaExtraccion != null) ? fechaExtraccion : new Date();
        Optional<FactorConversionModel> factorOpt = Optional.empty();

        if (especieId != null && humedadEstadoId != null) {
            factorOpt = factorConversionService.findFactorVigente(especieId, humedadEstadoId, fecha);
        }

        BigDecimal factor;
        Long factorId = null;

        if (factorOpt.isPresent()) {
            FactorConversionModel fc = factorOpt.get();
            factor = fc.getFactor();
            factorId = fc.getId();
        } else {
            String politica = configuracionGeneralService.getValor("captura_politica_sin_factor", "USAR_DEFAULT");
            if ("RECHAZAR_DECLARACION".equalsIgnoreCase(politica)) {
                return CalculoCapturaResult.builder()
                        .exitoso(false)
                        .mensaje("No existe un factor de conversión biológica vigente para la especie y estado de humedad a la fecha de extracción. La política actual exige factor registrado.")
                        .build();
            }

            double def = configuracionGeneralService.getDouble("captura_factor_default", 1.0);
            factor = BigDecimal.valueOf(def).setScale(4, RoundingMode.HALF_UP);
        }

        BigDecimal captura = desembarqueKg.multiply(factor).setScale(2, RoundingMode.HALF_UP);

        return CalculoCapturaResult.builder()
                .exitoso(true)
                .captura(captura)
                .factorAplicado(factor)
                .factorConversionId(factorId)
                .mensaje("Captura calculada con factor " + factor + (factorId != null ? " (ID: " + factorId + ")" : " (por defecto)"))
                .build();
    }
}
