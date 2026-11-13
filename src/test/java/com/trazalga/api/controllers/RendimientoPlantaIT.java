package com.trazalga.api.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trazalga.api.IntegracionBase;
import com.trazalga.api.models.DeclaracionMarcaModel;
import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.FactorConversionModel;
import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.models.ProductoModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IDeclaracionMarcaRepository;
import com.trazalga.api.repositories.IDeclaracionPlantaAbastecimientoRepository;
import com.trazalga.api.repositories.IDeclaracionPlantaDestinoRepository;
import com.trazalga.api.repositories.IDeclaracionPlantaProduccionRepository;
import com.trazalga.api.repositories.IEspecieRepository;
import com.trazalga.api.repositories.IFactorConversionRepository;
import com.trazalga.api.repositories.IHumedadEstadoRepository;
import com.trazalga.api.repositories.IProductoRepository;
import com.trazalga.api.repositories.IRendimientoRangoRepository;
import com.trazalga.api.repositories.IUsuarioRepository;
import com.trazalga.api.services.ConfiguracionGeneralService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RendimientoPlantaIT extends IntegracionBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ConfiguracionGeneralService configService;
    @Autowired private IUsuarioRepository usuarioRepo;
    @Autowired private IEspecieRepository especieRepo;
    @Autowired private IHumedadEstadoRepository humedadRepo;
    @Autowired private IProductoRepository productoRepo;
    @Autowired private IFactorConversionRepository factorRepo;
    @Autowired private IRendimientoRangoRepository rangoRepo;
    @Autowired private IDeclaracionPlantaAbastecimientoRepository abastRepo;
    @Autowired private IDeclaracionPlantaProduccionRepository prodRepo;
    @Autowired private IDeclaracionPlantaDestinoRepository destinoRepo;
    @Autowired private IDeclaracionMarcaRepository marcaRepo;

    private UsuarioModel planta;
    private EspecieModel pelillo;
    private HumedadEstadoModel humHumedo;
    private HumedadEstadoModel humSeco;
    private ProductoModel prodSeco;

    @BeforeEach
    void setup() {
        jdbc.execute("ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN IF NOT EXISTS folio_declaracion_a_pla VARCHAR(50)");
        jdbc.execute("ALTER TABLE declaracion_planta_produccion ADD COLUMN IF NOT EXISTS folio_declaracion_p_pla VARCHAR(50)");

        destinoRepo.deleteAll();
        prodRepo.deleteAll();
        abastRepo.deleteAll();
        marcaRepo.deleteAll();
        rangoRepo.deleteAll();
        productoRepo.deleteAll();

        pelillo = especieRepo.findAll().stream()
                .filter(e -> "PELILLO".equalsIgnoreCase(e.getNombre()))
                .findFirst()
                .orElseGet(() -> especieRepo.save(EspecieModel.builder().nombre("PELILLO").activo(true).build()));

        humHumedo = humedadRepo.findAll().stream()
                .filter(h -> h.getNombre().toLowerCase().contains("med"))
                .findFirst()
                .orElseGet(() -> humedadRepo.save(HumedadEstadoModel.builder().nombre("Humedo").rangoInicio(70).rangoFin(90).build()));

        humSeco = humedadRepo.findAll().stream()
                .filter(h -> "Seco".equalsIgnoreCase(h.getNombre()))
                .findFirst()
                .orElseGet(() -> humedadRepo.save(HumedadEstadoModel.builder().nombre("Seco").rangoInicio(1).rangoFin(25).build()));

        factorRepo.deleteAll();
        factorRepo.save(FactorConversionModel.builder()
                .especie(pelillo).humedadEstado(humHumedo).factor(new BigDecimal("1.00"))
                .vigenciaInicio(Date.valueOf("2025-01-01")).activo(true).build());
        factorRepo.save(FactorConversionModel.builder()
                .especie(pelillo).humedadEstado(humSeco).factor(new BigDecimal("3.16815"))
                .vigenciaInicio(Date.valueOf("2025-01-01")).activo(true).build());

        prodSeco = productoRepo.save(ProductoModel.builder()
                .codigo("SER-NP-SECO").nombre("Pelillo Seco Enfardado").descripcion("Formato NP.9")
                .humedadEstado(humSeco).especie(pelillo).activo(true).fuente("LOCAL").build());

        planta = usuarioRepo.findByRut("55.555.555-5").orElseGet(() ->
                usuarioRepo.saveAndFlush(UsuarioModel.builder()
                        .rut("55.555.555-5").nombres("Planta").apellidop("Rendimiento")
                        .estado("ACTIVO").build()));

        configService.updateConfig("declaracion_escritura_exige_sesion", "false");
        configService.updateConfig("produccion_exige_formato_oficial", "false");
        configService.updateConfig("rendimiento_activo", "true");
        configService.updateConfig("rendimiento_tolerancia_pct", "5");
        configService.updateConfig("marcas_bloqueantes_produccion", "RENDIMIENTO_ANOMALO");
    }

    @AfterEach
    void tearDown() {
        configService.updateConfig("declaracion_escritura_exige_sesion", "false");
        configService.updateConfig("rendimiento_activo", "true");
        configService.updateConfig("rendimiento_tolerancia_pct", "5");
        configService.updateConfig("marcas_bloqueantes_produccion", "RENDIMIENTO_ANOMALO");
    }

    @Test
    @DisplayName("NP.9 E2E: rendimiento normal permite destino; rendimiento anómalo bloquea destino (409), congela edición (423), libera fiscalizador y reporta métricas")
    void flujoCompletoRendimientoYCongelamiento() throws Exception {
        // Dos recepciones A-PLA: 10000 kg húmedo (romana) para flujo anómalo y 10000 kg húmedo para flujo normal
        DeclaracionPlantaAbastecimientoModel loteNormal = abastRepo.save(DeclaracionPlantaAbastecimientoModel.builder()
                .folioOrigen("C-NORM-1").folioDeclaracionAPla("APLA-NORM-1")
                .fechaIngresoPlanta(Date.valueOf("2026-11-10")).fechaTraslado(Date.valueOf("2026-11-10")).hora("09:00")
                .nombrePlanta("Planta Rendimiento").codigoSernapesca("PL-55")
                .usuario(planta).usuarioDestinatario(planta)
                .especie(pelillo).humedadEstado(humHumedo).humedadEstadoRecepcion(humHumedo)
                .cantidad(new BigDecimal("10500")).pesoRomanaKg(new BigDecimal("10000"))
                .build());

        DeclaracionPlantaAbastecimientoModel loteAnom = abastRepo.save(DeclaracionPlantaAbastecimientoModel.builder()
                .folioOrigen("C-ANOM-1").folioDeclaracionAPla("APLA-ANOM-1")
                .fechaIngresoPlanta(Date.valueOf("2026-11-10")).fechaTraslado(Date.valueOf("2026-11-10")).hora("09:30")
                .nombrePlanta("Planta Rendimiento").codigoSernapesca("PL-55")
                .usuario(planta).usuarioDestinatario(planta)
                .especie(pelillo).humedadEstado(humHumedo).humedadEstadoRecepcion(humHumedo)
                .cantidad(new BigDecimal("10500")).pesoRomanaKg(new BigDecimal("10000"))
                .build());

        // 1. Camino feliz: cliente manda 999 kg pero servidor calcula 10000 kg desde loteNormal; 3100 kg seco -> 31.00% <= 31.56%*1.05
        String bodyNormal = """
                {
                  "folioOrigen": "APLA-NORM-1",
                  "folioDeclaracionPpla": "PPLA-NORM-1",
                  "fechaProduccion": "2026-11-11",
                  "hora": "10:00",
                  "nombrePlanta": "Planta Rendimiento",
                  "codigoSernapesca": "PL-55",
                  "usuario": { "id": %d },
                  "materiaPrimaEspecie": { "id": %d },
                  "humedadEstado": { "id": %d },
                  "productoResultante": { "id": %d },
                  "cantidadMateriaPrima": 999,
                  "cantidadProducto": 3100,
                  "declaracionesSeleccionadas": "PLANTA_ABASTECIMIENTO:%d"
                }
                """.formatted(planta.getId(), pelillo.getId(), humSeco.getId(), prodSeco.getId(), loteNormal.getId());

        MvcResult resNorm = mockMvc.perform(post("/declaracionplantaproduccion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyNormal))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.cantidadMateriaPrima").value(10000.0))
                .andExpect(jsonPath("$.rendimientoPct").value(31.0))
                .andExpect(jsonPath("$.rendimientoFuente").value("FACTORES"))
                .andExpect(jsonPath("$.bloqueada").value(false))
                .andReturn();

        Long prodNormalId = objectMapper.readTree(resNorm.getResponse().getContentAsString()).get("id").asLong();
        assertTrue(marcaRepo.findByDeclaracionTipoAndDeclaracionId("PLANTA_PRODUCCION", prodNormalId).isEmpty());

        // Destino sobre producción normal -> 200 OK
        String destNormal = """
                {
                  "folioOrigen": "PPLA-NORM-1",
                  "folioDeclaracionDestino": "DPLA-NORM-1",
                  "fechaDeclaracionDestino": "2026-11-12",
                  "fechaTrasladoDestino": "2026-11-12",
                  "hora": "12:00",
                  "nombrePlanta": "Planta Rendimiento",
                  "codigoSernapesca": "PL-55",
                  "usuario": { "id": %d },
                  "especie": { "id": %d },
                  "producto": { "id": %d },
                  "cantidad": 3100,
                  "nombreDestino": "Exportadora Sur",
                  "rutDestino": "77.777.777-7",
                  "documentoTributarioTipo": "GUIA",
                  "documentoTributarioNumero": "G-NORM-1",
                  "declaracionesSeleccionadas": "PLANTA_PRODUCCION:%d"
                }
                """.formatted(planta.getId(), pelillo.getId(), prodSeco.getId(), prodNormalId);

        mockMvc.perform(post("/api/declaracion-planta-destino")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(destNormal))
                .andExpect(status().is2xxSuccessful());

        // 2. Camino anómalo: 4000 kg seco -> 40.00% -> RENDIMIENTO_ANOMALO + bloquea Destino (409) + congela edición (423)
        String bodyAnom = """
                {
                  "folioOrigen": "APLA-ANOM-1",
                  "folioDeclaracionPpla": "PPLA-ANOM-1",
                  "fechaProduccion": "2026-11-11",
                  "hora": "11:00",
                  "nombrePlanta": "Planta Rendimiento",
                  "codigoSernapesca": "PL-55",
                  "usuario": { "id": %d },
                  "materiaPrimaEspecie": { "id": %d },
                  "humedadEstado": { "id": %d },
                  "productoResultante": { "id": %d },
                  "cantidadMateriaPrima": 10000,
                  "cantidadProducto": 4000,
                  "declaracionesSeleccionadas": "PLANTA_ABASTECIMIENTO:%d"
                }
                """.formatted(planta.getId(), pelillo.getId(), humSeco.getId(), prodSeco.getId(), loteAnom.getId());

        MvcResult resAnom = mockMvc.perform(post("/declaracionplantaproduccion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyAnom))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.rendimientoPct").value(40.0))
                .andExpect(jsonPath("$.bloqueada").value(true))
                .andExpect(jsonPath("$.edicionBloqueada").value(true))
                .andReturn();

        Long prodAnomId = objectMapper.readTree(resAnom.getResponse().getContentAsString()).get("id").asLong();
        List<DeclaracionMarcaModel> marcas = marcaRepo.findByDeclaracionTipoAndDeclaracionId("PLANTA_PRODUCCION", prodAnomId);
        assertEquals(1, marcas.size());
        assertEquals("RENDIMIENTO_ANOMALO", marcas.get(0).getMarca());
        assertFalse(Boolean.TRUE.equals(marcas.get(0).getResuelta()));

        // Destino sobre producción anómala -> 409 Conflict
        String destAnom = """
                {
                  "folioOrigen": "PPLA-ANOM-1",
                  "folioDeclaracionDestino": "DPLA-ANOM-1",
                  "fechaDeclaracionDestino": "2026-11-12",
                  "fechaTrasladoDestino": "2026-11-12",
                  "hora": "13:00",
                  "nombrePlanta": "Planta Rendimiento",
                  "codigoSernapesca": "PL-55",
                  "usuario": { "id": %d },
                  "especie": { "id": %d },
                  "producto": { "id": %d },
                  "cantidad": 4000,
                  "nombreDestino": "Exportadora Sur",
                  "rutDestino": "77.777.777-7",
                  "documentoTributarioTipo": "GUIA",
                  "documentoTributarioNumero": "G-ANOM-1",
                  "declaracionesSeleccionadas": "PLANTA_PRODUCCION:%d"
                }
                """.formatted(planta.getId(), pelillo.getId(), prodSeco.getId(), prodAnomId);

        mockMvc.perform(post("/api/declaracion-planta-destino")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(destAnom))
                .andExpect(status().isConflict());

        // Dueño intenta editar producción congelada sin autorización -> 423 Locked
        mockMvc.perform(put("/declaracionplantaproduccion/" + prodAnomId)
                        .with(user(planta.getRut()).roles("PLANTA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyAnom.replace("\"cantidadProducto\": 4000", "\"cantidadProducto\": 3050")))
                .andExpect(status().isLocked());

        // Endpoint de reporte refleja 1 normal y 1 anómala antes de resolver
        mockMvc.perform(get("/api/reportes/rendimiento-planta")
                        .with(user("fisca").roles("FISCALIZADOR"))
                        .param("startDate", "2026-11-01")
                        .param("endDate", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumen.total").value(2))
                .andExpect(jsonPath("$.resumen.normales").value(1))
                .andExpect(jsonPath("$.resumen.anomalas").value(1))
                .andExpect(jsonPath("$.porPlantaFormato", hasSize(1)))
                .andExpect(jsonPath("$.detalle", hasSize(2)));

        // Fiscalizador autoriza edición y corrige/resuelve marca -> marca histórica se conserva (resuelta=true)
        mockMvc.perform(post("/api/declaraciones/PLANTA_PRODUCCION/" + prodAnomId + "/autorizar-edicion")
                        .with(user("fisca").roles("FISCALIZADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Error de digitación verificado\",\"actaVerificacion\":\"ACTA-NP9\",\"horas\":24}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/declaracionplantaproduccion/" + prodAnomId)
                        .with(user(planta.getRut()).roles("PLANTA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyAnom.replace("\"cantidadProducto\": 4000", "\"cantidadProducto\": 3050")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rendimientoPct").value(30.5));

        // La marca anómala previa sigue existiendo como rastro histórico (no se borró al recalcular)
        assertEquals(1, marcaRepo.findByDeclaracionTipoAndDeclaracionId("PLANTA_PRODUCCION", prodAnomId).size());

        // Fiscalizador resuelve la marca liberando el destino
        mockMvc.perform(put("/api/declaracion-marcas/" + marcas.get(0).getId() + "/resolver")
                        .with(user("fisca").roles("FISCALIZADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolucionTipo\":\"LIBERADA\",\"observacion\":\"Peso corregido a 3050 kg\"}"))
                .andExpect(status().isOk());

        // Ahora Destino ya puede despachar la producción liberada
        mockMvc.perform(post("/api/declaracion-planta-destino")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(destAnom.replace("\"cantidad\": 4000", "\"cantidad\": 3050")))
                .andExpect(status().is2xxSuccessful());
    }
}
