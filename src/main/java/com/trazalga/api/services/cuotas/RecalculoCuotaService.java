package com.trazalga.api.services.cuotas;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trazalga.api.dto.DetalleMarcaRecalculoDTO;
import com.trazalga.api.dto.ResumenRecalculoCuotaDTO;
import com.trazalga.api.models.CuotaExtraccionEventoModel;
import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.models.DeclaracionMarcaModel;
import com.trazalga.api.repositories.ICuotaExtraccionEventoRepository;
import com.trazalga.api.repositories.ICuotaExtraccionRepository;
import com.trazalga.api.repositories.IDeclaracionMarcaRepository;
import com.trazalga.api.services.ConfiguracionGeneralService;
import com.trazalga.api.services.CuotaExtraccionService;
import com.trazalga.api.services.hallazgos.CriterioHallazgo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio para el recálculo cronológico de hallazgos de cuota e idempotencia (TC.10 / TQ.3).
 * Reconstruye el consumo de la cuota en orden estricto (f_imp, hora, id),
 * generando las marcas CUOTA_EXCEDIDA, POSTERIOR_CIERRE y DECLARACION_EXTEMPORANEA que falten
 * con origen='RECALCULO', sin modificar marcas preexistentes.
 */
@Service
@Slf4j
public class RecalculoCuotaService {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private ICuotaExtraccionRepository cuotaRepository;

    @Autowired
    private IDeclaracionMarcaRepository declaracionMarcaRepository;

    @Autowired
    private ICuotaExtraccionEventoRepository cuotaEventoRepository;

    @Autowired
    @Lazy
    private CuotaExtraccionService cuotaService;

    @Autowired
    @Lazy
    private ConfiguracionGeneralService configService;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeclaracionCandidata {
        private String tipo; // RECOLECTOR | ARMADOR | AREA
        private Long id;
        private Long usuarioId;
        private Long comunaImputacionId;
        private Long especieId;
        private Long extraccionTipoId;
        private Long amerbId;
        private Date fechaExtraccion;
        private Date fechaDeclaracion;
        private Date fechaCreacion;
        private String hora;
        private BigDecimal desembarque;
        private BigDecimal captura;
        private LocalDate fechaEval;
    }

