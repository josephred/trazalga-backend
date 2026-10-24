package com.trazalga.api.services;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
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
import com.trazalga.api.models.DeclaracionComercializadorModel;
import com.trazalga.api.models.DeclaracionMarcaModel;
import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import com.trazalga.api.repositories.IConsultaFolioLogRepository;
import com.trazalga.api.repositories.IConsultaPatenteLogRepository;
import com.trazalga.api.repositories.IDeclaracionComercializadorRepository;
import com.trazalga.api.repositories.IDeclaracionMarcaRepository;
import com.trazalga.api.repositories.IDeclaracionPlantaAbastecimientoRepository;
import com.trazalga.api.services.EstadoTrasladoPatente.EvaluacionPatente;

/**
 * TN.1, TN.2 y TN.4 · Servicio de consulta de patentes en dos niveles:
 * <ul>
 *   <li>Ciudadano ({@code consultarPublica} / {@code consultar}): respuesta mínima con lista blanca.</li>
 *   <li>Fiscalizador ({@code consultarFiscalizador}): traza exhaustiva y registro en {@code consulta_folio_log}.</li>
 * </ul>
 */
@Service
public class ConsultaPatenteService {

    private static final DateTimeFormatter ISO_SEC = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

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
    private BloqueoCargaService bloqueoCargaService;

    @Autowired(required = false)
    private IDeclaracionMarcaRepository marcaRepository;

    public static String normalizarPatente(String patente) {
        if (patente == null) return "";
        return patente.toUpperCase().replaceAll("[\\s-]", "").trim();
    }

    /**
     * TN.2 · Consulta pública ciudadana en formato DTO de lista blanca.
     */
    @Transactional
    public ConsultaPatentePublicaDTO consultarPublica(String patenteInput, HttpServletRequest request, LocalDateTime ahoraOverride) {
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
            builder.fechaHoraAutorizacion(ev.getFechaHoraAutorizacion() != null
                            ? ev.getFechaHoraAutorizacion().format(ISO_SEC)
                            : null)
                    .comunaDestino(ev.getComunaDestino() != null ? ev.getComunaDestino() : "Comuna no informada")
                    .tipoAlga(ev.getTipoAlga() != null ? ev.getTipoAlga() : "Alga parda");
        }

        registrarLog(patente, request, ev.getEstado() != null ? ev.getEstado() : resultadoPublico);
        return builder.build();
    }

    /**
     * TN.2 · Devuelve exactamente el mapa de lista blanca de {@link ConsultaPatentePublicaDTO}.
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

    private List<MovimientoPatenteDTO> construirMovimientosFiscalizador(String patente, LocalDate desde, LocalDate hasta) {
        if (patente.isEmpty()) {
            return Collections.emptyList();
        }
        List<MovimientoPatenteDTO> lista = new ArrayList<>();

        // 1. Despachos de comercializador con esa patente
        if (comercializadorRepository != null) {
            List<DeclaracionComercializadorModel> despachos = comercializadorRepository.findCandidatosPorPatente(patente);
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

        // 2. Recepciones directas en planta con esa patente que no provengan de un despacho ya listado
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
            Map<Long, MotivoBloqueoDTO> bloq = bloqueoCargaService.bloqueosPorTipoEIds("COMERCIALIZADOR", List.of(dc.getId()));
            if (bloq != null && bloq.containsKey(dc.getId())) {
                estadoMov = "RETENIDO";
                MotivoBloqueoDTO b = bloq.get(dc.getId());
                motivoRet = "Retenido por " + b.getMarca() + " (hallazgo #" + b.getMarcaId() + ")";
            } else if (dc.getDeclaracionesSeleccionadas() != null && !dc.getDeclaracionesSeleccionadas().isBlank()) {
                Map<String, MotivoBloqueoDTO> bCargas = bloqueoCargaService.bloqueosPara(dc.getDeclaracionesSeleccionadas());
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

        if (dc.getDeclaracionDestinatario() != null
                && (dc.getConsumidaPorTipo() == null || "PLANTA_ABASTECIMIENTO".equalsIgnoreCase(dc.getConsumidaPorTipo()))
                && plantaRepository != null) {
            Optional<DeclaracionPlantaAbastecimientoModel> optP = plantaRepository.findById(dc.getDeclaracionDestinatario());
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
                .docTipo(dpa.getDocumentoTributarioOrigenTipo() != null ? dpa.getDocumentoTributarioOrigenTipo() : dpa.getDocumentoTributarioTipo())
                .docNumero(dpa.getDocumentoTributarioOrigenNumero() != null ? dpa.getDocumentoTributarioOrigenNumero() : dpa.getDocumentoTributarioNumero())
                .docFecha(dpa.getDocumentoTributarioOrigenFecha() != null ? dpa.getDocumentoTributarioOrigenFecha() : dpa.getDocumentoTributarioFecha())
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
                .marcas(marcasDto)
                .enlaceFicha("/consultas/ficha/PLANTA_ABASTECIMIENTO/" + dpa.getId())
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
        if (fecha == null) return false;
        LocalDate ld;
        if (fecha instanceof java.sql.Date sqlDate) {
            ld = sqlDate.toLocalDate();
        } else if (fecha instanceof java.sql.Timestamp sqlTs) {
            ld = sqlTs.toLocalDateTime().toLocalDate();
        } else {
            ld = fecha.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        if (desde != null && ld.isBefore(desde)) return false;
        if (hasta != null && ld.isAfter(hasta)) return false;
        return true;
    }

    private EstadoTrasladoPatente obtenerEvaluador() {
        if (estadoTrasladoPatente != null) {
            return estadoTrasladoPatente;
        }
        EstadoTrasladoPatente ev = new EstadoTrasladoPatente();
        ev.setComercializadorRepository(comercializadorRepository);
        ev.setPlantaRepository(plantaRepository);
        ev.setBloqueoCargaService(bloqueoCargaService);
        ev.setConfigService(configService);
        return ev;
    }

    private void registrarFolioLogPatente(String userRut, String patente, int totalResultados) {
        if (folioLogRepository == null) return;
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
        if (logRepository == null) return;
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
