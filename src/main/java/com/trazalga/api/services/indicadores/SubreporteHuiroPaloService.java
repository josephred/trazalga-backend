package com.trazalga.api.services.indicadores;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trazalga.api.dto.SubreporteHuiroPaloDTO;
import com.trazalga.api.repositories.IComposicionRepository;
import com.trazalga.api.repositories.IEspecieRepository;
import com.trazalga.api.repositories.IExtraccionTipoRepository;
import com.trazalga.api.services.CuotaExtraccionService;
import com.trazalga.api.services.parametros.ParametrosService;
import com.trazalga.api.services.parametros.ParametrosSubreporteHuiroPalo;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.extern.slf4j.Slf4j;

/**
 * Subreporte Huiro Palo (refinamiento 23-10-2026, punto 3).
 * <p>
 * Cuando el huiro palo barreteado se declara "sin fronda", la fronda (22 % de la captura biológica
 * según IFOP) no queda declarada. Este servicio suma la captura biológica corregida de esas
 * declaraciones en el rango y estima los kilos no declarados con el porcentaje y la fórmula vigentes.
 * <ul>
 *   <li>El método es el declarado ({@code extraccion_tipo_id}), aunque por la imputación cruzada por veda
 *       la cuota que descuenta sea la de otro método.</li>
 *   <li>Las declaraciones sin composición informada no entran en el cálculo; se informan aparte.</li>
 *   <li>La comuna es la de desembarque (la de la declaración o, si falta, la de la caleta).</li>
 *   <li>La fecha es la de extracción o la de declaración, según {@code cuota_fecha_imputacion}.</li>
 * </ul>
 */
@Service
@Slf4j
public class SubreporteHuiroPaloService {

    private static final ZoneId ZONA = ZoneId.of("America/Santiago");
    private static final int MAX_DIAS = 800;

    static final String PERFIL_RECOLECTOR = "RECOLECTOR";
    static final String PERFIL_ARMADOR = "ARMADOR";
    static final String PERFIL_AREA = "AREA";

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired(required = false)
    private ParametrosService parametrosService;

    @Autowired(required = false)
    @Lazy
    private CuotaExtraccionService cuotaExtraccionService;

    @Autowired private IEspecieRepository especieRepository;
    @Autowired private IExtraccionTipoRepository extraccionTipoRepository;
    @Autowired private IComposicionRepository composicionRepository;

    @Autowired(required = false)
    private Clock clock;

    /** Fila agregada por perfil, composición, comuna y mes. */
    record Fila(String perfil, Long composicionId, Long comunaId, String comuna, int anio, int mes,
                long declaraciones, double capturaKg) {}

    /** Ids de los maestros que calzan con los nombres configurados. */
    record Seleccion(List<Long> especies, List<Long> metodos, List<Long> composiciones) {}

