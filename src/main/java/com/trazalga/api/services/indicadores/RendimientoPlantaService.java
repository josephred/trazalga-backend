package com.trazalga.api.services.indicadores;

import com.trazalga.api.exceptions.ValidacionPlantaException;
import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import com.trazalga.api.models.DeclaracionPlantaProduccionModel;
import com.trazalga.api.models.FactorConversionModel;
import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.models.ProductoModel;
import com.trazalga.api.models.RendimientoRangoModel;
import com.trazalga.api.repositories.IDeclaracionPlantaAbastecimientoRepository;
import com.trazalga.api.repositories.IHumedadEstadoRepository;
import com.trazalga.api.repositories.IProductoRepository;
import com.trazalga.api.repositories.IRendimientoRangoRepository;
import com.trazalga.api.repositories.IUsuarioRepository;
import com.trazalga.api.services.ConfiguracionGeneralService;
import com.trazalga.api.services.FactorConversionService;
import com.trazalga.api.services.hallazgos.CriterioHallazgo;
import com.trazalga.api.services.hallazgos.HallazgoService;
import com.trazalga.api.services.trazabilidad.SeleccionTokens;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Cálculo de masa de entrada (NP.2) y evaluación de rendimiento en planta (NP.4 / NP.5).
 */
@Service
@Slf4j
public class RendimientoPlantaService {

    public static final String BASE_CAPTURA_ORIGEN = "CAPTURA_ORIGEN";
    public static final String BASE_MASA_RECIBIDA = "MASA_RECIBIDA";

    public static final String FUENTE_TABLA = "TABLA";
    public static final String FUENTE_FACTORES = "FACTORES";
    public static final String FUENTE_TABLA_ORIGEN = "TABLA_ORIGEN";
    public static final String FUENTE_FACTORES_ORIGEN = "FACTORES_ORIGEN";
    public static final String FUENTE_RESPALDO_TABLA = "RESPALDO_MASA_TABLA";
    public static final String FUENTE_RESPALDO_FACTORES = "RESPALDO_MASA_FACTORES";
    public static final String FUENTE_SIN_REFERENCIA = "SIN_REFERENCIA";

    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final Locale LOCALE_CL = Locale.forLanguageTag("es-CL");

    @Autowired private IDeclaracionPlantaAbastecimientoRepository abastecimientoRepository;
    @Autowired private IRendimientoRangoRepository rendimientoRangoRepository;
    @Autowired private FactorConversionService factorConversionService;
    @Autowired private ConfiguracionGeneralService configService;
    @Autowired private IProductoRepository productoRepository;
    @Autowired(required = false) private IHumedadEstadoRepository humedadEstadoRepository;
    @Autowired(required = false) private IUsuarioRepository usuarioRepository;
    @Autowired(required = false) private HallazgoService hallazgoService;
    @Autowired(required = false) private CapturaOrigenProduccion capturaOrigenProduccion;

    public RendimientoPlantaService() {
    }

    /** Constructor para pruebas unitarias puras (sin captura de origen). */
    public RendimientoPlantaService(
            IDeclaracionPlantaAbastecimientoRepository abastecimientoRepository,
            IRendimientoRangoRepository rendimientoRangoRepository,
            FactorConversionService factorConversionService,
            ConfiguracionGeneralService configService,
            IProductoRepository productoRepository,
            HallazgoService hallazgoService) {
        this(abastecimientoRepository, rendimientoRangoRepository, factorConversionService,
                configService, productoRepository, hallazgoService, null);
    }

