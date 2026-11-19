package com.trazalga.api.services;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDate;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trazalga.api.dto.ConsultaPatenteFiscalizadorDTO;
import com.trazalga.api.dto.ConsultaPatenteFiscalizadorDTO.EstadoActualDTO;
import com.trazalga.api.dto.ConsultaPatenteFiscalizadorDTO.MovimientoPatenteDTO;
import com.trazalga.api.dto.ConsultaPatentePublicaDTO;
import com.trazalga.api.dto.FichaMarcaDTO;
import com.trazalga.api.dto.MotivoBloqueoDTO;
import com.trazalga.api.models.ConsultaFolioLogModel;
import com.trazalga.api.models.ConsultaPatenteLogModel;
import com.trazalga.api.models.DeclaracionAreaModel;
import com.trazalga.api.models.DeclaracionArmadorModel;
import com.trazalga.api.models.DeclaracionComercializadorModel;
import com.trazalga.api.models.DeclaracionMarcaModel;
import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import com.trazalga.api.models.DeclaracionPlantaDestinoModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.repositories.IConsultaFolioLogRepository;
import com.trazalga.api.repositories.IConsultaPatenteLogRepository;
import com.trazalga.api.repositories.IDeclaracionAreaRepository;
import com.trazalga.api.repositories.IDeclaracionArmadorRepository;
import com.trazalga.api.repositories.IDeclaracionComercializadorRepository;
import com.trazalga.api.repositories.IDeclaracionMarcaRepository;
import com.trazalga.api.repositories.IDeclaracionPlantaAbastecimientoRepository;
import com.trazalga.api.repositories.IDeclaracionPlantaDestinoRepository;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;
import com.trazalga.api.services.EstadoTrasladoPatente.EvaluacionPatente;

/**
 * TN.1, TN.2 y TN.4 · Servicio de consulta de patentes en dos niveles:
 * <ul>
 * <li>Ciudadano ({@code consultarPublica} / {@code consultar}): respuesta
 * mínima con lista blanca.</li>
 * <li>Fiscalizador ({@code consultarFiscalizador}): traza exhaustiva y registro
 * en {@code consulta_folio_log}.</li>
 * </ul>
 */
@Service
public class ConsultaPatenteService {

    private static final Logger log = LoggerFactory.getLogger(ConsultaPatenteService.class);

    private static final DateTimeFormatter ISO_SEC = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Autowired(required = false)
    private Environment environment;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired(required = false)
    private ConfiguracionGeneralService configService;

    @Autowired(required = false)
    private IConsultaPatenteLogRepository logRepository;

    @Autowired(required = false)
    private IConsultaFolioLogRepository folioLogRepository;

    @Autowired(required = false)
    private EstadoTrasladoPatente estadoTrasladoPatente;

    @Autowired(required = false)
    private IDeclaracionComercializadorRepository comercializadorRepository;

    @Autowired(required = false)
    private IDeclaracionPlantaAbastecimientoRepository plantaRepository;

    @Autowired(required = false)
    private IDeclaracionPlantaDestinoRepository destinoRepository;

    @Autowired(required = false)
    private IDeclaracionRecolectorRepository recolectorRepository;

    @Autowired(required = false)
    private IDeclaracionArmadorRepository armadorRepository;

    @Autowired(required = false)
    private IDeclaracionAreaRepository areaRepository;

    @Autowired(required = false)
    private BloqueoCargaService bloqueoCargaService;

    @Autowired(required = false)
    private IDeclaracionMarcaRepository marcaRepository;

    @org.springframework.beans.factory.annotation.Value("${trazalga.proxy.confiables:}")
    private String proxiesConfiables = "";

    @org.springframework.beans.factory.annotation.Value("${trazalga.consulta.ip-hash-secret:}")
    private String ipHashSecretProp = "";

    private final byte[] ephemeralHmacKey = generarClaveEfemera();

