package com.trazalga.api.services.indicadores;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.trazalga.api.repositories.IDeclaracionAreaRepository;
import com.trazalga.api.repositories.IDeclaracionArmadorRepository;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;
import com.trazalga.api.services.ConfiguracionGeneralService;

/**
 * Kilos acumulados en un día para el Límite de Extracción Diario (refinamiento 06-11, NL.4).
 * <p>
 * Es la única suma del LED: la usan la evaluación en línea ({@code LimiteExtraccionDiarioService}) y la
 * pestaña «Monitoreo diario» ({@code ReportRepository.getLimiteExtraccionDiarioMetrics}), para que las dos
 * den siempre el mismo total.
 * <ul>
 *   <li>Unidad {@code EMBARCACION} o {@code BUZO}: suma las declaraciones de armador y, si
 *       {@code led_suma_declaraciones_area} está activo, las de área de la misma embarcación o buzo.</li>
 *   <li>Unidad {@code USUARIO}: suma las declaraciones del perfil que declara (armador, recolector o,
 *       con el parámetro activo, área).</li>
 * </ul>
 */
@Component
public class AcumuladoDiarioLed {

    /** D20: el límite por embarcación y por día cuenta también lo declarado como área de manejo. */
    public static final String PARAM_SUMA_AREA = "led_suma_declaraciones_area";

    @Autowired
    private IDeclaracionArmadorRepository armadorRepository;

    @Autowired
    private IDeclaracionRecolectorRepository recolectorRepository;

    @Autowired(required = false)
    private IDeclaracionAreaRepository areaRepository;

    @Autowired(required = false)
    private ConfiguracionGeneralService configuracionGeneralService;

    public AcumuladoDiarioLed() {
    }

    public AcumuladoDiarioLed(IDeclaracionArmadorRepository armadorRepository,
                              IDeclaracionRecolectorRepository recolectorRepository,
                              IDeclaracionAreaRepository areaRepository,
                              ConfiguracionGeneralService configuracionGeneralService) {
        this.armadorRepository = armadorRepository;
        this.recolectorRepository = recolectorRepository;
        this.areaRepository = areaRepository;
        this.configuracionGeneralService = configuracionGeneralService;
    }

    /** Las declaraciones de área suman al acumulado diario. */
    public boolean sumaArea() {
        return areaRepository != null && configuracionGeneralService != null
                && configuracionGeneralService.getBoolean(PARAM_SUMA_AREA, true);
    }

    /**
     * Kilos ya declarados en el día para la unidad de la regla.
     *
     * @param unidad    EMBARCACION, BUZO o USUARIO.
     * @param captura   true si la regla mide captura; false si mide desembarque.
     * @param perfil    ARMADOR, RECOLECTOR o AREA; sólo importa con la unidad USUARIO.
     * @param especieId especie por la que se filtra; null suma todas.
     * @return la suma; cero si la unidad no tiene identificador (por ejemplo, un recolector con una regla
     *         por embarcación).
     */
    public BigDecimal acumulado(String unidad, boolean captura, String perfil, Long embarcacionId, Long buzoId,
                                Long usuarioId, Long especieId, Date fecha) {
        String u = unidad != null ? unidad.toUpperCase(Locale.ROOT) : "EMBARCACION";
        String p = perfil != null ? perfil.toUpperCase(Locale.ROOT) : "";
        BigDecimal total = BigDecimal.ZERO;

        if ("EMBARCACION".equals(u) && embarcacionId != null) {
            total = captura
                    ? num(armadorRepository.sumCapturaByEmbarcacionAndFecha(embarcacionId, especieId, fecha))
                    : num(armadorRepository.sumDesembarqueByEmbarcacionAndFecha(embarcacionId, especieId, fecha));
            if (sumaArea()) {
                total = total.add(captura
                        ? num(areaRepository.sumCapturaByEmbarcacionAndFecha(embarcacionId, especieId, fecha))
                        : num(areaRepository.sumDesembarqueByEmbarcacionAndFecha(embarcacionId, especieId, fecha)));
            }
        } else if ("BUZO".equals(u) && buzoId != null) {
            total = captura
                    ? num(armadorRepository.sumCapturaByBuzoAndFecha(buzoId, especieId, fecha))
                    : num(armadorRepository.sumDesembarqueByBuzoAndFecha(buzoId, especieId, fecha));
            if (sumaArea()) {
                total = total.add(captura
                        ? num(areaRepository.sumCapturaByBuzoAndFecha(buzoId, especieId, fecha))
                        : num(areaRepository.sumDesembarqueByBuzoAndFecha(buzoId, especieId, fecha)));
            }
        } else if ("USUARIO".equals(u) && usuarioId != null) {
            if ("ARMADOR".equals(p)) {
                total = captura
                        ? num(armadorRepository.sumCapturaByUsuarioAndFecha(usuarioId, especieId, fecha))
                        : num(armadorRepository.sumDesembarqueByUsuarioAndFecha(usuarioId, especieId, fecha));
            } else if ("RECOLECTOR".equals(p)) {
                total = captura
                        ? num(recolectorRepository.sumCapturaByUsuarioAndFecha(usuarioId, especieId, fecha))
                        : num(recolectorRepository.sumDesembarqueByUsuarioAndFecha(usuarioId, especieId, fecha));
            } else if ("AREA".equals(p) && sumaArea()) {
                total = captura
                        ? num(areaRepository.sumCapturaByUsuarioAndFecha(usuarioId, especieId, fecha))
                        : num(areaRepository.sumDesembarqueByUsuarioAndFecha(usuarioId, especieId, fecha));
            }
        }
        return total;
    }

    private static BigDecimal num(Number n) {
        if (n == null) {
            return BigDecimal.ZERO;
        }
        return n instanceof BigDecimal b ? b : BigDecimal.valueOf(n.doubleValue());
    }
}
