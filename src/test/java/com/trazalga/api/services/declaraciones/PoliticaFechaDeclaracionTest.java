package com.trazalga.api.services.declaraciones;

import com.trazalga.api.ApiApplication;
import com.trazalga.api.models.DeclaracionFechaAjusteLogModel;
import com.trazalga.api.repositories.IDeclaracionFechaAjusteLogRepository;
import com.trazalga.api.services.ConfiguracionGeneralService;
import com.trazalga.api.services.fechas.FechaSql;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PoliticaFechaDeclaracionTest {

    @Mock
    private ConfiguracionGeneralService configService;

    @Mock
    private IDeclaracionFechaAjusteLogRepository logRepository;

    private PoliticaFechaDeclaracion politica;

    @BeforeEach
    void setUp() {
        politica = new PoliticaFechaDeclaracion();
        politica.setConfigService(configService);
        politica.setLogRepository(logRepository);
        lenient().when(configService.getInt(eq("declaracion_tolerancia_reloj_min"), anyInt())).thenReturn(15);
        lenient().when(configService.getBoolean(eq("declaracion_corrige_fecha_futura"), anyBoolean())).thenReturn(true);
    }

    private Date dateChile(int y, int m, int d) {
        return Date.from(LocalDate.of(y, m, d).atStartOfDay(ApiApplication.ZONA_CHILE).toInstant());
    }

    @Test
    @DisplayName("ZT.4: declaración de las 22:30 con fecha de mañana queda hoy")
    void declaracionNocturnaConFechaManana_quedaHoy() {
        LocalDateTime servidorAhora = LocalDateTime.of(2026, 11, 16, 22, 31, 0);
        AtomicReference<Date> fechaDecl = new AtomicReference<>(dateChile(2026, 11, 17));
        AtomicReference<Date> fechaExt = new AtomicReference<>(dateChile(2026, 11, 17));

        PoliticaFechaDeclaracion.AjustePendiente ajuste = politica.ajustarSiFutura(
                "RECOLECTOR",
                fechaDecl.get(),
                "22:30:00",
                fechaDecl::set,
                List.of(new PoliticaFechaDeclaracion.FechaAcompanante(fechaExt.get(), fechaExt::set)),
                servidorAhora);

        assertNotNull(ajuste);
        assertEquals(PoliticaFechaDeclaracion.MOTIVO_AJUSTADO, ajuste.motivo());
        assertEquals(LocalDate.of(2026, 11, 17), ajuste.fechaOriginal());
        assertEquals(LocalDate.of(2026, 11, 16), ajuste.fechaGuardada());
        assertEquals(LocalDate.of(2026, 11, 16), FechaSql.diaLocal(fechaDecl.get()));
        assertEquals(LocalDate.of(2026, 11, 16), FechaSql.diaLocal(fechaExt.get()));

        politica.registrar(ajuste, 501L);
        ArgumentCaptor<DeclaracionFechaAjusteLogModel> cap = ArgumentCaptor.forClass(DeclaracionFechaAjusteLogModel.class);
        verify(logRepository).save(cap.capture());
        assertEquals("RECOLECTOR", cap.getValue().getDeclaracionTipo());
        assertEquals(501L, cap.getValue().getDeclaracionId());
        assertEquals(PoliticaFechaDeclaracion.MOTIVO_AJUSTADO, cap.getValue().getMotivo());
    }

    @Test
    @DisplayName("ZT.4: enviada sin conexión con fecha de hoy y hora posterior a la actual queda ayer")
    void declaracionOfflineConFechaHoyYHoraPosterior_quedaAyer() {
        LocalDateTime servidorAhora = LocalDateTime.of(2026, 11, 17, 10, 0, 0);
        AtomicReference<Date> fechaDecl = new AtomicReference<>(dateChile(2026, 11, 17));
        AtomicReference<Date> fechaExtPrev = new AtomicReference<>(dateChile(2026, 11, 15));

        PoliticaFechaDeclaracion.AjustePendiente ajuste = politica.ajustarSiFutura(
                "ARMADOR",
                fechaDecl.get(),
                "23:10:00",
                fechaDecl::set,
                List.of(new PoliticaFechaDeclaracion.FechaAcompanante(fechaExtPrev.get(), fechaExtPrev::set)),
                servidorAhora);

        assertNotNull(ajuste);
        assertEquals(PoliticaFechaDeclaracion.MOTIVO_AJUSTADO, ajuste.motivo());
        assertEquals(LocalDate.of(2026, 11, 16), FechaSql.diaLocal(fechaDecl.get()));
        // La fecha de extracción de un día distinto no se modifica
        assertEquals(LocalDate.of(2026, 11, 15), FechaSql.diaLocal(fechaExtPrev.get()));
    }

    @Test
    @DisplayName("ZT.4: declaración de ayer no cambia")
    void declaracionDeAyer_noCambia() {
        LocalDateTime servidorAhora = LocalDateTime.of(2026, 11, 16, 22, 31, 0);
        AtomicReference<Date> fechaDecl = new AtomicReference<>(dateChile(2026, 11, 15));

        PoliticaFechaDeclaracion.AjustePendiente ajuste = politica.ajustarSiFutura(
                "COMERCIALIZADOR",
                fechaDecl.get(),
                "23:50:00",
                fechaDecl::set,
                List.of(),
                servidorAhora);

        assertNull(ajuste);
        assertEquals(LocalDate.of(2026, 11, 15), FechaSql.diaLocal(fechaDecl.get()));
    }

    @Test
    @DisplayName("ZT.4: declaración con dos días de adelanto no se corrige y se registra como reloj inconsistente")
    void declaracionDosDiasAdelanto_noCorrigeYRegistraInconsistente() {
        LocalDateTime servidorAhora = LocalDateTime.of(2026, 11, 16, 22, 31, 0);
        AtomicReference<Date> fechaDecl = new AtomicReference<>(dateChile(2026, 11, 18));

        PoliticaFechaDeclaracion.AjustePendiente ajuste = politica.ajustarSiFutura(
                "AREA",
                fechaDecl.get(),
                "22:30:00",
                fechaDecl::set,
                List.of(),
                servidorAhora);

        assertNotNull(ajuste);
        assertEquals(PoliticaFechaDeclaracion.MOTIVO_RELOJ_INCONSISTENTE, ajuste.motivo());
        assertEquals(LocalDate.of(2026, 11, 18), ajuste.fechaOriginal());
        assertEquals(LocalDate.of(2026, 11, 18), ajuste.fechaGuardada());
        assertEquals(LocalDate.of(2026, 11, 18), FechaSql.diaLocal(fechaDecl.get()));
    }
}
