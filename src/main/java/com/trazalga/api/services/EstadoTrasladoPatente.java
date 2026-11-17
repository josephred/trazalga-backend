package com.trazalga.api.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trazalga.api.dto.MotivoBloqueoDTO;
import com.trazalga.api.exceptions.ValidacionPlantaException;
import com.trazalga.api.models.DeclaracionAreaModel;
import com.trazalga.api.models.DeclaracionArmadorModel;
import com.trazalga.api.models.DeclaracionComercializadorModel;
import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import com.trazalga.api.models.DeclaracionPlantaDestinoModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IDeclaracionAreaRepository;
import com.trazalga.api.repositories.IDeclaracionArmadorRepository;
import com.trazalga.api.repositories.IDeclaracionComercializadorRepository;
import com.trazalga.api.repositories.IDeclaracionPlantaAbastecimientoRepository;
import com.trazalga.api.repositories.IDeclaracionPlantaDestinoRepository;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;
import com.trazalga.api.repositories.IUsuarioRepository;
import org.hibernate.Hibernate;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;

/**
 * TN.1 · Definición única del estado de un traslado por patente de camión o
 * rampla.
 * La usan tanto la consulta ciudadana
 * ({@code GET /api/public/patentes/{patente}})
 * como la consulta de fiscalizador
 * ({@code GET /api/consultas/patentes/{patente}}).
 *
 * <p>
 * Estados posibles:
 * </p>
 * <ul>
 * <li>{@code AUTORIZADO}: despacho vigente, sin bloqueo, no recibido en planta
 * (si termina en recepción),
 * y {@code ahora} entre la declaración y la salida +
 * {@code patente_vigencia_horas} (48).</li>
 * <li>{@code RETENIDO}: el despacho o sus cargas tienen una marca bloqueante
 * sin liberar ({@link BloqueoCargaService}).</li>
 * <li>{@code FINALIZADO}: la planta ya lo recibió y
 * {@code patente_vigencia_termina_en_recepcion = true}.</li>
 * <li>{@code VENCIDO}: pasó la ventana de vigencia desde la salida.</li>
 * <li>{@code SIN_REGISTROS}: no hay candidatos vigentes no futuros.</li>
 * </ul>
 */
@Service
public class EstadoTrasladoPatente {

    public static final String ESTADO_AUTORIZADO = "AUTORIZADO";
    public static final String ESTADO_RETENIDO = "RETENIDO";
    public static final String ESTADO_FINALIZADO = "FINALIZADO";
    public static final String ESTADO_VENCIDO = "VENCIDO";
    public static final String ESTADO_SIN_REGISTROS = "SIN_REGISTROS";

    public static final String RESULTADO_PUBLICO_AUTORIZADO = "AUTORIZADO";
    public static final String RESULTADO_PUBLICO_SIN_AUTORIZACION = "SIN_AUTORIZACION_VIGENTE";

    private static final ObjectMapper JSON = new ObjectMapper();

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
    private ConfiguracionGeneralService configService;

    public void setComercializadorRepository(IDeclaracionComercializadorRepository comercializadorRepository) {
        this.comercializadorRepository = comercializadorRepository;
    }

    public void setPlantaRepository(IDeclaracionPlantaAbastecimientoRepository plantaRepository) {
        this.plantaRepository = plantaRepository;
    }

    public void setDestinoRepository(IDeclaracionPlantaDestinoRepository destinoRepository) {
        this.destinoRepository = destinoRepository;
    }

    public void setRecolectorRepository(IDeclaracionRecolectorRepository recolectorRepository) {
        this.recolectorRepository = recolectorRepository;
    }

    public void setArmadorRepository(IDeclaracionArmadorRepository armadorRepository) {
        this.armadorRepository = armadorRepository;
    }

    public void setAreaRepository(IDeclaracionAreaRepository areaRepository) {
        this.areaRepository = areaRepository;
    }

    public void setBloqueoCargaService(BloqueoCargaService bloqueoCargaService) {
        this.bloqueoCargaService = bloqueoCargaService;
    }

