package com.trazalga.api.services.hallazgos;

import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Política de notificación para hallazgos de fiscalización (TA.5).
 * Determina qué actores deben ser notificados y qué enlace de navegación corresponde
 * según el tipo de marca/infracción.
 */
@Component
public class PoliticaNotificacionHallazgo {

    public record DestinatariosNotificacion(
            boolean notificarDeclarante,
            boolean notificarTitularCarga,
            boolean notificarFiscalizadores,
            boolean notificarAdmins,
            String titulo,
            String enlace
    ) {
    }

    private static final Map<String, DestinatariosNotificacion> POLITICAS = Map.of(
            "EN_VEDA", new DestinatariosNotificacion(
                    true, false, true, true,
                    "¡Alerta de Veda Biológica!",
                    "/alertas?marca=EN_VEDA"
            ),
            "LED_EXCEDIDO", new DestinatariosNotificacion(
                    true, false, true, true,
                    "Alerta Límite Diario (LED) Superado",
                    "/alertas?marca=LED_EXCEDIDO"
            ),
            "CUOTA_EXCEDIDA", new DestinatariosNotificacion(
                    true, false, true, true,
                    "Alerta de Cuota Superada",
                    "/alertas?marca=CUOTA_EXCEDIDA"
            ),
            "POSTERIOR_CIERRE", new DestinatariosNotificacion(
                    true, false, true, true,
                    "Declaración Post-Cierre de Cuota",
                    "/alertas?marca=POSTERIOR_CIERRE"
            ),
            "DECLARACION_EXTEMPORANEA", new DestinatariosNotificacion(
                    true, false, true, true,
                    "Declaración Extemporánea de Cuota",
                    "/alertas?marca=DECLARACION_EXTEMPORANEA"
            ),
            "DESEMBARQUE_ATIPICO", new DestinatariosNotificacion(
                    false, false, false, true,
                    "Aviso de Desembarque Atípico",
                    "/alertas?marca=DESEMBARQUE_ATIPICO"
            ),
            "RETENCION_EXCEDIDA", new DestinatariosNotificacion(
                    false, true, true, true,
                    "Carga Retenida en Bodega Virtual",
                    "/alertas?marca=RETENCION_EXCEDIDA"
            )
    );

    private static final DestinatariosNotificacion DEFAULT_POLITICA = new DestinatariosNotificacion(
            true, false, true, true,
            "Alerta de Hallazgo Normativo",
            "/alertas"
    );

    /**
     * Resuelve los destinatarios y títulos para la marca dada.
     */
    public DestinatariosNotificacion resolver(String marca) {
        if (marca == null) {
            return DEFAULT_POLITICA;
        }
        DestinatariosNotificacion pol = POLITICAS.get(marca.trim().toUpperCase());
        if (pol != null) {
            return pol;
        }
        return new DestinatariosNotificacion(
                true, false, true, true,
                "Alerta de Hallazgo: " + marca,
                enlacePara(marca)
        );
    }

    /**
     * Construye el enlace normativo para la consola de hallazgos.
     */
    public String enlacePara(String marca) {
        if (marca == null || marca.isBlank()) {
            return "/alertas";
        }
        return "/alertas?marca=" + marca.trim().toUpperCase();
    }
}
