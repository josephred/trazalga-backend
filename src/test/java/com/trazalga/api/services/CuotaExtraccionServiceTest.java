package com.trazalga.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.models.AmerbModel;
import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.MacrozonaModel;
import com.trazalga.api.models.ProvinciaModel;
import com.trazalga.api.models.RegionModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IAmerbRepository;
import com.trazalga.api.repositories.IComunaRepository;
import com.trazalga.api.repositories.ICuotaExtraccionRepository;
import com.trazalga.api.repositories.IEspecieRepository;
import com.trazalga.api.repositories.IExtraccionTipoRepository;
import com.trazalga.api.repositories.IHumedadEstadoRepository;
import com.trazalga.api.repositories.IMacrozonaRepository;
import com.trazalga.api.repositories.IProvinciaRepository;
import com.trazalga.api.repositories.IRegionRepository;
import com.trazalga.api.repositories.IUsuarioRepository;
import com.trazalga.api.services.CuotaExtraccionService.EvaluacionCuotaResult;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

@ExtendWith(MockitoExtension.class)
public class CuotaExtraccionServiceTest {

    @Mock
    private ICuotaExtraccionRepository cuotaRepository;

    @Mock
    private IRegionRepository regionRepository;

    @Mock
    private IMacrozonaRepository macrozonaRepository;

    @Mock
    private MacrozonaService macrozonaService;

    @Mock
    private IProvinciaRepository provinciaRepository;

    @Mock
    private IComunaRepository comunaRepository;

    @Mock
    private IEspecieRepository especieRepository;

    @Mock
    private IExtraccionTipoRepository extraccionTipoRepository;

    @Mock
    private IHumedadEstadoRepository humedadEstadoRepository;

    @Mock
    private IAmerbRepository amerbRepository;

    @Mock
    private IUsuarioRepository usuarioRepository;

    @Mock
    private AmerbEspecieHabilitadaService amerbEspecieHabilitadaService;

    @Mock
    private FactorConversionService factorConversionService;

    @Mock
    private ConfiguracionGeneralService configuracionGeneralService;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private CuotaExtraccionService cuotaExtraccionService;

    private EspecieModel especieHuiro;
    private RegionModel regionCoquimbo;
    private ProvinciaModel provinciaElqui;
    private ComunaModel comunaLaSerena;
    private MacrozonaModel macrozonaNorte;
    private MacrozonaModel macrozonaNacional;

    @BeforeEach
    void setUp() {
        cuotaExtraccionService.invalidarCacheConsumo();

        especieHuiro = new EspecieModel();
        especieHuiro.setId(1L);
        especieHuiro.setNombre("Huiro Negro");

        regionCoquimbo = new RegionModel();
        regionCoquimbo.setId(4L);
        regionCoquimbo.setNombre("Coquimbo");
        regionCoquimbo.setCodigo("04");

        provinciaElqui = new ProvinciaModel();
        provinciaElqui.setId(41L);
        provinciaElqui.setNombre("Elqui");
        provinciaElqui.setRegion(regionCoquimbo);

        comunaLaSerena = new ComunaModel();
        comunaLaSerena.setId(4101L);
        comunaLaSerena.setNombre("La Serena");
        comunaLaSerena.setRegion(regionCoquimbo);
        comunaLaSerena.setProvincia(provinciaElqui);

        macrozonaNorte = new MacrozonaModel();
        macrozonaNorte.setId(10L);
        macrozonaNorte.setNombre("Zona Norte");
        macrozonaNorte.setCodigo("MZ-NORTE");
        macrozonaNorte.setEsNacional(false);

        macrozonaNacional = new MacrozonaModel();
        macrozonaNacional.setId(99L);
        macrozonaNacional.setNombre("Nacional");
        macrozonaNacional.setCodigo("MZ-NAC");
        macrozonaNacional.setEsNacional(true);
    }

