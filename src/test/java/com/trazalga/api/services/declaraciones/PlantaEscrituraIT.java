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
 * Refinamiento 06-11, NP.0 (integración, H2 perfil "it"): las declaraciones de planta sólo las modifica o
 * elimina su dueño, y una producción o un destino sólo consumen lo que es de la misma planta, está libre
 * y no está retenido.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlantaEscrituraIT extends IntegracionBase {

    private static final String RUT_PLANTA_A = "41.111.111-1";
    private static final String RUT_PLANTA_B = "42.222.222-2";

    @Autowired private MockMvc mockMvc;
    @Autowired private DeclaracionPlantaProduccionService produccionService;
    @Autowired private DeclaracionPlantaDestinoService destinoService;
    @Autowired private ConfiguracionGeneralService configuracionGeneralService;
    @Autowired private BloqueoCargaService bloqueoCargaService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired private IDeclaracionPlantaAbastecimientoRepository abastecimientoRepository;
    @Autowired private IDeclaracionPlantaProduccionRepository produccionRepository;
    @Autowired private IDeclaracionPlantaDestinoRepository destinoRepository;
    @Autowired private IDeclaracionMarcaRepository marcaRepository;
    @Autowired private IEspecieRepository especieRepository;
    @Autowired private IHumedadEstadoRepository humedadEstadoRepository;
    @Autowired private IProductoRepository productoRepository;
    @Autowired private IUsuarioRepository usuarioRepository;

    private EspecieModel especie;
    private HumedadEstadoModel humedad;
    private ProductoModel producto;
    private UsuarioModel plantaA;
    private UsuarioModel plantaB;
    private Date fecha;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN IF NOT EXISTS folio_declaracion_a_pla VARCHAR(50)");
        jdbcTemplate.execute("ALTER TABLE declaracion_planta_produccion ADD COLUMN IF NOT EXISTS folio_declaracion_p_pla VARCHAR(50)");
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
    }

    // ------------------------------------------------------------------ selección de lotes

    @Test
    @DisplayName("Producción con un lote de otra planta: 409 con el folio, y el lote sigue libre")
    void produccion_loteDeOtraPlanta_409() {
        DeclaracionPlantaAbastecimientoModel ajeno = abastecimiento(plantaB, "DAPLA-NP0-AJENO");

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> produccionService.save(produccion(plantaA, "PLANTA_ABASTECIMIENTO:" + ajeno.getId())));

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
        assertEquals(p.getId(), abastecimientoRepository.findById(lote.getId()).orElseThrow().getDeclaracionDestinatario());

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
        String marcaBloqueante = bloqueoCargaService.getMarcasBloqueantes("PLANTA_ABASTECIMIENTO").stream().findFirst().orElse(null);
        org.junit.jupiter.api.Assumptions.assumeTrue(marcaBloqueante != null, "sin marcas bloqueantes configuradas para planta");
        DeclaracionPlantaAbastecimientoModel lote = abastecimiento(plantaA, "DAPLA-NP0-RETENIDO");
        marcaRepository.saveAndFlush(DeclaracionMarcaModel.builder()
                .declaracionTipo("PLANTA_ABASTECIMIENTO").declaracionId(lote.getId())
                .marca(marcaBloqueante).detalle("Retención de prueba").build());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> produccionService.save(produccion(plantaA, "PLANTA_ABASTECIMIENTO:" + lote.getId())));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        assertTrue(e.getReason().contains("retenido"), e.getReason());
    }

    @Test
    @DisplayName("Destino con una producción de otra planta: 409")
    void destino_produccionDeOtraPlanta_409() {
        DeclaracionPlantaProduccionModel ajena = produccionService.save(produccion(plantaB, null));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> destinoService.save(destino(plantaA, "PLANTA_PRODUCCION:" + ajena.getId())));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        assertNull(produccionRepository.findById(ajena.getId()).orElseThrow().getDeclaracionDestinatario());
    }

    // ------------------------------------------------------------------ sesión y dueño

    @Test
    @DisplayName("PUT y DELETE de producción con la sesión de otra planta: 403; la producción no cambia")
    void produccion_sesionDeOtraPlanta_403() throws Exception {
        DeclaracionPlantaProduccionModel p = produccionService.save(produccion(plantaA, null));

        mockMvc.perform(put("/api/declaracion-planta-produccion/" + p.getId()).with(user(RUT_PLANTA_B).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/declaracion-planta-produccion/" + p.getId()).with(user(RUT_PLANTA_B).roles("USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/declaracionplantaproduccion/" + p.getId()).with(user(RUT_PLANTA_B).roles("USER")))
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

        mockMvc.perform(put("/api/declaracion-planta-abastecimiento/" + a.getId()).with(user(RUT_PLANTA_B).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/declaracionplantaabastecimiento/" + a.getId()).with(user(RUT_PLANTA_B).roles("USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/declaracion-planta-destino/" + d.getId()).with(user(RUT_PLANTA_B).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/declaracion-planta-destino/" + d.getId()).with(user(RUT_PLANTA_B).roles("USER")))
                .andExpect(status().isForbidden());

        assertTrue(abastecimientoRepository.existsById(a.getId()));
        assertTrue(destinoRepository.existsById(d.getId()));
    }

    @Test
    @DisplayName("Sin sesión: con declaracion_escritura_exige_sesion encendido, 401; apagado, se permite como hasta ahora")
    void sinSesion_segunParametro() throws Exception {
        DeclaracionPlantaProduccionModel p = produccionService.save(produccion(plantaA, null));

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
        DeclaracionPlantaProduccionModel p = produccionService.save(produccion(plantaA, null));

        mockMvc.perform(delete("/api/declaracion-planta-produccion/" + p.getId()).with(user(RUT_PLANTA_A).roles("USER")))
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
        String jsonSinLote = String.format(baseFmt, plantaA.getId(), "IT-FOL-1", especie.getId(), humedad.getId(), humField, "");
        mockMvc.perform(post("/api/declaracion-planta-abastecimiento")
                        .with(user(RUT_PLANTA_A).roles("USER"))
                        .header("X-App-Version", "2.6.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonSinLote))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("VALIDACION_PLANTA"))
                .andExpect(jsonPath("$.message").exists());

        // 2) Alta válida -> 201 Created
        String jsonOk = String.format(baseFmt, plantaA.getId(), "IT-FOL-2", especie.getId(), humedad.getId(), humField, "\"loteInterno\": \"LOT-HTTP-01\",");
        mockMvc.perform(post("/api/declaracion-planta-abastecimiento")
                        .with(user(RUT_PLANTA_A).roles("USER"))
                        .header("X-App-Version", "2.6.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonOk))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loteInterno").value("LOT-HTTP-01"));

        // 3) Reintento con mismo lote (minúsculas) -> 409 Conflict
        String jsonDup = String.format(baseFmt, plantaA.getId(), "IT-FOL-3", especie.getId(), humedad.getId(), humField, "\"loteInterno\": \"lot-http-01\",");
        mockMvc.perform(post("/api/declaracion-planta-abastecimiento")
                        .with(user(RUT_PLANTA_A).roles("USER"))
                        .header("X-App-Version", "2.6.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonDup))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("LOTE_INTERNO_DUPLICADO"))
                .andExpect(jsonPath("$.message").exists());

        // 4) Con planta_exige_humedad_recepcion=true y sin humedadEstadoRecepcion -> 422 (NA.5)
        try {
            configuracionGeneralService.updateConfig("planta_exige_humedad_recepcion", "true");
            String jsonSinHum = String.format(baseFmt, plantaA.getId(), "IT-FOL-4", especie.getId(), humedad.getId(), "", "\"loteInterno\": \"LOT-HTTP-02\",");
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
