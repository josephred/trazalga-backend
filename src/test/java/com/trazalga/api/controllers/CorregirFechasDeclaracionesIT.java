package com.trazalga.api.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trazalga.api.ApiApplication;
import com.trazalga.api.IntegracionBase;
import com.trazalga.api.models.*;
import com.trazalga.api.repositories.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CorregirFechasDeclaracionesIT extends IntegracionBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;

    @Autowired private IRegionRepository regionRepo;
    @Autowired private IProvinciaRepository provinciaRepo;
    @Autowired private IComunaRepository comunaRepo;
    @Autowired private ICaletaRepository caletaRepo;
    @Autowired private IEspecieRepository especieRepo;
    @Autowired private IHumedadEstadoRepository humedadRepo;
    @Autowired private IExtraccionTipoRepository extraccionTipoRepo;
    @Autowired private IPerfilRepository perfilRepo;
    @Autowired private IUsuarioRepository usuarioRepo;
    @Autowired private IDeclaracionRecolectorRepository recolectorRepo;
    @Autowired private IDeclaracionMarcaRepository marcaRepo;
    @Autowired private IDeclaracionFechaAjusteLogRepository ajusteLogRepo;
    @Autowired private VedaEspecieRepository vedaRepo;
    @Autowired private ILimiteExtraccionDiarioConfigRepository ledRepo;

    private Long conGpsId;
    private Long sinGpsId;

    @BeforeEach
    void setUp() {
        limpiar();

        RegionModel reg = regionRepo.save(RegionModel.builder().nombre("Región Coquimbo ZT3").codigo("04").build());
        ProvinciaModel prov = provinciaRepo.save(ProvinciaModel.builder().nombre("Elqui ZT3").region(reg).build());
        ComunaModel com = comunaRepo.save(ComunaModel.builder().nombre("Coquimbo ZT3").provincia(prov).region(reg).build());
        CaletaModel cal = caletaRepo.save(CaletaModel.builder().nombre("Caleta ZT3").comuna(com).region(reg).build());

        EspecieModel esp = especieRepo.save(EspecieModel.builder().nombre("Huiro Palo ZT3").activo(true).build());
        HumedadEstadoModel hum = humedadRepo.save(HumedadEstadoModel.builder().nombre("Húmedo ZT3").build());
        ExtraccionTipoModel ext = extraccionTipoRepo.save(ExtraccionTipoModel.builder().nombre("Varado ZT3").build());

        PerfilModel perfilRec = perfilRepo.findById(10L)
                .orElseGet(() -> perfilRepo.save(PerfilModel.builder().id(10L).nombre("RECOLECTOR").build()));
        UsuarioModel userRec = usuarioRepo.save(UsuarioModel.builder()
                .rut("22220011").nombres("Recolector ZT3").apellidop("P").apellidom("M")
                .clave("1234").perfil(perfilRec).comuna(com).estado("1").build());

        // Veda vigente solo el 2026-11-17 (el día equivocado por UTC), NO el 2026-11-16 (día real del GPS)
        VedaEspecieModel veda = new VedaEspecieModel();
        veda.setEspecie(esp);
        veda.setRegion(reg);
        veda.setExtraccionTipo(ext);
        veda.setRecurrenciaAnual(false);
        veda.setFechaInicio(Date.valueOf(LocalDate.of(2026, 11, 17)));
        veda.setFechaFin(Date.valueOf(LocalDate.of(2026, 11, 20)));
        veda.setActivo(true);
        veda.setResolucion("RES-ZT3");
        vedaRepo.save(veda);

        // Regla LED vigente el 2026-11-16 con límite 500 kg -> 800 kg gatillará LED_EXCEDIDO en fechaProbable
        LimiteExtraccionDiarioConfigModel led = new LimiteExtraccionDiarioConfigModel();
        led.setNombreRegla("LED ZT3");
        led.setPerfilAplicable("RECOLECTOR");
        led.setUnidadAgregacion("PESCADOR");
        led.setMetrica("DESEMBARQUE");
        led.setEspecie(esp);
        led.setExtraccionTipo(ext);
        led.setRegion(reg);
        led.setLimiteKg(new BigDecimal("500.00"));
        led.setMargenToleranciaPct(BigDecimal.ZERO);
        led.setModoAccion("SOLO_ALERTA");
        led.setActivo(true);
        led.setVigenciaInicio(Date.valueOf(LocalDate.of(2026, 11, 1)));
        led.setVigenciaFin(Date.valueOf(LocalDate.of(2026, 11, 30)));
        ledRepo.save(led);

        // Declaración guardada el 2026-11-16 22:30 CLST (UTC 2026-11-17 01:30) con fecha corrida a 2026-11-17
        java.util.Date gpsNocturno = java.util.Date.from(
                LocalDateTime.of(2026, 11, 16, 22, 30, 0)
                        .atZone(ApiApplication.ZONA_CHILE).toInstant());

        DeclaracionRecolectorModel conGps = DeclaracionRecolectorModel.builder()
                .usuario(userRec)
                .usuarioDestinatario(userRec)
                .codigoDestinatario(userRec.getRut())
                .nombreDestinatario(userRec.getNombres())
                .folioOrigen("FOL-ZT3-GPS")
                .fechaDeclaracion(Date.valueOf(LocalDate.of(2026, 11, 17)))
                .fechaExtraccion(Date.valueOf(LocalDate.of(2026, 11, 17)))
                .hora("22:30:00")
                .gpsCapturadoEn(gpsNocturno)
                .nombre(userRec.getNombres())
                .codigoSernapesca("RPA-1")
                .caleta(cal)
                .comuna(com)
                .especie(esp)
                .humedadEstado(hum)
                .extraccionTipo(ext)
                .desembarque(new BigDecimal("800.00"))
                .captura(new BigDecimal("800.00"))
                .build();
        conGpsId = recolectorRepo.save(conGps).getId();

        // Marca existente EN_VEDA que dejaría de corresponder al corregir la fecha al 2026-11-16
        marcaRepo.save(DeclaracionMarcaModel.builder()
                .declaracionTipo("RECOLECTOR")
                .declaracionId(conGpsId)
                .marca("EN_VEDA")
                .detalle("Veda en fecha corrida")
                .resuelta(false)
                .estadoGestion("PENDIENTE")
                .build());

        // Declaración sin gps_capturado_en (nunca debe tocarse)
        DeclaracionRecolectorModel sinGps = DeclaracionRecolectorModel.builder()
                .usuario(userRec)
                .usuarioDestinatario(userRec)
                .codigoDestinatario(userRec.getRut())
                .nombreDestinatario(userRec.getNombres())
                .folioOrigen("FOL-ZT3-NOGPS")
                .fechaDeclaracion(Date.valueOf(LocalDate.of(2026, 11, 17)))
                .fechaExtraccion(Date.valueOf(LocalDate.of(2026, 11, 17)))
                .hora("10:00:00")
                .gpsCapturadoEn(null)
                .nombre(userRec.getNombres())
                .codigoSernapesca("RPA-1")
                .caleta(cal)
                .comuna(com)
                .especie(esp)
                .humedadEstado(hum)
                .extraccionTipo(ext)
                .desembarque(new BigDecimal("100.00"))
                .captura(new BigDecimal("100.00"))
                .build();
        sinGpsId = recolectorRepo.save(sinGps).getId();
    }

    @AfterEach
    void tearDown() {
        limpiar();
    }

    private void limpiar() {
        try {
            jdbc.execute("DROP TABLE IF EXISTS zz_respaldo_fechas_20261116");
        } catch (Exception ignored) {
        }
        ajusteLogRepo.deleteAll();
        marcaRepo.deleteAll();
        recolectorRepo.deleteAll();
        ledRepo.deleteAll();
        vedaRepo.deleteAll();
        usuarioRepo.deleteAll();
        caletaRepo.deleteAll();
        comunaRepo.deleteAll();
        provinciaRepo.deleteAll();
        regionRepo.deleteAll();
        extraccionTipoRepo.deleteAll();
        humedadRepo.deleteAll();
        especieRepo.deleteAll();
    }

    @Test
    @DisplayName("ZT.3 IT: simulación no escribe; aplicar respalda, corrige solo con GPS, no toca marcas e idempotencia en segunda corrida")
    void simulacionYAplicacionCorreccionFechas() throws Exception {
        // Sin rol ADMIN -> 403
        mockMvc.perform(post("/api/admin/declaraciones/corregir-fechas?aplicar=false")
                        .with(user("22220011").roles("RECOLECTOR")))
                .andExpect(status().isForbidden());

        // 1. Simulación (aplicar=false)
        MvcResult simRes = mockMvc.perform(post("/api/admin/declaraciones/corregir-fechas?aplicar=false")
                        .with(user("1111").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode sim = objectMapper.readTree(simRes.getResponse().getContentAsString());
        assertFalse(sim.get("aplicado").asBoolean());
        assertEquals(1, sim.get("totalPorCorregir").asInt());
        assertEquals(0, sim.get("actualizadas").asInt());
        assertEquals(1, sim.get("sinGpsCapturadoEnTotal").asInt());

        JsonNode cambio = sim.get("cambios").get(0);
        assertEquals("RECOLECTOR", cambio.get("tipo").asText());
        assertEquals(conGpsId.longValue(), cambio.get("id").asLong());
        assertEquals("2026-11-17", cambio.get("fechaGuardada").asText());
        assertEquals("2026-11-16", cambio.get("fechaProbable").asText());
        assertTrue(cambio.get("marcasQueDejarianDeCorresponder").toString().contains("EN_VEDA"));
        assertTrue(cambio.get("marcasQueAparecerian").toString().contains("LED_EXCEDIDO"));

        // Confirmar que simulación no escribió en BD
        assertEquals(LocalDate.of(2026, 11, 17),
                ((java.sql.Date) recolectorRepo.findById(conGpsId).orElseThrow().getFechaDeclaracion()).toLocalDate());
        assertEquals(0, ajusteLogRepo.count());

        // 2. Aplicar (aplicar=true)
        MvcResult appRes = mockMvc.perform(post("/api/admin/declaraciones/corregir-fechas?aplicar=true")
                        .with(user("1111").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode app = objectMapper.readTree(appRes.getResponse().getContentAsString());
        assertTrue(app.get("aplicado").asBoolean());
        assertEquals(1, app.get("actualizadas").asInt());
        assertEquals(0, app.get("marcasModificadas").asInt());

        // Declaración con GPS corregida; sin GPS intacta
        DeclaracionRecolectorModel corregida = recolectorRepo.findById(conGpsId).orElseThrow();
        assertEquals(LocalDate.of(2026, 11, 16), ((java.sql.Date) corregida.getFechaDeclaracion()).toLocalDate());
        assertEquals(LocalDate.of(2026, 11, 16), ((java.sql.Date) corregida.getFechaExtraccion()).toLocalDate());

        DeclaracionRecolectorModel intacta = recolectorRepo.findById(sinGpsId).orElseThrow();
        assertEquals(LocalDate.of(2026, 11, 17), ((java.sql.Date) intacta.getFechaDeclaracion()).toLocalDate());

        // Respaldo y log creados; marcas sin alterar
        Integer respaldoCnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM zz_respaldo_fechas_20261116 WHERE declaracion_tipo='RECOLECTOR' AND declaracion_id=?",
                Integer.class, conGpsId);
        assertEquals(1, respaldoCnt);
        assertEquals(1, ajusteLogRepo.count());
        assertEquals("CORRECCION_HISTORICA_GPS", ajusteLogRepo.findAll().get(0).getMotivo());

        var marcas = marcaRepo.findByDeclaracionTipoAndDeclaracionId("RECOLECTOR", conGpsId);
        assertEquals(1, marcas.size());
        assertEquals("EN_VEDA", marcas.get(0).getMarca());
        assertFalse(Boolean.TRUE.equals(marcas.get(0).getResuelta()));

        // 3. Segunda corrida no cambia nada
        MvcResult app2Res = mockMvc.perform(post("/api/admin/declaraciones/corregir-fechas?aplicar=true")
                        .with(user("1111").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode app2 = objectMapper.readTree(app2Res.getResponse().getContentAsString());
        assertEquals(0, app2.get("totalPorCorregir").asInt());
        assertEquals(0, app2.get("actualizadas").asInt());
    }
}
