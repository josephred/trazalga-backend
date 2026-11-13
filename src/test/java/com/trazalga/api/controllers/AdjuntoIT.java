package com.trazalga.api.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trazalga.api.IntegracionBase;
import com.trazalga.api.exceptions.DeclaracionCongeladaException;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IAdjuntoRepository;
import com.trazalga.api.repositories.IDeclaracionPlantaAbastecimientoRepository;
import com.trazalga.api.repositories.IEspecieRepository;
import com.trazalga.api.repositories.IHumedadEstadoRepository;
import com.trazalga.api.repositories.IUsuarioRepository;
import com.trazalga.api.services.AdjuntoService;
import com.trazalga.api.services.ConfiguracionGeneralService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        "trazalga.adjuntos.dir=target/test-adjuntos",
        "trazalga.adjuntos.max-mb=2"
})
class AdjuntoIT extends IntegracionBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private IAdjuntoRepository adjuntoRepo;
    @Autowired private AdjuntoService adjuntoService;
    @Autowired private ConfiguracionGeneralService configService;
    @Autowired private IUsuarioRepository usuarioRepo;
    @Autowired private IEspecieRepository especieRepo;
    @Autowired private IHumedadEstadoRepository humedadRepo;
    @Autowired private IDeclaracionPlantaAbastecimientoRepository abastRepo;

    private UsuarioModel planta;
    private EspecieModel pelillo;
    private HumedadEstadoModel humHumedo;

    @BeforeEach
    void setup() {
        jdbc.execute("ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN IF NOT EXISTS folio_declaracion_a_pla VARCHAR(50)");
        abastRepo.deleteAll();
        adjuntoRepo.deleteAll();

        pelillo = especieRepo.findAll().stream().findFirst()
                .orElseGet(() -> especieRepo.save(EspecieModel.builder().nombre("PELILLO").activo(true).build()));
        humHumedo = humedadRepo.findAll().stream().findFirst()
                .orElseGet(() -> humedadRepo.save(HumedadEstadoModel.builder().nombre("Humedo").rangoInicio(70).rangoFin(90).build()));
        planta = usuarioRepo.findByRut("33.333.333-3").orElseGet(() ->
                usuarioRepo.saveAndFlush(UsuarioModel.builder()
                        .rut("33.333.333-3").nombres("Planta").apellidop("Adjuntos").estado("ACTIVO").build()));

        configService.updateConfig("declaracion_escritura_exige_sesion", "false");
        configService.updateConfig("variacion_peso_exige_voucher", "true");
        configService.updateConfig("planta_exige_peso_romana", "true");
        configService.updateConfig("planta_exige_humedad_recepcion", "false");
        configService.updateConfig("planta_exige_lugar_acopio", "false");
        configService.updateConfig("planta_exige_lote_interno", "false");
        configService.updateConfig("planta_exige_guia_adjunta", "false");
    }

    @AfterEach
    void tearDown() {
        configService.updateConfig("planta_exige_guia_adjunta", "false");
        configService.updateConfig("planta_exige_lugar_acopio", "true");
        configService.updateConfig("planta_exige_lote_interno", "true");
    }

    private byte[] crearImagenJpg(int w, int h, Color c) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(c);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", baos);
        return baos.toByteArray();
    }

    @Test
    @DisplayName("NA.4: Subida redimensiona >2000px, deduplica SHA-256, exige sesión y bloquea borrado si congelada")
    void subirDescargarYSeguridad() throws Exception {
        byte[] imgGrande = crearImagenJpg(2400, 1200, Color.BLUE);
        MockMultipartFile file = new MockMultipartFile("file", "voucher_big.jpg", "image/jpeg", imgGrande);

        // Sin autenticación -> 401/403
        mockMvc.perform(multipart("/api/adjuntos").file(file))
                .andExpect(status().isUnauthorized());

        // Subida autenticada -> 201 + redimensionada a <= 2000px
        MvcResult up1 = mockMvc.perform(multipart("/api/adjuntos").file(file)
                        .param("tipo", "VOUCHER_ROMANA")
                        .with(user(planta.getRut()).roles("PLANTA")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uuid", not(emptyString())))
                .andExpect(jsonPath("$.url", startsWith("/api/adjuntos/")))
                .andExpect(jsonPath("$.sha256", not(emptyString())))
                .andReturn();

        JsonNode n1 = objectMapper.readTree(up1.getResponse().getContentAsString());
        String uuid1 = n1.get("uuid").asText();

        // Descarga sin sesión -> 401
        mockMvc.perform(get("/api/adjuntos/" + uuid1))
                .andExpect(status().isUnauthorized());

        // Descarga autenticada -> imagen con ancho 2000px y alto 1000px
        byte[] descargada = mockMvc.perform(get("/api/adjuntos/" + uuid1)
                        .with(user("fisca").roles("FISCALIZADOR")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(descargada));
        assertNotNull(decoded);
        assertEquals(2000, decoded.getWidth());
        assertEquals(1000, decoded.getHeight());

        // Deduplicación SHA-256 al subir idéntica imagen
        MockMultipartFile dupFile = new MockMultipartFile("file", "voucher_dup.jpg", "image/jpeg", imgGrande);
        MvcResult up2 = mockMvc.perform(multipart("/api/adjuntos").file(dupFile)
                        .with(user(planta.getRut()).roles("PLANTA")))
                .andExpect(status().isCreated())
                .andReturn();
        assertEquals(uuid1, objectMapper.readTree(up2.getResponse().getContentAsString()).get("uuid").asText());
        assertEquals(1, adjuntoRepo.count());

        // Formato inválido -> 422
        MockMultipartFile badFile = new MockMultipartFile("file", "script.exe", "application/x-msdownload", new byte[]{1, 2, 3});
        mockMvc.perform(multipart("/api/adjuntos").file(badFile)
                        .with(user(planta.getRut()).roles("PLANTA")))
                .andExpect(status().isUnprocessableEntity());

        // Declaración congelada prohíbe eliminar adjunto
        assertThrows(DeclaracionCongeladaException.class, () -> adjuntoService.eliminarSiPermitido(uuid1, true));
    }

    @Test
    @DisplayName("NA.4: Foto de voucher satisface exigencia de voucher y planta_exige_guia_adjunta controla respaldo de guía")
    void integracionConRecepcionPlanta() throws Exception {
        byte[] imgChica = crearImagenJpg(400, 300, Color.GREEN);
        MockMultipartFile fVoucher = new MockMultipartFile("file", "v.jpg", "image/jpeg", imgChica);
        MvcResult upV = mockMvc.perform(multipart("/api/adjuntos").file(fVoucher)
                        .param("tipo", "VOUCHER_ROMANA")
                        .with(user(planta.getRut()).roles("PLANTA")))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode vNode = objectMapper.readTree(upV.getResponse().getContentAsString());

        // Sin voucher número pero CON foto adjunta -> 200/201 OK
        String bodyConFoto = """
                {
                  "folioOrigen": "COM-NA4-1",
                  "folioDeclaracionAPla": "APLA-NA4-1",
                  "fechaIngresoPlanta": "2026-11-12",
                  "fechaTraslado": "2026-11-12",
                  "hora": "10:00",
                  "nombrePlanta": "Planta Adjuntos",
                  "codigoSernapesca": "PL-33",
                  "usuario": { "id": %d },
                  "especie": { "id": %d },
                  "humedadEstado": { "id": %d },
                  "cantidad": 500,
                  "pesoRomanaKg": 495,
                  "documentoTributarioTipo": "GUIA",
                  "documentoTributarioNumero": "G-900",
                  "documentoTributarioFecha": "2026-11-12",
                  "voucherRomanaAdjunto": "%s",
                  "voucherRomanaAdjuntoId": %d
                }
                """.formatted(planta.getId(), pelillo.getId(), humHumedo.getId(), vNode.get("url").asText(), vNode.get("id").asLong());

        mockMvc.perform(post("/api/declaracion-planta-abastecimiento")
                        .contentType(APPLICATION_JSON)
                        .content(bodyConFoto))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.voucherRomanaAdjunto").value(vNode.get("url").asText()));

        // Con planta_exige_guia_adjunta=true sin guía -> 422; con guía -> OK
        configService.updateConfig("planta_exige_guia_adjunta", "true");
        mockMvc.perform(post("/api/declaracion-planta-abastecimiento")
                        .contentType(APPLICATION_JSON)
                        .content(bodyConFoto.replace("APLA-NA4-1", "APLA-NA4-2")))
                .andExpect(status().isUnprocessableEntity());

        byte[] imgGuia = crearImagenJpg(500, 400, Color.DARK_GRAY);
        MockMultipartFile fGuia = new MockMultipartFile("file", "guia.jpg", "image/jpeg", imgGuia);
        MvcResult upG = mockMvc.perform(multipart("/api/adjuntos").file(fGuia)
                        .param("tipo", "GUIA_DESPACHO")
                        .with(user(planta.getRut()).roles("PLANTA")))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode gNode = objectMapper.readTree(upG.getResponse().getContentAsString());

        String bodyConGuia = bodyConFoto
                .replace("APLA-NA4-1", "APLA-NA4-3")
                .replace("\"voucherRomanaAdjuntoId\": " + vNode.get("id").asLong(),
                        "\"voucherRomanaAdjuntoId\": " + vNode.get("id").asLong() +
                        ", \"guiaDespachoAdjunto\": \"" + gNode.get("url").asText() + "\"" +
                        ", \"guiaDespachoAdjuntoId\": " + gNode.get("id").asLong());

        mockMvc.perform(post("/api/declaracion-planta-abastecimiento")
                        .contentType(APPLICATION_JSON)
                        .content(bodyConGuia))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.guiaDespachoAdjunto").value(gNode.get("url").asText()));
    }
}
