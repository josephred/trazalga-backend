package com.trazalga.api.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trazalga.api.dto.MotivoBloqueoDTO;
import com.trazalga.api.models.DeclaracionComercializadorModel;
import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import com.trazalga.api.repositories.IDeclaracionComercializadorRepository;
import com.trazalga.api.repositories.IDeclaracionPlantaAbastecimientoRepository;
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
 * TN.1 · Definición única del estado de un traslado por patente de camión o rampla.
 * La usan tanto la consulta ciudadana ({@code GET /api/public/patentes/{patente}})
 * como la consulta de fiscalizador ({@code GET /api/consultas/patentes/{patente}}).
 *
 * <p>Estados posibles:</p>
 * <ul>
 *   <li>{@code AUTORIZADO}: despacho vigente, sin bloqueo, no recibido en planta (si termina en recepción),
 *       y {@code ahora} entre la declaración y la salida + {@code patente_vigencia_horas} (48).</li>
 *   <li>{@code RETENIDO}: el despacho o sus cargas tienen una marca bloqueante sin liberar ({@link BloqueoCargaService}).</li>
 *   <li>{@code FINALIZADO}: la planta ya lo recibió y {@code patente_vigencia_termina_en_recepcion = true}.</li>
 *   <li>{@code VENCIDO}: pasó la ventana de vigencia desde la salida.</li>
 *   <li>{@code SIN_REGISTROS}: no hay candidatos vigentes no futuros.</li>
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
    private BloqueoCargaService bloqueoCargaService;

    @Autowired(required = false)
    private ConfiguracionGeneralService configService;

    public void setComercializadorRepository(IDeclaracionComercializadorRepository comercializadorRepository) {
        this.comercializadorRepository = comercializadorRepository;
    }

    public void setPlantaRepository(IDeclaracionPlantaAbastecimientoRepository plantaRepository) {
        this.plantaRepository = plantaRepository;
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

        if (patente.isEmpty() || comercializadorRepository == null) {
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

        List<DeclaracionComercializadorModel> candidatosRaw = comercializadorRepository.findCandidatosPorPatente(patente);
        if (candidatosRaw == null || candidatosRaw.isEmpty()) {
            return EvaluacionPatente.builder()
                    .patente(patente)
                    .estado(ESTADO_SIN_REGISTROS)
                    .resultadoPublico(RESULTADO_PUBLICO_SIN_AUTORIZACION)
                    .consultadoEn(momentoConsulta)
                    .build();
        }

        // Filtrar: estado no es RECHAZADA ni ANULADA, y declaración (TIMESTAMP(fecha_declaracion, hora)) no es futura
        List<DeclaracionComercializadorModel> candidatosValidos = new ArrayList<>();
        for (DeclaracionComercializadorModel dc : candidatosRaw) {
            if (!coincidePatente(dc, patente)) {
                continue;
            }
            if (dc.getEstado() != null) {
                String est = dc.getEstado().trim().toUpperCase();
                if ("RECHAZADA".equals(est) || "ANULADA".equals(est)) {
                    continue;
                }
            }
            LocalDateTime tsDecl = toTimestamp(dc.getFechaDeclaracion(), dc.getHora());
            if (tsDecl == null || tsDecl.isAfter(momentoConsulta)) {
                continue;
            }
            candidatosValidos.add(dc);
        }

        if (candidatosValidos.isEmpty()) {
            return EvaluacionPatente.builder()
                    .patente(patente)
                    .estado(ESTADO_SIN_REGISTROS)
                    .resultadoPublico(RESULTADO_PUBLICO_SIN_AUTORIZACION)
                    .consultadoEn(momentoConsulta)
                    .build();
        }

        // Ordenar por TIMESTAMP(fecha_declaracion, hora) descendente, luego id descendente, y tomar el más reciente
        candidatosValidos.sort((a, b) -> {
            LocalDateTime ta = toTimestamp(a.getFechaDeclaracion(), a.getHora());
            LocalDateTime tb = toTimestamp(b.getFechaDeclaracion(), b.getHora());
            int cmp = tb.compareTo(ta);
            if (cmp != 0) return cmp;
            Long ida = a.getId() != null ? a.getId() : 0L;
            Long idb = b.getId() != null ? b.getId() : 0L;
            return idb.compareTo(ida);
        });

        DeclaracionComercializadorModel reciente = candidatosValidos.get(0);
        LocalDateTime fechaHoraAut = toTimestamp(reciente.getFechaDeclaracion(), reciente.getHora());
        Date fechaSalidaBase = reciente.getFechaTraslado() != null ? reciente.getFechaTraslado() : reciente.getFechaDeclaracion();
        LocalDateTime fechaHoraSalida = toTimestamp(fechaSalidaBase, reciente.getHora());

        String comunaDestino = resolverComunaDestino(reciente);
        String tipoAlga = resolverTipoAlga(reciente);

        // 1. Verificar si está RETENIDO (el despacho o sus cargas tienen una marca bloqueante sin liberar)
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
                    .folioDespacho(reciente.getFolioDesembarqueAc() != null ? reciente.getFolioDesembarqueAc() : reciente.getFolioOrigen())
                    .motivoRetencion(motivo)
                    .hallazgoId(bloqueo.getMarcaId())
                    .hallazgoMarca(bloqueo.getMarca())
                    .consultadoEn(momentoConsulta)
                    .build();
        }

        // 2. Verificar si fue recibido en planta y patente_vigencia_termina_en_recepcion = true
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
                        .folioDespacho(reciente.getFolioDesembarqueAc() != null ? reciente.getFolioDesembarqueAc() : reciente.getFolioOrigen())
                        .plantaRecepcionId(recepcion.getId())
                        .nombrePlantaRecepcion(nomPlanta != null ? nomPlanta : "Planta")
                        .fechaHoraRecepcion(tsRecepcion)
                        .consultadoEn(momentoConsulta)
                        .build();
            }
        }

        // 3. Verificar ventana de vigencia: ahora entre la declaración y la salida + patente_vigencia_horas
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
                    .folioDespacho(reciente.getFolioDesembarqueAc() != null ? reciente.getFolioDesembarqueAc() : reciente.getFolioOrigen())
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
                .folioDespacho(reciente.getFolioDesembarqueAc() != null ? reciente.getFolioDesembarqueAc() : reciente.getFolioOrigen())
                .consultadoEn(momentoConsulta)
                .build();
    }

    private boolean coincidePatente(DeclaracionComercializadorModel dc, String patenteNorm) {
        if (patenteNorm == null || patenteNorm.isEmpty()) return false;
        return patenteNorm.equals(ConsultaPatenteService.normalizarPatente(dc.getPlacaPatente()))
                || patenteNorm.equals(ConsultaPatenteService.normalizarPatente(dc.getPlacaPatenteCarro()))
                || patenteNorm.equals(ConsultaPatenteService.normalizarPatente(dc.getPatente()));
    }

    private MotivoBloqueoDTO buscarBloqueoActivo(DeclaracionComercializadorModel dc) {
        if (bloqueoCargaService == null) {
            return null;
        }
        if (dc.getId() != null) {
            Map<Long, MotivoBloqueoDTO> mapPropio = bloqueoCargaService.bloqueosPorTipoEIds("COMERCIALIZADOR", List.of(dc.getId()));
            if (mapPropio != null && mapPropio.containsKey(dc.getId())) {
                return mapPropio.get(dc.getId());
            }
        }
        if (dc.getDeclaracionesSeleccionadas() != null && !dc.getDeclaracionesSeleccionadas().isBlank()) {
            Map<String, MotivoBloqueoDTO> mapCargas = bloqueoCargaService.bloqueosPara(dc.getDeclaracionesSeleccionadas());
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
                && (dc.getConsumidaPorTipo() == null || "PLANTA_ABASTECIMIENTO".equalsIgnoreCase(dc.getConsumidaPorTipo()))) {
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
        if (fechaDate == null) return null;
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
}
