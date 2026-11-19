package com.trazalga.api.services.declaraciones;

import com.trazalga.api.ApiApplication;
import com.trazalga.api.services.geo.CoordenadaNormalizer;
import com.trazalga.api.services.geo.ReferenciaOrigenService;
import com.trazalga.api.services.geo.ReferenciaOrigenService.EstadoCalceVaradero;
import com.trazalga.api.services.geo.ReferenciaOrigenService.ResultadoCalceVaradero;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/**
 * YR.1 / YR.2 · Vinculación masiva histórica de {@code declaracion_recolector.varadero_id}
 * a partir del texto {@code varadero} y la comuna, usando exactamente la misma regla
 * {@link ReferenciaOrigenService#calzarVaradero(String, Long)} aplicada al guardar.
 * <p>
 * Reglas de seguridad:
 * <ul>
 *   <li>Sólo evalúa declaraciones con {@code varadero_id IS NULL} y texto {@code varadero} no vacío.</li>
 *   <li>En simulación ({@code aplicar=false}) no escribe ni crea tablas.</li>
 *   <li>Al aplicar ({@code aplicar=true}): respalda {@code (id, varadero_id)} en
 *       {@code zz_respaldo_recolector_varadero_20261119}, escribe únicamente calces únicos
 *       en lotes de 500 y registra la corrida (cantidad y fecha) en
 *       {@code zz_recolector_varadero_vinculacion_log}.</li>
 *   <li>Nunca liga textos ambiguos ni reevalúa ni crea marcas en {@code declaracion_marca}.</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VincularVaraderoRecolectorService {

    public static final String TABLA_RESPALDO = "zz_respaldo_recolector_varadero_20261119";
    public static final String TABLA_LOG = "zz_recolector_varadero_vinculacion_log";
    public static final int TAMANO_LOTE = 500;

    private final ReferenciaOrigenService referenciaOrigenService;

    @PersistenceContext
    private EntityManager entityManager;

    private record ParVinculacion(Long declaracionId, Long varaderoId) {
    }

    private static final class AcumTextoAmbiguo {
        String texto;
        Long comunaId;
        String comuna;
        int candidatos;
        long frecuencia;
    }

    private static final class AcumTextoSinCalce {
        String texto;
        String textoNormalizado;
        long frecuencia;
        final Set<String> comunas = new LinkedHashSet<>();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> simular(String usuario) {
        return ejecutar(false, usuario);
    }

    @Transactional(readOnly = false)
    public Map<String, Object> aplicar(String usuario) {
        return ejecutar(true, usuario);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> ejecutar(boolean aplicar, String usuarioRaw) {
        String usuario = (usuarioRaw != null && !usuarioRaw.isBlank()) ? usuarioRaw.trim() : "ADMIN";
        LocalDateTime ahora = LocalDateTime.now(ApiApplication.ZONA_CHILE);

        // Asegurar caché fresca de varaderos por comuna
        referenciaOrigenService.invalidarCache();

        List<Object[]> filas = entityManager.createNativeQuery(
                "SELECT dr.id, dr.varadero, COALESCE(dr.comuna_id, cal.comuna_id) AS comuna_ef, com.nombre AS comuna_nom "
                        + "FROM declaracion_recolector dr "
                        + "LEFT JOIN caleta cal ON dr.caleta_id = cal.id "
                        + "LEFT JOIN comuna com ON com.id = COALESCE(dr.comuna_id, cal.comuna_id) "
                        + "WHERE dr.varadero_id IS NULL "
                        + "  AND dr.varadero IS NOT NULL "
                        + "  AND TRIM(dr.varadero) <> '' "
                        + "ORDER BY dr.id")
                .getResultList();

        List<ParVinculacion> unicos = new ArrayList<>();
        Map<String, AcumTextoAmbiguo> ambiguosMap = new LinkedHashMap<>();
        Map<String, AcumTextoSinCalce> sinCalceMap = new LinkedHashMap<>();

        long totalUnicos = 0L;
        long totalAmbiguos = 0L;
        long totalSinCalce = 0L;

        for (Object[] r : filas) {
            Long declId = ((Number) r[0]).longValue();
            String textoRaw = r[1] != null ? r[1].toString().trim() : "";
            Long comunaId = r[2] != null ? ((Number) r[2]).longValue() : null;
            String comunaNombre = r[3] != null ? r[3].toString() : null;

            ResultadoCalceVaradero res = referenciaOrigenService.evaluarCalceVaradero(textoRaw, comunaId);
            if (res.estado() == EstadoCalceVaradero.UNICO && res.varadero() != null && res.varadero().getId() != null) {
                totalUnicos++;
                unicos.add(new ParVinculacion(declId, res.varadero().getId()));
            } else if (res.estado() == EstadoCalceVaradero.AMBIGUO) {
                totalAmbiguos++;
                String clave = textoRaw + "|" + String.valueOf(comunaId);
                AcumTextoAmbiguo acc = ambiguosMap.computeIfAbsent(clave, k -> {
                    AcumTextoAmbiguo a = new AcumTextoAmbiguo();
                    a.texto = textoRaw;
                    a.comunaId = comunaId;
                    a.comuna = comunaNombre;
                    a.candidatos = res.candidatos();
                    return a;
                });
                acc.frecuencia++;
            } else {
                totalSinCalce++;
                String norm = CoordenadaNormalizer.normalizarTexto(textoRaw);
                String clave = textoRaw + "|" + norm;
                AcumTextoSinCalce acc = sinCalceMap.computeIfAbsent(clave, k -> {
                    AcumTextoSinCalce a = new AcumTextoSinCalce();
                    a.texto = textoRaw;
                    a.textoNormalizado = norm;
                    return a;
                });
                acc.frecuencia++;
                String etiquetaComuna = comunaId != null
                        ? comunaId + ":" + (comunaNombre != null ? comunaNombre : "?")
                        : "SIN_COMUNA";
                acc.comunas.add(etiquetaComuna);
            }
        }

        long actualizadas = 0L;
        int lotesProcesados = 0;

        if (aplicar) {
            asegurarTablasRespaldoYLog();
            Timestamp ts = Timestamp.valueOf(ahora);

            for (int inicio = 0; inicio < unicos.size(); inicio += TAMANO_LOTE) {
                int fin = Math.min(inicio + TAMANO_LOTE, unicos.size());
                List<ParVinculacion> lote = unicos.subList(inicio, fin);
                for (ParVinculacion par : lote) {
                    respaldarSiNoExiste(par.declaracionId(), ts);
                    int updated = entityManager.createNativeQuery(
                            "UPDATE declaracion_recolector SET varadero_id = :varaderoId "
                                    + "WHERE id = :id AND varadero_id IS NULL")
                            .setParameter("varaderoId", par.varaderoId())
                            .setParameter("id", par.declaracionId())
                            .executeUpdate();
                    actualizadas += updated;
                }
                entityManager.flush();
                lotesProcesados++;
            }

            registrarEjecucionLog(ts, usuario, filas.size(), totalUnicos, actualizadas, totalAmbiguos, totalSinCalce);
            log.info("YR.2 aplicado por {}: {} declaraciones de recolector ligadas a varadero_id en {} lote(s) de hasta {}.",
                    usuario, actualizadas, lotesProcesados, TAMANO_LOTE);
        }

        List<Map<String, Object>> ambiguosLista = ambiguosMap.values().stream()
                .sorted(Comparator.comparingLong((AcumTextoAmbiguo a) -> a.frecuencia).reversed()
                        .thenComparing(a -> a.texto))
                .map(a -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("texto", a.texto);
                    m.put("comunaId", a.comunaId);
                    m.put("comuna", a.comuna);
                    m.put("candidatos", a.candidatos);
                    m.put("frecuencia", a.frecuencia);
                    return m;
                })
                .toList();

        List<Map<String, Object>> sinCalceLista = sinCalceMap.values().stream()
                .sorted(Comparator.comparingLong((AcumTextoSinCalce a) -> a.frecuencia).reversed()
                        .thenComparing(a -> a.texto))
                .map(a -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("texto", a.texto);
                    m.put("textoNormalizado", a.textoNormalizado);
                    m.put("frecuencia", a.frecuencia);
                    m.put("comunas", new ArrayList<>(a.comunas));
                    return m;
                })
                .toList();

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("aplicado", aplicar);
        resp.put("ejecutadoEn", ahora.toString());
        resp.put("candidatasEvaluadas", filas.size());
        resp.put("calceUnico", totalUnicos);
        resp.put("ambiguo", totalAmbiguos);
        resp.put("sinCalce", totalSinCalce);
        resp.put("actualizadas", actualizadas);
        resp.put("lotesProcesados", lotesProcesados);
        resp.put("tamanoLote", TAMANO_LOTE);
        resp.put("respaldoTabla", TABLA_RESPALDO);
        resp.put("logTabla", TABLA_LOG);
        resp.put("ambiguos", ambiguosLista);
        resp.put("sinCalceDetalle", sinCalceLista);
        return resp;
    }

    private void asegurarTablasRespaldoYLog() {
        entityManager.createNativeQuery(
                "CREATE TABLE IF NOT EXISTS " + TABLA_RESPALDO + " ("
                        + "id BIGINT NOT NULL PRIMARY KEY, "
                        + "varadero_id BIGINT NULL, "
                        + "respaldado_en DATETIME NOT NULL)")
                .executeUpdate();

        entityManager.createNativeQuery(
                "CREATE TABLE IF NOT EXISTS " + TABLA_LOG + " ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                        + "ejecutado_en DATETIME NOT NULL, "
                        + "ejecutado_por VARCHAR(100) NOT NULL, "
                        + "candidatas_evaluadas BIGINT NOT NULL, "
                        + "calces_unicos BIGINT NOT NULL, "
                        + "actualizadas BIGINT NOT NULL, "
                        + "ambiguas BIGINT NOT NULL, "
                        + "sin_calce BIGINT NOT NULL)")
                .executeUpdate();
    }

    private void respaldarSiNoExiste(Long declaracionId, Timestamp ahora) {
        Number count = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM " + TABLA_RESPALDO + " WHERE id = :id")
                .setParameter("id", declaracionId)
                .getSingleResult();
        if (count != null && count.longValue() > 0) {
            return;
        }
        entityManager.createNativeQuery(
                "INSERT INTO " + TABLA_RESPALDO + " (id, varadero_id, respaldado_en) VALUES (:id, NULL, :ahora)")
                .setParameter("id", declaracionId)
                .setParameter("ahora", ahora)
                .executeUpdate();
    }

    private void registrarEjecucionLog(
            Timestamp ahora,
            String usuario,
            long candidatas,
            long unicos,
            long actualizadas,
            long ambiguas,
            long sinCalce) {
        String u = usuario.length() > 100 ? usuario.substring(0, 100) : usuario;
        entityManager.createNativeQuery(
                "INSERT INTO " + TABLA_LOG
                        + " (ejecutado_en, ejecutado_por, candidatas_evaluadas, calces_unicos, actualizadas, ambiguas, sin_calce) "
                        + "VALUES (:ahora, :usuario, :cand, :unicos, :act, :amb, :sin)")
                .setParameter("ahora", ahora)
                .setParameter("usuario", u)
                .setParameter("cand", candidatas)
                .setParameter("unicos", unicos)
                .setParameter("act", actualizadas)
                .setParameter("amb", ambiguas)
                .setParameter("sin", sinCalce)
                .executeUpdate();
    }
}