    @Test
    void testCalcularEspecificidadCuota7Niveles() {
        CuotaExtraccionModel cUsuario = new CuotaExtraccionModel();
        cUsuario.setUsuario(new UsuarioModel());
        assertEquals(64, cuotaExtraccionService.calcularEspecificidadCuota(cUsuario));

        CuotaExtraccionModel cAmerb = new CuotaExtraccionModel();
        cAmerb.setAmerb(new AmerbModel());
        assertEquals(32, cuotaExtraccionService.calcularEspecificidadCuota(cAmerb));

        CuotaExtraccionModel cComuna = new CuotaExtraccionModel();
        cComuna.setComuna(comunaLaSerena);
        assertEquals(16, cuotaExtraccionService.calcularEspecificidadCuota(cComuna));

        CuotaExtraccionModel cProvincia = new CuotaExtraccionModel();
        cProvincia.setProvincia(provinciaElqui);
        assertEquals(8, cuotaExtraccionService.calcularEspecificidadCuota(cProvincia));

        CuotaExtraccionModel cRegion = new CuotaExtraccionModel();
        cRegion.setRegion(regionCoquimbo);
        assertEquals(4, cuotaExtraccionService.calcularEspecificidadCuota(cRegion));

        CuotaExtraccionModel cMacrozona = new CuotaExtraccionModel();
        cMacrozona.setMacrozona(macrozonaNorte);
        assertEquals(2, cuotaExtraccionService.calcularEspecificidadCuota(cMacrozona));

        CuotaExtraccionModel cNacional = new CuotaExtraccionModel();
        cNacional.setMacrozona(macrozonaNacional);
        assertEquals(1, cuotaExtraccionService.calcularEspecificidadCuota(cNacional));

        CuotaExtraccionModel cGlobal = new CuotaExtraccionModel();
        assertEquals(1, cuotaExtraccionService.calcularEspecificidadCuota(cGlobal));
    }

    @Test
    void testDescribirAlcanceMacrozonaYNacional() {
        CuotaExtraccionModel cMacrozona = new CuotaExtraccionModel();
        cMacrozona.setMacrozona(macrozonaNorte);
        assertEquals("Macrozona Zona Norte", cuotaExtraccionService.describirAlcance(cMacrozona));

        CuotaExtraccionModel cNacional = new CuotaExtraccionModel();
        cNacional.setMacrozona(macrozonaNacional);
        assertEquals("Nacional", cuotaExtraccionService.describirAlcance(cNacional));
    }

    @Test
    void testValidarContraAmbitosSuperiores_MacrozonaSuperaNacional_LanzaExcepcion() {
        CuotaExtraccionModel cuotaNacional = new CuotaExtraccionModel();
        cuotaNacional.setId(1L);
        cuotaNacional.setMacrozona(macrozonaNacional);
        cuotaNacional.setEspecie(especieHuiro);
        cuotaNacional.setPeriodo("ANUAL");
        cuotaNacional.setLimiteKg(100000.0);
        cuotaNacional.setActivo(true);

        when(cuotaRepository.findByActivoTrue())
                .thenReturn(List.of(cuotaNacional));

        CuotaExtraccionModel cuotaMacrozona = new CuotaExtraccionModel();
        cuotaMacrozona.setPerfil("RECOLECTOR");
        cuotaMacrozona.setMacrozona(macrozonaNorte);
        cuotaMacrozona.setEspecie(especieHuiro);
        cuotaMacrozona.setPeriodo("ANUAL");
        cuotaMacrozona.setLimiteKg(150000.0); // 150.000 kg > 100.000 kg Nacional!
        cuotaMacrozona.setActivo(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                cuotaExtraccionService.validarJerarquia(cuotaMacrozona));

        assertTrue(ex.getMessage().contains("no puede superar la cuota nacional"));
    }

    @Test
    void testValidarContraAmbitosSuperiores_RegionSuperaMacrozona_LanzaExcepcion() {
        CuotaExtraccionModel cuotaMacrozona = new CuotaExtraccionModel();
        cuotaMacrozona.setId(1L);
        cuotaMacrozona.setMacrozona(macrozonaNorte);
        cuotaMacrozona.setEspecie(especieHuiro);
        cuotaMacrozona.setPeriodo("ANUAL");
        cuotaMacrozona.setLimiteKg(50000.0);
        cuotaMacrozona.setActivo(true);

        when(cuotaRepository.findByActivoTrue())
                .thenReturn(List.of(cuotaMacrozona));
        when(macrozonaService.getMacrozonasForRegion(eq(4L), any(Date.class)))
                .thenReturn(List.of(macrozonaNorte));

        CuotaExtraccionModel cuotaRegion = new CuotaExtraccionModel();
        cuotaRegion.setPerfil("RECOLECTOR");
        cuotaRegion.setRegion(regionCoquimbo);
        cuotaRegion.setEspecie(especieHuiro);
        cuotaRegion.setPeriodo("ANUAL");
        cuotaRegion.setLimiteKg(60000.0); // 60.000 kg > 50.000 kg Macrozona!
        cuotaRegion.setActivo(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                cuotaExtraccionService.validarJerarquia(cuotaRegion));

        assertTrue(ex.getMessage().contains("no puede superar la cuota macrozonal"));
    }