    /**
     * Ejecuta el recálculo de marcas y reconstrucción de consumo para una cuota (TC.10).
     */
    @Transactional
    public ResumenRecalculoCuotaDTO recalcularHallazgos(Long cuotaId, LocalDate desde, LocalDate hasta) {
        CuotaExtraccionModel cuota = cuotaRepository.findById(cuotaId)
                .orElseThrow(() -> new IllegalArgumentException("Cuota de extracción no encontrada con id: " + cuotaId));

        LocalDate fInicio = (desde != null) ? desde : toLocalDate(cuota.getFechaInicio());
        LocalDate fFin = (hasta != null) ? hasta : toLocalDate(cuota.getFechaFin());

        if (fInicio == null) {
            fInicio = LocalDate.of(1970, 1, 1);
        }
        if (fFin == null) {
            fFin = LocalDate.of(2099, 12, 31);
        }

        log.info("TC.10: Iniciando recálculo para cuota #{} ({}) en ventana [{} .. {}]",
                cuota.getId(), cuotaService.describirAlcance(cuota), fInicio, fFin);

        // 1. Cargar declaraciones candidatas dentro de la ventana de fechas
        List<DeclaracionCandidata> candidatas = cargarDeclaracionesCandidatas(fInicio, fFin);

        // 2. Filtrar únicamente las que aplican al ámbito, especie, método, territorio y usuario de la cuota
        List<DeclaracionCandidata> aplicables = candidatas.stream()
                .filter(d -> cuotaService.especieAplica(cuota, d.getEspecieId()))
                .filter(d -> cuotaService.metodoAplica(cuota, d.getExtraccionTipoId()))
                .filter(d -> cuotaService.ambitoAplica(cuota, d.getTipo()))
                .filter(d -> cuotaService.amerbAplica(cuota, d.getAmerbId()))
                .filter(d -> cuotaService.usuarioAplica(cuota, d.getUsuarioId()))
                .filter(d -> d.getComunaImputacionId() == null || cuotaService.contieneComuna(cuota, d.getComunaImputacionId()))
                .collect(Collectors.toList());

        // Manejo de cuota individual sobre plantilla
        boolean tieneIndividualEspecifica = aplicables.stream()
                .anyMatch(d -> cuota.getUsuario() != null && d.getUsuarioId() != null && cuota.getUsuario().getId().equals(d.getUsuarioId()));
        if (tieneIndividualEspecifica && Boolean.TRUE.equals(cuota.getEsPlantilla())) {
            aplicables.clear();
        }

        // 3. Orden cronológico estricto: fechaEval ASC, hora ASC, id ASC
        aplicables.sort((a, b) -> {
            int cmpF = a.getFechaEval().compareTo(b.getFechaEval());
            if (cmpF != 0) return cmpF;
            String ha = a.getHora() != null ? a.getHora() : "";
            String hb = b.getHora() != null ? b.getHora() : "";
            int cmpH = ha.compareTo(hb);
            if (cmpH != 0) return cmpH;
            return a.getId().compareTo(b.getId());
        });

        // 4. Parámetros de cierre y límites
        boolean cierrePorVencimientoActivo = (configService != null)
                && configService.getBoolean("cuota_cierre_automatico_vencimiento", false);
        int diasGracia = (configService != null)
                ? configService.getInt("cuota_dias_gracia_declaracion", 0) : 0;
        LocalDate hoy = LocalDate.now();
        LocalDate cierre = cuota.fechaCierreEfectiva(hoy, cierrePorVencimientoActivo);

        Date fechaEvalRef = toDate(fInicio);
        BigDecimal limiteEfectivo = cuotaService.calcularLimiteEfectivo(cuota, fechaEvalRef);
        boolean esCaptura = !"DESEMBARQUE".equalsIgnoreCase(cuota.getMetrica());

        BigDecimal consumoAcumulado = BigDecimal.ZERO;
        int marcasNuevas = 0;
        int marcasExistentes = 0;
        List<DetalleMarcaRecalculoDTO> detalleMarcas = new ArrayList<>();

        // 5. Simulación cronológica acumulada
        for (DeclaracionCandidata decl : aplicables) {
            BigDecimal monto = esCaptura
                    ? (decl.getCaptura() != null ? decl.getCaptura() : decl.getDesembarque())
                    : (decl.getDesembarque() != null ? decl.getDesembarque() : BigDecimal.ZERO);
            if (monto == null) monto = BigDecimal.ZERO;

            consumoAcumulado = consumoAcumulado.add(monto);

            LocalDate f_imp = decl.getFechaEval();
            LocalDate f_ing = toLocalDate(decl.getFechaDeclaracion());

            // A. Evaluación POSTERIOR_CIERRE
            if (cierre != null && f_imp != null && f_imp.isAfter(cierre)) {
                String clave = String.format("%s:%d:POSTERIOR_CIERRE:%d", decl.getTipo(), decl.getId(), cuota.getId());
                Optional<DeclaracionMarcaModel> optMarca = declaracionMarcaRepository.findByClaveIdempotencia(clave);
                if (optMarca.isPresent()) {
                    marcasExistentes++;
                    detalleMarcas.add(new DetalleMarcaRecalculoDTO(decl.getTipo(), decl.getId(), "POSTERIOR_CIERRE", "Confirmada preexistente", false));
                } else {
                    String msg = String.format("La cuota %s se encuentra cerrada desde el %s. Extracción fuera de plazo.",
                            cuotaService.describirAlcance(cuota), cierre);
                    CriterioHallazgo crit = CriterioHallazgo.deFecha("fecha_cierre", cierre.toString(), f_imp.toString());
                    DeclaracionMarcaModel nueva = DeclaracionMarcaModel.builder()
                            .declaracionTipo(decl.getTipo())
                            .declaracionId(decl.getId())
                            .marca("POSTERIOR_CIERRE")
                            .reglaId(cuota.getId())
                            .origen("RECALCULO")
                            .estadoGestion("PENDIENTE")
                            .resuelta(false)
                            .claveIdempotencia(clave)
                            .detalle(msg)
                            .criterioParametro(crit.parametro())
                            .criterioUmbral(crit.umbral())
                            .criterioValor(crit.valorObservado())
                            .criterioUnidad(crit.unidad())
                            .build();
                    declaracionMarcaRepository.save(nueva);
                    marcasNuevas++;
                    detalleMarcas.add(new DetalleMarcaRecalculoDTO(decl.getTipo(), decl.getId(), "POSTERIOR_CIERRE", msg, true));
                }
            } else if (cierre != null && f_imp != null && !f_imp.isAfter(cierre) && f_ing != null && f_ing.isAfter(cierre.plusDays(diasGracia))) {
                // B. Evaluación DECLARACION_EXTEMPORANEA
                String clave = String.format("%s:%d:DECLARACION_EXTEMPORANEA:%d", decl.getTipo(), decl.getId(), cuota.getId());
                Optional<DeclaracionMarcaModel> optMarca = declaracionMarcaRepository.findByClaveIdempotencia(clave);
                if (optMarca.isPresent()) {
                    marcasExistentes++;
                    detalleMarcas.add(new DetalleMarcaRecalculoDTO(decl.getTipo(), decl.getId(), "DECLARACION_EXTEMPORANEA", "Confirmada preexistente", false));
                } else {
                    LocalDate plazoGraciaFecha = cierre.plusDays(diasGracia);
                    String msg = String.format("La cuota %s cerró el %s (plazo gracia %d días hasta %s). Declaración extemporánea ingresada el %s.",
                            cuotaService.describirAlcance(cuota), cierre, diasGracia, plazoGraciaFecha, f_ing);
                    CriterioHallazgo crit = CriterioHallazgo.deFecha("fecha_cierre", plazoGraciaFecha.toString(), f_ing.toString());
                    DeclaracionMarcaModel nueva = DeclaracionMarcaModel.builder()
                            .declaracionTipo(decl.getTipo())
                            .declaracionId(decl.getId())
                            .marca("DECLARACION_EXTEMPORANEA")
                            .reglaId(cuota.getId())
                            .origen("RECALCULO")
                            .estadoGestion("PENDIENTE")
                            .resuelta(false)
                            .claveIdempotencia(clave)
                            .detalle(msg)
                            .criterioParametro(crit.parametro())
                            .criterioUmbral(crit.umbral())
                            .criterioValor(crit.valorObservado())
                            .criterioUnidad(crit.unidad())
                            .build();
                    declaracionMarcaRepository.save(nueva);
                    marcasNuevas++;
                    detalleMarcas.add(new DetalleMarcaRecalculoDTO(decl.getTipo(), decl.getId(), "DECLARACION_EXTEMPORANEA", msg, true));
                }
            }

            // C. Evaluación CUOTA_EXCEDIDA
            if (limiteEfectivo.compareTo(BigDecimal.ZERO) > 0 && consumoAcumulado.compareTo(limiteEfectivo) > 0) {
                String clave = String.format("%s:%d:CUOTA_EXCEDIDA:%d", decl.getTipo(), decl.getId(), cuota.getId());
                Optional<DeclaracionMarcaModel> optMarca = declaracionMarcaRepository.findByClaveIdempotencia(clave);
                if (optMarca.isPresent()) {
                    marcasExistentes++;
                    detalleMarcas.add(new DetalleMarcaRecalculoDTO(decl.getTipo(), decl.getId(), "CUOTA_EXCEDIDA", "Confirmada preexistente", false));
                } else {
                    double exceso = consumoAcumulado.subtract(limiteEfectivo).doubleValue();
                    double pctExceso = (limiteEfectivo.compareTo(BigDecimal.ZERO) > 0)
                            ? consumoAcumulado.multiply(BigDecimal.valueOf(100)).divide(limiteEfectivo, 1, RoundingMode.HALF_UP).doubleValue()
                            : 100.0;
                    String msg = String.format("Extracción excede la cuota %s (acumulado %.0f kg de %.0f kg, %.1f%%). Exceso de %.0f kg.",
                            cuotaService.describirAlcance(cuota), consumoAcumulado.doubleValue(), limiteEfectivo.doubleValue(), pctExceso, exceso);
                    CriterioHallazgo crit = CriterioHallazgo.deKilos("limite_cuota_kg", limiteEfectivo.doubleValue(), consumoAcumulado.doubleValue());
                    DeclaracionMarcaModel nueva = DeclaracionMarcaModel.builder()
                            .declaracionTipo(decl.getTipo())
                            .declaracionId(decl.getId())
                            .marca("CUOTA_EXCEDIDA")
                            .reglaId(cuota.getId())
                            .origen("RECALCULO")
                            .estadoGestion("PENDIENTE")
                            .resuelta(false)
                            .claveIdempotencia(clave)
                            .detalle(msg)
                            .criterioParametro(crit.parametro())
                            .criterioUmbral(crit.umbral())
                            .criterioValor(crit.valorObservado())
                            .criterioUnidad(crit.unidad())
                            .build();
                    declaracionMarcaRepository.save(nueva);
                    marcasNuevas++;
                    detalleMarcas.add(new DetalleMarcaRecalculoDTO(decl.getTipo(), decl.getId(), "CUOTA_EXCEDIDA", msg, true));
                }
            }
        }

        // 6. Registro de evento de auditoría RECALCULADA
        double pctFinal = (limiteEfectivo.compareTo(BigDecimal.ZERO) > 0)
                ? consumoAcumulado.multiply(BigDecimal.valueOf(100)).divide(limiteEfectivo, 2, RoundingMode.HALF_UP).doubleValue()
                : 0.0;

        String detalleEvento = String.format("Recálculo ejecutado. Evaluadas: %d, Marcas nuevas: %d, Marcas existentes: %d, Consumo final: %.2f kg (%.1f%%)",
                aplicables.size(), marcasNuevas, marcasExistentes, consumoAcumulado.doubleValue(), pctFinal);

        cuotaEventoRepository.save(CuotaExtraccionEventoModel.builder()
                .cuota(cuota)
                .tipo("RECALCULADA")
                .motivo("ADMINISTRATIVO")
                .detalle(detalleEvento)
                .createdAt(new Date())
                .build());

        log.info("TC.10: Recálculo completado para cuota #{}: {}", cuota.getId(), detalleEvento);

        return ResumenRecalculoCuotaDTO.builder()
                .cuotaId(cuota.getId())
                .cuotaNombre(cuotaService.describirAlcance(cuota))
                .declaracionesEvaluadas(aplicables.size())
                .marcasNuevas(marcasNuevas)
                .marcasExistentes(marcasExistentes)
                .consumoFinalKg(consumoAcumulado)
                .limiteKg(limiteEfectivo)
                .porcentajeConsumoFinal(pctFinal)
                .ejecutadoAt(new Date())
                .marcasGeneradas(detalleMarcas)
                .build();
    }

