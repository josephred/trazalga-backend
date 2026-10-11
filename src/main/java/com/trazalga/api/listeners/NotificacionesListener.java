package com.trazalga.api.listeners;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.trazalga.api.events.CargaRetenida;
import com.trazalga.api.events.CuotaCerrada;
import com.trazalga.api.events.CuotaUmbralAlcanzado;
import com.trazalga.api.events.HallazgoRegistrado;
import com.trazalga.api.services.NotificationService;
import com.trazalga.api.services.hallazgos.PoliticaNotificacionHallazgo;
import com.trazalga.api.services.hallazgos.PoliticaNotificacionHallazgo.DestinatariosNotificacion;

/**
 * Listener transaccional asíncrono para eventos de dominio (TA.5).
 * Se ejecuta exclusivamente en la fase AFTER_COMMIT: si una transacción revierte,
 * ninguna notificación espuria es despachada.
 */
@Component
public class NotificacionesListener {

    @Autowired(required = false)
    private NotificationService notificationService;

    @Autowired
    private PoliticaNotificacionHallazgo politica;

    @Async("notificacionesExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onHallazgoRegistrado(HallazgoRegistrado evento) {
        if (notificationService == null || evento == null) {
            return;
        }

        DestinatariosNotificacion dest = politica.resolver(evento.marca());

        Map<String, String> data = new HashMap<>();
        data.put("marca", evento.marca());
        data.put("tipo", "HALLAZGO");
        data.put("enlace", dest.enlace());
        if (evento.marcaId() != null) {
            data.put("marcaId", String.valueOf(evento.marcaId()));
        }
        if (evento.declaracionId() != null) {
            data.put("declaracionId", String.valueOf(evento.declaracionId()));
        }
        if (evento.tipo() != null) {
            data.put("declaracionTipo", evento.tipo());
        }

        String mensaje = String.format("Hallazgo normativo %s registrado para declaración %s #%d",
                evento.marca(),
                evento.tipo() != null ? evento.tipo() : "",
                evento.declaracionId() != null ? evento.declaracionId() : 0L);

        // 1. Notificar al declarante si aplica
        if (dest.notificarDeclarante() && evento.usuarioDeclaranteId() != null) {
            notificationService.sendPushNotificationToUser(evento.usuarioDeclaranteId(), dest.titulo(), mensaje, data);
        }

        // 2. Notificar a fiscalizadores de la región / administradores
        if (dest.notificarFiscalizadores() || dest.notificarAdmins()) {
            notificationService.notificarFiscalizadores(evento.regionId(), dest.titulo(), mensaje, data);
        }
    }

    @Async("notificacionesExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCuotaCerrada(CuotaCerrada evento) {
        if (notificationService == null || evento == null) {
            return;
        }

        Map<String, String> data = new HashMap<>();
        data.put("tipo", "CUOTA_CERRADA");
        data.put("cuotaId", String.valueOf(evento.cuotaId()));
        data.put("enlace", "/cuotas");

        String titulo = "Cierre de Cuota de Extracción";
        String mensaje = String.format("La cuota de extracción #%d ha sido cerrada. Motivo: %s",
                evento.cuotaId(), evento.motivo() != null ? evento.motivo() : "ADMINISTRATIVO");

        notificationService.notificarFiscalizadores(null, titulo, mensaje, data);
    }

    @Async("notificacionesExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCuotaUmbralAlcanzado(CuotaUmbralAlcanzado evento) {
        if (notificationService == null || evento == null) {
            return;
        }

        Map<String, String> data = new HashMap<>();
        data.put("tipo", "CUOTA_UMBRAL");
        data.put("cuotaId", String.valueOf(evento.cuotaId()));
        data.put("enlace", "/cuotas");

        String titulo = "Alerta de Umbral de Cuota Alcanzado";
        String mensaje = String.format("La cuota de extracción #%d ha alcanzado el %.1f%% de consumo asignado",
                evento.cuotaId(), evento.pct() != null ? evento.pct() : 0.0);

        notificationService.notificarFiscalizadores(null, titulo, mensaje, data);

        if (evento.personaId() != null) {
            notificationService.sendPushNotificationToUser(evento.personaId(), titulo, mensaje, data);
        }
    }

    @Async("notificacionesExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCargaRetenida(CargaRetenida evento) {
        if (notificationService == null || evento == null) {
            return;
        }

        Map<String, String> data = new HashMap<>();
        data.put("tipo", "CARGA_RETENIDA");
        data.put("marcaId", String.valueOf(evento.marcaId()));
        data.put("enlace", "/alertas?marca=RETENCION_EXCEDIDA");

        String titulo = "Carga Retenida en Bodega Virtual";
        String mensaje = "Su carga en bodega virtual ha superado el plazo máximo de permanencia y ha sido retenida.";

        if (evento.holderId() != null) {
            notificationService.sendPushNotificationToUser(evento.holderId(), titulo, mensaje, data);
        }

        notificationService.notificarFiscalizadores(null, titulo,
                "Se ha retenido una carga en bodega virtual por RETENCION_EXCEDIDA (hallazgo #" + evento.marcaId() + ")", data);
    }
}