    @Test
    void testEvaluacionConcurrente_MacrozonaAgotadaBloqueaDeclaracion() {
        // Cuota 1: Comunal La Serena (10.000 kg)
        CuotaExtraccionModel cComuna = new CuotaExtraccionModel();
        cComuna.setId(101L);
        cComuna.setPerfil("RECOLECTOR");
        cComuna.setEspecie(especieHuiro);
        cComuna.setComuna(comunaLaSerena);
        cComuna.setPeriodo("MENSUAL");
        cComuna.setLimiteKg(10000.0);
        cComuna.setMetrica("CAPTURA");
        cComuna.setEstado("ABIERTA");
        cComuna.setActivo(true);

        // Cuota 2: Macrozona Norte (50.000 kg)
        CuotaExtraccionModel cMacrozona = new CuotaExtraccionModel();
        cMacrozona.setId(102L);
        cMacrozona.setPerfil("RECOLECTOR");
        cMacrozona.setEspecie(especieHuiro);
        cMacrozona.setMacrozona(macrozonaNorte);
        cMacrozona.setPeriodo("MENSUAL");
        cMacrozona.setLimiteKg(50000.0);
        cMacrozona.setMetrica("CAPTURA");
        cMacrozona.setEstado("ABIERTA");
        cMacrozona.setActivo(true);

        when(cuotaRepository.findByActivoTrue()).thenReturn(List.of(cComuna, cMacrozona));
        when(comunaRepository.findById(4101L)).thenReturn(Optional.of(comunaLaSerena));
        when(macrozonaService.isRegionInMacrozona(eq(10L), eq(4L), any(Date.class))).thenReturn(true);
        when(configuracionGeneralService.getValor(eq("cuota_accion_exceso_limite"), anyString()))
                .thenReturn("BLOQUEO_DECLARACION");

        // Mock queries for consumption:
        // Comunal consumed: 1.000 kg (10%)
        // Macrozona consumed: 49.500 kg (99%)
        Query queryComuna = mock(Query.class);
        when(queryComuna.getSingleResult()).thenReturn("1000.00");

        Query queryMacrozona = mock(Query.class);
        when(queryMacrozona.getSingleResult()).thenReturn("49500.00");

        when(entityManager.createNativeQuery(anyString()))
                .thenReturn(queryComuna)
                .thenReturn(queryMacrozona);

        // Intento de declaración de 1.000 kg
        // Parámetros: perfil, usuarioId, amerbId, especieId, extraccionTipoId, comunaImputacionId, fechaExtraccion, fechaDeclaracion, desembarqueKg, capturaKg
        // Comuna: 1.000 + 1.000 = 2.000 kg <= 10.000 kg (OK)
        // Macrozona: 49.500 + 1.000 = 50.500 kg > 50.000 kg (EXCEDE Y BLOQUEA!)
        EvaluacionCuotaResult res = cuotaExtraccionService.evaluarCuotaDeclaracion(
                "RECOLECTOR", 55L, null, 1L, null, 4101L,
                null, new Date(), new BigDecimal("1000.00"), new BigDecimal("1000.00"));

        assertFalse(res.isPermite(), "La declaración debe ser rechazada");
        assertTrue(res.isBloquear(), "Debe marcar bandera de bloqueo");
        assertEquals("CUOTA_EXCEDIDA", res.getMarca());
        assertNotNull(res.getCuotaAplicada(), "Debe citar la cuota que causó el bloqueo");
        assertEquals(102L, res.getCuotaAplicada().getId(), "La cuota causante del bloqueo debe ser la de Macrozona");
        assertTrue(res.getMensaje().contains("Macrozona Zona Norte"), "El mensaje debe indicar la cuota macrozonal sobrepasada");
    }

