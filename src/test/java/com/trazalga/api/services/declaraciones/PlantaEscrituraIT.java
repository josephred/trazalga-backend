package com.trazalga.api.services.declaraciones;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Date;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import com.trazalga.api.IntegracionBase;
import com.trazalga.api.exceptions.ValidacionPlantaException;
import com.trazalga.api.models.DeclaracionMarcaModel;
import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import com.trazalga.api.models.DeclaracionPlantaDestinoModel;
import com.trazalga.api.models.DeclaracionPlantaProduccionModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.models.ProductoModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IDeclaracionMarcaRepository;
import com.trazalga.api.repositories.IDeclaracionPlantaAbastecimientoRepository;
import com.trazalga.api.repositories.IDeclaracionPlantaDestinoRepository;
import com.trazalga.api.repositories.IDeclaracionPlantaProduccionRepository;
import com.trazalga.api.repositories.IEspecieRepository;
import com.trazalga.api.repositories.IHumedadEstadoRepository;
import com.trazalga.api.repositories.IProductoRepository;
import com.trazalga.api.repositories.IUsuarioRepository;
import com.trazalga.api.services.BloqueoCargaService;
import com.trazalga.api.services.ConfiguracionGeneralService;
import com.trazalga.api.services.DeclaracionPlantaDestinoService;
import com.trazalga.api.services.DeclaracionPlantaProduccionService;

