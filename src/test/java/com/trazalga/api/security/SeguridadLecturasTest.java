package com.trazalga.api.security;

import com.trazalga.api.IntegracionBase;
import com.trazalga.api.models.AmerbModel;
import com.trazalga.api.models.BuzoModel;
import com.trazalga.api.models.CaletaModel;
import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.models.DeclaracionAreaModel;
import com.trazalga.api.models.DeclaracionArmadorModel;
import com.trazalga.api.models.DeclaracionComercializadorModel;
import com.trazalga.api.models.DeclaracionEnvioModel;
import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import com.trazalga.api.models.DeclaracionPlantaDestinoModel;
import com.trazalga.api.models.DeclaracionPlantaProduccionModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.models.EmbarcacionModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.ExtraccionTipoModel;
import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.models.ProductoModel;
import com.trazalga.api.models.ProvinciaModel;
import com.trazalga.api.models.RegionModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IAmerbRepository;
import com.trazalga.api.repositories.IBuzoRepository;
import com.trazalga.api.repositories.ICaletaRepository;
import com.trazalga.api.repositories.IComunaRepository;
import com.trazalga.api.repositories.IDeclaracionAreaRepository;
import com.trazalga.api.repositories.IDeclaracionArmadorRepository;
import com.trazalga.api.repositories.IDeclaracionComercializadorRepository;
import com.trazalga.api.repositories.IDeclaracionEnvioRepository;
import com.trazalga.api.repositories.IDeclaracionPlantaAbastecimientoRepository;
import com.trazalga.api.repositories.IDeclaracionPlantaDestinoRepository;
import com.trazalga.api.repositories.IDeclaracionPlantaProduccionRepository;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;
import com.trazalga.api.repositories.IEmbarcacionRepository;
import com.trazalga.api.repositories.IEspecieRepository;
import com.trazalga.api.repositories.IExtraccionTipoRepository;
import com.trazalga.api.repositories.IHumedadEstadoRepository;
import com.trazalga.api.repositories.IProductoRepository;
import com.trazalga.api.repositories.IProvinciaRepository;
import com.trazalga.api.repositories.IRegionRepository;
import com.trazalga.api.repositories.IUsuarioRepository;
import com.trazalga.api.services.ConfiguracionGeneralService;
import org.junit.jupiter.api.BeforeAll;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ZS.4 · Cobertura integral de seguridad en lecturas (ZS.2/ZS.3) y escrituras (ZS.2):
 * <ul>
 *   <li>401 sin token en los 7 controladores de declaración, /declaracionenvio y /usuario;</li>
 *   <li>200 para dueño / destinatario; 403 para otro usuario (y en listados generales);</li>
 *   <li>200 para ADMIN, FISCALIZADOR y AUDITOR;</li>
 *   <li>200 sin token en catálogos públicos y consulta pública de patentes;</li>
 *   <li>401 al modificar/eliminar sin token con declaracion_escritura_exige_sesion=true (incl. comercializador).</li>
 * </ul>
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SeguridadLecturasTest extends IntegracionBase {

    private static final String RUT_DUENO = "15.111.111-1";
    private static final String RUT_DEST = "16.222.222-2";
    private static final String RUT_AJENO = "17.333.333-3";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ConfiguracionGeneralService configuracionGeneralService;

    @Autowired private IRegionRepository regionRepo;
    @Autowired private IProvinciaRepository provinciaRepo;
    @Autowired private IComunaRepository comunaRepo;
    @Autowired private ICaletaRepository caletaRepo;
    @Autowired private IAmerbRepository amerbRepo;
    @Autowired private IEmbarcacionRepository embarcacionRepo;
    @Autowired private IBuzoRepository buzoRepo;
    @Autowired private IEspecieRepository especieRepo;
    @Autowired private IExtraccionTipoRepository extraccionTipoRepo;
    @Autowired private IHumedadEstadoRepository humedadRepo;
    @Autowired private IProductoRepository productoRepo;
    @Autowired private IUsuarioRepository usuarioRepo;

    @Autowired private IDeclaracionRecolectorRepository recolectorRepo;
    @Autowired private IDeclaracionArmadorRepository armadorRepo;
    @Autowired private IDeclaracionAreaRepository areaRepo;
    @Autowired private IDeclaracionComercializadorRepository comercializadorRepo;
    @Autowired private IDeclaracionPlantaAbastecimientoRepository abastRepo;
    @Autowired private IDeclaracionPlantaProduccionRepository prodRepo;
    @Autowired private IDeclaracionPlantaDestinoRepository destRepo;
    @Autowired private IDeclaracionEnvioRepository envioRepo;

    private UsuarioModel dueno;
    private UsuarioModel dest;
    private UsuarioModel ajeno;

    private DeclaracionRecolectorModel dRec;
    private DeclaracionArmadorModel dArm;
    private DeclaracionAreaModel dArea;
    private DeclaracionComercializadorModel dCom;
    private DeclaracionPlantaAbastecimientoModel dAbast;
    private DeclaracionPlantaProduccionModel dProd;
    private DeclaracionPlantaDestinoModel dDest;
    private DeclaracionEnvioModel dEnvio;

    @BeforeAll
    void prepararDatos() {
        jdbcTemplate.execute("ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN IF NOT EXISTS folio_declaracion_a_pla VARCHAR(50)");
        jdbcTemplate.execute("ALTER TABLE declaracion_planta_produccion ADD COLUMN IF NOT EXISTS folio_declaracion_p_pla VARCHAR(50)");

        RegionModel reg = regionRepo.saveAndFlush(RegionModel.builder().nombre("Coquimbo ZS4").codigo("04Z4").build());
        ProvinciaModel prov = provinciaRepo.saveAndFlush(ProvinciaModel.builder().nombre("Elqui ZS4").region(reg).build());
        ComunaModel com = comunaRepo.saveAndFlush(ComunaModel.builder().nombre("La Serena ZS4").region(reg).provincia(prov).build());
        CaletaModel caleta = caletaRepo.saveAndFlush(CaletaModel.builder().nombre("Caleta ZS4").region(reg).comuna(com).build());
        AmerbModel amerb = amerbRepo.saveAndFlush(AmerbModel.builder().nombre("AMERB ZS4").regionModel(reg).comuna(com).build());
        EmbarcacionModel emb = embarcacionRepo.saveAndFlush(EmbarcacionModel.builder().nombre("Emb ZS4").codigo("EMB-ZS4").build());
        BuzoModel buzo = buzoRepo.saveAndFlush(BuzoModel.builder().nombre("Buzo ZS4").codigo("BUZ-ZS4").build());
        EspecieModel esp = especieRepo.saveAndFlush(EspecieModel.builder().nombre("Huiro Negro ZS4").activo(true).build());
        ExtraccionTipoModel metodo = extraccionTipoRepo.saveAndFlush(ExtraccionTipoModel.builder().nombre("Barreteado ZS4").build());
        HumedadEstadoModel hum = humedadRepo.saveAndFlush(HumedadEstadoModel.builder().nombre("Humedo ZS4").rangoInicio(60).rangoFin(85).build());
        ProductoModel prod = productoRepo.saveAndFlush(ProductoModel.builder().nombre("Picado ZS4").especie(esp).activo(true).build());

        dueno = usuarioRepo.saveAndFlush(UsuarioModel.builder()
                .rut(RUT_DUENO).nombres("Dueno").apellidop("ZS4").comuna(com).estado("ACTIVO").build());
        dest = usuarioRepo.saveAndFlush(UsuarioModel.builder()
                .rut(RUT_DEST).nombres("Destinatario").apellidop("ZS4").comuna(com).estado("ACTIVO").build());
        ajeno = usuarioRepo.saveAndFlush(UsuarioModel.builder()
                .rut(RUT_AJENO).nombres("Ajeno").apellidop("ZS4").comuna(com).estado("ACTIVO").build());

        Date f = Date.valueOf("2026-10-15");

        dRec = recolectorRepo.saveAndFlush(DeclaracionRecolectorModel.builder()
                .usuario(dueno).folioOrigen("RO-ZS4-1").fechaExtraccion(f).fechaDeclaracion(f).hora("08:00:00")
                .nombre("Rec ZS4").codigoSernapesca("RPA-ZS4-1").caleta(caleta).comuna(com)
                .especie(esp).extraccionTipo(metodo).humedadEstado(hum)
                .desembarque(new BigDecimal("100.00")).captura(new BigDecimal("110.00"))
                .codigoDestinatario(RUT_DEST).nombreDestinatario("Destinatario ZS4")
                .usuarioDestinatario(dest).build());

        dArm = armadorRepo.saveAndFlush(DeclaracionArmadorModel.builder()
                .usuario(dueno).folioOrigen("DA-ZS4-1").fechaExtraccion(f).fechaDeclaracion(f).hora("09:00:00")
                .embarcacion(emb).buzo(buzo).caleta(caleta).especie(esp).humedadEstado(hum)
                .desembarque(new BigDecimal("150.00")).captura(160.0)
                .tipoDestinatario("comercializador").codigoDestinatario(RUT_DEST)
                .usuarioDestinatario(dest).build());

        dArea = areaRepo.saveAndFlush(DeclaracionAreaModel.builder()
                .usuario(dueno).folioOrigen("AM-ZS4-1").fechaExtraccion(f).fechaDeclaracion(f).hora("10:00:00")
                .amerb(amerb).caleta(caleta).especie(esp).humedadEstado(hum)
                .desembarque(200.0).captura(210.0)
                .tipoDestinatario("comercializador").codigoDestinatario(RUT_DEST)
                .usuarioDestinatario(dest).build());

        dCom = comercializadorRepo.saveAndFlush(DeclaracionComercializadorModel.builder()
                .usuario(dueno).folioOrigen("DC-ZS4-1").fechaDeclaracion(f).hora("11:00:00")
                .especie(esp).humedadEstado(hum).cantidad(new BigDecimal("100.00"))
                .codigoDestinatario(RUT_DEST).nombreDestinatario("Destinatario ZS4")
                .usuarioDestinatario(dest).build());

        dAbast = abastRepo.saveAndFlush(DeclaracionPlantaAbastecimientoModel.builder()
                .usuario(dueno).folioDeclaracionAPla("APLA-ZS4-1").fechaIngresoPlanta(f).hora("12:00:00")
                .nombrePlanta("Planta ZS4").especie(esp).humedadEstado(hum).cantidad(new BigDecimal("100.00"))
                .usuarioDestinatario(dest).build());

        dProd = prodRepo.saveAndFlush(DeclaracionPlantaProduccionModel.builder()
                .usuario(dueno).fechaProduccion(f).hora("13:00:00").nombrePlanta("Planta ZS4")
                .materiaPrimaEspecie(esp).humedadEstado(hum).cantidadMateriaPrima(new BigDecimal("100.00"))
                .productoResultante(prod).cantidadProducto(new BigDecimal("25.00"))
                .usuarioDestinatario(dest).build());

        dDest = destRepo.saveAndFlush(DeclaracionPlantaDestinoModel.builder()
                .usuario(dueno).fechaDeclaracionDestino(f).hora("14:00:00").nombrePlanta("Planta ZS4")
                .especie(esp).producto(prod).cantidad(new BigDecimal("25.00"))
                .rutDestino(RUT_DEST).usuarioDestinatario(dest).build());

        dEnvio = envioRepo.saveAndFlush(DeclaracionEnvioModel.builder()
                .idOrigen("1").idDestino("2").build());
    }

    @BeforeEach
    void restaurarParametros() {
        configuracionGeneralService.updateConfig("declaracion_lectura_exige_dueno", "true");
        configuracionGeneralService.updateConfig("declaracion_escritura_exige_sesion", "false");
    }

    @Test
    @DisplayName("ZS.4: Sin token devuelve 401 en los 7 controladores de declaración, /declaracionenvio y /usuario")
    void sinToken_devuelve401EnTodasLasRutasCerradas() throws Exception {
        String[] rutasCerradas = {
                "/declaracionrecolector",
                "/declaracionrecolector/" + dRec.getId(),
                "/declaracionrecolector/usuario/" + dueno.getId(),
                "/declaracionrecolector/usuariosdestinatarios/" + dest.getId(),
                "/declaracionrecolector/lastfolioorigen",
                "/declaracionarmador",
                "/declaracionarmador/" + dArm.getId(),
                "/declaracionarmador/usuario/" + dueno.getId(),
                "/declaracionarmador/usuariosdestinatarios/" + dest.getId(),
                "/declaracionarmador/lastfolioorigen",
                "/declaracionarea",
                "/declaracionarea/" + dArea.getId(),
                "/declaracionarea/usuario/" + dueno.getId(),
                "/declaracionarea/usuariosdestinatarios/" + dest.getId(),
                "/declaracionarea/lastfolioorigen",
                "/declaracioncomercializador",
                "/declaracioncomercializador/" + dCom.getId(),
                "/declaracioncomercializador/usuario/" + dueno.getId(),
                "/declaracioncomercializador/usuariodesc/" + dueno.getId(),
                "/declaracioncomercializador/usuariosdestinatarios/" + dest.getId(),
                "/declaracioncomercializador/detalle-consolidado/" + dCom.getId(),
                "/api/declaracion-planta-abastecimiento",
                "/declaracionplantaabastecimiento/" + dAbast.getId(),
                "/api/declaracion-planta-abastecimiento/usuario/" + dueno.getId(),
                "/api/declaracion-planta-abastecimiento/usuariosdestinatarios/" + dest.getId(),
                "/api/declaracion-planta-abastecimiento/detalle-consolidado/" + dAbast.getId(),
                "/api/declaracion-planta-produccion",
                "/declaracionplantaproduccion/" + dProd.getId(),
                "/api/declaracion-planta-produccion/usuario/" + dueno.getId(),
                "/api/declaracion-planta-produccion/usuariosdestinatarios/" + dest.getId(),
                "/api/declaracion-planta-produccion/detalle-consolidado/" + dProd.getId(),
                "/api/declaracion-planta-destino",
                "/api/declaracion-planta-destino/" + dDest.getId(),
                "/api/declaracion-planta-destino/usuario/" + dueno.getId(),
                "/api/declaracion-planta-destino/usuariosdestinatarios/" + dest.getId(),
                "/api/declaracion-planta-destino/detalle-consolidado/" + dDest.getId(),
                "/declaracionenvio",
                "/declaracionenvio/" + dEnvio.getId(),
                "/usuario",
                "/usuario/" + dueno.getId(),
                "/usuario/full",
                "/api/usuarios"
        };
        for (String ruta : rutasCerradas) {
            mockMvc.perform(get(ruta).with(anonymous()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    @DisplayName("ZS.4: Con token propio (dueño o destinatario) devuelve 200; otro usuario recibe 403")
    void tokenPropio200_otroUsuario403() throws Exception {
        String[] rutasDueno = {
                "/declaracionrecolector/usuario/" + dueno.getId(),
                "/declaracionrecolector/" + dRec.getId(),
                "/declaracionarmador/usuario/" + dueno.getId(),
                "/declaracionarmador/" + dArm.getId(),
                "/declaracionarea/usuario/" + dueno.getId(),
                "/declaracionarea/" + dArea.getId(),
                "/declaracioncomercializador/usuario/" + dueno.getId(),
                "/declaracioncomercializador/usuariodesc/" + dueno.getId(),
                "/declaracioncomercializador/" + dCom.getId(),
                "/declaracioncomercializador/detalle-consolidado/" + dCom.getId(),
                "/api/declaracion-planta-abastecimiento/usuario/" + dueno.getId(),
                "/api/declaracion-planta-abastecimiento/" + dAbast.getId(),
                "/api/declaracion-planta-abastecimiento/detalle-consolidado/" + dAbast.getId(),
                "/api/declaracion-planta-produccion/usuario/" + dueno.getId(),
                "/api/declaracion-planta-produccion/" + dProd.getId(),
                "/api/declaracion-planta-produccion/detalle-consolidado/" + dProd.getId(),
                "/api/declaracion-planta-destino/usuario/" + dueno.getId(),
                "/api/declaracion-planta-destino/" + dDest.getId(),
                "/api/declaracion-planta-destino/detalle-consolidado/" + dDest.getId()
        };
        for (String ruta : rutasDueno) {
            mockMvc.perform(get(ruta).with(user(RUT_DUENO).roles("USER")))
                    .andExpect(status().isOk());
            mockMvc.perform(get(ruta).with(user(RUT_AJENO).roles("USER")))
                    .andExpect(status().isForbidden());
        }

        String[] rutasDestinatario = {
                "/declaracionrecolector/usuariosdestinatarios/" + dest.getId(),
                "/declaracionarmador/usuariosdestinatarios/" + dest.getId(),
                "/declaracionarea/usuariosdestinatarios/" + dest.getId(),
                "/declaracioncomercializador/usuariosdestinatarios/" + dest.getId(),
                "/api/declaracion-planta-abastecimiento/usuariosdestinatarios/" + dest.getId(),
                "/api/declaracion-planta-produccion/usuariosdestinatarios/" + dest.getId(),
                "/api/declaracion-planta-destino/usuariosdestinatarios/" + dest.getId(),
                "/declaracionrecolector/" + dRec.getId(),
                "/declaracioncomercializador/" + dCom.getId(),
                "/api/declaracion-planta-destino/" + dDest.getId()
        };
        for (String ruta : rutasDestinatario) {
            mockMvc.perform(get(ruta).with(user(RUT_DEST).roles("USER")))
                    .andExpect(status().isOk());
            mockMvc.perform(get(ruta).with(user(RUT_AJENO).roles("USER")))
                    .andExpect(status().isForbidden());
        }

        // Listados raíz sin filtro de usuario: 403 para ROLE_USER
        String[] listadosRaiz = {
                "/declaracionrecolector",
                "/declaracionarmador",
                "/declaracionarea",
                "/declaracioncomercializador",
                "/api/declaracion-planta-produccion",
                "/api/declaracion-planta-destino"
        };
        for (String ruta : listadosRaiz) {
            mockMvc.perform(get(ruta).with(user(RUT_DUENO).roles("USER")))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("ZS.4: Roles de fiscalización (FISCALIZADOR, ADMIN, AUDITOR) acceden con 200 a declaraciones de otros")
    void fiscalizacion_retorna200() throws Exception {
        for (String rol : new String[]{"FISCALIZADOR", "ADMIN", "AUDITOR"}) {
            mockMvc.perform(get("/declaracionrecolector/usuario/" + dueno.getId()).with(user("fisc").roles(rol)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/declaracionrecolector").param("page", "0").with(user("fisc").roles(rol)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/declaracioncomercializador/usuariosdestinatarios/" + dest.getId()).with(user("fisc").roles(rol)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/declaracion-planta-destino/" + dDest.getId()).with(user("fisc").roles(rol)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("ZS.4: Catálogos públicos y consulta ciudadana siguen en 200 sin token; apagado de declaracion_lectura_exige_dueno funciona")
    void catalogosYConsultaPublicaIntactos_yToggleLecturaDueno() throws Exception {
        String[] catalogosPublicos = {
                "/perfil",
                "/comuna",
                "/region",
                "/especie",
                "/caleta",
                "/varadero",
                "/planta",
                "/humedadestado",
                "/extracciontipo",
                "/composicion",
                "/api/public/patentes/AB1234"
        };
        for (String ruta : catalogosPublicos) {
            mockMvc.perform(get(ruta).with(anonymous()))
                    .andExpect(status().isOk());
        }

        // Respaldo de emergencia: con declaracion_lectura_exige_dueno=false, otro usuario autenticado puede leer
        configuracionGeneralService.updateConfig("declaracion_lectura_exige_dueno", "false");
        mockMvc.perform(get("/declaracionrecolector/usuario/" + dueno.getId()).with(user(RUT_AJENO).roles("USER")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ZS.4: Con declaracion_escritura_exige_sesion=true, modificar o eliminar sin token responde 401 (incluido comercializador)")
    void escrituraSinSesion_retorna401CuandoParametroEncendido() throws Exception {
        configuracionGeneralService.updateConfig("declaracion_escritura_exige_sesion", "true");

        mockMvc.perform(put("/declaracionrecolector/" + dRec.getId()).with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/declaracionrecolector/" + dRec.getId()).with(anonymous()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/declaracioncomercializador/" + dCom.getId()).with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/declaracioncomercializador/" + dCom.getId()).with(anonymous()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/declaracioncomercializador/" + dCom.getId()).with(user(RUT_AJENO).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/declaracioncomercializador/" + dCom.getId()).with(user(RUT_AJENO).roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/declaracion-planta-destino/" + dDest.getId()).with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/declaracion-planta-destino/" + dDest.getId()).with(anonymous()))
                .andExpect(status().isUnauthorized());
    }
}
