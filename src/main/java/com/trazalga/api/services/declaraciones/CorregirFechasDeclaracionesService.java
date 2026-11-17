package com.trazalga.api.services.declaraciones;

import com.trazalga.api.ApiApplication;
import com.trazalga.api.dto.ResultadoValidacion.MarcaItem;
import com.trazalga.api.models.*;
import com.trazalga.api.repositories.*;
import com.trazalga.api.services.CuotaExtraccionService;
import com.trazalga.api.services.LimiteExtraccionDiarioService;
import com.trazalga.api.services.VedaEvaluadorService;
import com.trazalga.api.services.fechas.FechaSql;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * ZT.3 · Detección y corrección controlada de declaraciones ya guardadas cuya
 * fecha difiere del día local en {@code America/Santiago} de {@code gps_capturado_en}.
 * <p>
 * En simulación ({@code aplicar=false}) informa las filas afectadas y qué marcas de
 * LED, cuota o veda dejarían de corresponder o aparecerían. Al aplicar ({@code aplicar=true}),
 * respalda en {@code zz_respaldo_fechas_20261116}, actualiza solo las fechas listadas y
 * registra el cambio en {@code declaracion_fecha_ajuste_log} sin crear, borrar ni resolver
 * marcas en {@code declaracion_marca}.
 */
@Service
public class CorregirFechasDeclaracionesService {

    private static final Set<String> MARCAS_TEMPORALES = Set.of(
            "EN_VEDA",
            "LED_EXCEDIDO",
            "CUOTA_EXCEDIDA",
            "POSTERIOR_CIERRE",
            "DECLARACION_EXTEMPORANEA"
    );

    @Autowired private IDeclaracionRecolectorRepository recolectorRepository;
    @Autowired private IDeclaracionArmadorRepository armadorRepository;
    @Autowired private IDeclaracionAreaRepository areaRepository;
    @Autowired private IDeclaracionComercializadorRepository comercializadorRepository;
    @Autowired private IDeclaracionMarcaRepository marcaRepository;
    @Autowired private IDeclaracionFechaAjusteLogRepository fechaAjusteLogRepository;
    @Autowired private VedaEvaluadorService vedaEvaluadorService;
    @Autowired private CuotaExtraccionService cuotaExtraccionService;
    @Autowired private LimiteExtraccionDiarioService limiteExtraccionDiarioService;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public Map<String, Object> simularCorreccion() {
        return procesar(false);
    }

    @Transactional(readOnly = false)
    public Map<String, Object> aplicarCorreccion() {
        return procesar(true);
    }