    /**
     * Cuenta cuántas declaraciones del período de la cuota ingresaron antes de la carga de la cuota (TQ.3).
     */
    @Transactional(readOnly = true)
    public int contarDeclaracionesPreviasSinEvaluar(CuotaExtraccionModel cuota) {
        if (cuota == null || cuota.getId() == null || cuota.getCreatedAt() == null) {
            return 0;
        }

        LocalDate fInicio = toLocalDate(cuota.getFechaInicio());
        LocalDate fFin = toLocalDate(cuota.getFechaFin());
        if (fInicio == null || fFin == null) {
            return 0;
        }

        List<DeclaracionCandidata> candidatas = cargarDeclaracionesCandidatas(fInicio, fFin);
        Date cuotaCreatedAt = cuota.getCreatedAt();

        long count = candidatas.stream()
                .filter(d -> d.getFechaCreacion() != null && d.getFechaCreacion().before(cuotaCreatedAt))
                .filter(d -> cuotaService.especieAplica(cuota, d.getEspecieId()))
                .filter(d -> cuotaService.metodoAplica(cuota, d.getExtraccionTipoId()))
                .filter(d -> cuotaService.ambitoAplica(cuota, d.getTipo()))
                .filter(d -> cuotaService.amerbAplica(cuota, d.getAmerbId()))
                .filter(d -> cuotaService.usuarioAplica(cuota, d.getUsuarioId()))
                .filter(d -> d.getComunaImputacionId() == null || cuotaService.contieneComuna(cuota, d.getComunaImputacionId()))
                .count();

        return (int) count;
    }

