package com.trazalga.api.services.indicadores;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.trazalga.api.ApiApplication;
import com.trazalga.api.IntegracionBase;
import com.trazalga.api.models.AmerbModel;
import com.trazalga.api.models.BuzoModel;
import com.trazalga.api.models.CaletaModel;
import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.models.DeclaracionAreaModel;
import com.trazalga.api.models.DeclaracionArmadorModel;
import com.trazalga.api.models.DeclaracionMarcaModel;
import com.trazalga.api.models.EmbarcacionModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.ExtraccionTipoModel;
import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.models.LimiteExtraccionDiarioConfigModel;
import com.trazalga.api.models.ProvinciaModel;
import com.trazalga.api.models.RegionModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IAmerbRepository;
import com.trazalga.api.repositories.IBuzoRepository;
import com.trazalga.api.repositories.ICaletaRepository;
import com.trazalga.api.repositories.IComunaRepository;
import com.trazalga.api.repositories.IDeclaracionAreaRepository;
import com.trazalga.api.repositories.IDeclaracionArmadorRepository;
import com.trazalga.api.repositories.IDeclaracionMarcaRepository;
import com.trazalga.api.repositories.IEmbarcacionRepository;
import com.trazalga.api.repositories.IEspecieRepository;
import com.trazalga.api.repositories.IExtraccionTipoRepository;
import com.trazalga.api.repositories.IHumedadEstadoRepository;
import com.trazalga.api.repositories.ILimiteExtraccionDiarioConfigRepository;
import com.trazalga.api.repositories.IProvinciaRepository;
import com.trazalga.api.repositories.IRegionRepository;
import com.trazalga.api.repositories.IUsuarioRepository;
import com.trazalga.api.repositories.ReportRepository;
import com.trazalga.api.services.ConfiguracionGeneralService;
import com.trazalga.api.services.LimiteExtraccionDiarioService;