    private Map<String, Object> procesar(boolean aplicar) {
        List<Map<String, Object>> cambios = new ArrayList<>();
        List<Map<String, Object>> sinGpsListado = new ArrayList<>();
        Map<String, Long> sinGpsPorTipo = new LinkedHashMap<>();
        sinGpsPorTipo.put("RECOLECTOR", 0L);
        sinGpsPorTipo.put("ARMADOR", 0L);
        sinGpsPorTipo.put("AREA", 0L);
        sinGpsPorTipo.put("COMERCIALIZADOR", 0L);

        if (aplicar) {
            asegurarTablaRespaldo();
        }

        LocalDateTime ahora = LocalDateTime.now(ApiApplication.ZONA_CHILE);
        long actualizadas = 0L;

        // 1. RECOLECTOR
        for (DeclaracionRecolectorModel r : recolectorRepository.findAll()) {
            if (r.getGpsCapturadoEn() == null) {
                registrarSinGps("RECOLECTOR", r.getId(), r.getFolioOrigen(), r.getFechaDeclaracion(), sinGpsPorTipo, sinGpsListado);
                continue;
            }
            LocalDate fechaActual = FechaSql.diaLocal(r.getFechaDeclaracion());
            LocalDate fechaProbable = FechaSql.diaLocal(r.getGpsCapturadoEn());
            if (fechaActual != null && fechaProbable != null && !fechaActual.equals(fechaProbable)) {
                Map<String, Object> item = construirItemOrigen(
                        "RECOLECTOR", r.getId(), r.getFolioOrigen(), fechaActual, fechaProbable, r.getHora(),
                        r.getGpsCapturadoEn(), r.getFechaExtraccion(), r.getUsuario(),
                        r.getCaleta() != null ? r.getCaleta().getComuna() : r.getComuna(),
                        null, null, null,
                        r.getEspecie() != null ? r.getEspecie().getId() : null,
                        r.getExtraccionTipo() != null ? r.getExtraccionTipo().getId() : null,
                        r.getDesembarque(),
                        r.getCaptura());
                cambios.add(item);

                if (aplicar) {
                    respaldar("RECOLECTOR", r.getId(), r.getFolioOrigen(), r.getFechaDeclaracion(), r.getFechaExtraccion(), r.getHora(), r.getGpsCapturadoEn());
                    Date nuevaDate = java.sql.Date.valueOf(fechaProbable);
                    r.setFechaDeclaracion(nuevaDate);
                    if (r.getFechaExtraccion() != null && FechaSql.diaLocal(r.getFechaExtraccion()).equals(fechaActual)) {
                        r.setFechaExtraccion(nuevaDate);
                    }
                    if (r.getPeriodoExtraccionInicio() != null && FechaSql.diaLocal(r.getPeriodoExtraccionInicio()).equals(fechaActual)) {
                        r.setPeriodoExtraccionInicio(nuevaDate);
                    }
                    if (r.getPeriodoExtraccionFin() != null && FechaSql.diaLocal(r.getPeriodoExtraccionFin()).equals(fechaActual)) {
                        r.setPeriodoExtraccionFin(nuevaDate);
                    }
                    recolectorRepository.save(r);
                    guardarLog("RECOLECTOR", r.getId(), fechaActual, fechaProbable, r.getHora(), ahora);
                    actualizadas++;
                }
            }
        }

        // 2. ARMADOR
        for (DeclaracionArmadorModel a : armadorRepository.findAll()) {
            if (a.getGpsCapturadoEn() == null) {
                registrarSinGps("ARMADOR", a.getId(), a.getFolioOrigen(), a.getFechaDeclaracion(), sinGpsPorTipo, sinGpsListado);
                continue;
            }
            LocalDate fechaActual = FechaSql.diaLocal(a.getFechaDeclaracion());
            LocalDate fechaProbable = FechaSql.diaLocal(a.getGpsCapturadoEn());
            if (fechaActual != null && fechaProbable != null && !fechaActual.equals(fechaProbable)) {
                Map<String, Object> item = construirItemOrigen(
                        "ARMADOR", a.getId(), a.getFolioOrigen(), fechaActual, fechaProbable, a.getHora(),
                        a.getGpsCapturadoEn(), a.getFechaExtraccion(), a.getUsuario(),
                        a.getComuna() != null ? a.getComuna() : (a.getCaleta() != null ? a.getCaleta().getComuna() : null),
                        null,
                        a.getEmbarcacion() != null ? a.getEmbarcacion().getId() : null,
                        a.getBuzo() != null ? a.getBuzo().getId() : null,
                        a.getEspecie() != null ? a.getEspecie().getId() : null,
                        a.getExtraccionTipo() != null ? a.getExtraccionTipo().getId() : null,
                        a.getDesembarque(),
                        a.getCaptura() != null ? BigDecimal.valueOf(a.getCaptura()) : BigDecimal.ZERO);
                cambios.add(item);

                if (aplicar) {
                    respaldar("ARMADOR", a.getId(), a.getFolioOrigen(), a.getFechaDeclaracion(), a.getFechaExtraccion(), a.getHora(), a.getGpsCapturadoEn());
                    Date nuevaDate = java.sql.Date.valueOf(fechaProbable);
                    a.setFechaDeclaracion(nuevaDate);
                    if (a.getFechaExtraccion() != null && FechaSql.diaLocal(a.getFechaExtraccion()).equals(fechaActual)) {
                        a.setFechaExtraccion(nuevaDate);
                    }
                    armadorRepository.save(a);
                    guardarLog("ARMADOR", a.getId(), fechaActual, fechaProbable, a.getHora(), ahora);
                    actualizadas++;
                }
            }
        }

        // 3. AREA
        for (DeclaracionAreaModel ar : areaRepository.findAll()) {
            if (ar.getGpsCapturadoEn() == null) {
                registrarSinGps("AREA", ar.getId(), ar.getFolioOrigen(), ar.getFechaDeclaracion(), sinGpsPorTipo, sinGpsListado);
                continue;
            }
            LocalDate fechaActual = FechaSql.diaLocal(ar.getFechaDeclaracion());
            LocalDate fechaProbable = FechaSql.diaLocal(ar.getGpsCapturadoEn());
            if (fechaActual != null && fechaProbable != null && !fechaActual.equals(fechaProbable)) {
                ComunaModel comunaDes = ar.getAmerb() != null && ar.getAmerb().getComuna() != null
                        ? ar.getAmerb().getComuna()
                        : (ar.getCaleta() != null ? ar.getCaleta().getComuna() : null);
                Map<String, Object> item = construirItemOrigen(
                        "AREA", ar.getId(), ar.getFolioOrigen(), fechaActual, fechaProbable, ar.getHora(),
                        ar.getGpsCapturadoEn(), ar.getFechaExtraccion(), ar.getUsuario(),
                        comunaDes,
                        ar.getAmerb() != null ? ar.getAmerb().getId() : null,
                        ar.getEmbarcacion() != null ? ar.getEmbarcacion().getId() : null,
                        ar.getBuzo() != null ? ar.getBuzo().getId() : null,
                        ar.getEspecie() != null ? ar.getEspecie().getId() : null,
                        ar.getExtraccionTipo() != null ? ar.getExtraccionTipo().getId() : null,
                        ar.getDesembarque() != null ? BigDecimal.valueOf(ar.getDesembarque()) : BigDecimal.ZERO,
                        ar.getCaptura() != null ? BigDecimal.valueOf(ar.getCaptura()) : BigDecimal.ZERO);
                cambios.add(item);

                if (aplicar) {
                    respaldar("AREA", ar.getId(), ar.getFolioOrigen(), ar.getFechaDeclaracion(), ar.getFechaExtraccion(), ar.getHora(), ar.getGpsCapturadoEn());
                    Date nuevaDate = java.sql.Date.valueOf(fechaProbable);
                    ar.setFechaDeclaracion(nuevaDate);
                    if (ar.getFechaExtraccion() != null && FechaSql.diaLocal(ar.getFechaExtraccion()).equals(fechaActual)) {
                        ar.setFechaExtraccion(nuevaDate);
                    }
                    areaRepository.save(ar);
                    guardarLog("AREA", ar.getId(), fechaActual, fechaProbable, ar.getHora(), ahora);
                    actualizadas++;
                }
            }
        }

        // 4. COMERCIALIZADOR
        for (DeclaracionComercializadorModel c : comercializadorRepository.findAll()) {
            if (c.getGpsCapturadoEn() == null) {
                registrarSinGps("COMERCIALIZADOR", c.getId(), c.getFolioOrigen(), c.getFechaDeclaracion(), sinGpsPorTipo, sinGpsListado);
                continue;
            }
            LocalDate fechaActual = FechaSql.diaLocal(c.getFechaDeclaracion());
            LocalDate fechaProbable = FechaSql.diaLocal(c.getGpsCapturadoEn());
            if (fechaActual != null && fechaProbable != null && !fechaActual.equals(fechaProbable)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("tipo", "COMERCIALIZADOR");
                item.put("id", c.getId());
                item.put("folio", c.getFolioOrigen() != null ? c.getFolioOrigen() : c.getFolioDesembarqueAc());
                item.put("fechaGuardada", fechaActual.toString());
                item.put("fechaProbable", fechaProbable.toString());
                item.put("hora", c.getHora());
                item.put("marcasActuales", List.of());
                item.put("marcasEnFechaProbable", List.of());
                item.put("marcasQueDejarianDeCorresponder", List.of());
                item.put("marcasQueAparecerian", List.of());
                cambios.add(item);

                if (aplicar) {
                    respaldar("COMERCIALIZADOR", c.getId(), c.getFolioOrigen(), c.getFechaDeclaracion(), c.getFechaTraslado(), c.getHora(), c.getGpsCapturadoEn());
                    Date nuevaDate = java.sql.Date.valueOf(fechaProbable);
                    c.setFechaDeclaracion(nuevaDate);
                    if (c.getFechaTraslado() != null && FechaSql.diaLocal(c.getFechaTraslado()).equals(fechaActual)) {
                        c.setFechaTraslado(nuevaDate);
                    }
                    comercializadorRepository.save(c);
                    guardarLog("COMERCIALIZADOR", c.getId(), fechaActual, fechaProbable, c.getHora(), ahora);
                    actualizadas++;
                }
            }
        }

        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("aplicado", aplicar);
        resumen.put("totalPorCorregir", cambios.size());
        resumen.put("actualizadas", actualizadas);
        resumen.put("cambios", cambios);
        resumen.put("sinGpsCapturadoEnPorTipo", sinGpsPorTipo);
        resumen.put("sinGpsCapturadoEnTotal", sinGpsPorTipo.values().stream().mapToLong(Long::longValue).sum());
        resumen.put("sinGpsCapturadoEn", sinGpsListado);
        resumen.put("marcasModificadas", 0);
        return resumen;
    }

