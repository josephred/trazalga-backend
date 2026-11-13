package com.trazalga.api.services.fechas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.ZonedDateTime;
import java.util.Date;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.trazalga.api.ApiApplication;

/** Refinamiento 06-11, NL.2: las fechas de las consultas nativas viajan como DATE, con el día de Chile. */
class FechaSqlTest {

    private static Date enChile(int anio, int mes, int dia, int hora, int minuto) {
        return Date.from(ZonedDateTime.of(anio, mes, dia, hora, minuto, 0, 0, ApiApplication.ZONA_CHILE).toInstant());
    }

    @Test
    @DisplayName("La medianoche de Chile es ese día, no el anterior ni una hora UTC")
    void medianocheDeChile() {
        assertEquals("2026-11-06", FechaSql.dia(enChile(2026, 11, 6, 0, 0)).toString());
    }

    @Test
    @DisplayName("Las 23:30 de Chile siguen siendo ese día, aunque en UTC ya sea el siguiente")
    void finDelDiaEnChile() {
        assertEquals("2026-11-06", FechaSql.dia(enChile(2026, 11, 6, 23, 30)).toString());
    }

    @Test
    @DisplayName("En horario de invierno (UTC-4) también")
    void horarioDeInvierno() {
        assertEquals("2026-07-15", FechaSql.dia(enChile(2026, 7, 15, 0, 0)).toString());
        assertEquals("2026-07-15", FechaSql.dia(enChile(2026, 7, 15, 21, 0)).toString());
    }

    @Test
    @DisplayName("Un java.sql.Date conserva su día y null queda en null")
    void sqlDateYNull() {
        assertEquals("2026-11-06", FechaSql.dia(java.sql.Date.valueOf("2026-11-06")).toString());
        assertNull(FechaSql.dia(null));
    }
}
