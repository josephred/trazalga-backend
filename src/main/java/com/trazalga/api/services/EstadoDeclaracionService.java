package com.trazalga.api.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

/**
 * Transiciones de estado del ciclo de negociación de una declaración
 * (ENVIADA -> NEGOCIACION -> RECHAZADA -> NEGOCIACION), operando por
 * tipo/id sobre las tablas consumibles, mismo patrón TIPO:id del resto
 * del sistema (gestion_mensaje, seleccionDeclaraciones.js).
 */
@Service
public class EstadoDeclaracionService {

    public static final String ENVIADA = "ENVIADA";
    public static final String NEGOCIACION = "NEGOCIACION";
    public static final String RECHAZADA = "RECHAZADA";

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Tablas cuyas declaraciones pueden negociarse/rechazarse.
     *
     * Requisito para agregar un tipo aquí: su tabla debe tener la columna `estado`
     * (además de usuario_id, usuario_destinatario_id, declaracion_destinatario_id y
     * folio_origen, que usa obtenerInfo). Hoy declaracion_planta_produccion y
     * declaracion_planta_destino NO la tienen, por eso quedan fuera: incluirlas
     * provocaría un "Unknown column 'estado'" en vez del no-op actual.
     */
    private static String tabla(String tipo) {
        switch (tipo == null ? "" : tipo.toUpperCase()) {
            case "RECOLECTOR": return "declaracion_recolector";
            case "ARMADOR": return "declaracion_armador";
            case "AREA": return "declaracion_area";
            case "COMERCIALIZADOR": return "declaracion_comercializador";
            case "PLANTA_ABASTECIMIENTO": return "declaracion_planta_abastecimiento";
            default: throw new IllegalArgumentException("Tipo de declaración inválido: " + tipo);
        }
    }

    /**
     * Datos mínimos de la declaración para validar transiciones:
     * emisorId (dueño), destinatarioId, consumida, estado y folio.
     * Retorna null si la declaración no existe.
     */
    public Map<String, Object> obtenerInfo(String tipo, Long id) {
        String sql = "SELECT usuario_id, usuario_destinatario_id, declaracion_destinatario_id, estado, folio_origen "
                + "FROM " + tabla(tipo) + " WHERE id = :id";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("id", id);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        if (rows.isEmpty()) {
            return null;
        }
        Object[] row = rows.get(0);
        Map<String, Object> info = new HashMap<>();
        info.put("emisorId", row[0] != null ? ((Number) row[0]).longValue() : null);
        info.put("destinatarioId", row[1] != null ? ((Number) row[1]).longValue() : null);
        info.put("consumida", row[2] != null);
        info.put("estado", row[3] != null ? row[3].toString() : ENVIADA);
        info.put("folio", row[4] != null ? row[4].toString() : null);
        return info;
    }

    @Transactional
    public void cambiarEstado(String tipo, Long id, String nuevoEstado) {
        String sql = "UPDATE " + tabla(tipo) + " SET estado = :estado WHERE id = :id";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("estado", nuevoEstado);
        query.setParameter("id", id);
        query.executeUpdate();
    }

    /** ENVIADA -> NEGOCIACION al abrirse conversación; otros estados no cambian. */
    @Transactional
    public void marcarEnNegociacionSiEnviada(String tipo, Long id) {
        try {
            String sql = "UPDATE " + tabla(tipo) + " SET estado = :negociacion "
                    + "WHERE id = :id AND (estado IS NULL OR estado = :enviada)";
            Query query = entityManager.createNativeQuery(sql);
            query.setParameter("negociacion", NEGOCIACION);
            query.setParameter("enviada", ENVIADA);
            query.setParameter("id", id);
            query.executeUpdate();
        } catch (IllegalArgumentException e) {
            // Mensajes sobre tipos no negociables (planta producción/destino, sin columna
            // `estado`): la conversación se guarda igual, solo no hay estado que cambiar.
        }
    }
}
