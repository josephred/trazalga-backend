package com.trazalga.api.services.cuotas;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

import com.trazalga.api.IntegracionBase;
import com.trazalga.api.dto.ResumenImputacionCruzadaDTO;
import com.trazalga.api.models.CaletaModel;
import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.models.DeclaracionMarcaModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.ExtraccionTipoModel;
import com.trazalga.api.models.FactorConversionModel;
import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.models.PerfilModel;
import com.trazalga.api.models.ProvinciaModel;
import com.trazalga.api.models.RegionModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.models.VedaEspecieModel;
import com.trazalga.api.repositories.IAvisoEnviadoRepository;
import com.trazalga.api.repositories.ICaletaRepository;
import com.trazalga.api.repositories.IComunaRepository;
import com.trazalga.api.repositories.ICuotaExtraccionEventoRepository;
import com.trazalga.api.repositories.ICuotaExtraccionRepository;
import com.trazalga.api.repositories.IDeclaracionMarcaRepository;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;
import com.trazalga.api.repositories.IEspecieRepository;
import com.trazalga.api.repositories.IExtraccionTipoRepository;
import com.trazalga.api.repositories.IFactorConversionRepository;
import com.trazalga.api.repositories.IHumedadEstadoRepository;
import com.trazalga.api.repositories.IPerfilRepository;
import com.trazalga.api.repositories.IProvinciaRepository;
import com.trazalga.api.repositories.IRegionRepository;
import com.trazalga.api.repositories.IUsuarioRepository;
import com.trazalga.api.repositories.VedaEspecieRepository;
import com.trazalga.api.services.ConfiguracionGeneralService;
import com.trazalga.api.services.CuotaExtraccionService;
import com.trazalga.api.services.CadenaCustodiaService;
import com.trazalga.api.services.DeclaracionRecolectorService;
import com.trazalga.api.services.FichaTrazabilidadService;

