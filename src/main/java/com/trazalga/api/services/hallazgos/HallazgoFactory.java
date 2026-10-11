package com.trazalga.api.services.hallazgos;

import org.springframework.stereotype.Component;

import com.trazalga.api.models.DeclaracionMarcaModel;

/**
 * Fábrica de hallazgos y marcas de sistema (TA.3).
 * Construye marcas normalizadas con clave de idempotencia estándar
 * y criterios estructurados en es-CL.
 */
@Component
public class HallazgoFactory {

    /**
     * Construye la clave de idempotencia para evitar marcas duplicadas.
     * Formatos estándar:
     * - General: TIPO:ID:MARCA:REGLA
     * - Retención en bodega: TIPO:ID:RETENCION_EXCEDIDA
     */
    public String generarClaveIdempotencia(String tipo, Long id, String marca, String regla) {
        String tipoNorm = tipo != null ? tipo.trim().toUpperCase() : "DESCONOCIDO";
        String marcaNorm = marca != null ? marca.trim().toUpperCase() : "MARCA";
        long idVal = id != null ? id : 0L;

        if ("RETENCION_EXCEDIDA".equals(marcaNorm)) {
            return String.format("%s:%d:RETENCION_EXCEDIDA", tipoNorm, idVal);
        }

        String reglaNorm = (regla != null && !regla.isBlank()) ? regla.trim() : "GENERAL";
        return String.format("%s:%d:%s:%s", tipoNorm, idVal, marcaNorm, reglaNorm);
    }

    /**
     * Construye una nueva instancia de DeclaracionMarcaModel a partir del criterio estructurado.
     */
    public DeclaracionMarcaModel crear(
            String tipo,
            Long declaracionId,
            String marca,
            String regla,
            CriterioHallazgo criterio,
            String detalleFallback,
            String origen
    ) {
        String clave = generarClaveIdempotencia(tipo, declaracionId, marca, regla);
        String detalleFinal = (criterio != null && criterio.texto() != null)
                ? criterio.texto()
                : (detalleFallback != null ? detalleFallback : marca);

        Long reglaIdNum = null;
        if (regla != null) {
            try {
                reglaIdNum = Long.parseLong(regla.trim());
            } catch (NumberFormatException ignored) {
                // regla puede ser identificador alfanumérico
            }
        }

        return DeclaracionMarcaModel.builder()
                .declaracionTipo(tipo != null ? tipo.trim().toUpperCase() : null)
                .declaracionId(declaracionId)
                .marca(marca != null ? marca.trim().toUpperCase() : null)
                .detalle(detalleFinal)
                .reglaId(reglaIdNum)
                .criterioParametro(criterio != null ? criterio.parametro() : null)
                .criterioUmbral(criterio != null ? criterio.umbral() : null)
                .criterioValor(criterio != null ? criterio.valorObservado() : null)
                .criterioUnidad(criterio != null ? criterio.unidad() : null)
                .origen(origen != null ? origen.trim().toUpperCase() : "VALIDACION")
                .claveIdempotencia(clave)
                .resuelta(false)
                .estadoGestion("PENDIENTE")
                .build();
    }
}