    private List<DeclaracionCandidata> cargarDeclaracionesCandidatas(LocalDate fInicio, LocalDate fFin) {
        String modo = cuotaService != null ? cuotaService.getModoImputacion() : "DECLARACION";
        String colFecha = "DECLARACION".equalsIgnoreCase(modo) ? "fecha_declaracion" : "fecha_extraccion";

        List<DeclaracionCandidata> lista = new ArrayList<>();

        java.sql.Date sqlInicio = java.sql.Date.valueOf(fInicio);
        java.sql.Date sqlFin = java.sql.Date.valueOf(fFin);

        // 1. Recolectores
        String sqlRec = "SELECT d.id, d.usuario_id, u.comuna_id, d.especie_id, d.extraccion_tipo_id, "
                + "d.fecha_extraccion, d.fecha_declaracion, d.hora, d.desembarque, d.captura, d.fecha_declaracion AS f_creacion "
                + "FROM declaracion_recolector d "
                + "LEFT JOIN usuario u ON u.id = d.usuario_id "
                + "WHERE d." + colFecha + " BETWEEN :desde AND :hasta";
        Query qRec = em.createNativeQuery(sqlRec);
        qRec.setParameter("desde", sqlInicio);
        qRec.setParameter("hasta", sqlFin);
        @SuppressWarnings("unchecked")
        List<Object[]> rowsRec = qRec.getResultList();
        for (Object[] r : rowsRec) {
            lista.add(mapearCandidata("RECOLECTOR", r, modo));
        }

        // 2. Armadores
        String sqlArm = "SELECT d.id, d.usuario_id, d.comuna_id, d.especie_id, d.extraccion_tipo_id, "
                + "d.fecha_extraccion, d.fecha_declaracion, d.hora, d.desembarque, d.captura, d.fecha_declaracion AS f_creacion "
                + "FROM declaracion_armador d "
                + "WHERE d." + colFecha + " BETWEEN :desde AND :hasta";
        Query qArm = em.createNativeQuery(sqlArm);
        qArm.setParameter("desde", sqlInicio);
        qArm.setParameter("hasta", sqlFin);
        @SuppressWarnings("unchecked")
        List<Object[]> rowsArm = qArm.getResultList();
        for (Object[] r : rowsArm) {
            lista.add(mapearCandidata("ARMADOR", r, modo));
        }

        // 3. Áreas de Manejo
        String sqlArea = "SELECT d.id, d.usuario_id, d.comuna_id, d.especie_id, d.extraccion_tipo_id, "
                + "d.fecha_extraccion, d.fecha_declaracion, d.hora, d.desembarque, d.captura, d.fecha_declaracion AS f_creacion "
                + "FROM declaracion_area d "
                + "WHERE d." + colFecha + " BETWEEN :desde AND :hasta";
        Query qArea = em.createNativeQuery(sqlArea);
        qArea.setParameter("desde", sqlInicio);
        qArea.setParameter("hasta", sqlFin);
        @SuppressWarnings("unchecked")
        List<Object[]> rowsArea = qArea.getResultList();
        for (Object[] r : rowsArea) {
            lista.add(mapearCandidata("AREA", r, modo));
        }

        return lista;
    }

