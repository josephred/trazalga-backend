package com.trazalga.api.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @Autowired
    private com.trazalga.api.services.hallazgos.HallazgoService hallazgoService;

    /**
     * Procesa las marcas resultantes de la validación del servidor (Fachada TA.5):
     * Delega en HallazgoService para persistencia idempotente y emisión de eventos
     * de dominio notificados AFTER_COMMIT.
     */
    public void procesarMarcas(String declaracionTipo, Long declaracionId, Long usuarioDeclaradorId, List<MarcaItem> marcas) {
        if (marcas == null || marcas.isEmpty()) {
            return;
        }

        for (MarcaItem m : marcas) {
            String reglaStr = m.getReglaId() != null ? String.valueOf(m.getReglaId()) : "GENERAL";
            hallazgoService.registrar(
                    declaracionTipo,
                    declaracionId,
                    m.getMarca(),
                    reglaStr,
                    m.getCriterio(),
                    m.getDetalle(),
                    "VALIDACION",
                    usuarioDeclaradorId,
                    null
            );
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
