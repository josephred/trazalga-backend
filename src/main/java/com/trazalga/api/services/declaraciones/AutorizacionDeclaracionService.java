package com.trazalga.api.services.declaraciones;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trazalga.api.models.DeclaracionAreaModel;
import com.trazalga.api.models.DeclaracionArmadorModel;
import com.trazalga.api.models.DeclaracionAutorizacionModel;
import com.trazalga.api.models.DeclaracionBuzosModel;
import com.trazalga.api.models.DeclaracionMarcaModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IDeclaracionAreaRepository;
import com.trazalga.api.repositories.IDeclaracionArmadorRepository;
import com.trazalga.api.repositories.IDeclaracionAutorizacionRepository;
import com.trazalga.api.repositories.DeclaracionBuzosRepository;
import com.trazalga.api.repositories.IDeclaracionMarcaRepository;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;
import com.trazalga.api.repositories.IUsuarioRepository;
import com.trazalga.api.services.CuotaExtraccionService;

import lombok.extern.slf4j.Slf4j;

/**
 * Procedimiento del fiscalizador sobre declaraciones congeladas (refinamiento 30-10, punto 3).
 * <ul>
 *   <li><b>Autorizar edición:</b> con motivo y n.º de acta de verificación física, el dueño puede
 *       modificar la declaración una vez dentro de la ventana ({@code declaracion_autorizacion_horas}).</li>
 *   <li><b>Eliminar:</b> con motivo y acta, el fiscalizador elimina la declaración. Antes se guarda en
 *       {@code declaracion_autorizacion.snapshot} la declaración y sus marcas en JSON.</li>
 * </ul>
 */
@Service
@Slf4j
public class AutorizacionDeclaracionService {

    @Autowired private PoliticaEdicionDeclaracion politica;
    @Autowired private IDeclaracionAutorizacionRepository autorizacionRepository;
    @Autowired private IDeclaracionMarcaRepository marcaRepository;
    @Autowired private IDeclaracionRecolectorRepository recolectorRepository;
    @Autowired private IDeclaracionArmadorRepository armadorRepository;
    @Autowired private IDeclaracionAreaRepository areaRepository;
    @Autowired private DeclaracionBuzosRepository buzosRepository;
    @Autowired private IUsuarioRepository usuarioRepository;
    @Autowired private ObjectMapper objectMapper;

    @Autowired(required = false)
    @Lazy
    private CuotaExtraccionService cuotaExtraccionService;

    /** Estado de edición e historial de autorizaciones (sin el respaldo JSON). */
    @Transactional(readOnly = true)
    public Map<String, Object> estado(String tipo, Long id) {
        String t = PoliticaEdicionDeclaracion.normalizarTipo(tipo);
        cargarDeclaracion(t, id);
        PoliticaEdicionDeclaracion.EstadoEdicion e = politica.evaluar(t, id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("declaracionTipo", t);
        out.put("declaracionId", id);
        out.put("congelada", e.congelada());
        out.put("motivo", e.motivo());
        out.put("marcasCongelantes", e.marcasCongelantes());
        out.put("autorizacionEdicionVigente", e.autorizacionVigente());
        out.put("alertas", e.alertas());
        out.put("autorizaciones", autorizacionRepository.findByDeclaracionTipoAndDeclaracionIdOrderByAutorizadoEnDesc(t, id)
                .stream().map(a -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", a.getId());
                    m.put("tipo", a.getTipo());
                    m.put("motivo", a.getMotivo());
                    m.put("actaVerificacion", a.getActaVerificacion());
                    m.put("autorizadoPorRut", a.getAutorizadoPorRut());
                    m.put("autorizadoEn", a.getAutorizadoEn());
                    m.put("venceEn", a.getVenceEn());
                    m.put("usadaEn", a.getUsadaEn());
                    return m;
                }).toList());
        return out;
    }

    /** El fiscalizador autoriza una edición del dueño, una vez y por un tiempo. */
    @Transactional
    public DeclaracionAutorizacionModel autorizarEdicion(String tipo, Long id, String motivo, String acta, Integer horas) {
        String t = PoliticaEdicionDeclaracion.normalizarTipo(tipo);
        cargarDeclaracion(t, id);
        validarTextos(motivo, acta);
        PoliticaEdicionDeclaracion.EstadoEdicion e = politica.evaluar(t, id);
        if (e.marcasCongelantes().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La declaración no tiene alertas que la congelen: su dueño puede modificarla sin autorización.");
        }
        int h = (horas != null && horas > 0 && horas <= 720) ? horas : politica.horasAutorizacion();
        Date ahora = new Date();
        UsuarioModel fiscalizador = usuarioSesion();
        DeclaracionAutorizacionModel a = DeclaracionAutorizacionModel.builder()
                .declaracionTipo(t)
                .declaracionId(id)
                .tipo(DeclaracionAutorizacionModel.TIPO_EDICION)
                .motivo(motivo.trim())
                .actaVerificacion(acta.trim())
                .autorizadoPorUsuarioId(fiscalizador != null ? fiscalizador.getId() : null)
                .autorizadoPorRut(rutSesion())
                .autorizadoEn(ahora)
                .venceEn(new Date(ahora.getTime() + h * 3_600_000L))
                .build();
        return autorizacionRepository.save(a);
    }

