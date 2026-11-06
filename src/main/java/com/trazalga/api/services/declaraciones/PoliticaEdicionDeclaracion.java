package com.trazalga.api.services.declaraciones;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.trazalga.api.dto.AlertaDeclaracionDTO;
import com.trazalga.api.exceptions.DeclaracionCongeladaException;
import com.trazalga.api.models.DeclaracionAreaModel;
import com.trazalga.api.models.DeclaracionArmadorModel;
import com.trazalga.api.models.DeclaracionAutorizacionModel;
import com.trazalga.api.models.DeclaracionMarcaModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IDeclaracionAreaRepository;
import com.trazalga.api.repositories.IDeclaracionArmadorRepository;
import com.trazalga.api.repositories.IDeclaracionAutorizacionRepository;
import com.trazalga.api.repositories.IDeclaracionMarcaRepository;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;
import com.trazalga.api.services.ConfiguracionGeneralService;

/**
 * Congelamiento de declaraciones de origen con alerta de fiscalización (refinamiento 30-10, punto 3).
 * <p>
 * Si una declaración de recolector, armador o área tiene una marca de {@code declaracion_congelar_marcas}
 * (por defecto EN_VEDA, CUOTA_EXCEDIDA y POSTERIOR_CIERRE), su dueño no puede modificarla ni eliminarla:
 * <ul>
 *   <li>modificarla requiere una autorización de edición vigente de un fiscalizador, que se usa una vez;</li>
 *   <li>eliminarla sólo lo hace un fiscalizador, con motivo y acta ({@link AutorizacionDeclaracionService}).</li>
 * </ul>
 * Resolver la marca en la Consola de Hallazgos no descongela la declaración.
 * <p>
 * Además, {@code declaracion_escritura_exige_sesion} exige sesión para modificar o eliminar. Si la petición
 * trae sesión, el usuario debe ser el dueño de la declaración o administrador.
 */
@Service
public class PoliticaEdicionDeclaracion {

    public static final String PARAM_ACTIVO = "declaracion_congelar_activo";
    public static final String PARAM_MARCAS = "declaracion_congelar_marcas";
    public static final String PARAM_HORAS = "declaracion_autorizacion_horas";
    public static final String PARAM_EXIGE_SESION = "declaracion_escritura_exige_sesion";

    public static final String MARCAS_POR_DEFECTO = "EN_VEDA,CUOTA_EXCEDIDA,POSTERIOR_CIERRE";

    public static final Set<String> TIPOS = Set.of("RECOLECTOR", "ARMADOR", "AREA");

    @Autowired private IDeclaracionMarcaRepository marcaRepository;
    @Autowired private IDeclaracionAutorizacionRepository autorizacionRepository;
    @Autowired private ConfiguracionGeneralService configuracionGeneralService;
    @Autowired private IDeclaracionRecolectorRepository recolectorRepository;
    @Autowired private IDeclaracionArmadorRepository armadorRepository;
    @Autowired private IDeclaracionAreaRepository areaRepository;

    /**
     * Estado de edición de una declaración.
     *
     * @param congelada           tiene alguna marca congelante y no hay autorización de edición vigente.
     * @param motivo              texto para el usuario cuando está congelada; null si no.
     * @param alertas             todas las marcas de la declaración, para mostrarlas.
     * @param marcasCongelantes   las marcas que la congelan.
     * @param autorizacionVigente hay una autorización de edición sin usar y sin vencer.
     */
    public record EstadoEdicion(boolean congelada, String motivo, List<AlertaDeclaracionDTO> alertas,
                                List<AlertaDeclaracionDTO> marcasCongelantes, boolean autorizacionVigente) {
        public static EstadoEdicion libre(List<AlertaDeclaracionDTO> alertas) {
            return new EstadoEdicion(false, null, alertas, List.of(), false);
        }
    }

    // ------------------------------------------------------------------ parámetros

    public boolean activo() {
        return configuracionGeneralService.getBoolean(PARAM_ACTIVO, true);
    }

    public Set<String> marcasCongelantes() {
        String valor = configuracionGeneralService.getValor(PARAM_MARCAS, MARCAS_POR_DEFECTO);
        Set<String> out = new LinkedHashSet<>();
        for (String s : (valor != null ? valor : "").split(",")) {
            String m = s.trim().toUpperCase(Locale.ROOT);
            if (!m.isEmpty()) out.add(m);
        }
        return out;
    }

    public int horasAutorizacion() {
        int h = configuracionGeneralService.getInt(PARAM_HORAS, 48);
        return h > 0 ? h : 48;
    }

    public boolean exigeSesion() {
        return configuracionGeneralService.getBoolean(PARAM_EXIGE_SESION, false);
    }

    // ------------------------------------------------------------------ evaluación

    @Transactional(readOnly = true)
    public EstadoEdicion evaluar(String tipo, Long declaracionId) {
        if (declaracionId == null) {
            return EstadoEdicion.libre(List.of());
        }
        return evaluarLote(tipo, List.of(declaracionId)).getOrDefault(declaracionId, EstadoEdicion.libre(List.of()));
    }