    /** Constructor completo para pruebas unitarias con captura de origen (QR). */
    public RendimientoPlantaService(
            IDeclaracionPlantaAbastecimientoRepository abastecimientoRepository,
            IRendimientoRangoRepository rendimientoRangoRepository,
            FactorConversionService factorConversionService,
            ConfiguracionGeneralService configService,
            IProductoRepository productoRepository,
            HallazgoService hallazgoService,
            CapturaOrigenProduccion capturaOrigenProduccion) {
        this.abastecimientoRepository = abastecimientoRepository;
        this.rendimientoRangoRepository = rendimientoRangoRepository;
        this.factorConversionService = factorConversionService;
        this.configService = configService;
        this.productoRepository = productoRepository;
        this.hallazgoService = hallazgoService;
        this.capturaOrigenProduccion = capturaOrigenProduccion;
    }

    public static boolean esFuenteCapturaOrigen(String fuente) {
        return FUENTE_TABLA_ORIGEN.equals(fuente) || FUENTE_FACTORES_ORIGEN.equals(fuente);
    }

    public record ResultadoRendimiento(
            boolean sinLotes,
            BigDecimal masaEntradaKg,
            BigDecimal masaEntradaEquivalenteKg,
            BigDecimal masaProductoEquivalenteKg,
            BigDecimal rendimientoPct,
            BigDecimal rendimientoMaxTeoricoPct,
            BigDecimal rendimientoMinAplicadoPct,
            String fuente,
            boolean anomalo,
            String tipoAnomalia,
            String detalleAnomalia,
            String parametroCriterio,
            String umbralCriterio,
            Date evaluadoEn,
            BigDecimal capturaOrigenKg,
            boolean capturaOrigenCompleta,
            BigDecimal rendimientoFinalPct,
            String baseAplicada,
            BigDecimal rendimientoEvaluadoPct
    ) {
    }

    /**
     * Calcula la masa de entrada real desde los lotes seleccionados (NP.2), evalúa el rendimiento
     * declarado vs el rango teórico (NP.4) y actualiza los campos de auditoría en la entidad.
     */
    public ResultadoRendimiento evaluarYActualizar(DeclaracionPlantaProduccionModel declaracion) {
        List<Long> loteIds = SeleccionTokens.idsParaTipo(
                SeleccionTokens.parse(declaracion.getDeclaracionesSeleccionadas()), "PLANTA_ABASTECIMIENTO");
        List<DeclaracionPlantaAbastecimientoModel> lotes = loteIds.isEmpty()
                ? Collections.emptyList()
                : abastecimientoRepository.findAllById(loteIds);
        return evaluarYActualizar(declaracion, lotes, new Date());
    }