    public void setConfigService(ConfiguracionGeneralService configService) {
        this.configService = configService;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvaluacionPatente {
        private String patente;
        private String estado; // AUTORIZADO, RETENIDO, FINALIZADO, VENCIDO, SIN_REGISTROS
        private String resultadoPublico; // AUTORIZADO o SIN_AUTORIZACION_VIGENTE
        private LocalDateTime fechaHoraAutorizacion;
        private LocalDateTime fechaHoraSalida;
        private String comunaDestino;
        private String tipoAlga;
        private Long declaracionId;
        private String folioDespacho;
        private String motivoRetencion;
        private Long hallazgoId;
        private String hallazgoMarca;
        private Long plantaRecepcionId;
        private String nombrePlantaRecepcion;
        private LocalDateTime fechaHoraRecepcion;
        private LocalDateTime consultadoEn;
    }

    public EvaluacionPatente evaluar(String patenteInput, LocalDateTime ahora) {
        String patente = ConsultaPatenteService.normalizarPatente(patenteInput);
        LocalDateTime momentoConsulta = ahora != null ? ahora : LocalDateTime.now();

        if (patente.isEmpty() || (comercializadorRepository == null && destinoRepository == null
                && recolectorRepository == null && armadorRepository == null && areaRepository == null)) {
            return EvaluacionPatente.builder()
                    .patente(patente)
                    .estado(ESTADO_SIN_REGISTROS)
                    .resultadoPublico(RESULTADO_PUBLICO_SIN_AUTORIZACION)
                    .consultadoEn(momentoConsulta)
                    .build();
        }

        int vigenciaHoras = configService != null ? configService.getInt("patente_vigencia_horas", 48) : 48;
        boolean terminaEnRecepcion = configService == null
                || configService.getBoolean("patente_vigencia_termina_en_recepcion", true);
        boolean incluyeDespachoPlanta = configService == null
                || configService.getBoolean("patente_incluye_despacho_planta", true);

        record OrigenDirectoCandidate(
                String tipo, Long id, String folio, Date fechaDeclaracion, String hora,
                UsuarioModel destinatario, EspecieModel especie, Long declaracionDestinatario,
                String consumidaPorTipo) {
        }

        record CandidatoMovimiento(
                LocalDateTime tsAutorizacion,
                long id,
                String tipo,
                DeclaracionComercializadorModel com,
                DeclaracionPlantaDestinoModel destino,
                OrigenDirectoCandidate origen) {
        }

        List<CandidatoMovimiento> candidatosValidos = new ArrayList<>();

        if (comercializadorRepository != null) {
            List<DeclaracionComercializadorModel> candidatosCom = comercializadorRepository
                    .findCandidatosPorPatente(patente);
            if (candidatosCom != null) {
                for (DeclaracionComercializadorModel dc : candidatosCom) {
                    if (!coincidePatente(dc, patente) || estaAnuladoORechazado(dc.getEstado())) {
                        continue;
                    }
                    LocalDateTime tsDecl = toTimestamp(dc.getFechaDeclaracion(), dc.getHora());
                    if (tsDecl == null || tsDecl.isAfter(momentoConsulta)) {
                        continue;
                    }
                    long id = dc.getId() != null ? dc.getId() : 0L;
                    candidatosValidos.add(new CandidatoMovimiento(tsDecl, id, "COMERCIALIZADOR", dc, null, null));
                }
            }
        }

        if (incluyeDespachoPlanta && destinoRepository != null) {
            List<DeclaracionPlantaDestinoModel> candidatosDest = destinoRepository.findCandidatosPorPatente(patente);
            if (candidatosDest != null) {
                for (DeclaracionPlantaDestinoModel dpd : candidatosDest) {
                    if (!coincidePatenteCampos(dpd.getPlacaPatente(), dpd.getPlacaPatenteCarro(), null, patente)
                            || estaAnuladoORechazado(dpd.getEstado())) {
                        continue;
                    }
                    LocalDateTime tsDecl = toTimestamp(dpd.getFechaDeclaracionDestino(), dpd.getHora());
                    if (tsDecl == null || tsDecl.isAfter(momentoConsulta)) {
                        continue;
                    }
                    long id = dpd.getId() != null ? dpd.getId() : 0L;
                    candidatosValidos.add(new CandidatoMovimiento(tsDecl, id, "PLANTA_DESTINO", null, dpd, null));
                }
            }
        }

        if (recolectorRepository != null) {
            List<DeclaracionRecolectorModel> recs = recolectorRepository.findCandidatosPorPatente(patente);
            if (recs != null) {
                for (DeclaracionRecolectorModel r : recs) {
                    if (!coincidePatenteCampos(r.getPlacaPatente(), r.getPlacaPatenteCarro(), null, patente)
                            || estaAnuladoORechazado(r.getEstado())
                            || !esDestinoDirectoPlanta(r.getTipoDestinatario(), r.getConsumidaPorTipo(),
                                    r.getUsuarioDestinatario(), null)) {
                        continue;
                    }
                    LocalDateTime tsDecl = toTimestamp(r.getFechaDeclaracion(), r.getHora());
                    if (tsDecl == null || tsDecl.isAfter(momentoConsulta)) {
                        continue;
                    }
                    String folio = r.getFolioDesembarqueRo() != null ? r.getFolioDesembarqueRo() : r.getFolioOrigen();
                    long id = r.getId() != null ? r.getId() : 0L;
                    candidatosValidos.add(new CandidatoMovimiento(tsDecl, id, "RECOLECTOR", null, null,
                            new OrigenDirectoCandidate("RECOLECTOR", r.getId(), folio, r.getFechaDeclaracion(),
                                    r.getHora(),
                                    r.getUsuarioDestinatario(), r.getEspecie(), r.getDeclaracionDestinatario(),
                                    r.getConsumidaPorTipo())));
                }
            }
        }

        if (armadorRepository != null) {
            List<DeclaracionArmadorModel> arms = armadorRepository.findCandidatosPorPatente(patente);
            if (arms != null) {
                for (DeclaracionArmadorModel a : arms) {
                    if (!coincidePatenteCampos(a.getPlacaPatente(), a.getPlacaPatenteCarro(), null, patente)
                            || estaAnuladoORechazado(a.getEstado())
                            || !esDestinoDirectoPlanta(a.getTipoDestinatario(), a.getConsumidaPorTipo(),
                                    a.getUsuarioDestinatario(), null)) {
                        continue;
                    }
                    LocalDateTime tsDecl = toTimestamp(a.getFechaDeclaracion(), a.getHora());
                    if (tsDecl == null || tsDecl.isAfter(momentoConsulta)) {
                        continue;
                    }
                    String folio = a.getFolioDesembarqueDa() != null ? a.getFolioDesembarqueDa() : a.getFolioOrigen();
                    long id = a.getId() != null ? a.getId() : 0L;
                    candidatosValidos.add(new CandidatoMovimiento(tsDecl, id, "ARMADOR", null, null,
                            new OrigenDirectoCandidate("ARMADOR", a.getId(), folio, a.getFechaDeclaracion(),
                                    a.getHora(),
                                    a.getUsuarioDestinatario(), a.getEspecie(), a.getDeclaracionDestinatario(),
                                    a.getConsumidaPorTipo())));
                }
            }
        }

        if (areaRepository != null) {
            List<DeclaracionAreaModel> areas = areaRepository.findCandidatosPorPatente(patente);
            if (areas != null) {
                for (DeclaracionAreaModel ar : areas) {
                    if (!coincidePatenteCampos(ar.getPlacaPatente(), ar.getPlacaPatenteCarro(), null, patente)
                            || estaAnuladoORechazado(ar.getEstado())
                            || !esDestinoDirectoPlanta(ar.getTipoDestinatario(), ar.getConsumidaPorTipo(),
                                    ar.getUsuarioDestinatario(), null)) {
                        continue;
                    }
                    LocalDateTime tsDecl = toTimestamp(ar.getFechaDeclaracion(), ar.getHora());
                    if (tsDecl == null || tsDecl.isAfter(momentoConsulta)) {
                        continue;
                    }
                    String folio = ar.getFolioDesembarqueAmerb() != null ? ar.getFolioDesembarqueAmerb()
                            : ar.getFolioOrigen();
                    long id = ar.getId() != null ? ar.getId() : 0L;
                    candidatosValidos.add(new CandidatoMovimiento(tsDecl, id, "AREA", null, null,
                            new OrigenDirectoCandidate("AREA", ar.getId(), folio, ar.getFechaDeclaracion(),
                                    ar.getHora(),
                                    ar.getUsuarioDestinatario(), ar.getEspecie(), ar.getDeclaracionDestinatario(),
                                    ar.getConsumidaPorTipo())));
                }
            }
        }

        if (candidatosValidos.isEmpty()) {
            return EvaluacionPatente.builder()
                    .patente(patente)
                    .estado(ESTADO_SIN_REGISTROS)
                    .resultadoPublico(RESULTADO_PUBLICO_SIN_AUTORIZACION)
                    .consultadoEn(momentoConsulta)
                    .build();
        }

        // Ordenar por TIMESTAMP descendente, luego id descendente, y tomar el más
        // reciente
        candidatosValidos.sort((a, b) -> {
            int cmp = b.tsAutorizacion().compareTo(a.tsAutorizacion());
            if (cmp != 0)
                return cmp;
            return Long.compare(b.id(), a.id());
        });

        CandidatoMovimiento ganador = candidatosValidos.get(0);
        if ("PLANTA_DESTINO".equals(ganador.tipo())) {
            return evaluarDestinoPlanta(
                    ganador.destino(), patente, momentoConsulta, vigenciaHoras);
        }
        if (ganador.origen() != null) {
            return evaluarOrigenDirectoPlanta(
                    ganador.origen().tipo(), ganador.origen().id(), ganador.origen().folio(),
                    ganador.origen().fechaDeclaracion(), ganador.origen().hora(),
                    ganador.origen().destinatario(), ganador.origen().especie(),
                    ganador.origen().declaracionDestinatario(), ganador.origen().consumidaPorTipo(),
                    patente, momentoConsulta, vigenciaHoras, terminaEnRecepcion);
        }

        DeclaracionComercializadorModel reciente = ganador.com();
        LocalDateTime fechaHoraAut = toTimestamp(reciente.getFechaDeclaracion(), reciente.getHora());
        LocalDateTime fechaHoraSalida = resolverFechaHoraSalidaAcotada(
                reciente.getFechaDeclaracion(), reciente.getFechaTraslado(), reciente.getHora());

        String comunaDestino = resolverComunaDestino(reciente);
        String tipoAlga = resolverTipoAlga(reciente);

        // 1. Verificar si está RETENIDO (el despacho o sus cargas tienen una marca
        // bloqueante sin liberar)
        MotivoBloqueoDTO bloqueo = buscarBloqueoActivo(reciente);
        if (bloqueo != null) {
            String motivo = "Retenido por " + bloqueo.getMarca()
                    + (bloqueo.getMarcaId() != null ? " (hallazgo #" + bloqueo.getMarcaId() + ")" : "");
            return EvaluacionPatente.builder()
                    .patente(patente)
                    .estado(ESTADO_RETENIDO)
                    .resultadoPublico(RESULTADO_PUBLICO_SIN_AUTORIZACION)
                    .fechaHoraAutorizacion(fechaHoraAut)
                    .fechaHoraSalida(fechaHoraSalida)
                    .comunaDestino(comunaDestino)
                    .tipoAlga(tipoAlga)
                    .declaracionId(reciente.getId())
                    .folioDespacho(reciente.getFolioDesembarqueAc() != null ? reciente.getFolioDesembarqueAc()
                            : reciente.getFolioOrigen())
                    .motivoRetencion(motivo)
                    .hallazgoId(bloqueo.getMarcaId())
                    .hallazgoMarca(bloqueo.getMarca())
                    .consultadoEn(momentoConsulta)
                    .build();
        }

        // 2. Verificar si fue recibido en planta y
        // patente_vigencia_termina_en_recepcion = true
        DeclaracionPlantaAbastecimientoModel recepcion = buscarRecepcionPlanta(reciente);
        if (terminaEnRecepcion && recepcion != null) {
            LocalDateTime tsRecepcion = toTimestamp(recepcion.getFechaIngresoPlanta(), recepcion.getHora());
            if (tsRecepcion == null || !tsRecepcion.isAfter(momentoConsulta)) {
                String nomPlanta = recepcion.getNombrePlanta();
                if ((nomPlanta == null || nomPlanta.isBlank()) && recepcion.getUsuario() != null) {
                    nomPlanta = recepcion.getUsuario().getNombres();
                }
                return EvaluacionPatente.builder()
                        .patente(patente)
                        .estado(ESTADO_FINALIZADO)
                        .resultadoPublico(RESULTADO_PUBLICO_SIN_AUTORIZACION)
                        .fechaHoraAutorizacion(fechaHoraAut)
                        .fechaHoraSalida(fechaHoraSalida)
                        .comunaDestino(comunaDestino)
                        .tipoAlga(tipoAlga)
                        .declaracionId(reciente.getId())
                        .folioDespacho(reciente.getFolioDesembarqueAc() != null ? reciente.getFolioDesembarqueAc()
                                : reciente.getFolioOrigen())
                        .plantaRecepcionId(recepcion.getId())
                        .nombrePlantaRecepcion(nomPlanta != null ? nomPlanta : "Planta")
                        .fechaHoraRecepcion(tsRecepcion)
                        .consultadoEn(momentoConsulta)
                        .build();
            }
        }

        // 3. Verificar ventana de vigencia: ahora entre la declaración y la salida +
        // patente_vigencia_horas
        LocalDateTime limiteVigencia = fechaHoraSalida != null
                ? fechaHoraSalida.plusHours(vigenciaHoras)
                : fechaHoraAut.plusHours(vigenciaHoras);

        if (momentoConsulta.isAfter(limiteVigencia)) {
            return EvaluacionPatente.builder()
                    .patente(patente)
                    .estado(ESTADO_VENCIDO)
                    .resultadoPublico(RESULTADO_PUBLICO_SIN_AUTORIZACION)
                    .fechaHoraAutorizacion(fechaHoraAut)
                    .fechaHoraSalida(fechaHoraSalida)
                    .comunaDestino(comunaDestino)
                    .tipoAlga(tipoAlga)
                    .declaracionId(reciente.getId())
                    .folioDespacho(reciente.getFolioDesembarqueAc() != null ? reciente.getFolioDesembarqueAc()
                            : reciente.getFolioOrigen())
                    .consultadoEn(momentoConsulta)
                    .build();
        }

        // 4. AUTORIZADO
        return EvaluacionPatente.builder()
                .patente(patente)
                .estado(ESTADO_AUTORIZADO)
                .resultadoPublico(RESULTADO_PUBLICO_AUTORIZADO)
                .fechaHoraAutorizacion(fechaHoraAut)
                .fechaHoraSalida(fechaHoraSalida)
                .comunaDestino(comunaDestino)
                .tipoAlga(tipoAlga)
                .declaracionId(reciente.getId())
                .folioDespacho(reciente.getFolioDesembarqueAc() != null ? reciente.getFolioDesembarqueAc()
                        : reciente.getFolioOrigen())
                .consultadoEn(momentoConsulta)
                .build();
    }

    private boolean coincidePatente(DeclaracionComercializadorModel dc, String patenteNorm) {
        if (dc == null)
            return false;
        return coincidePatenteCampos(dc.getPlacaPatente(), dc.getPlacaPatenteCarro(), dc.getPatente(), patenteNorm);
    }

    public static boolean coincidePatenteCampos(String placaPatente, String placaPatenteCarro, String patenteLegacy,
            String patenteNorm) {
        if (patenteNorm == null || patenteNorm.isEmpty())
            return false;
        return patenteNorm.equals(ConsultaPatenteService.normalizarPatente(placaPatente))
                || patenteNorm.equals(ConsultaPatenteService.normalizarPatente(placaPatenteCarro))
                || patenteNorm.equals(ConsultaPatenteService.normalizarPatente(patenteLegacy));
    }

    public static boolean estaAnuladoORechazado(String estado) {
        if (estado == null)
            return false;
        String est = estado.trim().toUpperCase();
        return "RECHAZADA".equals(est) || "ANULADA".equals(est) || "RECHAZADO".equals(est) || "ANULADO".equals(est);
    }

    private EvaluacionPatente evaluarDestinoPlanta(
            DeclaracionPlantaDestinoModel dpd,
            String patente,
            LocalDateTime momentoConsulta,
            int vigenciaHoras) {
        LocalDateTime fechaHoraAut = toTimestamp(dpd.getFechaDeclaracionDestino(), dpd.getHora());
        Date fSalida = dpd.getFechaTrasladoDestino() != null
                ? dpd.getFechaTrasladoDestino()
                : (dpd.getFechaTraslado() != null ? dpd.getFechaTraslado() : dpd.getFechaDeclaracionDestino());
        LocalDateTime fechaHoraSalida = resolverFechaHoraSalidaAcotada(
                dpd.getFechaDeclaracionDestino(), fSalida, dpd.getHora());
        String tipoAlga = (dpd.getEspecie() != null && dpd.getEspecie().getNombre() != null
                && !dpd.getEspecie().getNombre().isBlank())
                        ? dpd.getEspecie().getNombre().trim()
                        : "Alga parda";
        String folio = dpd.getFolioDeclaracionDestino() != null ? dpd.getFolioDeclaracionDestino()
                : dpd.getFolioOrigen();

        // Retenido si el destino o alguna de sus producciones seleccionadas tiene marca
        // bloqueante activa
        MotivoBloqueoDTO bloqueo = buscarBloqueoActivoDestino(dpd);
        if (bloqueo != null) {
            String motivo = "Retenido por " + bloqueo.getMarca()
                    + (bloqueo.getMarcaId() != null ? " (hallazgo #" + bloqueo.getMarcaId() + ")" : "");
            return EvaluacionPatente.builder()
                    .patente(patente)
                    .estado(ESTADO_RETENIDO)
                    .resultadoPublico(RESULTADO_PUBLICO_SIN_AUTORIZACION)
                    .fechaHoraAutorizacion(fechaHoraAut)
                    .fechaHoraSalida(fechaHoraSalida)
                    .comunaDestino(null)
                    .tipoAlga(tipoAlga)
                    .declaracionId(dpd.getId())
                    .folioDespacho(folio)
                    .motivoRetencion(motivo)
                    .hallazgoId(bloqueo.getMarcaId())
                    .hallazgoMarca(bloqueo.getMarca())
                    .consultadoEn(momentoConsulta)
                    .build();
        }

        LocalDateTime limiteVigencia = fechaHoraSalida != null
                ? fechaHoraSalida.plusHours(vigenciaHoras)
                : fechaHoraAut.plusHours(vigenciaHoras);
        if (momentoConsulta.isAfter(limiteVigencia)) {
            return EvaluacionPatente.builder()
                    .patente(patente)
                    .estado(ESTADO_VENCIDO)
                    .resultadoPublico(RESULTADO_PUBLICO_SIN_AUTORIZACION)
                    .fechaHoraAutorizacion(fechaHoraAut)
                    .fechaHoraSalida(fechaHoraSalida)
                    .comunaDestino(null)
                    .tipoAlga(tipoAlga)
                    .declaracionId(dpd.getId())
                    .folioDespacho(folio)
                    .consultadoEn(momentoConsulta)
                    .build();
        }

        return EvaluacionPatente.builder()
                .patente(patente)
                .estado(ESTADO_AUTORIZADO)
                .resultadoPublico(RESULTADO_PUBLICO_AUTORIZADO)
                .fechaHoraAutorizacion(fechaHoraAut)
                .fechaHoraSalida(fechaHoraSalida)
                .comunaDestino(null)
                .tipoAlga(tipoAlga)
                .declaracionId(dpd.getId())
                .folioDespacho(folio)
                .consultadoEn(momentoConsulta)
                .build();
    }

    private MotivoBloqueoDTO buscarBloqueoActivoDestino(DeclaracionPlantaDestinoModel dpd) {
        if (bloqueoCargaService == null || dpd == null) {
            return null;
        }
        if (dpd.getId() != null) {
            Map<Long, MotivoBloqueoDTO> mapPropio = bloqueoCargaService.bloqueosPorTipoEIds("PLANTA_DESTINO",
                    List.of(dpd.getId()));
            if (mapPropio != null && mapPropio.containsKey(dpd.getId())) {
                return mapPropio.get(dpd.getId());
            }
        }
        if (dpd.getDeclaracionesSeleccionadas() != null && !dpd.getDeclaracionesSeleccionadas().isBlank()) {
            Map<String, MotivoBloqueoDTO> mapProds = bloqueoCargaService
                    .bloqueosPara(dpd.getDeclaracionesSeleccionadas());
            if (mapProds != null && !mapProds.isEmpty()) {
                return mapProds.values().iterator().next();
            }
        }
        return null;
    }

    private MotivoBloqueoDTO buscarBloqueoActivo(DeclaracionComercializadorModel dc) {
        if (bloqueoCargaService == null) {
            return null;
        }
        if (dc.getId() != null) {
            Map<Long, MotivoBloqueoDTO> mapPropio = bloqueoCargaService.bloqueosPorTipoEIds("COMERCIALIZADOR",
                    List.of(dc.getId()));
            if (mapPropio != null && mapPropio.containsKey(dc.getId())) {
                return mapPropio.get(dc.getId());
            }
        }
        if (dc.getDeclaracionesSeleccionadas() != null && !dc.getDeclaracionesSeleccionadas().isBlank()) {
            Map<String, MotivoBloqueoDTO> mapCargas = bloqueoCargaService
                    .bloqueosPara(dc.getDeclaracionesSeleccionadas());
            if (mapCargas != null && !mapCargas.isEmpty()) {
                return mapCargas.values().iterator().next();
            }
        }
        return null;
    }

    private DeclaracionPlantaAbastecimientoModel buscarRecepcionPlanta(DeclaracionComercializadorModel dc) {
        if (dc == null || plantaRepository == null) {
            return null;
        }
        if (dc.getDeclaracionDestinatario() != null
                && (dc.getConsumidaPorTipo() == null
                        || "PLANTA_ABASTECIMIENTO".equalsIgnoreCase(dc.getConsumidaPorTipo()))) {
            return plantaRepository.findById(dc.getDeclaracionDestinatario()).orElse(null);
        }
        return null;
    }

    public static String resolverComunaDestino(DeclaracionComercializadorModel dc) {
        if (dc != null && dc.getUsuarioDestinatario() != null
                && dc.getUsuarioDestinatario().getComuna() != null
                && dc.getUsuarioDestinatario().getComuna().getNombre() != null
                && !dc.getUsuarioDestinatario().getComuna().getNombre().isBlank()) {
            return dc.getUsuarioDestinatario().getComuna().getNombre().trim();
        }
        return "Comuna no informada";
    }

    @SuppressWarnings("unchecked")
    public static String resolverTipoAlga(DeclaracionComercializadorModel dc) {
        String especieCabecera = (dc != null && dc.getEspecie() != null && dc.getEspecie().getNombre() != null
                && !dc.getEspecie().getNombre().isBlank())
                        ? dc.getEspecie().getNombre().trim()
                        : "Alga parda";

        if (dc != null && dc.getResumenDocumento() != null && !dc.getResumenDocumento().isBlank()) {
            try {
                List<Map<String, Object>> lineas = JSON.readValue(
                        dc.getResumenDocumento(),
                        JSON.getTypeFactory().constructCollectionType(List.class, Map.class));
                Set<String> especiesDistintas = new LinkedHashSet<>();
                especiesDistintas.add(especieCabecera.toUpperCase());
                for (Map<String, Object> l : lineas) {
                    Object espObj = l.get("especie");
                    if (espObj != null && !espObj.toString().isBlank()) {
                        especiesDistintas.add(espObj.toString().trim().toUpperCase());
                    }
                }
                if (especiesDistintas.size() > 1) {
                    return especieCabecera + " y otras";
                }
            } catch (Exception ignored) {
            }
        }
        return especieCabecera;
    }

    public static LocalDateTime toTimestamp(Date fechaDate, String horaStr) {
        if (fechaDate == null)
            return null;
        LocalDate localDate;
        if (fechaDate instanceof java.sql.Date sqlDate) {
            localDate = sqlDate.toLocalDate();
        } else if (fechaDate instanceof java.sql.Timestamp sqlTs) {
            localDate = sqlTs.toLocalDateTime().toLocalDate();
        } else {
            localDate = fechaDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }

        LocalTime localTime = LocalTime.MIDNIGHT;
        if (horaStr != null && !horaStr.isBlank()) {
            String h = horaStr.trim();
            try {
                if (h.length() == 5) {
                    localTime = LocalTime.parse(h);
                } else if (h.length() >= 8) {
                    localTime = LocalTime.parse(h.substring(0, 8));
                } else {
                    localTime = LocalTime.parse(h);
                }
            } catch (Exception ignored) {
                localTime = LocalTime.MIDNIGHT;
            }
        }
        return LocalDateTime.of(localDate, localTime);
    }

    /**
     * ZC.9 (D53): valida y normaliza placa de camión y carro en declaraciones de
     * origen
     * cuando el destinatario es planta (o si se ingresaron voluntariamente).
     */
    public static void normalizarYValidarPatenteOrigen(
            ConfiguracionGeneralService configService,
            boolean esPlanta,
            String rawCamion,
            String rawCarro,
            Consumer<String> setCamion,
            Consumer<String> setCarro) {
        String camion = ConsultaPatenteService.normalizarPatente(rawCamion);
        String carro = ConsultaPatenteService.normalizarPatente(rawCarro);
        String camionFinal = camion.isEmpty() ? null : camion;
        String carroFinal = carro.isEmpty() ? null : carro;

        if (camionFinal != null && !ConsultaPatenteService.esPatenteValida(camionFinal)) {
            throw new ValidacionPlantaException("Patente del camión inválida (" + camionFinal
                    + "): ingrese entre 5 y 8 caracteres alfanuméricos.");
        }
        if (carroFinal != null && !ConsultaPatenteService.esPatenteValida(carroFinal)) {
            throw new ValidacionPlantaException("Patente del carro inválida (" + carroFinal
                    + "): ingrese entre 5 y 8 caracteres alfanuméricos.");
        }
        boolean exigePatente = configService != null
                && configService.getBoolean("origen_directo_exige_patente", false);
        if (exigePatente && esPlanta && camionFinal == null) {
            throw new ValidacionPlantaException(
                    "Debe ingresar la patente del camión para un traslado directo a planta.");
        }
        if (setCamion != null)
            setCamion.accept(camionFinal);
        if (setCarro != null)
            setCarro.accept(carroFinal);
    }

    public static boolean esUsuarioPlanta(UsuarioModel u) {
        if (u == null)
            return false;
        try {
            if (!Hibernate.isInitialized(u))
                return false;
            if (u.getPerfil() == null || !Hibernate.isInitialized(u.getPerfil()))
                return false;
            Long pid = u.getPerfil().getId();
            return pid != null && (pid == 5L || pid == 6L || pid == 7L);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean esDestinoDirectoPlanta(
            String tipoDestinatario,
            String consumidaPorTipo,
            UsuarioModel destinatario,
            IUsuarioRepository usuarioRepository) {
        if ("planta".equalsIgnoreCase(tipoDestinatario)
                || "PLANTA_ABASTECIMIENTO".equalsIgnoreCase(consumidaPorTipo)) {
            return true;
        }
        if (esUsuarioPlanta(destinatario)) {
            return true;
        }
        if (usuarioRepository != null && destinatario != null && destinatario.getId() != null) {
            Long pid = usuarioRepository.findPerfilIdByUsuarioId(destinatario.getId()).orElse(null);
            return pid != null && (pid == 5L || pid == 6L || pid == 7L);
        }
        return false;
    }

    private EvaluacionPatente evaluarOrigenDirectoPlanta(
            String tipo, Long id, String folio, Date fechaDeclaracion, String hora,
            UsuarioModel destinatario, EspecieModel especie, Long declaracionDestinatario,
            String consumidaPorTipo, String patente, LocalDateTime momentoConsulta,
            int vigenciaHoras, boolean terminaEnRecepcion) {
        LocalDateTime fechaHoraAut = toTimestamp(fechaDeclaracion, hora);
        String comunaDestino = "Comuna no informada";
        try {
            if (destinatario != null && destinatario.getComuna() != null
                    && destinatario.getComuna().getNombre() != null
                    && !destinatario.getComuna().getNombre().isBlank()) {
                comunaDestino = destinatario.getComuna().getNombre().trim();
            }
        } catch (Exception ignored) {
        }
        String tipoAlga = (especie != null && especie.getNombre() != null && !especie.getNombre().isBlank())
                ? especie.getNombre().trim()
                : "Alga parda";

        if (bloqueoCargaService != null && id != null) {
            Map<Long, MotivoBloqueoDTO> mapBloq = bloqueoCargaService.bloqueosPorTipoEIds(tipo, List.of(id));
            if (mapBloq != null && mapBloq.containsKey(id)) {
                MotivoBloqueoDTO b = mapBloq.get(id);
                String motivo = "Retenido por " + b.getMarca()
                        + (b.getMarcaId() != null ? " (hallazgo #" + b.getMarcaId() + ")" : "");
                return EvaluacionPatente.builder()
                        .patente(patente)
                        .estado(ESTADO_RETENIDO)
                        .resultadoPublico(RESULTADO_PUBLICO_SIN_AUTORIZACION)
                        .fechaHoraAutorizacion(fechaHoraAut)
                        .fechaHoraSalida(fechaHoraAut)
                        .comunaDestino(comunaDestino)
                        .tipoAlga(tipoAlga)
                        .declaracionId(id)
                        .folioDespacho(folio)
                        .motivoRetencion(motivo)
                        .hallazgoId(b.getMarcaId())
                        .hallazgoMarca(b.getMarca())
                        .consultadoEn(momentoConsulta)
                        .build();
            }
        }

        if (terminaEnRecepcion && plantaRepository != null && declaracionDestinatario != null
                && (consumidaPorTipo == null || "PLANTA_ABASTECIMIENTO".equalsIgnoreCase(consumidaPorTipo))) {
            DeclaracionPlantaAbastecimientoModel recepcion = plantaRepository.findById(declaracionDestinatario)
                    .orElse(null);
            if (recepcion != null) {
                LocalDateTime tsRecepcion = toTimestamp(recepcion.getFechaIngresoPlanta(), recepcion.getHora());
                if (tsRecepcion == null || !tsRecepcion.isAfter(momentoConsulta)) {
                    String nomPlanta = recepcion.getNombrePlanta();
                    if ((nomPlanta == null || nomPlanta.isBlank()) && recepcion.getUsuario() != null) {
                        nomPlanta = recepcion.getUsuario().getNombres();
                    }
                    return EvaluacionPatente.builder()
                            .patente(patente)
                            .estado(ESTADO_FINALIZADO)
                            .resultadoPublico(RESULTADO_PUBLICO_SIN_AUTORIZACION)
                            .fechaHoraAutorizacion(fechaHoraAut)
                            .fechaHoraSalida(fechaHoraAut)
                            .comunaDestino(comunaDestino)
                            .tipoAlga(tipoAlga)
                            .declaracionId(id)
                            .folioDespacho(folio)
                            .plantaRecepcionId(recepcion.getId())
                            .nombrePlantaRecepcion(nomPlanta != null ? nomPlanta : "Planta")
                            .fechaHoraRecepcion(tsRecepcion)
                            .consultadoEn(momentoConsulta)
                            .build();
                }
            }
        }

        LocalDateTime limiteVigencia = fechaHoraAut.plusHours(vigenciaHoras);
        if (momentoConsulta.isAfter(limiteVigencia)) {
            return EvaluacionPatente.builder()
                    .patente(patente)
                    .estado(ESTADO_VENCIDO)
                    .resultadoPublico(RESULTADO_PUBLICO_SIN_AUTORIZACION)
                    .fechaHoraAutorizacion(fechaHoraAut)
                    .fechaHoraSalida(fechaHoraAut)
                    .comunaDestino(comunaDestino)
                    .tipoAlga(tipoAlga)
                    .declaracionId(id)
                    .folioDespacho(folio)
                    .consultadoEn(momentoConsulta)
                    .build();
        }

        return EvaluacionPatente.builder()
                .patente(patente)
                .estado(ESTADO_AUTORIZADO)
                .resultadoPublico(RESULTADO_PUBLICO_AUTORIZADO)
                .fechaHoraAutorizacion(fechaHoraAut)
                .fechaHoraSalida(fechaHoraAut)
                .comunaDestino(comunaDestino)
                .tipoAlga(tipoAlga)
                .declaracionId(id)
                .folioDespacho(folio)
                .consultadoEn(momentoConsulta)
                .build();
    }

    /**
     * ZC.2 (D51): acota la fecha de salida usada para vigencia a
     * {@code min(fechaTraslado, fechaDeclaracion + patente_traslado_max_dias)}.
     * Si el parámetro está vacío, no acota.
     */
    public LocalDateTime resolverFechaHoraSalidaAcotada(Date fechaDeclaracion, Date fechaTraslado, String hora) {
        LocalDateTime tsDecl = toTimestamp(fechaDeclaracion, hora);
        if (fechaTraslado == null) {
            return tsDecl;
        }
        LocalDateTime tsSalida = toTimestamp(fechaTraslado, hora);
        if (tsSalida == null || tsDecl == null) {
            return tsSalida != null ? tsSalida : tsDecl;
        }
        Integer maxDias = resolverMaxDiasTraslado();
        if (maxDias != null) {
            LocalDate tope = tsDecl.toLocalDate().plusDays(maxDias);
            if (tsSalida.toLocalDate().isAfter(tope)) {
                tsSalida = LocalDateTime.of(tope, tsSalida.toLocalTime());
            }
        }
        return tsSalida;
    }

    private Integer resolverMaxDiasTraslado() {
        if (configService == null) {
            return 1;
        }
        String raw = configService.getValor("patente_traslado_max_dias", "1");
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return Math.max(0, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