    /**
     * El fiscalizador elimina una declaración con alerta. Guarda antes un respaldo JSON de la declaración
     * y sus marcas, y después borra marcas, buzos y declaración.
     */
    @Transactional
    public DeclaracionAutorizacionModel eliminarConAutorizacion(String tipo, Long id, String motivo, String acta) {
        String t = PoliticaEdicionDeclaracion.normalizarTipo(tipo);
        Object declaracion = cargarDeclaracion(t, id);
        validarTextos(motivo, acta);
        if (consumida(declaracion)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La declaración ya fue recibida por un destinatario. Primero hay que corregir el documento que la recibió.");
        }
        List<DeclaracionMarcaModel> marcas = marcaRepository.findByDeclaracionTipoAndDeclaracionId(t, id);

        Map<String, Object> respaldo = new LinkedHashMap<>();
        respaldo.put("declaracionTipo", t);
        respaldo.put("declaracion", declaracion);
        respaldo.put("marcas", marcas);
        String snapshot;
        try {
            snapshot = objectMapper.writeValueAsString(respaldo);
        } catch (Exception ex) {
            log.error("No se pudo respaldar la declaración {} {} antes de eliminarla", t, id, ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo guardar el respaldo de la declaración; no se eliminó.");
        }

        UsuarioModel fiscalizador = usuarioSesion();
        DeclaracionAutorizacionModel a = autorizacionRepository.save(DeclaracionAutorizacionModel.builder()
                .declaracionTipo(t)
                .declaracionId(id)
                .tipo(DeclaracionAutorizacionModel.TIPO_ELIMINACION)
                .motivo(motivo.trim())
                .actaVerificacion(acta.trim())
                .autorizadoPorUsuarioId(fiscalizador != null ? fiscalizador.getId() : null)
                .autorizadoPorRut(rutSesion())
                .autorizadoEn(new Date())
                .usadaEn(new Date())
                .snapshot(snapshot)
                .build());

        marcaRepository.deleteAll(marcas);
        switch (t) {
            case "RECOLECTOR" -> {
                List<DeclaracionBuzosModel> b = buzosRepository.findByDeclaracionRecolectorId(id);
                buzosRepository.deleteAll(b);
                recolectorRepository.deleteById(id);
            }
            case "ARMADOR" -> {
                List<DeclaracionBuzosModel> b = buzosRepository.findByDeclaracionArmadorId(id);
                buzosRepository.deleteAll(b);
                armadorRepository.deleteById(id);
            }
            default -> {
                List<DeclaracionBuzosModel> b = buzosRepository.findByDeclaracionAreaId(id);
                buzosRepository.deleteAll(b);
                areaRepository.deleteById(id);
            }
        }
        if (cuotaExtraccionService != null) {
            cuotaExtraccionService.invalidarCacheConsumo();
        }
        log.info("Declaración {} {} eliminada por fiscalización (acta {}, autorización {})", t, id, acta.trim(), a.getId());
        return a;
    }

    // ------------------------------------------------------------------

    private Object cargarDeclaracion(String tipo, Long id) {
        Object d = switch (tipo) {
            case "RECOLECTOR" -> recolectorRepository.findById(id).orElse(null);
            case "ARMADOR" -> armadorRepository.findById(id).orElse(null);
            default -> areaRepository.findById(id).orElse(null);
        };
        if (d == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe la declaración " + tipo + " " + id + ".");
        }
        return d;
    }

    private static boolean consumida(Object d) {
        if (d instanceof DeclaracionRecolectorModel r) return r.getDeclaracionDestinatario() != null;
        if (d instanceof DeclaracionArmadorModel a) return a.getDeclaracionDestinatario() != null;
        if (d instanceof DeclaracionAreaModel a) return a.getDeclaracionDestinatario() != null;
        return false;
    }

    private static void validarTextos(String motivo, String acta) {
        if (motivo == null || motivo.trim().length() < 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique el motivo (al menos 5 caracteres).");
        }
        if (motivo.trim().length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El motivo no puede superar 500 caracteres.");
        }
        if (acta == null || acta.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique el número o la referencia del acta de verificación física.");
        }
        if (acta.trim().length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La referencia del acta no puede superar 120 caracteres.");
        }
    }

    private static String rutSesion() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    private UsuarioModel usuarioSesion() {
        String rut = rutSesion();
        return rut != null ? usuarioRepository.findByRut(rut).orElse(null) : null;
    }
}
