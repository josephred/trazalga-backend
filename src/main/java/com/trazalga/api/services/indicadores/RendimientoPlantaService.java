package com.trazalga.api.services.indicadores;

import com.trazalga.api.exceptions.ValidacionPlantaException;
import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import com.trazalga.api.models.DeclaracionPlantaProduccionModel;
import com.trazalga.api.models.FactorConversionModel;
import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.models.ProductoModel;
import com.trazalga.api.models.RendimientoRangoModel;
import com.trazalga.api.repositories.IDeclaracionPlantaAbastecimientoRepository;
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

    public static final String FUENTE_TABLA = "TABLA";
    public static final String FUENTE_FACTORES = "FACTORES";
    public static final String FUENTE_SIN_REFERENCIA = "SIN_REFERENCIA";

    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final Locale LOCALE_CL = Locale.forLanguageTag("es-CL");

    @Autowired private IDeclaracionPlantaAbastecimientoRepository abastecimientoRepository;
    @Autowired private IRendimientoRangoRepository rendimientoRangoRepository;
    @Autowired private FactorConversionService factorConversionService;
    @Autowired private ConfiguracionGeneralService configService;
    @Autowired private IProductoRepository productoRepository;
    @Autowired(required = false) private IUsuarioRepository usuarioRepository;
    @Autowired(required = false) private HallazgoService hallazgoService;

    public RendimientoPlantaService() {
    }

    /** Constructor para pruebas unitarias puras. */
    public RendimientoPlantaService(
            IDeclaracionPlantaAbastecimientoRepository abastecimientoRepository,
            IRendimientoRangoRepository rendimientoRangoRepository,
            FactorConversionService factorConversionService,
            ConfiguracionGeneralService configService,
            IProductoRepository productoRepository,
            HallazgoService hallazgoService) {
        this.abastecimientoRepository = abastecimientoRepository;
        this.rendimientoRangoRepository = rendimientoRangoRepository;
        this.factorConversionService = factorConversionService;
        this.configService = configService;
        this.productoRepository = productoRepository;
        this.hallazgoService = hallazgoService;
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
            Date evaluadoEn
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

        String fuente = FUENTE_SIN_REFERENCIA;
        BigDecimal maxTeoricoExact = null;
        BigDecimal maxTeoricoPct = null;
        BigDecimal minAplicadoPct = globalMinPct;
        BigDecimal masaEntradaEqKg = null;
        BigDecimal masaProductoEqKg = null;

        // 1. Prioridad TABLA (rendimiento_rango)
        Optional<RendimientoRangoModel> rangoOpt = (rendimientoRangoRepository != null && especieId != null && humedadPredId != null && productoId != null)
                ? rendimientoRangoRepository.findVigente(especieId, humedadPredId, productoId, fechaRef)
                : Optional.empty();

        if (rangoOpt.isPresent()) {
            RendimientoRangoModel r = rangoOpt.get();
            fuente = FUENTE_TABLA;
            maxTeoricoPct = r.getMaxPct();
            maxTeoricoExact = r.getMaxPct();
            if (r.getMinPct() != null) {
                minAplicadoPct = r.getMinPct();
            }
            // Enrich equivalent masses optionally when factors are present
            BalanceFactores bf = calcularBalanceConFactores(declaracion, listaLotes, especieId, humFormato, cantProd, masaEntrada, fechaRef);
            if (bf.completo) {
                masaEntradaEqKg = bf.entradaEqKg;
                masaProductoEqKg = bf.productoEqKg;
            }
        } else {
            // 2. Balance de biomasa con FACTORES
            BalanceFactores bf = calcularBalanceConFactores(declaracion, listaLotes, especieId, humFormato, cantProd, masaEntrada, fechaRef);
            if (bf.completo) {
                fuente = FUENTE_FACTORES;
                masaEntradaEqKg = bf.entradaEqKg;
                masaProductoEqKg = bf.productoEqKg;
                maxTeoricoExact = bf.maxTeoricoExact;
                maxTeoricoPct = bf.maxTeoricoExact.setScale(2, RoundingMode.HALF_UP);
            }
        }

        boolean anomalo = false;
        String tipoAnomalia = null;
        String detalleAnomalia = null;
        String parametroCrit = null;
        String umbralCrit = null;

        if (rendimientoActivo && !FUENTE_SIN_REFERENCIA.equals(fuente)) {
            if (maxTeoricoExact != null) {
                BigDecimal mult = BigDecimal.ONE.add(BigDecimal.valueOf(toleranciaPct).divide(CIEN, 6, RoundingMode.HALF_UP));
                BigDecimal limiteSup = maxTeoricoExact.multiply(mult);
                if (rendimientoExact.compareTo(limiteSup) > 0) {
                    anomalo = true;
                    tipoAnomalia = "EXCESO";
                    String fuenteTexto = FUENTE_TABLA.equals(fuente) ? "tabla" : "factores";
                    detalleAnomalia = String.format(LOCALE_CL,
                            "Rendimiento declarado %.1f %% sobre un máximo teórico de %.1f %% (%s; tolerancia %s %%)",
                            rendimientoPct, maxTeoricoPct, fuenteTexto, formatearTolerancia(toleranciaPct));
                    parametroCrit = "rendimiento_tolerancia_pct";
                    umbralCrit = limiteSup.setScale(2, RoundingMode.HALF_UP).toPlainString();
                }
            }
            if (!anomalo && minAplicadoPct != null && rendimientoExact.compareTo(minAplicadoPct) < 0) {
                anomalo = true;
                tipoAnomalia = "BAJO_MINIMO";
                String fuenteTexto = FUENTE_TABLA.equals(fuente) ? "tabla" : "factores";
                detalleAnomalia = String.format(LOCALE_CL,
                        "Rendimiento declarado %.1f %% bajo el mínimo esperado de %.1f %% (%s)",
                        rendimientoPct, minAplicadoPct, fuenteTexto);
                parametroCrit = "rendimiento_min_pct";
                umbralCrit = minAplicadoPct.setScale(2, RoundingMode.HALF_UP).toPlainString();
            }
        }

        declaracion.setMasaEntradaEquivalenteKg(masaEntradaEqKg);
        declaracion.setMasaProductoEquivalenteKg(masaProductoEqKg);
        declaracion.setRendimientoPct(rendimientoPct);
        declaracion.setRendimientoMaxTeoricoPct(maxTeoricoPct);
        declaracion.setRendimientoFuente(fuente);
        declaracion.setRendimientoEvaluadoEn(ahora);

        return new ResultadoRendimiento(
                sinLotes, masaEntrada, masaEntradaEqKg, masaProductoEqKg,
                rendimientoPct, maxTeoricoPct, minAplicadoPct,
                fuente, anomalo, tipoAnomalia, detalleAnomalia,
                parametroCrit, umbralCrit, ahora
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
            hallazgoService.registrar(
                    "PLANTA_PRODUCCION",
                    saved.getId(),
                    "RENDIMIENTO_ANOMALO",
                    res.tipoAnomalia(),
                    new CriterioHallazgo(
                            res.parametroCriterio(),
                            res.umbralCriterio(),
                            res.rendimientoPct() != null ? res.rendimientoPct().toPlainString() : "0",
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

    private record BalanceFactores(boolean completo, BigDecimal entradaEqKg, BigDecimal productoEqKg, BigDecimal maxTeoricoExact) {
        static final BalanceFactores INCOMPLETO = new BalanceFactores(false, null, null, null);
    }
}
