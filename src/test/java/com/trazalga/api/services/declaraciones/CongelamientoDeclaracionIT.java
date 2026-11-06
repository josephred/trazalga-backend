package com.trazalga.api.services.declaraciones;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import com.trazalga.api.IntegracionBase;
import com.trazalga.api.exceptions.DeclaracionCongeladaException;
import com.trazalga.api.models.CaletaModel;
import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.models.DeclaracionAutorizacionModel;
import com.trazalga.api.models.DeclaracionMarcaModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.ExtraccionTipoModel;
import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.models.ProvinciaModel;
import com.trazalga.api.models.RegionModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.ICaletaRepository;
import com.trazalga.api.repositories.IComunaRepository;
import com.trazalga.api.repositories.IDeclaracionAutorizacionRepository;
import com.trazalga.api.repositories.IDeclaracionMarcaRepository;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;
import com.trazalga.api.repositories.IEspecieRepository;
import com.trazalga.api.repositories.IExtraccionTipoRepository;
import com.trazalga.api.repositories.IHumedadEstadoRepository;
import com.trazalga.api.repositories.IProvinciaRepository;
import com.trazalga.api.repositories.IRegionRepository;
import com.trazalga.api.repositories.IUsuarioRepository;
import com.trazalga.api.services.ConfiguracionGeneralService;
import com.trazalga.api.services.DeclaracionRecolectorService;