/**
 * Refinamiento 06-11, punto 1 (integración, H2 perfil "it"): el monitoreo diario del LED, la pestaña de
 * hallazgos y la evaluación en línea.
 * <p>
 * H2 no reproduce el desfase de MySQL con {@code serverTimezone=UTC} (F8): aquí se comprueba que la consulta
 * recibe un día y no un instante, y que el monitoreo y la evaluación en línea dan el mismo total.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureMockMvc
class LedMonitoreoIT extends IntegracionBase {

    @Autowired private MockMvc mockMvc;

    /** Se calcula en setUp: el contexto de Spring fija la zona horaria de la JVM en America/Santiago. */
    private java.sql.Date dia;

    @Autowired private ReportRepository reportRepository;
    @Autowired private LimiteExtraccionDiarioService ledService;
    @Autowired private ConfiguracionGeneralService configuracionGeneralService;

    @Autowired private ILimiteExtraccionDiarioConfigRepository reglaRepository;
    @Autowired private IDeclaracionArmadorRepository armadorRepository;
    @Autowired private IDeclaracionAreaRepository areaRepository;
    @Autowired private IDeclaracionMarcaRepository marcaRepository;
    @Autowired private IRegionRepository regionRepository;
    @Autowired private IProvinciaRepository provinciaRepository;
    @Autowired private IComunaRepository comunaRepository;
    @Autowired private ICaletaRepository caletaRepository;
    @Autowired private IEspecieRepository especieRepository;
    @Autowired private IExtraccionTipoRepository extraccionTipoRepository;
    @Autowired private IHumedadEstadoRepository humedadEstadoRepository;
    @Autowired private IUsuarioRepository usuarioRepository;
    @Autowired private IEmbarcacionRepository embarcacionRepository;
    @Autowired private IBuzoRepository buzoRepository;
    @Autowired private IAmerbRepository amerbRepository;

    private RegionModel region;
    private ComunaModel comuna;
    private CaletaModel caleta;
    private EspecieModel especie;
    private ExtraccionTipoModel metodo;
    private HumedadEstadoModel humedad;
    private UsuarioModel armador;
    private UsuarioModel organizacion;
    private UsuarioModel planta;
    private EmbarcacionModel embarcacion;
    private BuzoModel buzo;
    private AmerbModel amerb;
    private LimiteExtraccionDiarioConfigModel regla;

    @BeforeEach
    void setUp() {
        dia = java.sql.Date.valueOf("2026-11-06");
        configuracionGeneralService.updateConfig(AcumuladoDiarioLed.PARAM_SUMA_AREA, "true");
        if (comuna != null) {
            return;
        }
        // Sólo cuenta la regla de esta prueba
        for (LimiteExtraccionDiarioConfigModel r : reglaRepository.findAll()) {
            r.setActivo(false);
            reglaRepository.save(r);
        }
        region = regionRepository.saveAndFlush(RegionModel.builder().nombre("Coquimbo LED06").codigo("04").build());
        ProvinciaModel prov = provinciaRepository.saveAndFlush(ProvinciaModel.builder().nombre("Elqui LED06").region(region).build());
        comuna = comunaRepository.saveAndFlush(ComunaModel.builder().nombre("La Higuera LED06").region(region).provincia(prov).build());
        caleta = caletaRepository.saveAndFlush(CaletaModel.builder().nombre("Chungungo LED06").region(region).comuna(comuna).build());
        especie = especieRepository.saveAndFlush(EspecieModel.builder().nombre("Huiro palo LED06").build());
        metodo = extraccionTipoRepository.saveAndFlush(ExtraccionTipoModel.builder().nombre("Barreteado LED06").build());
        humedad = humedadEstadoRepository.saveAndFlush(HumedadEstadoModel.builder().nombre("Húmedo LED06").build());
        armador = usuarioRepository.saveAndFlush(UsuarioModel.builder()
                .rut("31.111.111-1").nombres("Armador").apellidop("LED06").comuna(comuna).estado("ACTIVO").build());
        organizacion = usuarioRepository.saveAndFlush(UsuarioModel.builder()
                .rut("32.222.222-2").nombres("Sindicato").apellidop("LED06").comuna(comuna).estado("ACTIVO").build());
        planta = usuarioRepository.saveAndFlush(UsuarioModel.builder()
                .rut("33.333.333-3").nombres("Planta").apellidop("LED06").estado("ACTIVO").build());
        embarcacion = embarcacionRepository.saveAndFlush(EmbarcacionModel.builder().nombre("Doña Rosa LED06").codigo("LED06-0001").build());
        buzo = buzoRepository.saveAndFlush(BuzoModel.builder().nombre("Buzo LED06").codigo("BZ-LED06").build());
        amerb = amerbRepository.saveAndFlush(AmerbModel.builder().nombre("AMERB Chungungo LED06").build());
        regla = reglaRepository.saveAndFlush(LimiteExtraccionDiarioConfigModel.builder()
                .nombreRegla("LED 2 toneladas LED06").perfilAplicable("TODOS").unidadAgregacion("EMBARCACION")
                .metrica("DESEMBARQUE").limiteKg(new BigDecimal("2000.00")).margenToleranciaPct(BigDecimal.ZERO)
                .modoAccion("SOLO_ALERTA").activo(true).build());
    }

    @AfterEach
    void tearDown() {
        marcaRepository.deleteAll();
        armadorRepository.deleteAll();
        areaRepository.deleteAll();
        configuracionGeneralService.updateConfig(AcumuladoDiarioLed.PARAM_SUMA_AREA, "true");
    }

    // ------------------------------------------------------------------ NL.2

    @Test
    @DisplayName("NL.2 · Una faena del día D aparece en el monitoreo del día D, venga la fecha a medianoche o con hora")
    void monitoreo_encuentraLaFaenaDelDia() {
        armador(1200);

        Date medianoche = Date.from(ZonedDateTime.of(2026, 11, 6, 0, 0, 0, 0, ApiApplication.ZONA_CHILE).toInstant());
        Date conHora = Date.from(ZonedDateTime.of(2026, 11, 6, 15, 30, 0, 0, ApiApplication.ZONA_CHILE).toInstant());

        for (Date fecha : List.of(medianoche, conHora)) {
            Map<String, Object> res = reportRepository.getLimiteExtraccionDiarioMetrics(fecha);
            List<Map<String, Object>> detalle = detalle(res);
            assertEquals("2026-11-06", res.get("dia"));
            assertEquals(1, detalle.size(), "fecha " + fecha);
            assertEquals(1200.0, ((Number) detalle.get(0).get("kgDesembarcados")).doubleValue(), 0.001);
            assertEquals("EMBARCACION", detalle.get(0).get("tipo"));
            assertEquals("Doña Rosa LED06", detalle.get(0).get("nombre"));
            assertEquals("NORMAL", detalle.get(0).get("estado"));
        }

        Date otroDia = Date.from(ZonedDateTime.of(2026, 11, 7, 0, 0, 0, 0, ApiApplication.ZONA_CHILE).toInstant());
        assertTrue(detalle(reportRepository.getLimiteExtraccionDiarioMetrics(otroDia)).isEmpty());
    }

    // ------------------------------------------------------------------ NL.4

    @Test
    @DisplayName("NL.4 · Armador y área de la misma embarcación y día: el monitoreo y la evaluación en línea dan el mismo total")
    void monitoreoYEvaluacion_mismoTotalConArea() {
        armador(1200);
        area(1100, true);

        List<Map<String, Object>> detalle = detalle(reportRepository.getLimiteExtraccionDiarioMetrics(dia));
        assertEquals(1, detalle.size(), "una sola fila por embarcación");
        Map<String, Object> fila = detalle.get(0);
        assertEquals(2300.0, ((Number) fila.get("kgDesembarcados")).doubleValue(), 0.001);
        assertEquals("EXCEDIDO", fila.get("estado"));
        assertEquals(List.of("ARMADOR", "AREA"), fila.get("origenes"));

        LimiteExtraccionDiarioService.EvaluacionLedResult ev = ledService.evaluar("ARMADOR", embarcacion.getId(),
                armador.getId(), buzo.getId(), especie.getId(), metodo.getId(), region.getId(), dia,
                BigDecimal.ZERO, BigDecimal.ZERO);
        assertEquals(0, new BigDecimal("2300").compareTo(ev.getTotalAcumulado()), "total " + ev.getTotalAcumulado());
        assertTrue(ev.isExcede());
    }

    @Test
    @DisplayName("NL.4 · Con led_suma_declaraciones_area apagado, las declaraciones de área no suman en ninguno de los dos")
    void parametroApagado_soloArmador() {
        configuracionGeneralService.updateConfig(AcumuladoDiarioLed.PARAM_SUMA_AREA, "false");
        armador(1200);
        area(1100, true);

        List<Map<String, Object>> detalle = detalle(reportRepository.getLimiteExtraccionDiarioMetrics(dia));
        assertEquals(1, detalle.size());
        assertEquals(1200.0, ((Number) detalle.get(0).get("kgDesembarcados")).doubleValue(), 0.001);

        LimiteExtraccionDiarioService.EvaluacionLedResult ev = ledService.evaluar("ARMADOR", embarcacion.getId(),
                armador.getId(), buzo.getId(), especie.getId(), metodo.getId(), region.getId(), dia,
                BigDecimal.ZERO, BigDecimal.ZERO);
        assertEquals(0, new BigDecimal("1200").compareTo(ev.getTotalAcumulado()));
        assertFalse(ev.isExcede());
    }

    @Test
    @DisplayName("NL.4 · Un área sin embarcación no tiene unidad que acumular frente a una regla por embarcación")
    void areaSinEmbarcacion_noSeMonitorea() {
        area(2500, false);

        Map<String, Object> res = reportRepository.getLimiteExtraccionDiarioMetrics(dia);
        assertTrue(detalle(res).isEmpty());
        assertEquals(1, ((Number) res.get("sinUnidad")).intValue());
    }

    // ------------------------------------------------------------------ NL.3

    @Test
    @DisplayName("NL.3 · Hallazgos: una marca de armador y una de área salen con folio, embarcación, caleta y kilos")
    void hallazgos_armadorYArea() {
        DeclaracionArmadorModel da = armador(2600);
        DeclaracionAreaModel dar = area(2400, true);
        marca("ARMADOR", da.getId(), regla.getId(), "texto libre");
        marca("AREA", dar.getId(), regla.getId(), "texto libre");

        List<Map<String, Object>> hs = reportRepository.getLedHallazgos(dia, dia, null, null);
        assertEquals(2, hs.size());

        Map<String, Object> hArmador = hs.stream().filter(h -> "ARMADOR".equals(h.get("declaracionTipo"))).findFirst().orElseThrow();
        assertEquals("EMBARCACION", hArmador.get("tipo"));
        assertEquals(da.getFolioDesembarqueDa(), hArmador.get("folio"));
        assertEquals("Doña Rosa LED06", hArmador.get("embarcacion"));
        assertEquals("Chungungo LED06", hArmador.get("caleta"));
        assertEquals(2600.0, ((Number) hArmador.get("kgDia")).doubleValue(), 0.001);
        assertEquals(2000.0, ((Number) hArmador.get("limiteKg")).doubleValue(), 0.001);
        assertEquals(600.0, ((Number) hArmador.get("excesoKg")).doubleValue(), 0.001);

        Map<String, Object> hArea = hs.stream().filter(h -> "AREA".equals(h.get("declaracionTipo"))).findFirst().orElseThrow();
        assertEquals("AMERB", hArea.get("tipo"));
        assertEquals(dar.getFolioDesembarqueAmerb(), hArea.get("folio"));
        assertEquals("Doña Rosa LED06", hArea.get("embarcacion"));
        assertEquals("Chungungo LED06", hArea.get("caleta"));
        assertEquals("Coquimbo LED06", hArea.get("region"));
        assertEquals(2400.0, ((Number) hArea.get("kgDia")).doubleValue(), 0.001);
        assertEquals(400.0, ((Number) hArea.get("excesoKg")).doubleValue(), 0.001);

        // El filtro por embarcación y por región alcanza también a la marca de área
        assertEquals(2, reportRepository.getLedHallazgos(dia, dia, region.getId(), embarcacion.getId()).size());
        // Fuera del período no sale ninguna
        java.sql.Date despues = java.sql.Date.valueOf("2026-11-07");
        assertTrue(reportRepository.getLedHallazgos(despues, despues, null, null).isEmpty());
    }

    @Test
    @DisplayName("NL.3 · Sin declaración, sin regla y sin texto que calce: kilos, límite y exceso van en null, sin relleno")
    void hallazgos_sinDato_noRellena() {
        marca("AREA", 999_999L, null, "texto que no calza");
        marca("ARMADOR", null, null,
                "Embarcación LED06-0001 — armador 31.111.111-1 — 2026-11-06: 2500,00 kg acumulados sobre límite de 2000,00 kg (exceso 500,00 kg, 25,0%)");

        List<Map<String, Object>> hs = reportRepository.getLedHallazgos(null, null, null, null);
        assertEquals(2, hs.size());

        Map<String, Object> sinDato = hs.stream().filter(h -> "AREA".equals(h.get("declaracionTipo"))).findFirst().orElseThrow();
        assertNull(sinDato.get("folio"), "no se inventa un folio DA-<id>");
        assertNull(sinDato.get("kgDia"));
        assertNull(sinDato.get("limiteKg"), "no se rellena con 2.000 kg");
        assertNull(sinDato.get("excesoKg"));
        assertEquals(Boolean.FALSE, sinDato.get("declaracionEncontrada"));

        Map<String, Object> intento = hs.stream().filter(h -> "ARMADOR".equals(h.get("declaracionTipo"))).findFirst().orElseThrow();
        assertEquals("BLOQUEADO (Intento)", intento.get("folio"));
        assertEquals("LED06-0001", intento.get("embarcacion"));
        assertEquals(2500.0, ((Number) intento.get("kgDia")).doubleValue(), 0.001);
        assertEquals(2000.0, ((Number) intento.get("limiteKg")).doubleValue(), 0.001);
        assertEquals(500.0, ((Number) intento.get("excesoKg")).doubleValue(), 0.001);
        // Con dato primero; sin dato al final
        assertSame(intento, hs.get(0));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("QL.2 · Parámetros ?fecha y ?startDate/?endDate YYYY-MM-DD se interpretan en hora de Chile y no retroceden un día")
    void endpointLed_parametroFechaEnQueryNoCorreUnDiaEnChile() throws Exception {
        DeclaracionArmadorModel da = armador(2300);
        marca("ARMADOR", da.getId(), regla.getId(),
                "Embarcación LED06-0001 — armador 31.111.111-1 — 2026-11-06: 2300,00 kg acumulados sobre límite de 2000,00 kg (exceso 300,00 kg, 15,0%)");

        mockMvc.perform(get("/api/reportes/limite-extraccion-diario").param("fecha", "2026-11-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dia").value("2026-11-06"))
                .andExpect(jsonPath("$.totalMonitoreados").value(1))
                .andExpect(jsonPath("$.excedidos").value(1));

        mockMvc.perform(get("/api/reportes/led-hallazgos")
                        .param("startDate", "2026-11-06")
                        .param("endDate", "2026-11-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].declaracionId").value(da.getId().intValue()))
                .andExpect(jsonPath("$[0].fecha").value("2026-11-06"));
    }

    // ------------------------------------------------------------------ utilidades

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> detalle(Map<String, Object> res) {
        return (List<Map<String, Object>>) res.get("detalle");
    }

    private DeclaracionArmadorModel armador(int kg) {
        return armadorRepository.saveAndFlush(DeclaracionArmadorModel.builder()
                .usuario(armador).especie(especie).extraccionTipo(metodo).humedadEstado(humedad)
                .comuna(comuna).caleta(caleta).embarcacion(embarcacion).buzo(buzo)
                .fechaDeclaracion(dia).fechaExtraccion(dia).hora("10:00:00")
                .folioDesembarqueDa("DA-LED06-" + System.nanoTime())
                .tipoDestinatario("comercializador").codigoDestinatario("X").usuarioDestinatario(planta)
                .desembarque(new BigDecimal(kg)).captura((double) kg)
                .build());
    }

    private DeclaracionAreaModel area(int kg, boolean conEmbarcacion) {
        return areaRepository.saveAndFlush(DeclaracionAreaModel.builder()
                .usuario(organizacion).especie(especie).extraccionTipo(metodo).humedadEstado(humedad)
                .caleta(caleta).amerb(amerb)
                .embarcacion(conEmbarcacion ? embarcacion : null).buzo(conEmbarcacion ? buzo : null)
                .fechaDeclaracion(dia).fechaExtraccion(dia).hora("12:00:00")
                .folioDesembarqueAmerb("AM-LED06-" + System.nanoTime()).codigoSernapescaAmerb("AM-LED06")
                .desembarque((double) kg).captura((double) kg)
                .tipoDestinatario("comercializador").usuarioDestinatario(planta)
                .build());
    }

    private DeclaracionMarcaModel marca(String tipo, Long declaracionId, Long reglaId, String detalle) {
        return marcaRepository.saveAndFlush(DeclaracionMarcaModel.builder()
                .declaracionTipo(tipo).declaracionId(declaracionId).marca("LED_EXCEDIDO")
                .detalle(detalle).reglaId(reglaId)
                .build());
    }
}
