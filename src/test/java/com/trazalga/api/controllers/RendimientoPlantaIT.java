package com.trazalga.api.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trazalga.api.IntegracionBase;
import com.trazalga.api.dto.TrazabilidadNodoDTO;
import com.trazalga.api.models.CaletaModel;
import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.models.DeclaracionArmadorModel;
import com.trazalga.api.models.DeclaracionComercializadorModel;
import com.trazalga.api.models.DeclaracionMarcaModel;
import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import com.trazalga.api.models.DeclaracionPlantaProduccionModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.models.BuzoModel;
import com.trazalga.api.models.EmbarcacionModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.ExtraccionTipoModel;
import com.trazalga.api.models.FactorConversionModel;
import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.models.ProductoModel;
import com.trazalga.api.models.ProvinciaModel;
import com.trazalga.api.models.RegionModel;
import com.trazalga.api.models.RendimientoRangoModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IBuzoRepository;
import com.trazalga.api.repositories.ICaletaRepository;
import com.trazalga.api.repositories.IComunaRepository;
import com.trazalga.api.repositories.IDeclaracionArmadorRepository;
import com.trazalga.api.repositories.IDeclaracionComercializadorRepository;
import com.trazalga.api.repositories.IDeclaracionMarcaRepository;
import com.trazalga.api.repositories.IDeclaracionPlantaAbastecimientoRepository;
import com.trazalga.api.repositories.IDeclaracionPlantaDestinoRepository;
import com.trazalga.api.repositories.IDeclaracionPlantaProduccionRepository;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;
import com.trazalga.api.repositories.IEmbarcacionRepository;
import com.trazalga.api.repositories.IEspecieRepository;
import com.trazalga.api.repositories.IExtraccionTipoRepository;
import com.trazalga.api.repositories.IFactorConversionRepository;
import com.trazalga.api.repositories.IHumedadEstadoRepository;
import com.trazalga.api.repositories.IProductoRepository;
import com.trazalga.api.repositories.IProvinciaRepository;
import com.trazalga.api.repositories.IRegionRepository;
import com.trazalga.api.repositories.IRendimientoRangoRepository;
import com.trazalga.api.repositories.IUsuarioRepository;
import com.trazalga.api.services.CadenaCustodiaService;
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
import java.util.ArrayList;
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
    @Autowired private IDeclaracionRecolectorRepository recolectorRepo;
    @Autowired private IDeclaracionArmadorRepository armadorRepo;
    @Autowired private IDeclaracionComercializadorRepository comercializadorRepo;
    @Autowired private IRegionRepository regionRepo;
    @Autowired private IProvinciaRepository provinciaRepo;
    @Autowired private IComunaRepository comunaRepo;
    @Autowired private ICaletaRepository caletaRepo;
    @Autowired private IExtraccionTipoRepository extraccionTipoRepo;
    @Autowired private IEmbarcacionRepository embarcacionRepo;
    @Autowired private IBuzoRepository buzoRepo;
    @Autowired private CadenaCustodiaService cadenaCustodiaService;

    private UsuarioModel planta;
    private UsuarioModel recUsuario;
    private UsuarioModel armUsuario;
    private UsuarioModel comUsuario;
    private ComunaModel comunaQr6;
    private CaletaModel caletaQr6;
    private ExtraccionTipoModel metodoQr6;
    private EmbarcacionModel embarcacionQr6;
    private BuzoModel buzoQr6;
    private final List<Long> createdRecIds = new ArrayList<>();
    private final List<Long> createdArmIds = new ArrayList<>();
    private final List<Long> createdComIds = new ArrayList<>();
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
        configService.updateConfig("rendimiento_base", "CAPTURA_ORIGEN");
        configService.updateConfig("marcas_bloqueantes_produccion", "RENDIMIENTO_ANOMALO");
    }

    @AfterEach
    void tearDown() {
        destinoRepo.deleteAll();
        prodRepo.deleteAll();
        abastRepo.deleteAll();
        if (!createdComIds.isEmpty()) {
            comercializadorRepo.deleteAllByIdInBatch(createdComIds);
            createdComIds.clear();
        }
        if (!createdRecIds.isEmpty()) {
            recolectorRepo.deleteAllByIdInBatch(createdRecIds);
            createdRecIds.clear();
        }
        if (!createdArmIds.isEmpty()) {
            armadorRepo.deleteAllByIdInBatch(createdArmIds);
            createdArmIds.clear();
        }
        configService.updateConfig("declaracion_escritura_exige_sesion", "false");
        configService.updateConfig("rendimiento_activo", "true");
        configService.updateConfig("rendimiento_tolerancia_pct", "5");
        configService.updateConfig("rendimiento_base", "CAPTURA_ORIGEN");
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
                .andExpect(jsonPath("$.rendimientoFuente").value("RESPALDO_MASA_FACTORES"))
                .andExpect(jsonPath("$.capturaOrigenCompleta").value(false))
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

    private void configurarFactorSeco35800() {
        factorRepo.deleteAll();
        factorRepo.save(FactorConversionModel.builder()
                .especie(pelillo).humedadEstado(humHumedo).factor(new BigDecimal("1.00"))
                .vigenciaInicio(Date.valueOf("2025-01-01")).activo(true).build());
        factorRepo.save(FactorConversionModel.builder()
                .especie(pelillo).humedadEstado(humSeco).factor(new BigDecimal("3.5800"))
                .vigenciaInicio(Date.valueOf("2025-01-01")).activo(true).build());
    }

    private void prepararActoresOrigenQr6() {
        if (comunaQr6 == null) {
            RegionModel region = regionRepo.findAll().stream().findFirst().orElseGet(() ->
                    regionRepo.saveAndFlush(RegionModel.builder().nombre("Coquimbo QR6").codigo("04").build()));
            ProvinciaModel provincia = provinciaRepo.findAll().stream().findFirst().orElseGet(() ->
                    provinciaRepo.saveAndFlush(ProvinciaModel.builder().nombre("Elqui QR6").region(region).build()));
            comunaQr6 = comunaRepo.findAll().stream().findFirst().orElseGet(() ->
                    comunaRepo.saveAndFlush(ComunaModel.builder().nombre("La Serena QR6").region(region).provincia(provincia).build()));
            caletaQr6 = caletaRepo.findAll().stream().findFirst().orElseGet(() ->
                    caletaRepo.saveAndFlush(CaletaModel.builder().nombre("Caleta QR6").region(region).comuna(comunaQr6).build()));
            metodoQr6 = extraccionTipoRepo.findAll().stream().findFirst().orElseGet(() ->
                    extraccionTipoRepo.saveAndFlush(ExtraccionTipoModel.builder().nombre("Barreteado QR6").build()));
            embarcacionQr6 = embarcacionRepo.findAll().stream().findFirst().orElseGet(() ->
                    embarcacionRepo.saveAndFlush(EmbarcacionModel.builder().nombre("Bote QR6").codigo("QR6-EMB-1").build()));
            buzoQr6 = buzoRepo.findAll().stream().findFirst().orElseGet(() ->
                    buzoRepo.saveAndFlush(BuzoModel.builder().nombre("Buzo QR6").codigo("QR6-BZ-1").build()));
            recUsuario = usuarioRepo.findByRut("51.111.111-1").orElseGet(() ->
                    usuarioRepo.saveAndFlush(UsuarioModel.builder()
                            .rut("51.111.111-1").nombres("Recolector").apellidop("QR6").comuna(comunaQr6).estado("ACTIVO").build()));
            armUsuario = usuarioRepo.findByRut("52.222.222-2").orElseGet(() ->
                    usuarioRepo.saveAndFlush(UsuarioModel.builder()
                            .rut("52.222.222-2").nombres("Armador").apellidop("QR6").comuna(comunaQr6).estado("ACTIVO").build()));
            comUsuario = usuarioRepo.findByRut("53.333.333-3").orElseGet(() ->
                    usuarioRepo.saveAndFlush(UsuarioModel.builder()
                            .rut("53.333.333-3").nombres("Comercializador").apellidop("QR6").comuna(comunaQr6).estado("ACTIVO").build()));
        }
    }

    private DeclaracionRecolectorModel crearRecolectorQr6(Date dia, BigDecimal captura, String folio) {
        DeclaracionRecolectorModel rec = recolectorRepo.saveAndFlush(DeclaracionRecolectorModel.builder()
                .usuario(recUsuario).folioOrigen(folio).fechaExtraccion(dia).fechaDeclaracion(dia).hora("08:00:00")
                .nombre("Recolector QR6").codigoSernapesca("RPA-QR6").caleta(caletaQr6).especie(pelillo)
                .comuna(comunaQr6).extraccionTipo(metodoQr6).humedadEstado(humHumedo)
                .desembarque(captura).captura(captura)
                .codigoDestinatario(planta.getRut()).nombreDestinatario("Planta Rendimiento")
                .usuarioDestinatario(planta).build());
        createdRecIds.add(rec.getId());
        return rec;
    }

    private DeclaracionArmadorModel crearArmadorQr6(Date dia, double captura, String folio) {
        DeclaracionArmadorModel arm = armadorRepo.saveAndFlush(DeclaracionArmadorModel.builder()
                .usuario(armUsuario).folioOrigen(folio).fechaExtraccion(dia).fechaDeclaracion(dia).hora("08:30:00")
                .embarcacion(embarcacionQr6).buzo(buzoQr6).caleta(caletaQr6).comuna(comunaQr6)
                .especie(pelillo).extraccionTipo(metodoQr6).humedadEstado(humHumedo)
                .desembarque(BigDecimal.valueOf(captura)).captura(captura)
                .tipoDestinatario("planta").codigoDestinatario(planta.getRut()).usuarioDestinatario(planta).build());
        createdArmIds.add(arm.getId());
        return arm;
    }

    private DeclaracionComercializadorModel crearComercializadorQr6(Date dia, String sel, BigDecimal cantidad, String folio) {
        DeclaracionComercializadorModel com = comercializadorRepo.saveAndFlush(DeclaracionComercializadorModel.builder()
                .usuario(comUsuario).folioOrigen(folio).fechaDeclaracion(dia).hora("09:00:00")
                .especie(pelillo).humedadEstado(humHumedo).cantidad(cantidad)
                .codigoDestinatario(planta.getRut()).nombreDestinatario("Planta Rendimiento")
                .usuarioDestinatario(planta).declaracionesSeleccionadas(sel).build());
        createdComIds.add(com.getId());
        return com;
    }

    @Test
    @DisplayName("QR.6 E2E: cadena Recolector + Armador -> Comercializador + directo -> 2 A-PLA -> P-PLA (factor 3.58), reporte y árbolDTO")
    void flujoCompletoCadenaOrigenYDeduplicacion_qr6() throws Exception {
        configurarFactorSeco35800();
        prepararActoresOrigenQr6();
        Date dia = Date.valueOf("2026-11-10");

        // Cadena normal compartida: RECOLECTOR (6000 kg) aparece en directo y vía COMERCIALIZADOR + ARMADOR (4000 kg) => captura única 10000 kg
        DeclaracionRecolectorModel recN = crearRecolectorQr6(dia, new BigDecimal("6000.00"), "OR-REC-N");
        DeclaracionArmadorModel armN = crearArmadorQr6(dia, 4000.0, "OR-ARM-N");
        DeclaracionComercializadorModel comN = crearComercializadorQr6(dia, "RECOLECTOR:" + recN.getId(), new BigDecimal("6000.00"), "COM-N");

        DeclaracionPlantaAbastecimientoModel loteN1 = abastRepo.saveAndFlush(DeclaracionPlantaAbastecimientoModel.builder()
                .folioOrigen("OR-N1").folioDeclaracionAPla("APLA-QR6-N1")
                .fechaIngresoPlanta(dia).fechaTraslado(dia).hora("10:00")
                .nombrePlanta("Planta Rendimiento").codigoSernapesca("PL-55")
                .usuario(planta).usuarioDestinatario(planta)
                .especie(pelillo).humedadEstado(humHumedo).humedadEstadoRecepcion(humHumedo)
                .cantidad(new BigDecimal("6000")).pesoRomanaKg(new BigDecimal("6000"))
                .declaracionesSeleccionadas("RECOLECTOR:" + recN.getId() + ",ARMADOR:" + armN.getId())
                .build());
        DeclaracionPlantaAbastecimientoModel loteN2 = abastRepo.saveAndFlush(DeclaracionPlantaAbastecimientoModel.builder()
                .folioOrigen("OR-N2").folioDeclaracionAPla("APLA-QR6-N2")
                .fechaIngresoPlanta(dia).fechaTraslado(dia).hora("10:15")
                .nombrePlanta("Planta Rendimiento").codigoSernapesca("PL-55")
                .usuario(planta).usuarioDestinatario(planta)
                .especie(pelillo).humedadEstado(humHumedo).humedadEstadoRecepcion(humHumedo)
                .cantidad(new BigDecimal("6000")).pesoRomanaKg(new BigDecimal("6000"))
                .declaracionesSeleccionadas("COMERCIALIZADOR:" + comN.getId())
                .build());

        // 2800 kg seco / 10000 kg origen = 28.00% <= 29.33% (normal); proceso = 2800 / 12000 = 23.33%
        String bodyNormal = """
                {
                  "folioOrigen": "APLA-QR6-N1,APLA-QR6-N2",
                  "folioDeclaracionPpla": "PPLA-QR6-NORM",
                  "fechaProduccion": "2026-11-11",
                  "hora": "11:00",
                  "nombrePlanta": "Planta Rendimiento",
                  "codigoSernapesca": "PL-55",
                  "usuario": { "id": %d },
                  "materiaPrimaEspecie": { "id": %d },
                  "humedadEstado": { "id": %d },
                  "productoResultante": { "id": %d },
                  "cantidadMateriaPrima": 12000,
                  "cantidadProducto": 2800,
                  "declaracionesSeleccionadas": "PLANTA_ABASTECIMIENTO:%d,PLANTA_ABASTECIMIENTO:%d"
                }
                """.formatted(planta.getId(), pelillo.getId(), humSeco.getId(), prodSeco.getId(), loteN1.getId(), loteN2.getId());

        MvcResult resNorm = mockMvc.perform(post("/declaracionplantaproduccion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyNormal))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.capturaOrigenKg").value(10000.0))
                .andExpect(jsonPath("$.capturaOrigenCompleta").value(true))
                .andExpect(jsonPath("$.rendimientoPct").value(23.33))
                .andExpect(jsonPath("$.rendimientoFinalPct").value(28.0))
                .andExpect(jsonPath("$.rendimientoMaxTeoricoPct").value(27.93))
                .andExpect(jsonPath("$.rendimientoFuente").value("FACTORES_ORIGEN"))
                .andExpect(jsonPath("$.bloqueada").value(false))
                .andReturn();
        Long prodNormalId = objectMapper.readTree(resNorm.getResponse().getContentAsString()).get("id").asLong();

        // Verificar campos en DTO de árbol CadenaCustodiaService.resolver("PLANTA_PRODUCCION", id)
        CadenaCustodiaService.Cadena arbol = cadenaCustodiaService.resolver("PLANTA_PRODUCCION", prodNormalId);
        TrazabilidadNodoDTO nodoProd = arbol.getNodos().stream()
                .filter(n -> ("PLANTA_PRODUCCION:" + prodNormalId).equals(n.getIdUnico()))
                .findFirst().orElseThrow();
        assertEquals("Planta Producción", nodoProd.getTipoNodo());
        assertEquals(new BigDecimal("10000.00"), nodoProd.getCapturaOrigenKg());
        assertEquals(Boolean.TRUE, nodoProd.getCapturaOrigenCompleta());
        assertEquals(new BigDecimal("28.00"), nodoProd.getRendimientoFinalPct());

        // Cadena anómala: origen 10000 kg, romana planta 12000 kg, producto 3000 kg seco -> 25.00% proceso pero 30.00% final (> 29.33%)
        DeclaracionRecolectorModel recA = crearRecolectorQr6(dia, new BigDecimal("6000.00"), "OR-REC-A");
        DeclaracionArmadorModel armA = crearArmadorQr6(dia, 4000.0, "OR-ARM-A");
        DeclaracionComercializadorModel comA = crearComercializadorQr6(dia, "RECOLECTOR:" + recA.getId(), new BigDecimal("6000.00"), "COM-A");
        DeclaracionPlantaAbastecimientoModel loteA1 = abastRepo.saveAndFlush(DeclaracionPlantaAbastecimientoModel.builder()
                .folioOrigen("OR-A1").folioDeclaracionAPla("APLA-QR6-A1")
                .fechaIngresoPlanta(dia).fechaTraslado(dia).hora("10:30")
                .nombrePlanta("Planta Rendimiento").codigoSernapesca("PL-55")
                .usuario(planta).usuarioDestinatario(planta)
                .especie(pelillo).humedadEstado(humHumedo).humedadEstadoRecepcion(humHumedo)
                .cantidad(new BigDecimal("6000")).pesoRomanaKg(new BigDecimal("6000"))
                .declaracionesSeleccionadas("RECOLECTOR:" + recA.getId() + ",ARMADOR:" + armA.getId())
                .build());
        DeclaracionPlantaAbastecimientoModel loteA2 = abastRepo.saveAndFlush(DeclaracionPlantaAbastecimientoModel.builder()
                .folioOrigen("OR-A2").folioDeclaracionAPla("APLA-QR6-A2")
                .fechaIngresoPlanta(dia).fechaTraslado(dia).hora("10:45")
                .nombrePlanta("Planta Rendimiento").codigoSernapesca("PL-55")
                .usuario(planta).usuarioDestinatario(planta)
                .especie(pelillo).humedadEstado(humHumedo).humedadEstadoRecepcion(humHumedo)
                .cantidad(new BigDecimal("6000")).pesoRomanaKg(new BigDecimal("6000"))
                .declaracionesSeleccionadas("COMERCIALIZADOR:" + comA.getId())
                .build());

        String bodyAnom = bodyNormal
                .replace("APLA-QR6-N1,APLA-QR6-N2", "APLA-QR6-A1,APLA-QR6-A2")
                .replace("PPLA-QR6-NORM", "PPLA-QR6-ANOM")
                .replace("\"cantidadProducto\": 2800", "\"cantidadProducto\": 3000")
                .replace("PLANTA_ABASTECIMIENTO:" + loteN1.getId() + ",PLANTA_ABASTECIMIENTO:" + loteN2.getId(),
                        "PLANTA_ABASTECIMIENTO:" + loteA1.getId() + ",PLANTA_ABASTECIMIENTO:" + loteA2.getId());

        MvcResult resAnom = mockMvc.perform(post("/declaracionplantaproduccion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyAnom))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.rendimientoPct").value(25.0))
                .andExpect(jsonPath("$.rendimientoFinalPct").value(30.0))
                .andExpect(jsonPath("$.rendimientoFuente").value("FACTORES_ORIGEN"))
                .andExpect(jsonPath("$.bloqueada").value(true))
                .andExpect(jsonPath("$.edicionBloqueada").value(true))
                .andReturn();
        Long prodAnomId = objectMapper.readTree(resAnom.getResponse().getContentAsString()).get("id").asLong();

        List<DeclaracionMarcaModel> marcas = marcaRepo.findByDeclaracionTipoAndDeclaracionId("PLANTA_PRODUCCION", prodAnomId);
        assertEquals(1, marcas.size());
        assertEquals("RENDIMIENTO_ANOMALO", marcas.get(0).getMarca());
        assertTrue(marcas.get(0).getDetalle().contains("captura biológica de origen"));

        String destAnom = """
                {
                  "folioOrigen": "PPLA-QR6-ANOM",
                  "folioDeclaracionDestino": "DPLA-QR6-ANOM",
                  "fechaDeclaracionDestino": "2026-11-12",
                  "fechaTrasladoDestino": "2026-11-12",
                  "hora": "13:00",
                  "nombrePlanta": "Planta Rendimiento",
                  "codigoSernapesca": "PL-55",
                  "usuario": { "id": %d },
                  "especie": { "id": %d },
                  "producto": { "id": %d },
                  "cantidad": 3000,
                  "nombreDestino": "Exportadora Sur",
                  "rutDestino": "77.777.777-7",
                  "documentoTributarioTipo": "GUIA",
                  "documentoTributarioNumero": "G-QR6-1",
                  "declaracionesSeleccionadas": "PLANTA_PRODUCCION:%d"
                }
                """.formatted(planta.getId(), pelillo.getId(), prodSeco.getId(), prodAnomId);

        mockMvc.perform(post("/api/declaracion-planta-destino")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(destAnom))
                .andExpect(status().isConflict());

        // Reporte consolidado refleja base CAPTURA_ORIGEN y ambas producciones
        mockMvc.perform(get("/api/reportes/rendimiento-planta")
                        .with(user("fisca").roles("FISCALIZADOR"))
                        .param("startDate", "2026-11-01")
                        .param("endDate", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumen.rendimientoBase").value("CAPTURA_ORIGEN"))
                .andExpect(jsonPath("$.resumen.total").value(2))
                .andExpect(jsonPath("$.resumen.normales").value(1))
                .andExpect(jsonPath("$.resumen.anomalas").value(1))
                .andExpect(jsonPath("$.resumen.cadenasIncompletas").value(0))
                .andExpect(jsonPath("$.porPlantaFormato[0].capturaOrigenKg").value(20000.0))
                .andExpect(jsonPath("$.porPlantaFormato[0].rendimientoFinalPromedioPct").value(29.0))
                .andExpect(jsonPath("$.detalle[?(@.id == " + prodNormalId + ")].evaluadoSobreOrigen").value(hasItem(true)))
                .andExpect(jsonPath("$.detalle[?(@.id == " + prodAnomId + ")].anomalo").value(hasItem(true)));
    }

    @Test
    @DisplayName("QR.6 E2E: rendimiento_rango queda aislado por base (MASA_RECIBIDA vs CAPTURA_ORIGEN)")
    void aislamientoRangoPorBase_masaRecibidaVsCapturaOrigen_qr6() throws Exception {
        configurarFactorSeco35800();
        prepararActoresOrigenQr6();
        Date dia = Date.valueOf("2026-11-10");

        // Rango específico para MASA_RECIBIDA (40%) NO debe interferir cuando la base activa es CAPTURA_ORIGEN
        rangoRepo.saveAndFlush(RendimientoRangoModel.builder()
                .base("MASA_RECIBIDA").especie(pelillo).humedadEntrada(humHumedo).producto(prodSeco)
                .minPct(new BigDecimal("10.00")).maxPct(new BigDecimal("40.00"))
                .activo(true).vigenciaInicio(Date.valueOf("2026-01-01")).build());

        DeclaracionRecolectorModel rec = crearRecolectorQr6(dia, new BigDecimal("10000.00"), "OR-RANGO-1");
        DeclaracionPlantaAbastecimientoModel lote1 = abastRepo.saveAndFlush(DeclaracionPlantaAbastecimientoModel.builder()
                .folioOrigen("OR-R1").folioDeclaracionAPla("APLA-RANGO-1")
                .fechaIngresoPlanta(dia).fechaTraslado(dia).hora("09:00")
                .nombrePlanta("Planta Rendimiento").codigoSernapesca("PL-55")
                .usuario(planta).usuarioDestinatario(planta)
                .especie(pelillo).humedadEstado(humHumedo).humedadEstadoRecepcion(humHumedo)
                .cantidad(new BigDecimal("10000")).pesoRomanaKg(new BigDecimal("10000"))
                .declaracionesSeleccionadas("RECOLECTOR:" + rec.getId()).build());
        DeclaracionPlantaAbastecimientoModel lote2 = abastRepo.saveAndFlush(DeclaracionPlantaAbastecimientoModel.builder()
                .folioOrigen("OR-R1").folioDeclaracionAPla("APLA-RANGO-2")
                .fechaIngresoPlanta(dia).fechaTraslado(dia).hora("09:05")
                .nombrePlanta("Planta Rendimiento").codigoSernapesca("PL-55")
                .usuario(planta).usuarioDestinatario(planta)
                .especie(pelillo).humedadEstado(humHumedo).humedadEstadoRecepcion(humHumedo)
                .cantidad(new BigDecimal("10000")).pesoRomanaKg(new BigDecimal("10000"))
                .declaracionesSeleccionadas("RECOLECTOR:" + rec.getId()).build());
        DeclaracionPlantaAbastecimientoModel lote3 = abastRepo.saveAndFlush(DeclaracionPlantaAbastecimientoModel.builder()
                .folioOrigen("OR-R1").folioDeclaracionAPla("APLA-RANGO-3")
                .fechaIngresoPlanta(dia).fechaTraslado(dia).hora("09:10")
                .nombrePlanta("Planta Rendimiento").codigoSernapesca("PL-55")
                .usuario(planta).usuarioDestinatario(planta)
                .especie(pelillo).humedadEstado(humHumedo).humedadEstadoRecepcion(humHumedo)
                .cantidad(new BigDecimal("10000")).pesoRomanaKg(new BigDecimal("10000"))
                .declaracionesSeleccionadas("RECOLECTOR:" + rec.getId()).build());

        String payloadTemplate = """
                {
                  "folioOrigen": "%s",
                  "folioDeclaracionPpla": "%s",
                  "fechaProduccion": "2026-11-11",
                  "hora": "10:00",
                  "nombrePlanta": "Planta Rendimiento",
                  "codigoSernapesca": "PL-55",
                  "usuario": { "id": %d },
                  "materiaPrimaEspecie": { "id": %d },
                  "humedadEstado": { "id": %d },
                  "productoResultante": { "id": %d },
                  "cantidadMateriaPrima": 10000,
                  "cantidadProducto": 3000,
                  "declaracionesSeleccionadas": "PLANTA_ABASTECIMIENTO:%d"
                }
                """;

        // 1) Con base CAPTURA_ORIGEN y sin rango CAPTURA_ORIGEN -> cae a FACTORES_ORIGEN (27.93%) => 30% ANÓMALO
        configService.updateConfig("rendimiento_base", "CAPTURA_ORIGEN");
        mockMvc.perform(post("/declaracionplantaproduccion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadTemplate.formatted("APLA-RANGO-1", "PPLA-RNG-1", planta.getId(), pelillo.getId(), humSeco.getId(), prodSeco.getId(), lote1.getId())))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.rendimientoFuente").value("FACTORES_ORIGEN"))
                .andExpect(jsonPath("$.bloqueada").value(true));

        // 2) Cambiando base a MASA_RECIBIDA -> aplica tabla MASA_RECIBIDA (40%) => 30% NORMAL (TABLA)
        configService.updateConfig("rendimiento_base", "MASA_RECIBIDA");
        mockMvc.perform(post("/declaracionplantaproduccion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadTemplate.formatted("APLA-RANGO-2", "PPLA-RNG-2", planta.getId(), pelillo.getId(), humSeco.getId(), prodSeco.getId(), lote2.getId())))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.rendimientoFuente").value("TABLA"))
                .andExpect(jsonPath("$.rendimientoMaxTeoricoPct").value(40.0))
                .andExpect(jsonPath("$.bloqueada").value(false));

        // 3) Creando rango específico CAPTURA_ORIGEN (35%) y volviendo a base CAPTURA_ORIGEN => 30% NORMAL (TABLA_ORIGEN)
        rangoRepo.saveAndFlush(RendimientoRangoModel.builder()
                .base("CAPTURA_ORIGEN").especie(pelillo).humedadEntrada(humHumedo).producto(prodSeco)
                .minPct(new BigDecimal("20.00")).maxPct(new BigDecimal("35.00"))
                .activo(true).vigenciaInicio(Date.valueOf("2026-01-01")).build());
        configService.updateConfig("rendimiento_base", "CAPTURA_ORIGEN");
        mockMvc.perform(post("/declaracionplantaproduccion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadTemplate.formatted("APLA-RANGO-3", "PPLA-RNG-3", planta.getId(), pelillo.getId(), humSeco.getId(), prodSeco.getId(), lote3.getId())))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.rendimientoFuente").value("TABLA_ORIGEN"))
                .andExpect(jsonPath("$.rendimientoMaxTeoricoPct").value(35.0))
                .andExpect(jsonPath("$.bloqueada").value(false));
    }

    @Test
    @DisplayName("QR.6 E2E: endpoint administrativo /api/admin/rendimiento/recalcular-final backfillea sin crear marcas ni bloquear destinos")
    void recalculoHistoricoAdminEndpoint_qr6() throws Exception {
        configurarFactorSeco35800();
        prepararActoresOrigenQr6();
        Date dia = Date.valueOf("2026-11-10");

        DeclaracionRecolectorModel rec = crearRecolectorQr6(dia, new BigDecimal("10000.00"), "OR-HIST-1");
        DeclaracionPlantaAbastecimientoModel loteCompleto = abastRepo.saveAndFlush(DeclaracionPlantaAbastecimientoModel.builder()
                .folioOrigen("OR-H1").folioDeclaracionAPla("APLA-HIST-1")
                .fechaIngresoPlanta(dia).fechaTraslado(dia).hora("09:00")
                .nombrePlanta("Planta Rendimiento").codigoSernapesca("PL-55")
                .usuario(planta).usuarioDestinatario(planta)
                .especie(pelillo).humedadEstado(humHumedo).humedadEstadoRecepcion(humHumedo)
                .cantidad(new BigDecimal("12000")).pesoRomanaKg(new BigDecimal("12000"))
                .declaracionesSeleccionadas("RECOLECTOR:" + rec.getId()).build());
        DeclaracionPlantaAbastecimientoModel loteIncompleto = abastRepo.saveAndFlush(DeclaracionPlantaAbastecimientoModel.builder()
                .folioOrigen("OR-H2").folioDeclaracionAPla("APLA-HIST-2")
                .fechaIngresoPlanta(dia).fechaTraslado(dia).hora("09:30")
                .nombrePlanta("Planta Rendimiento").codigoSernapesca("PL-55")
                .usuario(planta).usuarioDestinatario(planta)
                .especie(pelillo).humedadEstado(humHumedo).humedadEstadoRecepcion(humHumedo)
                .cantidad(new BigDecimal("10000")).pesoRomanaKg(new BigDecimal("10000"))
                .build());

        // Registros históricos previos a QR (sin captura_origen ni rendimiento_final)
        DeclaracionPlantaProduccionModel histAnomEnOrigen = prodRepo.saveAndFlush(DeclaracionPlantaProduccionModel.builder()
                .folioOrigen("APLA-HIST-1").folioDeclaracionPpla("PPLA-HIST-1")
                .fechaProduccion(Date.valueOf("2026-11-11")).hora("11:00")
                .nombrePlanta("Planta Rendimiento").codigoSernapesca("PL-55")
                .usuario(planta).materiaPrimaEspecie(pelillo).humedadEstado(humSeco).productoResultante(prodSeco)
                .cantidadMateriaPrima(new BigDecimal("12000.00")).cantidadProducto(new BigDecimal("3000.00"))
                .rendimientoPct(new BigDecimal("25.00")).rendimientoMaxTeoricoPct(new BigDecimal("27.93"))
                .rendimientoFuente("FACTORES").estado("ENVIADA").bloqueada(false).edicionBloqueada(false)
                .declaracionesSeleccionadas("PLANTA_ABASTECIMIENTO:" + loteCompleto.getId())
                .build());

        DeclaracionPlantaProduccionModel histIncompleto = prodRepo.saveAndFlush(DeclaracionPlantaProduccionModel.builder()
                .folioOrigen("APLA-HIST-2").folioDeclaracionPpla("PPLA-HIST-2")
                .fechaProduccion(Date.valueOf("2026-11-11")).hora("12:00")
                .nombrePlanta("Planta Rendimiento").codigoSernapesca("PL-55")
                .usuario(planta).materiaPrimaEspecie(pelillo).humedadEstado(humSeco).productoResultante(prodSeco)
                .cantidadMateriaPrima(new BigDecimal("10000.00")).cantidadProducto(new BigDecimal("2500.00"))
                .rendimientoPct(new BigDecimal("25.00")).rendimientoMaxTeoricoPct(new BigDecimal("27.93"))
                .rendimientoFuente("FACTORES").estado("ENVIADA").bloqueada(false).edicionBloqueada(false)
                .declaracionesSeleccionadas("PLANTA_ABASTECIMIENTO:" + loteIncompleto.getId())
                .build());

        // FISCALIZADOR no autorizado -> 403 Forbidden
        mockMvc.perform(post("/api/admin/rendimiento/recalcular-final")
                        .with(user("fisca").roles("FISCALIZADOR"))
                        .param("aplicar", "false"))
                .andExpect(status().isForbidden());

        // ADMIN en modo simulación (aplicar=false) -> informa pero no escribe en BD
        mockMvc.perform(post("/api/admin/rendimiento/recalcular-final")
                        .with(user("admin").roles("ADMIN"))
                        .param("aplicar", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aplicado").value(false))
                .andExpect(jsonPath("$.totalEvaluadas").value(2))
                .andExpect(jsonPath("$.actualizadas").value(0))
                .andExpect(jsonPath("$.cadenasCompletas").value(1))
                .andExpect(jsonPath("$.cadenasIncompletas").value(1))
                .andExpect(jsonPath("$.habrianSidoAnomalasCount").value(1))
                .andExpect(jsonPath("$.habrianSidoAnomalas[0].id").value(histAnomEnOrigen.getId()))
                .andExpect(jsonPath("$.marcasCreadas").value(0))
                .andExpect(jsonPath("$.destinosBloqueados").value(0));

        assertNull(prodRepo.findById(histAnomEnOrigen.getId()).orElseThrow().getCapturaOrigenKg());

        // ADMIN aplica backfill (aplicar=true) -> puebla solo las 3 columnas nuevas y preserva rendimientoPct/rendimientoFuente
        mockMvc.perform(post("/api/admin/rendimiento/recalcular-final")
                        .with(user("admin").roles("ADMIN"))
                        .param("aplicar", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aplicado").value(true))
                .andExpect(jsonPath("$.actualizadas").value(2))
                .andExpect(jsonPath("$.habrianSidoAnomalasCount").value(1))
                .andExpect(jsonPath("$.marcasCreadas").value(0))
                .andExpect(jsonPath("$.destinosBloqueados").value(0));

        DeclaracionPlantaProduccionModel recargadaAnom = prodRepo.findById(histAnomEnOrigen.getId()).orElseThrow();
        assertEquals(new BigDecimal("10000.00"), recargadaAnom.getCapturaOrigenKg());
        assertEquals(Boolean.TRUE, recargadaAnom.getCapturaOrigenCompleta());
        assertEquals(new BigDecimal("30.00"), recargadaAnom.getRendimientoFinalPct());
        assertEquals(new BigDecimal("25.00"), recargadaAnom.getRendimientoPct());
        assertEquals("FACTORES", recargadaAnom.getRendimientoFuente());
        assertFalse(Boolean.TRUE.equals(recargadaAnom.getBloqueada()));

        DeclaracionPlantaProduccionModel recargadaInc = prodRepo.findById(histIncompleto.getId()).orElseThrow();
        assertEquals(Boolean.FALSE, recargadaInc.getCapturaOrigenCompleta());

        // 0 marcas creadas y destino sobre producción histórica anómala en origen NO queda bloqueado (2xx)
        assertTrue(marcaRepo.findByDeclaracionTipoAndDeclaracionId("PLANTA_PRODUCCION", histAnomEnOrigen.getId()).isEmpty());

        String destHist = """
                {
                  "folioOrigen": "PPLA-HIST-1",
                  "folioDeclaracionDestino": "DPLA-HIST-1",
                  "fechaDeclaracionDestino": "2026-11-12",
                  "fechaTrasladoDestino": "2026-11-12",
                  "hora": "15:00",
                  "nombrePlanta": "Planta Rendimiento",
                  "codigoSernapesca": "PL-55",
                  "usuario": { "id": %d },
                  "especie": { "id": %d },
                  "producto": { "id": %d },
                  "cantidad": 3000,
                  "nombreDestino": "Exportadora Sur",
                  "rutDestino": "77.777.777-7",
                  "documentoTributarioTipo": "GUIA",
                  "documentoTributarioNumero": "G-HIST-1",
                  "declaracionesSeleccionadas": "PLANTA_PRODUCCION:%d"
                }
                """.formatted(planta.getId(), pelillo.getId(), prodSeco.getId(), histAnomEnOrigen.getId());

        mockMvc.perform(post("/api/declaracion-planta-destino")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(destHist))
                .andExpect(status().is2xxSuccessful());
    }
}