    private DeclaracionCandidata mapearCandidata(String tipo, Object[] r, String modo) {
        Long id = r[0] != null ? ((Number) r[0]).longValue() : null;
        Long uid = r[1] != null ? ((Number) r[1]).longValue() : null;
        Long cid = r[2] != null ? ((Number) r[2]).longValue() : null;
        Long espId = r[3] != null ? ((Number) r[3]).longValue() : null;
        Long extId = r[4] != null ? ((Number) r[4]).longValue() : null;
        Date fExt = (Date) r[5];
        Date fDec = (Date) r[6];
        String hora = r[7] != null ? r[7].toString() : null;
        BigDecimal desembarque = r[8] != null ? new BigDecimal(r[8].toString()) : BigDecimal.ZERO;
        BigDecimal captura = r[9] != null ? new BigDecimal(r[9].toString()) : desembarque;
        Date fCreacion = (Date) r[10];

        Date fEvalDate = "DECLARACION".equalsIgnoreCase(modo) ? (fDec != null ? fDec : fExt) : (fExt != null ? fExt : fDec);
        LocalDate fechaEval = toLocalDate(fEvalDate);

        return DeclaracionCandidata.builder()
                .tipo(tipo)
                .id(id)
                .usuarioId(uid)
                .comunaImputacionId(cid)
                .especieId(espId)
                .extraccionTipoId(extId)
                .amerbId(null)
                .fechaExtraccion(fExt)
                .fechaDeclaracion(fDec)
                .fechaCreacion(fCreacion)
                .hora(hora)
                .desembarque(desembarque)
                .captura(captura)
                .fechaEval(fechaEval != null ? fechaEval : LocalDate.now())
                .build();
    }

    private LocalDate toLocalDate(Date date) {
        if (date == null) return null;
        if (date instanceof java.sql.Date) {
            return ((java.sql.Date) date).toLocalDate();
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private Date toDate(LocalDate localDate) {
        if (localDate == null) return null;
        return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
