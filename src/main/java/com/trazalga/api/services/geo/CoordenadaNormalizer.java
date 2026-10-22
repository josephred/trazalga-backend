package com.trazalga.api.services.geo;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilitario para normalización y validación de coordenadas geográficas
 * y cadenas de texto según especificación TG.1.
 */
public class CoordenadaNormalizer {

    // Rango oficial Chile continental (definido en TG.1)
    public static final double LAT_MIN = -56.0;
    public static final double LAT_MAX = -17.4;
    public static final double LON_MIN = -76.0;
    public static final double LON_MAX = -66.4;

    // Patrón DMS: 29°54'09.7"S, 71°15'10.2"W, 29º54'09,7"S, 29 54 9.7 S
    private static final Pattern DMS_PATTERN = Pattern.compile(
        "^([+-]?\\d{1,3})[°º\\s]+(\\d{1,2})[''´\\s]+(\\d{1,2}(?:[.,]\\d+)?)[\\\"\\s]*([NSEWO])?$",
        Pattern.CASE_INSENSITIVE
    );

    // Patrón grados decimales simples con o sin letra cardinal: -29.9027, 29.9027S, 71.25 W
    private static final Pattern DECIMAL_PATTERN = Pattern.compile(
        "^([+-]?\\d+(?:[.,]\\d+)?)\\s*([NSEWO])?$",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * Parsea una coordenada (latitud o longitud) en grados decimales o formato DMS.
     * Sur (S) y Oeste (W, O) siempre resultan en signo negativo.
     * Acepta punto o coma decimal.
     */
    public static Double parsearCoordenada(String raw, boolean isLatitud) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }

        // Intento 1: Grados, Minutos, Segundos (DMS)
        Matcher dmsMatcher = DMS_PATTERN.matcher(s);
        if (dmsMatcher.matches()) {
            double degrees = Double.parseDouble(dmsMatcher.group(1));
            double minutes = Double.parseDouble(dmsMatcher.group(2));
            double seconds = Double.parseDouble(dmsMatcher.group(3).replace(',', '.'));
            String cardinal = dmsMatcher.group(4);

            double sign = (degrees < 0) ? -1.0 : 1.0;
            degrees = Math.abs(degrees);

            double decimal = degrees + (minutes / 60.0) + (seconds / 3600.0);
            decimal = decimal * sign;

            if (cardinal != null) {
                cardinal = cardinal.toUpperCase(Locale.ROOT);
                if (cardinal.equals("S") || cardinal.equals("W") || cardinal.equals("O")) {
                    decimal = -Math.abs(decimal);
                } else if (cardinal.equals("N") || cardinal.equals("E")) {
                    decimal = Math.abs(decimal);
                }
            } else if (decimal > 0) {
                // En Chile continental, latitud (Sur) y longitud (Oeste) son negativas por defecto
                decimal = -decimal;
            }
            return decimal;
        }

        // Intento 2: Grados Decimales
        Matcher decMatcher = DECIMAL_PATTERN.matcher(s);
        if (decMatcher.matches()) {
            double val = Double.parseDouble(decMatcher.group(1).replace(',', '.'));
            String cardinal = decMatcher.group(2);

            if (cardinal != null) {
                cardinal = cardinal.toUpperCase(Locale.ROOT);
                if (cardinal.equals("S") || cardinal.equals("W") || cardinal.equals("O")) {
                    val = -Math.abs(val);
                } else if (cardinal.equals("N") || cardinal.equals("E")) {
                    val = Math.abs(val);
                }
            } else if (val > 0) {
                // Por defecto en Chile, si se ingresa positivo sin cardinal, se asigna negativo
                val = -val;
            }
            return val;
        }

        // Si no cumple ningún patrón, intentamos parseo numérico directo con reemplazo de coma
        try {
            double val = Double.parseDouble(s.replace(',', '.'));
            if (val > 0) {
                val = -val;
            }
            return val;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * Valida si el datum provisto es compatible con WGS84.
     */
    public static boolean esDatumWgs84(String datum) {
        if (datum == null || datum.trim().isEmpty()) {
            return true; // Si no se especifica, se asume WGS84 conforme estándar
        }
        String clean = datum.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return clean.equals("WGS84") || clean.equals("EPSG4326") || clean.equals("GPS");
    }

    /**
     * Valida si las coordenadas están dentro del rango geográfico continental de Chile.
     */
    public static boolean estaEnRangoChile(Double lat, Double lon) {
        if (lat == null || lon == null) {
            return false;
        }
        return lat >= LAT_MIN && lat <= LAT_MAX && lon >= LON_MIN && lon <= LON_MAX;
    }

    /**
     * Normaliza nombres eliminando tildes, signos diacríticos, mayúsculas y espacios duplicados.
     */
    public static String normalizarTexto(String texto) {
        if (texto == null) {
            return "";
        }
        String n = texto.trim();
        // Separar caracteres base de acentos
        n = Normalizer.normalize(n, Normalizer.Form.NFD);
        n = n.replaceAll("\\p{M}", ""); // Remueve tildes
        n = n.toUpperCase(Locale.ROOT);
        n = n.replaceAll("[^A-Z0-9\\s]", " "); // Reemplaza símbolos raros con espacio
        n = n.replaceAll("\\s+", " ").trim(); // Colapsa espacios
        return n;
    }
}
