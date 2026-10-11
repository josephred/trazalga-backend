package com.trazalga.api.listeners;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.events.CargaRetenida;
import com.trazalga.api.events.CuotaCerrada;
import com.trazalga.api.events.CuotaUmbralAlcanzado;
import com.trazalga.api.events.HallazgoRegistrado;
import com.trazalga.api.services.NotificationService;
import com.trazalga.api.services.hallazgos.PoliticaNotificacionHallazgo;

@ExtendWith(MockitoExtension.class)
class NotificacionesListenerTest {

    @Mock
    private NotificationService notificationService;

    @Spy
    private PoliticaNotificacionHallazgo politica;

    @InjectMocks
    private NotificacionesListener listener;

    @Test
    @DisplayName("TA.5 - Hallazgo EN_VEDA notifica al declarante y fiscalizadores con enlace específico")
    void hallazgoEnVedaNotificaActoresCorrectos() {
        HallazgoRegistrado evento = new HallazgoRegistrado(
                10L, "EN_VEDA", "RECOLECTOR", 101L, 5L, 4L
        );

        listener.onHallazgoRegistrado(evento);

        verify(notificationService, times(1)).sendPushNotificationToUser(
                eq(5L),
                contains("Veda Biológica"),
                contains("EN_VEDA"),
                argThat(data -> "/alertas?marca=EN_VEDA".equals(data.get("enlace")))
        );

        verify(notificationService, times(1)).notificarFiscalizadores(
                eq(4L),
                contains("Veda Biológica"),
                contains("EN_VEDA"),
                argThat(data -> "/alertas?marca=EN_VEDA".equals(data.get("enlace")))
        );
    }

    @Test
    @DisplayName("TA.5 - CuotaCerrada notifica cierre administrativo a fiscalizadores")
    void cuotaCerradaNotificaFiscalizadores() {
        CuotaCerrada evento = new CuotaCerrada(55L, "ADMINISTRATIVO");

        listener.onCuotaCerrada(evento);

        verify(notificationService, times(1)).notificarFiscalizadores(
                isNull(),
                contains("Cierre de Cuota"),
                contains("ADMINISTRATIVO"),
                argThat(data -> "/cuotas".equals(data.get("enlace")))
        );
    }

    @Test
    @DisplayName("TA.5 - CuotaUmbralAlcanzado notifica porcentaje a titular y fiscalizadores")
    void cuotaUmbralAlcanzadoNotificaTitularYFiscalizadores() {
        CuotaUmbralAlcanzado evento = new CuotaUmbralAlcanzado(55L, 20L, 92.5);

        listener.onCuotaUmbralAlcanzado(evento);

        verify(notificationService, times(1)).sendPushNotificationToUser(
                eq(20L),
                contains("Umbral de Cuota"),
                contains("92"),
                anyMap()
        );
        verify(notificationService, times(1)).notificarFiscalizadores(
                isNull(),
                contains("Umbral de Cuota"),
                contains("92"),
                anyMap()
        );
    }

    @Test
    @DisplayName("TA.5 - CargaRetenida notifica a titular de carga y fiscalizadores con enlace RETENCION_EXCEDIDA")
    void cargaRetenidaNotificaTitularYFiscalizadores() {
        CargaRetenida evento = new CargaRetenida(77L, 33L);

        listener.onCargaRetenida(evento);

        verify(notificationService, times(1)).sendPushNotificationToUser(
                eq(33L),
                contains("Carga Retenida"),
                anyString(),
                argThat(data -> "/alertas?marca=RETENCION_EXCEDIDA".equals(data.get("enlace")))
        );
        verify(notificationService, times(1)).notificarFiscalizadores(
                isNull(),
                contains("Carga Retenida"),
                contains("77"),
                argThat(data -> "/alertas?marca=RETENCION_EXCEDIDA".equals(data.get("enlace")))
        );
    }
}
