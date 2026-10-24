package com.trazalga.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TN.2 · Respuesta pública mínima para consulta ciudadana por patente ({@code GET /api/public/patentes/{patente}}).
 * Lista blanca estricta de campos permitidos (Ley 21.719 / Ley 19.628):
 * <ul>
 *   <li>{@code patente}</li>
 *   <li>{@code resultado} ("AUTORIZADO" o "SIN_AUTORIZACION_VIGENTE")</li>
 *   <li>{@code fechaHoraAutorizacion} (sólo si {@code resultado == "AUTORIZADO"})</li>
 *   <li>{@code comunaDestino} (sólo si {@code resultado == "AUTORIZADO"})</li>
 *   <li>{@code tipoAlga} (sólo si {@code resultado == "AUTORIZADO"})</li>
 *   <li>{@code texto} ({@code consulta_publica_texto_proteccion}, puede venir vacío)</li>
 *   <li>{@code grafica} ({@code consulta_publica_grafica}, opcional si está configurada)</li>
 *   <li>{@code consultadoEn}</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConsultaPatentePublicaDTO {

    private String patente;
    private String resultado;
    private String fechaHoraAutorizacion;
    private String comunaDestino;
    private String tipoAlga;
    private String texto;
    private String grafica;
    private String consultadoEn;

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("patente", patente);
        map.put("resultado", resultado);
        if ("AUTORIZADO".equals(resultado)) {
            if (fechaHoraAutorizacion != null) {
                map.put("fechaHoraAutorizacion", fechaHoraAutorizacion);
            }
            if (comunaDestino != null) {
                map.put("comunaDestino", comunaDestino);
            }
            if (tipoAlga != null) {
                map.put("tipoAlga", tipoAlga);
            }
        }
        map.put("texto", texto != null ? texto : "");
        if (grafica != null && !grafica.isBlank()) {
            map.put("grafica", grafica);
        }
        map.put("consultadoEn", consultadoEn);
        return map;
    }
}