    @Transactional(readOnly = true)
    public SubreporteHuiroPaloDTO calcular(LocalDate desde, LocalDate hasta) {
        LocalDate hoy = clock != null ? LocalDate.now(clock) : LocalDate.now(ZONA);
        LocalDate fDesde = desde != null ? desde : (hasta != null ? hasta.withDayOfMonth(1) : hoy.withDayOfMonth(1));
        LocalDate fHasta = hasta != null ? hasta : fDesde.withDayOfMonth(fDesde.lengthOfMonth());
        if (fHasta.isBefore(fDesde)) {
            throw new IllegalArgumentException("La fecha 'hasta' no puede ser anterior a 'desde'.");
        }
        if (fDesde.plusDays(MAX_DIAS).isBefore(fHasta)) {
            throw new IllegalArgumentException("El rango no puede superar " + MAX_DIAS + " días.");
        }

        ParametrosSubreporteHuiroPalo p = parametros();
        String criterioFecha = cuotaExtraccionService != null ? cuotaExtraccionService.getModoImputacion() : "EXTRACCION";

        SubreporteHuiroPaloDTO dto = base(p, fDesde, fHasta, criterioFecha);
        if (!p.activo()) {
            dto.setMensaje("El Subreporte Huiro Palo está desactivado en Administración (Alertas y Parámetros, Indicador 2).");
            return dto;
        }

        List<Long> especies = resolverIds(especieRepository.findAll(), e -> e.getId(), e -> e.getNombre(), p.especie(), true);
        List<Long> metodos = resolverIds(extraccionTipoRepository.findAll(), m -> m.getId(), m -> m.getNombre(), p.metodo(), true);
        List<Long> composiciones = resolverIds(composicionRepository.findAll(), c -> c.getId(), c -> c.getNombre(), p.composicion(), false);

        List<String> problemas = new ArrayList<>();
        if (especies.isEmpty()) problemas.add("no hay una especie «" + p.especie() + "» en el maestro de especies");
        if (metodos.isEmpty()) problemas.add("no hay un método «" + p.metodo() + "» en el maestro de métodos");
        if (composiciones.isEmpty()) problemas.add("no hay una composición «" + p.composicion() + "» en el maestro de composiciones");
        if (!p.factorValido()) problemas.add("el porcentaje de fronda debe ser mayor que 0 y menor que 100");
        if (!problemas.isEmpty()) {
            String mensaje = "No se puede calcular el subreporte: " + String.join("; ", problemas) + ".";
            log.warn("Subreporte Huiro Palo sin efecto: {}", mensaje);
            dto.setMensaje(mensaje);
            return dto;
        }

        String colFecha = "DECLARACION".equalsIgnoreCase(criterioFecha) ? "fecha_declaracion" : "fecha_extraccion";
        List<Fila> filas = new ArrayList<>();
        filas.addAll(cargar(PERFIL_RECOLECTOR, "declaracion_recolector", "COALESCE(d.comuna_id, ca.comuna_id)", colFecha, fDesde, fHasta, especies, metodos));
        filas.addAll(cargar(PERFIL_ARMADOR, "declaracion_armador", "COALESCE(d.comuna_id, ca.comuna_id)", colFecha, fDesde, fHasta, especies, metodos));
        // declaracion_area no tiene comuna_id: se usa la de la caleta
        filas.addAll(cargar(PERFIL_AREA, "declaracion_area", "ca.comuna_id", colFecha, fDesde, fHasta, especies, metodos));

        agregar(dto, filas, new Seleccion(especies, metodos, composiciones), p.multiplicador());
        return dto;
    }

    private ParametrosSubreporteHuiroPalo parametros() {
        if (parametrosService != null) {
            ParametrosSubreporteHuiroPalo p = parametrosService.subreporteHuiroPalo();
            if (p != null) {
                return p;
            }
        }
        return ParametrosSubreporteHuiroPalo.porDefecto();
    }

    static SubreporteHuiroPaloDTO base(ParametrosSubreporteHuiroPalo p, LocalDate desde, LocalDate hasta, String criterioFecha) {
        return SubreporteHuiroPaloDTO.builder()
                .desde(desde.toString())
                .hasta(hasta.toString())
                .criterioFecha(criterioFecha)
                .activo(p.activo())
                .configuracionValida(false)
                .especie(p.especie())
                .metodo(p.metodo())
                .composicion(p.composicion())
                .factorPct(p.factorPct())
                .formula(p.formulaNormalizada())
                .multiplicador(redondear(p.multiplicador(), 4))
                .porComuna(List.of())
                .porMes(List.of())
                .porPerfil(List.of())
                .build();
    }

