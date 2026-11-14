package com.trazalga.api.controllers;

import com.trazalga.api.IntegracionBase;
import com.trazalga.api.models.DeclaracionPlantaDestinoModel;
import com.trazalga.api.models.DeclaracionPlantaProduccionModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.models.ProductoModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IDeclaracionPlantaDestinoRepository;
import com.trazalga.api.repositories.IDeclaracionPlantaProduccionRepository;
import com.trazalga.api.repositories.IEspecieRepository;
import com.trazalga.api.repositories.IHumedadEstadoRepository;
import com.trazalga.api.repositories.IProductoRepository;
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

import java.math.BigDecimal;
import java.sql.Date;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductoFormatoIT extends IntegracionBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private IProductoRepository productoRepo;
    @Autowired private IHumedadEstadoRepository humedadRepo;
    @Autowired private IEspecieRepository especieRepo;
    @Autowired private IUsuarioRepository usuarioRepo;
    @Autowired private IDeclaracionPlantaProduccionRepository produccionRepo;
    @Autowired private IDeclaracionPlantaDestinoRepository destinoRepo;
    @Autowired private ConfiguracionGeneralService configService;

    private HumedadEstadoModel humSeco;
    private HumedadEstadoModel humSemiSeco;
    private EspecieModel especiePelillo;
    private UsuarioModel planta;
    private ProductoModel activoConCodigo;
    private ProductoModel inactivoConCodigo;
    private ProductoModel activoSinCodigo;

    @BeforeEach
    void setup() {
        jdbc.execute("ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN IF NOT EXISTS folio_declaracion_a_pla VARCHAR(50)");
        jdbc.execute("ALTER TABLE declaracion_planta_produccion ADD COLUMN IF NOT EXISTS folio_declaracion_p_pla VARCHAR(50)");

        destinoRepo.deleteAll();
        produccionRepo.deleteAll();
        productoRepo.deleteAll();

        humSeco = humedadRepo.findAll().stream().filter(h -> "Seco".equalsIgnoreCase(h.getNombre())).findFirst()
                .orElseGet(() -> humedadRepo.save(HumedadEstadoModel.builder().nombre("Seco").rangoInicio(1).rangoFin(25).build()));
        humSemiSeco = humedadRepo.findAll().stream().filter(h -> "Semi Seco".equalsIgnoreCase(h.getNombre())).findFirst()
                .orElseGet(() -> humedadRepo.save(HumedadEstadoModel.builder().nombre("Semi Seco").rangoInicio(26).rangoFin(50).build()));

        especiePelillo = especieRepo.findAll().stream().findFirst()
                .orElseGet(() -> especieRepo.save(EspecieModel.builder().nombre("PELILLO").activo(true).build()));

        planta = usuarioRepo.findByRut("44.444.444-4").orElseGet(() ->
                usuarioRepo.saveAndFlush(UsuarioModel.builder()
                        .rut("44.444.444-4")
                        .nombres("Planta Formatos")
                        .apellidop("NF1")
                        .estado("ACTIVO")
                        .build()));

        activoConCodigo = productoRepo.save(ProductoModel.builder()
                .codigo("SER-SECO-01")
                .nombre("Pelillo Seco Enfardado")
                .descripcion("Formato oficial")
                .humedadEstado(humSeco)
                .especie(especiePelillo)
                .activo(true)
                .fuente("LOCAL")
                .build());

        inactivoConCodigo = productoRepo.save(ProductoModel.builder()
                .codigo("SER-OLD-99")
                .nombre("Formato Histórico Inactivo")
                .humedadEstado(humSemiSeco)
                .activo(false)
                .fuente("LOCAL")
                .build());

        activoSinCodigo = productoRepo.save(ProductoModel.builder()
                .nombre("Alga Sin Codigo")
                .humedadEstado(humSeco)
                .activo(true)
                .fuente("LOCAL")
                .build());

        configService.updateConfig("produccion_exige_formato_oficial", "false");
        configService.updateConfig("declaracion_escritura_exige_sesion", "false");
    }

    @AfterEach
    void cleanup() {
        configService.updateConfig("produccion_exige_formato_oficial", "false");
    }

    @Test
    @DisplayName("NF.1: GET /producto filtra inactivos por defecto e incluye humedad y especie sin LazyInitializationException")
    void getProductos_filtraInactivos() throws Exception {
        mockMvc.perform(get("/producto"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].nombre", hasItems("Pelillo Seco Enfardado", "Alga Sin Codigo")))
                .andExpect(jsonPath("$[*].nombre", not(hasItem("Formato Histórico Inactivo"))))
                .andExpect(jsonPath("$[?(@.codigo=='SER-SECO-01')].humedadEstado.nombre", contains("Seco")));

        mockMvc.perform(get("/producto").param("incluirInactivos", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        // Con tabla vacía no hay lista de respaldo en memoria (QF.5 / F26)
        productoRepo.deleteAll();
        mockMvc.perform(get("/producto"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("NF.1/NF.3: /api/admin/productos exige ADMIN, reporta usos en Producción y Destino, y valida códigos únicos")
    void adminProductos_crudYUsos() throws Exception {
        DeclaracionPlantaProduccionModel prod = produccionRepo.save(DeclaracionPlantaProduccionModel.builder()
                .folioOrigen("F-1").folioDeclaracionPpla("P-PLA-FMT-1")
                .fechaProduccion(Date.valueOf("2026-11-10")).hora("10:00")
                .nombrePlanta("Planta Formatos").codigoSernapesca("PL-FMT")
                .usuario(planta).materiaPrimaEspecie(especiePelillo)
                .humedadEstado(humSeco).cantidadMateriaPrima(new BigDecimal("100"))
                .productoResultante(activoConCodigo).cantidadProducto(new BigDecimal("40"))
                .build());

        destinoRepo.save(DeclaracionPlantaDestinoModel.builder()
                .folioOrigen("P-PLA-FMT-1").folioDeclaracionDestino("D-PLA-FMT-1")
                .fechaDeclaracionDestino(Date.valueOf("2026-11-11"))
                .fechaTrasladoDestino(Date.valueOf("2026-11-11"))
                .hora("11:00").nombrePlanta("Planta Formatos").codigoSernapesca("PL-FMT")
                .usuario(planta).especie(especiePelillo).producto(activoConCodigo)
                .cantidad(new BigDecimal("40")).nombreDestino("Destino X").rutDestino("77.777.777-7")
                .documentoTributarioTipo("GUIA").documentoTributarioNumero("G-1")
                .build());

        // PLANTA no puede acceder
        mockMvc.perform(get("/api/admin/productos").with(user(planta.getRut()).roles("PLANTA")))
                .andExpect(status().isForbidden());

        // ADMIN lista con conteo de usos = 1 + 1 = 2
        mockMvc.perform(get("/api/admin/productos").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.codigo=='SER-SECO-01')].usosProduccion", contains(1)))
                .andExpect(jsonPath("$[?(@.codigo=='SER-SECO-01')].usosDestino", contains(1)))
                .andExpect(jsonPath("$[?(@.codigo=='SER-SECO-01')].usosTotales", contains(2)));

        // ADMIN crea un nuevo formato
        String nuevoBody = String.format(
                "{\"codigo\":\"ser-pic-02\",\"nombre\":\"Picado Comercial\",\"descripcion\":\"En sacos\",\"humedadEstadoId\":%d,\"especieId\":%d,\"activo\":true}",
                humSemiSeco.getId(), especiePelillo.getId());
        mockMvc.perform(post("/api/admin/productos")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nuevoBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value("SER-PIC-02"))
                .andExpect(jsonPath("$.humedadEstadoNombre").value("Semi Seco"))
                .andExpect(jsonPath("$.fuente").value("LOCAL"));

        // Código duplicado -> 409 Conflict
        mockMvc.perform(post("/api/admin/productos")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"SER-PIC-02\",\"nombre\":\"Otro Nombre\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICTO_FORMATO"));

        // Actualiza inactivo
        mockMvc.perform(put("/api/admin/productos/" + activoSinCodigo.getId())
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"SER-NEW-03\",\"nombre\":\"Alga Con Codigo\",\"activo\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("SER-NEW-03"));
        assert prod.getId() != null;
    }

    @Test
    @DisplayName("NF.4 (backend): si produccion_exige_formato_oficial=true, rechaza formato inactivo o sin código con 422")
    void exigeFormatoOficial_rechazaInactivoOSinCodigo() throws Exception {
        configService.updateConfig("produccion_exige_formato_oficial", "true");

        String baseProd = "{\"folioOrigen\":\"F-2\",\"fechaProduccion\":\"2026-11-12\",\"hora\":\"09:00\","
                + "\"nombrePlanta\":\"Planta Formatos\",\"codigoSernapesca\":\"PL-FMT\","
                + "\"usuario\":{\"id\":" + planta.getId() + "},"
                + "\"materiaPrimaEspecie\":{\"id\":" + especiePelillo.getId() + "},"
                + "\"humedadEstado\":{\"id\":" + humSeco.getId() + "},"
                + "\"cantidadMateriaPrima\":100,\"cantidadProducto\":40,";

        // Formato inactivo -> 422
        mockMvc.perform(post("/declaracionplantaproduccion")
                        .with(user(planta.getRut()).roles("PLANTA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(baseProd + "\"productoResultante\":{\"id\":" + inactivoConCodigo.getId() + "}}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("VALIDACION_PLANTA"));

        // Formato sin código -> 422
        mockMvc.perform(post("/declaracionplantaproduccion")
                        .with(user(planta.getRut()).roles("PLANTA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(baseProd + "\"productoResultante\":{\"id\":" + activoSinCodigo.getId() + "}}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("VALIDACION_PLANTA"));

        // Formato activo con código -> 200/201 OK
        mockMvc.perform(post("/declaracionplantaproduccion")
                        .with(user(planta.getRut()).roles("PLANTA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(baseProd + "\"productoResultante\":{\"id\":" + activoConCodigo.getId() + "}}"))
                .andExpect(status().is2xxSuccessful());

        // Destino con formato sin código -> 422
        String destInvalido = "{\"folioOrigen\":\"P-PLA-1\",\"fechaDeclaracionDestino\":\"2026-11-12\",\"fechaTrasladoDestino\":\"2026-11-12\","
                + "\"hora\":\"10:00\",\"nombrePlanta\":\"Planta Formatos\",\"codigoSernapesca\":\"PL-FMT\","
                + "\"usuario\":{\"id\":" + planta.getId() + "},"
                + "\"especie\":{\"id\":" + especiePelillo.getId() + "},"
                + "\"producto\":{\"id\":" + activoSinCodigo.getId() + "},"
                + "\"cantidad\":40,\"nombreDestino\":\"Cliente\",\"rutDestino\":\"88.888.888-8\","
                + "\"documentoTributarioTipo\":\"GUIA\",\"documentoTributarioNumero\":\"G-2\"}";
        mockMvc.perform(post("/api/declaracion-planta-destino")
                        .with(user(planta.getRut()).roles("PLANTA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(destInvalido))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("VALIDACION_PLANTA"));
    }
}
