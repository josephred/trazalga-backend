package com.trazalga.api.services;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.dto.ResultadoValidacion.MarcaItem;
import com.trazalga.api.models.ConfiguracionAlertaModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IUsuarioRepository;

@Service
public class AlertaTriggerService {

    @Autowired
    private ConfiguracionAlertaService configuracionService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private DeclaracionMarcaService declaracionMarcaService;

    @Autowired
    private IUsuarioRepository usuarioRepository;

    /**
     * Procesa las marcas resultantes de la validación del servidor:
     * 1. Las persiste en declaracion_marca para auditoría y reportes.
     * 2. Despacha notificaciones push al declarante y administradores según la configuración.
     */
    public void procesarMarcas(String declaracionTipo, Long declaracionId, Long usuarioDeclaradorId, List<MarcaItem> marcas) {
        if (marcas == null || marcas.isEmpty() || declaracionId == null) {
            return;
        }

        for (MarcaItem m : marcas) {
            // 1. Persistir marca
            declaracionMarcaService.marcar(
                    declaracionTipo,
                    declaracionId,
                    m.getMarca(),
                    m.getDetalle(),
                    m.getReglaId());

            // 2. Notificar según tipo de marca
            switch (m.getMarca()) {
                case "EN_VEDA" -> {
                    Optional<ConfiguracionAlertaModel> cfg = configuracionService.getByTipo("EXTRACCION_VEDA");
                    if (cfg.isPresent() && Boolean.TRUE.equals(cfg.get().getActivo())) {
                        notificarAlerta(usuarioDeclaradorId, "¡Alerta de Veda!", m.getDetalle());
                    }
                }
                case "CUOTA_EXCEDIDA" -> {
                    Optional<ConfiguracionAlertaModel> cfg = configuracionService.getByTipo("LIMITE_CUOTA");
                    if (cfg.isPresent() && Boolean.TRUE.equals(cfg.get().getActivo())) {
                        notificarAlerta(usuarioDeclaradorId, "Alerta de Cuota Superada", m.getDetalle());
                    }
                }
                case "POSTERIOR_CIERRE" -> {
                    notificarAlerta(usuarioDeclaradorId, "Declaración Post-Cierre de Cuota", m.getDetalle());
                }
                case "LED_EXCEDIDO" -> {
                    notificarAlerta(usuarioDeclaradorId, "Alerta Límite Diario (LED) Superado", m.getDetalle());
                }
                case "DESEMBARQUE_ATIPICO" -> {
                    notificarSoloAdmins("Aviso de Desembarque Atípico", m.getDetalle());
                }
            }
        }
    }

    /**
     * Compatibilidad hacia atrás.
     */
    public void evaluarDeclaracion(Long especieId, Long usuarioId, Long regionId, Double volumen, String perfilAplicable) {
        // En Fase 1 la evaluación rigurosa ocurre en ValidacionDeclaracionService.validar(...) antes de persistir.
    }

    private void notificarAlerta(Long usuarioDeclaradorId, String titulo, String mensaje) {
        if (usuarioDeclaradorId != null) {
            notificationService.sendPushNotificationToUser(usuarioDeclaradorId, titulo, mensaje);
        }
        notificarSoloAdmins(titulo, mensaje);
    }

    private void notificarSoloAdmins(String titulo, String mensaje) {
        List<UsuarioModel> admins = usuarioRepository.findByPerfilId(1L); // Perfil 1 = Administrador
        if (admins != null) {
            for (UsuarioModel admin : admins) {
                notificationService.sendPushNotificationToUser(admin.getId(), titulo, mensaje);
            }
        }
    }
}
