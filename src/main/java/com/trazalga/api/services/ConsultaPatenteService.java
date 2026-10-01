package com.trazalga.api.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trazalga.api.models.ConsultaPatenteLogModel;
import com.trazalga.api.repositories.IConsultaPatenteLogRepository;

@Service
public class ConsultaPatenteService {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private ConfiguracionGeneralService configService;

    @Autowired
    private IConsultaPatenteLogRepository logRepository;

    public static String normalizarPatente(String patente) {
        if (patente == null) return "";
        return patente.toUpperCase().replaceAll("[\\s-]", "").trim();
    }

    @Transactional
    public Map<String, Object> consultar(String patenteInput, HttpServletRequest request) {
        String patente = normalizarPatente(patenteInput);
        int vigenciaHoras = configService.getInt("patente_vigencia_horas", 48);

        Map<String, Object> response = new LinkedHashMap<>();

        if (patente.isEmpty()) {
            response.put("patente", "");
            response.put("vigente", false);
            response.put("sinRegistros", true);
            registrarLog("", request, "SIN_REGISTROS");
            return response;
        }

        // Consulta del último movimiento autorizado donde la patente aparezca como camión o carro.
        // Excluye explícitamente declaraciones con estado = 'RECHAZADA'.
        // Si fecha_traslado es nula, retrocede a fecha_declaracion o fecha_ingreso_planta.
        String sql = """
            SELECT fecha_mov, hora, tipo, folio, especie_nombre, kg, comuna_nombre, destino_nombre
            FROM (
                SELECT COALESCE(dc.fecha_traslado, dc.fecha_declaracion) as fecha_mov, 
                       dc.hora, 
                       'COMERCIALIZADOR_A_PLANTA' as tipo,
                       COALESCE(dc.folio_origen, CONCAT('DC-', dc.id)) as folio, 
                       COALESCE(e.nombre, 'Alga') as especie_nombre,
                       dc.cantidad as kg,
                       COALESCE(c.nombre, '') as comuna_nombre,
                       COALESCE(dc.nombre_destinatario, ud.nombres, 'Planta / Destinatario') as destino_nombre
                FROM declaracion_comercializador dc
                LEFT JOIN especie e ON dc.especie_id = e.id
                LEFT JOIN usuario u ON dc.usuario_id = u.id
                LEFT JOIN comuna c ON u.comuna_id = c.id
                LEFT JOIN usuario ud ON dc.usuario_destinatario_id = ud.id
                WHERE (UPPER(REPLACE(REPLACE(COALESCE(dc.placa_patente, ''), ' ', ''), '-', '')) = :patente
                    OR UPPER(REPLACE(REPLACE(COALESCE(dc.placa_patente_carro, ''), ' ', ''), '-', '')) = :patente
                    OR UPPER(REPLACE(REPLACE(COALESCE(dc.patente, ''), ' ', ''), '-', '')) = :patente)
                AND (dc.estado IS NULL OR dc.estado != 'RECHAZADA')

                UNION ALL

                SELECT COALESCE(dpa.fecha_traslado, dpa.fecha_ingreso_planta) as fecha_mov, 
                       dpa.hora, 
                       'RECEPCION_EN_PLANTA' as tipo,
                       COALESCE(dpa.folio_origen, dpa.folio_declaracion_a_pla, CONCAT('DPA-', dpa.id)) as folio, 
                       COALESCE(e.nombre, 'Alga') as especie_nombre,
                       dpa.cantidad as kg,
                       COALESCE(c.nombre, '') as comuna_nombre,
                       COALESCE(dpa.nombre_planta, ud.nombres, 'Planta') as destino_nombre
                FROM declaracion_planta_abastecimiento dpa
                LEFT JOIN especie e ON dpa.especie_id = e.id
                LEFT JOIN usuario ud ON dpa.usuario_destinatario_id = ud.id
                LEFT JOIN comuna c ON ud.comuna_id = c.id
                WHERE (UPPER(REPLACE(REPLACE(COALESCE(dpa.placa_patente, ''), ' ', ''), '-', '')) = :patente
                    OR UPPER(REPLACE(REPLACE(COALESCE(dpa.placa_patente_carro, ''), ' ', ''), '-', '')) = :patente
                    OR UPPER(REPLACE(REPLACE(COALESCE(dpa.patente, ''), ' ', ''), '-', '')) = :patente)
                AND (dpa.estado IS NULL OR dpa.estado != 'RECHAZADA')
            ) AS movimientos
            WHERE fecha_mov IS NOT NULL
            ORDER BY fecha_mov DESC, hora DESC
        """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("patente", patente);
        query.setMaxResults(1);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        if (rows.isEmpty()) {
            response.put("patente", patente);
            response.put("vigente", false);
            response.put("sinRegistros", true);
            registrarLog(patente, request, "SIN_REGISTROS");
            return response;
        }

        Object[] row = rows.get(0);
        Date fechaDate = (Date) row[0];
        String horaStr = row[1] != null ? row[1].toString().trim() : null;
        String tipoMovimiento = (String) row[2];
        String folio = (String) row[3];
        String especie = (String) row[4];
        Number kgNum = (Number) row[5];
        String comunaOrigen = (String) row[6];
        String destino = (String) row[7];

        boolean horaInformada = true;
        LocalTime localTime;
        if (horaStr == null || horaStr.isBlank()) {
            localTime = LocalTime.MIDNIGHT; // Interpretación conservadora: inicio del día
            horaInformada = false;
        } else {
            try {
                if (horaStr.length() == 5) {
                    localTime = LocalTime.parse(horaStr);
                } else if (horaStr.length() >= 8) {
                    localTime = LocalTime.parse(horaStr.substring(0, 8));
                } else {
                    localTime = LocalTime.MIDNIGHT;
                    horaInformada = false;
                }
            } catch (Exception e) {
                localTime = LocalTime.MIDNIGHT;
                horaInformada = false;
            }
        }

        LocalDate localDate;
        if (fechaDate instanceof java.sql.Date sqlDate) {
            localDate = sqlDate.toLocalDate();
        } else if (fechaDate instanceof java.sql.Timestamp sqlTs) {
            localDate = sqlTs.toLocalDateTime().toLocalDate();
        } else {
            localDate = fechaDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        LocalDateTime fechaHoraMovimiento = LocalDateTime.of(localDate, localTime);
        LocalDateTime ahora = LocalDateTime.now();

        long diffSeconds = ChronoUnit.SECONDS.between(fechaHoraMovimiento, ahora);
        double horasTranscurridas = Math.max(0.0, Math.round((diffSeconds / 3600.0) * 10.0) / 10.0);
        boolean vigente = (horasTranscurridas <= vigenciaHoras);

        // ⚠️ SEGURIDAD Y PRIVACIDAD DE DATOS (T10.2):
        // La respuesta ciudadana NO debe contener RUT, ni nombre del chofer, ni nombre personal del titular
        response.put("patente", patente);
        response.put("vigente", vigente);
        response.put("horasTranscurridas", horasTranscurridas);
        response.put("fechaHoraMovimiento", fechaHoraMovimiento.toString());
        response.put("tipoMovimiento", tipoMovimiento);
        response.put("especie", especie);
        response.put("kg", kgNum != null ? Math.round(kgNum.doubleValue() * 10.0) / 10.0 : 0.0);
        response.put("comunaOrigen", comunaOrigen != null && !comunaOrigen.isBlank() ? comunaOrigen : "Origen acreditado");
        response.put("destino", sanitizarDestino(destino));
        response.put("folio", folio);
        response.put("horaInformada", horaInformada);

        String resultado = vigente ? "VIGENTE" : "NO_VIGENTE";
        registrarLog(patente, request, resultado);

        return response;
    }

    private String sanitizarDestino(String destino) {
        if (destino == null || destino.isBlank()) return "Planta de destino autorizada";
        return destino.replaceAll("\\b\\d{1,2}\\.\\d{3}\\.\\d{3}[-][0-9kK]\\b", "").trim();
    }

    private void registrarLog(String patente, HttpServletRequest request, String resultado) {
        try {
            String ip = getClientIp(request);
            String ipHash = hashIp(ip);
            ConsultaPatenteLogModel log = ConsultaPatenteLogModel.builder()
                    .patente(patente)
                    .ipHash(ipHash)
                    .fecha(new Date())
                    .resultado(resultado)
                    .build();
            logRepository.save(log);
        } catch (Exception e) {
            // Silencioso para no abortar la consulta pública si falla el log
            System.err.println("Advertencia al registrar log de consulta patente: " + e.getMessage());
        }
    }

    public String getClientIp(HttpServletRequest request) {
        if (request == null) return "127.0.0.1";
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
    }

    public String hashIp(String ip) {
        if (ip == null || ip.isBlank()) return "ANONYMOUS";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(ip.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "HASH_ERROR";
        }
    }
}