    private static byte[] generarClaveEfemera() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }

    public void setProxiesConfiables(String proxiesConfiables) {
        this.proxiesConfiables = proxiesConfiables != null ? proxiesConfiables : "";
    }

    public void setIpHashSecret(String ipHashSecret) {
        this.ipHashSecretProp = ipHashSecret != null ? ipHashSecret : "";
    }

    /**
     * YC.1: Advierte al arrancar si falta {@code trazalga.consulta.ip-hash-secret}
     * ({@code TRAZALGA_IP_HASH_SECRET}), y en perfil {@code prod} impide el arranque.
     */
    @PostConstruct
    public void validarConfiguracionIpHashSecret() {
        if (ipHashSecretProp == null || ipHashSecretProp.isBlank()) {
            String mensaje = "trazalga.consulta.ip-hash-secret (TRAZALGA_IP_HASH_SECRET) no configurado: "
                    + "la huella de IP cambia en cada reinicio.";
            if (esPerfilProd()) {
                throw new IllegalStateException(mensaje);
            }
            log.warn(mensaje);
        }
    }

    private boolean esPerfilProd() {
        if (environment == null) {
            return false;
        }
        for (String perfil : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(perfil)) {
                return true;
            }
        }
        return false;
    }

    public static final java.util.regex.Pattern PATENTE_VALIDA = java.util.regex.Pattern.compile("^[A-Z0-9]{5,8}$");

    public static String normalizarPatente(String patente) {
        if (patente == null)
            return "";
        return patente.toUpperCase().replaceAll("[\\s-]", "").trim();
    }

    public static boolean esPatenteValida(String patenteNormalizada) {
        return patenteNormalizada != null && PATENTE_VALIDA.matcher(patenteNormalizada).matches();
    }

    /**
     * TN.2 · Consulta pública ciudadana en formato DTO de lista blanca.
     */
    @Transactional
    public ConsultaPatentePublicaDTO consultarPublica(String patenteInput, HttpServletRequest request,
            LocalDateTime ahoraOverride) {
        String patente = normalizarPatente(patenteInput);
        LocalDateTime ahora = ahoraOverride != null ? ahoraOverride : LocalDateTime.now();

        String textoProteccion = configService != null
                ? configService.getValor("consulta_publica_texto_proteccion", "")
                : "";
        String grafica = configService != null
                ? configService.getValor("consulta_publica_grafica", "")
                : "";

        EstadoTrasladoPatente evalService = obtenerEvaluador();
        EvaluacionPatente ev = evalService != null
                ? evalService.evaluar(patente, ahora)
                : EvaluacionPatente.builder()
                        .patente(patente)
                        .estado(EstadoTrasladoPatente.ESTADO_SIN_REGISTROS)
                        .resultadoPublico(EstadoTrasladoPatente.RESULTADO_PUBLICO_SIN_AUTORIZACION)
                        .consultadoEn(ahora)
                        .build();

        String resultadoPublico = ev.getResultadoPublico() != null
                ? ev.getResultadoPublico()
                : EstadoTrasladoPatente.RESULTADO_PUBLICO_SIN_AUTORIZACION;

        ConsultaPatentePublicaDTO.ConsultaPatentePublicaDTOBuilder builder = ConsultaPatentePublicaDTO.builder()
                .patente(patente)
                .resultado(resultadoPublico)
                .texto(textoProteccion != null ? textoProteccion : "")
                .grafica(grafica != null && !grafica.isBlank() ? grafica : null)
                .consultadoEn(ahora.format(ISO_SEC));

        if (EstadoTrasladoPatente.RESULTADO_PUBLICO_AUTORIZADO.equals(resultadoPublico)) {
            boolean muestraComuna = true;
            if (configService != null) {
                String valComuna = configService.getValor("consulta_publica_muestra_comuna", "true");
                if (valComuna != null && !valComuna.isBlank()) {
                    muestraComuna = Boolean.parseBoolean(valComuna.trim());
                }
            }
            builder.fechaHoraAutorizacion(ev.getFechaHoraAutorizacion() != null
                    ? ev.getFechaHoraAutorizacion().format(ISO_SEC)
                    : null)
                    .tipoAlga(ev.getTipoAlga() != null ? ev.getTipoAlga() : "Alga parda");
            if (muestraComuna && ev.getComunaDestino() != null) {
                builder.comunaDestino(ev.getComunaDestino());
            }
        } else if (EstadoTrasladoPatente.RESULTADO_PUBLICO_SIN_AUTORIZACION.equals(resultadoPublico)) {
            String fonoDenuncia = configService != null
                    ? configService.getValor("consulta_publica_fono_denuncia", "")
                    : "";
            String whatsappDenuncia = configService != null
                    ? configService.getValor("consulta_publica_whatsapp", "")
                    : "";
            if (fonoDenuncia != null && !fonoDenuncia.isBlank()) {
                builder.fonoDenuncia(fonoDenuncia.trim());
            }
            if (whatsappDenuncia != null && !whatsappDenuncia.isBlank()) {
                builder.whatsappDenuncia(whatsappDenuncia.trim());
            }
        }

        registrarLog(patente, request, ev.getEstado() != null ? ev.getEstado() : resultadoPublico);
        return builder.build();
    }

    /**
     * ZC.5 (D54): Registra intención anónima de denuncia (FONO o WHATSAPP) en
     * consulta_patente_log
     * cuando consulta_publica_registra_denuncia está activo. Sin datos del
     * ciudadano.
     */
    @Transactional(readOnly = false)
    public boolean registrarIntencionDenuncia(String patenteNorm, String canal, HttpServletRequest request) {
        if (!esPatenteValida(patenteNorm)) {
            return false;
        }
        boolean activo = configService == null || configService.getBoolean("consulta_publica_registra_denuncia", true);
        if (!activo) {
            return false;
        }
        String resultado = "FONO".equalsIgnoreCase(canal) ? "DENUNCIA_FONO" : "DENUNCIA_WHATSAPP";
        registrarLog(patenteNorm, request, resultado);
        return true;
    }

    /**
     * ZC.5: Purga diaria de registros en consulta_patente_log más antiguos que
     * consulta_patente_log_retencion_dias (vacío o <= 0 no borra).
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 30 3 * * *", zone = "America/Santiago")
    @Transactional(readOnly = false)
    public int depurarLogsAntiguos() {
        return depurarLogsAntiguos(new Date());
    }

    @Transactional(readOnly = false)
    public int depurarLogsAntiguos(Date ahoraReferencia) {
        if (logRepository == null) {
            return 0;
        }
        int dias = 365;
        if (configService != null) {
            String raw = configService.getValor("consulta_patente_log_retencion_dias", "365");
            if (raw == null || raw.trim().isEmpty()) {
                return 0;
            }
            try {
                dias = Integer.parseInt(raw.trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        if (dias <= 0) {
            return 0;
        }
        Date ref = ahoraReferencia != null ? ahoraReferencia : new Date();
        Date cutoff = Date.from(ref.toInstant().minus(java.time.Duration.ofDays(dias)));
        return logRepository.deleteOlderThan(cutoff);
    }

    /**
     * TN.2 · Devuelve exactamente el mapa de lista blanca de
     * {@link ConsultaPatentePublicaDTO}.
     */
    @Transactional
    public Map<String, Object> consultar(String patenteInput, HttpServletRequest request) {
        return consultar(patenteInput, request, null);
    }

    @Transactional
    public Map<String, Object> consultar(String patenteInput, HttpServletRequest request, LocalDateTime ahoraOverride) {
        return consultarPublica(patenteInput, request, ahoraOverride).toMap();
    }

    /**
     * TN.4 · Consulta exhaustiva para fiscalizadores, administradores y auditores.
     * Registra {@code PATENTE:<patente>} en {@code consulta_folio_log}.
     */
    @Transactional
    public ConsultaPatenteFiscalizadorDTO consultarFiscalizador(
            String patenteInput,
            LocalDate desde,
            LocalDate hasta,
            String userRut,
            LocalDateTime ahoraOverride) {

        String patente = normalizarPatente(patenteInput);
        LocalDateTime ahora = ahoraOverride != null ? ahoraOverride : LocalDateTime.now();

        int diasDefecto = configService != null
                ? configService.getInt("consulta_patente_fiscalizador_dias", 7)
                : 7;

        LocalDate fechaHasta = hasta != null ? hasta : ahora.toLocalDate();
        LocalDate fechaDesde = desde != null ? desde : fechaHasta.minusDays(diasDefecto);

        EstadoTrasladoPatente evalService = obtenerEvaluador();
        EvaluacionPatente ev = evalService != null
                ? evalService.evaluar(patente, ahora)
                : EvaluacionPatente.builder()
                        .patente(patente)
                        .estado(EstadoTrasladoPatente.ESTADO_SIN_REGISTROS)
                        .consultadoEn(ahora)
                        .build();

        EstadoActualDTO estadoActual = EstadoActualDTO.builder()
                .estado(ev.getEstado())
                .fechaHoraAutorizacion(ev.getFechaHoraAutorizacion())
                .fechaHoraSalida(ev.getFechaHoraSalida())
                .comunaDestino(ev.getComunaDestino())
                .tipoAlga(ev.getTipoAlga())
                .declaracionId(ev.getDeclaracionId())
                .folioDespacho(ev.getFolioDespacho())
                .motivoRetencion(ev.getMotivoRetencion())
                .hallazgoId(ev.getHallazgoId())
                .hallazgoMarca(ev.getHallazgoMarca())
                .plantaRecepcionId(ev.getPlantaRecepcionId())
                .nombrePlantaRecepcion(ev.getNombrePlantaRecepcion())
                .fechaHoraRecepcion(ev.getFechaHoraRecepcion())
                .consultadoEn(ev.getConsultadoEn() != null ? ev.getConsultadoEn() : ahora)
                .build();

        List<MovimientoPatenteDTO> movimientos = construirMovimientosFiscalizador(patente, fechaDesde, fechaHasta);

        registrarFolioLogPatente(userRut, patente, movimientos.size());

        return ConsultaPatenteFiscalizadorDTO.builder()
                .patente(patente)
                .estadoActual(estadoActual)
                .vehiculo(null) // Enriquecido en TT cuando el vehículo existe en la nómina
                .movimientos(movimientos)
                .build();
    }

    private List<MovimientoPatenteDTO> construirMovimientosFiscalizador(String patente, LocalDate desde,
            LocalDate hasta) {
        if (patente.isEmpty()) {
            return Collections.emptyList();
        }
        List<MovimientoPatenteDTO> lista = new ArrayList<>();

        // 1. Despachos de comercializador con esa patente
        if (comercializadorRepository != null) {
            List<DeclaracionComercializadorModel> despachos = comercializadorRepository
                    .findCandidatosPorPatente(patente);
            if (despachos != null) {
                for (DeclaracionComercializadorModel dc : despachos) {
                    Date fBase = dc.getFechaTraslado() != null ? dc.getFechaTraslado() : dc.getFechaDeclaracion();
                    if (!enRango(fBase, desde, hasta) && !enRango(dc.getFechaDeclaracion(), desde, hasta)) {
                        continue;
                    }
                    lista.add(construirMovimientoDesdeComercializador(dc));
                }
            }
        }

        // 2. Recepciones directas en planta con esa patente que no provengan de un
        // despacho ya listado
        if (plantaRepository != null) {
            Set<Long> plantasYaListadas = new HashSet<>();
            for (MovimientoPatenteDTO m : lista) {
                if (m.getRecepcionPlantaId() != null) {
                    plantasYaListadas.add(m.getRecepcionPlantaId());
                }
            }
            for (DeclaracionPlantaAbastecimientoModel dpa : plantaRepository.findAll()) {
                if (dpa.getId() != null && plantasYaListadas.contains(dpa.getId())) {
                    continue;
                }
                if (dpa.getEstado() != null) {
                    String est = dpa.getEstado().trim().toUpperCase();
                    if ("RECHAZADA".equals(est) || "ANULADA".equals(est)) {
                        continue;
                    }
                }
                boolean matchPatente = patente.equals(normalizarPatente(dpa.getPlacaPatente()))
                        || patente.equals(normalizarPatente(dpa.getPlacaPatenteCarro()))
                        || patente.equals(normalizarPatente(dpa.getPatente()));
                if (!matchPatente) {
                    continue;
                }
                Date fBase = dpa.getFechaIngresoPlanta() != null ? dpa.getFechaIngresoPlanta() : dpa.getFechaTraslado();
                if (!enRango(fBase, desde, hasta)) {
                    continue;
                }
                lista.add(construirMovimientoDesdePlanta(dpa));
            }
        }

        // 3. Despachos de producto terminado de planta (PLANTA_DESTINO, ZC.3 / D52)
        boolean incluyeDestinoPlanta = configService == null
                || configService.getBoolean("patente_incluye_despacho_planta", true);
        if (incluyeDestinoPlanta && destinoRepository != null) {
            List<DeclaracionPlantaDestinoModel> destinos = destinoRepository.findCandidatosPorPatente(patente);
            if (destinos != null) {
                for (DeclaracionPlantaDestinoModel dpd : destinos) {
                    Date fBase = dpd.getFechaTrasladoDestino() != null
                            ? dpd.getFechaTrasladoDestino()
                            : (dpd.getFechaTraslado() != null ? dpd.getFechaTraslado()
                                    : dpd.getFechaDeclaracionDestino());
                    if (!enRango(fBase, desde, hasta) && !enRango(dpd.getFechaDeclaracionDestino(), desde, hasta)) {
                        continue;
                    }
                    lista.add(construirMovimientoDesdePlantaDestino(dpd));
                }
            }
        }

        // 4. Traslados directos de origen a planta con patente declarada (ZC.9 / D53)
        if (recolectorRepository != null) {
            List<DeclaracionRecolectorModel> recs = recolectorRepository.findCandidatosPorPatente(patente);
            if (recs != null) {
                for (DeclaracionRecolectorModel r : recs) {
                    if (!EstadoTrasladoPatente.esDestinoDirectoPlanta(
                            r.getTipoDestinatario(), r.getConsumidaPorTipo(), r.getUsuarioDestinatario(), null)
                            || !enRango(r.getFechaDeclaracion(), desde, hasta)) {
                        continue;
                    }
                    String folio = r.getFolioDesembarqueRo() != null ? r.getFolioDesembarqueRo() : r.getFolioOrigen();
                    lista.add(construirMovimientoOrigenDirecto(
                            "RECOLECTOR", r.getId(), r.getFolioOrigen(), folio,
                            r.getFechaDeclaracion(), r.getHora(), r.getUsuario(), r.getUsuarioDestinatario(),
                            r.getCodigoDestinatario(), r.getNombreDestinatario(), r.getEspecie(),
                            r.getHumedadEstado(), r.getDesembarque(), r.getPlacaPatente(),
                            r.getPlacaPatenteCarro(), r.getDeclaracionDestinatario(), r.getConsumidaPorTipo()));
                }
            }
        }
        if (armadorRepository != null) {
            List<DeclaracionArmadorModel> arms = armadorRepository.findCandidatosPorPatente(patente);
            if (arms != null) {
                for (DeclaracionArmadorModel a : arms) {
                    if (!EstadoTrasladoPatente.esDestinoDirectoPlanta(
                            a.getTipoDestinatario(), a.getConsumidaPorTipo(), a.getUsuarioDestinatario(), null)
                            || !enRango(a.getFechaDeclaracion(), desde, hasta)) {
                        continue;
                    }
                    String folio = a.getFolioDesembarqueDa() != null ? a.getFolioDesembarqueDa() : a.getFolioOrigen();
                    lista.add(construirMovimientoOrigenDirecto(
                            "ARMADOR", a.getId(), a.getFolioOrigen(), folio,
                            a.getFechaDeclaracion(), a.getHora(), a.getUsuario(), a.getUsuarioDestinatario(),
                            a.getCodigoDestinatario(), null, a.getEspecie(),
                            a.getHumedadEstado(), a.getDesembarque(), a.getPlacaPatente(),
                            a.getPlacaPatenteCarro(), a.getDeclaracionDestinatario(), a.getConsumidaPorTipo()));
                }
            }
        }
        if (areaRepository != null) {
            List<DeclaracionAreaModel> areas = areaRepository.findCandidatosPorPatente(patente);
            if (areas != null) {
                for (DeclaracionAreaModel ar : areas) {
                    if (!EstadoTrasladoPatente.esDestinoDirectoPlanta(
                            ar.getTipoDestinatario(), ar.getConsumidaPorTipo(), ar.getUsuarioDestinatario(), null)
                            || !enRango(ar.getFechaDeclaracion(), desde, hasta)) {
                        continue;
                    }
                    String folio = ar.getFolioDesembarqueAmerb() != null ? ar.getFolioDesembarqueAmerb()
                            : ar.getFolioOrigen();
                    BigDecimal kilos = ar.getDesembarque() != null ? BigDecimal.valueOf(ar.getDesembarque()) : null;
                    lista.add(construirMovimientoOrigenDirecto(
                            "AREA", ar.getId(), ar.getFolioOrigen(), folio,
                            ar.getFechaDeclaracion(), ar.getHora(), ar.getUsuario(), ar.getUsuarioDestinatario(),
                            ar.getCodigoDestinatario(), null, ar.getEspecie(),
                            ar.getHumedadEstado(), kilos, ar.getPlacaPatente(),
                            ar.getPlacaPatenteCarro(), ar.getDeclaracionDestinatario(), ar.getConsumidaPorTipo()));
                }
            }
        }

        lista.sort((a, b) -> {
            LocalDateTime ta = a.getFechaHoraSalida() != null ? a.getFechaHoraSalida() : LocalDateTime.MIN;
            LocalDateTime tb = b.getFechaHoraSalida() != null ? b.getFechaHoraSalida() : LocalDateTime.MIN;
            return tb.compareTo(ta);
        });

        return lista;
    }

    private MovimientoPatenteDTO construirMovimientoDesdeComercializador(DeclaracionComercializadorModel dc) {
        Date fSalida = dc.getFechaTraslado() != null ? dc.getFechaTraslado() : dc.getFechaDeclaracion();
        LocalDateTime tsSalida = EstadoTrasladoPatente.toTimestamp(fSalida, dc.getHora());

        String comRut = dc.getUsuario() != null ? dc.getUsuario().getRut() : null;
        String comNom = dc.getNombreComercializador();
        if ((comNom == null || comNom.isBlank()) && dc.getUsuario() != null) {
            String nom = dc.getUsuario().getNombres() != null ? dc.getUsuario().getNombres() : "";
            String apP = dc.getUsuario().getApellidop() != null ? dc.getUsuario().getApellidop() : "";
            String apM = dc.getUsuario().getApellidom() != null ? dc.getUsuario().getApellidom() : "";
            comNom = (nom + " " + apP + " " + apM).trim().replaceAll(" +", " ");
        }

        String patCamion = dc.getPlacaPatente() != null && !dc.getPlacaPatente().isBlank()
                ? normalizarPatente(dc.getPlacaPatente())
                : normalizarPatente(dc.getPatente());
        String patCarro = normalizarPatente(dc.getPlacaPatenteCarro());

        // Estado del movimiento: RETENIDO, RECIBIDO o EN_TRANSITO
        String estadoMov = "EN_TRANSITO";
        String motivoRet = null;
        if (bloqueoCargaService != null && dc.getId() != null) {
            Map<Long, MotivoBloqueoDTO> bloq = bloqueoCargaService.bloqueosPorTipoEIds("COMERCIALIZADOR",
                    List.of(dc.getId()));
            if (bloq != null && bloq.containsKey(dc.getId())) {
                estadoMov = "RETENIDO";
                MotivoBloqueoDTO b = bloq.get(dc.getId());
                motivoRet = "Retenido por " + b.getMarca() + " (hallazgo #" + b.getMarcaId() + ")";
            } else if (dc.getDeclaracionesSeleccionadas() != null && !dc.getDeclaracionesSeleccionadas().isBlank()) {
                Map<String, MotivoBloqueoDTO> bCargas = bloqueoCargaService
                        .bloqueosPara(dc.getDeclaracionesSeleccionadas());
                if (bCargas != null && !bCargas.isEmpty()) {
                    estadoMov = "RETENIDO";
                    MotivoBloqueoDTO b = bCargas.values().iterator().next();
                    motivoRet = "Retenido por " + b.getMarca() + " en origen (hallazgo #" + b.getMarcaId() + ")";
                }
            }
        }

        Long recPlantaId = null;
        Date recFecha = null;
        String recHora = null;
        String recPlantaNom = null;
        BigDecimal recRomanaKg = null;
        String recVoucher = null;
        String recLoteInterno = null;
        String recLugarAcopio = null;

        if (dc.getDeclaracionDestinatario() != null
                && (dc.getConsumidaPorTipo() == null
                        || "PLANTA_ABASTECIMIENTO".equalsIgnoreCase(dc.getConsumidaPorTipo()))
                && plantaRepository != null) {
            Optional<DeclaracionPlantaAbastecimientoModel> optP = plantaRepository
                    .findById(dc.getDeclaracionDestinatario());
            if (optP.isPresent()) {
                DeclaracionPlantaAbastecimientoModel p = optP.get();
                if (!"RETENIDO".equals(estadoMov)) {
                    estadoMov = "RECIBIDO";
                }
                recPlantaId = p.getId();
                recFecha = p.getFechaIngresoPlanta();
                recHora = p.getHora();
                recPlantaNom = p.getNombrePlanta() != null ? p.getNombrePlanta()
                        : (p.getUsuario() != null ? p.getUsuario().getNombres() : "Planta");
                recRomanaKg = p.getPesoRomanaKg();
                recVoucher = p.getVoucherRomanaNumero();
                recLoteInterno = p.getLoteInterno();
                recLugarAcopio = p.getLugarAcopio();
            }
        }

        List<FichaMarcaDTO> marcasDto = obtenerMarcas("COMERCIALIZADOR", dc.getId());

        return MovimientoPatenteDTO.builder()
                .tipo("COMERCIALIZADOR")
                .id(dc.getId())
                .folioOrigen(dc.getFolioOrigen())
                .folioSecundario(dc.getFolioDesembarqueAc())
                .fechaDeclaracion(dc.getFechaDeclaracion())
                .horaDeclaracion(dc.getHora())
                .fechaTraslado(dc.getFechaTraslado())
                .fechaHoraSalida(tsSalida)
                .recibidaEn(dc.getRecibidaEn())
                .comercializadorRut(comRut)
                .comercializadorNombre(comNom)
                .choferDeclarado(dc.getChoferTransporte())
                .choferRut(dc.getRutChofer())
                .patenteCamion(patCamion)
                .patenteCarro(patCarro.isEmpty() ? null : patCarro)
                .tipoVehiculo(dc.getVehiculoTransporte())
                .docTipo(dc.getDocumentoTributarioOrigenTipo())
                .docNumero(dc.getDocumentoTributarioOrigenNumero())
                .docFecha(dc.getDocumentoTributarioOrigenFecha())
                .destinoNombre(dc.getNombreDestinatario())
                .destinoRut(dc.getCodigoDestinatario())
                .destinoComuna(EstadoTrasladoPatente.resolverComunaDestino(dc))
                .especies(EstadoTrasladoPatente.resolverTipoAlga(dc))
                .estadoHumedad(dc.getHumedadEstado() != null ? dc.getHumedadEstado().getNombre() : null)
                .kilos(dc.getCantidad())
                .estadoMovimiento(estadoMov)
                .motivoRetencion(motivoRet)
                .recepcionPlantaId(recPlantaId)
                .recepcionFecha(recFecha)
                .recepcionHora(recHora)
                .recepcionPlantaNombre(recPlantaNom)
                .recepcionPesoRomanaKg(recRomanaKg)
                .recepcionVoucherRomana(recVoucher)
                .recepcionLoteInterno(recLoteInterno)
                .recepcionLugarAcopio(recLugarAcopio)
                .marcas(marcasDto)
                .enlaceFicha("/consultas/ficha/COMERCIALIZADOR/" + dc.getId())
                .build();
    }

    private MovimientoPatenteDTO construirMovimientoDesdePlanta(DeclaracionPlantaAbastecimientoModel dpa) {
        Date fSalida = dpa.getFechaTraslado() != null ? dpa.getFechaTraslado() : dpa.getFechaIngresoPlanta();
        LocalDateTime tsSalida = EstadoTrasladoPatente.toTimestamp(fSalida, dpa.getHora());

        String patCamion = dpa.getPlacaPatente() != null && !dpa.getPlacaPatente().isBlank()
                ? normalizarPatente(dpa.getPlacaPatente())
                : normalizarPatente(dpa.getPatente());
        String patCarro = normalizarPatente(dpa.getPlacaPatenteCarro());

        String plantaNom = dpa.getNombrePlanta() != null ? dpa.getNombrePlanta()
                : (dpa.getUsuario() != null ? dpa.getUsuario().getNombres() : "Planta");
        String plantaRut = dpa.getUsuario() != null ? dpa.getUsuario().getRut() : null;
        String plantaComuna = (dpa.getUsuario() != null && dpa.getUsuario().getComuna() != null)
                ? dpa.getUsuario().getComuna().getNombre()
                : "Comuna no informada";

        List<FichaMarcaDTO> marcasDto = obtenerMarcas("PLANTA_ABASTECIMIENTO", dpa.getId());

        return MovimientoPatenteDTO.builder()
                .tipo("PLANTA_ABASTECIMIENTO")
                .id(dpa.getId())
                .folioOrigen(dpa.getFolioOrigen())
                .folioSecundario(dpa.getFolioDeclaracionAPla())
                .fechaDeclaracion(dpa.getFechaIngresoPlanta())
                .horaDeclaracion(dpa.getHora())
                .fechaTraslado(dpa.getFechaTraslado())
                .fechaHoraSalida(tsSalida)
                .choferDeclarado(dpa.getChoferTransporte())
                .choferRut(dpa.getRutChofer())
                .patenteCamion(patCamion)
                .patenteCarro(patCarro.isEmpty() ? null : patCarro)
                .tipoVehiculo(dpa.getVehiculoTransporte())
                .docTipo(dpa.getDocumentoTributarioOrigenTipo() != null ? dpa.getDocumentoTributarioOrigenTipo()
                        : dpa.getDocumentoTributarioTipo())
                .docNumero(dpa.getDocumentoTributarioOrigenNumero() != null ? dpa.getDocumentoTributarioOrigenNumero()
                        : dpa.getDocumentoTributarioNumero())
                .docFecha(dpa.getDocumentoTributarioOrigenFecha() != null ? dpa.getDocumentoTributarioOrigenFecha()
                        : dpa.getDocumentoTributarioFecha())
                .destinoNombre(plantaNom)
                .destinoRut(plantaRut)
                .destinoComuna(plantaComuna)
                .especies(dpa.getEspecie() != null ? dpa.getEspecie().getNombre() : "Alga parda")
                .estadoHumedad(dpa.getHumedadEstadoRecepcion() != null ? dpa.getHumedadEstadoRecepcion().getNombre()
                        : (dpa.getHumedadEstado() != null ? dpa.getHumedadEstado().getNombre() : null))
                .kilos(dpa.getPesoRomanaKg() != null ? dpa.getPesoRomanaKg() : dpa.getCantidad())
                .estadoMovimiento("RECIBIDO")
                .recepcionPlantaId(dpa.getId())
                .recepcionFecha(dpa.getFechaIngresoPlanta())
                .recepcionHora(dpa.getHora())
                .recepcionPlantaNombre(plantaNom)
                .recepcionPesoRomanaKg(dpa.getPesoRomanaKg())
                .recepcionVoucherRomana(dpa.getVoucherRomanaNumero())
                .recepcionLoteInterno(dpa.getLoteInterno())
                .recepcionLugarAcopio(dpa.getLugarAcopio())
                .marcas(marcasDto)
                .enlaceFicha("/consultas/ficha/PLANTA_ABASTECIMIENTO/" + dpa.getId())
                .build();
    }

    private MovimientoPatenteDTO construirMovimientoDesdePlantaDestino(DeclaracionPlantaDestinoModel dpd) {
        Date fSalida = dpd.getFechaTrasladoDestino() != null
                ? dpd.getFechaTrasladoDestino()
                : (dpd.getFechaTraslado() != null ? dpd.getFechaTraslado() : dpd.getFechaDeclaracionDestino());
        LocalDateTime tsSalida = EstadoTrasladoPatente.toTimestamp(fSalida, dpd.getHora());

        String plantaNom = dpd.getNombrePlanta() != null ? dpd.getNombrePlanta()
                : (dpd.getUsuario() != null ? dpd.getUsuario().getNombres() : "Planta");
        String plantaRut = dpd.getUsuario() != null ? dpd.getUsuario().getRut() : null;

        String estadoMov = "EN_TRANSITO";
        String motivoRet = null;
        if (bloqueoCargaService != null && dpd.getDeclaracionesSeleccionadas() != null
                && !dpd.getDeclaracionesSeleccionadas().isBlank()) {
            Map<String, MotivoBloqueoDTO> bProds = bloqueoCargaService
                    .bloqueosPara(dpd.getDeclaracionesSeleccionadas());
            if (bProds != null && !bProds.isEmpty()) {
                estadoMov = "RETENIDO";
                MotivoBloqueoDTO b = bProds.values().iterator().next();
                motivoRet = "Retenido por " + b.getMarca() + " en producción (hallazgo #" + b.getMarcaId() + ")";
            }
        }

        List<FichaMarcaDTO> marcasDto = obtenerMarcas("PLANTA_DESTINO", dpd.getId());

        return MovimientoPatenteDTO.builder()
                .tipo("PLANTA_DESTINO")
                .id(dpd.getId())
                .folioOrigen(dpd.getFolioOrigen())
                .folioSecundario(dpd.getFolioDeclaracionDestino())
                .fechaDeclaracion(dpd.getFechaDeclaracionDestino())
                .horaDeclaracion(dpd.getHora())
                .fechaTraslado(fSalida)
                .fechaHoraSalida(tsSalida)
                .comercializadorRut(plantaRut)
                .comercializadorNombre(plantaNom)
                .choferDeclarado(dpd.getChoferTransporte())
                .choferRut(dpd.getRutChofer())
                .patenteCamion(normalizarPatente(dpd.getPlacaPatente()))
                .patenteCarro(dpd.getPlacaPatenteCarro() != null && !dpd.getPlacaPatenteCarro().isBlank()
                        ? normalizarPatente(dpd.getPlacaPatenteCarro())
                        : null)
                .tipoVehiculo(dpd.getVehiculoTransporte())
                .docTipo(dpd.getDocumentoTributarioTipo())
                .docNumero(dpd.getDocumentoTributarioNumero())
                .docFecha(dpd.getDocumentoTributarioFecha())
                .destinoNombre(dpd.getNombreDestino())
                .destinoRut(dpd.getRutDestino())
                .especies(dpd.getEspecie() != null ? dpd.getEspecie().getNombre() : "Alga parda")
                .kilos(dpd.getCantidad())
                .estadoMovimiento(estadoMov)
                .motivoRetencion(motivoRet)
                .marcas(marcasDto)
                .enlaceFicha("/consultas/ficha/PLANTA_DESTINO/" + dpd.getId())
                .build();
    }

    private MovimientoPatenteDTO construirMovimientoOrigenDirecto(
            String tipo, Long id, String folioOrigen, String folioSecundario,
            Date fechaDeclaracion, String hora,
            com.trazalga.api.models.UsuarioModel emisor,
            com.trazalga.api.models.UsuarioModel destinatario,
            String destRutFallback, String destNomFallback,
            com.trazalga.api.models.EspecieModel especie,
            com.trazalga.api.models.HumedadEstadoModel humedad,
            BigDecimal kilos, String placaPatente, String placaPatenteCarro,
            Long declaracionDestinatario, String consumidaPorTipo) {
        LocalDateTime tsSalida = EstadoTrasladoPatente.toTimestamp(fechaDeclaracion, hora);
        String emisorRut = emisor != null ? emisor.getRut() : null;
        String emisorNom = emisor != null ? emisor.getNombreCompleto() : tipo;
        String destNom = destNomFallback;
        String destRut = destRutFallback;
        String destComuna = "Comuna no informada";
        if (destinatario != null) {
            if (destNom == null || destNom.isBlank())
                destNom = destinatario.getNombreCompleto();
            if (destRut == null || destRut.isBlank())
                destRut = destinatario.getRut();
            if (destinatario.getComuna() != null && destinatario.getComuna().getNombre() != null) {
                destComuna = destinatario.getComuna().getNombre();
            }
        }

        String estadoMov = "EN_TRANSITO";
        String motivoRet = null;
        if (bloqueoCargaService != null && id != null) {
            Map<Long, MotivoBloqueoDTO> bloq = bloqueoCargaService.bloqueosPorTipoEIds(tipo, List.of(id));
            if (bloq != null && bloq.containsKey(id)) {
                estadoMov = "RETENIDO";
                MotivoBloqueoDTO b = bloq.get(id);
                motivoRet = "Retenido por " + b.getMarca() + " (hallazgo #" + b.getMarcaId() + ")";
            }
        }

        Long recPlantaId = null;
        Date recFecha = null;
        String recHora = null;
        String recPlantaNom = null;
        if (declaracionDestinatario != null
                && (consumidaPorTipo == null || "PLANTA_ABASTECIMIENTO".equalsIgnoreCase(consumidaPorTipo))
                && plantaRepository != null) {
            Optional<DeclaracionPlantaAbastecimientoModel> optP = plantaRepository.findById(declaracionDestinatario);
            if (optP.isPresent()) {
                DeclaracionPlantaAbastecimientoModel p = optP.get();
                if (!"RETENIDO".equals(estadoMov))
                    estadoMov = "RECIBIDO";
                recPlantaId = p.getId();
                recFecha = p.getFechaIngresoPlanta();
                recHora = p.getHora();
                recPlantaNom = p.getNombrePlanta() != null ? p.getNombrePlanta()
                        : (p.getUsuario() != null ? p.getUsuario().getNombres() : "Planta");
            }
        }

        return MovimientoPatenteDTO.builder()
                .tipo(tipo)
                .id(id)
                .folioOrigen(folioOrigen)
                .folioSecundario(folioSecundario)
                .fechaDeclaracion(fechaDeclaracion)
                .horaDeclaracion(hora)
                .fechaTraslado(fechaDeclaracion)
                .fechaHoraSalida(tsSalida)
                .comercializadorRut(emisorRut)
                .comercializadorNombre(emisorNom)
                .patenteCamion(normalizarPatente(placaPatente))
                .patenteCarro(placaPatenteCarro != null && !placaPatenteCarro.isBlank()
                        ? normalizarPatente(placaPatenteCarro)
                        : null)
                .destinoNombre(destNom)
                .destinoRut(destRut)
                .destinoComuna(destComuna)
                .especies(especie != null ? especie.getNombre() : "Alga parda")
                .estadoHumedad(humedad != null ? humedad.getNombre() : null)
                .kilos(kilos)
                .estadoMovimiento(estadoMov)
                .motivoRetencion(motivoRet)
                .recepcionPlantaId(recPlantaId)
                .recepcionFecha(recFecha)
                .recepcionHora(recHora)
                .recepcionPlantaNombre(recPlantaNom)
                .marcas(obtenerMarcas(tipo, id))
                .enlaceFicha("/consultas/ficha/" + tipo + "/" + id)
                .build();
    }

    private List<FichaMarcaDTO> obtenerMarcas(String tipo, Long id) {
        if (marcaRepository == null || id == null) {
            return Collections.emptyList();
        }
        List<DeclaracionMarcaModel> list = marcaRepository.findByDeclaracionTipoAndDeclaracionId(tipo, id);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        List<FichaMarcaDTO> out = new ArrayList<>();
        for (DeclaracionMarcaModel m : list) {
            out.add(FichaMarcaDTO.builder()
                    .id(m.getId())
                    .marca(m.getMarca())
                    .detalle(m.getDetalle())
                    .resuelta(Boolean.TRUE.equals(m.getResuelta()))
                    .estadoGestion(m.getEstadoGestion())
                    .resolucionTipo(m.getResolucionTipo())
                    .fechaDeteccion(m.getCreatedAt())
                    .build());
        }
        return out;
    }

    private boolean enRango(Date fecha, LocalDate desde, LocalDate hasta) {
        if (fecha == null)
            return false;
        LocalDate ld;
        if (fecha instanceof java.sql.Date sqlDate) {
            ld = sqlDate.toLocalDate();
        } else if (fecha instanceof java.sql.Timestamp sqlTs) {
            ld = sqlTs.toLocalDateTime().toLocalDate();
        } else {
            ld = fecha.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        if (desde != null && ld.isBefore(desde))
            return false;
        if (hasta != null && ld.isAfter(hasta))
            return false;
        return true;
    }

    private EstadoTrasladoPatente obtenerEvaluador() {
        if (estadoTrasladoPatente != null) {
            return estadoTrasladoPatente;
        }
        EstadoTrasladoPatente ev = new EstadoTrasladoPatente();
        ev.setComercializadorRepository(comercializadorRepository);
        ev.setPlantaRepository(plantaRepository);
        ev.setDestinoRepository(destinoRepository);
        ev.setRecolectorRepository(recolectorRepository);
        ev.setArmadorRepository(armadorRepository);
        ev.setAreaRepository(areaRepository);
        ev.setBloqueoCargaService(bloqueoCargaService);
        ev.setConfigService(configService);
        return ev;
    }

    private void registrarFolioLogPatente(String userRut, String patente, int totalResultados) {
        if (folioLogRepository == null)
            return;
        try {
            ConsultaFolioLogModel log = ConsultaFolioLogModel.builder()
                    .usuarioRut(userRut != null && !userRut.isBlank() ? userRut : "ANONIMO")
                    .textoBuscado("PATENTE:" + patente)
                    .cantidadResultados(totalResultados)
                    .fecha(new Date())
                    .build();
            folioLogRepository.save(log);
        } catch (Exception ignored) {
        }
    }

    private void registrarLog(String patente, HttpServletRequest request, String resultado) {
        if (logRepository == null)
            return;
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
            System.err.println("Advertencia al registrar log de consulta patente: " + e.getMessage());
        }
    }

    public String getClientIp(HttpServletRequest request) {
        return com.trazalga.api.security.RateLimitFilter.resolverIpCliente(request, proxiesConfiables);
    }

    public String hashIp(String ip) {
        if (ip == null || ip.isBlank())
            return "ANONYMOUS";
        try {
            byte[] keyBytes = (ipHashSecretProp != null && !ipHashSecretProp.isBlank())
                    ? ipHashSecretProp.getBytes(StandardCharsets.UTF_8)
                    : ephemeralHmacKey;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
            byte[] hash = mac.doFinal(ip.getBytes(StandardCharsets.UTF_8));
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