    private List<Fila> cargar(String perfil, String tabla, String sqlComuna, String colFecha, LocalDate desde, LocalDate hasta,
                              List<Long> especies, List<Long> metodos) {
        String fecha = "d." + colFecha;
        String sql = "SELECT d.composicion_id, " + sqlComuna + ", c.nombre, "
                + "EXTRACT(YEAR FROM " + fecha + "), EXTRACT(MONTH FROM " + fecha + "), "
                + "COUNT(*), SUM(COALESCE(d.captura, d.desembarque)) "
                + "FROM " + tabla + " d "
                + "LEFT JOIN caleta ca ON ca.id = d.caleta_id "
                + "LEFT JOIN comuna c ON c.id = " + sqlComuna + " "
                + "WHERE d.especie_id IN (:especies) AND d.extraccion_tipo_id IN (:metodos) "
                + "AND " + fecha + " BETWEEN :desde AND :hasta "
                + "GROUP BY d.composicion_id, " + sqlComuna + ", c.nombre, "
                + "EXTRACT(YEAR FROM " + fecha + "), EXTRACT(MONTH FROM " + fecha + ")";
        Query q = entityManager.createNativeQuery(sql);
        q.setParameter("especies", especies);
        q.setParameter("metodos", metodos);
        q.setParameter("desde", java.sql.Date.valueOf(desde));
        q.setParameter("hasta", java.sql.Date.valueOf(hasta));
        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();
        List<Fila> filas = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            filas.add(new Fila(
                    perfil,
                    r[0] != null ? ((Number) r[0]).longValue() : null,
                    r[1] != null ? ((Number) r[1]).longValue() : null,
                    r[2] != null ? r[2].toString() : null,
                    r[3] != null ? ((Number) r[3]).intValue() : 0,
                    r[4] != null ? ((Number) r[4]).intValue() : 0,
                    r[5] != null ? ((Number) r[5]).longValue() : 0L,
                    r[6] != null ? ((Number) r[6]).doubleValue() : 0.0));
        }
        return filas;
    }

    /**
     * Clasifica las filas por composición y arma los totales y desgloses. Sólo las filas con la
     * composición configurada entran en el cálculo.
     */
    static void agregar(SubreporteHuiroPaloDTO dto, List<Fila> filas, Seleccion seleccion, double multiplicador) {
        Set<Long> composiciones = Set.copyOf(seleccion.composiciones());
        long decl = 0;
        double captura = 0;
        long declSinComp = 0;
        double capSinComp = 0;
        long declOtra = 0;
        double capOtra = 0;

        Map<String, Acumulador> porComuna = new LinkedHashMap<>();
        Map<String, Acumulador> porMes = new LinkedHashMap<>();
        Map<String, Acumulador> porPerfil = new LinkedHashMap<>();

        for (Fila f : filas) {
            if (f.composicionId() == null) {
                declSinComp += f.declaraciones();
                capSinComp += f.capturaKg();
                continue;
            }
            if (!composiciones.contains(f.composicionId())) {
                declOtra += f.declaraciones();
                capOtra += f.capturaKg();
                continue;
            }
            decl += f.declaraciones();
            captura += f.capturaKg();

            String claveComuna = f.comunaId() != null ? String.valueOf(f.comunaId()) : "SIN_COMUNA";
            String etiquetaComuna = f.comuna() != null ? f.comuna() : "Sin comuna";
            porComuna.computeIfAbsent(claveComuna, k -> new Acumulador(k, etiquetaComuna)).sumar(f);

            String claveMes = String.format(Locale.ROOT, "%04d-%02d", f.anio(), f.mes());
            porMes.computeIfAbsent(claveMes, k -> new Acumulador(k, etiquetaMes(f.anio(), f.mes()))).sumar(f);

            porPerfil.computeIfAbsent(f.perfil(), k -> new Acumulador(k, etiquetaPerfil(k))).sumar(f);
        }

        double subreporte = captura * multiplicador;
        dto.setConfiguracionValida(true);
        dto.setDeclaraciones(decl);
        dto.setCapturaSinFrondaKg(redondear(captura, 2));
        dto.setSubreporteKg(redondear(subreporte, 2));
        dto.setCapturaEstimadaKg(redondear(captura + subreporte, 2));
        dto.setDeclaracionesSinComposicion(declSinComp);
        dto.setCapturaSinComposicionKg(redondear(capSinComp, 2));
        dto.setDeclaracionesOtraComposicion(declOtra);
        dto.setCapturaOtraComposicionKg(redondear(capOtra, 2));

        dto.setPorComuna(grupos(porComuna, multiplicador, Comparator.comparingDouble(SubreporteHuiroPaloDTO.Grupo::getCapturaSinFrondaKg).reversed()));
        dto.setPorMes(grupos(porMes, multiplicador, Comparator.comparing(SubreporteHuiroPaloDTO.Grupo::getClave)));
        dto.setPorPerfil(grupos(porPerfil, multiplicador, Comparator.comparingDouble(SubreporteHuiroPaloDTO.Grupo::getCapturaSinFrondaKg).reversed()));
        dto.setMensaje(decl == 0
                ? "No hay declaraciones de " + minusculas(dto.getEspecie()) + " " + minusculas(dto.getMetodo())
                        + " " + minusculas(dto.getComposicion()) + " en el rango."
                : null);
    }

    private static List<SubreporteHuiroPaloDTO.Grupo> grupos(Map<String, Acumulador> acc, double multiplicador,
                                                            Comparator<SubreporteHuiroPaloDTO.Grupo> orden) {
        List<SubreporteHuiroPaloDTO.Grupo> out = new ArrayList<>(acc.size());
        for (Acumulador a : acc.values()) {
            out.add(SubreporteHuiroPaloDTO.Grupo.builder()
                    .clave(a.clave)
                    .etiqueta(a.etiqueta)
                    .declaraciones(a.declaraciones)
                    .capturaSinFrondaKg(redondear(a.captura, 2))
                    .subreporteKg(redondear(a.captura * multiplicador, 2))
                    .build());
        }
        out.sort(orden);
        return out;
    }

    private static final class Acumulador {
        final String clave;
        final String etiqueta;
        long declaraciones;
        double captura;

        Acumulador(String clave, String etiqueta) {
            this.clave = clave;
            this.etiqueta = etiqueta;
        }

        void sumar(Fila f) {
            declaraciones += f.declaraciones();
            captura += f.capturaKg();
        }
    }

    // =========================================================================
    // Nombres de los maestros
    // =========================================================================

    /**
     * Ids de los registros cuyo nombre equivale al buscado, sin importar tildes, mayúsculas, espacios
     * ni plural ("SIN FRONDA" = "Sin frondas"). Si no hay equivalentes y {@code permitirParcial}, acepta
     * un único registro que contenga el nombre buscado.
     */
    static <T> List<Long> resolverIds(List<T> items, Function<T, Long> id, Function<T, String> nombre,
                                      String buscado, boolean permitirParcial) {
        String b = singular(normalizar(buscado));
        List<Long> exactos = new ArrayList<>();
        List<Long> parciales = new ArrayList<>();
        if (b.isEmpty() || items == null) {
            return exactos;
        }
        for (T item : items) {
            if (item == null || id.apply(item) == null) continue;
            String n = singular(normalizar(nombre.apply(item)));
            if (n.equals(b)) {
                exactos.add(id.apply(item));
            } else if (permitirParcial && n.contains(b)) {
                parciales.add(id.apply(item));
            }
        }
        if (!exactos.isEmpty()) return exactos;
        if (parciales.size() == 1) return parciales;
        return List.of();
    }

    static String normalizar(String s) {
        if (s == null) return "";
        String sinTildes = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    /** Quita la S final de cada palabra de más de tres letras: "SIN FRONDAS" → "SIN FRONDA". */
    static String singular(String normalizado) {
        if (normalizado.isEmpty()) return normalizado;
        String[] palabras = normalizado.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : palabras) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(w.length() > 3 && w.endsWith("S") ? w.substring(0, w.length() - 1) : w);
        }
        return sb.toString();
    }

    private static final String[] MESES = {"ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"};

    static String etiquetaMes(int anio, int mes) {
        return (mes >= 1 && mes <= 12 ? MESES[mes - 1] : String.valueOf(mes)) + " " + anio;
    }

    static String etiquetaPerfil(String perfil) {
        return switch (perfil) {
            case PERFIL_RECOLECTOR -> "Recolector de orilla";
            case PERFIL_ARMADOR -> "Armador";
            case PERFIL_AREA -> "Área de manejo";
            default -> perfil;
        };
    }

    private static String minusculas(String s) {
        return s != null ? s.trim().toLowerCase(Locale.forLanguageTag("es-CL")) : "";
    }

    static double redondear(double v, int decimales) {
        double f = Math.pow(10, decimales);
        return Math.round(v * f) / f;
    }
}
