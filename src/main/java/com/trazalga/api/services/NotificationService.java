package com.trazalga.api.services;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.trazalga.api.models.DeviceTokenModel;
import com.trazalga.api.repositories.DeviceTokenRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class NotificationService {

    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

    @Value("${trazalga.firebase.config-path}")
    private String firebaseConfigPath;

    @Autowired
    private ResourceLoader resourceLoader;

    @PostConstruct
    public void initialize() {
        try {
            Resource resource = resourceLoader.getResource(firebaseConfigPath);
            if (resource.exists()) {
                try (InputStream serviceAccount = resource.getInputStream()) {
                    if (FirebaseApp.getApps().isEmpty()) {
                        FirebaseOptions options = FirebaseOptions.builder()
                                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                                .build();
                        FirebaseApp.initializeApp(options);
                        System.out.println("Firebase App initialized from: " + firebaseConfigPath);
                    }
                }
            } else {
                System.out.println("WARNING: Firebase config resource not found: " + firebaseConfigPath);
            }
            System.out.println("Notification Service Ready.");
        } catch (Exception e) {
            System.out.println("ERROR: Failed to initialize Firebase.");
            e.printStackTrace();
        }
    }

    public void saveToken(Long usuarioId, String token, String dispositivo) {
        // El token FCM identifica al DISPOSITIVO. Si ya existe registrado a otro
        // usuario (ej: alguien más inició sesión antes en este mismo teléfono),
        // se reasigna: las notificaciones deben llegar al usuario con sesión activa.
        DeviceTokenModel existente = deviceTokenRepository.findByToken(token).orElse(null);
        if (existente != null) {
            if (!usuarioId.equals(existente.getUsuarioId())) {
                existente.setUsuarioId(usuarioId);
                existente.setCreatedAt(new Date());
                deviceTokenRepository.save(existente);
            }
            return;
        }
        DeviceTokenModel deviceToken = DeviceTokenModel.builder()
                .usuarioId(usuarioId)
                .token(token)
                .dispositivo(dispositivo)
                .createdAt(new Date())
                .build();
        deviceTokenRepository.save(deviceToken);
    }

    // @Transactional es obligatorio: los delete derivados de Spring Data
    // fallan con TransactionRequiredException sin una transacción activa.
    @org.springframework.transaction.annotation.Transactional
    public void removeToken(String token) {
        deviceTokenRepository.deleteByToken(token);
    }

    public void sendPushNotificationToUser(Long usuarioId, String title, String body) {
        sendPushNotificationToUser(usuarioId, title, body, null);
    }

    /**
     * Envía push notification con datos de navegación adicionales.
     * El data map se incluye en el payload FCM para que el frontend pueda
     * reaccionar según el tipo de notificación (ej: abrir modal de gestión).
     */
    public void sendPushNotificationToUser(Long usuarioId, String title, String body, Map<String, String> data) {
        // Incluir siempre el destinatario en el payload: la app lo usa para descartar
        // notificaciones dirigidas a un usuario distinto al de la sesión activa
        // (posible con tokens antiguos en dispositivos compartidos).
        Map<String, String> dataConReceptor = new java.util.HashMap<>();
        if (data != null) {
            dataConReceptor.putAll(data);
        }
        dataConReceptor.put("receptorId", String.valueOf(usuarioId));
        data = dataConReceptor;

        List<DeviceTokenModel> tokens = deviceTokenRepository.findByUsuarioId(usuarioId);
        
        for (DeviceTokenModel deviceToken : tokens) {
            try {
                if (FirebaseApp.getApps().isEmpty()) {
                    System.out.println("MOCK FCM PUSH a " + deviceToken.getToken() + ": " + title + " - " + body
                            + (data != null ? " data=" + data : ""));
                    continue; // Mock fallback
                }

                Message.Builder messageBuilder = Message.builder()
                        .setToken(deviceToken.getToken())
                        .setNotification(Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build());

                // Agregar datos de navegación al payload
                if (data != null && !data.isEmpty()) {
                    messageBuilder.putAllData(data);
                }

                String response = FirebaseMessaging.getInstance().send(messageBuilder.build());
                System.out.println("Successfully sent message: " + response);
            } catch (Exception e) {
                System.out.println("Failed to send push: " + e.getMessage());
            }
        }
    }
}
