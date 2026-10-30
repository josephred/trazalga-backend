package com.trazalga.api.services.indicadores;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

import com.trazalga.api.IntegracionBase;
import com.trazalga.api.dto.SubreporteHuiroPaloDTO;
import com.trazalga.api.models.CaletaModel;
import com.trazalga.api.models.ComposicionModel;
import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.ExtraccionTipoModel;
import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.models.ProvinciaModel;
import com.trazalga.api.models.RegionModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.ICaletaRepository;
import com.trazalga.api.repositories.IComposicionRepository;
import com.trazalga.api.repositories.IComunaRepository;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;
import com.trazalga.api.repositories.IEspecieRepository;
import com.trazalga.api.repositories.IExtraccionTipoRepository;
import com.trazalga.api.repositories.IHumedadEstadoRepository;
import com.trazalga.api.repositories.IProvinciaRepository;
import com.trazalga.api.repositories.IRegionRepository;
import com.trazalga.api.repositories.IUsuarioRepository;
import com.trazalga.api.services.ConfiguracionGeneralService;

/**
 * Refinamiento 23-10 · punto 3 (integración, H2 perfil "it"): el Subreporte Huiro Palo suma la captura
 * corregida del huiro palo barreteado declarado sin fronda y estima la fronda no declarada.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SubreporteHuiroPaloIT extends IntegracionBase {

    @Autowired private SubreporteHuiroPaloService service;
    @Autowired private ConfiguracionGeneralService configuracionGeneralService;

    @Autowired private IDeclaracionRecolectorRepository declaracionRecolectorRepository;
    @Autowired private IEspecieRepository especieRepository;
    @Autowired private IExtraccionTipoRepository extraccionTipoRepository;
    @Autowired private IComposicionRepository composicionRepository;
    @Autowired private IHumedadEstadoRepository humedadEstadoRepository;
    @Autowired private IRegionRepository regionRepository;
    @Autowired private IProvinciaRepository provinciaRepository;
    @Autowired private IComunaRepository comunaRepository;
    @Autowired private ICaletaRepository caletaRepository;
    @Autowired private IUsuarioRepository usuarioRepository;

    private static final LocalDate DESDE = LocalDate.of(2026, 10, 1);
    private static final LocalDate HASTA = LocalDate.of(2026, 10, 31);

    private ComunaModel comuna;
    private CaletaModel caleta;
    private EspecieModel huiroPalo;
    private EspecieModel huiroNegro;
    private ExtraccionTipoModel barreteado;
    private ExtraccionTipoModel varado;
    private ComposicionModel sinFronda;
    private ComposicionModel conFronda;
    private HumedadEstadoModel humedo;
    private UsuarioModel recolector;
    private UsuarioModel planta;

    @BeforeEach
    void setUp() {
        configuracionGeneralService.updateConfig("subreporte_huiro_palo_activo", "true");
        configuracionGeneralService.updateConfig("subreporte_huiro_palo_especie", "Huiro palo IT24");
        configuracionGeneralService.updateConfig("subreporte_huiro_palo_metodo", "BARRETEADO");
        configuracionGeneralService.updateConfig("subreporte_huiro_palo_composicion", "SIN FRONDA IT24");
        configuracionGeneralService.updateConfig("subreporte_huiro_palo_factor_pct", "22");
        configuracionGeneralService.updateConfig("subreporte_huiro_palo_formula", "SOBRE_SIN_FRONDA");
        configuracionGeneralService.updateConfig("cuota_fecha_imputacion", "EXTRACCION");

        barreteado = metodo("Barreteado");
        varado = metodo("Varado");

        if (comuna == null) {
            RegionModel region = regionRepository.saveAndFlush(RegionModel.builder().nombre("Coquimbo IT24").codigo("04").build());
            ProvinciaModel provincia = provinciaRepository.saveAndFlush(ProvinciaModel.builder().nombre("Elqui IT24").region(region).build());
            comuna = comunaRepository.saveAndFlush(ComunaModel.builder().nombre("La Higuera IT24").region(region).provincia(provincia).build());
            caleta = caletaRepository.saveAndFlush(CaletaModel.builder().nombre("Punta Choros IT24").region(region).comuna(comuna).build());
            huiroPalo = especieRepository.saveAndFlush(EspecieModel.builder().nombre("Huiro palo IT24").build());
            huiroNegro = especieRepository.saveAndFlush(EspecieModel.builder().nombre("Huiro negro IT24").build());
            sinFronda = composicionRepository.saveAndFlush(ComposicionModel.builder().nombre("Sin frondas IT24").build());
            conFronda = composicionRepository.saveAndFlush(ComposicionModel.builder().nombre("Con frondas IT24").build());
            humedo = humedadEstadoRepository.saveAndFlush(HumedadEstadoModel.builder().nombre("Húmedo IT24").build());
            recolector = usuarioRepository.saveAndFlush(UsuarioModel.builder()
                    .rut("14.141.414-4").nombres("Recolector").apellidop("Prueba IT24").comuna(comuna).estado("ACTIVO").build());
            planta = usuarioRepository.saveAndFlush(UsuarioModel.builder()
                    .rut("15.151.515-5").nombres("Planta").apellidop("Prueba IT24")
                    .correo("planta-it24@trazalga.it").estado("ACTIVO").build());
        }
    }

    @AfterEach
    void tearDown() {
        declaracionRecolectorRepository.deleteAll();
        configuracionGeneralService.updateConfig("subreporte_huiro_palo_especie", "HUIRO PALO");
        configuracionGeneralService.updateConfig("subreporte_huiro_palo_composicion", "SIN FRONDA");
        configuracionGeneralService.updateConfig("subreporte_huiro_palo_formula", "SOBRE_SIN_FRONDA");
        configuracionGeneralService.updateConfig("cuota_fecha_imputacion", "EXTRACCION");
    }

    @Test
    @DisplayName("Suma la captura del barreteado sin fronda, cuenta el imputado a varado y deja fuera lo que no aplica")
    void subreporte_sumaSoloBarreteadoSinFronda() {
        declarar(huiroPalo, barreteado, sinFronda, "1000.00", "2026-10-05", "2026-10-05", null);
        // Barreteado en veda imputado a la cuota de varado: cuenta igual (método declarado)
        declarar(huiroPalo, barreteado, sinFronda, "500.00", "2026-10-12", "2026-10-12", varado.getId());
        // No aplican
        declarar(huiroPalo, barreteado, conFronda, "9000.00", "2026-10-06", "2026-10-06", null);
        declarar(huiroPalo, barreteado, null, "700.00", "2026-10-07", "2026-10-07", null);
        declarar(huiroPalo, varado, sinFronda, "800.00", "2026-10-08", "2026-10-08", null);
        declarar(huiroNegro, barreteado, sinFronda, "600.00", "2026-10-09", "2026-10-09", null);
        declarar(huiroPalo, barreteado, sinFronda, "400.00", "2026-09-30", "2026-09-30", null); // fuera del rango

        SubreporteHuiroPaloDTO dto = service.calcular(DESDE, HASTA);

        assertTrue(dto.isActivo());
        assertTrue(dto.isConfiguracionValida(), dto.getMensaje());
        assertEquals("EXTRACCION", dto.getCriterioFecha());
        assertEquals(2, dto.getDeclaraciones());
        assertEquals(1500.0, dto.getCapturaSinFrondaKg(), 0.001);
        assertEquals(330.0, dto.getSubreporteKg(), 0.001);
        assertEquals(1830.0, dto.getCapturaEstimadaKg(), 0.001);
        assertEquals(1, dto.getDeclaracionesSinComposicion());
        assertEquals(700.0, dto.getCapturaSinComposicionKg(), 0.001);
        assertEquals(1, dto.getDeclaracionesOtraComposicion());
        assertEquals(9000.0, dto.getCapturaOtraComposicionKg(), 0.001);

        assertEquals(1, dto.getPorComuna().size());
        assertEquals("La Higuera IT24", dto.getPorComuna().get(0).getEtiqueta());
        assertEquals("2026-10", dto.getPorMes().get(0).getClave());
        assertEquals("Recolector de orilla", dto.getPorPerfil().get(0).getEtiqueta());
    }

    @Test
    @DisplayName("Con la fórmula sobre el total, 1.000 kg sin fronda estiman 282,05 kg de fronda")
    void subreporte_formulaSobreTotal() {
        configuracionGeneralService.updateConfig("subreporte_huiro_palo_formula", "SOBRE_TOTAL");
        declarar(huiroPalo, barreteado, sinFronda, "1000.00", "2026-10-05", "2026-10-05", null);

        SubreporteHuiroPaloDTO dto = service.calcular(DESDE, HASTA);

        assertEquals("SOBRE_TOTAL", dto.getFormula());
        assertEquals(282.05, dto.getSubreporteKg(), 0.001);
    }

    @Test
    @DisplayName("El rango usa la fecha de extracción o la de declaración, según cuota_fecha_imputacion")
    void subreporte_criterioDeFecha() {
        declarar(huiroPalo, barreteado, sinFronda, "1000.00", "2026-09-28", "2026-10-02", null);

        assertEquals(0, service.calcular(DESDE, HASTA).getDeclaraciones(), "Extraída en septiembre");

        configuracionGeneralService.updateConfig("cuota_fecha_imputacion", "DECLARACION");
        SubreporteHuiroPaloDTO dto = service.calcular(DESDE, HASTA);
        assertEquals("DECLARACION", dto.getCriterioFecha());
        assertEquals(1, dto.getDeclaraciones(), "Declarada en octubre");
    }

    @Test
    @DisplayName("Si la composición configurada no existe, lo informa y no calcula")
    void subreporte_composicionInexistente() {
        configuracionGeneralService.updateConfig("subreporte_huiro_palo_composicion", "SIN TALLO");

        SubreporteHuiroPaloDTO dto = service.calcular(DESDE, HASTA);

        assertFalse(dto.isConfiguracionValida());
        assertTrue(dto.getMensaje().contains("no hay una composición «SIN TALLO»"), dto.getMensaje());
    }

    @Test
    @DisplayName("Desactivado en Administración: no calcula")
    void subreporte_desactivado() {
        configuracionGeneralService.updateConfig("subreporte_huiro_palo_activo", "false");

        SubreporteHuiroPaloDTO dto = service.calcular(DESDE, HASTA);

        assertFalse(dto.isActivo());
        assertFalse(dto.isConfiguracionValida());
        assertTrue(dto.getMensaje().contains("desactivado"));
    }

    @Test
    @DisplayName("Un rango con 'hasta' anterior a 'desde' se rechaza")
    void subreporte_rangoInvalido() {
        assertThrows(IllegalArgumentException.class, () -> service.calcular(HASTA, DESDE));
    }

    // -------------------------------------------------------------------------

    private ExtraccionTipoModel metodo(String nombre) {
        return extraccionTipoRepository.findAll().stream()
                .filter(m -> nombre.equals(m.getNombre()))
                .findFirst()
                .orElseGet(() -> extraccionTipoRepository.saveAndFlush(ExtraccionTipoModel.builder().nombre(nombre).build()));
    }

    private void declarar(EspecieModel especie, ExtraccionTipoModel metodo, ComposicionModel composicion, String capturaKg,
                          String fechaExtraccion, String fechaDeclaracion, Long imputacionId) {
        declaracionRecolectorRepository.saveAndFlush(DeclaracionRecolectorModel.builder()
                .usuario(recolector)
                .especie(especie)
                .extraccionTipo(metodo)
                .extraccionTipoImputacionId(imputacionId)
                .composicion(composicion)
                .humedadEstado(humedo)
                .comuna(comuna)
                .caleta(caleta)
                .fechaExtraccion(Date.valueOf(fechaExtraccion))
                .fechaDeclaracion(Date.valueOf(fechaDeclaracion))
                .hora("10:00:00")
                .nombre("Recolector Prueba IT24")
                .codigoSernapesca("RPA-IT24")
                .folioDesembarqueRo("RO-IT24-" + System.nanoTime())
                .codigoDestinatario("DEST-IT24")
                .nombreDestinatario("Planta Prueba IT24")
                .usuarioDestinatario(planta)
                .desembarque(new BigDecimal(capturaKg))
                .captura(new BigDecimal(capturaKg))
                .build());
    }
}
