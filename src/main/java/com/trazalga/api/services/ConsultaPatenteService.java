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

    @Transactional(readOnly = true)
    public List<Map<String, Object>> obtenerPatentesCamionesComerciantes() {
        int vigenciaHoras = configService.getInt("patente_vigencia_horas", 48);

        String sql = """
            SELECT 
                sub.patente_norm,
                sub.placa_patente_carro,
                sub.vehiculo_transporte,
                sub.comerciante,
                sub.rut_comerciante,
                sub.chofer_transporte,
                sub.rut_chofer,
                sub.fecha_mov,
                sub.hora,
                sub.especie,
                sub.kg,
                sub.comuna_origen,
                sub.destino,
                sub.estado,
                sub.folio,
                sub.total_movs
            FROM (
                SELECT 
                    UPPER(REPLACE(REPLACE(COALESCE(dc.placa_patente, dc.patente), ' ', ''), '-', '')) AS patente_norm,
                    dc.placa_patente_carro,
                    dc.vehiculo_transporte,
                    COALESCE(dc.nombre_comercializador, CONCAT(COALESCE(u.nombres, ''), ' ', COALESCE(u.apellidop, ''))) AS comerciante,
                    u.rut AS rut_comerciante,
                    dc.chofer_transporte,
                    dc.rut_chofer,
                    COALESCE(dc.fecha_traslado, dc.fecha_declaracion) AS fecha_mov,
                    dc.hora,
                    COALESCE(e.nombre, 'Alga') AS especie,
                    dc.cantidad AS kg,
                    COALESCE(c.nombre, '') AS comuna_origen,
                    COALESCE(dc.nombre_destinatario, ud.nombres, 'Planta') AS destino,
                    dc.estado,
                    COALESCE(dc.folio_origen, CONCAT('DC-', dc.id)) AS folio,
                    COUNT(*) OVER (PARTITION BY UPPER(REPLACE(REPLACE(COALESCE(dc.placa_patente, dc.patente), ' ', ''), '-', ''))) AS total_movs,
                    ROW_NUMBER() OVER (
                        PARTITION BY UPPER(REPLACE(REPLACE(COALESCE(dc.placa_patente, dc.patente), ' ', ''), '-', '')) 
                        ORDER BY COALESCE(dc.fecha_traslado, dc.fecha_declaracion) DESC, dc.hora DESC, dc.id DESC
                    ) AS rn
                FROM declaracion_comercializador dc
                LEFT JOIN usuario u ON dc.usuario_id = u.id
                LEFT JOIN comuna c ON u.comuna_id = c.id
                LEFT JOIN usuario ud ON dc.usuario_destinatario_id = ud.id
                LEFT JOIN especie e ON dc.especie_id = e.id
                WHERE (dc.placa_patente IS NOT NULL AND TRIM(dc.placa_patente) != '')
                   OR (dc.patente IS NOT NULL AND TRIM(dc.patente) != '')
            ) sub
            WHERE sub.rn = 1
            ORDER BY sub.fecha_mov DESC, sub.hora DESC
        """;

        Query query = entityManager.createNativeQuery(sql);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        List<Map<String, Object>> resultado = new ArrayList<>();
        LocalDateTime ahora = LocalDateTime.now();

        for (Object[] row : rows) {
            String patenteNorm = (String) row[0];
            String patenteCarro = row[1] != null ? normalizarPatente(row[1].toString()) : null;
            String vehiculoRaw = row[2] != null ? row[2].toString().trim() : null;
            String comerciante = row[3] != null ? row[3].toString().trim() : "Comerciante";
            String rutComerciante = row[4] != null ? row[4].toString().trim() : null;
            String chofer = row[5] != null ? row[5].toString().trim() : "Chofer no informado";
            String rutChofer = row[6] != null ? row[6].toString().trim() : null;
            Date fechaDate = (Date) row[7];
            String horaStr = row[8] != null ? row[8].toString().trim() : null;
            String especie = (String) row[9];
            Number kgNum = (Number) row[10];
            String comunaOrigen = row[11] != null && !row[11].toString().isBlank() ? row[11].toString().trim() : "Origen acreditado";
            String destino = row[12] != null ? sanitizarDestino(row[12].toString()) : "Planta autorizada";
            String estado = (String) row[13];
            String folio = (String) row[14];
            Number totalMovsNum = (Number) row[15];

            LocalTime localTime = parsearHora(horaStr);
            LocalDate localDate = toLocalDate(fechaDate);
            LocalDateTime fechaHoraMovimiento = localDate != null ? LocalDateTime.of(localDate, localTime) : null;

            double horasTranscurridas = 9999.0;
            boolean vigente = false;
            if (fechaHoraMovimiento != null) {
                long diffSeconds = ChronoUnit.SECONDS.between(fechaHoraMovimiento, ahora);
                horasTranscurridas = Math.max(0.0, Math.round((diffSeconds / 3600.0) * 10.0) / 10.0);
                vigente = (horasTranscurridas <= vigenciaHoras);
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("patente", patenteNorm);
            item.put("patenteCarro", patenteCarro);
            item.put("vehiculo", formatearVehiculo(vehiculoRaw));
            item.put("vehiculoCodigo", vehiculoRaw);
            item.put("comerciante", comerciante);
            item.put("rutComerciante", rutComerciante);
            item.put("chofer", chofer);
            item.put("rutChofer", rutChofer);
            item.put("fechaHoraMovimiento", fechaHoraMovimiento != null ? fechaHoraMovimiento.toString() : null);
            item.put("hora", horaStr);
            item.put("especie", especie);
            item.put("kg", kgNum != null ? Math.round(kgNum.doubleValue() * 10.0) / 10.0 : 0.0);
            item.put("comunaOrigen", comunaOrigen);
            item.put("destino", destino);
            item.put("estado", estado != null ? estado : "ENVIADA");
            item.put("folio", folio);
            item.put("totalMovimientos", totalMovsNum != null ? totalMovsNum.intValue() : 1);
            item.put("horasTranscurridas", horasTranscurridas);
            item.put("vigente", vigente);

            resultado.add(item);
        }

        return resultado;
    }

    public LocalTime parsearHora(String horaStr) {
        if (horaStr == null || horaStr.isBlank()) {
            return LocalTime.MIDNIGHT;
        }
        try {
            if (horaStr.length() == 5) {
                return LocalTime.parse(horaStr);
            } else if (horaStr.length() >= 8) {
                return LocalTime.parse(horaStr.substring(0, 8));
            }
        } catch (Exception ignored) {}
        return LocalTime.MIDNIGHT;
    }

    public LocalDate toLocalDate(Date fechaDate) {
        if (fechaDate == null) return null;
        if (fechaDate instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        } else if (fechaDate instanceof java.sql.Timestamp sqlTs) {
            return sqlTs.toLocalDateTime().toLocalDate();
        } else {
            return fechaDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
    }

    public String formatearVehiculo(String vehiculoRaw) {
        if (vehiculoRaw == null || vehiculoRaw.isBlank()) {
            return "Camión";
        }
        return switch (vehiculoRaw.toLowerCase().trim()) {
            case "camion_sin_acoplado" -> "Camión sin acoplado";
            case "camion_con_acoplado" -> "Camión con acoplado";
            case "camioneta" -> "Camioneta";
            case "furgon" -> "Furgón";
            case "carro_arrastre" -> "Carro de arrastre";
            default -> vehiculoRaw.replace("_", " ");
        };
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
