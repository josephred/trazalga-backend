package com.trazalga.api.controllers;

import com.trazalga.api.services.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribeToNotifications(@RequestBody Map<String, String> body) {
        // En un caso real, obtendrías el usuario_id del token JWT (SecurityContext)
        // Para simplificar, usaremos un mock ID 1 o el que venga en el body
        String token = body.get("token");
        String usuarioIdStr = body.get("usuarioId");
        Long usuarioId = usuarioIdStr != null ? Long.parseLong(usuarioIdStr) : 1L;

        if (token != null && !token.isEmpty()) {
            notificationService.saveToken(usuarioId, token, "Web/PWA");
            return ResponseEntity.ok().body(Map.of("message", "Suscrito exitosamente"));
        }
        return ResponseEntity.badRequest().body("Token es requerido");
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<?> unsubscribe(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token != null) {
            notificationService.removeToken(token);
        }
        return ResponseEntity.ok().body(Map.of("message", "Desuscrito"));
    }
    
    // Endpoint para probar notificaciones manualmente
    @PostMapping("/test-push")
    public ResponseEntity<?> testPush(@RequestBody Map<String, String> body) {
        Long usuarioId = body.containsKey("usuarioId") ? Long.parseLong(body.get("usuarioId")) : 1L;
        String title = body.getOrDefault("title", "Alerta Trazalga");
        String message = body.getOrDefault("message", "Esta es una notificación de prueba");
        
        notificationService.sendPushNotificationToUser(usuarioId, title, message);
        return ResponseEntity.ok().body(Map.of("message", "Push trigger sent"));
    }
}
