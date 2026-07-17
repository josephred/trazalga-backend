package com.trazalga.api.services;

import com.trazalga.api.models.GestionMensajeModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IGestionMensajeRepository;
import com.trazalga.api.repositories.IUsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class GestionMensajeService {

    @Autowired
    private IGestionMensajeRepository mensajeRepository;

    @Autowired
    private IUsuarioRepository usuarioRepository;

    @Autowired
    private NotificationService notificationService;

    /**
     * Envía un nuevo mensaje de gestión y notifica al receptor vía push notification.
     */
    public GestionMensajeModel enviarMensaje(String declaracionTipo, Long declaracionId,
                                              Long emisorId, Long receptorId, String mensaje) {
        Optional<UsuarioModel> emisorOpt = usuarioRepository.findById(emisorId);
        Optional<UsuarioModel> receptorOpt = usuarioRepository.findById(receptorId);

        if (emisorOpt.isEmpty()) {
            throw new RuntimeException("Emisor no encontrado con ID: " + emisorId);
        }
        if (receptorOpt.isEmpty()) {
            throw new RuntimeException("Receptor no encontrado con ID: " + receptorId);
        }

        UsuarioModel emisor = emisorOpt.get();
        UsuarioModel receptor = receptorOpt.get();

        GestionMensajeModel nuevoMensaje = GestionMensajeModel.builder()
                .declaracionTipo(declaracionTipo)
                .declaracionId(declaracionId)
                .emisor(emisor)
                .receptor(receptor)
                .mensaje(mensaje)
                .fechaEnvio(new Date())
                .leido(false)
                .build();

        GestionMensajeModel saved = mensajeRepository.save(nuevoMensaje);

        // Enviar push notification al receptor con datos de navegación
        String nombreEmisor = (emisor.getNombres() + " " + (emisor.getApellidop() != null ? emisor.getApellidop() : "")).trim();
        String titulo = "Gestión de Declaración";
        String cuerpo = nombreEmisor + ": " + (mensaje.length() > 80 ? mensaje.substring(0, 80) + "..." : mensaje);

        Map<String, String> data = new HashMap<>();
        data.put("type", "GESTION");
        data.put("declaracionTipo", declaracionTipo);
        data.put("declaracionId", String.valueOf(declaracionId));
        data.put("emisorId", String.valueOf(emisorId));
        // Perfil del receptor: el frontend lo necesita para navegar a la vista correcta
        // (el dueño de la declaración ve su historial; el destinatario, sus asignadas)
        if (receptor.getPerfil() != null) {
            data.put("receptorPerfilId", String.valueOf(receptor.getPerfil().getId()));
        }

        notificationService.sendPushNotificationToUser(receptorId, titulo, cuerpo, data);

        return saved;
    }

    /**
     * Obtiene el historial completo de la conversación de una declaración.
     */
    public List<GestionMensajeModel> obtenerConversacion(String declaracionTipo, Long declaracionId) {
        return mensajeRepository.findByDeclaracionTipoAndDeclaracionIdOrderByFechaEnvioAsc(declaracionTipo, declaracionId);
    }

    /**
     * Marca como leídos todos los mensajes no leídos de una conversación para un receptor.
     */
    public void marcarLeidos(String declaracionTipo, Long declaracionId, Long usuarioId) {
        List<GestionMensajeModel> noLeidos = mensajeRepository
                .findByDeclaracionTipoAndDeclaracionIdAndReceptorIdAndLeidoFalse(declaracionTipo, declaracionId, usuarioId);
        for (GestionMensajeModel m : noLeidos) {
            m.setLeido(true);
        }
        mensajeRepository.saveAll(noLeidos);
    }

    /**
     * Retorna la cantidad de mensajes no leídos para un usuario.
     */
    public long contarNoLeidos(Long usuarioId) {
        return mensajeRepository.countByReceptorIdAndLeidoFalse(usuarioId);
    }

    /**
     * Retorna la bandeja de gestiones pendientes (mensajes no leídos) para un usuario.
     */
    public List<GestionMensajeModel> obtenerPendientes(Long usuarioId) {
        return mensajeRepository.findByReceptorIdAndLeidoFalseOrderByFechaEnvioDesc(usuarioId);
    }

    /**
     * Notifica al destinatario que una declaración ha sido modificada.
     * Se llama desde los servicios de declaración al hacer update.
     */
    public void notificarModificacion(String declaracionTipo, Long declaracionId,
                                       Long modificadorId, Long destinatarioId, String folio) {
        Optional<UsuarioModel> modificadorOpt = usuarioRepository.findById(modificadorId);
        String nombreModificador = modificadorOpt
                .map(u -> (u.getNombres() + " " + (u.getApellidop() != null ? u.getApellidop() : "")).trim())
                .orElse("Un usuario");

        // Crear un mensaje automático en la conversación
        Optional<UsuarioModel> destinatarioOpt = destinatarioId != null
                ? usuarioRepository.findById(destinatarioId) : Optional.empty();
        if (modificadorOpt.isPresent() && destinatarioOpt.isPresent()) {
            GestionMensajeModel mensajeAuto = GestionMensajeModel.builder()
                    .declaracionTipo(declaracionTipo)
                    .declaracionId(declaracionId)
                    .emisor(modificadorOpt.get())
                    .receptor(destinatarioOpt.get())
                    .mensaje("📝 Declaración modificada automáticamente por " + nombreModificador)
                    .fechaEnvio(new Date())
                    .leido(false)
                    .build();
            mensajeRepository.save(mensajeAuto);
        }

        // Enviar push notification
        String titulo = "Declaración Modificada";
        String cuerpo = nombreModificador + " ha modificado la declaración " + (folio != null ? folio : "#" + declaracionId);

        Map<String, String> data = new HashMap<>();
        data.put("type", "GESTION_MODIFICADA");
        data.put("declaracionTipo", declaracionTipo);
        data.put("declaracionId", String.valueOf(declaracionId));
        // Perfil del receptor para que el frontend navegue a la vista correcta
        destinatarioOpt.ifPresent(d -> {
            if (d.getPerfil() != null) {
                data.put("receptorPerfilId", String.valueOf(d.getPerfil().getId()));
            }
        });

        notificationService.sendPushNotificationToUser(destinatarioId, titulo, cuerpo, data);
    }
}