    private void registrarSinGps(
            String tipo,
            Long id,
            String folio,
            Date fechaDeclaracion,
            Map<String, Long> porTipo,
            List<Map<String, Object>> listado) {
        porTipo.merge(tipo, 1L, Long::sum);
        if (listado.size() < 500) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tipo", tipo);
            m.put("id", id);
            m.put("folio", folio);
            m.put("fechaDeclaracion", fechaDeclaracion != null ? FechaSql.diaLocal(fechaDeclaracion).toString() : null);
            listado.add(m);
        }
    }

    private Map<String, Object> construirItemOrigen(
            String tipo,
            Long id,
            String folio,
            LocalDate fechaActual,
            LocalDate fechaProbable,
            String hora,
            Date gpsCapturadoEn,
            Date fechaExtraccionActual,
            UsuarioModel usuario,
            ComunaModel comunaDesembarque,
            Long amerbId,
            Long embarcacionId,
            Long buzoId,
            Long especieId,
            Long extraccionTipoId,
            BigDecimal desembarqueKg,
            BigDecimal capturaKg) {

        Set<String> marcasActuales = new LinkedHashSet<>();
        for (DeclaracionMarcaModel m : marcaRepository.findByDeclaracionTipoAndDeclaracionId(tipo, id)) {
            if (m.getMarca() != null && MARCAS_TEMPORALES.contains(m.getMarca())) {
                marcasActuales.add(m.getMarca());
            }
        }

        Date nuevaFechaDecl = java.sql.Date.valueOf(fechaProbable);
        Date nuevaFechaExt = (fechaExtraccionActual == null || FechaSql.diaLocal(fechaExtraccionActual).equals(fechaActual))
                ? nuevaFechaDecl
                : fechaExtraccionActual;

        Long comunaInscripcionId = (usuario != null && usuario.getComuna() != null) ? usuario.getComuna().getId() : null;
        Long comunaDesId = comunaDesembarque != null ? comunaDesembarque.getId() : null;
        Long regionId = (comunaDesembarque != null && comunaDesembarque.getRegion() != null)
                ? comunaDesembarque.getRegion().getId()
                : ((usuario != null && usuario.getComuna() != null && usuario.getComuna().getRegion() != null)
                        ? usuario.getComuna().getRegion().getId()
                        : null);
        Long comunaImputacion = comunaInscripcionId != null ? comunaInscripcionId : comunaDesId;
        Long usuarioId = usuario != null ? usuario.getId() : null;

        Set<String> marcasProbables = new LinkedHashSet<>();
        if (especieId != null) {
            VedaEvaluadorService.EvaluacionVedaResult veda = vedaEvaluadorService.evaluar(
                    especieId, extraccionTipoId, regionId, nuevaFechaExt);
            if (veda != null && veda.isEnVeda()) {
                marcasProbables.add("EN_VEDA");
            }

            CuotaExtraccionService.EvaluacionCuotaResult cuota = cuotaExtraccionService.evaluarCuotaDeclaracion(
                    tipo, usuarioId, amerbId, especieId, extraccionTipoId, comunaImputacion,
                    nuevaFechaExt, nuevaFechaDecl, desembarqueKg, capturaKg, true, id);
            if (cuota != null) {
                if (cuota.getMarcas() != null) {
                    for (MarcaItem mi : cuota.getMarcas()) {
                        if (mi.getMarca() != null && MARCAS_TEMPORALES.contains(mi.getMarca())) {
                            marcasProbables.add(mi.getMarca());
                        }
                    }
                }
                if (cuota.getMarca() != null && MARCAS_TEMPORALES.contains(cuota.getMarca())) {
                    marcasProbables.add(cuota.getMarca());
                }
            }

            LimiteExtraccionDiarioService.EvaluacionLedResult led = limiteExtraccionDiarioService.evaluar(
                    tipo, embarcacionId, usuarioId, buzoId, especieId, extraccionTipoId,
                    regionId, nuevaFechaDecl, desembarqueKg, capturaKg);
            if (led != null && led.isExcede()) {
                marcasProbables.add("LED_EXCEDIDO");
            }
        }

        List<String> dejarian = new ArrayList<>();
        for (String m : marcasActuales) {
            if (!marcasProbables.contains(m)) {
                dejarian.add(m);
            }
        }
        List<String> aparecerian = new ArrayList<>();
        for (String m : marcasProbables) {
            if (!marcasActuales.contains(m)) {
                aparecerian.add(m);
            }
        }

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("tipo", tipo);
        item.put("id", id);
        item.put("folio", folio);
        item.put("fechaGuardada", fechaActual.toString());
        item.put("fechaProbable", fechaProbable.toString());
        item.put("hora", hora);
        item.put("marcasActuales", new ArrayList<>(marcasActuales));
        item.put("marcasEnFechaProbable", new ArrayList<>(marcasProbables));
        item.put("marcasQueDejarianDeCorresponder", dejarian);
        item.put("marcasQueAparecerian", aparecerian);
        return item;
    }

    private void asegurarTablaRespaldo() {
        entityManager.createNativeQuery(
                "CREATE TABLE IF NOT EXISTS zz_respaldo_fechas_20261116 ("
                        + "declaracion_tipo VARCHAR(40) NOT NULL, "
                        + "declaracion_id BIGINT NOT NULL, "
                        + "folio VARCHAR(60) NULL, "
                        + "fecha_declaracion_ant DATE NOT NULL, "
                        + "fecha_secundaria_ant DATE NULL, "
                        + "hora VARCHAR(16) NULL, "
                        + "gps_capturado_en DATETIME NULL, "
                        + "respaldado_en DATETIME NOT NULL, "
                        + "PRIMARY KEY (declaracion_tipo, declaracion_id))")
                .executeUpdate();
    }

    private void respaldar(
            String tipo,
            Long id,
            String folio,
            Date fechaDeclAnt,
            Date fechaSecAnt,
            String hora,
            Date gpsCapturadoEn) {
        Number count = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM zz_respaldo_fechas_20261116 WHERE declaracion_tipo = :tipo AND declaracion_id = :id")
                .setParameter("tipo", tipo)
                .setParameter("id", id)
                .getSingleResult();
        if (count != null && count.longValue() > 0) {
            return;
        }
        entityManager.createNativeQuery(
                "INSERT INTO zz_respaldo_fechas_20261116 "
                        + "(declaracion_tipo, declaracion_id, folio, fecha_declaracion_ant, fecha_secundaria_ant, hora, gps_capturado_en, respaldado_en) "
                        + "VALUES (:tipo, :id, :folio, :fDecl, :fSec, :hora, :gps, :ahora)")
                .setParameter("tipo", tipo)
                .setParameter("id", id)
                .setParameter("folio", folio != null && folio.length() > 60 ? folio.substring(0, 60) : folio)
                .setParameter("fDecl", FechaSql.dia(fechaDeclAnt))
                .setParameter("fSec", FechaSql.dia(fechaSecAnt))
                .setParameter("hora", hora)
                .setParameter("gps", gpsCapturadoEn)
                .setParameter("ahora", java.sql.Timestamp.valueOf(LocalDateTime.now(ApiApplication.ZONA_CHILE)))
                .executeUpdate();
    }

    private void guardarLog(
            String tipo,
            Long id,
            LocalDate fechaOriginal,
            LocalDate fechaGuardada,
            String hora,
            LocalDateTime servidor) {
        String horaTrim = hora != null && hora.trim().length() > 16 ? hora.trim().substring(0, 16) : (hora != null ? hora.trim() : null);
        fechaAjusteLogRepository.save(DeclaracionFechaAjusteLogModel.builder()
                .declaracionTipo(tipo)
                .declaracionId(id)
                .fechaOriginal(java.sql.Date.valueOf(fechaOriginal))
                .fechaGuardada(java.sql.Date.valueOf(fechaGuardada))
                .horaDeclarada(horaTrim)
                .motivo("CORRECCION_HISTORICA_GPS")
                .fechaServidor(servidor)
                .creadoEn(servidor)
                .build());
    }
}
