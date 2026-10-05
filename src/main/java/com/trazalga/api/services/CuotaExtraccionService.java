package com.trazalga.api.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trazalga.api.dto.ControlCuotaDiariaDTO;
import com.trazalga.api.dto.CuotaListadoDTO;
import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.models.FactorConversionModel;
import com.trazalga.api.models.MacrozonaModel;
import com.trazalga.api.models.ProvinciaModel;
import com.trazalga.api.models.RegionModel;
import com.trazalga.api.repositories.IAmerbRepository;
import com.trazalga.api.repositories.IComunaRepository;
import com.trazalga.api.repositories.ICuotaExtraccionRepository;
import com.trazalga.api.repositories.IEspecieRepository;
import com.trazalga.api.repositories.IExtraccionTipoRepository;
import com.trazalga.api.repositories.IHumedadEstadoRepository;
import com.trazalga.api.repositories.IMacrozonaRepository;
import com.trazalga.api.repositories.IProvinciaRepository;
import com.trazalga.api.repositories.IRegionRepository;
import com.trazalga.api.repositories.IUsuarioRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

@Service
@Slf4j
@Transactional(readOnly = true)
public class CuotaExtraccionService {

    @Autowired
    private ICuotaExtraccionRepository cuotaRepository;

    @Autowired
    private IRegionRepository regionRepository;

    @Autowired
    private IMacrozonaRepository macrozonaRepository;

    @Autowired
    private MacrozonaService macrozonaService;

    @Autowired
    private IProvinciaRepository provinciaRepository;

    @Autowired
    private IComunaRepository comunaRepository;

    @Autowired
    private IEspecieRepository especieRepository;

    @Autowired
    private IExtraccionTipoRepository extraccionTipoRepository;

    @Autowired
    private IHumedadEstadoRepository humedadEstadoRepository;

    @Autowired
    private IAmerbRepository amerbRepository;

    @Autowired
    private IUsuarioRepository usuarioRepository;

    @Autowired
    private AmerbEspecieHabilitadaService amerbEspecieHabilitadaService;

    @Autowired
    private FactorConversionService factorConversionService;

    @Autowired
    private ConfiguracionGeneralService configuracionGeneralService;

    @Autowired
    private EntityManager entityManager;

    public List<CuotaExtraccionModel> getAll() {
        return cuotaRepository.findAll();
    }

    public Optional<CuotaExtraccionModel> getById(Long id) {
        return cuotaRepository.findById(id);
    }

    public String getModoImputacion() {
        if (configuracionGeneralService == null) {
            return "EXTRACCION";
        }
        String modo = configuracionGeneralService.getValor("cuota_fecha_imputacion", "EXTRACCION");
        return (modo != null && "DECLARACION".equalsIgnoreCase(modo.trim())) ? "DECLARACION" : "EXTRACCION";
    }

    @Transactional
    public CuotaExtraccionModel save(CuotaExtraccionModel cuota) {
        resolverReferencias(cuota);
        validarDatosBasicos(cuota);
        validarSolapamiento(cuota);
        validarJerarquia(cuota);
        invalidarCacheConsumo();
        return cuotaRepository.save(cuota);
    }

    @Transactional
    public CuotaExtraccionModel update(Long id, CuotaExtraccionModel request) {
        CuotaExtraccionModel cuota = cuotaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cuota no encontrada con ID: " + id));

        resolverReferencias(request);
        cuota.setPerfil(request.getPerfil());
        if (request.getAmbito() != null) cuota.setAmbito(request.getAmbito());
        cuota.setEspecie(request.getEspecie());
        cuota.setRegion(request.getRegion());
        cuota.setMacrozona(request.getMacrozona());
        cuota.setProvincia(request.getProvincia());
        cuota.setComuna(request.getComuna());
        if (request.getComunas() != null) {
            cuota.getComunas().clear();
            cuota.getComunas().addAll(request.getComunas());
        }
        cuota.setUsuario(request.getUsuario());
        cuota.setAmerb(request.getAmerb());
        cuota.setExtraccionTipo(request.getExtraccionTipo());
        cuota.setHumedadEstado(request.getHumedadEstado());
        if (request.getNivelAgregacion() != null) cuota.setNivelAgregacion(request.getNivelAgregacion());
        if (request.getMetrica() != null) cuota.setMetrica(request.getMetrica());
        if (request.getEsPlantilla() != null) cuota.setEsPlantilla(request.getEsPlantilla());
        if (request.getModoAccion() != null) cuota.setModoAccion(request.getModoAccion());
        cuota.setPeriodo(request.getPeriodo());
        cuota.setLimiteKg(request.getLimiteKg());
        cuota.setFechaInicio(request.getFechaInicio());
        cuota.setFechaFin(request.getFechaFin());
        cuota.setResolucion(request.getResolucion());
        if (request.getEstado() != null) cuota.setEstado(request.getEstado());
        cuota.setFechaCierre(request.getFechaCierre());
        if (request.getActivo() != null) cuota.setActivo(request.getActivo());

        validarDatosBasicos(cuota);
        validarSolapamiento(cuota);
        validarJerarquia(cuota);
        invalidarCacheConsumo();
        return cuotaRepository.save(cuota);
    }

