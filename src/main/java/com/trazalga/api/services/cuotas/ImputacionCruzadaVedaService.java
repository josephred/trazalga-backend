package com.trazalga.api.services.cuotas;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trazalga.api.dto.ContextoDeclaracion;
import com.trazalga.api.dto.ResumenImputacionCruzadaDTO;
import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.models.ExtraccionTipoModel;
import com.trazalga.api.repositories.IExtraccionTipoRepository;
import com.trazalga.api.services.CuotaExtraccionService;
import com.trazalga.api.services.VedaEvaluadorService;
import com.trazalga.api.services.parametros.ParametrosImputacionVeda;
import com.trazalga.api.services.parametros.ParametrosService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * Imputación cruzada por veda (refinamiento del 23-10-2026, punto 1).
 *
 * <p><b>Regla acordada con Sernapesca.</b> Si un recolector, armador u organización declara extracción
 * por un método que está en veda para la especie (p. ej. huiro negro por barreteado en octubre) y en la
 * comuna y el período de imputación no hay una cuota que aplique a ese método, la alerta {@code EN_VEDA}
 * se mantiene y la captura corregida se imputa a la cuota del método de destino (varado) de la misma
 * especie, comuna y período. Así la biomasa extraída queda cuantificada y descontada.</p>
 *
 * <p><b>Lo declarado no se reescribe.</b> El método declarado queda en {@code extraccion_tipo_id}; el
 * método de imputación se guarda aparte, en {@code extraccion_tipo_imputacion_id}, y las consultas de
 * consumo lo leen con {@link MetodoImputacion#sqlMetodoEfectivo(String)}.</p>
 *
 * <p>Parámetros: {@code cuota_imputacion_veda_activo}, {@code cuota_imputacion_veda_metodo_origen}
 * (BARRETEADO) y {@code cuota_imputacion_veda_metodo_destino} (VARADO).</p>
 *
 * <p>{@link #resolver(ContextoImputacion)} no tiene efectos laterales: no calcula consumo ni registra
 * avisos. {@link #recalcularImputaciones(LocalDate, LocalDate, boolean)} recalcula la imputación de las
 * declaraciones ya guardadas, por ejemplo después de crear o cambiar cuotas o vedas.</p>
 */
@Service
@Slf4j
public class ImputacionCruzadaVedaService {

    private static final Locale LOCALE_CL = Locale.forLanguageTag("es-CL");
    private static final int MAX_DIAS_RECALCULO = 400;

    public enum Estado {
        /** No corresponde imputación cruzada: la declaración consume con su método declarado. */
        NO_APLICA,
        /** Método en veda sin cuota propia: la captura se imputa a la cuota del método de destino. */
        CRUZADA,
        /** Método en veda sin cuota propia ni cuota del método de destino: no se descuenta de ninguna cuota. */
        SIN_CUOTA_DESTINO
    }

    @Value
    @Builder
    public static class ContextoImputacion {
        String perfil;
        Long usuarioId;
        Long amerbId;
        Long especieId;
        /** Método declarado. */
        Long extraccionTipoId;
        Long comunaImputacionId;
        Date fechaExtraccion;
        Date fechaDeclaracion;
        /** Si la extracción está en veda para el método declarado ({@link VedaEvaluadorService}). */
        boolean enVeda;
    }

    @Value
    @Builder
    public static class Resultado {
        Estado estado;
        /** Método cuya cuota recibe la captura; sólo cuando {@link Estado#CRUZADA}. */
        Long metodoImputacionId;
        String metodoDeclaradoNombre;
        String metodoDestinoNombre;
        Long cuotaDestinoId;
        String cuotaDestinoAlcance;

        public boolean isCruzada() {
            return estado == Estado.CRUZADA;
        }

        /**
         * Texto es-CL que se agrega al detalle de la marca {@code EN_VEDA}. Nulo cuando no aplica.
         */
        public String texto(BigDecimal capturaKg) {
            if (estado == Estado.CRUZADA) {
                String kilos = capturaKg != null ? " (" + formatoKg(capturaKg) + " kg)" : "";
                return String.format(LOCALE_CL,
                        "Sin cuota de %s para la especie, la comuna y el período: la captura corregida%s se imputa a la cuota de %s %s (ID %d).",
                        minusculas(metodoDeclaradoNombre), kilos, minusculas(metodoDestinoNombre),
                        cuotaDestinoAlcance, cuotaDestinoId);
            }
            if (estado == Estado.SIN_CUOTA_DESTINO) {
                return String.format(LOCALE_CL,
                        "Sin cuota de %s ni de %s para la especie, la comuna y el período: la captura no se descuenta de ninguna cuota.",
                        minusculas(metodoDeclaradoNombre), minusculas(metodoDestinoNombre));
            }
            return null;
        }

        static Resultado noAplica() {
            return Resultado.builder().estado(Estado.NO_APLICA).build();
        }
    }

    /** Ids y nombres de los métodos de origen y destino, resueltos desde el maestro por nombre. */
    public record MetodosImputacion(Long origenId, String origenNombre, Long destinoId, String destinoNombre) {}

    @Autowired
    @Lazy
    private CuotaExtraccionService cuotaExtraccionService;

    @Autowired
    private IExtraccionTipoRepository extraccionTipoRepository;

    @Autowired(required = false)
    @Lazy
    private ParametrosService parametrosService;

    @Autowired(required = false)
    @Lazy
    private VedaEvaluadorService vedaEvaluadorService;

    @Autowired(required = false)
    private ResolutorImputacionTerritorial resolutorImputacion;

    @PersistenceContext
    private EntityManager entityManager;

    // =========================================================================
    // RESOLUCIÓN (al validar una declaración)
    // =========================================================================

    /**
     * Decide si la captura de una declaración se imputa a la cuota de otro método por veda.
     * No calcula consumo ni registra avisos.
     */
    @Transactional(readOnly = true)
    public Resultado resolver(ContextoImputacion ctx) {
        if (ctx == null || !ctx.isEnVeda() || ctx.getExtraccionTipoId() == null) {
            return Resultado.noAplica();
        }
        MetodosImputacion metodos = resolverMetodos();
        if (metodos == null) {
            return Resultado.noAplica();
        }
        return resolver(ctx, metodos);
    }

    private Resultado resolver(ContextoImputacion ctx, MetodosImputacion metodos) {
        if (ctx == null || !ctx.isEnVeda() || ctx.getExtraccionTipoId() == null
                || !ctx.getExtraccionTipoId().equals(metodos.origenId())) {
            return Resultado.noAplica();
        }

        // ¿El método declarado tiene cuota en la comuna y el período? (una cuota sin método también cuenta)
        List<CuotaExtraccionModel> propias = cuotaExtraccionService.cuotasAplicables(
                ctx.getPerfil(), ctx.getUsuarioId(), ctx.getAmerbId(), ctx.getEspecieId(),
                ctx.getExtraccionTipoId(), ctx.getComunaImputacionId(),
                ctx.getFechaExtraccion(), ctx.getFechaDeclaracion());
        if (!propias.isEmpty()) {
            return Resultado.noAplica();
        }

        List<CuotaExtraccionModel> destino = cuotaExtraccionService.cuotasAplicables(
                ctx.getPerfil(), ctx.getUsuarioId(), ctx.getAmerbId(), ctx.getEspecieId(),
                metodos.destinoId(), ctx.getComunaImputacionId(),
                ctx.getFechaExtraccion(), ctx.getFechaDeclaracion());
        if (destino.isEmpty()) {
            return Resultado.builder()
                    .estado(Estado.SIN_CUOTA_DESTINO)
                    .metodoDeclaradoNombre(metodos.origenNombre())
                    .metodoDestinoNombre(metodos.destinoNombre())
                    .build();
        }

        // La cuota más específica (persona > AMERB > comuna > provincia > región) es la que se informa.
        CuotaExtraccionModel principal = destino.stream()
                .max(Comparator.comparingInt(cuotaExtraccionService::calcularEspecificidadCuota))
                .orElse(destino.get(0));

        return Resultado.builder()
                .estado(Estado.CRUZADA)
                .metodoImputacionId(metodos.destinoId())
                .metodoDeclaradoNombre(metodos.origenNombre())
                .metodoDestinoNombre(metodos.destinoNombre())
                .cuotaDestinoId(principal.getId())
                .cuotaDestinoAlcance(cuotaExtraccionService.describirAlcance(principal))
                .build();
    }

    /**
     * Resuelve los métodos de origen y destino desde el maestro {@code extraccion_tipo}, comparando
     * nombres sin tildes ni mayúsculas. Devuelve nulo si la regla está apagada o si algún método no se
     * encuentra o es ambiguo.
     */
    @Transactional(readOnly = true)
    public MetodosImputacion resolverMetodos() {
        ParametrosImputacionVeda p = parametros();
        if (!p.activo()) {
            return null;
        }
        List<ExtraccionTipoModel> todos = extraccionTipoRepository.findAll();
        ExtraccionTipoModel origen = buscarPorNombre(todos, p.metodoEnVeda());
        ExtraccionTipoModel destino = buscarPorNombre(todos, p.metodoDestino());
        if (origen == null || destino == null) {
            log.warn("Imputación cruzada por veda sin efecto: no se encontró un único método '{}' y '{}' en el maestro de métodos de extracción.",
                    p.metodoEnVeda(), p.metodoDestino());
            return null;
        }
        if (origen.getId().equals(destino.getId())) {
            log.warn("Imputación cruzada por veda sin efecto: el método de origen y el de destino son el mismo ({}).", origen.getNombre());
            return null;
        }
        return new MetodosImputacion(origen.getId(), origen.getNombre(), destino.getId(), destino.getNombre());
    }

    private ParametrosImputacionVeda parametros() {
        if (parametrosService != null) {
            ParametrosImputacionVeda p = parametrosService.imputacionVeda();
            if (p != null) {
                return p;
            }
        }
        return new ParametrosImputacionVeda(true, "BARRETEADO", "VARADO");
    }

    static ExtraccionTipoModel buscarPorNombre(List<ExtraccionTipoModel> metodos, String nombreBuscado) {
        String buscado = normalizar(nombreBuscado);
        if (buscado.isEmpty() || metodos == null) {
            return null;
        }
        List<ExtraccionTipoModel> exactos = new ArrayList<>();
        List<ExtraccionTipoModel> parciales = new ArrayList<>();
        for (ExtraccionTipoModel m : metodos) {
            if (m == null || m.getId() == null) continue;
            String nombre = normalizar(m.getNombre());
            if (nombre.equals(buscado)) {
                exactos.add(m);
            } else if (nombre.contains(buscado)) {
                parciales.add(m);
            }
        }
        if (exactos.size() == 1) return exactos.get(0);
        if (exactos.isEmpty() && parciales.size() == 1) return parciales.get(0);
        return null;
    }

    static String normalizar(String s) {
        if (s == null) return "";
        String sinTildes = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private static String minusculas(String s) {
        return s != null ? s.trim().toLowerCase(LOCALE_CL) : "";
    }

    private static String formatoKg(BigDecimal kg) {
        DecimalFormat df = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(LOCALE_CL));
        return df.format(kg);
    }

    // =========================================================================
    // RECÁLCULO DE DECLARACIONES YA GUARDADAS
    // =========================================================================

    /**
     * Recalcula la imputación cruzada de las declaraciones de origen cuya fecha de imputación de cuota
     * cae entre {@code desde} y {@code hasta}. Con {@code aplicar = false} sólo informa lo que cambiaría.
     *
     * <p>Sólo escribe {@code extraccion_tipo_imputacion_id}; no toca lo declarado ni las marcas. Las
     * marcas de cuota de declaraciones antiguas se completan después con el recálculo de hallazgos de
     * cada cuota ({@code POST /api/cuotas/{id}/recalcular-hallazgos}).</p>
     */
    @Transactional
    public ResumenImputacionCruzadaDTO recalcularImputaciones(LocalDate desde, LocalDate hasta, boolean aplicar) {
        LocalDate hoy = LocalDate.now();
        LocalDate fDesde = desde != null ? desde : hoy.withDayOfMonth(1);
        LocalDate fHasta = hasta != null ? hasta : fDesde.withDayOfMonth(fDesde.lengthOfMonth());
        if (fHasta.isBefore(fDesde)) {
            throw new IllegalArgumentException("La fecha 'hasta' no puede ser anterior a 'desde'.");
        }
        if (fDesde.plusDays(MAX_DIAS_RECALCULO).isBefore(fHasta)) {
            throw new IllegalArgumentException("El rango no puede superar " + MAX_DIAS_RECALCULO + " días.");
        }

        ParametrosImputacionVeda p = parametros();
        MetodosImputacion metodos = resolverMetodos();
        String modo = cuotaExtraccionService.getModoImputacion();
        String colFecha = "DECLARACION".equalsIgnoreCase(modo) ? "fecha_declaracion" : "fecha_extraccion";

        List<ResumenImputacionCruzadaDTO.Item> cambios = new ArrayList<>();
        int evaluadas = 0;
        int cruzadas = 0;
        int sinCuotaDestino = 0;

        // tipo, tabla, folio, AMERB y comuna de desembarque (declaracion_area no tiene comuna_id: se usa la de la caleta)
        String[][] tablas = {
                {"RECOLECTOR", "declaracion_recolector", "COALESCE(d.folio_origen, d.folio_desembarque_ro, CONCAT('DR-', d.id))", "NULL", "COALESCE(d.comuna_id, ca.comuna_id)"},
                {"ARMADOR", "declaracion_armador", "COALESCE(d.folio_origen, d.folio_desembarque_da, CONCAT('DA-', d.id))", "NULL", "COALESCE(d.comuna_id, ca.comuna_id)"},
                {"AREA", "declaracion_area", "COALESCE(d.folio_origen, d.folio_desembarque_amerb, CONCAT('DAM-', d.id))", "d.amerb_id", "ca.comuna_id"}
        };

        for (String[] t : tablas) {
            String tipo = t[0];
            String tabla = t[1];
            List<Object[]> filas = cargarCandidatas(tabla, t[2], t[3], t[4], colFecha, fDesde, fHasta, metodos);
            for (Object[] r : filas) {
                evaluadas++;
                Long id = toLong(r[0]);
                Long usuarioId = toLong(r[1]);
                Long comunaInscripcionId = toLong(r[2]);
                Long comunaDesembarqueId = toLong(r[3]);
                Long regionId = toLong(r[4]);
                Long especieId = toLong(r[5]);
                Long extraccionTipoId = toLong(r[6]);
                Long imputacionAnterior = toLong(r[7]);
                Date fechaExtraccion = (Date) r[8];
                Date fechaDeclaracion = (Date) r[9];
                String folio = r[10] != null ? r[10].toString() : null;
                Long amerbId = toLong(r[11]);
                String especieNombre = r[12] != null ? r[12].toString() : null;
                String metodoDeclaradoNombre = r[13] != null ? r[13].toString() : null;

                Resultado res = Resultado.noAplica();
                if (metodos != null && extraccionTipoId != null && extraccionTipoId.equals(metodos.origenId())) {
                    boolean enVeda = vedaEvaluadorService != null
                            && vedaEvaluadorService.evaluar(especieId, extraccionTipoId, regionId, fechaExtraccion).isEnVeda();
                    Long comunaImputacionId = comunaImputacion(tipo, comunaInscripcionId, comunaDesembarqueId);
                    res = resolver(ContextoImputacion.builder()
                            .perfil(tipo)
                            .usuarioId(usuarioId)
                            .amerbId(amerbId)
                            .especieId(especieId)
                            .extraccionTipoId(extraccionTipoId)
                            .comunaImputacionId(comunaImputacionId)
                            .fechaExtraccion(fechaExtraccion)
                            .fechaDeclaracion(fechaDeclaracion)
                            .enVeda(enVeda)
                            .build(), metodos);
                }
                if (res.getEstado() == Estado.CRUZADA) cruzadas++;
                if (res.getEstado() == Estado.SIN_CUOTA_DESTINO) sinCuotaDestino++;

                Long imputacionNueva = res.isCruzada() ? res.getMetodoImputacionId() : null;
                boolean cambia = (imputacionAnterior == null) ? imputacionNueva != null : !imputacionAnterior.equals(imputacionNueva);
                if (cambia || res.getEstado() == Estado.SIN_CUOTA_DESTINO) {
                    cambios.add(ResumenImputacionCruzadaDTO.Item.builder()
                            .tipo(tipo)
                            .declaracionId(id)
                            .folio(folio)
                            .fechaExtraccion(fechaExtraccion != null ? CuotaExtraccionService.toLocalDateSafe(fechaExtraccion).toString() : null)
                            .especie(especieNombre)
                            .metodoDeclarado(metodoDeclaradoNombre)
                            .imputacionAnteriorId(imputacionAnterior)
                            .imputacionNuevaId(imputacionNueva)
                            .estado(res.getEstado().name())
                            .cambia(cambia)
                            .cuotaDestinoId(res.getCuotaDestinoId())
                            .detalle(res.texto(null))
                            .build());
                }
                if (aplicar && cambia) {
                    Query upd = entityManager.createNativeQuery(
                            "UPDATE " + tabla + " SET " + MetodoImputacion.COLUMNA_IMPUTACION + " = :nuevo WHERE id = :id");
                    upd.setParameter("nuevo", imputacionNueva);
                    upd.setParameter("id", id);
                    upd.executeUpdate();
                }
            }
        }

        long nCambios = cambios.stream().filter(ResumenImputacionCruzadaDTO.Item::isCambia).count();
        if (aplicar && nCambios > 0) {
            cuotaExtraccionService.invalidarCacheConsumo();
        }
        log.info("Imputación cruzada por veda [{} .. {}] aplicar={} evaluadas={} cruzadas={} sinCuotaDestino={} cambios={}",
                fDesde, fHasta, aplicar, evaluadas, cruzadas, sinCuotaDestino, nCambios);

        String mensaje;
        if (!p.activo()) {
            mensaje = "La regla está apagada (cuota_imputacion_veda_activo = false): ninguna declaración se imputa a otro método.";
        } else if (metodos == null) {
            mensaje = "No se encontró un único método '" + p.metodoEnVeda() + "' y '" + p.metodoDestino()
                    + "' en el maestro de métodos de extracción: la regla no tiene efecto.";
        } else {
            mensaje = "Método en veda: " + metodos.origenNombre() + ". Método de destino: " + metodos.destinoNombre() + ".";
        }

        return ResumenImputacionCruzadaDTO.builder()
                .desde(fDesde.toString())
                .hasta(fHasta.toString())
                .aplicado(aplicar)
                .reglaActiva(p.activo())
                .metodoEnVeda(metodos != null ? metodos.origenNombre() : p.metodoEnVeda())
                .metodoDestino(metodos != null ? metodos.destinoNombre() : p.metodoDestino())
                .declaracionesEvaluadas(evaluadas)
                .imputacionesCruzadas(cruzadas)
                .sinCuotaDestino(sinCuotaDestino)
                .cambios((int) nCambios)
                .mensaje(mensaje)
                .detalle(cambios)
                .build();
    }

    private List<Object[]> cargarCandidatas(String tabla, String sqlFolio, String sqlAmerb, String sqlComuna, String colFecha,
                                           LocalDate desde, LocalDate hasta, MetodosImputacion metodos) {
        // Se evalúan las declaraciones del método de origen y las que ya tienen imputación (para corregirlas).
        StringBuilder sql = new StringBuilder()
                .append("SELECT d.id, d.usuario_id, u.comuna_id, ").append(sqlComuna).append(", ")
                .append("c.region_id, d.especie_id, d.extraccion_tipo_id, d.")
                .append(MetodoImputacion.COLUMNA_IMPUTACION).append(", d.fecha_extraccion, d.fecha_declaracion, ")
                .append(sqlFolio).append(", ").append(sqlAmerb).append(", e.nombre, et.nombre ")
                .append("FROM ").append(tabla).append(" d ")
                .append("LEFT JOIN usuario u ON u.id = d.usuario_id ")
                .append("LEFT JOIN caleta ca ON ca.id = d.caleta_id ")
                .append("LEFT JOIN comuna c ON c.id = ").append(sqlComuna).append(" ")
                .append("LEFT JOIN especie e ON e.id = d.especie_id ")
                .append("LEFT JOIN extraccion_tipo et ON et.id = d.extraccion_tipo_id ")
                .append("WHERE d.").append(colFecha).append(" BETWEEN :desde AND :hasta ")
                .append("AND (d.").append(MetodoImputacion.COLUMNA_IMPUTACION).append(" IS NOT NULL");
        if (metodos != null) {
            sql.append(" OR d.extraccion_tipo_id = :origenId");
        }
        sql.append(") ORDER BY d.id");

        Query q = entityManager.createNativeQuery(sql.toString());
        q.setParameter("desde", java.sql.Date.valueOf(desde));
        q.setParameter("hasta", java.sql.Date.valueOf(hasta));
        if (metodos != null) {
            q.setParameter("origenId", metodos.origenId());
        }
        @SuppressWarnings("unchecked")
        List<Object[]> filas = q.getResultList();
        return filas;
    }

    private Long comunaImputacion(String tipo, Long comunaInscripcionId, Long comunaDesembarqueId) {
        ContextoDeclaracion ctx = ContextoDeclaracion.builder()
                .tipoDeclaracion(tipo)
                .comunaInscripcionId(comunaInscripcionId)
                .comunaDesembarqueId(comunaDesembarqueId)
                .build();
        if (resolutorImputacion != null) {
            return resolutorImputacion.resolver(tipo).comunaImputacion(ctx);
        }
        // Mismo respaldo que ValidacionDeclaracionService cuando no hay resolutor
        return "RECOLECTOR".equalsIgnoreCase(tipo)
                ? (comunaInscripcionId != null ? comunaInscripcionId : comunaDesembarqueId)
                : (comunaDesembarqueId != null ? comunaDesembarqueId : comunaInscripcionId);
    }

    private static Long toLong(Object o) {
        return o instanceof Number n ? n.longValue() : null;
    }
}
