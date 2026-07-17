package com.trazalga.api.controllers;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trazalga.api.services.EstadoDeclaracionService;
import com.trazalga.api.services.GestionMensajeService;

/**
 * Ciclo de rechazo/re-envío de declaraciones:
 *  - El destinatario (comercializador/planta) puede RECHAZAR una declaración
 *    que aún no consume; deja de aparecer en sus seleccionables.
 *  - El emisor puede corregirla y RE-ENVIARLA: vuelve a NEGOCIACION y
 *    reaparece para el destinatario vigente.
 * Ambas acciones dejan un mensaje automático en la conversación de gestión
 * (registro auditable) y notifican push a la contraparte.
 */
@RestController
@RequestMapping("/api/declaracion-estado")
public class EstadoDeclaracionController {

    @Autowired
    private EstadoDeclaracionService estadoService;

    @Autowired
    private GestionMensajeService gestionMensajeService;

    public static class AccionRequest {
        public Long usuarioId;
        public String motivo;
    }

    @PutMapping("/rechazar/{tipo}/{id}")
    public ResponseEntity<?> rechazar(@PathVariable String tipo, @PathVariable Long id,
                                      @RequestBody AccionRequest req) {
        try {
            if (req == null || req.usuarioId == null) {
                return ResponseEntity.badRequest().body("usuarioId es requerido");
            }
            Map<String, Object> info = estadoService.obtenerInfo(tipo, id);
            if (info == null) {
                return ResponseEntity.notFound().build();
            }
            if (!req.usuarioId.equals(info.get("destinatarioId"))) {
                return ResponseEntity.badRequest().body("Solo el destinatario de la declaración puede rechazarla.");
            }
            if (Boolean.TRUE.equals(info.get("consumida"))) {
                return ResponseEntity.badRequest().body(
                        "La declaración ya fue ingresada en otra declaración. Para rechazarla, primero anula la declaración que la consume.");
            }
            if (EstadoDeclaracionService.RECHAZADA.equals(info.get("estado"))) {
                return ResponseEntity.badRequest().body("La declaración ya está rechazada.");
            }

            // Mensaje automático (queda como registro auditable) + push al emisor
            String motivo = req.motivo != null && !req.motivo.trim().isEmpty()
                    ? req.motivo.trim() : "Sin motivo indicado";
            Long emisorDeclaracionId = (Long) info.get("emisorId");
            gestionMensajeService.enviarMensaje(tipo.toUpperCase(), id,
                    req.usuarioId, emisorDeclaracionId,
                    "❌ Declaración rechazada. Motivo: " + motivo);

            estadoService.cambiarEstado(tipo, id, EstadoDeclaracionService.RECHAZADA);

            return ResponseEntity.ok(Map.of(
                    "estado", EstadoDeclaracionService.RECHAZADA,
                    "message", "Declaración rechazada. El emisor fue notificado con el motivo."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error interno: " + e.getMessage());
        }
    }

    @PutMapping("/reenviar/{tipo}/{id}")
    public ResponseEntity<?> reenviar(@PathVariable String tipo, @PathVariable Long id,
                                      @RequestBody AccionRequest req) {
        try {
            if (req == null || req.usuarioId == null) {
                return ResponseEntity.badRequest().body("usuarioId es requerido");
            }
            Map<String, Object> info = estadoService.obtenerInfo(tipo, id);
            if (info == null) {
                return ResponseEntity.notFound().build();
            }
            if (!req.usuarioId.equals(info.get("emisorId"))) {
                return ResponseEntity.badRequest().body("Solo el emisor de la declaración puede re-enviarla.");
            }
            if (!EstadoDeclaracionService.RECHAZADA.equals(info.get("estado"))) {
                return ResponseEntity.badRequest().body("Solo una declaración rechazada puede re-enviarse.");
            }

            Long destinatarioId = (Long) info.get("destinatarioId");
            if (destinatarioId != null) {
                // Mensaje automático + push al destinatario vigente (puede haber cambiado al editar)
                gestionMensajeService.enviarMensaje(tipo.toUpperCase(), id,
                        req.usuarioId, destinatarioId,
                        "📤 Declaración corregida y re-enviada. Queda disponible nuevamente para su gestión.");
            }

            estadoService.cambiarEstado(tipo, id, EstadoDeclaracionService.NEGOCIACION);

            return ResponseEntity.ok(Map.of(
                    "estado", EstadoDeclaracionService.NEGOCIACION,
                    "message", "Declaración re-enviada. El destinatario fue notificado."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error interno: " + e.getMessage());
        }
    }
}