    @Transactional
    public boolean delete(Long id) {
        try {
            cuotaRepository.deleteById(id);
            invalidarCacheConsumo();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // =========================================================================
    // EVALUACIÓN DE CUOTA EN VALIDACIÓN DE DECLARACIÓN (FASE 1)
    // =========================================================================

    public static class EvaluacionCuotaResult {
        private final boolean permite;
        private final boolean posteriorCierre;
        private final boolean excedeLimite;
        private final boolean bloquear;
        private final String marca; // CUOTA_EXCEDIDA | POSTERIOR_CIERRE
        private final BigDecimal totalAcumulado;
        private final BigDecimal limiteEfectivo;
        private final String mensaje;
        private final CuotaExtraccionModel cuotaAplicada;

        public EvaluacionCuotaResult(boolean permite, boolean posteriorCierre, boolean excedeLimite,
                                     boolean bloquear, String marca, BigDecimal totalAcumulado,
                                     BigDecimal limiteEfectivo, String mensaje, CuotaExtraccionModel cuotaAplicada) {
            this.permite = permite;
            this.posteriorCierre = posteriorCierre;
            this.excedeLimite = excedeLimite;
            this.bloquear = bloquear;
            this.marca = marca;
            this.totalAcumulado = totalAcumulado;
            this.limiteEfectivo = limiteEfectivo;
            this.mensaje = mensaje;
            this.cuotaAplicada = cuotaAplicada;
        }

        public boolean isPermite() { return permite; }
        public boolean isPosteriorCierre() { return posteriorCierre; }
        public boolean isExcedeLimite() { return excedeLimite; }
        public boolean isBloquear() { return bloquear; }
        public String getMarca() { return marca; }
        public BigDecimal getTotalAcumulado() { return totalAcumulado; }
        public BigDecimal getLimiteEfectivo() { return limiteEfectivo; }
        public String getMensaje() { return mensaje; }
        public CuotaExtraccionModel getCuotaAplicada() { return cuotaAplicada; }
    }

    /**
     * Evalúa el consumo de cuotas para una declaración según todas las dimensiones parametrizadas.
     */
    public EvaluacionCuotaResult evaluarCuotaDeclaracion(
            String perfil,
            Long usuarioId,
            Long amerbId,
            Long especieId,
            Long extraccionTipoId,
            Long comunaImputacionId,
            Date fechaExtraccion,
            Date fechaDeclaracion,
            BigDecimal desembarqueKg,
            BigDecimal capturaKg) {

        String modo = getModoImputacion();
        Date fechaEval;
        if ("DECLARACION".equalsIgnoreCase(modo)) {
            fechaEval = (fechaDeclaracion != null) ? fechaDeclaracion : (fechaExtraccion != null ? fechaExtraccion : new Date());
        } else {
            fechaEval = (fechaExtraccion != null) ? fechaExtraccion : (fechaDeclaracion != null ? fechaDeclaracion : new Date());
        }

        // 1. Si es AMERB, validar primero si la especie está habilitada por resolución
        if (amerbId != null && especieId != null) {
            if (!amerbEspecieHabilitadaService.isEspecieHabilitada(amerbId, especieId)) {
                return new EvaluacionCuotaResult(false, false, false, true, null,
                        BigDecimal.ZERO, BigDecimal.ZERO,
                        "La especie indicada no está habilitada por resolución para esta Área de Manejo (AMERB).", null);
            }
        }

        // 2. Buscar cuotas activas
        List<CuotaExtraccionModel> todasCuotas = cuotaRepository.findByActivoTrue();

        // 3. Filtrar cuotas aplicables por perfil/ámbito, especie, método, fechas
        List<CuotaExtraccionModel> aplicables = new ArrayList<>();
        for (CuotaExtraccionModel c : todasCuotas) {
            String ambito = c.getAmbito() != null ? c.getAmbito().trim().toUpperCase() : "AREA_LIBRE";
            if ("AREA_LIBRE".equals(ambito)) {
                // Cuotas de área libre aplican conjuntamente a RECOLECTOR y ARMADOR
                if (!"RECOLECTOR".equalsIgnoreCase(perfil) && !"ARMADOR".equalsIgnoreCase(perfil)) {
                    continue;
                }
            } else if ("AMERB".equals(ambito)) {
                // Cuotas AMERB aplican a Área de Manejo
                if (!"AREA".equalsIgnoreCase(perfil) && !"ÁREA DE MANEJO".equalsIgnoreCase(perfil)) {
                    continue;
                }
            } else {
                if (c.getPerfil() != null && !c.getPerfil().equalsIgnoreCase(perfil)) {
                    continue;
                }
            }
            if (c.getEspecie() != null && especieId != null && !c.getEspecie().getId().equals(especieId)) {
                continue;
            }
            if (c.getExtraccionTipo() != null && extraccionTipoId != null && !c.getExtraccionTipo().getId().equals(extraccionTipoId)) {
                continue;
            }

            // Vigencia por fechas
            if (c.getFechaInicio() != null && fechaEval.before(c.getFechaInicio())) {
                continue;
            }
            if (c.getFechaFin() != null && fechaEval.after(c.getFechaFin())) {
                continue;
            }

            // Filtrar AMERB: si cuota tiene AMERB, sólo aplica a declaraciones de esa AMERB
            if (c.getAmerb() != null) {
                if (amerbId == null || !c.getAmerb().getId().equals(amerbId)) {
                    continue;
                }
            }

            // Filtrar usuario específico: si cuota tiene usuario, sólo aplica a ese usuario
            if (c.getUsuario() != null) {
                if (usuarioId == null || !c.getUsuario().getId().equals(usuarioId)) {
                    continue;
                }
            }

            // Filtrar territorialmente
            if (comunaImputacionId != null && !contieneComuna(c, comunaImputacionId)) {
                continue;
            }

            aplicables.add(c);
        }

        if (aplicables.isEmpty()) {
            return new EvaluacionCuotaResult(true, false, false, false, null,
                    BigDecimal.ZERO, BigDecimal.ZERO, "No aplica cuota de extracción.", null);
        }

        // 4. EVALUACIÓN CONCURRENTE ACUMULADA DE TODAS LAS CUOTAS APLICABLES
        // Toda cuota aplicable debe cumplirse concurrentemente.
        // Si al menos una cuota bloquea, la declaración es rechazada.
        // Se reporta la cuota más restrictiva (cuello de botella) como cuota aplicada principal.
        CuotaExtraccionModel cuotaCuelloBotella = null;
        double maxPctConsumo = -1.0;
        BigDecimal totalCuello = BigDecimal.ZERO;
        BigDecimal limiteCuello = BigDecimal.ZERO;

        EvaluacionCuotaResult bloqueoResult = null;
        List<String> advertencias = new ArrayList<>();
        String marcaAlerta = null;
        CuotaExtraccionModel cuotaMarcaAlerta = null;

        for (CuotaExtraccionModel c : aplicables) {
            // A. Verificar cierre administrativo
            if ("CERRADA".equalsIgnoreCase(c.getEstado())) {
                Date fechaCierre = c.getFechaCierre() != null ? c.getFechaCierre() : c.getFechaFin();
                if (fechaCierre != null && fechaEval.after(fechaCierre)) {
                    String accionCierre = configuracionGeneralService.getValor("cuota_accion_post_cierre", "ALERTA_CRITICA");
                    boolean bloquear = "BLOQUEO_TOTAL".equalsIgnoreCase(accionCierre) || "BLOQUEO".equalsIgnoreCase(accionCierre);
                    String msg = String.format("Bloqueo: La cuota %s se encuentra administrativamente CERRADA desde el %s. Declaración fuera de plazo.",
                            describirAlcance(c), fechaCierre);
                    if (bloquear) {
                        return new EvaluacionCuotaResult(false, true, false, true, "POSTERIOR_CIERRE",
                                BigDecimal.ZERO, BigDecimal.valueOf(c.getLimiteKg()), msg, c);
                    } else {
                        advertencias.add(msg);
                        marcaAlerta = "POSTERIOR_CIERRE";
                        cuotaMarcaAlerta = c;
                    }
                }
            }

            // B. Consumo acumulado y límite efectivo
            BigDecimal consumoAcumulado = ejecutarConsultaConsumo(c, fechaEval, perfil, usuarioId);
            boolean esCaptura = !"DESEMBARQUE".equalsIgnoreCase(c.getMetrica());
            BigDecimal limiteEfectivo = calcularLimiteEfectivo(c, fechaEval);

            BigDecimal nuevoMonto = esCaptura ? (capturaKg != null ? capturaKg : BigDecimal.ZERO)
                                              : (desembarqueKg != null ? desembarqueKg : BigDecimal.ZERO);
            BigDecimal total = consumoAcumulado.add(nuevoMonto);

            double pct = (limiteEfectivo.compareTo(BigDecimal.ZERO) > 0)
                    ? total.multiply(BigDecimal.valueOf(100)).divide(limiteEfectivo, 2, RoundingMode.HALF_UP).doubleValue()
                    : 100.0;

            // Tracking del cuello de botella (mayor porcentaje de consumo)
            if (cuotaCuelloBotella == null || pct > maxPctConsumo) {
                cuotaCuelloBotella = c;
                maxPctConsumo = pct;
                totalCuello = total;
                limiteCuello = limiteEfectivo;
            }

            // C. Comparar contra el límite
            if (total.compareTo(limiteEfectivo) > 0) {
                String modoCuota = (c.getModoAccion() != null && !c.getModoAccion().isBlank())
                        ? c.getModoAccion().trim().toUpperCase()
                        : configuracionGeneralService.getValor("cuota_accion_exceso_limite", "SOLO_ALERTA");
                boolean bloquear = "BLOQUEO_DECLARACION".equalsIgnoreCase(modoCuota) || "BLOQUEO".equalsIgnoreCase(modoCuota) || "BLOQUEO_TOTAL".equalsIgnoreCase(modoCuota);
                String metricaNombre = esCaptura ? "captura biológica" : "desembarque físico";
                BigDecimal exceso = total.subtract(limiteEfectivo);
                double sobreconsumoPct = (limiteEfectivo.compareTo(BigDecimal.ZERO) > 0)
                        ? (total.doubleValue() / limiteEfectivo.doubleValue()) * 100.0
                        : 100.0;
                String msg = String.format("Cuota %s (%s) de %.2f kg ha sido sobrepasada. Total acumulado con esta declaración: %.2f kg (exceso: %.2f kg, %.1f%% de consumo).",
                        describirAlcance(c), metricaNombre, limiteEfectivo, total, exceso, sobreconsumoPct);

                if (bloquear) {
                    if (bloqueoResult == null) {
                        bloqueoResult = new EvaluacionCuotaResult(false, false, true, true, "CUOTA_EXCEDIDA", total, limiteEfectivo, msg, c);
                    }
                } else {
                    advertencias.add(msg);
                    if (marcaAlerta == null) {
                        marcaAlerta = "CUOTA_EXCEDIDA";
                        cuotaMarcaAlerta = c;
                    }
                }
            }
        }

        // Si alguna cuota aplicable bloqueó, la declaración es rechazada
        if (bloqueoResult != null) {
            return bloqueoResult;
        }

        // Si no hubo bloqueo pero sí alertas
        if (marcaAlerta != null) {
            String msgAlerta = String.join(" | ", advertencias);
            return new EvaluacionCuotaResult(true, "POSTERIOR_CIERRE".equals(marcaAlerta), "CUOTA_EXCEDIDA".equals(marcaAlerta), false,
                    marcaAlerta, totalCuello, limiteCuello, msgAlerta, cuotaMarcaAlerta);
        }

        // Declaración dentro de todas las cuotas concurrentes evaluadas
        return new EvaluacionCuotaResult(true, false, false, false, null, totalCuello, limiteCuello, "Declaración dentro de la cuota.", cuotaCuelloBotella);
    }

    public int calcularEspecificidadCuota(CuotaExtraccionModel c) {
        int score = 0;
        if (c.getUsuario() != null) score += 64;
        if (c.getAmerb() != null) score += 32;
        if (c.getComuna() != null || (c.getComunas() != null && !c.getComunas().isEmpty())) score += 16;
        if (c.getProvincia() != null) score += 8;
        if (c.getRegion() != null) score += 4;
        if (c.getMacrozona() != null && !Boolean.TRUE.equals(c.getMacrozona().getEsNacional())) score += 2;
        if (score == 0) score = 1;
        return score;
    }

    // =========================================================================
    // COMPATIBILIDAD CON ENDPOINTS Y DASHBOARD ANTERIORES
    // =========================================================================

    public QuotaCheckResult checkDeclarationQuota(Long usuarioId, String perfil, Long especieId, Date fechaDeclaracion, BigDecimal nuevaCantidadKg) {
        EvaluacionCuotaResult res = evaluarCuotaDeclaracion(
                perfil, usuarioId, null, especieId, null, null,
                null, fechaDeclaracion, nuevaCantidadKg, nuevaCantidadKg);
        return new QuotaCheckResult(res.isPermite(), res.getMensaje());
    }

    public List<ControlCuotaDiariaDTO> getControlCuotasDiarioGlobal(
            Date startDate, Date endDate, String periodo, Long comunaId, Long extraccionTipoId, String perfil) {
        if (perfil != null && !perfil.isBlank()) {
            log.warn("Parámetro 'perfil' deprecado en getControlCuotasDiarioGlobal. Se favorece ámbito AREA_LIBRE.");
        }

        List<CuotaExtraccionModel> cuotas = cuotaRepository.findByAmbitoAndActivoTrue("AREA_LIBRE");
        if ((cuotas == null || cuotas.isEmpty()) && perfil != null && !perfil.isBlank()) {
            cuotas = cuotaRepository.findByPerfilAndActivoTrue(perfil.toUpperCase());
        }
        if (cuotas == null) cuotas = Collections.emptyList();

        if (comunaId != null) {
            cuotas = cuotas.stream()
                    .filter(c -> contieneComuna(c, comunaId))
                    .collect(Collectors.toList());
        }

        if (extraccionTipoId != null) {
            cuotas = cuotas.stream()
                    .filter(c -> c.getExtraccionTipo() == null || c.getExtraccionTipo().getId().equals(extraccionTipoId))
                    .collect(Collectors.toList());
        }

        if (startDate == null) {
            LocalDate localDate = new Date().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            startDate = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        }
        if (endDate == null) {
            endDate = startDate;
        }

        LocalDate sDate = toLocalDateSafe(startDate);
        LocalDate eDate = toLocalDateSafe(endDate);

        String[] nombresMeses = {"", "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
                                 "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"};

        List<ControlCuotaDiariaDTO> result = new ArrayList<>();
        for (CuotaExtraccionModel cuota : cuotas) {
            if (periodo != null && !periodo.isBlank() && !"ALL".equalsIgnoreCase(periodo) && !periodo.equalsIgnoreCase(cuota.getPeriodo())) {
                continue;
            }
            if (cuota.getEspecie() == null) {
                continue;
            }

            // T1.7: Conservar sólo cuotas cuya vigencia se solapa con [startDate, endDate] (hoy por defecto)
            // Las cuotas sin fechas (formato anterior) se siguen mostrando.
            if (cuota.getFechaInicio() != null || cuota.getFechaFin() != null) {
                LocalDate cInicio = cuota.getFechaInicio() != null ? toLocalDateSafe(cuota.getFechaInicio()) : LocalDate.MIN;
                LocalDate cFin = cuota.getFechaFin() != null ? toLocalDateSafe(cuota.getFechaFin()) : LocalDate.MAX;
                boolean seSolapan = !cInicio.isAfter(eDate) && !sDate.isAfter(cFin);
                if (!seSolapan) {
                    continue;
                }
            }

            BigDecimal sumVolumen = ejecutarConsultaConsumo(cuota, startDate, perfil, cuota.getUsuario() != null ? cuota.getUsuario().getId() : null);

            BigDecimal limiteNominal = (cuota.getLimiteKg() != null) ? BigDecimal.valueOf(cuota.getLimiteKg()) : BigDecimal.ZERO;
            BigDecimal limiteEfectivo = calcularLimiteEfectivo(cuota, startDate);

            Double porcentaje = 0.0;
            if (limiteEfectivo.compareTo(BigDecimal.ZERO) > 0) {
                porcentaje = sumVolumen.doubleValue() / limiteEfectivo.doubleValue() * 100.0;
            }

            String humedadNombre = cuota.getHumedadEstado() != null ? cuota.getHumedadEstado().getNombre() : null;
            String extraccionTipoNombre = cuota.getExtraccionTipo() != null ? cuota.getExtraccionTipo().getNombre() : null;
            String comunaNombre = cuota.getComuna() != null ? cuota.getComuna().getNombre() : null;
            Long comunaIdCuota = cuota.getComuna() != null ? cuota.getComuna().getId() : null;
            BigDecimal factor = null;
            String equivalencia = null;

            if (cuota.getHumedadEstado() != null && cuota.getEspecie() != null && !"DESEMBARQUE".equalsIgnoreCase(cuota.getMetrica())) {
                Optional<FactorConversionModel> fOpt = factorConversionService.findFactorVigente(
                        cuota.getEspecie().getId(), cuota.getHumedadEstado().getId(), startDate);
                if (fOpt.isPresent()) {
                    factor = fOpt.get().getFactor();
                    if (factor != null && factor.compareTo(BigDecimal.ONE) != 0) {
                        equivalencia = String.format("%,.0f kg %s ≡ %,.0f kg captura (factor %.2f)",
                                limiteNominal.doubleValue(),
                                humedadNombre != null ? humedadNombre.toLowerCase() : "seco",
                                limiteEfectivo.doubleValue(),
                                factor.doubleValue());
                    }
                }
            }

            Set<Long> cIds = idsComunas(cuota);
            String comunasNombre = null;
            if (!cIds.isEmpty()) {
                if (cuota.getComunas() != null && !cuota.getComunas().isEmpty()) {
                    comunasNombre = cuota.getComunas().stream().map(ComunaModel::getNombre).collect(Collectors.joining(" + "));
                } else if (cuota.getComuna() != null) {
                    comunasNombre = cuota.getComuna().getNombre();
                }
            }

            ControlCuotaDiariaDTO dto = ControlCuotaDiariaDTO.builder()
                .cuotaId(cuota.getId())
                .especieNombre(cuota.getEspecie().getNombre())
                .volumenExtraido(sumVolumen)
                .limiteCuota(limiteEfectivo)
                .limiteNominal(limiteNominal)
                .limiteEfectivo(limiteEfectivo)
                .humedadEstadoNombre(humedadNombre)
                .metrica(cuota.getMetrica() != null ? cuota.getMetrica() : "CAPTURA")
                .factorConversion(factor)
                .descripcionEquivalencia(equivalencia)
                .porcentajeUso(Math.round(porcentaje * 100.0) / 100.0)
                .alcance(describirAlcance(cuota))
                .periodo(cuota.getPeriodo())
                .ambito(cuota.getAmbito() != null ? cuota.getAmbito() : "AREA_LIBRE")
                .extraccionTipoNombre(extraccionTipoNombre)
                .comunaId(comunaIdCuota)
                .comunaNombre(comunaNombre)
                .comunaIds(cIds)
                .comunasNombre(comunasNombre)
                .fechaInicio(cuota.getFechaInicio() != null ? cuota.getFechaInicio().toString() : null)
                .fechaFin(cuota.getFechaFin() != null ? cuota.getFechaFin().toString() : null)
                .vigenciaFormateada(formatearVigencia(cuota, nombresMeses))
                .build();

            result.add(dto);
        }
        return result;
    }

    public List<ControlCuotaDiariaDTO> getControlCuotasDiarioGlobal(Date startDate, Date endDate, String periodo, String perfil) {
        return getControlCuotasDiarioGlobal(startDate, endDate, periodo, null, null, perfil);
    }

    public List<ControlCuotaDiariaDTO> getControlCuotasDiarioGlobal(Date startDate, Date endDate, String periodo, Long comunaId, Long extraccionTipoId) {
        return getControlCuotasDiarioGlobal(startDate, endDate, periodo, comunaId, extraccionTipoId, null);
    }

    private void resolverReferencias(CuotaExtraccionModel cuota) {
        if (cuota.getRegion() != null && cuota.getRegion().getId() != null) {
            cuota.setRegion(regionRepository.findById(cuota.getRegion().getId())
                    .orElseThrow(() -> new IllegalArgumentException("La región indicada no existe.")));
        }
        if (cuota.getMacrozona() != null && cuota.getMacrozona().getId() != null) {
            cuota.setMacrozona(macrozonaRepository.findById(cuota.getMacrozona().getId())
                    .orElseThrow(() -> new IllegalArgumentException("La macrozona indicada no existe.")));
        }
        if (cuota.getProvincia() != null && cuota.getProvincia().getId() != null) {
            cuota.setProvincia(provinciaRepository.findById(cuota.getProvincia().getId())
                    .orElseThrow(() -> new IllegalArgumentException("La provincia indicada no existe.")));
        }
        if (cuota.getComunas() != null && !cuota.getComunas().isEmpty()) {
            Set<ComunaModel> resueltas = new LinkedHashSet<>();
            for (ComunaModel c : cuota.getComunas()) {
                if (c != null && c.getId() != null) {
                    resueltas.add(comunaRepository.findById(c.getId())
                            .orElseThrow(() -> new IllegalArgumentException("La comuna indicada con ID " + c.getId() + " no existe.")));
                }
            }
            cuota.setComunas(resueltas);
            if (cuota.getComuna() == null && !resueltas.isEmpty()) {
                cuota.setComuna(resueltas.iterator().next());
            }
        } else if (cuota.getComuna() != null && cuota.getComuna().getId() != null) {
            ComunaModel com = comunaRepository.findById(cuota.getComuna().getId())
                    .orElseThrow(() -> new IllegalArgumentException("La comuna indicada no existe."));
            cuota.setComuna(com);
            cuota.setComunas(new LinkedHashSet<>(Collections.singletonList(com)));
        }
        if (cuota.getEspecie() != null && cuota.getEspecie().getId() != null) {
            cuota.setEspecie(especieRepository.findById(cuota.getEspecie().getId())
                    .orElseThrow(() -> new IllegalArgumentException("La especie indicada no existe.")));
        }
        if (cuota.getAmerb() != null && cuota.getAmerb().getId() != null) {
            cuota.setAmerb(amerbRepository.findById(cuota.getAmerb().getId())
                    .orElseThrow(() -> new IllegalArgumentException("El área de manejo indicada no existe.")));
        }
        if (cuota.getUsuario() != null && cuota.getUsuario().getId() != null) {
            cuota.setUsuario(usuarioRepository.findById(cuota.getUsuario().getId())
                    .orElseThrow(() -> new IllegalArgumentException("El usuario indicado no existe.")));
        }
        if (cuota.getExtraccionTipo() != null && cuota.getExtraccionTipo().getId() != null) {
            cuota.setExtraccionTipo(extraccionTipoRepository.findById(cuota.getExtraccionTipo().getId())
                    .orElseThrow(() -> new IllegalArgumentException("El método de extracción indicado no existe.")));
        }
        if (cuota.getHumedadEstado() != null && cuota.getHumedadEstado().getId() != null) {
            cuota.setHumedadEstado(humedadEstadoRepository.findById(cuota.getHumedadEstado().getId())
                    .orElseThrow(() -> new IllegalArgumentException("El estado de humedad indicado no existe.")));
        }
    }

    public java.util.Map<String, Object> getMaestros() {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("regiones", regionRepository.findAll().stream()
            .map(r -> java.util.Map.of("id", r.getId(), "nombre", r.getNombre() != null ? r.getNombre() : ""))
            .toList());
        out.put("macrozonas", macrozonaRepository.findByActivoTrue().stream()
            .map(mz -> {
                java.util.Map<String, Object> m = new java.util.HashMap<>();
                m.put("id", mz.getId());
                m.put("nombre", mz.getNombre() != null ? mz.getNombre() : "");
                m.put("codigo", mz.getCodigo() != null ? mz.getCodigo() : "");
                m.put("esNacional", Boolean.TRUE.equals(mz.getEsNacional()));
                return m;
            })
            .toList());
        out.put("provincias", provinciaRepository.findAll().stream()
            .map(p -> {
                java.util.Map<String, Object> m = new java.util.HashMap<>();
                m.put("id", p.getId());
                m.put("nombre", p.getNombre() != null ? p.getNombre() : "");
                if (p.getRegion() != null) m.put("regionId", p.getRegion().getId());
                return m;
            })
            .toList());
        out.put("comunas", comunaRepository.findAll().stream()
            .map(c -> {
                java.util.Map<String, Object> m = new java.util.HashMap<>();
                m.put("id", c.getId());
                m.put("nombre", c.getNombre() != null ? c.getNombre() : "");
                if (c.getRegion() != null) m.put("regionId", c.getRegion().getId());
                if (c.getProvincia() != null) m.put("provinciaId", c.getProvincia().getId());
                return m;
            })
            .toList());
        out.put("especies", especieRepository.findAll().stream()
            .map(e -> java.util.Map.of("id", e.getId(), "nombre", e.getNombre() != null ? e.getNombre() : ""))
            .toList());
        out.put("extraccionTipos", extraccionTipoRepository.findAll().stream()
            .map(et -> java.util.Map.of("id", et.getId(), "nombre", et.getNombre() != null ? et.getNombre() : ""))
            .toList());
        out.put("humedadEstados", humedadEstadoRepository.findAll().stream()
            .map(he -> java.util.Map.of("id", he.getId(), "nombre", he.getNombre() != null ? he.getNombre() : ""))
            .toList());
        out.put("amerbs", amerbRepository.findAll().stream()
            .map(a -> java.util.Map.of("id", a.getId(), "nombre", a.getNombre() != null ? a.getNombre() : ""))
            .toList());
        out.put("usuarios", usuarioRepository.findAll().stream()
            .map(u -> {
                java.util.Map<String, Object> m = new java.util.HashMap<>();
                m.put("id", u.getId());
                String nom = ((u.getNombres() != null ? u.getNombres() : "") + " " + (u.getApellidop() != null ? u.getApellidop() : "")).trim();
                m.put("nombre", nom.isEmpty() ? "Usuario " + u.getId() : nom);
                if (u.getRut() != null) m.put("rut", u.getRut());
                return m;
            })
            .toList());
        return out;
    }

    private void validarDatosBasicos(CuotaExtraccionModel cuota) {
        if (cuota.getLimiteKg() == null || cuota.getLimiteKg() <= 0) {
            throw new IllegalArgumentException("El límite de la cuota debe ser mayor que 0 kg.");
        }
        if (cuota.getPeriodo() == null || cuota.getPeriodo().isBlank()) {
            throw new IllegalArgumentException("El periodo de la cuota es obligatorio (DIARIO, MENSUAL, ANUAL).");
        }

        // T1.4: Validación de vigencia mensual en mismo mes calendario
        if ("MENSUAL".equalsIgnoreCase(cuota.getPeriodo()) && cuota.getFechaInicio() != null && cuota.getFechaFin() != null) {
            LocalDate inicio = toLocalDateSafe(cuota.getFechaInicio());
            LocalDate fin = toLocalDateSafe(cuota.getFechaFin());
            if (inicio.getYear() != fin.getYear() || inicio.getMonthValue() != fin.getMonthValue()) {
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MM-yyyy");
                throw new IllegalArgumentException(String.format(
                        "La vigencia mensual debe quedar dentro de un mismo mes (recibido: %s a %s).",
                        inicio.format(dtf), fin.format(dtf)));
            }
        }

        if (cuota.getAmbito() == null || cuota.getAmbito().isBlank()) {
            if (cuota.getAmerb() != null || "AREA".equalsIgnoreCase(cuota.getPerfil())) {
                cuota.setAmbito("AMERB");
            } else {
                cuota.setAmbito("AREA_LIBRE");
            }
        } else {
            String ambNorm = cuota.getAmbito().trim().toUpperCase();
            if (!java.util.Set.of("AREA_LIBRE", "AMERB").contains(ambNorm)) {
                throw new IllegalArgumentException("Ámbito de cuota inválido: " + cuota.getAmbito() + ". Los valores permitidos son AREA_LIBRE o AMERB.");
            }
            cuota.setAmbito(ambNorm);
        }

        if ("AREA_LIBRE".equalsIgnoreCase(cuota.getAmbito())) {
            // T1.2: Columna heredada, sin efecto en áreas libres
            cuota.setPerfil("RECOLECTOR");
        } else if (cuota.getPerfil() == null || cuota.getPerfil().isBlank()) {
            cuota.setPerfil("AMERB".equalsIgnoreCase(cuota.getAmbito()) ? "AREA" : "RECOLECTOR");
        }
        if (cuota.getNivelAgregacion() == null || cuota.getNivelAgregacion().trim().isEmpty()) {
            throw new IllegalArgumentException("El nivel de agregación de la cuota es obligatorio (COMUNA, PROVINCIA, REGION, MACROZONA, INDIVIDUAL, NACIONAL).");
        }
        String nivelNorm = cuota.getNivelAgregacion().trim().toUpperCase();
        if (!java.util.Set.of("COMUNA", "PROVINCIA", "REGION", "MACROZONA", "INDIVIDUAL", "NACIONAL").contains(nivelNorm)) {
            throw new IllegalArgumentException("Nivel de agregación inválido: " + cuota.getNivelAgregacion() + ". Los valores permitidos son COMUNA, PROVINCIA, REGION, MACROZONA, INDIVIDUAL, NACIONAL.");
        }
        cuota.setNivelAgregacion(nivelNorm);

        if (cuota.getEstado() == null || cuota.getEstado().trim().isEmpty()) {
            cuota.setEstado("ABIERTA");
        } else {
            String estNorm = cuota.getEstado().trim().toUpperCase();
            if (!java.util.Set.of("ABIERTA", "CERRADA").contains(estNorm)) {
                throw new IllegalArgumentException("Estado de cuota inválido: " + cuota.getEstado() + ". Los valores permitidos son ABIERTA o CERRADA.");
            }
            cuota.setEstado(estNorm);
        }
        // Regla normativa Sernapesca (T3):
        // Por defecto las cuotas se descuentan obligatoriamente en CAPTURA biológica corregida.
        // La métrica DESEMBARQUE sólo se permite si la resolución técnica de Subpesca lo explicita expresamente.
        if (cuota.getMetrica() == null || cuota.getMetrica().isBlank()) {
            cuota.setMetrica("CAPTURA");
        } else if ("DESEMBARQUE".equalsIgnoreCase(cuota.getMetrica())) {
            cuota.setMetrica("DESEMBARQUE");
            if (cuota.getResolucion() == null || cuota.getResolucion().trim().isEmpty()) {
                throw new IllegalArgumentException("Sernapesca definió que las cuotas se descuentan obligatoriamente con captura biológica corregida. La métrica DESEMBARQUE sólo se permite si la resolución técnica de Subpesca lo especifica expresamente (campo resolución obligatorio).");
            }
        } else if ("CAPTURA".equalsIgnoreCase(cuota.getMetrica())) {
            cuota.setMetrica("CAPTURA");
        } else {
            throw new IllegalArgumentException("Métrica inválida: " + cuota.getMetrica() + ". Los valores permitidos son CAPTURA o DESEMBARQUE.");
        }

        // Modo de acción (R3.2)
        if (cuota.getModoAccion() == null || cuota.getModoAccion().isBlank()) {
            cuota.setModoAccion("SOLO_ALERTA");
        } else {
            String modoNorm = cuota.getModoAccion().trim().toUpperCase();
            if (!java.util.Set.of("SOLO_ALERTA", "BLOQUEO_DECLARACION").contains(modoNorm)) {
                throw new IllegalArgumentException("Modo de acción inválido: " + cuota.getModoAccion() + ". Los valores permitidos son SOLO_ALERTA o BLOQUEO_DECLARACION.");
            }
            cuota.setModoAccion(modoNorm);
        }

        // T1.1: Validaciones de alcance territorial por nivel COMUNA y REGION
        if ("COMUNA".equals(nivelNorm)) {
            Set<Long> cIds = idsComunas(cuota);
            if (cIds.isEmpty()) {
                throw new IllegalArgumentException("Las cuotas de nivel COMUNA deben tener al menos una comuna asociada.");
            }
            // Validar que todas pertenezcan a la misma región
            Long regId = null;
            RegionModel regObj = null;
            if (cuota.getComunas() != null && !cuota.getComunas().isEmpty()) {
                for (ComunaModel com : cuota.getComunas()) {
                    RegionModel r = com.getRegion();
                    if (r == null && com.getId() != null) {
                        r = comunaRepository.findById(com.getId()).map(ComunaModel::getRegion).orElse(null);
                    }
                    if (r != null) {
                        if (regId == null) {
                            regId = r.getId();
                            regObj = r;
                        } else if (!regId.equals(r.getId())) {
                            throw new IllegalArgumentException("Todas las comunas seleccionadas deben pertenecer a la misma región.");
                        }
                    }
                }
                if (regObj != null) {
                    cuota.setRegion(regObj);
                }
                cuota.setProvincia(null);
                cuota.setMacrozona(null);
                if (!cuota.getComunas().isEmpty()) {
                    cuota.setComuna(cuota.getComunas().iterator().next());
                }
            } else if (cuota.getComuna() != null) {
                if (cuota.getComuna().getRegion() != null) {
                    cuota.setRegion(cuota.getComuna().getRegion());
                }
                cuota.setProvincia(null);
                cuota.setMacrozona(null);
            }
        } else if ("REGION".equals(nivelNorm)) {
            if (cuota.getRegion() == null) {
                throw new IllegalArgumentException("Las cuotas de nivel REGION deben especificar una región.");
            }
            if (!idsComunas(cuota).isEmpty()) {
                throw new IllegalArgumentException("Las cuotas de nivel REGION no deben tener comunas asociadas.");
            }
            cuota.setComuna(null);
            cuota.setComunas(new LinkedHashSet<>());
            cuota.setProvincia(null);
            cuota.setMacrozona(null);
        }
    }

    public void validarSolapamiento(CuotaExtraccionModel cuota) {
        if (!Boolean.TRUE.equals(cuota.getActivo())) {
            return;
        }

        List<CuotaExtraccionModel> activas = new ArrayList<>(cuotaRepository.findByActivoTrue());
        activas.removeIf(c -> cuota.getId() != null && cuota.getId().equals(c.getId()));

        String ambitoCuota = cuota.getAmbito() != null ? cuota.getAmbito().trim().toUpperCase() : "AREA_LIBRE";
        String nivelCuota = cuota.getNivelAgregacion() != null ? cuota.getNivelAgregacion().trim().toUpperCase() : "";

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MM-yyyy");

        for (CuotaExtraccionModel otra : activas) {
            String ambitoOtra = otra.getAmbito() != null ? otra.getAmbito().trim().toUpperCase() : "AREA_LIBRE";
            String nivelOtra = otra.getNivelAgregacion() != null ? otra.getNivelAgregacion().trim().toUpperCase() : "";

            if (!ambitoCuota.equals(ambitoOtra)) continue;
            if (!nivelCuota.equals(nivelOtra)) continue;
            if (!seSolapan(cuota, otra)) continue;
            if (!especiesComparables(cuota, otra)) continue;

            String nombreEsp = otra.getEspecie() != null ? otra.getEspecie().getNombre() : (cuota.getEspecie() != null ? cuota.getEspecie().getNombre() : "Todas las especies");
            String nombreMet = otra.getExtraccionTipo() != null ? otra.getExtraccionTipo().getNombre() : (cuota.getExtraccionTipo() != null ? cuota.getExtraccionTipo().getNombre() : "Todos los métodos");
            String iniStr = otra.getFechaInicio() != null ? toLocalDateSafe(otra.getFechaInicio()).format(dtf) : "indefinido";
            String finStr = otra.getFechaFin() != null ? toLocalDateSafe(otra.getFechaFin()).format(dtf) : "indefinido";

            if ("COMUNA".equals(nivelCuota)) {
                Set<Long> cIdsCuota = idsComunas(cuota);
                Set<Long> cIdsOtra = idsComunas(otra);
                Set<Long> inter = new LinkedHashSet<>(cIdsCuota);
                inter.retainAll(cIdsOtra);

                if (!inter.isEmpty()) {
                    Long cConflictoId = inter.iterator().next();
                    String nombreComuna = "ID " + cConflictoId;
                    if (cuota.getComunas() != null) {
                        for (ComunaModel cm : cuota.getComunas()) {
                            if (cm != null && cConflictoId.equals(cm.getId()) && cm.getNombre() != null) {
                                nombreComuna = cm.getNombre();
                                break;
                            }
                        }
                    }
                    if (("ID " + cConflictoId).equals(nombreComuna) && otra.getComunas() != null) {
                        for (ComunaModel cm : otra.getComunas()) {
                            if (cm != null && cConflictoId.equals(cm.getId()) && cm.getNombre() != null) {
                                nombreComuna = cm.getNombre();
                                break;
                            }
                        }
                    }
                    if (("ID " + cConflictoId).equals(nombreComuna) && cuota.getComuna() != null && cConflictoId.equals(cuota.getComuna().getId())) {
                        nombreComuna = cuota.getComuna().getNombre();
                    }
                    if (("ID " + cConflictoId).equals(nombreComuna) && otra.getComuna() != null && cConflictoId.equals(otra.getComuna().getId())) {
                        nombreComuna = otra.getComuna().getNombre();
                    }

                    throw new IllegalArgumentException(String.format(
                        "La comuna %s ya tiene una cuota activa de %s (%s) del %s al %s (cuota #%d).",
                        nombreComuna, nombreEsp, nombreMet, iniStr, finStr, otra.getId()
                    ));
                }
            } else if ("REGION".equals(nivelCuota)) {
                Long rIdCuota = idRegionDe(cuota);
                Long rIdOtra = idRegionDe(otra);
                if (rIdCuota != null && rIdCuota.equals(rIdOtra)) {
                    String nombreReg = nombreRegionDe(otra) != null ? nombreRegionDe(otra) : ("ID " + rIdOtra);
                    throw new IllegalArgumentException(String.format(
                        "La región %s ya tiene una cuota activa de %s (%s) del %s al %s (cuota #%d).",
                        nombreReg, nombreEsp, nombreMet, iniStr, finStr, otra.getId()
                    ));
                }
            }
        }
    }

    public void validarJerarquia(CuotaExtraccionModel cuota) {
        if (!Boolean.TRUE.equals(cuota.getActivo())) {
            return;
        }

        List<CuotaExtraccionModel> activas = new ArrayList<>(cuotaRepository.findByActivoTrue());
        activas.removeIf(c -> cuota.getId() != null && cuota.getId().equals(c.getId()));

        validarContraAmbitosSuperiores(cuota, activas);
    }

    private void validarContraAmbitosSuperiores(CuotaExtraccionModel cuota, List<CuotaExtraccionModel> activas) {
        String alcance = alcanceDe(cuota);
        Long regionId = idRegionDe(cuota);

        // 1. Validar contra AMERB si es usuario en AMERB
        if ("USUARIO".equals(alcance) && cuota.getAmerb() != null) {
            for (CuotaExtraccionModel padre : activas) {
                if ("AREA".equals(alcanceDe(padre))
                        && padre.getAmerb().getId().equals(cuota.getAmerb().getId())
                        && seSolapan(cuota, padre) && especiesComparables(cuota, padre)
                        && cuota.getLimiteKg() > padre.getLimiteKg()) {
                    throw new IllegalArgumentException(String.format(
                            "La cuota del usuario (%.2f kg) no puede superar la cuota del área de manejo «%s» (%.2f kg) para %s en periodo %s.",
                            cuota.getLimiteKg(), nombreAmerb(padre), padre.getLimiteKg(), nombreEspecie(padre), padre.getPeriodo()));
                }
            }
        }

        // 2. Validar contra Región (para cuotas subordinadas a una región)
        if (("USUARIO".equals(alcance) || "AREA".equals(alcance) || "COMUNA".equals(alcance) || "PROVINCIA".equals(alcance)) && regionId != null) {
            validarContraRegion(cuota, activas, regionId, "subordinada");
        }

        // 3. Validar contra Macrozonas superiores que cubran la región (N:M)
        if (regionId != null && !"NACIONAL".equals(alcance)) {
            List<MacrozonaModel> mzsCubren = macrozonaService.getMacrozonasForRegion(regionId, new Date());
            Set<Long> macrozonaIdsPadre = mzsCubren.stream().map(MacrozonaModel::getId).collect(Collectors.toSet());

            for (CuotaExtraccionModel padre : activas) {
                String alcancePadre = alcanceDe(padre);
                if (("MACROZONA".equals(alcancePadre) || "NACIONAL".equals(alcancePadre))
                        && padre.getMacrozona() != null
                        && macrozonaIdsPadre.contains(padre.getMacrozona().getId())
                        && seSolapan(cuota, padre) && especiesComparables(cuota, padre)
                        && cuota.getLimiteKg() > padre.getLimiteKg()) {
                    throw new IllegalArgumentException(String.format(
                            "La cuota %s (%.2f kg) no puede superar la cuota macrozonal de «%s» (%.2f kg) para %s en periodo %s.",
                            describirAlcance(cuota), cuota.getLimiteKg(), padre.getMacrozona().getNombre(), padre.getLimiteKg(),
                            nombreEspecie(padre), padre.getPeriodo()));
                }
            }
        }

        // 4. Si la cuota es MACROZONA intermedia, validar contra la cuota NACIONAL
        if ("MACROZONA".equals(alcance)) {
            for (CuotaExtraccionModel padre : activas) {
                if ("NACIONAL".equals(alcanceDe(padre))
                        && seSolapan(cuota, padre) && especiesComparables(cuota, padre)
                        && cuota.getLimiteKg() > padre.getLimiteKg()) {
                    throw new IllegalArgumentException(String.format(
                            "La cuota macrozonal «%s» (%.2f kg) no puede superar la cuota nacional (%.2f kg) para %s en periodo %s.",
                            cuota.getMacrozona().getNombre(), cuota.getLimiteKg(), padre.getLimiteKg(),
                            nombreEspecie(padre), padre.getPeriodo()));
                }
            }
        }
    }

    private void validarContraRegion(CuotaExtraccionModel cuota, List<CuotaExtraccionModel> activas,
                                     Long regionIdHija, String etiquetaHija) {
        if (regionIdHija == null) return;
        for (CuotaExtraccionModel padre : activas) {
            Long regionIdPadre = idRegionDe(padre);
            if ("REGION".equals(alcanceDe(padre))
                    && regionIdPadre != null
                    && regionIdHija.equals(regionIdPadre)
                    && seSolapan(cuota, padre) && especiesComparables(cuota, padre)
                    && cuota.getLimiteKg() > padre.getLimiteKg()) {
                throw new IllegalArgumentException(String.format(
                        "La cuota %s (%.2f kg) no puede superar la cuota regional de %s (%.2f kg) para %s en periodo %s.",
                        etiquetaHija, cuota.getLimiteKg(), nombreRegionDe(padre), padre.getLimiteKg(), nombreEspecie(padre), padre.getPeriodo()));
            }
        }
    }

    private Long idRegionDe(CuotaExtraccionModel c) {
        if (c.getRegion() != null) return c.getRegion().getId();
        if (c.getProvincia() != null && c.getProvincia().getRegion() != null) return c.getProvincia().getRegion().getId();
        if (c.getComuna() != null && c.getComuna().getRegion() != null) return c.getComuna().getRegion().getId();
        if (c.getComunas() != null && !c.getComunas().isEmpty()) {
            for (ComunaModel com : c.getComunas()) {
                if (com != null && com.getRegion() != null) return com.getRegion().getId();
            }
        }
        if (c.getAmerb() != null && c.getAmerb().getRegionModel() != null) return c.getAmerb().getRegionModel().getId();
        return null;
    }

    private String alcanceDe(CuotaExtraccionModel c) {
        if (c.getUsuario() != null) return "USUARIO";
        if (c.getAmerb() != null) return "AREA";
        if (!idsComunas(c).isEmpty()) return "COMUNA";
        if (c.getProvincia() != null) return "PROVINCIA";
        if (c.getRegion() != null) return "REGION";
        if (c.getMacrozona() != null) {
            return Boolean.TRUE.equals(c.getMacrozona().getEsNacional()) ? "NACIONAL" : "MACROZONA";
        }
        return "GLOBAL";
    }

    private String nombreRegionDe(CuotaExtraccionModel c) {
        if (c.getRegion() != null) return c.getRegion().getNombre();
        if (c.getProvincia() != null && c.getProvincia().getRegion() != null) return c.getProvincia().getRegion().getNombre();
        if (c.getComuna() != null && c.getComuna().getRegion() != null) return c.getComuna().getRegion().getNombre();
        if (c.getComunas() != null && !c.getComunas().isEmpty()) {
            for (ComunaModel com : c.getComunas()) {
                if (com != null && com.getRegion() != null && com.getRegion().getNombre() != null) {
                    return com.getRegion().getNombre();
                }
            }
        }
        if (c.getAmerb() != null && c.getAmerb().getRegionModel() != null) return c.getAmerb().getRegionModel().getNombre();
        return null;
    }

    private String nombreEspecie(CuotaExtraccionModel c) {
        return c.getEspecie() != null ? c.getEspecie().getNombre() : "todas las especies";
    }

    private String nombreAmerb(CuotaExtraccionModel c) {
        return c.getAmerb() != null ? c.getAmerb().getNombre() : "(sin área)";
    }

    private boolean mismoPeriodo(CuotaExtraccionModel a, CuotaExtraccionModel b) {
        return a.getPeriodo() != null && a.getPeriodo().equalsIgnoreCase(b.getPeriodo());
    }

    public boolean seSolapan(CuotaExtraccionModel a, CuotaExtraccionModel b) {
        if (a == null || b == null) return false;
        if (a.getFechaInicio() == null && a.getFechaFin() == null && b.getFechaInicio() == null && b.getFechaFin() == null) {
            return mismoPeriodo(a, b);
        }
        LocalDate aInicio = a.getFechaInicio() != null ? toLocalDateSafe(a.getFechaInicio()) : LocalDate.MIN;
        LocalDate aFin = a.getFechaFin() != null ? toLocalDateSafe(a.getFechaFin()) : LocalDate.MAX;
        LocalDate bInicio = b.getFechaInicio() != null ? toLocalDateSafe(b.getFechaInicio()) : LocalDate.MIN;
        LocalDate bFin = b.getFechaFin() != null ? toLocalDateSafe(b.getFechaFin()) : LocalDate.MAX;
        return !aInicio.isAfter(bFin) && !bInicio.isAfter(aFin);
    }

    private boolean especiesComparables(CuotaExtraccionModel a, CuotaExtraccionModel b) {
        String ambA = a.getAmbito() != null ? a.getAmbito().trim().toUpperCase() : "AREA_LIBRE";
        String ambB = b.getAmbito() != null ? b.getAmbito().trim().toUpperCase() : "AREA_LIBRE";
        if (!ambA.equals(ambB)) return false;

        if (a.getEspecie() != null && b.getEspecie() != null) {
            if (!a.getEspecie().getId().equals(b.getEspecie().getId())) return false;
        }
        if (a.getExtraccionTipo() != null && b.getExtraccionTipo() != null) {
            if (!a.getExtraccionTipo().getId().equals(b.getExtraccionTipo().getId())) return false;
        }
        return true;
    }

    public Set<Long> idsComunas(CuotaExtraccionModel c) {
        if (c == null) return Collections.emptySet();
        Set<Long> ids = new LinkedHashSet<>();
        if (c.getComunas() != null && !c.getComunas().isEmpty()) {
            for (ComunaModel com : c.getComunas()) {
                if (com != null && com.getId() != null) {
                    ids.add(com.getId());
                }
            }
        }
        if (ids.isEmpty() && c.getComuna() != null && c.getComuna().getId() != null) {
            ids.add(c.getComuna().getId());
        }
        return ids;
    }

    public String describirAlcance(CuotaExtraccionModel cuota) {
        if (cuota.getUsuario() != null) {
            String nombre = ((cuota.getUsuario().getNombres() != null ? cuota.getUsuario().getNombres() : "") + " " +
                             (cuota.getUsuario().getApellidop() != null ? cuota.getUsuario().getApellidop() : "")).trim();
            return nombre.isEmpty() ? "Actor " + cuota.getUsuario().getId() : nombre;
        }
        if (cuota.getAmerb() != null) {
            String nombre = cuota.getAmerb().getNombre();
            if (nombre == null || nombre.trim().isEmpty()) return "AMERB " + cuota.getAmerb().getId();
            return nombre.toUpperCase().startsWith("AMERB") ? nombre : "AMERB " + nombre;
        }
        Set<Long> cIds = idsComunas(cuota);
        if (!cIds.isEmpty()) {
            if (cuota.getComunas() != null && cuota.getComunas().size() > 1) {
                String nombres = cuota.getComunas().stream()
                        .map(cm -> cm.getNombre() != null ? cm.getNombre() : String.valueOf(cm.getId()))
                        .collect(Collectors.joining(" + "));
                return "Comunas " + nombres;
            }
            if (cuota.getComuna() != null) {
                String nombre = cuota.getComuna().getNombre();
                return (nombre != null && !nombre.isBlank()) ? "Comuna " + nombre : "Comuna " + cuota.getComuna().getId();
            }
        }
        if (cuota.getProvincia() != null) {
            String nombre = cuota.getProvincia().getNombre();
            return (nombre != null && !nombre.isBlank()) ? "Provincia " + nombre : "Provincia " + cuota.getProvincia().getId();
        }
        if (cuota.getRegion() != null) {
            String nombre = cuota.getRegion().getNombre();
            return (nombre != null && !nombre.isBlank()) ? "Región " + nombre : "Región " + cuota.getRegion().getId();
        }
        if (cuota.getMacrozona() != null) {
            String nombre = cuota.getMacrozona().getNombre();
            boolean esNac = Boolean.TRUE.equals(cuota.getMacrozona().getEsNacional());
            if (esNac) return "Nacional";
            return (nombre != null && !nombre.isBlank()) ? "Macrozona " + nombre : "Macrozona " + cuota.getMacrozona().getId();
        }
        return "Global";
    }

    public BigDecimal calcularConsumoAcumulado(CuotaExtraccionModel cuota, Date fechaEval) {
        if (cuota == null) return BigDecimal.ZERO;
        if (fechaEval == null) fechaEval = new Date();
        String perfil = cuota.getPerfil() != null ? cuota.getPerfil().toUpperCase() : "RECOLECTOR";
        Long targetUid = cuota.getUsuario() != null ? cuota.getUsuario().getId() : null;
        return ejecutarConsultaConsumo(cuota, fechaEval, perfil, targetUid);
    }

    public static class FiltroTerritorialCuota {
        private final String sqlFragment;
        private final Map<String, Object> parametros;

        public FiltroTerritorialCuota(String sqlFragment, Map<String, Object> parametros) {
            this.sqlFragment = sqlFragment;
            this.parametros = parametros;
        }

        public String getSqlFragment() {
            return sqlFragment;
        }

        public Map<String, Object> getParametros() {
            return parametros;
        }

        public void aplicarParametros(Query q) {
            for (Map.Entry<String, Object> entry : parametros.entrySet()) {
                q.setParameter(entry.getKey(), entry.getValue());
            }
        }
    }

    public static LocalDate toLocalDateSafe(Date date) {
        if (date == null) return null;
        if (date instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    public java.sql.Date[] calcularRangoFechas(CuotaExtraccionModel cuota, Date fechaEval) {
        LocalDate fechaLocal = toLocalDateSafe(fechaEval);
        LocalDate start, end;
        if (cuota.getFechaInicio() != null && cuota.getFechaFin() != null) {
            start = toLocalDateSafe(cuota.getFechaInicio());
            end = toLocalDateSafe(cuota.getFechaFin());
        } else if ("MENSUAL".equalsIgnoreCase(cuota.getPeriodo())) {
            start = fechaLocal.withDayOfMonth(1);
            end = fechaLocal.withDayOfMonth(fechaLocal.lengthOfMonth());
        } else if ("ANUAL".equalsIgnoreCase(cuota.getPeriodo())) {
            start = fechaLocal.withDayOfYear(1);
            end = fechaLocal.withDayOfYear(fechaLocal.lengthOfYear());
        } else { // DIARIO
            start = fechaLocal;
            end = fechaLocal;
        }
        return new java.sql.Date[]{java.sql.Date.valueOf(start), java.sql.Date.valueOf(end)};
    }

    public FiltroTerritorialCuota construirFiltroTerritorial(CuotaExtraccionModel cuota, String tableName, Long targetUsuarioId) {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        String modo = getModoImputacion();
        String colFecha = "DECLARACION".equalsIgnoreCase(modo) ? "fecha_declaracion" : "fecha_extraccion";

        boolean esPlantilla = Boolean.TRUE.equals(cuota.getEsPlantilla());
        String nivelAgregacion = cuota.getNivelAgregacion() != null ? cuota.getNivelAgregacion().toUpperCase().trim() : "";

        if (nivelAgregacion.isEmpty()) {
            if (cuota.getUsuario() != null) nivelAgregacion = "INDIVIDUAL";
            else if (cuota.getAmerb() != null) nivelAgregacion = "AREA";
            else if (cuota.getComuna() != null) nivelAgregacion = "COMUNA";
            else if (cuota.getProvincia() != null) nivelAgregacion = "PROVINCIA";
            else if (cuota.getRegion() != null) nivelAgregacion = "REGION";
            else if (cuota.getMacrozona() != null) {
                nivelAgregacion = Boolean.TRUE.equals(cuota.getMacrozona().getEsNacional()) ? "NACIONAL" : "MACROZONA";
            } else {
                nivelAgregacion = "NACIONAL";
            }
        }

        if (esPlantilla || "INDIVIDUAL".equals(nivelAgregacion) || cuota.getUsuario() != null) {
            sql.append("WHERE d.usuario_id = :filtroUsuarioId ");
            Long uid = (targetUsuarioId != null) ? targetUsuarioId : (cuota.getUsuario() != null ? cuota.getUsuario().getId() : null);
            params.put("filtroUsuarioId", uid);
        } else if (cuota.getAmerb() != null && "declaracion_area".equals(tableName)) {
            sql.append("WHERE d.amerb_id = :filtroAmerbId ");
            params.put("filtroAmerbId", cuota.getAmerb().getId());
        } else if ("declaracion_recolector".equals(tableName)) {
            sql.append("JOIN usuario u ON d.usuario_id = u.id ");
            if ("COMUNA".equals(nivelAgregacion)) {
                Set<Long> cIds = idsComunas(cuota);
                if (cIds.isEmpty()) {
                    throw new IllegalStateException("Cuota nivel COMUNA sin comuna asociada (id=" + cuota.getId() + ")");
                }
                sql.append("WHERE u.comuna_id IN (:filtroComunaIds) ");
                params.put("filtroComunaIds", cIds);
            } else if ("PROVINCIA".equals(nivelAgregacion)) {
                if (cuota.getProvincia() == null) {
                    throw new IllegalStateException("Cuota nivel PROVINCIA sin provincia asociada (id=" + cuota.getId() + ")");
                }
                sql.append("JOIN comuna c ON u.comuna_id = c.id WHERE c.provincia_id = :filtroProvinciaId ");
                params.put("filtroProvinciaId", cuota.getProvincia().getId());
            } else if ("REGION".equals(nivelAgregacion)) {
                if (cuota.getRegion() == null) {
                    throw new IllegalStateException("Cuota nivel REGION sin región asociada (id=" + cuota.getId() + ")");
                }
                sql.append("JOIN comuna c ON u.comuna_id = c.id WHERE c.region_id = :filtroRegionId ");
                params.put("filtroRegionId", cuota.getRegion().getId());
            } else if ("MACROZONA".equals(nivelAgregacion)) {
                if (cuota.getMacrozona() == null) {
                    throw new IllegalStateException("Cuota nivel MACROZONA sin macrozona asociada (id=" + cuota.getId() + ")");
                }
                sql.append("JOIN comuna c ON u.comuna_id = c.id ")
                   .append("JOIN macrozona_region mr ON c.region_id = mr.region_id ")
                   .append("  AND (mr.vigencia_inicio IS NULL OR d.").append(colFecha).append(" >= mr.vigencia_inicio) ")
                   .append("  AND (mr.vigencia_fin IS NULL OR d.").append(colFecha).append(" <= mr.vigencia_fin) ")
                   .append("WHERE mr.macrozona_id = :filtroMacrozonaId ");
                params.put("filtroMacrozonaId", cuota.getMacrozona().getId());
            } else if ("NACIONAL".equals(nivelAgregacion) || "GLOBAL".equals(nivelAgregacion)) {
                sql.append("WHERE 1=1 ");
            } else {
                throw new IllegalStateException("Nivel de agregación territorial desconocido: " + nivelAgregacion + " en cuota id=" + cuota.getId());
            }
        } else {
            // declaracion_armador o declaracion_area territorial
            if ("COMUNA".equals(nivelAgregacion)) {
                Set<Long> cIds = idsComunas(cuota);
                if (cIds.isEmpty()) {
                    throw new IllegalStateException("Cuota nivel COMUNA sin comuna asociada (id=" + cuota.getId() + ")");
                }
                sql.append("WHERE d.comuna_id IN (:filtroComunaIds) ");
                params.put("filtroComunaIds", cIds);
            } else if ("PROVINCIA".equals(nivelAgregacion)) {
                if (cuota.getProvincia() == null) {
                    throw new IllegalStateException("Cuota nivel PROVINCIA sin provincia asociada (id=" + cuota.getId() + ")");
                }
                sql.append("JOIN comuna c ON d.comuna_id = c.id WHERE c.provincia_id = :filtroProvinciaId ");
                params.put("filtroProvinciaId", cuota.getProvincia().getId());
            } else if ("REGION".equals(nivelAgregacion)) {
                if (cuota.getRegion() == null) {
                    throw new IllegalStateException("Cuota nivel REGION sin región asociada (id=" + cuota.getId() + ")");
                }
                sql.append("JOIN comuna c ON d.comuna_id = c.id WHERE c.region_id = :filtroRegionId ");
                params.put("filtroRegionId", cuota.getRegion().getId());
            } else if ("MACROZONA".equals(nivelAgregacion)) {
                if (cuota.getMacrozona() == null) {
                    throw new IllegalStateException("Cuota nivel MACROZONA sin macrozona asociada (id=" + cuota.getId() + ")");
                }
                sql.append("JOIN comuna c ON d.comuna_id = c.id ")
                   .append("JOIN macrozona_region mr ON c.region_id = mr.region_id ")
                   .append("  AND (mr.vigencia_inicio IS NULL OR d.").append(colFecha).append(" >= mr.vigencia_inicio) ")
                   .append("  AND (mr.vigencia_fin IS NULL OR d.").append(colFecha).append(" <= mr.vigencia_fin) ")
                   .append("WHERE mr.macrozona_id = :filtroMacrozonaId ");
                params.put("filtroMacrozonaId", cuota.getMacrozona().getId());
            } else if ("NACIONAL".equals(nivelAgregacion) || "GLOBAL".equals(nivelAgregacion)) {
                sql.append("WHERE 1=1 ");
            } else {
                throw new IllegalStateException("Nivel de agregación territorial desconocido: " + nivelAgregacion + " en cuota id=" + cuota.getId());
            }
        }

        return new FiltroTerritorialCuota(sql.toString(), params);
    }

    // =========================================================================
    // C5: CACHÉ CONCURRENTE L2 DE DOS FASES PARA CUOTAS DE AMPLIO ALCANCE
    // =========================================================================

    private static class CacheConsumoEntry {
        final BigDecimal consumo;
        final long timestamp;

        CacheConsumoEntry(BigDecimal consumo, long timestamp) {
            this.consumo = consumo;
            this.timestamp = timestamp;
        }
    }

    private final Map<String, CacheConsumoEntry> cacheConsumoL2 = new ConcurrentHashMap<>();

    public void invalidarCacheConsumo() {
        cacheConsumoL2.clear();
    }

    public boolean contieneComuna(CuotaExtraccionModel cuota, Long comunaId) {
        if (comunaId == null) return true;
        if (cuota == null) return false;

        Set<Long> cIds = idsComunas(cuota);
        if (!cIds.isEmpty()) {
            return cIds.contains(comunaId);
        }

        Optional<ComunaModel> comOpt = comunaRepository.findById(comunaId);
        if (comOpt.isEmpty()) return false;
        ComunaModel com = comOpt.get();

        // Provincia
        if (cuota.getProvincia() != null) {
            return com.getProvincia() != null && cuota.getProvincia().getId().equals(com.getProvincia().getId());
        }

        // Región
        if (cuota.getRegion() != null) {
            return com.getRegion() != null && cuota.getRegion().getId().equals(com.getRegion().getId());
        }

        // Macrozona
        if (cuota.getMacrozona() != null) {
            if (Boolean.TRUE.equals(cuota.getMacrozona().getEsNacional())) return true;
            if (com.getRegion() == null) return false;
            return macrozonaService.isRegionInMacrozona(cuota.getMacrozona().getId(), com.getRegion().getId(), new Date());
        }

        // Si no tiene alcance territorial restringido (Global / Nacional)
        return cuota.getAmerb() == null && cuota.getUsuario() == null;
    }

    private BigDecimal ejecutarQueryConsumo(
            String tableName, String colMetrica, FiltroTerritorialCuota filtro, CuotaExtraccionModel cuota, java.sql.Date[] rango) {
        String modo = getModoImputacion();
        String colFecha = "DECLARACION".equalsIgnoreCase(modo) ? "fecha_declaracion" : "fecha_extraccion";

        StringBuilder sql = new StringBuilder("SELECT COALESCE(SUM(d." + colMetrica + "), 0) FROM " + tableName + " d ");
        sql.append(filtro.getSqlFragment());
        if (cuota.getEspecie() != null) {
            sql.append("AND d.especie_id = :especieId ");
        }
        if (cuota.getExtraccionTipo() != null) {
            sql.append("AND d.extraccion_tipo_id = :extraccionTipoId ");
        }
        sql.append("AND d.").append(colFecha).append(" BETWEEN :startDate AND :endDate");

        Query q = entityManager.createNativeQuery(sql.toString());
        q.setParameter("startDate", rango[0]);
        q.setParameter("endDate", rango[1]);
        if (cuota.getEspecie() != null) q.setParameter("especieId", cuota.getEspecie().getId());
        if (cuota.getExtraccionTipo() != null) q.setParameter("extraccionTipoId", cuota.getExtraccionTipo().getId());
        filtro.aplicarParametros(q);

        Object singleResult = q.getSingleResult();
        return (singleResult != null) ? new BigDecimal(singleResult.toString()) : BigDecimal.ZERO;
    }

    private BigDecimal consumoAreaLibre(CuotaExtraccionModel cuota, Date fechaEval, Long targetUsuarioId) {
        java.sql.Date[] rango = calcularRangoFechas(cuota, fechaEval);
        boolean esCaptura = !"DESEMBARQUE".equalsIgnoreCase(cuota.getMetrica());
        String colMetrica = esCaptura ? "captura" : "desembarque";

        // 1. Consumo de recolectores — imputación por comuna de inscripción del usuario (u.comuna_id)
        FiltroTerritorialCuota filtroRecolector = construirFiltroTerritorial(cuota, "declaracion_recolector", targetUsuarioId);
        BigDecimal consumoRecolector = ejecutarQueryConsumo(
                "declaracion_recolector", colMetrica, filtroRecolector, cuota, rango);

        // 2. Consumo de armadores — imputación por caleta de desembarque (d.comuna_id)
        FiltroTerritorialCuota filtroArmador = construirFiltroTerritorial(cuota, "declaracion_armador", targetUsuarioId);
        BigDecimal consumoArmador = ejecutarQueryConsumo(
                "declaracion_armador", colMetrica, filtroArmador, cuota, rango);

        return consumoRecolector.add(consumoArmador);
    }

    private BigDecimal consumoTablaUnica(CuotaExtraccionModel cuota, Date fechaEval, String perfil, Long targetUsuarioId) {
        boolean esCaptura = !"DESEMBARQUE".equalsIgnoreCase(cuota.getMetrica());
        String colMetrica = esCaptura ? "captura" : "desembarque";
        String tableName = "declaracion_recolector";
        if ("ARMADOR".equalsIgnoreCase(perfil)) tableName = "declaracion_armador";
        if ("AREA".equalsIgnoreCase(perfil) || "ÁREA DE MANEJO".equalsIgnoreCase(perfil) || cuota.getAmerb() != null || "AMERB".equalsIgnoreCase(cuota.getAmbito())) {
            tableName = "declaracion_area";
        }

        java.sql.Date[] rango = calcularRangoFechas(cuota, fechaEval);
        FiltroTerritorialCuota filtro = construirFiltroTerritorial(cuota, tableName, targetUsuarioId);
        return ejecutarQueryConsumo(tableName, colMetrica, filtro, cuota, rango);
    }

    public BigDecimal ejecutarConsultaConsumo(CuotaExtraccionModel cuota, Date fechaEval, String perfil, Long targetUsuarioId) {
        String alcance = alcanceDe(cuota);
        boolean esAlcanceAmplio = "MACROZONA".equals(alcance) || "NACIONAL".equals(alcance) || "REGION".equals(alcance) || "GLOBAL".equals(alcance);

        boolean esAreaLibre = "AREA_LIBRE".equalsIgnoreCase(cuota.getAmbito()) || (cuota.getAmbito() == null && cuota.getAmerb() == null && !"AREA".equalsIgnoreCase(cuota.getPerfil()));

        String modoImputacion = getModoImputacion();
        String cachePerfil = esAreaLibre ? "AREALIBRE" : (perfil != null ? perfil : "DEFAULT");
        String cacheKey = (cuota.getId() != null ? cuota.getId() : 0L) + "_" +
                          (fechaEval != null ? fechaEval.getTime() / 86400000L : 0L) + "_" +
                          cachePerfil + "_" +
                          modoImputacion + "_" +
                          (targetUsuarioId != null ? targetUsuarioId : "ALL");

        long ahora = System.currentTimeMillis();
        BigDecimal limiteEfectivo = (cuota.getLimiteKg() != null) ? BigDecimal.valueOf(cuota.getLimiteKg()) : BigDecimal.ZERO;

        if (esAlcanceAmplio) {
            CacheConsumoEntry cached = cacheConsumoL2.get(cacheKey);
            if (cached != null && (ahora - cached.timestamp < 30_000)) {
                // Zona operativa normal (< 90%): responder desde memoria sin colapso de base de datos
                BigDecimal umbral90 = limiteEfectivo.multiply(new BigDecimal("0.90"));
                if (limiteEfectivo.compareTo(BigDecimal.ZERO) == 0 || cached.consumo.compareTo(umbral90) < 0) {
                    return cached.consumo;
                }
            }
        }

        BigDecimal consumo;
        if (esAreaLibre) {
            consumo = consumoAreaLibre(cuota, fechaEval, targetUsuarioId);
        } else {
            consumo = consumoTablaUnica(cuota, fechaEval, perfil, targetUsuarioId);
        }

        if (esAlcanceAmplio) {
            cacheConsumoL2.put(cacheKey, new CacheConsumoEntry(consumo, ahora));
        }

        return consumo;
    }

    public BigDecimal calcularLimiteEfectivo(CuotaExtraccionModel cuota, Date fechaEval) {
        if (cuota == null || cuota.getLimiteKg() == null) return BigDecimal.ZERO;
        BigDecimal limiteEfectivo = BigDecimal.valueOf(cuota.getLimiteKg());
        boolean esCaptura = !"DESEMBARQUE".equalsIgnoreCase(cuota.getMetrica());
        if (cuota.getHumedadEstado() != null && cuota.getEspecie() != null && esCaptura) {
            Optional<FactorConversionModel> factorOpt = factorConversionService.findFactorVigente(
                    cuota.getEspecie().getId(), cuota.getHumedadEstado().getId(), fechaEval);
            if (factorOpt.isPresent()) {
                limiteEfectivo = limiteEfectivo.multiply(factorOpt.get().getFactor()).setScale(2, RoundingMode.HALF_UP);
            }
        }
        return limiteEfectivo;
    }

    public java.util.Map<String, Object> getConsumoCuota(Long id) {
        CuotaExtraccionModel cuota = cuotaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cuota no encontrada con id: " + id));
        Date now = new Date();
        LocalDate nowLocal = toLocalDateSafe(now);
        LocalDate start, end;
        if (cuota.getFechaInicio() != null && cuota.getFechaFin() != null) {
            start = toLocalDateSafe(cuota.getFechaInicio());
            end = toLocalDateSafe(cuota.getFechaFin());
        } else if ("MENSUAL".equalsIgnoreCase(cuota.getPeriodo())) {
            start = nowLocal.withDayOfMonth(1);
            end = nowLocal.withDayOfMonth(nowLocal.lengthOfMonth());
        } else if ("ANUAL".equalsIgnoreCase(cuota.getPeriodo())) {
            start = nowLocal.withDayOfYear(1);
            end = nowLocal.withDayOfYear(nowLocal.lengthOfYear());
        } else {
            start = nowLocal;
            end = nowLocal;
        }

        BigDecimal limiteEfectivo = calcularLimiteEfectivo(cuota, now);
        BigDecimal consumido = calcularConsumoAcumulado(cuota, now);
        BigDecimal disponible = limiteEfectivo.subtract(consumido);
        if (disponible.compareTo(BigDecimal.ZERO) < 0) disponible = BigDecimal.ZERO;

        BigDecimal pctConsumido = BigDecimal.ZERO;
        BigDecimal pctRestante = BigDecimal.ZERO;
        if (limiteEfectivo.compareTo(BigDecimal.ZERO) > 0) {
            pctConsumido = consumido.multiply(BigDecimal.valueOf(100)).divide(limiteEfectivo, 2, RoundingMode.HALF_UP);
            pctRestante = disponible.multiply(BigDecimal.valueOf(100)).divide(limiteEfectivo, 2, RoundingMode.HALF_UP);
        }

        java.util.Map<String, Object> res = new java.util.HashMap<>();
        res.put("cuotaId", cuota.getId());
        res.put("limiteKg", cuota.getLimiteKg());
        res.put("limiteEfectivoKg", limiteEfectivo);
        res.put("consumidoKg", consumido);
        res.put("disponibleKg", disponible);
        res.put("pctConsumido", pctConsumido);
        res.put("pctRestante", pctRestante);
        res.put("estado", cuota.getEstado());
        res.put("fechaCierre", cuota.getFechaCierre());
        return res;
    }

    public CuotaExtraccionModel cerrarCuota(Long id) {
        CuotaExtraccionModel c = cuotaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cuota no encontrada con id: " + id));
        c.setEstado("CERRADA");
        c.setFechaCierre(new Date());
        c.setMotivoCierre("ADMINISTRATIVO");
        return cuotaRepository.save(c);
    }

    public List<CuotaListadoDTO> getListado(Integer anio, Integer mes, Long comunaId, Long extraccionTipoId, String ambito) {
        String ambitoNorm = (ambito != null && !ambito.isBlank()) ? ambito.trim().toUpperCase() : "AREA_LIBRE";
        List<CuotaExtraccionModel> todas = cuotaRepository.findAll();

        List<CuotaListadoDTO> result = new ArrayList<>();
        Date now = new Date();
        LocalDate nowLocal = toLocalDateSafe(now);

        String[] nombresMeses = {"", "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
                                 "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"};

        for (CuotaExtraccionModel c : todas) {
            String cAmbito = c.getAmbito() != null ? c.getAmbito().trim().toUpperCase() : "AREA_LIBRE";
            if (!"TODOS".equals(ambitoNorm)) {
                if ("AMERB".equals(ambitoNorm)) {
                    if (!"AMERB".equals(cAmbito) && c.getAmerb() == null && !"AREA".equalsIgnoreCase(c.getPerfil())) {
                        continue;
                    }
                } else if ("AREA_LIBRE".equals(ambitoNorm)) {
                    if ("AMERB".equals(cAmbito) || c.getAmerb() != null || "AREA".equalsIgnoreCase(c.getPerfil())) {
                        continue;
                    }
                } else {
                    if (!ambitoNorm.equals(cAmbito)) {
                        continue;
                    }
                }
            }

            // Filtro año
            if (anio != null && c.getFechaInicio() != null) {
                LocalDate ini = toLocalDateSafe(c.getFechaInicio());
                LocalDate fin = c.getFechaFin() != null ? toLocalDateSafe(c.getFechaFin()) : ini;
                if (anio < ini.getYear() || anio > fin.getYear()) {
                    continue;
                }
            }

            // Filtro mes
            if (mes != null && mes >= 1 && mes <= 12 && c.getFechaInicio() != null) {
                LocalDate ini = toLocalDateSafe(c.getFechaInicio());
                LocalDate fin = c.getFechaFin() != null ? toLocalDateSafe(c.getFechaFin()) : ini;
                int targetAnio = (anio != null) ? anio : ini.getYear();
                LocalDate mesInicio = LocalDate.of(targetAnio, mes, 1);
                LocalDate mesFin = mesInicio.withDayOfMonth(mesInicio.lengthOfMonth());
                if (ini.isAfter(mesFin) || fin.isBefore(mesInicio)) {
                    continue;
                }
            }

            // Filtro comuna
            if (comunaId != null) {
                if (!contieneComuna(c, comunaId)) {
                    continue;
                }
            }

            // Filtro tipo extracción
            if (extraccionTipoId != null) {
                if (c.getExtraccionTipo() == null || !c.getExtraccionTipo().getId().equals(extraccionTipoId)) {
                    continue;
                }
            }

            // Determinar si es formato anterior
            boolean esFormatoAnt = esFormatoAnterior(c);

            // Calcular fecha de evaluación para el consumo
            Date fechaEval = now;
            if (c.getFechaFin() != null) {
                LocalDate fin = toLocalDateSafe(c.getFechaFin());
                if (nowLocal.isAfter(fin)) {
                    fechaEval = c.getFechaFin();
                } else if (c.getFechaInicio() != null && nowLocal.isBefore(toLocalDateSafe(c.getFechaInicio()))) {
                    fechaEval = c.getFechaInicio();
                }
            }

            BigDecimal limiteNominal = (c.getLimiteKg() != null) ? BigDecimal.valueOf(c.getLimiteKg()) : BigDecimal.ZERO;
            BigDecimal limiteEfectivo = calcularLimiteEfectivo(c, fechaEval);
            BigDecimal factor = BigDecimal.ONE;
            if (c.getHumedadEstado() != null && c.getEspecie() != null && !"DESEMBARQUE".equalsIgnoreCase(c.getMetrica())) {
                factor = factorConversionService.findFactorVigente(c.getEspecie().getId(), c.getHumedadEstado().getId(), fechaEval)
                        .map(FactorConversionModel::getFactor)
                        .orElse(BigDecimal.ONE);
            }

            BigDecimal consumo = BigDecimal.ZERO;
            try {
                consumo = ejecutarConsultaConsumo(c, fechaEval, c.getPerfil(), (c.getUsuario() != null ? c.getUsuario().getId() : null));
            } catch (Exception e) {
                log.warn("No se pudo calcular consumo para cuota id={}: {}", c.getId(), e.getMessage());
            }

            BigDecimal saldo = limiteEfectivo.subtract(consumo);
            if (saldo.compareTo(BigDecimal.ZERO) < 0) saldo = BigDecimal.ZERO;

            double pct = (limiteEfectivo.compareTo(BigDecimal.ZERO) > 0)
                    ? (consumo.doubleValue() / limiteEfectivo.doubleValue()) * 100.0
                    : 0.0;

            String vigenciaDesc = formatearVigencia(c, nombresMeses);

            Set<Long> cIds = idsComunas(c);
            List<String> comNombres = new ArrayList<>();
            if (c.getComunas() != null && !c.getComunas().isEmpty()) {
                comNombres = c.getComunas().stream()
                        .map(cm -> cm.getNombre() != null ? cm.getNombre() : ("Comuna " + cm.getId()))
                        .toList();
            } else if (c.getComuna() != null) {
                comNombres = List.of(c.getComuna().getNombre() != null ? c.getComuna().getNombre() : ("Comuna " + c.getComuna().getId()));
            }
            String comunasNombre = String.join(" + ", comNombres);

            CuotaListadoDTO dto = CuotaListadoDTO.builder()
                    .id(c.getId())
                    .ambito(cAmbito)
                    .perfil(c.getPerfil())
                    .nivelAgregacion(c.getNivelAgregacion())
                    .regionId(c.getRegion() != null ? c.getRegion().getId() : (c.getComuna() != null && c.getComuna().getRegion() != null ? c.getComuna().getRegion().getId() : null))
                    .regionNombre(nombreRegionDe(c))
                    .comunaId(c.getComuna() != null ? c.getComuna().getId() : (!cIds.isEmpty() ? cIds.iterator().next() : null))
                    .comunaNombre(c.getComuna() != null ? c.getComuna().getNombre() : (!comNombres.isEmpty() ? comNombres.get(0) : null))
                    .comunaIds(cIds)
                    .comunaNombres(comNombres)
                    .comunasNombre(comunasNombre)
                    .especieId(c.getEspecie() != null ? c.getEspecie().getId() : null)
                    .especieNombre(c.getEspecie() != null ? c.getEspecie().getNombre() : "Sin especie")
                    .extraccionTipoId(c.getExtraccionTipo() != null ? c.getExtraccionTipo().getId() : null)
                    .extraccionTipoNombre(c.getExtraccionTipo() != null ? c.getExtraccionTipo().getNombre() : "Todos los métodos")
                    .periodo(c.getPeriodo())
                    .fechaInicio(c.getFechaInicio())
                    .fechaFin(c.getFechaFin())
                    .vigenciaDescripcion(vigenciaDesc)
                    .limiteKg(c.getLimiteKg())
                    .limiteNominal(limiteNominal)
                    .limiteEfectivo(limiteEfectivo)
                    .consumoAcumulado(consumo)
                    .porcentajeUso(Math.round(pct * 10.0) / 10.0)
                    .saldoDisponible(saldo)
                    .metrica(c.getMetrica() != null ? c.getMetrica() : "CAPTURA")
                    .humedadEstadoId(c.getHumedadEstado() != null ? c.getHumedadEstado().getId() : null)
                    .humedadEstadoNombre(c.getHumedadEstado() != null ? c.getHumedadEstado().getNombre() : "Sin conversión")
                    .factorConversion(factor)
                    .modoAccion(c.getModoAccion() != null ? c.getModoAccion() : "SOLO_ALERTA")
                    .resolucion(c.getResolucion())
                    .estado(c.getEstado() != null ? c.getEstado() : "ABIERTA")
                    .fechaCierre(c.getFechaCierre())
                    .motivoCierre(c.getMotivoCierre())
                    .activo(c.getActivo())
                    .alcance(describirAlcance(c))
                    .esFormatoAnterior(esFormatoAnt)
                    .build();

            result.add(dto);
        }

        result.sort((a, b) -> {
            int cmpAnt = Boolean.compare(Boolean.TRUE.equals(a.getEsFormatoAnterior()), Boolean.TRUE.equals(b.getEsFormatoAnterior()));
            if (cmpAnt != 0) return cmpAnt;
            if (a.getFechaInicio() != null && b.getFechaInicio() != null) {
                int cmpFecha = b.getFechaInicio().compareTo(a.getFechaInicio());
                if (cmpFecha != 0) return cmpFecha;
            } else if (a.getFechaInicio() != null) {
                return -1;
            } else if (b.getFechaInicio() != null) {
                return 1;
            }
            Long idA = a.getId() != null ? a.getId() : 0L;
            Long idB = b.getId() != null ? b.getId() : 0L;
            return idB.compareTo(idA);
        });

        return result;
    }

    public boolean esFormatoAnterior(CuotaExtraccionModel c) {
        if (Boolean.TRUE.equals(c.getEsPlantilla())) return true;
        if (c.getUsuario() != null) return true;
        if (c.getMacrozona() != null || c.getProvincia() != null) return true;
        String periodo = c.getPeriodo() != null ? c.getPeriodo().trim().toUpperCase() : "";
        if (!"MENSUAL".equals(periodo)) return true;
        if (c.getFechaInicio() == null || c.getFechaFin() == null) return true;
        String nivel = c.getNivelAgregacion() != null ? c.getNivelAgregacion().trim().toUpperCase() : "";
        if (!"COMUNA".equals(nivel) && !"REGION".equals(nivel)) return true;
        return false;
    }

    private String formatearVigencia(CuotaExtraccionModel c, String[] nombresMeses) {
        if (c.getFechaInicio() == null) {
            return c.getPeriodo() != null ? c.getPeriodo() : "Sin vigencia";
        }
        LocalDate ini = toLocalDateSafe(c.getFechaInicio());
        LocalDate fin = c.getFechaFin() != null ? toLocalDateSafe(c.getFechaFin()) : null;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MM-yyyy");

        if ("MENSUAL".equalsIgnoreCase(c.getPeriodo()) && fin != null && ini.getMonthValue() == fin.getMonthValue() && ini.getYear() == fin.getYear()) {
            String mesNombre = nombresMeses[ini.getMonthValue()] + " " + ini.getYear();
            if (ini.getDayOfMonth() == 1 && fin.getDayOfMonth() == fin.lengthOfMonth()) {
                return mesNombre;
            } else {
                return String.format("%s (%02d al %02d)", mesNombre, ini.getDayOfMonth(), fin.getDayOfMonth());
            }
        }
        if (fin != null) {
            return ini.format(dtf) + " al " + fin.format(dtf);
        }
        return "Desde " + ini.format(dtf);
    }

    public static class QuotaCheckResult {
        private boolean allowed;
        private String message;

        public QuotaCheckResult(boolean allowed, String message) {
            this.allowed = allowed;
            this.message = message;
        }

        public boolean isAllowed() { return allowed; }
        public String getMessage() { return message; }
    }
}