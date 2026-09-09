package com.trazalga.api.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.dto.ControlCuotaDiariaDTO;
import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.models.FactorConversionModel;
import com.trazalga.api.models.MacrozonaModel;
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

    public CuotaExtraccionModel save(CuotaExtraccionModel cuota) {
        resolverReferencias(cuota);
        validarDatosBasicos(cuota);
        validarJerarquia(cuota);
        invalidarCacheConsumo();
        return cuotaRepository.save(cuota);
    }

    public CuotaExtraccionModel update(Long id, CuotaExtraccionModel request) {
        CuotaExtraccionModel cuota = cuotaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cuota no encontrada con ID: " + id));

        resolverReferencias(request);
        cuota.setPerfil(request.getPerfil());
        cuota.setEspecie(request.getEspecie());
        cuota.setRegion(request.getRegion());
        cuota.setMacrozona(request.getMacrozona());
        cuota.setProvincia(request.getProvincia());
        cuota.setComuna(request.getComuna());
        cuota.setUsuario(request.getUsuario());
        cuota.setAmerb(request.getAmerb());
        cuota.setExtraccionTipo(request.getExtraccionTipo());
        cuota.setHumedadEstado(request.getHumedadEstado());
        if (request.getNivelAgregacion() != null) cuota.setNivelAgregacion(request.getNivelAgregacion());
        if (request.getMetrica() != null) cuota.setMetrica(request.getMetrica());
        if (request.getEsPlantilla() != null) cuota.setEsPlantilla(request.getEsPlantilla());
        cuota.setPeriodo(request.getPeriodo());
        cuota.setLimiteKg(request.getLimiteKg());
        cuota.setFechaInicio(request.getFechaInicio());
        cuota.setFechaFin(request.getFechaFin());
        cuota.setResolucion(request.getResolucion());
        if (request.getEstado() != null) cuota.setEstado(request.getEstado());
        cuota.setFechaCierre(request.getFechaCierre());
        if (request.getActivo() != null) cuota.setActivo(request.getActivo());

        validarDatosBasicos(cuota);
        validarJerarquia(cuota);
        invalidarCacheConsumo();
        return cuotaRepository.save(cuota);
    }

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

        Date fechaEval = (fechaDeclaracion != null) ? fechaDeclaracion : (fechaExtraccion != null ? fechaExtraccion : new Date());

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

        // 3. Filtrar cuotas aplicables por perfil, especie, método, fechas
        List<CuotaExtraccionModel> aplicables = new ArrayList<>();
        for (CuotaExtraccionModel c : todasCuotas) {
            if (c.getPerfil() != null && !c.getPerfil().equalsIgnoreCase(perfil)) {
                continue;
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
            if (comunaImputacionId != null) {
                Optional<ComunaModel> comOpt = comunaRepository.findById(comunaImputacionId);
                if (comOpt.isPresent()) {
                    ComunaModel com = comOpt.get();
                    if (c.getComuna() != null && !c.getComuna().getId().equals(com.getId())) {
                        continue;
                    }
                    if (c.getProvincia() != null && (com.getProvincia() == null || !c.getProvincia().getId().equals(com.getProvincia().getId()))) {
                        continue;
                    }
                    if (c.getRegion() != null && (com.getRegion() == null || !c.getRegion().getId().equals(com.getRegion().getId()))) {
                        continue;
                    }
                    if (c.getMacrozona() != null) {
                        Long regId = com.getRegion() != null ? com.getRegion().getId() : null;
                        if (regId == null || !macrozonaService.isRegionInMacrozona(c.getMacrozona().getId(), regId, fechaEval)) {
                            continue;
                        }
                    }
                }
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
                String accionExceso = configuracionGeneralService.getValor("cuota_accion_exceso_limite", "ALERTA_EXCESO");
                boolean bloquear = "BLOQUEO_DECLARACION".equalsIgnoreCase(accionExceso) || "BLOQUEO".equalsIgnoreCase(accionExceso) || "BLOQUEO_TOTAL".equalsIgnoreCase(accionExceso);
                String metricaNombre = esCaptura ? "captura biológica" : "desembarque físico";
                String msg = String.format("Bloqueo: La cuota %s (%s) de %.2f kg ha sido sobrepasada. Total acumulado con esta declaración: %.2f kg (límite: %.2f kg).",
                        describirAlcance(c), metricaNombre, limiteEfectivo, total, limiteEfectivo);

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
        if (c.getComuna() != null) score += 16;
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

    public List<ControlCuotaDiariaDTO> getControlCuotasDiarioGlobal(Date startDate, Date endDate, String periodo, String perfil) {
        if (periodo == null || periodo.isEmpty()) periodo = "DIARIO";
        if (perfil == null || perfil.isEmpty()) perfil = "RECOLECTOR";

        List<ControlCuotaDiariaDTO> result = new ArrayList<>();
        List<CuotaExtraccionModel> cuotas = cuotaRepository.findByPerfilAndActivoTrue(perfil.toUpperCase());

        if (startDate == null) {
            LocalDate localDate = new Date().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            startDate = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        }
        if (endDate == null) {
            endDate = startDate;
        }

        for (CuotaExtraccionModel cuota : cuotas) {
            if (!periodo.equalsIgnoreCase(cuota.getPeriodo()) || cuota.getEspecie() == null) {
                continue;
            }

            BigDecimal sumVolumen = ejecutarConsultaConsumo(cuota, startDate, perfil, cuota.getUsuario() != null ? cuota.getUsuario().getId() : null);

            BigDecimal limiteNominal = (cuota.getLimiteKg() != null) ? BigDecimal.valueOf(cuota.getLimiteKg()) : BigDecimal.ZERO;
            BigDecimal limiteEfectivo = calcularLimiteEfectivo(cuota, startDate);

            Double porcentaje = 0.0;
            if (limiteEfectivo.compareTo(BigDecimal.ZERO) > 0) {
                porcentaje = sumVolumen.doubleValue() / limiteEfectivo.doubleValue() * 100.0;
            }

            String humedadNombre = cuota.getHumedadEstado() != null ? cuota.getHumedadEstado().getNombre() : null;
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

            ControlCuotaDiariaDTO dto = ControlCuotaDiariaDTO.builder()
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
                .build();

            result.add(dto);
        }
        return result;
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
        if (cuota.getComuna() != null && cuota.getComuna().getId() != null) {
            cuota.setComuna(comunaRepository.findById(cuota.getComuna().getId())
                    .orElseThrow(() -> new IllegalArgumentException("La comuna indicada no existe.")));
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
        if (cuota.getPerfil() == null || cuota.getPerfil().isBlank()) {
            throw new IllegalArgumentException("El perfil de la cuota es obligatorio.");
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
                        && mismoPeriodo(cuota, padre) && especiesComparables(cuota, padre)
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
                        && mismoPeriodo(cuota, padre) && especiesComparables(cuota, padre)
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
                        && mismoPeriodo(cuota, padre) && especiesComparables(cuota, padre)
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
                    && mismoPeriodo(cuota, padre) && especiesComparables(cuota, padre)
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
        if (c.getAmerb() != null && c.getAmerb().getRegionModel() != null) return c.getAmerb().getRegionModel().getId();
        return null;
    }

    private String alcanceDe(CuotaExtraccionModel c) {
        if (c.getUsuario() != null) return "USUARIO";
        if (c.getAmerb() != null) return "AREA";
        if (c.getComuna() != null) return "COMUNA";
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

    private boolean especiesComparables(CuotaExtraccionModel a, CuotaExtraccionModel b) {
        if (a.getEspecie() == null || b.getEspecie() == null) return true;
        return a.getEspecie().getId().equals(b.getEspecie().getId());
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
        if (cuota.getComuna() != null) return "Comuna " + cuota.getComuna().getNombre();
        if (cuota.getProvincia() != null) return "Provincia " + cuota.getProvincia().getNombre();
        if (cuota.getRegion() != null) return "Región " + cuota.getRegion().getNombre();
        if (cuota.getMacrozona() != null) {
            return Boolean.TRUE.equals(cuota.getMacrozona().getEsNacional()) ? "Nacional" : "Macrozona " + cuota.getMacrozona().getNombre();
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

    public java.sql.Date[] calcularRangoFechas(CuotaExtraccionModel cuota, Date fechaEval) {
        LocalDate fechaLocal = fechaEval.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate start, end;
        if (cuota.getFechaInicio() != null && cuota.getFechaFin() != null) {
            start = cuota.getFechaInicio().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            end = cuota.getFechaFin().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
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

        boolean esPlantilla = Boolean.TRUE.equals(cuota.getEsPlantilla());
        String nivelAgregacion = cuota.getNivelAgregacion() != null ? cuota.getNivelAgregacion().toUpperCase() : "COMUNA";

        if (esPlantilla || "INDIVIDUAL".equals(nivelAgregacion) || cuota.getUsuario() != null) {
            sql.append("WHERE d.usuario_id = :filtroUsuarioId ");
            Long uid = (targetUsuarioId != null) ? targetUsuarioId : (cuota.getUsuario() != null ? cuota.getUsuario().getId() : null);
            params.put("filtroUsuarioId", uid);
        } else if (cuota.getAmerb() != null && "declaracion_area".equals(tableName)) {
            sql.append("WHERE d.amerb_id = :filtroAmerbId ");
            params.put("filtroAmerbId", cuota.getAmerb().getId());
        } else if ("declaracion_recolector".equals(tableName)) {
            sql.append("JOIN usuario u ON d.usuario_id = u.id ");
            if ("COMUNA".equals(nivelAgregacion) && cuota.getComuna() != null) {
                sql.append("WHERE u.comuna_id = :filtroComunaId ");
                params.put("filtroComunaId", cuota.getComuna().getId());
            } else if ("PROVINCIA".equals(nivelAgregacion) && cuota.getProvincia() != null) {
                sql.append("JOIN comuna c ON u.comuna_id = c.id WHERE c.provincia_id = :filtroProvinciaId ");
                params.put("filtroProvinciaId", cuota.getProvincia().getId());
            } else if ("REGION".equals(nivelAgregacion) && cuota.getRegion() != null) {
                sql.append("JOIN comuna c ON u.comuna_id = c.id WHERE c.region_id = :filtroRegionId ");
                params.put("filtroRegionId", cuota.getRegion().getId());
            } else if ("MACROZONA".equals(nivelAgregacion) && cuota.getMacrozona() != null) {
                sql.append("JOIN comuna c ON u.comuna_id = c.id ")
                   .append("JOIN macrozona_region mr ON c.region_id = mr.region_id ")
                   .append("  AND (mr.vigencia_inicio IS NULL OR d.fecha_declaracion >= mr.vigencia_inicio) ")
                   .append("  AND (mr.vigencia_fin IS NULL OR d.fecha_declaracion <= mr.vigencia_fin) ")
                   .append("WHERE mr.macrozona_id = :filtroMacrozonaId ");
                params.put("filtroMacrozonaId", cuota.getMacrozona().getId());
            } else {
                sql.append("WHERE 1=1 ");
            }
        } else {
            // declaracion_armador o declaracion_area territorial
            if ("COMUNA".equals(nivelAgregacion) && cuota.getComuna() != null) {
                sql.append("WHERE d.comuna_id = :filtroComunaId ");
                params.put("filtroComunaId", cuota.getComuna().getId());
            } else if ("PROVINCIA".equals(nivelAgregacion) && cuota.getProvincia() != null) {
                sql.append("JOIN comuna c ON d.comuna_id = c.id WHERE c.provincia_id = :filtroProvinciaId ");
                params.put("filtroProvinciaId", cuota.getProvincia().getId());
            } else if ("REGION".equals(nivelAgregacion) && cuota.getRegion() != null) {
                sql.append("JOIN comuna c ON d.comuna_id = c.id WHERE c.region_id = :filtroRegionId ");
                params.put("filtroRegionId", cuota.getRegion().getId());
            } else if ("MACROZONA".equals(nivelAgregacion) && cuota.getMacrozona() != null) {
                sql.append("JOIN comuna c ON d.comuna_id = c.id ")
                   .append("JOIN macrozona_region mr ON c.region_id = mr.region_id ")
                   .append("  AND (mr.vigencia_inicio IS NULL OR d.fecha_declaracion >= mr.vigencia_inicio) ")
                   .append("  AND (mr.vigencia_fin IS NULL OR d.fecha_declaracion <= mr.vigencia_fin) ")
                   .append("WHERE mr.macrozona_id = :filtroMacrozonaId ");
                params.put("filtroMacrozonaId", cuota.getMacrozona().getId());
            } else {
                sql.append("WHERE 1=1 ");
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

    private BigDecimal ejecutarConsultaConsumo(CuotaExtraccionModel cuota, Date fechaEval, String perfil, Long targetUsuarioId) {
        String alcance = alcanceDe(cuota);
        boolean esAlcanceAmplio = "MACROZONA".equals(alcance) || "NACIONAL".equals(alcance) || "REGION".equals(alcance) || "GLOBAL".equals(alcance);

        String cacheKey = (cuota.getId() != null ? cuota.getId() : 0L) + "_" +
                          (fechaEval != null ? fechaEval.getTime() / 86400000L : 0L) + "_" +
                          perfil + "_" +
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

        // Zona crítica (>= 90%) o caché expirada: consulta directa transaccional
        boolean esCaptura = !"DESEMBARQUE".equalsIgnoreCase(cuota.getMetrica());
        String colMetrica = esCaptura ? "captura" : "desembarque";
        String tableName = "declaracion_recolector";
        if ("ARMADOR".equalsIgnoreCase(perfil)) tableName = "declaracion_armador";
        if ("AREA".equalsIgnoreCase(perfil) || "ÁREA DE MANEJO".equalsIgnoreCase(perfil)) tableName = "declaracion_area";

        java.sql.Date[] rango = calcularRangoFechas(cuota, fechaEval);
        java.sql.Date sqlStart = rango[0];
        java.sql.Date sqlEnd = rango[1];

        FiltroTerritorialCuota filtro = construirFiltroTerritorial(cuota, tableName, targetUsuarioId);

        StringBuilder sql = new StringBuilder("SELECT COALESCE(SUM(d." + colMetrica + "), 0) FROM " + tableName + " d ");
        sql.append(filtro.getSqlFragment());
        if (cuota.getEspecie() != null) {
            sql.append("AND d.especie_id = :especieId ");
        }
        if (cuota.getExtraccionTipo() != null) {
            sql.append("AND d.extraccion_tipo_id = :extraccionTipoId ");
        }
        sql.append("AND d.fecha_declaracion BETWEEN :startDate AND :endDate");

        Query q = entityManager.createNativeQuery(sql.toString());
        q.setParameter("startDate", sqlStart);
        q.setParameter("endDate", sqlEnd);
        if (cuota.getEspecie() != null) q.setParameter("especieId", cuota.getEspecie().getId());
        if (cuota.getExtraccionTipo() != null) q.setParameter("extraccionTipoId", cuota.getExtraccionTipo().getId());
        filtro.aplicarParametros(q);

        Object singleResult = q.getSingleResult();
        BigDecimal consumo = (singleResult != null) ? new BigDecimal(singleResult.toString()) : BigDecimal.ZERO;

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
        LocalDate nowLocal = now.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate start, end;
        if (cuota.getFechaInicio() != null && cuota.getFechaFin() != null) {
            start = cuota.getFechaInicio().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            end = cuota.getFechaFin().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
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
        return cuotaRepository.save(c);
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