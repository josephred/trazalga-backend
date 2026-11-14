package com.trazalga.api.services.catalogo;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Parser puro (sin acceso a BD) para los nombres del catálogo oficial Sernapesca (QF.2).
 * Distingue filas de recurso (especies 141, 142, 145) de filas de formato de producto (8 partes
 * separadas por {@code \s*-\s+}) y clasifica cualquier anomalía como {@code POR_REVISAR}.
 */
public final class NombreFormatoOficial {

    private static final Pattern SEPARADOR_PARTES = Pattern.compile("\\s*-\\s+");
    private static final Pattern ESPACIOS_MULTIPLES = Pattern.compile("\\s+");
    private static final Pattern ESPACIOS_BARRA = Pattern.compile("\\s*/\\s*");
    private static final String ESTADO_OFICIAL_SECO = "SECO (SOLO ALGAS)";

    private static final Map<String, String> SINONIMOS_ESPECIE = Map.of(
            "HUIRO NEGRO O CHASCON", "HUIRO NEGRO",
            "CHASCON O HUIRO NEGRO", "HUIRO NEGRO",
            "HUIRO NEGRO", "HUIRO NEGRO",
            "CHASCON", "HUIRO NEGRO",
            "HUIRO PALO", "HUIRO PALO",
            "HUIRO", "HUIRO"
    );

    public enum TipoFila {
        RECURSO,
        FORMATO,
        POR_REVISAR
    }

    public record Resultado(
            TipoFila tipo,
            String codigo,
            String nombreOficial,
            String especieCanon,
            String estadoHumedad,
            String presentacion,
            String calibre,
            String envase,
            String cantidad,
            String unidad,
            String motivo
    ) {
    }

    private NombreFormatoOficial() {
    }

    /**
     * Analiza una dupla {@code (codigo, nombre)} proveniente del listado oficial de Sernapesca.
     */
    public static Resultado analizar(String codigoRaw, String nombreRaw) {
        String codigo = codigoRaw != null ? colapsarEspacios(codigoRaw) : "";
        String nombreOficial = nombreRaw != null ? colapsarEspacios(nombreRaw) : "";

        if (codigo.isEmpty()) {
            return porRevisar(codigo, nombreOficial, "Código vacío.");
        }
        if (nombreOficial.isEmpty()) {
            return porRevisar(codigo, nombreOficial, "Nombre vacío.");
        }

        // Regla 1: Fila de recurso (no contiene " - ")
        if (!nombreOficial.contains(" - ")) {
            String especie = normalizarEspecie(nombreOficial);
            if (especie == null) {
                return porRevisar(codigo, nombreOficial, "Recurso/especie no reconocido en el maestro: '" + nombreOficial + "'.");
            }
            return new Resultado(TipoFila.RECURSO, codigo, nombreOficial, especie, null, null, null, null, null, null, null);
        }

        // Regla 2: Fila de formato — 8 partes separadas por guion seguido de espacio (\s*-\s+)
        String[] partes = SEPARADOR_PARTES.split(nombreOficial, -1);
        if (partes.length != 8) {
            return porRevisar(codigo, nombreOficial,
                    "Se esperaban 8 partes separadas por '-' y se encontraron " + partes.length + ".");
        }
        for (int i = 0; i < partes.length; i++) {
            partes[i] = colapsarEspacios(partes[i]);
        }

        String especie = normalizarEspecie(partes[0]);
        if (especie == null) {
            return porRevisar(codigo, nombreOficial, "Especie no reconocida: '" + partes[0] + "'.");
        }

        String estadoCanon = normalizarEstadoHumedad(partes[1]);
        if (estadoCanon == null) {
            return porRevisar(codigo, nombreOficial, "Estado de humedad no soportado: '" + partes[1] + "'.");
        }

        String presentacion = normalizarPresentacion(partes[3]);
        if (presentacion.isEmpty()) {
            return porRevisar(codigo, nombreOficial, "Presentación vacía.");
        }
        if (presentacion.length() > 60) {
            return porRevisar(codigo, nombreOficial, "Presentación excede 60 caracteres.");
        }

        String calibre = normalizarCalibre(partes[4]);
        if (calibre.length() > 40) {
            return porRevisar(codigo, nombreOficial, "Calibre excede 40 caracteres.");
        }

        if (nombreOficial.length() > 150) {
            return porRevisar(codigo, nombreOficial, "El nombre oficial supera los 150 caracteres (" + nombreOficial.length() + ").");
        }

        return new Resultado(
                TipoFila.FORMATO,
                codigo,
                nombreOficial,
                especie,
                estadoCanon,
                presentacion,
                calibre,
                partes[5],
                partes[6],
                partes[7],
                null
        );
    }

    /**
     * Resuelve el nombre canónico del maestro de especies a partir de sinónimos conocidos.
     * Devuelve {@code null} si no corresponde a ninguna especie admitida por el catálogo.
     */
    public static String normalizarEspecie(String raw) {
        String clave = normalizarClave(raw);
        if (clave.isEmpty()) {
            return null;
        }
        return SINONIMOS_ESPECIE.get(clave);
    }

    private static String normalizarEstadoHumedad(String raw) {
        String clave = normalizarClave(raw);
        if (ESTADO_OFICIAL_SECO.equals(clave)) {
            return "SECO";
        }
        return null;
    }

    private static String normalizarPresentacion(String raw) {
        if (raw == null) return "";
        return ESPACIOS_BARRA.matcher(colapsarEspacios(raw)).replaceAll("/");
    }

    private static String normalizarCalibre(String raw) {
        String t = colapsarEspacios(raw);
        if (t.isEmpty() || "NINGUNO".equalsIgnoreCase(t)) {
            return "";
        }
        return t;
    }

    private static String colapsarEspacios(String raw) {
        if (raw == null) return "";
        return ESPACIOS_MULTIPLES.matcher(raw.trim()).replaceAll(" ");
    }

    private static String normalizarClave(String raw) {
        if (raw == null) return "";
        String colapsado = colapsarEspacios(raw);
        String sinAcentos = Normalizer.normalize(colapsado, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return sinAcentos.toUpperCase(Locale.ROOT);
    }

    private static Resultado porRevisar(String codigo, String nombreOficial, String motivo) {
        return new Resultado(TipoFila.POR_REVISAR, codigo, nombreOficial, null, null, null, null, null, null, null, motivo);
    }
}
