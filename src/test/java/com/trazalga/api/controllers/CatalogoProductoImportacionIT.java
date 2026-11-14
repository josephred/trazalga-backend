package com.trazalga.api.controllers;

import com.trazalga.api.IntegracionBase;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.models.ProductoModel;
import com.trazalga.api.repositories.IDeclaracionPlantaDestinoRepository;
import com.trazalga.api.repositories.IDeclaracionPlantaProduccionRepository;
import com.trazalga.api.repositories.IEspecieRepository;
import com.trazalga.api.repositories.IHumedadEstadoRepository;
import com.trazalga.api.repositories.IProductoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("QF.3 / QF.8 - Importación del catálogo oficial Sernapesca (.xlsx y .csv)")
class CatalogoProductoImportacionIT extends IntegracionBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private IProductoRepository productoRepo;
    @Autowired private IEspecieRepository especieRepo;
    @Autowired private IHumedadEstadoRepository humedadRepo;
    @Autowired private IDeclaracionPlantaProduccionRepository produccionRepo;
    @Autowired private IDeclaracionPlantaDestinoRepository destinoRepo;

    private EspecieModel huiroPalo;
    private EspecieModel huiroNegro;
    private EspecieModel huiro;
    private ProductoModel productoLocal;

    @BeforeEach
    void setup() {
        destinoRepo.deleteAll();
        produccionRepo.deleteAll();
        productoRepo.deleteAll();

        humedadRepo.findAll().stream()
                .filter(h -> "Seco".equalsIgnoreCase(h.getNombre()))
                .findFirst()
                .orElseGet(() -> humedadRepo.save(HumedadEstadoModel.builder()
                        .nombre("Seco").rangoInicio(1).rangoFin(25).build()));

        huiroPalo = asegurarEspecie("HUIRO PALO");
        huiroNegro = asegurarEspecie("HUIRO NEGRO O CHASCON");
        huiro = asegurarEspecie("HUIRO");

        productoLocal = productoRepo.save(ProductoModel.builder()
                .nombre("Producto Local Previo")
                .activo(true)
                .fuente("LOCAL")
                .build());
    }

    @AfterEach
    void cleanup() {
        destinoRepo.deleteAll();
        produccionRepo.deleteAll();
        productoRepo.deleteAll();
        for (EspecieModel e : especieRepo.findAll()) {
            if (e.getCodigoSernapesca() != null) {
                e.setCodigoSernapesca(null);
                especieRepo.save(e);
            }
        }
    }

    private EspecieModel asegurarEspecie(String nombre) {
        return especieRepo.findByNombreIgnoreCase(nombre).orElseGet(() ->
                especieRepo.save(EspecieModel.builder().nombre(nombre).activo(true).build()));
    }

    @Test
    @DisplayName("QF.3 / QF.8: Excel real -> vista previa no escribe, aplicar crea 19 formatos (104 chars íntegro) y segunda carga da 19 sin cambio")
    void importarXlsxOficial_previewYAplicarIdempotente() throws Exception {
        byte[] xlsxBytes = new ClassPathResource("formatos_algas_2026.xlsx").getInputStream().readAllBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "formatos_algas_2026.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes);

        // RBAC: usuario no ADMIN recibe 403
        mockMvc.perform(multipart("/api/admin/productos/importar").file(file).with(user("6666").roles("PLANTA")))
                .andExpect(status().isForbidden());

        // 1. Vista previa (aplicar=false por defecto): 0 escrituras
        mockMvc.perform(multipart("/api/admin/productos/importar").file(file).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aplicado").value(false))
                .andExpect(jsonPath("$.totalFilas").value(22))
                .andExpect(jsonPath("$.nuevos").value(19))
                .andExpect(jsonPath("$.actualizados").value(0))
                .andExpect(jsonPath("$.sinCambio").value(0))
                .andExpect(jsonPath("$.recursos").value(3))
                .andExpect(jsonPath("$.porRevisar").value(0))
                .andExpect(jsonPath("$.localesNoEnCatalogoCount").value(1))
                .andExpect(jsonPath("$.localesNoEnCatalogo[0].nombre").value("Producto Local Previo"));

        assertEquals(1L, productoRepo.count(), "La vista previa no debe persistir nuevos productos");
        assertNull(especieRepo.findById(huiroPalo.getId()).orElseThrow().getCodigoSernapesca(),
                "La vista previa no debe asignar codigo_sernapesca");

        // 2. Aplicar=true: crea 19 formatos y vincula 3 especies; conserva producto local activo (D45)
        mockMvc.perform(multipart("/api/admin/productos/importar")
                        .file(file)
                        .param("aplicar", "true")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aplicado").value(true))
                .andExpect(jsonPath("$.nuevos").value(19))
                .andExpect(jsonPath("$.recursos").value(3))
                .andExpect(jsonPath("$.porRevisar").value(0))
                .andExpect(jsonPath("$.localesNoEnCatalogoCount").value(1));

        assertEquals(20L, productoRepo.count(), "Debe haber 19 oficiales + 1 local previo");
        assertTrue(productoRepo.findById(productoLocal.getId()).orElseThrow().getActivo(),
                "El producto local ausente del catálogo no se desactiva automáticamente (D45)");

        ProductoModel p40959 = productoRepo.findByCodigoIgnoreCase("40959").orElseThrow();
        assertEquals(104, p40959.getNombre().length(), "El nombre de 104 caracteres debe guardarse íntegro");
        assertEquals("MOLIDA/PICADA", p40959.getPresentacion());
        assertNull(p40959.getCalibre());
        assertEquals(huiroNegro.getId(), p40959.getEspecie().getId());
        assertTrue(p40959.getFuente().startsWith("formatos_algas_2026.xlsx ("));

        ProductoModel p47954 = productoRepo.findByCodigoIgnoreCase("47954").orElseThrow();
        assertEquals("PELLET", p47954.getPresentacion());
        assertNull(p47954.getCalibre());
        assertEquals(huiroPalo.getId(), p47954.getEspecie().getId());

        assertEquals("141", especieRepo.findById(huiroNegro.getId()).orElseThrow().getCodigoSernapesca());
        assertEquals("142", especieRepo.findById(huiroPalo.getId()).orElseThrow().getCodigoSernapesca());
        assertEquals("145", especieRepo.findById(huiro.getId()).orElseThrow().getCodigoSernapesca());

        // 3. Segunda carga: 19 sin cambio, 0 nuevos, 0 actualizados
        mockMvc.perform(multipart("/api/admin/productos/importar")
                        .file(file)
                        .param("aplicar", "true")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aplicado").value(true))
                .andExpect(jsonPath("$.nuevos").value(0))
                .andExpect(jsonPath("$.actualizados").value(0))
                .andExpect(jsonPath("$.sinCambio").value(19))
                .andExpect(jsonPath("$.recursos").value(3))
                .andExpect(jsonPath("$.porRevisar").value(0));

        // 4. QF.5 / QF.8: GET /producto?especieId= devuelve 9 (huiro palo), 6 (huiro negro) y 4 (huiro)
        mockMvc.perform(get("/producto").param("especieId", huiroPalo.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(9)))
                .andExpect(jsonPath("$[?(@.codigo=='47954')].presentacion", contains("PELLET")))
                .andExpect(jsonPath("$[?(@.codigo=='47954')].calibre", contains(nullValue())));

        mockMvc.perform(get("/producto").param("especieId", huiroNegro.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)));

        mockMvc.perform(get("/producto").param("especieId", huiro.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)));
    }

    @Test
    @DisplayName("QF.3: CSV descarta códigos repetidos y reporta fila de 7 partes como POR_REVISAR sin importarla")
    void importarCsv_descartaDuplicadosYPorRevisar() throws Exception {
        String csv = "codigo;nombre\n"
                + "45455;HUIRO PALO - SECO (SOLO ALGAS) - NINGUNO - ENTERO - FARDO - A GRANEL - 1 - KILOGRAMO\n"
                + "45455;HUIRO PALO - SECO (SOLO ALGAS) - NINGUNO - ENTERO - FARDO - A GRANEL - 1 - KILOGRAMO\n"
                + "99999;HUIRO PALO - SECO (SOLO ALGAS) - NINGUNO - PICADA - 3/4 - A GRANEL - 1\n";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "catalogo.csv",
                "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/admin/productos/importar")
                        .file(file)
                        .param("aplicar", "true")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nuevos").value(1))
                .andExpect(jsonPath("$.porRevisar").value(1))
                .andExpect(jsonPath("$.descartadosDuplicados").value(1))
                .andExpect(jsonPath("$.filas[?(@.codigo=='99999')].estado", contains("POR_REVISAR")));

        assertTrue(productoRepo.findByCodigoIgnoreCase("45455").isPresent());
        assertTrue(productoRepo.findByCodigoIgnoreCase("99999").isEmpty());
    }
}
