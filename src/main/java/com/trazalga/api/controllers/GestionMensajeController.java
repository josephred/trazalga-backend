package com.trazalga.api.controllers;

import com.trazalga.api.models.GestionMensajeModel;
import com.trazalga.api.services.GestionMensajeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gestion-mensaje")
public class GestionMensajeController {

    @Autowired
    private GestionMensajeService gestionMensajeService;

    /**
     * Enviar un nuevo mensaje de gestión.
     * Body: { declaracionTipo, declaracionId, emisorId, receptorId, mensaje }
     */
    @PostMapping
    public ResponseEntity<?> enviarMensaje(@RequestBody Map<String, Object> body) {
        try {
            String declaracionTipo = (String) body.get("declaracionTipo");
            Long declaracionId = Long.valueOf(body.get("declaracionId").toString());
            Long emisorId = Long.valueOf(body.get("emisorId").toString());
            Long receptorId = Long.valueOf(body.get("receptorId").toString());
            String mensaje = (String) body.get("mensaje");

            if (declaracionTipo == null || mensaje == null || mensaje.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("declaracionTipo y mensaje son obligatorios");
            }

            GestionMensajeModel saved = gestionMensajeService.enviarMensaje(
                    declaracionTipo, declaracionId, emisorId, receptorId, mensaje.trim());
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Obtener la conversación completa de una declaración.
     */
    @GetMapping("/declaracion/{tipo}/{id}")
    public ResponseEntity<List<GestionMensajeModel>> obtenerConversacion(
            @PathVariable String tipo, @PathVariable Long id) {
        List<GestionMensajeModel> mensajes = gestionMensajeService.obtenerConversacion(tipo, id);
        return ResponseEntity.ok(mensajes);
    }

    /**
     * Marcar como leídos los mensajes de una conversación para un usuario.
     */
    @PutMapping("/leidos/{tipo}/{id}/{usuarioId}")
    public ResponseEntity<?> marcarLeidos(
            @PathVariable String tipo, @PathVariable Long id, @PathVariable Long usuarioId) {
        gestionMensajeService.marcarLeidos(tipo, id, usuarioId);
        return ResponseEntity.ok(Map.of("message", "Mensajes marcados como leídos"));
    }

    /**
     * Contar mensajes no leídos para un usuario.
     */
    @GetMapping("/no-leidos/{usuarioId}")
    public ResponseEntity<Map<String, Long>> contarNoLeidos(@PathVariable Long usuarioId) {
        long count = gestionMensajeService.contarNoLeidos(usuarioId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * Bandeja de gestiones pendientes (mensajes no leídos) para un usuario.
     */
    @GetMapping("/pendientes/{usuarioId}")
    public ResponseEntity<List<GestionMensajeModel>> obtenerPendientes(@PathVariable Long usuarioId) {
        List<GestionMensajeModel> pendientes = gestionMensajeService.obtenerPendientes(usuarioId);
        return ResponseEntity.ok(pendientes);
    }
}
