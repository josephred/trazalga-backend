package com.trazalga.api.services.geo;

import com.trazalga.api.ApiApplication;
import com.trazalga.api.models.CaletaModel;
import com.trazalga.api.models.CoordenadaCambioLogModel;
import com.trazalga.api.models.VaraderoModel;
import com.trazalga.api.repositories.ICaletaRepository;
import com.trazalga.api.repositories.ICoordenadaCambioLogRepository;
import com.trazalga.api.repositories.IVaraderoRepository;
import com.trazalga.api.services.ReportService;
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
 * YV.2 / YV.4 · Diagnóstico, simulación y corrección controlada de coordenadas
 * fuera de Chile en el maestro {@code varadero} (y en {@code caleta} heredadas
 * desde varadero sin coordenada oficial de planilla Sernapesca).
 * <p>
 * Al aplicar ({@code aplicar=true}):
 * <ul>
 *   <li>respalda cada fila original en {@code zz_respaldo_varadero_20261119};</li>
 *   <li>escribe sólo las coordenadas que quedan dentro de {@link CoordenadaNormalizer#estaEnRangoChile};</li>
 *   <li>registra cada cambio en {@code coordenada_cambio_log};</li>
 *   <li>invalida la caché de {@link ReferenciaOrigenService};</li>
 *   <li>no modifica ni elimina marcas de {@code declaracion_marca}.</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CorregirCoordenadasVaraderosService {

    public static final String TABLA_RESPALDO = "zz_respaldo_varadero_20261119";
    public static final String REGLA_YA_VALIDA = "SIN_CAMBIO_YA_VALIDA";
    public static final String REGLA_DESPLAZAMIENTO_DMS = "CORRECCION_DESPLAZAMIENTO_DMS_20261119";
    public static final String REGLA_SIGNO = "CORRECCION_SIGNO_20261119";
    public static final String REGLA_DMS = "CORRECCION_DMS_20261119";
    public static final String REGLA_NO_CORREGIBLE = "NO_CORREGIBLE";
    public static final String FUENTE_CALETA_HEREDADA = "CORRECCION_HEREDADA_VARADERO_20261119";

    private static final double TOLERANCIA_HERENCIA_GRADOS = 1e-6;
    private static final double UMBRAL_DIFF_LON_DMS_M = 50.0;

    private final IVaraderoRepository varaderoRepository;
    private final ICaletaRepository caletaRepository;
    private final ICoordenadaCambioLogRepository cambioLogRepository;
    private final ReferenciaOrigenService referenciaOrigenService;

    @PersistenceContext
    private EntityManager entityManager;

    public record PropuestaVaradero(
            Long id,
            String nombre,
            Double latActual,
            Double lonActual,
            Double latPropuesta,
            Double lonPropuesta,
            Integer longGraPropuesto,
            Integer longMinPropuesto,
            Double longSegPropuesto,
            String regla,
            boolean dentroDeChile,
            boolean requiereCambio,
            Double diferenciaDmsMetros) {
    }

    /**
     * Regla pura de diagnóstico y propuesta por varadero (YV.1 / YV.2).
     */
    public static PropuestaVaradero evaluarVaradero(VaraderoModel v) {
        Long id = v.getId();
        String nombre = v.getNombre();
        Double latAct = v.getLatitud();
        Double lonAct = v.getLongitud();

        // 0. Idempotencia: si ya está dentro de Chile, no tocar
        if (CoordenadaNormalizer.estaEnRangoChile(latAct, lonAct)) {
            Double diffDms = calcularDiferenciaDmsNormalMetros(latAct, lonAct, v);
            return new PropuestaVaradero(
                    id, nombre, latAct, lonAct, latAct, lonAct,
                    null, null, null,
                    REGLA_YA_VALIDA, true, false, diffDms);
        }

        // 1. Regla principal YV.1: desplazamiento de 4 columnas (latitud=long_gra, long_gra=long_min, long_min=long_seg, long_seg=latitud real)
        if (tienePatronDesplazamientoColumnas(v)) {
            double dmsLat = -(Math.abs(v.getLatGra()) + Math.abs(v.getLatMin()) / 60.0 + Math.abs(v.getLatSeg()) / 3600.0);
            double dmsLonReord = -(Math.abs(latAct) + Math.abs(v.getLongGra()) / 60.0 + Math.abs(v.getLongMin()) / 3600.0);
            double latProp = redondear6(dmsLat);
            double lonProp = redondear6(lonAct);

            double diffLonM = ReportService.haversineKm(latProp, lonProp, latProp, dmsLonReord) * 1000.0;
            if (diffLonM > UMBRAL_DIFF_LON_DMS_M && CoordenadaNormalizer.estaEnRangoChile(latProp, dmsLonReord)) {
                lonProp = redondear6(dmsLonReord);
            }

            if (CoordenadaNormalizer.estaEnRangoChile(latProp, lonProp)) {
                double diffTotalM = redondear3(ReportService.haversineKm(latProp, lonProp, dmsLat, dmsLonReord) * 1000.0);
                return new PropuestaVaradero(
                        id, nombre, latAct, lonAct, latProp, lonProp,
                        (int) Math.round(Math.abs(latAct)),
                        Math.abs(v.getLongGra()),
                        (double) Math.abs(v.getLongMin()),
                        REGLA_DESPLAZAMIENTO_DMS, true, true, diffTotalM);
            }
        }

        // 2. Respaldo secundario: signo positivo sin desplazamiento de columnas
        if (latAct != null && lonAct != null && Double.isFinite(latAct) && Double.isFinite(lonAct)) {
            double latNeg = redondear6(-Math.abs(latAct));
            double lonNeg = redondear6(-Math.abs(lonAct));
            if (CoordenadaNormalizer.estaEnRangoChile(latNeg, lonNeg)) {
                Double diffDms = calcularDiferenciaDmsNormalMetros(latNeg, lonNeg, v);
                return new PropuestaVaradero(
                        id, nombre, latAct, lonAct, latNeg, lonNeg,
                        null, null, null,
                        REGLA_SIGNO, true, true, diffDms);
            }
        }

        // 3. Respaldo terciario: recálculo directo desde DMS cuando están en orden normal
        if (tieneDmsOrdenNormalValido(v)) {
            double dmsLat = redondear6(-(Math.abs(v.getLatGra()) + Math.abs(v.getLatMin()) / 60.0 + Math.abs(v.getLatSeg()) / 3600.0));
            double dmsLon = redondear6(-(Math.abs(v.getLongGra()) + Math.abs(v.getLongMin()) / 60.0 + Math.abs(v.getLongSeg()) / 3600.0));
            if (CoordenadaNormalizer.estaEnRangoChile(dmsLat, dmsLon)) {
                return new PropuestaVaradero(
                        id, nombre, latAct, lonAct, dmsLat, dmsLon,
                        null, null, null,
                        REGLA_DMS, true, true, 0.0);
            }
        }

        // 4. Ninguna regla deja el punto dentro de Chile: no se toca
        return new PropuestaVaradero(
                id, nombre, latAct, lonAct, null, null,
                null, null, null,
                REGLA_NO_CORREGIBLE, false, false, null);
    }

    public static boolean esFuentePlanillaOficialOManual(String fuente) {
        if (fuente == null || fuente.isBlank()) {
            return false;
        }
        String f = fuente.trim().toUpperCase(Locale.ROOT);
        return f.contains("SERNAPESCA")
                || f.contains("PLANILLA")
                || f.contains("QG")
                || f.contains("MANUAL")
                || f.contains("CALETAS_IVREGION")
                || f.contains("CSV");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> simular(String usuario) {
        return ejecutar(false, usuario);
    }

    @Transactional(readOnly = false)
    public Map<String, Object> aplicar(String usuario) {
        return ejecutar(true, usuario);
    }

    private Map<String, Object> ejecutar(boolean aplicar, String usuarioRaw) {
        String usuario = (usuarioRaw != null && !usuarioRaw.isBlank()) ? usuarioRaw.trim() : "ADMIN";
        LocalDateTime ahora = LocalDateTime.now(ApiApplication.ZONA_CHILE);

        List<VaraderoModel> varaderos = varaderoRepository.findAll();
        varaderos.sort(Comparator.comparing(VaraderoModel::getId, Comparator.nullsLast(Long::compareTo)));

        List<Map<String, Object>> detalleVaraderos = new ArrayList<>();
        List<Map<String, Object>> noCorregibles = new ArrayList<>();
        Map<String, Long> resumenPorRegla = new LinkedHashMap<>();

        // Guardamos coordenadas originales y destino por varaderoId para evaluar caletas heredadas (YV.4)
        Map<Long, double[]> varaderoOriginal = new HashMap<>();
        Map<Long, double[]> varaderoDestino = new HashMap<>();

        long yaValidos = 0L;
        long corregibles = 0L;
        long actualizados = 0L;

        if (aplicar) {
            asegurarTablaRespaldo();
        }

        for (VaraderoModel v : varaderos) {
            if (v.getId() != null && v.getLatitud() != null && v.getLongitud() != null) {
                varaderoOriginal.put(v.getId(), new double[]{v.getLatitud(), v.getLongitud()});
            }

            PropuestaVaradero p = evaluarVaradero(v);
            resumenPorRegla.merge(p.regla(), 1L, Long::sum);

            Map<String, Object> item = construirItemVaradero(p);
            detalleVaraderos.add(item);

            if (p.dentroDeChile() && p.latPropuesta() != null && p.lonPropuesta() != null && v.getId() != null) {
                varaderoDestino.put(v.getId(), new double[]{p.latPropuesta(), p.lonPropuesta()});
            }

            if (REGLA_YA_VALIDA.equals(p.regla())) {
                yaValidos++;
            } else if (p.requiereCambio() && p.dentroDeChile()) {
                corregibles++;
                if (aplicar) {
                    respaldarVaradero(v, ahora);

                    Double latAnt = v.getLatitud();
                    Double lonAnt = v.getLongitud();
                    String fuenteAnt = v.getFuenteCoordenada();

                    v.setLatitud(p.latPropuesta());
                    v.setLongitud(p.lonPropuesta());
                    if (p.longGraPropuesto() != null) {
                        v.setLongGra(p.longGraPropuesto());
                    }
                    if (p.longMinPropuesto() != null) {
                        v.setLongMin(p.longMinPropuesto());
                    }
                    if (p.longSegPropuesto() != null) {
                        v.setLongSeg(p.longSegPropuesto());
                    }
                    v.setFuenteCoordenada(p.regla());
                    v.setCoordenadaActualizadaEn(ahora);
                    v.setCoordenadaActualizadaPor(usuario);
                    varaderoRepository.save(v);

                    guardarLogCambio("VARADERO", v.getId(), v.getNombre(),
                            latAnt, lonAnt, fuenteAnt,
                            p.latPropuesta(), p.lonPropuesta(), p.regla(),
                            ahora, usuario);
                    actualizados++;
                }
            } else {
                noCorregibles.add(item);
            }
        }

        // YV.4: Caletas que heredaron coordenadas inválidas desde su varadero (nunca tocar planilla de Sergio / QG)
        List<CaletaModel> caletas = caletaRepository.findAll();
        caletas.sort(Comparator.comparing(CaletaModel::getId, Comparator.nullsLast(Long::compareTo)));

        List<Map<String, Object>> detalleCaletasHeredadas = new ArrayList<>();
        long caletasProtegidasPlanilla = 0L;
        long caletasHeredadasCorregibles = 0L;
        long caletasHeredadasActualizadas = 0L;

        for (CaletaModel cal : caletas) {
            if (esFuentePlanillaOficialOManual(cal.getFuenteCoordenada())) {
                caletasProtegidasPlanilla++;
                continue;
            }
            if (cal.getVaradero() == null || cal.getVaradero().getId() == null) {
                continue;
            }
            if (CoordenadaNormalizer.estaEnRangoChile(cal.getLatitud(), cal.getLongitud())) {
                continue;
            }
            Long vId = cal.getVaradero().getId();
            double[] origVar = varaderoOriginal.get(vId);
            double[] destVar = varaderoDestino.get(vId);
            if (origVar == null || destVar == null || cal.getLatitud() == null || cal.getLongitud() == null) {
                continue;
            }
            boolean heredoDelVaradero = Math.abs(cal.getLatitud() - origVar[0]) < TOLERANCIA_HERENCIA_GRADOS
                    && Math.abs(cal.getLongitud() - origVar[1]) < TOLERANCIA_HERENCIA_GRADOS;
            if (!heredoDelVaradero || !CoordenadaNormalizer.estaEnRangoChile(destVar[0], destVar[1])) {
                continue;
            }

            caletasHeredadasCorregibles++;
            Map<String, Object> itemCal = new LinkedHashMap<>();
            itemCal.put("id", cal.getId());
            itemCal.put("nombre", cal.getNombre());
            itemCal.put("varaderoId", vId);
            itemCal.put("latActual", cal.getLatitud());
            itemCal.put("lonActual", cal.getLongitud());
            itemCal.put("latPropuesta", destVar[0]);
            itemCal.put("lonPropuesta", destVar[1]);
            itemCal.put("regla", FUENTE_CALETA_HEREDADA);
            itemCal.put("dentroDeChile", true);
            detalleCaletasHeredadas.add(itemCal);

            if (aplicar) {
                respaldarCaleta(cal, ahora);

                Double latAnt = cal.getLatitud();
                Double lonAnt = cal.getLongitud();
                String fuenteAnt = cal.getFuenteCoordenada();

                cal.setLatitud(destVar[0]);
                cal.setLongitud(destVar[1]);
                cal.setFuenteCoordenada(FUENTE_CALETA_HEREDADA);
                cal.setCoordenadaActualizadaEn(ahora);
                cal.setCoordenadaActualizadaPor(usuario);
                caletaRepository.save(cal);

                guardarLogCambio("CALETA", cal.getId(), cal.getNombre(),
                        latAnt, lonAnt, fuenteAnt,
                        destVar[0], destVar[1], FUENTE_CALETA_HEREDADA,
                        ahora, usuario);
                caletasHeredadasActualizadas++;
            }
        }

        if (aplicar && (actualizados > 0 || caletasHeredadasActualizadas > 0)) {
            entityManager.flush();
            referenciaOrigenService.invalidarCache();
            log.info("YV.2 aplicado por {}: {} varaderos y {} caletas heredadas corregidas; caché invalidada.",
                    usuario, actualizados, caletasHeredadasActualizadas);
        }

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("aplicado", aplicar);
        respuesta.put("totalVaraderos", varaderos.size());
        respuesta.put("yaValidos", yaValidos);
        respuesta.put("corregibles", corregibles);
        respuesta.put("actualizados", actualizados);
        respuesta.put("noCorregiblesCount", noCorregibles.size());
        respuesta.put("resumenPorRegla", resumenPorRegla);
        respuesta.put("caletasHeredadasCorregibles", caletasHeredadasCorregibles);
        respuesta.put("caletasHeredadasActualizadas", caletasHeredadasActualizadas);
        respuesta.put("caletasProtegidasPlanilla", caletasProtegidasPlanilla);
        respuesta.put("respaldoTabla", TABLA_RESPALDO);
        respuesta.put("varaderos", detalleVaraderos);
        respuesta.put("noCorregibles", noCorregibles);
        respuesta.put("caletasHeredadas", detalleCaletasHeredadas);
        return respuesta;
    }

    private void asegurarTablaRespaldo() {
        entityManager.createNativeQuery(
                "CREATE TABLE IF NOT EXISTS " + TABLA_RESPALDO + " ("
                        + "entidad_tipo VARCHAR(20) NOT NULL, "
                        + "entidad_id BIGINT NOT NULL, "
                        + "nombre VARCHAR(255) NULL, "
                        + "comuna_id BIGINT NULL, "
                        + "lat_gra INT NULL, "
                        + "lat_min INT NULL, "
                        + "lat_seg DOUBLE NULL, "
                        + "long_gra INT NULL, "
                        + "long_min INT NULL, "
                        + "long_seg DOUBLE NULL, "
                        + "latitud DOUBLE NULL, "
                        + "longitud DOUBLE NULL, "
                        + "fuente_coordenada VARCHAR(100) NULL, "
                        + "respaldado_en DATETIME NOT NULL, "
                        + "PRIMARY KEY (entidad_tipo, entidad_id))")
                .executeUpdate();
    }

    private void respaldarVaradero(VaraderoModel v, LocalDateTime ahora) {
        if (yaRespaldado("VARADERO", v.getId())) {
            return;
        }
        entityManager.createNativeQuery(
                "INSERT INTO " + TABLA_RESPALDO + " "
                        + "(entidad_tipo, entidad_id, nombre, comuna_id, lat_gra, lat_min, lat_seg, "
                        + "long_gra, long_min, long_seg, latitud, longitud, fuente_coordenada, respaldado_en) "
                        + "VALUES (:tipo, :id, :nombre, :comunaId, :latGra, :latMin, :latSeg, "
                        + ":longGra, :longMin, :longSeg, :lat, :lon, :fuente, :ahora)")
                .setParameter("tipo", "VARADERO")
                .setParameter("id", v.getId())
                .setParameter("nombre", truncar(v.getNombre(), 255))
                .setParameter("comunaId", v.getComuna() != null ? v.getComuna().getId() : null)
                .setParameter("latGra", v.getLatGra())
                .setParameter("latMin", v.getLatMin())
                .setParameter("latSeg", v.getLatSeg())
                .setParameter("longGra", v.getLongGra())
                .setParameter("longMin", v.getLongMin())
                .setParameter("longSeg", v.getLongSeg())
                .setParameter("lat", v.getLatitud())
                .setParameter("lon", v.getLongitud())
                .setParameter("fuente", truncar(v.getFuenteCoordenada(), 100))
                .setParameter("ahora", Timestamp.valueOf(ahora))
                .executeUpdate();
    }

    private void respaldarCaleta(CaletaModel c, LocalDateTime ahora) {
        if (yaRespaldado("CALETA", c.getId())) {
            return;
        }
        entityManager.createNativeQuery(
                "INSERT INTO " + TABLA_RESPALDO + " "
                        + "(entidad_tipo, entidad_id, nombre, comuna_id, lat_gra, lat_min, lat_seg, "
                        + "long_gra, long_min, long_seg, latitud, longitud, fuente_coordenada, respaldado_en) "
                        + "VALUES (:tipo, :id, :nombre, :comunaId, NULL, NULL, NULL, "
                        + "NULL, NULL, NULL, :lat, :lon, :fuente, :ahora)")
                .setParameter("tipo", "CALETA")
                .setParameter("id", c.getId())
                .setParameter("nombre", truncar(c.getNombre(), 255))
                .setParameter("comunaId", c.getComuna() != null ? c.getComuna().getId() : null)
                .setParameter("lat", c.getLatitud())
                .setParameter("lon", c.getLongitud())
                .setParameter("fuente", truncar(c.getFuenteCoordenada(), 100))
                .setParameter("ahora", Timestamp.valueOf(ahora))
                .executeUpdate();
    }

    private boolean yaRespaldado(String tipo, Long id) {
        Number count = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM " + TABLA_RESPALDO + " WHERE entidad_tipo = :tipo AND entidad_id = :id")
                .setParameter("tipo", tipo)
                .setParameter("id", id)
                .getSingleResult();
        return count != null && count.longValue() > 0;
    }

    private void guardarLogCambio(
            String entidadTipo,
            Long entidadId,
            String entidadNombre,
            Double latAnt,
            Double lonAnt,
            String fuenteAnt,
            Double latNueva,
            Double lonNueva,
            String fuenteNueva,
            LocalDateTime ahora,
            String usuario) {
        cambioLogRepository.save(CoordenadaCambioLogModel.builder()
                .entidadTipo(entidadTipo)
                .entidadId(entidadId)
                .entidadNombre(truncar(entidadNombre, 150))
                .latitudAnterior(latAnt)
                .longitudAnterior(lonAnt)
                .fuenteAnterior(truncar(fuenteAnt, 100))
                .latitudNueva(latNueva)
                .longitudNueva(lonNueva)
                .fuenteNueva(truncar(fuenteNueva, 100))
                .actualizadoEn(ahora)
                .actualizadoPor(truncar(usuario, 100))
                .build());
    }

    private static Map<String, Object> construirItemVaradero(PropuestaVaradero p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.id());
        m.put("nombre", p.nombre());
        m.put("latActual", p.latActual());
        m.put("lonActual", p.lonActual());
        m.put("latPropuesta", p.latPropuesta());
        m.put("lonPropuesta", p.lonPropuesta());
        m.put("regla", p.regla());
        m.put("dentroDeChile", p.dentroDeChile());
        m.put("requiereCambio", p.requiereCambio());
        m.put("diferenciaDmsMetros", p.diferenciaDmsMetros());
        return m;
    }

    private static boolean tienePatronDesplazamientoColumnas(VaraderoModel v) {
        if (v.getLatGra() == null || v.getLatMin() == null || v.getLatSeg() == null
                || v.getLongGra() == null || v.getLongMin() == null
                || v.getLatitud() == null || v.getLongitud() == null) {
            return false;
        }
        if (!Double.isFinite(v.getLatitud()) || !Double.isFinite(v.getLongitud()) || !Double.isFinite(v.getLatSeg())) {
            return false;
        }
        // latitud contiene los grados enteros de longitud (66..76)
        double absLatCol = Math.abs(v.getLatitud());
        if (Math.abs(absLatCol - Math.rint(absLatCol)) > 1e-6 || absLatCol < 66.0 || absLatCol > 76.0) {
            return false;
        }
        // longitud ya contiene la longitud decimal negativa de Chile
        if (v.getLongitud() < CoordenadaNormalizer.LON_MIN || v.getLongitud() > CoordenadaNormalizer.LON_MAX) {
            return false;
        }
        int latG = Math.abs(v.getLatGra());
        int latM = Math.abs(v.getLatMin());
        double latS = Math.abs(v.getLatSeg());
        int lonM = Math.abs(v.getLongGra());
        int lonS = Math.abs(v.getLongMin());
        return latG >= 17 && latG <= 56
                && latM < 60 && latS <= 60.0
                && lonM < 60 && lonS <= 60;
    }

    private static boolean tieneDmsOrdenNormalValido(VaraderoModel v) {
        if (v.getLatGra() == null || v.getLatMin() == null || v.getLatSeg() == null
                || v.getLongGra() == null || v.getLongMin() == null || v.getLongSeg() == null) {
            return false;
        }
        if (!Double.isFinite(v.getLatSeg()) || !Double.isFinite(v.getLongSeg())) {
            return false;
        }
        int latG = Math.abs(v.getLatGra());
        int latM = Math.abs(v.getLatMin());
        double latS = Math.abs(v.getLatSeg());
        int lonG = Math.abs(v.getLongGra());
        int lonM = Math.abs(v.getLongMin());
        double lonS = Math.abs(v.getLongSeg());
        return latG >= 17 && latG <= 56
                && lonG >= 66 && lonG <= 76
                && latM < 60 && latS <= 60.0
                && lonM < 60 && lonS <= 60.0;
    }

    private static Double calcularDiferenciaDmsNormalMetros(Double lat, Double lon, VaraderoModel v) {
        if (lat == null || lon == null || !tieneDmsOrdenNormalValido(v)) {
            return null;
        }
        double dmsLat = -(Math.abs(v.getLatGra()) + Math.abs(v.getLatMin()) / 60.0 + Math.abs(v.getLatSeg()) / 3600.0);
        double dmsLon = -(Math.abs(v.getLongGra()) + Math.abs(v.getLongMin()) / 60.0 + Math.abs(v.getLongSeg()) / 3600.0);
        return redondear3(ReportService.haversineKm(lat, lon, dmsLat, dmsLon) * 1000.0);
    }

    private static double redondear6(double v) {
        return Math.round(v * 1_000_000.0) / 1_000_000.0;
    }

    private static double redondear3(double v) {
        return Math.round(v * 1_000.0) / 1_000.0;
    }

    private static String truncar(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