/**
 * Refinamiento 30-10, punto 3 (integración, H2 perfil "it"): una declaración con alerta de veda queda
 * congelada para su dueño; sólo un fiscalizador autoriza una edición o la elimina, con motivo y acta.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CongelamientoDeclaracionIT extends IntegracionBase {

    private static final String RUT_DUENO = "21.212.121-2";
    private static final String RUT_OTRO = "22.222.222-2";
    private static final String RUT_FISCALIZADOR = "23.232.323-3";

    @Autowired private MockMvc mockMvc;
    @Autowired private PoliticaEdicionDeclaracion politica;
    @Autowired private AutorizacionDeclaracionService autorizacionService;
    @Autowired private DeclaracionRecolectorService declaracionRecolectorService;
    @Autowired private ConfiguracionGeneralService configuracionGeneralService;

    @Autowired private IDeclaracionRecolectorRepository recolectorRepository;
    @Autowired private IDeclaracionMarcaRepository marcaRepository;
    @Autowired private IDeclaracionAutorizacionRepository autorizacionRepository;
    @Autowired private IRegionRepository regionRepository;
    @Autowired private IProvinciaRepository provinciaRepository;
    @Autowired private IComunaRepository comunaRepository;
    @Autowired private ICaletaRepository caletaRepository;
    @Autowired private IEspecieRepository especieRepository;
    @Autowired private IExtraccionTipoRepository extraccionTipoRepository;
    @Autowired private IHumedadEstadoRepository humedadEstadoRepository;
    @Autowired private IUsuarioRepository usuarioRepository;

    private ComunaModel comuna;
    private CaletaModel caleta;
    private EspecieModel especie;
    private ExtraccionTipoModel metodo;
    private HumedadEstadoModel humedad;
    private UsuarioModel dueno;
    private UsuarioModel planta;

    @BeforeEach
    void setUp() {
        configuracionGeneralService.updateConfig("declaracion_congelar_activo", "true");
        configuracionGeneralService.updateConfig("declaracion_congelar_marcas", "EN_VEDA,CUOTA_EXCEDIDA,POSTERIOR_CIERRE");
        configuracionGeneralService.updateConfig("declaracion_escritura_exige_sesion", "false");
        if (comuna != null) {
            return;
        }
        RegionModel region = regionRepository.saveAndFlush(RegionModel.builder().nombre("Coquimbo CG06").codigo("04").build());
        ProvinciaModel prov = provinciaRepository.saveAndFlush(ProvinciaModel.builder().nombre("Elqui CG06").region(region).build());
        comuna = comunaRepository.saveAndFlush(ComunaModel.builder().nombre("La Higuera CG06").region(region).provincia(prov).build());
        caleta = caletaRepository.saveAndFlush(CaletaModel.builder().nombre("Punta Choros CG06").region(region).comuna(comuna).build());
        especie = especieRepository.saveAndFlush(EspecieModel.builder().nombre("Huiro negro CG06").build());
        metodo = extraccionTipoRepository.saveAndFlush(ExtraccionTipoModel.builder().nombre("Barreteado CG06").build());
        humedad = humedadEstadoRepository.saveAndFlush(HumedadEstadoModel.builder().nombre("Húmedo CG06").build());
        dueno = usuarioRepository.saveAndFlush(UsuarioModel.builder()
                .rut(RUT_DUENO).nombres("Recolector").apellidop("CG06").comuna(comuna).estado("ACTIVO").build());
        usuarioRepository.saveAndFlush(UsuarioModel.builder()
                .rut(RUT_FISCALIZADOR).nombres("Fiscalizador").apellidop("CG06").estado("ACTIVO").build());
        planta = usuarioRepository.saveAndFlush(UsuarioModel.builder()
                .rut("24.242.424-4").nombres("Planta").apellidop("CG06").correo("planta-cg06@trazalga.it").estado("ACTIVO").build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        autorizacionRepository.deleteAll();
        marcaRepository.deleteAll();
        recolectorRepository.deleteAll();
        configuracionGeneralService.updateConfig("declaracion_escritura_exige_sesion", "false");
    }

    // ------------------------------------------------------------------ dueño

    @Test
    @DisplayName("Con EN_VEDA, el dueño no puede modificarla: 423 con la marca en el cuerpo")
    void conVeda_put_423() throws Exception {
        DeclaracionRecolectorModel d = declarar();
        marcar(d, "EN_VEDA");

        mockMvc.perform(put("/declaracionrecolector/" + d.getId()).with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error").value("DECLARACION_CONGELADA"))
                .andExpect(jsonPath("$.marcas[*].marca", hasItem("EN_VEDA")));
    }

    @Test
    @DisplayName("La app 2.4.0 edita recolectores con POST y el id existente: también responde 423")
    void conVeda_postConId_423() throws Exception {
        DeclaracionRecolectorModel d = declarar();
        marcar(d, "EN_VEDA");

        mockMvc.perform(post("/declaracionrecolector").with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"id\":" + d.getId() + "}"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error").value("DECLARACION_CONGELADA"));
    }

    @Test
    @DisplayName("Con EN_VEDA, el dueño no puede eliminarla: 423 y la declaración sigue")
    void conVeda_delete_423() throws Exception {
        DeclaracionRecolectorModel d = declarar();
        marcar(d, "EN_VEDA");

        mockMvc.perform(delete("/declaracionrecolector/" + d.getId()).with(user(RUT_DUENO).roles("USER")))
                .andExpect(status().isLocked());
        assertTrue(recolectorRepository.existsById(d.getId()));
    }

    @Test
    @DisplayName("Resolver la marca en la Consola no descongela la declaración")
    void marcaResuelta_sigueCongelada() {
        DeclaracionRecolectorModel d = declarar();
        DeclaracionMarcaModel m = marcar(d, "EN_VEDA");
        m.setResuelta(true);
        m.setEstadoGestion("RESUELTA");
        marcaRepository.saveAndFlush(m);

        assertTrue(politica.evaluar("RECOLECTOR", d.getId()).congelada());
        assertThrows(DeclaracionCongeladaException.class, () -> declaracionRecolectorService.deleteDeclaracionRecolector(d.getId()));
    }

    @Test
    @DisplayName("Sin alertas que congelen, el dueño elimina como siempre; una marca no congelante no la bloquea")
    void sinAlertaCongelante_deleteOk() throws Exception {
        DeclaracionRecolectorModel d = declarar();
        marcar(d, "DESEMBARQUE_ATIPICO");

        mockMvc.perform(delete("/declaracionrecolector/" + d.getId()).with(user(RUT_DUENO).roles("USER")))
                .andExpect(status().isOk());
        assertFalse(recolectorRepository.existsById(d.getId()));
    }

    @Test
    @DisplayName("El listado del usuario trae las alertas y edicionBloqueada")
    void listado_traeAlertasYEstado() throws Exception {
        DeclaracionRecolectorModel congelada = declarar();
        marcar(congelada, "EN_VEDA");
        DeclaracionRecolectorModel libre = declarar();

        mockMvc.perform(get("/declaracionrecolector/usuario/" + dueno.getId()).with(user(RUT_DUENO).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + congelada.getId() + ")].edicionBloqueada").value(true))
                .andExpect(jsonPath("$[?(@.id == " + congelada.getId() + ")].alertas[0].marca").value("EN_VEDA"))
                .andExpect(jsonPath("$[?(@.id == " + libre.getId() + ")].edicionBloqueada").value(false));
    }

    @Test
    @DisplayName("Con sesión de otro usuario: 403. Con la opción de sesión encendida y sin sesión: 401")
    void sesionYDueno() throws Exception {
        DeclaracionRecolectorModel d = declarar();

        mockMvc.perform(delete("/declaracionrecolector/" + d.getId()).with(user(RUT_OTRO).roles("USER")))
                .andExpect(status().isForbidden());

        configuracionGeneralService.updateConfig("declaracion_escritura_exige_sesion", "true");
        mockMvc.perform(delete("/declaracionrecolector/" + d.getId()).with(anonymous()))
                .andExpect(status().isUnauthorized());
        assertTrue(recolectorRepository.existsById(d.getId()));
    }

    // ------------------------------------------------------------------ fiscalizador

    @Test
    @DisplayName("Autorización de edición: habilita una sola edición; después vuelve a quedar congelada")
    void autorizacionEdicion_unaVez() {
        DeclaracionRecolectorModel d = declarar();
        marcar(d, "EN_VEDA");
        comoFiscalizador();

        autorizacionService.autorizarEdicion("RECOLECTOR", d.getId(), "Error de tipeo en los kilos", "ACTA-2026-114", 24);

        assertFalse(politica.evaluar("RECOLECTOR", d.getId()).congelada());
        Optional<DeclaracionAutorizacionModel> a = politica.verificarEdicion("RECOLECTOR", d.getId());
        assertTrue(a.isPresent());
        politica.registrarUsoAutorizacion(a);

        assertTrue(politica.evaluar("RECOLECTOR", d.getId()).congelada());
        assertThrows(DeclaracionCongeladaException.class, () -> politica.verificarEdicion("RECOLECTOR", d.getId()));
    }

    @Test
    @DisplayName("Eliminación por el fiscalizador: guarda respaldo con las marcas y borra declaración y marcas")
    void eliminacionFiscalizador_respaldo() {
        DeclaracionRecolectorModel d = declarar();
        marcar(d, "EN_VEDA");
        comoFiscalizador();

        DeclaracionAutorizacionModel a = autorizacionService.eliminarConAutorizacion("RECOLECTOR", d.getId(),
                "Declaración duplicada verificada en terreno", "ACTA-2026-115");

        assertEquals(DeclaracionAutorizacionModel.TIPO_ELIMINACION, a.getTipo());
        assertEquals(RUT_FISCALIZADOR, a.getAutorizadoPorRut());
        assertTrue(a.getSnapshot().contains("EN_VEDA"), a.getSnapshot());
        assertFalse(recolectorRepository.existsById(d.getId()));
        assertTrue(marcaRepository.findByDeclaracionTipoAndDeclaracionId("RECOLECTOR", d.getId()).isEmpty());
        assertEquals(1, autorizacionRepository.findByDeclaracionTipoAndDeclaracionIdOrderByAutorizadoEnDesc("RECOLECTOR", d.getId()).size());
    }

    @Test
    @DisplayName("Endpoints del fiscalizador: 403 para un usuario común, 400 sin acta, 409 si no está congelada")
    void endpointsFiscalizador() throws Exception {
        DeclaracionRecolectorModel congelada = declarar();
        marcar(congelada, "EN_VEDA");
        DeclaracionRecolectorModel libre = declarar();
        String body = "{\"motivo\":\"Error de tipeo\",\"actaVerificacion\":\"ACTA-1\"}";

        mockMvc.perform(post("/api/declaraciones/RECOLECTOR/" + congelada.getId() + "/autorizar-edicion")
                        .with(user(RUT_DUENO).roles("USER")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/declaraciones/RECOLECTOR/" + congelada.getId() + "/autorizar-edicion")
                        .with(user(RUT_FISCALIZADOR).roles("FISCALIZADOR")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Error de tipeo\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/declaraciones/RECOLECTOR/" + libre.getId() + "/autorizar-edicion")
                        .with(user(RUT_FISCALIZADOR).roles("FISCALIZADOR")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/declaraciones/RECOLECTOR/" + congelada.getId() + "/autorizar-edicion")
                        .with(user(RUT_FISCALIZADOR).roles("FISCALIZADOR")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("EDICION"));
        mockMvc.perform(get("/api/declaraciones/RECOLECTOR/" + congelada.getId() + "/edicion")
                        .with(user(RUT_FISCALIZADOR).roles("AUDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.congelada").value(false))
                .andExpect(jsonPath("$.autorizacionEdicionVigente").value(true))
                .andExpect(jsonPath("$.autorizaciones[0].actaVerificacion").value("ACTA-1"));
    }

    // ------------------------------------------------------------------

    private void comoFiscalizador() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                RUT_FISCALIZADOR, null, List.of(new SimpleGrantedAuthority("ROLE_FISCALIZADOR"))));
    }

    private DeclaracionRecolectorModel declarar() {
        Date fecha = Date.valueOf("2026-10-10");
        return recolectorRepository.saveAndFlush(DeclaracionRecolectorModel.builder()
                .usuario(dueno).especie(especie).extraccionTipo(metodo).humedadEstado(humedad)
                .comuna(comuna).caleta(caleta)
                .fechaDeclaracion(fecha).fechaExtraccion(fecha).hora("09:00:00")
                .nombre("Recolector CG06").codigoSernapesca("RPA-CG06")
                .folioDesembarqueRo("RO-CG06-" + System.nanoTime())
                .codigoDestinatario("DEST-CG06").nombreDestinatario("Planta CG06").usuarioDestinatario(planta)
                .desembarque(new BigDecimal("500.00")).captura(new BigDecimal("500.00"))
                .build());
    }

    private DeclaracionMarcaModel marcar(DeclaracionRecolectorModel d, String marca) {
        return marcaRepository.saveAndFlush(DeclaracionMarcaModel.builder()
                .declaracionTipo("RECOLECTOR")
                .declaracionId(d.getId())
                .marca(marca)
                .detalle("Detalle de prueba " + marca)
                .build());
    }
}