    @Transactional(readOnly = true)
    public Map<Long, EstadoEdicion> evaluarLote(String tipo, Collection<Long> ids) {
        Map<Long, EstadoEdicion> out = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        String t = normalizarTipo(tipo);
        boolean activo = activo();
        Set<String> congelantes = marcasCongelantes();

        Map<Long, List<DeclaracionMarcaModel>> marcasPorId = new HashMap<>();
        for (DeclaracionMarcaModel m : marcaRepository.findByDeclaracionTipoAndDeclaracionIdIn(t, ids)) {
            marcasPorId.computeIfAbsent(m.getDeclaracionId(), k -> new ArrayList<>()).add(m);
        }
        Date ahora = new Date();
        for (Long id : ids) {
            List<DeclaracionMarcaModel> marcas = marcasPorId.getOrDefault(id, List.of());
            List<AlertaDeclaracionDTO> alertas = new ArrayList<>();
            List<AlertaDeclaracionDTO> criticas = new ArrayList<>();
            for (DeclaracionMarcaModel m : marcas) {
                AlertaDeclaracionDTO a = new AlertaDeclaracionDTO(m.getMarca(), m.getDetalle());
                alertas.add(a);
                if (m.getMarca() != null && congelantes.contains(m.getMarca().toUpperCase(Locale.ROOT))) {
                    criticas.add(a);
                }
            }
            if (!activo || criticas.isEmpty()) {
                out.put(id, EstadoEdicion.libre(alertas));
                continue;
            }
            boolean vigente = autorizacionEdicionVigente(t, id, ahora).isPresent();
            out.put(id, new EstadoEdicion(!vigente, vigente ? null : motivo(criticas), alertas, criticas, vigente));
        }
        return out;
    }

    // ------------------------------------------------------------------ verificaciones

    /**
     * Antes de modificar: si la declaración está congelada y no hay autorización de edición vigente,
     * lanza {@link DeclaracionCongeladaException} (423).
     *
     * @return la autorización que habilita esta edición, para marcarla usada después de guardar.
     */
    @Transactional(readOnly = true)
    public Optional<DeclaracionAutorizacionModel> verificarEdicion(String tipo, Long declaracionId) {
        EstadoEdicion estado = evaluar(tipo, declaracionId);
        if (estado.congelada()) {
            throw new DeclaracionCongeladaException(normalizarTipo(tipo), declaracionId,
                    estado.motivo(), estado.marcasCongelantes());
        }
        if (estado.autorizacionVigente()) {
            return autorizacionEdicionVigente(normalizarTipo(tipo), declaracionId, new Date());
        }
        return Optional.empty();
    }

    /** Marca usada la autorización de edición con que se modificó la declaración. */
    @Transactional
    public void registrarUsoAutorizacion(Optional<DeclaracionAutorizacionModel> autorizacion) {
        autorizacion.ifPresent(a -> {
            a.setUsadaEn(new Date());
            autorizacionRepository.save(a);
        });
    }

    /**
     * Antes de eliminar por la vía del dueño: una declaración con marca congelante no se elimina nunca
     * por esta vía, aunque tenga una autorización de edición. La elimina un fiscalizador con
     * {@link AutorizacionDeclaracionService#eliminarConAutorizacion}.
     */
    @Transactional(readOnly = true)
    public void verificarEliminacion(String tipo, Long declaracionId) {
        EstadoEdicion estado = evaluar(tipo, declaracionId);
        if (!estado.marcasCongelantes().isEmpty()) {
            throw new DeclaracionCongeladaException(normalizarTipo(tipo), declaracionId,
                    "Declaración con alerta de fiscalización (" + codigos(estado.marcasCongelantes())
                            + "): no se puede eliminar. Si hay un error, contacte a Sernapesca.",
                    estado.marcasCongelantes());
        }
    }