/**
 * Refinamiento 06-11, NP.0 (integración, H2 perfil "it"): las declaraciones de
 * planta sólo las modifica o
 * elimina su dueño, y una producción o un destino sólo consumen lo que es de la
 * misma planta, está libre
 * y no está retenido.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlantaEscrituraIT extends IntegracionBase {

        private static final String RUT_PLANTA_A = "41.111.111-1";
        private static final String RUT_PLANTA_B = "42.222.222-2";

        @Autowired
        private MockMvc mockMvc;
        @Autowired
        private DeclaracionPlantaProduccionService produccionService;
        @Autowired
        private DeclaracionPlantaDestinoService destinoService;
        @Autowired
        private ConfiguracionGeneralService configuracionGeneralService;
        @Autowired
        private BloqueoCargaService bloqueoCargaService;
        @Autowired
        private JdbcTemplate jdbcTemplate;

        @Autowired
        private IDeclaracionPlantaAbastecimientoRepository abastecimientoRepository;
        @Autowired
        private IDeclaracionPlantaProduccionRepository produccionRepository;
        @Autowired
        private IDeclaracionPlantaDestinoRepository destinoRepository;
        @Autowired
        private IDeclaracionMarcaRepository marcaRepository;
        @Autowired
        private IEspecieRepository especieRepository;
        @Autowired
        private IHumedadEstadoRepository humedadEstadoRepository;
        @Autowired
        private IProductoRepository productoRepository;
        @Autowired
        private IUsuarioRepository usuarioRepository;

        private EspecieModel especie;
        private HumedadEstadoModel humedad;
        private ProductoModel producto;
        private UsuarioModel plantaA;
        private UsuarioModel plantaB;
        private Date fecha;

        @BeforeEach
        void setUp() {
                jdbcTemplate.execute(
                                "ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN IF NOT EXISTS folio_declaracion_a_pla VARCHAR(50)");
                jdbcTemplate.execute(
                                "ALTER TABLE declaracion_planta_produccion ADD COLUMN IF NOT EXISTS folio_declaracion_p_pla VARCHAR(50)");
                fecha = Date.valueOf("2026-11-06");
                configuracionGeneralService.updateConfig("declaracion_escritura_exige_sesion", "false");
                if (especie != null) {
                        return;
                }
                especie = especieRepository.saveAndFlush(EspecieModel.builder().nombre("Huiro negro NP0").build());
                humedad = humedadEstadoRepository.saveAndFlush(HumedadEstadoModel.builder().nombre("Seco NP0").build());
                producto = productoRepository.saveAndFlush(ProductoModel.builder().nombre("Alga picada NP0").build());
                plantaA = usuarioRepository.saveAndFlush(UsuarioModel.builder()
                                .rut(RUT_PLANTA_A).nombres("Planta A").apellidop("NP0").estado("ACTIVO").build());
                plantaB = usuarioRepository.saveAndFlush(UsuarioModel.builder()
                                .rut(RUT_PLANTA_B).nombres("Planta B").apellidop("NP0").estado("ACTIVO").build());
        }

        @AfterEach
        void tearDown() {
                marcaRepository.deleteAll();
                destinoRepository.deleteAll();
                produccionRepository.deleteAll();
                abastecimientoRepository.deleteAll();
                configuracionGeneralService.updateConfig("declaracion_escritura_exige_sesion", "false");
                configuracionGeneralService.updateConfig("destino_exige_producciones", "true");
                configuracionGeneralService.updateConfig("destino_tolerancia_pct", "1");
                configuracionGeneralService.updateConfig("destino_exige_mismo_formato", "true");
                configuracionGeneralService.updateConfig("produccion_exige_lotes", "true");
                configuracionGeneralService.updateConfig("produccion_exige_formato_oficial", "false");
                configuracionGeneralService.updateConfig("marcas_bloqueantes_produccion", "RENDIMIENTO_ANOMALO");
        }

        // ------------------------------------------------------------------ selección
        // de lotes

        @Test
        @DisplayName("Producción con un lote de otra planta: 409 con el folio, y el lote sigue libre")
        void produccion_loteDeOtraPlanta_409() {
                DeclaracionPlantaAbastecimientoModel ajeno = abastecimiento(plantaB, "DAPLA-NP0-AJENO");

                ResponseStatusException e = assertThrows(ResponseStatusException.class,
                                () -> produccionService
                                                .save(produccion(plantaA, "PLANTA_ABASTECIMIENTO:" + ajeno.getId())));

                assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
                assertTrue(e.getReason().contains("DAPLA-NP0-AJENO"), e.getReason());
                assertNull(abastecimientoRepository.findById(ajeno.getId()).orElseThrow().getDeclaracionDestinatario());
                assertEquals(0, produccionRepository.count());
        }

        @Test
        @DisplayName("Producción con un lote propio: se guarda y el lote queda consumido; usarlo de nuevo en otra producción da 409")
        void produccion_lotePropio_yDobleConsumo() {
                DeclaracionPlantaAbastecimientoModel lote = abastecimiento(plantaA, "DAPLA-NP0-PROPIO");
                String sel = "PLANTA_ABASTECIMIENTO:" + lote.getId();

                DeclaracionPlantaProduccionModel p = produccionService.save(produccion(plantaA, sel));
                assertEquals(p.getId(), abastecimientoRepository.findById(lote.getId()).orElseThrow()
                                .getDeclaracionDestinatario());

                ResponseStatusException e = assertThrows(ResponseStatusException.class,
                                () -> produccionService.save(produccion(plantaA, sel)));
                assertEquals(HttpStatus.CONFLICT, e.getStatusCode());

                // Modificar la misma producción conservando su lote sigue siendo válido
                DeclaracionPlantaProduccionModel cambio = produccion(plantaA, sel);
                cambio.setCantidadProducto(new BigDecimal("450.00"));
                assertDoesNotThrow(() -> produccionService.updateById(cambio, p.getId()));
        }

        @Test
        @DisplayName("Producción con un lote retenido por una marca bloqueante: 409")
        void produccion_loteRetenido_409() {
                String marcaBloqueante = bloqueoCargaService.getMarcasBloqueantes("PLANTA_ABASTECIMIENTO").stream()
                                .findFirst().orElse(null);
                org.junit.jupiter.api.Assumptions.assumeTrue(marcaBloqueante != null,
                                "sin marcas bloqueantes configuradas para planta");
                DeclaracionPlantaAbastecimientoModel lote = abastecimiento(plantaA, "DAPLA-NP0-RETENIDO");
                marcaRepository.saveAndFlush(DeclaracionMarcaModel.builder()
                                .declaracionTipo("PLANTA_ABASTECIMIENTO").declaracionId(lote.getId())
                                .marca(marcaBloqueante).detalle("Retención de prueba").build());

                ResponseStatusException e = assertThrows(ResponseStatusException.class,
                                () -> produccionService
                                                .save(produccion(plantaA, "PLANTA_ABASTECIMIENTO:" + lote.getId())));
                assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
                assertTrue(e.getReason().contains("retenido"), e.getReason());
        }

        @Test
        @DisplayName("Destino con una producción de otra planta: 409")
        void destino_produccionDeOtraPlanta_409() {
                DeclaracionPlantaAbastecimientoModel loteB = abastecimiento(plantaB, "DAPLA-NP0-OTRA-B");
                DeclaracionPlantaProduccionModel ajena = produccionService.save(
                                produccion(plantaB, "PLANTA_ABASTECIMIENTO:" + loteB.getId()));

                ResponseStatusException e = assertThrows(ResponseStatusException.class,
                                () -> destinoService.save(destino(plantaA, "PLANTA_PRODUCCION:" + ajena.getId())));
                assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
                assertNull(produccionRepository.findById(ajena.getId()).orElseThrow().getDeclaracionDestinatario());
        }

        // ------------------------------------------------------------------ sesión y
        // dueño

        @Test
        @DisplayName("PUT y DELETE de producción con la sesión de otra planta: 403; la producción no cambia")
        void produccion_sesionDeOtraPlanta_403() throws Exception {
                DeclaracionPlantaAbastecimientoModel loteA1 = abastecimiento(plantaA, "DAPLA-NP0-403-A1");
                DeclaracionPlantaProduccionModel p = produccionService.save(
                                produccion(plantaA, "PLANTA_ABASTECIMIENTO:" + loteA1.getId()));

                mockMvc.perform(put("/api/declaracion-planta-produccion/" + p.getId())
                                .with(user(RUT_PLANTA_B).roles("USER"))
                                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                                .andExpect(status().isForbidden());
                mockMvc.perform(delete("/api/declaracion-planta-produccion/" + p.getId())
                                .with(user(RUT_PLANTA_B).roles("USER")))
                                .andExpect(status().isForbidden());
                mockMvc.perform(delete("/declaracionplantaproduccion/" + p.getId())
                                .with(user(RUT_PLANTA_B).roles("USER")))
                                .andExpect(status().isForbidden());

                assertTrue(produccionRepository.existsById(p.getId()));
        }

        @Test
        @DisplayName("Abastecimiento y destino: la sesión de otra planta tampoco los modifica ni elimina (403)")
        void abastecimientoYDestino_sesionDeOtraPlanta_403() throws Exception {
                DeclaracionPlantaAbastecimientoModel a = abastecimiento(plantaA, "DAPLA-NP0-403");
                DeclaracionPlantaAbastecimientoModel loteProd = abastecimiento(plantaA, "DAPLA-NP0-403-P");
                DeclaracionPlantaProduccionModel prodPropia = produccionService.save(
                                produccion(plantaA, "PLANTA_ABASTECIMIENTO:" + loteProd.getId()));
                DeclaracionPlantaDestinoModel d = destinoService.save(
                                destino(plantaA, "PLANTA_PRODUCCION:" + prodPropia.getId()));

                mockMvc.perform(put("/api/declaracion-planta-abastecimiento/" + a.getId())
                                .with(user(RUT_PLANTA_B).roles("USER"))
                                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                                .andExpect(status().isForbidden());
                mockMvc.perform(delete("/declaracionplantaabastecimiento/" + a.getId())
                                .with(user(RUT_PLANTA_B).roles("USER")))
                                .andExpect(status().isForbidden());
                mockMvc.perform(put("/api/declaracion-planta-destino/" + d.getId())
                                .with(user(RUT_PLANTA_B).roles("USER"))
                                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                                .andExpect(status().isForbidden());
                mockMvc.perform(delete("/api/declaracion-planta-destino/" + d.getId())
                                .with(user(RUT_PLANTA_B).roles("USER")))
                                .andExpect(status().isForbidden());

                assertTrue(abastecimientoRepository.existsById(a.getId()));
                assertTrue(destinoRepository.existsById(d.getId()));
        }

        @Test
        @DisplayName("Sin sesión: con declaracion_escritura_exige_sesion encendido, 401; apagado, se permite como hasta ahora")
        void sinSesion_segunParametro() throws Exception {
                DeclaracionPlantaAbastecimientoModel loteA2 = abastecimiento(plantaA, "DAPLA-NP0-SES-A2");
                DeclaracionPlantaProduccionModel p = produccionService.save(
                                produccion(plantaA, "PLANTA_ABASTECIMIENTO:" + loteA2.getId()));

                configuracionGeneralService.updateConfig("declaracion_escritura_exige_sesion", "true");
                mockMvc.perform(delete("/api/declaracion-planta-produccion/" + p.getId()).with(anonymous()))
                                .andExpect(status().isUnauthorized());
                assertTrue(produccionRepository.existsById(p.getId()));

                configuracionGeneralService.updateConfig("declaracion_escritura_exige_sesion", "false");
                mockMvc.perform(delete("/api/declaracion-planta-produccion/" + p.getId()).with(anonymous()))
                                .andExpect(status().isOk());
                assertFalse(produccionRepository.existsById(p.getId()));
        }

        @Test
        @DisplayName("El dueño, con su sesión, elimina su producción")
        void dueno_elimina() throws Exception {
                DeclaracionPlantaAbastecimientoModel loteA3 = abastecimiento(plantaA, "DAPLA-NP0-DEL-A3");
                DeclaracionPlantaProduccionModel p = produccionService.save(
                                produccion(plantaA, "PLANTA_ABASTECIMIENTO:" + loteA3.getId()));

                mockMvc.perform(delete("/api/declaracion-planta-produccion/" + p.getId())
                                .with(user(RUT_PLANTA_A).roles("USER")))
                                .andExpect(status().isOk());
                assertFalse(produccionRepository.existsById(p.getId()));
        }

        @Test
        @DisplayName("NA.5/NA.7: POST /api/declaracion-planta-abastecimiento responde 422 por validación, 409 por lote duplicado y exige humedad recepción si parámetro=true")
        void abastecimiento_postHttp_422_409_y_humedadRecepcion() throws Exception {
                String baseFmt = """
                                {
                                  "usuario": {"id": %d},
                                  "folioOrigen": "%s",
                                  "codigoSernapesca": "SERN-TEST",
                                  "documentoTributarioTipo": "GUIA",
                                  "documentoTributarioNumero": "1001",
                                  "documentoTributarioFecha": "2026-11-06",
                                  "fechaIngresoPlanta": "2026-11-06",
                                  "fechaTraslado": "2026-11-06",
                                  "hora": "10:00:00",
                                  "nombrePlanta": "Planta NP0",
                                  "especie": {"id": %d},
                                  "humedadEstado": {"id": %d},
                                  %s
                                  "cantidad": 1000.00,
                                  "pesoRomanaKg": 1000.00,
                                  "voucherRomanaNumero": "VCH-IT-1",
                                  "lugarAcopio": "Galpón IT",
                                  %s
                                  "estado": "CONGELADA"
                                }
                                """;
                String humField = String.format("\"humedadEstadoRecepcion\": {\"id\": %d},", humedad.getId());

                // 1) Sin loteInterno con X-App-Version 2.6.0 -> 422 JSON legible
                String jsonSinLote = String.format(baseFmt, plantaA.getId(), "IT-FOL-1", especie.getId(),
                                humedad.getId(), humField, "");
                mockMvc.perform(post("/api/declaracion-planta-abastecimiento")
                                .with(user(RUT_PLANTA_A).roles("USER"))
                                .header("X-App-Version", "2.6.0")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonSinLote))
                                .andExpect(status().isUnprocessableEntity())
                                .andExpect(jsonPath("$.error").value("VALIDACION_PLANTA"))
                                .andExpect(jsonPath("$.message").exists());

                // 2) Alta válida -> 201 Created
                String jsonOk = String.format(baseFmt, plantaA.getId(), "IT-FOL-2", especie.getId(), humedad.getId(),
                                humField, "\"loteInterno\": \"LOT-HTTP-01\",");
                mockMvc.perform(post("/api/declaracion-planta-abastecimiento")
                                .with(user(RUT_PLANTA_A).roles("USER"))
                                .header("X-App-Version", "2.6.0")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonOk))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.loteInterno").value("LOT-HTTP-01"));

                // 3) Reintento con mismo lote (minúsculas) -> 409 Conflict
                String jsonDup = String.format(baseFmt, plantaA.getId(), "IT-FOL-3", especie.getId(), humedad.getId(),
                                humField, "\"loteInterno\": \"lot-http-01\",");
                mockMvc.perform(post("/api/declaracion-planta-abastecimiento")
                                .with(user(RUT_PLANTA_A).roles("USER"))
                                .header("X-App-Version", "2.6.0")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonDup))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.error").value("LOTE_INTERNO_DUPLICADO"))
                                .andExpect(jsonPath("$.message").exists());

                // 4) Con planta_exige_humedad_recepcion=true y sin humedadEstadoRecepcion ->
                // 422 (NA.5)
                try {
                        configuracionGeneralService.updateConfig("planta_exige_humedad_recepcion", "true");
                        String jsonSinHum = String.format(baseFmt, plantaA.getId(), "IT-FOL-4", especie.getId(),
                                        humedad.getId(), "", "\"loteInterno\": \"LOT-HTTP-02\",");
                        mockMvc.perform(post("/api/declaracion-planta-abastecimiento")
                                        .with(user(RUT_PLANTA_A).roles("USER"))
                                        .header("X-App-Version", "2.6.0")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(jsonSinHum))
                                        .andExpect(status().isUnprocessableEntity())
                                        .andExpect(jsonPath("$.error").value("VALIDACION_PLANTA"));
                } finally {
                        configuracionGeneralService.updateConfig("planta_exige_humedad_recepcion", "false");
                }
        }

        @Test
        @DisplayName("ZP.1: destino sin producciones responde 422 al crear y al modificar")
        void zp1_destinoSinProducciones_422_creacionYModificacion() throws Exception {
                String bodyVacio = """
                                {
                                  "usuario": {"id": %d},
                                  "fechaDeclaracionDestino": "2026-11-06",
                                  "hora": "15:00:00",
                                  "nombrePlanta": "Planta NP0",
                                  "especie": {"id": %d},
                                  "producto": {"id": %d},
                                  "cantidad": 100.00
                                }
                                """.formatted(plantaA.getId(), especie.getId(), producto.getId());

                mockMvc.perform(post("/api/declaracion-planta-destino")
                                .with(user(RUT_PLANTA_A).roles("USER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(bodyVacio))
                                .andExpect(status().isUnprocessableEntity())
                                .andExpect(jsonPath("$.error").value("VALIDACION_PLANTA"));

                DeclaracionPlantaAbastecimientoModel lote = abastecimiento(plantaA, "DAPLA-ZP1-1");
                DeclaracionPlantaProduccionModel prod = produccionService.save(
                                produccion(plantaA, "PLANTA_ABASTECIMIENTO:" + lote.getId()));
                DeclaracionPlantaDestinoModel dest = destinoService.save(
                                destino(plantaA, "PLANTA_PRODUCCION:" + prod.getId()));

                mockMvc.perform(put("/api/declaracion-planta-destino/" + dest.getId())
                                .with(user(RUT_PLANTA_A).roles("USER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(bodyVacio))
                                .andExpect(status().isUnprocessableEntity())
                                .andExpect(jsonPath("$.error").value("VALIDACION_PLANTA"));
        }

        @Test
        @DisplayName("ZP.2: destino valida cantidad > 0, tolerancia 1%, especie y formato coincidente en creación y modificación")
        void zp2_destinoCantidadEspecieYFormato_422() throws Exception {
                DeclaracionPlantaAbastecimientoModel lote = abastecimiento(plantaA, "DAPLA-ZP2-1");
                DeclaracionPlantaProduccionModel prod = produccionService.save(
                                produccion(plantaA, "PLANTA_ABASTECIMIENTO:" + lote.getId()));
                EspecieModel otraEspecie = especieRepository
                                .saveAndFlush(EspecieModel.builder().nombre("Otra ZP2").build());
                ProductoModel otroFormato = productoRepository
                                .saveAndFlush(ProductoModel.builder().nombre("Otro Formato ZP2").build());

                // Cantidad 0 -> 422
                DeclaracionPlantaDestinoModel dCero = destino(plantaA, "PLANTA_PRODUCCION:" + prod.getId());
                dCero.setCantidad(BigDecimal.ZERO);
                assertThrows(ValidacionPlantaException.class, () -> destinoService.save(dCero));

                // 405 kg sobre 400 kg (> 1% -> max 404.00) -> 422
                DeclaracionPlantaDestinoModel dExceso = destino(plantaA, "PLANTA_PRODUCCION:" + prod.getId());
                dExceso.setCantidad(new BigDecimal("405.00"));
                assertThrows(ValidacionPlantaException.class, () -> destinoService.save(dExceso));

                // Especie distinta -> 422
                DeclaracionPlantaDestinoModel dEsp = destino(plantaA, "PLANTA_PRODUCCION:" + prod.getId());
                dEsp.setEspecie(otraEspecie);
                assertThrows(ValidacionPlantaException.class, () -> destinoService.save(dEsp));

                // Formato distinto (destino_exige_mismo_formato=true) -> 422
                DeclaracionPlantaDestinoModel dFmt = destino(plantaA, "PLANTA_PRODUCCION:" + prod.getId());
                dFmt.setProducto(otroFormato);
                assertThrows(ValidacionPlantaException.class, () -> destinoService.save(dFmt));

                // 404.00 kg exacto en límite 1% -> 201 OK
                DeclaracionPlantaDestinoModel dOk = destino(plantaA, "PLANTA_PRODUCCION:" + prod.getId());
                dOk.setCantidad(new BigDecimal("404.00"));
                DeclaracionPlantaDestinoModel guardada = destinoService.save(dOk);
                assertNotNull(guardada.getId());

                // Modificación excediendo tolerancia -> 422 vía HTTP (no degradado a 400)
                String bodyExceso = """
                                {
                                  "usuario": {"id": %d},
                                  "fechaDeclaracionDestino": "2026-11-06",
                                  "hora": "15:00:00",
                                  "nombrePlanta": "Planta NP0",
                                  "especie": {"id": %d},
                                  "producto": {"id": %d},
                                  "cantidad": 405.00,
                                  "declaracionesSeleccionadas": "PLANTA_PRODUCCION:%d"
                                }
                                """.formatted(plantaA.getId(), especie.getId(), producto.getId(), prod.getId());
                mockMvc.perform(put("/api/declaracion-planta-destino/" + guardada.getId())
                                .with(user(RUT_PLANTA_A).roles("USER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(bodyExceso))
                                .andExpect(status().isUnprocessableEntity())
                                .andExpect(jsonPath("$.error").value("VALIDACION_PLANTA"));
        }

        @Test
        @DisplayName("ZP.3: producción sin lotes -> 422; producción antigua con marca PRODUCCION_SIN_LOTES (D57) -> 409 hasta liberar")
        void zp3_produccionSinLotes_y_bloqueoD57() {
                assertThrows(ValidacionPlantaException.class, () -> produccionService.save(produccion(plantaA, null)));

                DeclaracionPlantaProduccionModel antiguaSinLotes = produccionRepository.saveAndFlush(
                                produccion(plantaA, null).setFolioDeclaracionPpla("PPLA-ANT-SIN-LOTES"));

                configuracionGeneralService.updateConfig("marcas_bloqueantes_produccion",
                                "RENDIMIENTO_ANOMALO,PRODUCCION_SIN_LOTES");
                DeclaracionMarcaModel marca = marcaRepository.saveAndFlush(DeclaracionMarcaModel.builder()
                                .declaracionTipo("PLANTA_PRODUCCION").declaracionId(antiguaSinLotes.getId())
                                .marca("PRODUCCION_SIN_LOTES").detalle("Sin lotes (D57)").build());

                ResponseStatusException ex409 = assertThrows(ResponseStatusException.class,
                                () -> destinoService.save(
                                                destino(plantaA, "PLANTA_PRODUCCION:" + antiguaSinLotes.getId())));
                assertEquals(HttpStatus.CONFLICT, ex409.getStatusCode());

                // Liberada por fiscalizador -> permite despachar
                marca.setResuelta(true).setResolucionTipo("LIBERADA").setEstadoGestion("RESUELTA");
                marcaRepository.saveAndFlush(marca);
                DeclaracionPlantaDestinoModel despachada = destinoService.save(
                                destino(plantaA, "PLANTA_PRODUCCION:" + antiguaSinLotes.getId()));
                assertNotNull(despachada.getId());
        }

        @Test
        @DisplayName("ZP.4: producción valida 0 kg (422), lotes de dos especies con folios (422), formato incompatible (422) y asigna especie desde lotes")
        void zp4_lotesDosEspeciesFormatoYKilos() {
                EspecieModel especieB = especieRepository
                                .saveAndFlush(EspecieModel.builder().nombre("Pelillo ZP4").build());
                ProductoModel formatoB = productoRepository.saveAndFlush(ProductoModel.builder()
                                .nombre("Formato Pelillo ZP4").especie(especieB).activo(true).build());

                DeclaracionPlantaAbastecimientoModel lote1 = abastecimiento(plantaA, "DAPLA-ZP4-ESP1");
                DeclaracionPlantaAbastecimientoModel lote2EspB = abastecimientoRepository.saveAndFlush(
                                DeclaracionPlantaAbastecimientoModel.builder()
                                                .usuario(plantaA).usuarioDestinatario(plantaA)
                                                .folioDeclaracionAPla("DAPLA-ZP4-ESP2").nombrePlanta("Planta NP0")
                                                .fechaIngresoPlanta(fecha).hora("10:00:00")
                                                .especie(especieB).humedadEstado(humedad)
                                                .cantidad(new BigDecimal("1000.00"))
                                                .build());

                // 0 kg producto -> 422
                DeclaracionPlantaProduccionModel pCero = produccion(plantaA, "PLANTA_ABASTECIMIENTO:" + lote1.getId());
                pCero.setCantidadProducto(BigDecimal.ZERO);
                assertThrows(ValidacionPlantaException.class, () -> produccionService.save(pCero));

                // Dos especies distintas -> 422 mencionando los folios
                ValidacionPlantaException exDos = assertThrows(ValidacionPlantaException.class,
                                () -> produccionService.save(produccion(plantaA,
                                                "PLANTA_ABASTECIMIENTO:" + lote1.getId() + ",PLANTA_ABASTECIMIENTO:"
                                                                + lote2EspB.getId())));
                assertTrue(exDos.getMessage().contains("DAPLA-ZP4-ESP1"), exDos.getMessage());
                assertTrue(exDos.getMessage().contains("DAPLA-ZP4-ESP2"), exDos.getMessage());

                // Formato cuya especie no coincide (incluso con
                // produccion_exige_formato_oficial=false) -> 422
                configuracionGeneralService.updateConfig("produccion_exige_formato_oficial", "false");
                DeclaracionPlantaProduccionModel pFmtMal = produccion(plantaA,
                                "PLANTA_ABASTECIMIENTO:" + lote1.getId());
                pFmtMal.setProductoResultante(formatoB);
                assertThrows(ValidacionPlantaException.class, () -> produccionService.save(pFmtMal));

                // Cliente envía otra especie en materiaPrimaEspecie, pero el servidor asigna la
                // especie real de los lotes
                DeclaracionPlantaProduccionModel pAsignada = produccion(plantaA,
                                "PLANTA_ABASTECIMIENTO:" + lote1.getId());
                pAsignada.setMateriaPrimaEspecie(especieB);
                DeclaracionPlantaProduccionModel guardada = produccionService.save(pAsignada);
                assertEquals(especie.getId(), guardada.getMateriaPrimaEspecie().getId());
        }

        // ------------------------------------------------------------------ utilidades

        private DeclaracionPlantaAbastecimientoModel abastecimiento(UsuarioModel planta, String folio) {
                return abastecimientoRepository.saveAndFlush(DeclaracionPlantaAbastecimientoModel.builder()
                                .usuario(planta).usuarioDestinatario(planta)
                                .folioDeclaracionAPla(folio).nombrePlanta("Planta NP0")
                                .fechaIngresoPlanta(fecha).hora("10:00:00")
                                .especie(especie).humedadEstado(humedad).cantidad(new BigDecimal("1000.00"))
                                .build());
        }

        private DeclaracionPlantaProduccionModel produccion(UsuarioModel planta, String seleccion) {
                return DeclaracionPlantaProduccionModel.builder()
                                .usuario(planta).usuarioDestinatario(planta)
                                .fechaProduccion(fecha).hora("12:00:00").nombrePlanta("Planta NP0")
                                .materiaPrimaEspecie(especie).humedadEstado(humedad)
                                .cantidadMateriaPrima(new BigDecimal("1000.00"))
                                .productoResultante(producto).cantidadProducto(new BigDecimal("400.00"))
                                .declaracionesSeleccionadas(seleccion)
                                .build();
        }

        private DeclaracionPlantaDestinoModel destino(UsuarioModel planta, String seleccion) {
                return DeclaracionPlantaDestinoModel.builder()
                                .usuario(planta)
                                .fechaDeclaracionDestino(fecha).hora("15:00:00").nombrePlanta("Planta NP0")
                                .especie(especie).producto(producto).cantidad(new BigDecimal("400.00"))
                                .declaracionesSeleccionadas(seleccion)
                                .build();
        }
}