    /**
     * Variante parametrizable (útil en pruebas unitarias sin base de datos).
     */
    public ResultadoRendimiento evaluarYActualizar(
            DeclaracionPlantaProduccionModel declaracion,
            List<DeclaracionPlantaAbastecimientoModel> lotes,
            Date ahora) {
        CapturaOrigenProduccion.ResultadoCapturaOrigen capRes = (capturaOrigenProduccion != null)
                ? capturaOrigenProduccion.resolverParaLotes(declaracion, lotes)
                : new CapturaOrigenProduccion.ResultadoCapturaOrigen(
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), false, 0, List.of(), "SIN_SERVICIO_ORIGEN");
        return evaluarYActualizar(declaracion, lotes, capRes, ahora);
    }

    /**
     * Variante parametrizable con resultado explícito de captura de origen (QR.3 / QR.6).
     */
    public ResultadoRendimiento evaluarYActualizar(
            DeclaracionPlantaProduccionModel declaracion,
            List<DeclaracionPlantaAbastecimientoModel> lotes,
            CapturaOrigenProduccion.ResultadoCapturaOrigen capturaOrigen,
            Date ahora) {
        List<DeclaracionPlantaAbastecimientoModel> listaLotes = lotes != null ? lotes : Collections.emptyList();
        boolean sinLotes = listaLotes.isEmpty();

        // NP.2: masa de entrada calculada en el servidor
        BigDecimal masaEntrada;
        if (!sinLotes) {
            BigDecimal suma = BigDecimal.ZERO;
            for (DeclaracionPlantaAbastecimientoModel l : listaLotes) {
                suma = suma.add(masaBaseLote(l));
            }
            if (suma.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidacionPlantaException("La masa total de los lotes seleccionados debe ser mayor a 0 kg.");
            }
            if (declaracion.getCantidadMateriaPrima() != null
                    && declaracion.getCantidadMateriaPrima().setScale(2, RoundingMode.HALF_UP)
                            .compareTo(suma.setScale(2, RoundingMode.HALF_UP)) != 0) {
                log.info("NP.2: Diferencia en masa de entrada cliente={} kg vs servidor={} kg (folio {}). Se usa valor del servidor.",
                        declaracion.getCantidadMateriaPrima(), suma, declaracion.getFolioDeclaracionPpla());
            }
            masaEntrada = suma.setScale(2, RoundingMode.HALF_UP);
            declaracion.setCantidadMateriaPrima(masaEntrada);
        } else {
            BigDecimal cliente = declaracion.getCantidadMateriaPrima();
            if (cliente == null || cliente.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidacionPlantaException("Sin lotes seleccionados debe indicar una cantidad de materia prima mayor a 0 kg.");
            }
            masaEntrada = cliente.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal cantProd = declaracion.getCantidadProducto() != null
                ? declaracion.getCantidadProducto() : BigDecimal.ZERO;
        BigDecimal rendimientoExact = cantProd.multiply(CIEN)
                .divide(masaEntrada, 6, RoundingMode.HALF_UP);
        BigDecimal rendimientoPct = rendimientoExact.setScale(2, RoundingMode.HALF_UP);

        // QR.2 / QR.3: captura biológica de origen y rendimiento final sobre captura
        BigDecimal capturaOrigenKg = (capturaOrigen != null && capturaOrigen.capturaOrigenKg() != null)
                ? capturaOrigen.capturaOrigenKg().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        boolean capturaOrigenCompleta = capturaOrigen != null && capturaOrigen.completa();
        BigDecimal rendimientoFinalExact = capturaOrigenKg.compareTo(BigDecimal.ZERO) > 0
                ? cantProd.multiply(CIEN).divide(capturaOrigenKg, 6, RoundingMode.HALF_UP)
                : null;
        BigDecimal rendimientoFinalPct = rendimientoFinalExact != null
                ? rendimientoFinalExact.setScale(2, RoundingMode.HALF_UP)
                : null;

        // Resolver entidad completa de productoResultante para leer su humedadEstado (NF.1)
        ProductoModel producto = resolverProducto(declaracion.getProductoResultante());
        Long productoId = producto != null ? producto.getId() : null;
        HumedadEstadoModel humFormato = producto != null ? producto.getHumedadEstado() : null;

        Long especieId = declaracion.getMateriaPrimaEspecie() != null
                ? declaracion.getMateriaPrimaEspecie().getId()
                : (!sinLotes && listaLotes.get(0).getEspecie() != null ? listaLotes.get(0).getEspecie().getId() : null);

        Long humedadPredId = resolverHumedadPredominanteId(declaracion, listaLotes);
        Date fechaRef = declaracion.getFechaProduccion() != null ? declaracion.getFechaProduccion() : ahora;

        boolean rendimientoActivo = configService == null || configService.getBoolean("rendimiento_activo", true);
        double toleranciaPct = configService != null ? configService.getDouble("rendimiento_tolerancia_pct", 5.0) : 5.0;
        BigDecimal globalMinPct = leerDecimalOpcional("rendimiento_min_pct");
        String baseConfig = resolverBaseConfigurada();
        boolean esBaseOrigenConfig = BASE_CAPTURA_ORIGEN.equals(baseConfig);
        boolean evaluadoPorOrigen = esBaseOrigenConfig
                && capturaOrigenCompleta
                && capturaOrigenKg.compareTo(BigDecimal.ZERO) > 0;
        boolean respaldoPorIncompleta = esBaseOrigenConfig && !evaluadoPorOrigen;
        String baseAplicada = evaluadoPorOrigen ? BASE_CAPTURA_ORIGEN : BASE_MASA_RECIBIDA;

        String fuente = FUENTE_SIN_REFERENCIA;
        BigDecimal maxTeoricoExact = null;
        BigDecimal maxTeoricoPct = null;
        BigDecimal minAplicadoPct = globalMinPct;
        BigDecimal masaEntradaEqKg = null;
        BigDecimal masaProductoEqKg = null;

        if (evaluadoPorOrigen) {
            Optional<RendimientoRangoModel> rangoOpt = buscarRangoCapturaOrigen(
                    especieId, humedadPredId, productoId, fechaRef);
            if (rangoOpt.isPresent()) {
                RendimientoRangoModel r = rangoOpt.get();
                fuente = FUENTE_TABLA_ORIGEN;
                maxTeoricoPct = r.getMaxPct();
                maxTeoricoExact = r.getMaxPct();
                if (r.getMinPct() != null) {
                    minAplicadoPct = r.getMinPct();
                }
                masaEntradaEqKg = capturaOrigenKg;
                masaProductoEqKg = calcularMasaProductoEquivalente(especieId, humFormato, cantProd, fechaRef);
            } else {
                BalanceOrigen bo = calcularBalanceOrigenConFactores(
                        especieId, humFormato, cantProd, capturaOrigenKg, fechaRef);
                if (bo.completo) {
                    fuente = FUENTE_FACTORES_ORIGEN;
                    masaEntradaEqKg = bo.capturaEqKg;
                    masaProductoEqKg = bo.productoEqKg;
                    maxTeoricoExact = bo.maxTeoricoExact;
                    maxTeoricoPct = bo.maxTeoricoExact.setScale(2, RoundingMode.HALF_UP);
                }
            }
        } else {
            // Evaluación sobre MASA_RECIBIDA (ya sea por configuración explícita o respaldo por cadena incompleta)
            Optional<RendimientoRangoModel> rangoOpt = (rendimientoRangoRepository != null && especieId != null && humedadPredId != null && productoId != null)
                    ? rendimientoRangoRepository.findVigentePorBase(especieId, humedadPredId, productoId, BASE_MASA_RECIBIDA, fechaRef)
                    : Optional.empty();

            if (rangoOpt.isPresent()) {
                RendimientoRangoModel r = rangoOpt.get();
                fuente = respaldoPorIncompleta ? FUENTE_RESPALDO_TABLA : FUENTE_TABLA;
                maxTeoricoPct = r.getMaxPct();
                maxTeoricoExact = r.getMaxPct();
                if (r.getMinPct() != null) {
                    minAplicadoPct = r.getMinPct();
                }
                BalanceFactores bf = calcularBalanceConFactores(declaracion, listaLotes, especieId, humFormato, cantProd, masaEntrada, fechaRef);
                if (bf.completo) {
                    masaEntradaEqKg = bf.entradaEqKg;
                    masaProductoEqKg = bf.productoEqKg;
                }
            } else {
                BalanceFactores bf = calcularBalanceConFactores(declaracion, listaLotes, especieId, humFormato, cantProd, masaEntrada, fechaRef);
                if (bf.completo) {
                    fuente = respaldoPorIncompleta ? FUENTE_RESPALDO_FACTORES : FUENTE_FACTORES;
                    masaEntradaEqKg = bf.entradaEqKg;
                    masaProductoEqKg = bf.productoEqKg;
                    maxTeoricoExact = bf.maxTeoricoExact;
                    maxTeoricoPct = bf.maxTeoricoExact.setScale(2, RoundingMode.HALF_UP);
                }
            }
        }

        BigDecimal rendimientoEvaluadoExact = evaluadoPorOrigen ? rendimientoFinalExact : rendimientoExact;
        BigDecimal rendimientoEvaluadoPct = evaluadoPorOrigen ? rendimientoFinalPct : rendimientoPct;

        boolean anomalo = false;
        String tipoAnomalia = null;
        String detalleAnomalia = null;
        String parametroCrit = null;
        String umbralCrit = null;

        if (rendimientoActivo && !FUENTE_SIN_REFERENCIA.equals(fuente) && rendimientoEvaluadoExact != null) {
            boolean esTabla = FUENTE_TABLA.equals(fuente) || FUENTE_TABLA_ORIGEN.equals(fuente) || FUENTE_RESPALDO_TABLA.equals(fuente);
            String fuenteTexto = esTabla ? "tabla" : "factores";
            String baseTexto = evaluadoPorOrigen
                    ? "captura biológica de origen"
                    : (respaldoPorIncompleta ? "masa recibida — respaldo por cadena incompleta" : "masa recibida");

            if (maxTeoricoExact != null) {
                BigDecimal mult = BigDecimal.ONE.add(BigDecimal.valueOf(toleranciaPct).divide(CIEN, 6, RoundingMode.HALF_UP));
                BigDecimal limiteSup = maxTeoricoExact.multiply(mult);
                if (rendimientoEvaluadoExact.compareTo(limiteSup) > 0) {
                    anomalo = true;
                    tipoAnomalia = "EXCESO";
                    detalleAnomalia = String.format(LOCALE_CL,
                            "Rendimiento declarado sobre %s %.1f %% sobre un máximo teórico de %.1f %% (%s; tolerancia %s %%)",
                            baseTexto, rendimientoEvaluadoPct, maxTeoricoPct, fuenteTexto, formatearTolerancia(toleranciaPct));
                    parametroCrit = "rendimiento_tolerancia_pct";
                    umbralCrit = limiteSup.setScale(2, RoundingMode.HALF_UP).toPlainString();
                }
            }
            if (!anomalo && minAplicadoPct != null && rendimientoEvaluadoExact.compareTo(minAplicadoPct) < 0) {
                anomalo = true;
                tipoAnomalia = "BAJO_MINIMO";
                detalleAnomalia = String.format(LOCALE_CL,
                        "Rendimiento declarado sobre %s %.1f %% bajo el mínimo esperado de %.1f %% (%s)",
                        baseTexto, rendimientoEvaluadoPct, minAplicadoPct, fuenteTexto);
                parametroCrit = "rendimiento_min_pct";
                umbralCrit = minAplicadoPct.setScale(2, RoundingMode.HALF_UP).toPlainString();
            }
        }

        declaracion.setMasaEntradaEquivalenteKg(masaEntradaEqKg);
        declaracion.setMasaProductoEquivalenteKg(masaProductoEqKg);
        declaracion.setRendimientoPct(rendimientoPct);
        declaracion.setCapturaOrigenKg(capturaOrigenKg);
        declaracion.setCapturaOrigenCompleta(capturaOrigenCompleta);
        declaracion.setRendimientoFinalPct(rendimientoFinalPct);
        declaracion.setRendimientoMaxTeoricoPct(maxTeoricoPct);
        declaracion.setRendimientoFuente(fuente);
        declaracion.setRendimientoEvaluadoEn(ahora);

        return new ResultadoRendimiento(
                sinLotes, masaEntrada, masaEntradaEqKg, masaProductoEqKg,
                rendimientoPct, maxTeoricoPct, minAplicadoPct,
                fuente, anomalo, tipoAnomalia, detalleAnomalia,
                parametroCrit, umbralCrit, ahora,
                capturaOrigenKg, capturaOrigenCompleta, rendimientoFinalPct,
                baseAplicada, rendimientoEvaluadoPct
        );
    }

    /**
     * Registra las marcas post-guardado (`PRODUCCION_SIN_LOTES` / `RENDIMIENTO_ANOMALO`).
     * No elimina marcas previas al recalcular tras edición autorizada (NP.5).
     */
    @Transactional(readOnly = false)
    public void registrarHallazgosPostGuardado(DeclaracionPlantaProduccionModel saved, ResultadoRendimiento res) {
        if (hallazgoService == null || saved == null || saved.getId() == null || res == null) {
            return;
        }
        Long usuarioId = saved.getUsuario() != null ? saved.getUsuario().getId() : null;
        Long regionId = resolverRegionUsuario(usuarioId);

        if (res.sinLotes()) {
            hallazgoService.registrar(
                    "PLANTA_PRODUCCION",
                    saved.getId(),
                    "PRODUCCION_SIN_LOTES",
                    "SIN_LOTES",
                    new CriterioHallazgo("lotes_abastecimiento", "1", "0", "lotes"),
                    "Declaración de producción registrada sin lotes de abastecimiento seleccionados (masa informada manualmente).",
                    "VALIDACION",
                    usuarioId,
                    regionId
            );
        }

        if (res.anomalo()) {
            BigDecimal valDetectado = res.rendimientoEvaluadoPct() != null
                    ? res.rendimientoEvaluadoPct()
                    : res.rendimientoPct();
            hallazgoService.registrar(
                    "PLANTA_PRODUCCION",
                    saved.getId(),
                    "RENDIMIENTO_ANOMALO",
                    res.tipoAnomalia(),
                    new CriterioHallazgo(
                            res.parametroCriterio(),
                            res.umbralCriterio(),
                            valDetectado != null ? valDetectado.toPlainString() : "0",
                            "%"
                    ),
                    res.detalleAnomalia(),
                    "VALIDACION",
                    usuarioId,
                    regionId
            );
        }
    }

    public static BigDecimal masaBaseLote(DeclaracionPlantaAbastecimientoModel lote) {
        if (lote == null) return BigDecimal.ZERO;
        if (lote.getPesoRomanaKg() != null && lote.getPesoRomanaKg().compareTo(BigDecimal.ZERO) > 0) {
            return lote.getPesoRomanaKg();
        }
        return lote.getCantidad() != null ? lote.getCantidad() : BigDecimal.ZERO;
    }

    private Long resolverHumedadPredominanteId(DeclaracionPlantaProduccionModel decl, List<DeclaracionPlantaAbastecimientoModel> lotes) {
        if (lotes.isEmpty()) {
            return decl.getHumedadEstado() != null ? decl.getHumedadEstado().getId() : null;
        }
        Map<Long, BigDecimal> porHum = new LinkedHashMap<>();
        for (DeclaracionPlantaAbastecimientoModel l : lotes) {
            HumedadEstadoModel h = l.getHumedadEstadoRecepcion() != null ? l.getHumedadEstadoRecepcion() : l.getHumedadEstado();
            if (h != null && h.getId() != null) {
                porHum.merge(h.getId(), masaBaseLote(l), BigDecimal::add);
            }
        }
        if (!porHum.isEmpty()) {
            return porHum.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
        }
        return decl.getHumedadEstado() != null ? decl.getHumedadEstado().getId() : null;
    }

    private BalanceFactores calcularBalanceConFactores(
            DeclaracionPlantaProduccionModel decl,
            List<DeclaracionPlantaAbastecimientoModel> lotes,
            Long especieFallbackId,
            HumedadEstadoModel humFormato,
            BigDecimal cantProd,
            BigDecimal masaEntrada,
            Date fechaRef) {
        if (factorConversionService == null || humFormato == null || humFormato.getId() == null || especieFallbackId == null) {
            return BalanceFactores.INCOMPLETO;
        }
        Optional<FactorConversionModel> fFormatoOpt = factorConversionService.findFactorVigente(especieFallbackId, humFormato.getId(), fechaRef);
        if (fFormatoOpt.isEmpty() || fFormatoOpt.get().getFactor() == null || fFormatoOpt.get().getFactor().compareTo(BigDecimal.ZERO) <= 0) {
            return BalanceFactores.INCOMPLETO;
        }
        BigDecimal factorFormato = fFormatoOpt.get().getFactor();

        BigDecimal entradaEq = BigDecimal.ZERO;
        if (!lotes.isEmpty()) {
            for (DeclaracionPlantaAbastecimientoModel l : lotes) {
                Long espId = l.getEspecie() != null && l.getEspecie().getId() != null ? l.getEspecie().getId() : especieFallbackId;
                HumedadEstadoModel humLote = l.getHumedadEstadoRecepcion() != null ? l.getHumedadEstadoRecepcion() : l.getHumedadEstado();
                if (humLote == null || humLote.getId() == null) {
                    return BalanceFactores.INCOMPLETO;
                }
                Optional<FactorConversionModel> fLote = factorConversionService.findFactorVigente(espId, humLote.getId(), fechaRef);
                if (fLote.isEmpty() || fLote.get().getFactor() == null) {
                    return BalanceFactores.INCOMPLETO;
                }
                entradaEq = entradaEq.add(masaBaseLote(l).multiply(fLote.get().getFactor()));
            }
        } else {
            if (decl.getHumedadEstado() == null || decl.getHumedadEstado().getId() == null) {
                return BalanceFactores.INCOMPLETO;
            }
            Optional<FactorConversionModel> fEntrada = factorConversionService.findFactorVigente(especieFallbackId, decl.getHumedadEstado().getId(), fechaRef);
            if (fEntrada.isEmpty() || fEntrada.get().getFactor() == null) {
                return BalanceFactores.INCOMPLETO;
            }
            entradaEq = masaEntrada.multiply(fEntrada.get().getFactor());
        }

        BigDecimal prodEq = cantProd.multiply(factorFormato);
        BigDecimal productoMax = entradaEq.divide(factorFormato, 6, RoundingMode.HALF_UP);
        BigDecimal maxTeoricoExact = productoMax.multiply(CIEN).divide(masaEntrada, 6, RoundingMode.HALF_UP);

        return new BalanceFactores(
                true,
                entradaEq.setScale(2, RoundingMode.HALF_UP),
                prodEq.setScale(2, RoundingMode.HALF_UP),
                maxTeoricoExact
        );
    }

    private ProductoModel resolverProducto(ProductoModel ref) {
        if (ref == null) return null;
        if (ref.getHumedadEstado() != null) return ref;
        if (ref.getId() != null && productoRepository != null) {
            return productoRepository.findById(ref.getId()).orElse(ref);
        }
        return ref;
    }

    private BigDecimal leerDecimalOpcional(String clave) {
        if (configService == null) return null;
        String raw = configService.getValor(clave, "");
        if (raw == null || raw.isBlank()) return null;
        try {
            return new BigDecimal(raw.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long resolverRegionUsuario(Long usuarioId) {
        if (usuarioId == null || usuarioRepository == null) return null;
        return usuarioRepository.findById(usuarioId)
                .map(u -> u.getComuna() != null && u.getComuna().getRegion() != null ? u.getComuna().getRegion().getId() : null)
                .orElse(null);
    }

    private static String formatearTolerancia(double tol) {
        if (Math.abs(tol - Math.rint(tol)) < 1e-6) {
            return String.format(LOCALE_CL, "%.0f", tol);
        }
        return String.format(LOCALE_CL, "%.1f", tol);
    }

    private String resolverBaseConfigurada() {
        if (configService == null) {
            return BASE_CAPTURA_ORIGEN;
        }
        String raw = configService.getValor("rendimiento_base", BASE_CAPTURA_ORIGEN);
        if (raw == null || raw.isBlank()) {
            return BASE_CAPTURA_ORIGEN;
        }
        return BASE_MASA_RECIBIDA.equalsIgnoreCase(raw.trim()) ? BASE_MASA_RECIBIDA : BASE_CAPTURA_ORIGEN;
    }

    private Optional<RendimientoRangoModel> buscarRangoCapturaOrigen(
            Long especieId,
            Long humedadPredId,
            Long productoId,
            Date fechaRef) {
        if (rendimientoRangoRepository == null || especieId == null || productoId == null) {
            return Optional.empty();
        }
        if (humedadPredId != null) {
            Optional<RendimientoRangoModel> opt = rendimientoRangoRepository.findVigentePorBase(
                    especieId, humedadPredId, productoId, BASE_CAPTURA_ORIGEN, fechaRef);
            if (opt.isPresent()) {
                return opt;
            }
        }
        Long humedadBiologicaId = resolverIdHumedadHumedo();
        if (humedadBiologicaId != null && !humedadBiologicaId.equals(humedadPredId)) {
            return rendimientoRangoRepository.findVigentePorBase(
                    especieId, humedadBiologicaId, productoId, BASE_CAPTURA_ORIGEN, fechaRef);
        }
        return Optional.empty();
    }

    private Long resolverIdHumedadHumedo() {
        if (humedadEstadoRepository == null) {
            return null;
        }
        for (HumedadEstadoModel h : humedadEstadoRepository.findAll()) {
            if (h.getNombre() != null) {
                String n = h.getNombre().trim().toUpperCase(Locale.ROOT)
                        .replace("Ú", "U");
                if ("HUMEDO".equals(n)) {
                    return h.getId();
                }
            }
        }
        return null;
    }

    private BigDecimal calcularMasaProductoEquivalente(
            Long especieFallbackId,
            HumedadEstadoModel humFormato,
            BigDecimal cantProd,
            Date fechaRef) {
        if (factorConversionService == null || humFormato == null || humFormato.getId() == null || especieFallbackId == null) {
            return null;
        }
        Optional<FactorConversionModel> fOpt = factorConversionService.findFactorVigente(
                especieFallbackId, humFormato.getId(), fechaRef);
        if (fOpt.isEmpty() || fOpt.get().getFactor() == null || fOpt.get().getFactor().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return cantProd.multiply(fOpt.get().getFactor()).setScale(2, RoundingMode.HALF_UP);
    }

    private BalanceOrigen calcularBalanceOrigenConFactores(
            Long especieFallbackId,
            HumedadEstadoModel humFormato,
            BigDecimal cantProd,
            BigDecimal capturaOrigenKg,
            Date fechaRef) {
        if (factorConversionService == null || humFormato == null || humFormato.getId() == null
                || especieFallbackId == null || capturaOrigenKg == null
                || capturaOrigenKg.compareTo(BigDecimal.ZERO) <= 0) {
            return BalanceOrigen.INCOMPLETO;
        }
        Optional<FactorConversionModel> fFormatoOpt = factorConversionService.findFactorVigente(
                especieFallbackId, humFormato.getId(), fechaRef);
        if (fFormatoOpt.isEmpty() || fFormatoOpt.get().getFactor() == null
                || fFormatoOpt.get().getFactor().compareTo(BigDecimal.ZERO) <= 0) {
            return BalanceOrigen.INCOMPLETO;
        }
        BigDecimal factorFormato = fFormatoOpt.get().getFactor();
        BigDecimal prodEq = cantProd.multiply(factorFormato).setScale(2, RoundingMode.HALF_UP);
        BigDecimal maxTeoricoExact = CIEN.divide(factorFormato, 6, RoundingMode.HALF_UP);
        return new BalanceOrigen(true, capturaOrigenKg.setScale(2, RoundingMode.HALF_UP), prodEq, maxTeoricoExact);
    }

    private record BalanceFactores(boolean completo, BigDecimal entradaEqKg, BigDecimal productoEqKg, BigDecimal maxTeoricoExact) {
        static final BalanceFactores INCOMPLETO = new BalanceFactores(false, null, null, null);
    }

    private record BalanceOrigen(boolean completo, BigDecimal capturaEqKg, BigDecimal productoEqKg, BigDecimal maxTeoricoExact) {
        static final BalanceOrigen INCOMPLETO = new BalanceOrigen(false, null, null, null);
    }
}