/**
 * Refinamiento 23-10 · punto 1 (integración, H2 perfil "it"):
 * un recolector declara huiro negro por barreteado en octubre, con el barreteado en veda y sin cuota de
 * barreteado en su comuna. La alerta EN_VEDA se registra y la captura corregida se imputa a la cuota de
 * varado de la comuna y el mes, que la descuenta (y genera CUOTA_EXCEDIDA cuando corresponde).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImputacionCruzadaVedaIT extends IntegracionBase {

    @Autowired private DeclaracionRecolectorService declaracionRecolectorService;
    @Autowired private CuotaExtraccionService cuotaExtraccionService;
    @Autowired private ImputacionCruzadaVedaService imputacionCruzadaVedaService;
    @Autowired private ConfiguracionGeneralService configuracionGeneralService;
    @Autowired private FichaTrazabilidadService fichaTrazabilidadService;
    @Autowired private CadenaCustodiaService cadenaCustodiaService;

    @Autowired private ICuotaExtraccionRepository cuotaRepository;
    @Autowired private ICuotaExtraccionEventoRepository cuotaEventoRepository;
    @Autowired private IDeclaracionRecolectorRepository declaracionRecolectorRepository;
    @Autowired private IDeclaracionMarcaRepository declaracionMarcaRepository;
    @Autowired private IAvisoEnviadoRepository avisoEnviadoRepository;
    @Autowired private IEspecieRepository especieRepository;
    @Autowired private IExtraccionTipoRepository extraccionTipoRepository;
    @Autowired private IHumedadEstadoRepository humedadEstadoRepository;
    @Autowired private IFactorConversionRepository factorConversionRepository;
    @Autowired private VedaEspecieRepository vedaEspecieRepository;
    @Autowired private IRegionRepository regionRepository;
    @Autowired private IProvinciaRepository provinciaRepository;
    @Autowired private IComunaRepository comunaRepository;
    @Autowired private ICaletaRepository caletaRepository;
    @Autowired private IUsuarioRepository usuarioRepository;
    @Autowired private IPerfilRepository perfilRepository;

    private static final Date FECHA_OCTUBRE = Date.valueOf("2026-10-15");
    private static final Date FECHA_EVAL = Date.valueOf("2026-10-20");

    private RegionModel region;
    private ComunaModel comuna;
    private CaletaModel caleta;
    private EspecieModel huiroNegro;
    private ExtraccionTipoModel varado;
    private ExtraccionTipoModel barreteado;
    private HumedadEstadoModel humedo;
    private UsuarioModel recolector;
    private UsuarioModel planta;

    @BeforeEach
    void setUp() {
        configuracionGeneralService.updateConfig("veda_modo_operacion", "ALERTA_FISCALIZACION");
        configuracionGeneralService.updateConfig("cuota_imputacion_veda_activo", "true");
        cuotaExtraccionService.invalidarCacheConsumo();

        varado = metodo("Varado");
        barreteado = metodo("Barreteado");

        if (region == null) {
            region = regionRepository.saveAndFlush(RegionModel.builder().nombre("Coquimbo IT23").codigo("04").build());
            ProvinciaModel provincia = provinciaRepository.saveAndFlush(ProvinciaModel.builder().nombre("Elqui IT23").region(region).build());
            comuna = comunaRepository.saveAndFlush(ComunaModel.builder().nombre("Coquimbo IT23").region(region).provincia(provincia).build());
            caleta = caletaRepository.saveAndFlush(CaletaModel.builder().nombre("Caleta Coquimbo IT23").region(region).comuna(comuna).build());
            huiroNegro = especieRepository.saveAndFlush(EspecieModel.builder().nombre("Huiro negro IT23").build());
            humedo = humedadEstadoRepository.saveAndFlush(HumedadEstadoModel.builder().nombre("Húmedo IT23").build());

            FactorConversionModel factor = new FactorConversionModel();
            factor.setEspecie(huiroNegro);
            factor.setHumedadEstado(humedo);
            factor.setFactor(new BigDecimal("1.1300"));
            factor.setVigenciaInicio(Date.valueOf("2026-01-01"));
            factor.setActivo(true);
            factorConversionRepository.saveAndFlush(factor);

            // Huiro negro: barreteado en veda en octubre (Coquimbo)
            vedaEspecieRepository.saveAndFlush(VedaEspecieModel.builder()
                    .especie(huiroNegro)
                    .region(region)
                    .extraccionTipo(barreteado)
                    .recurrenciaAnual(true)
                    .mesesVeda("10")
                    .resolucion("RES-VEDA-IT23")
                    .activo(true)
                    .build());

            PerfilModel perfilRecolector = perfilRepository.findAll().stream()
                    .filter(p -> "RECOLECTOR".equalsIgnoreCase(p.getNombre()))
                    .findFirst()
                    .orElseGet(() -> {
                        PerfilModel p = new PerfilModel();
                        p.setNombre("RECOLECTOR");
                        return perfilRepository.saveAndFlush(p);
                    });
            recolector = usuarioRepository.saveAndFlush(UsuarioModel.builder()
                    .rut("12.345.678-5").nombres("Recolectora").apellidop("Prueba IT23")
                    .perfil(perfilRecolector).comuna(comuna).estado("ACTIVO").build());
            planta = usuarioRepository.saveAndFlush(UsuarioModel.builder()
                    .rut("11.111.111-1").nombres("Planta").apellidop("Prueba IT23")
                    .correo("planta-it23@trazalga.it").estado("ACTIVO").build());
        }
    }

    @AfterEach
    void tearDown() {
        declaracionMarcaRepository.deleteAll();
        avisoEnviadoRepository.deleteAll();
        declaracionRecolectorRepository.deleteAll();
        cuotaEventoRepository.deleteAll();
        cuotaRepository.deleteAll();
        configuracionGeneralService.updateConfig("cuota_imputacion_veda_activo", "true");
        configuracionGeneralService.updateConfig("veda_modo_operacion", "BLOQUEO_ESTRICTO");
        cuotaExtraccionService.invalidarCacheConsumo();
    }

    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Barreteado en veda sin cuota de barreteado: EN_VEDA y la captura descuenta la cuota de varado")
    void barreteadoEnVeda_imputaALaCuotaDeVarado_yGeneraCuotaExcedida() {
        CuotaExtraccionModel cuotaVarado = cuotaMensual(varado, 1000.0);

        DeclaracionRecolectorModel d1 = declarar(barreteado, "500.00");

        assertEquals(varado.getId(), d1.getExtraccionTipoImputacionId(), "Se imputa a varado");
        assertEquals(barreteado.getId(), d1.getExtraccionTipo().getId(), "El método declarado no se reescribe");
        assertEquals(0, new BigDecimal("565.00").compareTo(d1.getCaptura()), "Captura corregida = 500 x 1,13");

        DeclaracionMarcaModel enVeda = unicaMarca(d1.getId(), "EN_VEDA");
        assertTrue(enVeda.getDetalle().contains("durante veda vigente"), enVeda.getDetalle());
        assertTrue(enVeda.getDetalle().contains("Sin cuota de barreteado para la especie, la comuna y el período: "
                        + "la captura corregida (565,00 kg) se imputa a la cuota de varado Comuna Coquimbo IT23 (ID "
                        + cuotaVarado.getId() + ")."), enVeda.getDetalle());

        assertConsumo(cuotaVarado, "565.00");

        // La ficha (Consultas) y la traza (Reportes) muestran el método declarado y el de imputación
        com.trazalga.api.dto.FichaOrigenDTO origen = fichaTrazabilidadService.obtenerFicha("RECOLECTOR", d1.getId()).getOrigenes().get(0);
        assertEquals("Barreteado", origen.getMetodoExtraccion());
        assertEquals("Varado", origen.getMetodoImputacionCuota());
        com.trazalga.api.dto.TrazabilidadNodoDTO nodo = cadenaCustodiaService.getTrazabilidad("RECOLECTOR", d1.getId()).getNodos().stream()
                .filter(n -> d1.getId().equals(n.getIdDeclaracion()))
                .findFirst().orElseThrow();
        assertEquals("Barreteado", nodo.getMetodoExtraccion());
        assertEquals("Varado", nodo.getMetodoImputacionCuota());

        // La segunda declaración sobrepasa los 1.000 kg de la cuota de varado
        DeclaracionRecolectorModel d2 = declarar(barreteado, "500.00");
        assertEquals(varado.getId(), d2.getExtraccionTipoImputacionId());
        DeclaracionMarcaModel excedida = unicaMarca(d2.getId(), "CUOTA_EXCEDIDA");
        assertEquals(cuotaVarado.getId(), excedida.getReglaId());
        assertConsumo(cuotaVarado, "1130.00");
        assertTrue(declaracionMarcaRepository.findByDeclaracionTipoAndDeclaracionIdAndMarca("RECOLECTOR", d1.getId(), "CUOTA_EXCEDIDA").isEmpty());
    }

    @Test
    @DisplayName("Con cuota de barreteado en la comuna y el mes, el barreteado consume su propia cuota")
    void barreteadoEnVeda_conCuotaDeBarreteado_noSeImputaAVarado() {
        CuotaExtraccionModel cuotaVarado = cuotaMensual(varado, 1000.0);
        CuotaExtraccionModel cuotaBarreteado = cuotaMensual(barreteado, 1000.0);

        DeclaracionRecolectorModel d = declarar(barreteado, "500.00");

        assertNull(d.getExtraccionTipoImputacionId());
        DeclaracionMarcaModel enVeda = unicaMarca(d.getId(), "EN_VEDA");
        assertFalse(enVeda.getDetalle().contains("se imputa"), enVeda.getDetalle());
        assertConsumo(cuotaBarreteado, "565.00");
        assertConsumo(cuotaVarado, "0");
    }

    @Test
    @DisplayName("Sin cuota de barreteado ni de varado: EN_VEDA informa que la captura no se descuenta")
    void barreteadoEnVeda_sinCuotaDeVarado_loInforma() {
        DeclaracionRecolectorModel d = declarar(barreteado, "500.00");

        assertNull(d.getExtraccionTipoImputacionId());
        DeclaracionMarcaModel enVeda = unicaMarca(d.getId(), "EN_VEDA");
        assertTrue(enVeda.getDetalle().contains("Sin cuota de barreteado ni de varado para la especie, la comuna y el período: "
                + "la captura no se descuenta de ninguna cuota."), enVeda.getDetalle());
    }

    @Test
    @DisplayName("Una declaración de varado (sin veda) consume su cuota sin imputación cruzada")
    void varado_sinVeda_consumoNormal() {
        CuotaExtraccionModel cuotaVarado = cuotaMensual(varado, 1000.0);

        DeclaracionRecolectorModel d = declarar(varado, "100.00");

        assertNull(d.getExtraccionTipoImputacionId());
        assertTrue(declaracionMarcaRepository.findByDeclaracionTipoAndDeclaracionIdAndMarca("RECOLECTOR", d.getId(), "EN_VEDA").isEmpty());
        assertConsumo(cuotaVarado, "113.00");
        assertNull(fichaTrazabilidadService.obtenerFicha("RECOLECTOR", d.getId()).getOrigenes().get(0).getMetodoImputacionCuota());
    }

    @Test
    @DisplayName("Editar la declaración mantiene la imputación y no cuenta dos veces la captura")
    void edicion_mantieneImputacion_sinDobleConteo() {
        CuotaExtraccionModel cuotaVarado = cuotaMensual(varado, 1000.0);
        DeclaracionRecolectorModel d = declarar(barreteado, "500.00");

        DeclaracionRecolectorModel cambio = solicitud(barreteado, "600.00");
        DeclaracionRecolectorModel editada = declaracionRecolectorService.updateById(cambio, d.getId());

        assertEquals(varado.getId(), editada.getExtraccionTipoImputacionId());
        assertConsumo(cuotaVarado, "678.00");
        assertTrue(declaracionMarcaRepository.findByDeclaracionTipoAndDeclaracionIdAndMarca("RECOLECTOR", d.getId(), "CUOTA_EXCEDIDA").isEmpty(),
                "678 kg no sobrepasan los 1.000 kg: la captura anterior no se suma");
    }

    @Test
    @DisplayName("Recálculo: completa la imputación de declaraciones guardadas antes de la regla y es idempotente")
    void recalculo_completaDeclaracionesAntiguas_yEsIdempotente() {
        CuotaExtraccionModel cuotaVarado = cuotaMensual(varado, 1000.0);
        // Declaración guardada antes de la regla: sin imputación
        DeclaracionRecolectorModel antigua = declaracionRecolectorRepository.saveAndFlush(solicitud(barreteado, "500.00")
                .setUsuario(recolector)
                .setEspecie(huiroNegro)
                .setExtraccionTipo(barreteado)
                .setHumedadEstado(humedo)
                .setComuna(comuna)
                .setCaleta(caleta)
                .setUsuarioDestinatario(planta)
                .setCaptura(new BigDecimal("565.00")));
        assertNull(antigua.getExtraccionTipoImputacionId());
        assertConsumo(cuotaVarado, "0");

        ResumenImputacionCruzadaDTO previa = imputacionCruzadaVedaService.recalcularImputaciones(
                Date.valueOf("2026-10-01").toLocalDate(), Date.valueOf("2026-10-31").toLocalDate(), false);
        assertEquals(1, previa.getCambios());
        assertEquals(1, previa.getImputacionesCruzadas());
        ResumenImputacionCruzadaDTO.Item item = previa.getDetalle().get(0);
        assertEquals(antigua.getId(), item.getDeclaracionId());
        assertEquals("CRUZADA", item.getEstado());
        assertEquals(varado.getId(), item.getImputacionNuevaId());
        assertNull(declaracionRecolectorRepository.findById(antigua.getId()).orElseThrow().getExtraccionTipoImputacionId(),
                "La vista previa no escribe");

        ResumenImputacionCruzadaDTO aplicada = imputacionCruzadaVedaService.recalcularImputaciones(
                Date.valueOf("2026-10-01").toLocalDate(), Date.valueOf("2026-10-31").toLocalDate(), true);
        assertEquals(1, aplicada.getCambios());
        assertEquals(varado.getId(), declaracionRecolectorRepository.findById(antigua.getId()).orElseThrow().getExtraccionTipoImputacionId());
        assertConsumo(cuotaVarado, "565.00");

        ResumenImputacionCruzadaDTO otraVez = imputacionCruzadaVedaService.recalcularImputaciones(
                Date.valueOf("2026-10-01").toLocalDate(), Date.valueOf("2026-10-31").toLocalDate(), true);
        assertEquals(0, otraVez.getCambios(), "Correrlo dos veces deja lo mismo");

        // El recálculo de hallazgos de la cuota de varado (TC.10) ve la declaración imputada
        com.trazalga.api.dto.ResumenRecalculoCuotaDTO hallazgos = cuotaExtraccionService.recalcularHallazgos(cuotaVarado.getId(), null, null);
        assertEquals(1, hallazgos.getDeclaracionesEvaluadas());
        assertEquals(0, new BigDecimal("565.00").compareTo(hallazgos.getConsumoFinalKg()));

        // Si la regla se apaga, el recálculo devuelve la captura a su método declarado
        configuracionGeneralService.updateConfig("cuota_imputacion_veda_activo", "false");
        ResumenImputacionCruzadaDTO apagada = imputacionCruzadaVedaService.recalcularImputaciones(
                Date.valueOf("2026-10-01").toLocalDate(), Date.valueOf("2026-10-31").toLocalDate(), true);
        assertEquals(1, apagada.getCambios());
        assertFalse(apagada.isReglaActiva());
        assertNull(declaracionRecolectorRepository.findById(antigua.getId()).orElseThrow().getExtraccionTipoImputacionId());
        assertConsumo(cuotaVarado, "0");
    }

    // -------------------------------------------------------------------------

    private ExtraccionTipoModel metodo(String nombre) {
        return extraccionTipoRepository.findAll().stream()
                .filter(m -> nombre.equals(m.getNombre()))
                .findFirst()
                .orElseGet(() -> extraccionTipoRepository.saveAndFlush(ExtraccionTipoModel.builder().nombre(nombre).build()));
    }

    private CuotaExtraccionModel cuotaMensual(ExtraccionTipoModel metodo, double limiteKg) {
        Set<ComunaModel> comunas = new LinkedHashSet<>();
        comunas.add(comuna);
        return cuotaRepository.saveAndFlush(CuotaExtraccionModel.builder()
                .nivelAgregacion("COMUNA")
                .ambito("AREA_LIBRE")
                .perfil("RECOLECTOR")
                .region(region)
                .comuna(comuna)
                .comunas(comunas)
                .especie(huiroNegro)
                .extraccionTipo(metodo)
                .periodo("MENSUAL")
                .fechaInicio(Date.valueOf("2026-10-01"))
                .fechaFin(Date.valueOf("2026-10-31"))
                .limiteKg(limiteKg)
                .metrica("CAPTURA")
                .modoAccion("SOLO_ALERTA")
                .resolucion("RES-CUOTA-IT23")
                .activo(true)
                .estado("ABIERTA")
                .build());
    }

    private DeclaracionRecolectorModel solicitud(ExtraccionTipoModel metodo, String desembarqueKg) {
        return DeclaracionRecolectorModel.builder()
                .usuario(UsuarioModel.builder().id(recolector.getId()).build())
                .especie(EspecieModel.builder().id(huiroNegro.getId()).build())
                .extraccionTipo(ExtraccionTipoModel.builder().id(metodo.getId()).build())
                .humedadEstado(HumedadEstadoModel.builder().id(humedo.getId()).build())
                .comuna(ComunaModel.builder().id(comuna.getId()).build())
                .caleta(CaletaModel.builder().id(caleta.getId()).build())
                .fechaExtraccion(FECHA_OCTUBRE)
                .fechaDeclaracion(FECHA_OCTUBRE)
                .hora("10:00:00")
                .nombre("Recolectora Prueba IT23")
                .codigoSernapesca("RPA-IT23")
                .folioDesembarqueRo("RO-IT23-" + System.nanoTime())
                .codigoDestinatario("DEST-IT23")
                .nombreDestinatario("Planta Prueba IT23")
                .usuarioDestinatario(UsuarioModel.builder().id(planta.getId()).build())
                .desembarque(new BigDecimal(desembarqueKg))
                .build();
    }

    private DeclaracionRecolectorModel declarar(ExtraccionTipoModel metodo, String desembarqueKg) {
        DeclaracionRecolectorModel guardada = declaracionRecolectorService.saveDeclaracionRecolector(solicitud(metodo, desembarqueKg));
        assertNotNull(guardada.getId());
        return guardada;
    }

    private DeclaracionMarcaModel unicaMarca(Long declaracionId, String marca) {
        List<DeclaracionMarcaModel> marcas = declaracionMarcaRepository
                .findByDeclaracionTipoAndDeclaracionIdAndMarca("RECOLECTOR", declaracionId, marca);
        assertEquals(1, marcas.size(), "Se esperaba una marca " + marca + " para la declaración " + declaracionId);
        return marcas.get(0);
    }

    private void assertConsumo(CuotaExtraccionModel cuota, String esperadoKg) {
        cuotaExtraccionService.invalidarCacheConsumo();
        BigDecimal consumo = cuotaExtraccionService.calcularConsumoAcumulado(cuota, FECHA_EVAL);
        assertEquals(0, new BigDecimal(esperadoKg).compareTo(consumo),
                "Consumo de la cuota " + cuota.getId() + ": esperado " + esperadoKg + ", obtenido " + consumo);
    }
}
