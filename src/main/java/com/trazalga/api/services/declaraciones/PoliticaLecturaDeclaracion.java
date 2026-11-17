package com.trazalga.api.services.declaraciones;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.trazalga.api.models.DeclaracionAreaModel;
import com.trazalga.api.models.DeclaracionArmadorModel;
import com.trazalga.api.models.DeclaracionComercializadorModel;
import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import com.trazalga.api.models.DeclaracionPlantaDestinoModel;
import com.trazalga.api.models.DeclaracionPlantaProduccionModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IDeclaracionAreaRepository;
import com.trazalga.api.repositories.IDeclaracionArmadorRepository;
import com.trazalga.api.repositories.IDeclaracionComercializadorRepository;
import com.trazalga.api.repositories.IDeclaracionPlantaAbastecimientoRepository;
import com.trazalga.api.repositories.IDeclaracionPlantaDestinoRepository;
import com.trazalga.api.repositories.IDeclaracionPlantaProduccionRepository;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;
import com.trazalga.api.repositories.IUsuarioRepository;
import com.trazalga.api.services.ConfiguracionGeneralService;

/**
 * ZS.3 · Control de propiedad/destinatario en lecturas de declaraciones.
 * <ul>
 *   <li>Activable por parámetro {@code declaracion_lectura_exige_dueno} (por defecto {@code true}).</li>
 *   <li>Roles {@code ADMIN}, {@code FISCALIZADOR} y {@code AUDITOR} leen cualquier declaración.</li>
 *   <li>{@code /usuario/{id}} y {@code /usuariodesc/{id}}: sólo el usuario {@code id}.</li>
 *   <li>{@code /usuariosdestinatarios/{id}}: sólo el destinatario {@code id}.</li>
 *   <li>{@code /{id}} y {@code /detalle-consolidado/{id}}: sólo el dueño o el destinatario.</li>
 * </ul>
 */
@Service
public class PoliticaLecturaDeclaracion {

    public static final String PARAM_EXIGE_DUENO = "declaracion_lectura_exige_dueno";

    private static final Set<String> ROLES_FISCALIZACION = Set.of(
            "ROLE_ADMIN", "ROLE_FISCALIZADOR", "ROLE_AUDITOR");

    @Autowired(required = false) private ConfiguracionGeneralService configuracionGeneralService;
    @Autowired(required = false) private IUsuarioRepository usuarioRepository;
    @Autowired(required = false) private IDeclaracionRecolectorRepository recolectorRepository;
    @Autowired(required = false) private IDeclaracionArmadorRepository armadorRepository;
    @Autowired(required = false) private IDeclaracionAreaRepository areaRepository;
    @Autowired(required = false) private IDeclaracionComercializadorRepository comercializadorRepository;
    @Autowired(required = false) private IDeclaracionPlantaAbastecimientoRepository abastecimientoRepository;
    @Autowired(required = false) private IDeclaracionPlantaProduccionRepository produccionRepository;
    @Autowired(required = false) private IDeclaracionPlantaDestinoRepository destinoRepository;

    private record ParticipantesDeclaracion(
            boolean encontrada,
            String rutDueno,
            String rutDestinatario,
            String rutDestinatarioTexto) {
    }

    public boolean exigeDueno() {
        return configuracionGeneralService == null
                || configuracionGeneralService.getBoolean(PARAM_EXIGE_DUENO, true);
    }

