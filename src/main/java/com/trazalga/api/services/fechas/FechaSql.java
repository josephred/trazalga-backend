package com.trazalga.api.services.fechas;

import java.time.LocalDate;
import java.util.Date;

import com.trazalga.api.ApiApplication;

/**
 * Fechas para comparar con columnas {@code DATE} en consultas nativas (refinamiento 06-11, NL.2).
 * <p>
 * Un {@code java.util.Date} en una consulta nativa viaja como {@code TIMESTAMP}. Con la JVM en hora de
 * Chile y la conexión en UTC ({@code serverTimezone=UTC}), la medianoche de Chile llega a MySQL como
 * {@code 03:00} o {@code 04:00} y no calza con ninguna fecha {@code DATE}. Un {@code java.sql.Date} viaja
 * como fecha, sin hora.
 */
public final class FechaSql {

    private FechaSql() {
    }

    /** El día de {@code fecha} en hora de Chile, como {@code java.sql.Date}; {@code null} si no hay fecha. */
    public static java.sql.Date dia(Date fecha) {
        if (fecha == null) {
            return null;
        }
        return java.sql.Date.valueOf(diaLocal(fecha));
    }

    /** El día de {@code fecha} en hora de Chile. */
    public static LocalDate diaLocal(Date fecha) {
        if (fecha instanceof java.sql.Date sql) {
            return sql.toLocalDate();
        }
        return fecha.toInstant().atZone(ApiApplication.ZONA_CHILE).toLocalDate();
    }
}
