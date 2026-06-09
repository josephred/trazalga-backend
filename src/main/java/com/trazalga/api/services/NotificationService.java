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

import java.io.InputStream;
import java.util.Date;
import java.util.List;

@Service
public class NotificationService {

    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

    @PostConstruct
    public void initialize() {
        try {
            // Nota: Aquí se debería cargar el service-account.json de Firebase real.
            // Para la prueba/dummy se omite para no romper el inicio si no existe.
            /*
            InputStream serviceAccount = getClass().getClassLoader().getResourceAsStream("firebase-service-account.json");
            if (serviceAccount != null && FirebaseApp.getApps().isEmpty()) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();
                FirebaseApp.initializeApp(options);
                System.out.println("Firebase App initialized.");
            }
            */
            System.out.println("Notification Service Ready.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void saveToken(Long usuarioId, String token, String dispositivo) {
        if (!deviceTokenRepository.existsByToken(token)) {
            DeviceTokenModel deviceToken = DeviceTokenModel.builder()
                    .usuarioId(usuarioId)
                    .token(token)
                    .dispositivo(dispositivo)
                    .createdAt(new Date())
                    .build();
            deviceTokenRepository.save(deviceToken);
        }
    }

    public void removeToken(String token) {
        deviceTokenRepository.deleteByToken(token);
    }

    public void sendPushNotificationToUser(Long usuarioId, String title, String body) {
        List<DeviceTokenModel> tokens = deviceTokenRepository.findByUsuarioId(usuarioId);
        
        for (DeviceTokenModel deviceToken : tokens) {
            try {
                if (FirebaseApp.getApps().isEmpty()) {
                    System.out.println("MOCK FCM PUSH a " + deviceToken.getToken() + ": " + title + " - " + body);
                    continue; // Mock fallback
                }

                Message message = Message.builder()
                        .setToken(deviceToken.getToken())
                        .setNotification(Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build())
                        .build();

                String response = FirebaseMessaging.getInstance().send(message);
                System.out.println("Successfully sent message: " + response);
            } catch (Exception e) {
                System.out.println("Failed to send push: " + e.getMessage());
            }
        }
    }
}