    @Test
    void testEvaluacionConcurrente_RegionFueraDeVigenciaMacrozona_NoAplicaCuotaMacrozona() {
        CuotaExtraccionModel cMacrozona = new CuotaExtraccionModel();
        cMacrozona.setId(102L);
        cMacrozona.setPerfil("RECOLECTOR");
        cMacrozona.setEspecie(especieHuiro);
        cMacrozona.setMacrozona(macrozonaNorte);
        cMacrozona.setPeriodo("MENSUAL");
        cMacrozona.setLimiteKg(50000.0);
        cMacrozona.setMetrica("CAPTURA");
        cMacrozona.setEstado("ABIERTA");
        cMacrozona.setActivo(true);

        when(cuotaRepository.findByActivoTrue()).thenReturn(List.of(cMacrozona));
        when(comunaRepository.findById(4101L)).thenReturn(Optional.of(comunaLaSerena));
        // Region 4 no está vigente en Macrozona 10 para la fecha evaluada
        when(macrozonaService.isRegionInMacrozona(eq(10L), eq(4L), any(Date.class))).thenReturn(false);

        EvaluacionCuotaResult res = cuotaExtraccionService.evaluarCuotaDeclaracion(
                "RECOLECTOR", 55L, null, 1L, null, 4101L,
                null, new Date(), new BigDecimal("1000.00"), new BigDecimal("1000.00"));

        assertTrue(res.isPermite(), "Al no aplicar la macrozona por vigencia temporal, se permite la declaración");
        assertFalse(res.isBloquear());
        assertEquals("No aplica cuota de extracción.", res.getMensaje());
    }

    @Test
    void testControlCuotasDiarioGlobal_ConHumedadSeco_AplicaLimiteEfectivoYEquivalencia() {
        com.trazalga.api.models.HumedadEstadoModel estadoSeco = new com.trazalga.api.models.HumedadEstadoModel();
        estadoSeco.setId(4L);
        estadoSeco.setNombre("Seco");

        CuotaExtraccionModel cuotaMensual = new CuotaExtraccionModel();
        cuotaMensual.setId(201L);
        cuotaMensual.setPerfil("RECOLECTOR");
        cuotaMensual.setEspecie(especieHuiro);
        cuotaMensual.setComuna(comunaLaSerena);
        cuotaMensual.setNivelAgregacion("COMUNA");
        cuotaMensual.setPeriodo("MENSUAL");
        cuotaMensual.setLimiteKg(5000.0); // 5.000 kg secos nominales
        cuotaMensual.setHumedadEstado(estadoSeco);
        cuotaMensual.setMetrica("CAPTURA");
        cuotaMensual.setEstado("ABIERTA");
        cuotaMensual.setActivo(true);

        com.trazalga.api.models.FactorConversionModel factor358 = new com.trazalga.api.models.FactorConversionModel();
        factor358.setFactor(new BigDecimal("3.5800"));

        when(cuotaRepository.findByPerfilAndActivoTrue("RECOLECTOR")).thenReturn(List.of(cuotaMensual));
        when(factorConversionService.findFactorVigente(eq(1L), eq(4L), any(Date.class)))
                .thenReturn(Optional.of(factor358));

        // Consumo simulado: 3.580 kg de captura (correspondiente a 1.000 kg secos extraídos)
        Query queryConsumo = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(queryConsumo);
        when(queryConsumo.getSingleResult()).thenReturn(new BigDecimal("3580.00"));

        Date fecha = new Date();
        List<com.trazalga.api.dto.ControlCuotaDiariaDTO> dtos = cuotaExtraccionService.getControlCuotasDiarioGlobal(
                fecha, fecha, "MENSUAL", "RECOLECTOR");

        assertNotNull(dtos);
        assertEquals(1, dtos.size());
        com.trazalga.api.dto.ControlCuotaDiariaDTO dto = dtos.get(0);

        assertEquals("Huiro Negro", dto.getEspecieNombre());
        assertEquals(new BigDecimal("3580.00"), dto.getVolumenExtraido());
        assertEquals(new BigDecimal("5000.0"), dto.getLimiteNominal());
        assertEquals(new BigDecimal("17900.00"), dto.getLimiteEfectivo());
        assertEquals(new BigDecimal("17900.00"), dto.getLimiteCuota()); // Coincide con límite efectivo
        // Porcentaje: 3.580 / 17.900 = 20.0%
        assertEquals(20.0, dto.getPorcentajeUso());
        assertEquals("Seco", dto.getHumedadEstadoNombre());
        assertEquals(new BigDecimal("3.5800"), dto.getFactorConversion());
        assertNotNull(dto.getDescripcionEquivalencia());
        assertTrue(dto.getDescripcionEquivalencia().contains("5.000 kg seco ≡ 17.900 kg captura"));
    }