    /**
     * Sesión y dueño, para las rutas de modificación y eliminación.
     * <ul>
     *   <li>Sin sesión: se rechaza (401) sólo si {@code declaracion_escritura_exige_sesion} está activo,
     *       para no cortar la app 2.4.0, que borra sin token.</li>
     *   <li>Con sesión: el RUT de la sesión debe ser el del dueño, salvo administrador (403).</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public void verificarSesionYDueno(String tipo, Long declaracionId) {
        String t = normalizarTipo(tipo);
        UsuarioModel duenio = switch (t) {
            case "RECOLECTOR" -> recolectorRepository.findById(declaracionId).map(DeclaracionRecolectorModel::getUsuario).orElse(null);
            case "ARMADOR" -> armadorRepository.findById(declaracionId).map(DeclaracionArmadorModel::getUsuario).orElse(null);
            default -> areaRepository.findById(declaracionId).map(DeclaracionAreaModel::getUsuario).orElse(null);
        };
        boolean existe = switch (t) {
            case "RECOLECTOR" -> recolectorRepository.existsById(declaracionId);
            case "ARMADOR" -> armadorRepository.existsById(declaracionId);
            default -> areaRepository.existsById(declaracionId);
        };
        if (!existe) {
            return; // el servicio responde como siempre ante una declaración inexistente
        }
        // Dentro de la transacción: el usuario es un proxy LAZY y aquí se puede leer su RUT
        verificarSesionYDueno(duenio != null ? duenio.getRut() : null);
    }

    void verificarSesionYDueno(String rutDuenio) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean conSesion = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)
                && auth.getName() != null && !"anonymousUser".equals(auth.getName());
        if (!conSesion) {
            if (exigeSesion()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Debe iniciar sesión para modificar o eliminar declaraciones.");
            }
            return;
        }
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (admin) {
            return;
        }
        if (rutDuenio == null || !normalizarRut(rutDuenio).equals(normalizarRut(auth.getName()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sólo el usuario que emitió la declaración puede modificarla o eliminarla.");
        }
    }

    // ------------------------------------------------------------------ anotación de respuestas

    /** Llena alertas y estado de edición en una declaración de recolector (respuesta de POST, PUT o GET). */
    public DeclaracionRecolectorModel anotar(DeclaracionRecolectorModel d) {
        if (d != null) anotarLote("RECOLECTOR", List.of(d));
        return d;
    }

    public DeclaracionArmadorModel anotar(DeclaracionArmadorModel d) {
        if (d != null) anotarLote("ARMADOR", List.of(d));
        return d;
    }

    public DeclaracionAreaModel anotar(DeclaracionAreaModel d) {
        if (d != null) anotarLote("AREA", List.of(d));
        return d;
    }

    /** Llena alertas y estado de edición en un listado, con una sola consulta de marcas. */
    @Transactional(readOnly = true)
    public <T, L extends List<T>> L anotarLote(String tipo, L lista) {
        if (lista == null || lista.isEmpty()) {
            return lista;
        }
        String t = normalizarTipo(tipo);
        Function<Object, Long> id = o -> o instanceof DeclaracionRecolectorModel r ? r.getId()
                : o instanceof DeclaracionArmadorModel a ? a.getId()
                : o instanceof DeclaracionAreaModel m ? m.getId() : null;
        List<Long> ids = new ArrayList<>();
        for (T o : lista) {
            Long i = id.apply(o);
            if (i != null) ids.add(i);
        }
        Map<Long, EstadoEdicion> estados = evaluarLote(t, ids);
        for (T o : lista) {
            Long i = id.apply(o);
            EstadoEdicion e = i != null ? estados.getOrDefault(i, EstadoEdicion.libre(List.of())) : EstadoEdicion.libre(List.of());
            if (o instanceof DeclaracionRecolectorModel r) {
                aplicar(e, r::setAlertas, r::setEdicionBloqueada, r::setMotivoEdicionBloqueada);
            } else if (o instanceof DeclaracionArmadorModel a) {
                aplicar(e, a::setAlertas, a::setEdicionBloqueada, a::setMotivoEdicionBloqueada);
            } else if (o instanceof DeclaracionAreaModel m) {
                aplicar(e, m::setAlertas, m::setEdicionBloqueada, m::setMotivoEdicionBloqueada);
            }
        }
        return lista;
    }

    private static void aplicar(EstadoEdicion e, Consumer<List<AlertaDeclaracionDTO>> alertas,
                                Consumer<Boolean> bloqueada, Consumer<String> motivo) {
        alertas.accept(e.alertas());
        bloqueada.accept(e.congelada());
        motivo.accept(e.motivo());
    }

    // ------------------------------------------------------------------ utilidades

    Optional<DeclaracionAutorizacionModel> autorizacionEdicionVigente(String tipo, Long id, Date ahora) {
        return autorizacionRepository
                .findByDeclaracionTipoAndDeclaracionIdAndTipoAndUsadaEnIsNull(tipo, id, DeclaracionAutorizacionModel.TIPO_EDICION)
                .stream()
                .filter(a -> a.getVenceEn() == null || a.getVenceEn().after(ahora))
                .findFirst();
    }

    public static String normalizarTipo(String tipo) {
        String t = tipo != null ? tipo.trim().toUpperCase(Locale.ROOT) : "";
        if (t.equals("ÁREA") || t.equals("AMERB")) {
            t = "AREA";
        }
        if (!TIPOS.contains(t)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de declaración no válido: " + tipo);
        }
        return t;
    }

    static String normalizarRut(String rut) {
        return rut == null ? "" : rut.replace(".", "").replace(" ", "").toUpperCase(Locale.ROOT);
    }

    private static String motivo(List<AlertaDeclaracionDTO> criticas) {
        return "Declaración con alerta de fiscalización (" + codigos(criticas)
                + "): no se puede modificar ni eliminar. Si hay un error, contacte a Sernapesca.";
    }

    private static String codigos(List<AlertaDeclaracionDTO> marcas) {
        Set<String> c = new LinkedHashSet<>();
        for (AlertaDeclaracionDTO a : marcas) c.add(a.marca());
        return String.join(", ", c);
    }
}