    @Transactional(readOnly = true)
    public void verificarUsuario(Long usuarioId) {
        Authentication auth = requerirSesionSiNoEsFiscalizacion();
        if (auth == null) {
            return;
        }
        if (usuarioId == null || !rutSesionCoincideConUsuarioId(auth.getName(), usuarioId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Sólo puede consultar sus propias declaraciones.");
        }
    }

    @Transactional(readOnly = true)
    public void verificarDestinatario(Long usuarioDestinatarioId) {
        Authentication auth = requerirSesionSiNoEsFiscalizacion();
        if (auth == null) {
            return;
        }
        if (usuarioDestinatarioId == null || !rutSesionCoincideConUsuarioId(auth.getName(), usuarioDestinatarioId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Sólo el destinatario asignado puede consultar estas declaraciones.");
        }
    }

    @Transactional(readOnly = true)
    public void verificarDeclaracion(String tipo, Long declaracionId) {
        Authentication auth = requerirSesionSiNoEsFiscalizacion();
        if (auth == null || declaracionId == null) {
            return;
        }
        ParticipantesDeclaracion p = cargarParticipantes(tipo, declaracionId);
        if (!p.encontrada()) {
            return;
        }
        String rutSesion = PoliticaEdicionDeclaracion.normalizarRut(auth.getName());
        if (rutSesion.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado a la declaración.");
        }
        if (coincideRut(rutSesion, p.rutDueno())
                || coincideRut(rutSesion, p.rutDestinatario())
                || coincideRut(rutSesion, p.rutDestinatarioTexto())) {
            return;
        }
        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Sólo el emisor, destinatario o fiscalización pueden ver esta declaración.");
    }

    private Authentication requerirSesionSiNoEsFiscalizacion() {
        if (!exigeDueno()) {
            return null;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean conSesion = auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken)
                && auth.getName() != null && !"anonymousUser".equals(auth.getName());
        if (!conSesion) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Debe iniciar sesión para consultar declaraciones.");
        }
        boolean fiscalizacion = auth.getAuthorities().stream()
                .anyMatch(a -> ROLES_FISCALIZACION.contains(a.getAuthority()));
        if (fiscalizacion) {
            return null;
        }
        return auth;
    }

    private boolean rutSesionCoincideConUsuarioId(String rutSesionRaw, Long usuarioId) {
        if (usuarioRepository == null || rutSesionRaw == null) {
            return false;
        }
        String rutSesion = PoliticaEdicionDeclaracion.normalizarRut(rutSesionRaw);
        Optional<UsuarioModel> uOpt = usuarioRepository.findById(usuarioId);
        if (uOpt.isEmpty()) {
            return false;
        }
        return coincideRut(rutSesion, uOpt.get().getRut());
    }

    private ParticipantesDeclaracion cargarParticipantes(String tipo, Long declaracionId) {
        String t = tipo != null ? tipo.trim().toUpperCase(Locale.ROOT) : "";
        switch (t) {
            case "RECOLECTOR" -> {
                if (recolectorRepository == null) return new ParticipantesDeclaracion(false, null, null, null);
                Optional<DeclaracionRecolectorModel> opt = recolectorRepository.findById(declaracionId);
                if (opt.isEmpty()) return new ParticipantesDeclaracion(false, null, null, null);
                var d = opt.get();
                return new ParticipantesDeclaracion(true,
                        rutDe(d.getUsuario()), rutDe(d.getUsuarioDestinatario()), d.getCodigoDestinatario());
            }
            case "ARMADOR" -> {
                if (armadorRepository == null) return new ParticipantesDeclaracion(false, null, null, null);
                Optional<DeclaracionArmadorModel> opt = armadorRepository.findById(declaracionId);
                if (opt.isEmpty()) return new ParticipantesDeclaracion(false, null, null, null);
                var d = opt.get();
                return new ParticipantesDeclaracion(true,
                        rutDe(d.getUsuario()), rutDe(d.getUsuarioDestinatario()), d.getCodigoDestinatario());
            }
            case "AREA" -> {
                if (areaRepository == null) return new ParticipantesDeclaracion(false, null, null, null);
                Optional<DeclaracionAreaModel> opt = areaRepository.findById(declaracionId);
                if (opt.isEmpty()) return new ParticipantesDeclaracion(false, null, null, null);
                var d = opt.get();
                return new ParticipantesDeclaracion(true,
                        rutDe(d.getUsuario()), rutDe(d.getUsuarioDestinatario()), d.getCodigoDestinatario());
            }
            case "COMERCIALIZADOR" -> {
                if (comercializadorRepository == null) return new ParticipantesDeclaracion(false, null, null, null);
                Optional<DeclaracionComercializadorModel> opt = comercializadorRepository.findById(declaracionId);
                if (opt.isEmpty()) return new ParticipantesDeclaracion(false, null, null, null);
                var d = opt.get();
                return new ParticipantesDeclaracion(true,
                        rutDe(d.getUsuario()), rutDe(d.getUsuarioDestinatario()), d.getCodigoDestinatario());
            }
            case "PLANTA_ABASTECIMIENTO" -> {
                if (abastecimientoRepository == null) return new ParticipantesDeclaracion(false, null, null, null);
                Optional<DeclaracionPlantaAbastecimientoModel> opt = abastecimientoRepository.findById(declaracionId);
                if (opt.isEmpty()) return new ParticipantesDeclaracion(false, null, null, null);
                var d = opt.get();
                return new ParticipantesDeclaracion(true,
                        rutDe(d.getUsuario()), rutDe(d.getUsuarioDestinatario()), null);
            }
            case "PLANTA_PRODUCCION" -> {
                if (produccionRepository == null) return new ParticipantesDeclaracion(false, null, null, null);
                Optional<DeclaracionPlantaProduccionModel> opt = produccionRepository.findById(declaracionId);
                if (opt.isEmpty()) return new ParticipantesDeclaracion(false, null, null, null);
                var d = opt.get();
                return new ParticipantesDeclaracion(true,
                        rutDe(d.getUsuario()), rutDe(d.getUsuarioDestinatario()), null);
            }
            case "PLANTA_DESTINO" -> {
                if (destinoRepository == null) return new ParticipantesDeclaracion(false, null, null, null);
                Optional<DeclaracionPlantaDestinoModel> opt = destinoRepository.findById(declaracionId);
                if (opt.isEmpty()) return new ParticipantesDeclaracion(false, null, null, null);
                var d = opt.get();
                return new ParticipantesDeclaracion(true,
                        rutDe(d.getUsuario()), rutDe(d.getUsuarioDestinatario()), d.getRutDestino());
            }
            default -> throw new IllegalArgumentException("Tipo de declaración no válido: " + tipo);
        }
    }

    private static String rutDe(UsuarioModel usuario) {
        return usuario != null ? usuario.getRut() : null;
    }

    private static boolean coincideRut(String rutSesionNorm, String rutCandidato) {
        if (rutCandidato == null || rutCandidato.isBlank()) {
            return false;
        }
        return rutSesionNorm.equals(PoliticaEdicionDeclaracion.normalizarRut(rutCandidato));
    }
}