    @Test
    void testValidarDatosBasicos_MetricaNullOBlanca_DefaultsToCaptura() {
        CuotaExtraccionModel c = new CuotaExtraccionModel();
        c.setPerfil("RECOLECTOR");
        c.setPeriodo("MENSUAL");
        c.setLimiteKg(1000.0);
        c.setMetrica(null);
        c.setActivo(false); // para obviar validacion jerarquica compleja

        when(cuotaRepository.save(any(CuotaExtraccionModel.class))).thenAnswer(inv -> inv.getArgument(0));

        CuotaExtraccionModel guardada = cuotaExtraccionService.save(c);
        assertEquals("CAPTURA", guardada.getMetrica(), "Debe asignar CAPTURA por defecto cuando metrica es null");
    }

    @Test
    void testValidarDatosBasicos_MetricaDesembarqueSinResolucion_LanzaExcepcionNormativa() {
        CuotaExtraccionModel c = new CuotaExtraccionModel();
        c.setPerfil("RECOLECTOR");
        c.setPeriodo("MENSUAL");
        c.setLimiteKg(1000.0);
        c.setMetrica("DESEMBARQUE");
        c.setResolucion(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            cuotaExtraccionService.save(c);
        });

        assertTrue(ex.getMessage().contains("Sernapesca definió que las cuotas se descuentan obligatoriamente con captura biológica corregida"));
        assertTrue(ex.getMessage().contains("campo resolución obligatorio"));
    }

    @Test
    void testValidarDatosBasicos_MetricaDesembarqueConResolucionEnBlanco_LanzaExcepcionNormativa() {
        CuotaExtraccionModel c = new CuotaExtraccionModel();
        c.setPerfil("RECOLECTOR");
        c.setPeriodo("MENSUAL");
        c.setLimiteKg(1000.0);
        c.setMetrica("DESEMBARQUE");
        c.setResolucion("    ");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            cuotaExtraccionService.save(c);
        });

        assertTrue(ex.getMessage().contains("campo resolución obligatorio"));
    }

    @Test
    void testValidarDatosBasicos_MetricaDesembarqueConResolucionValida_PermiteGuardar() {
        CuotaExtraccionModel c = new CuotaExtraccionModel();
        c.setPerfil("RECOLECTOR");
        c.setPeriodo("MENSUAL");
        c.setLimiteKg(1000.0);
        c.setMetrica("DESEMBARQUE");
        c.setResolucion("Res. Ex. Nº 142/2026 Subpesca");
        c.setActivo(false);

        when(cuotaRepository.save(any(CuotaExtraccionModel.class))).thenAnswer(inv -> inv.getArgument(0));

        CuotaExtraccionModel guardada = cuotaExtraccionService.save(c);
        assertEquals("DESEMBARQUE", guardada.getMetrica());
        assertEquals("Res. Ex. Nº 142/2026 Subpesca", guardada.getResolucion());
    }

    @Test
    void testValidarDatosBasicos_MetricaInvalida_LanzaExcepcion() {
        CuotaExtraccionModel c = new CuotaExtraccionModel();
        c.setPerfil("RECOLECTOR");
        c.setPeriodo("MENSUAL");
        c.setLimiteKg(1000.0);
        c.setMetrica("OTRA_METRICA");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            cuotaExtraccionService.save(c);
        });

        assertTrue(ex.getMessage().contains("Métrica inválida"));
    }
}
