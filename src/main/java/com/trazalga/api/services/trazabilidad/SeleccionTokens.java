package com.trazalga.api.services.trazabilidad;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utilidad para el campo {@code declaracionesSeleccionadas}.
 *
 * <p>Histórico: era un CSV de IDs numéricos planos (ej. {@code "1,3,5"}). El problema es que
 * cada tipo de declaración de origen (recolector, armador, área, comercializador, plantas...)
 * vive en su propia tabla con IDs autoincrementales independientes, por lo que el id {@code 1}
 * existe en varias tablas a la vez. Al consumir por id plano se marcaba/liberaba la declaración
 * del tipo equivocado.
 *
 * <p>Nuevo formato: CSV de tokens {@code TIPO:id} (ej. {@code "RECOLECTOR:1,ARMADOR:1,AREA:5"}).
 *
 * <p>Retrocompatibilidad: un token sin {@code :} se interpreta como {@code LEGACY} y se aplica a
 * todas las tablas de origen del consumidor (comportamiento antiguo), de modo que los registros
 * ya guardados con IDs planos siguen pudiéndose marcar/liberar.
 */
public final class SeleccionTokens {

    /** Clave para IDs heredados (sin tipo); aplican a cualquier tabla de origen del consumidor. */
    public static final String LEGACY = "LEGACY";

    private SeleccionTokens() {
    }

    /**
     * Parsea el CSV a un mapa {@code TIPO -> [ids]}. Los tokens sin tipo caen en {@link #LEGACY}.
     * Tokens malformados se ignoran silenciosamente.
     */
    public static Map<String, List<Long>> parse(String csv) {
        Map<String, List<Long>> out = new HashMap<>();
        if (csv == null || csv.isBlank()) {
            return out;
        }
        for (String raw : csv.split(",")) {
            String token = raw.trim();
            if (token.isEmpty()) {
                continue;
            }
            String tipo;
            String idStr;
            int idx = token.indexOf(':');
            if (idx > 0) {
                tipo = token.substring(0, idx).trim().toUpperCase();
                idStr = token.substring(idx + 1).trim();
            } else {
                tipo = LEGACY;
                idStr = token;
            }
            try {
                Long id = Long.valueOf(idStr);
                out.computeIfAbsent(tipo, k -> new ArrayList<>()).add(id);
            } catch (NumberFormatException ignored) {
                // token malformado: se ignora
            }
        }
        return out;
    }

    /**
     * IDs aplicables a una tabla de origen del tipo dado, incluyendo los IDs heredados
     * ({@link #LEGACY}), que aplican a cualquier tabla de origen.
     */
    public static List<Long> idsParaTipo(Map<String, List<Long>> parsed, String tipo) {
        List<Long> result = new ArrayList<>();
        List<Long> propios = parsed.get(tipo == null ? null : tipo.toUpperCase());
        if (propios != null) {
            result.addAll(propios);
        }
        List<Long> legacy = parsed.get(LEGACY);
        if (legacy != null) {
            result.addAll(legacy);
        }
        return result;
    }
}
