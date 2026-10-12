package com.trazalga.api.services.cuotas;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.trazalga.api.IntegracionBase;
import com.trazalga.api.dto.ResumenPlantillaDTO;
import com.trazalga.api.models.CaletaModel;
import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.ExtraccionTipoModel;
import com.trazalga.api.models.FactorConversionModel;
import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.models.PerfilModel;
import com.trazalga.api.models.ProvinciaModel;
import com.trazalga.api.models.RegionModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.ICaletaRepository;
import com.trazalga.api.repositories.IComunaRepository;
import com.trazalga.api.repositories.ICuotaExtraccionRepository;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;
import com.trazalga.api.repositories.IEspecieRepository;
import com.trazalga.api.repositories.IExtraccionTipoRepository;
import com.trazalga.api.repositories.IFactorConversionRepository;
import com.trazalga.api.repositories.IHumedadEstadoRepository;
import com.trazalga.api.repositories.IPerfilRepository;
import com.trazalga.api.repositories.IProvinciaRepository;
import com.trazalga.api.repositories.IRegionRepository;
import com.trazalga.api.repositories.IUsuarioRepository;
import com.trazalga.api.services.CuotaExtraccionService;
import com.trazalga.api.services.CuotaExtraccionService.EvaluacionCuotaResult;

import jakarta.persistence.EntityManager;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Suite de pruebas de integración territorial para el mantenedor de cuotas (TM.4).
 * Ejecuta consultas reales sobre el esquema H2 en perfil 'it' con transacciones reales:
 * 1. Tres comunas (Coquimbo, La Serena, La Higuera) suman 15.000 kg y excluyen a Andacollo (4.000 kg).
 * 2. Provincia de Huasco suma a Freirina (8.000 kg) y excluye a Caldera (6.000 kg, Provincia Copiapó).
 * 3. Plantilla de Antofagasta (TM.2) con factor 3,58 valida sobrepaso y resumenPlantilla.
 */
public class MantenedorCuotasTerritorialIT extends IntegracionBase {

    @Autowired
    private CuotaExtraccionService cuotaExtraccionService;

    @Autowired
    private ConsumoIndividualQuery consumoIndividualQuery;

    @Autowired
    private ICuotaExtraccionRepository cuotaRepository;

    @Autowired
    private IDeclaracionRecolectorRepository declaracionRecolectorRepository;

    @Autowired
    private IEspecieRepository especieRepository;

    @Autowired
    private IExtraccionTipoRepository extraccionTipoRepository;

    @Autowired
    private IHumedadEstadoRepository humedadEstadoRepository;

    @Autowired
    private IFactorConversionRepository factorConversionRepository;

    @Autowired
    private IRegionRepository regionRepository;

    @Autowired
    private IProvinciaRepository provinciaRepository;

    @Autowired
    private IComunaRepository comunaRepository;

    @Autowired
    private ICaletaRepository caletaRepository;

    @Autowired
    private IUsuarioRepository usuarioRepository;

    @Autowired
    private IPerfilRepository perfilRepository;

    @Autowired
    private EntityManager entityManager;

    private PerfilModel perfilRecolector;
    private UsuarioModel destinatarioDefault;

    @BeforeEach
    void setUp() {
        cuotaExtraccionService.invalidarCacheConsumo();

        perfilRecolector = perfilRepository.findAll().stream()
                .filter(p -> "RECOLECTOR".equalsIgnoreCase(p.getNombre()))
                .findFirst()
                .orElseGet(() -> {
                    PerfilModel p = new PerfilModel();
                    p.setNombre("RECOLECTOR");
                    return perfilRepository.saveAndFlush(p);
                });

        destinatarioDefault = usuarioRepository.findByRut("99.999.999-9").orElseGet(() -> {
            UsuarioModel u = UsuarioModel.builder()
                    .rut("99.999.999-9")
                    .nombres("Planta")
                    .apellidop("Procesadora IT")
                    .correo("planta@trazalga.it")
                    .estado("ACTIVO")
                    .build();
            return usuarioRepository.saveAndFlush(u);
        });
    }

    @AfterEach
    void tearDown() {
        cuotaExtraccionService.invalidarCacheConsumo();
        declaracionRecolectorRepository.deleteAll();
        cuotaRepository.deleteAll();
    }

    @Test
    @DisplayName("TM.4 Caso 1: Cuota multicomunal suma Coquimbo, La Serena y La Higuera (15.000 kg) y excluye a Andacollo")
    void testCasoTresComunas_CoquimboLaSerenaLaHiguera_ExcluyeAndacollo() {
        // 1. Maestros territoriales
        RegionModel regionCoquimbo = regionRepository.saveAndFlush(RegionModel.builder()
                .nombre("Región de Coquimbo IT")
                .codigo("04")
                .build());

        ProvinciaModel provinciaElqui = provinciaRepository.saveAndFlush(ProvinciaModel.builder()
                .nombre("Provincia de Elqui IT")
                .region(regionCoquimbo)
                .build());

        ComunaModel comunaCoquimbo = comunaRepository.saveAndFlush(ComunaModel.builder()
                .nombre("Coquimbo IT")
                .region(regionCoquimbo)
                .provincia(provinciaElqui)
                .build());

        ComunaModel comunaLaSerena = comunaRepository.saveAndFlush(ComunaModel.builder()
                .nombre("La Serena IT")
                .region(regionCoquimbo)
                .provincia(provinciaElqui)
                .build());

        ComunaModel comunaLaHiguera = comunaRepository.saveAndFlush(ComunaModel.builder()
                .nombre("La Higuera IT")
                .region(regionCoquimbo)
                .provincia(provinciaElqui)
                .build());

        ComunaModel comunaAndacollo = comunaRepository.saveAndFlush(ComunaModel.builder()
                .nombre("Andacollo IT")
                .region(regionCoquimbo)
                .provincia(provinciaElqui)
                .build());

        CaletaModel caletaCoquimbo = caletaRepository.saveAndFlush(CaletaModel.builder()
                .nombre("Caleta Coquimbo IT")
                .region(regionCoquimbo)
                .comuna(comunaCoquimbo)
                .build());

        CaletaModel caletaLaSerena = caletaRepository.saveAndFlush(CaletaModel.builder()
                .nombre("Caleta La Serena IT")
                .region(regionCoquimbo)
                .comuna(comunaLaSerena)
                .build());

        CaletaModel caletaLaHiguera = caletaRepository.saveAndFlush(CaletaModel.builder()
                .nombre("Caleta La Higuera IT")
                .region(regionCoquimbo)
                .comuna(comunaLaHiguera)
                .build());

        CaletaModel caletaAndacollo = caletaRepository.saveAndFlush(CaletaModel.builder()
                .nombre("Caleta Andacollo IT")
                .region(regionCoquimbo)
                .comuna(comunaAndacollo)
                .build());

        EspecieModel especie = especieRepository.saveAndFlush(EspecieModel.builder()
                .nombre("Huiro negro Coquimbo IT")
                .build());

        ExtraccionTipoModel metodo = extraccionTipoRepository.saveAndFlush(ExtraccionTipoModel.builder()
                .nombre("Varado Coquimbo IT")
                .build());

        HumedadEstadoModel humedad = humedadEstadoRepository.saveAndFlush(HumedadEstadoModel.builder()
                .nombre("Húmedo Coquimbo IT")
                .build());

        // 2. Usuarios con sus comunas de inscripción
        UsuarioModel userCoquimbo = usuarioRepository.saveAndFlush(UsuarioModel.builder()
                .rut("41.000.001-1")
                .nombres("Recolector")
                .apellidop("Coquimbo")
                .perfil(perfilRecolector)
                .comuna(comunaCoquimbo)
                .build());

        UsuarioModel userLaSerena = usuarioRepository.saveAndFlush(UsuarioModel.builder()
                .rut("41.000.002-2")
                .nombres("Recolector")
                .apellidop("La Serena")
                .perfil(perfilRecolector)
                .comuna(comunaLaSerena)
                .build());

        UsuarioModel userLaHiguera = usuarioRepository.saveAndFlush(UsuarioModel.builder()
                .rut("41.000.003-3")
                .nombres("Recolector")
                .apellidop("La Higuera")
                .perfil(perfilRecolector)
                .comuna(comunaLaHiguera)
                .build());

        UsuarioModel userAndacollo = usuarioRepository.saveAndFlush(UsuarioModel.builder()
                .rut("41.000.004-4")
                .nombres("Recolector")
                .apellidop("Andacollo")
                .perfil(perfilRecolector)
                .comuna(comunaAndacollo)
                .build());

        // 3. Cuota comunal multicomunal (Coquimbo, La Serena, La Higuera)
        Set<ComunaModel> comunasCuota = new LinkedHashSet<>();
        comunasCuota.add(comunaCoquimbo);
        comunasCuota.add(comunaLaSerena);
        comunasCuota.add(comunaLaHiguera);

        CuotaExtraccionModel cuota = cuotaRepository.saveAndFlush(CuotaExtraccionModel.builder()
                .nivelAgregacion("COMUNA")
                .ambito("AREA_LIBRE")
                .perfil("RECOLECTOR")
                .region(regionCoquimbo)
                .comuna(comunaCoquimbo)
                .comunas(comunasCuota)
                .especie(especie)
                .extraccionTipo(metodo)
                .periodo("MENSUAL")
                .fechaInicio(Date.valueOf("2026-10-01"))
                .fechaFin(Date.valueOf("2026-10-31"))
                .limiteKg(30000.0)
                .metrica("CAPTURA")
                .modoAccion("SOLO_ALERTA")
                .resolucion("RES-COQ-09OCT")
                .activo(true)
                .estado("ABIERTA")
                .build());

        // 4. Inserción de declaraciones reales
        // Coquimbo: 5.000 kg
        crearDeclaracionRecolector(userCoquimbo, especie, metodo, humedad, comunaCoquimbo, caletaCoquimbo,
                Date.valueOf("2026-10-05"), new BigDecimal("5000.00"));
        // La Serena: 7.000 kg
        crearDeclaracionRecolector(userLaSerena, especie, metodo, humedad, comunaLaSerena, caletaLaSerena,
                Date.valueOf("2026-10-07"), new BigDecimal("7000.00"));
        // La Higuera: 3.000 kg
        crearDeclaracionRecolector(userLaHiguera, especie, metodo, humedad, comunaLaHiguera, caletaLaHiguera,
                Date.valueOf("2026-10-10"), new BigDecimal("3000.00"));
        // Andacollo: 4.000 kg (fuera de la cuota)
        crearDeclaracionRecolector(userAndacollo, especie, metodo, humedad, comunaAndacollo, caletaAndacollo,
                Date.valueOf("2026-10-12"), new BigDecimal("4000.00"));

        entityManager.clear();
        cuotaExtraccionService.invalidarCacheConsumo();

        // 5. Verificación de consumo acumulado
        BigDecimal consumo = cuotaExtraccionService.calcularConsumoAcumulado(cuota, Date.valueOf("2026-10-15"));

        assertEquals(0, new BigDecimal("15000.00").compareTo(consumo),
                "La consulta multicomunal debe sumar 5.000 + 7.000 + 3.000 = 15.000 kg y excluir los 4.000 kg de Andacollo");
    }

    @Test
    @DisplayName("TM.4 Caso 2: Cuota provincial de Huasco suma Freirina (8.000 kg) y excluye a Caldera (6.000 kg)")
    void testCasoProvincial_HuascoFreirina_ExcluyeCaldera() {
        // 1. Maestros territoriales
        RegionModel regionAtacama = regionRepository.saveAndFlush(RegionModel.builder()
                .nombre("Región de Atacama IT")
                .codigo("03")
                .build());

        ProvinciaModel provinciaHuasco = provinciaRepository.saveAndFlush(ProvinciaModel.builder()
                .nombre("Provincia de Huasco IT")
                .region(regionAtacama)
                .build());

        ProvinciaModel provinciaCopiapo = provinciaRepository.saveAndFlush(ProvinciaModel.builder()
                .nombre("Provincia de Copiapó IT")
                .region(regionAtacama)
                .build());

        ComunaModel comunaFreirina = comunaRepository.saveAndFlush(ComunaModel.builder()
                .nombre("Freirina IT")
                .region(regionAtacama)
                .provincia(provinciaHuasco)
                .build());

        ComunaModel comunaCaldera = comunaRepository.saveAndFlush(ComunaModel.builder()
                .nombre("Caldera IT")
                .region(regionAtacama)
                .provincia(provinciaCopiapo)
                .build());

        CaletaModel caletaFreirina = caletaRepository.saveAndFlush(CaletaModel.builder()
                .nombre("Caleta Freirina IT")
                .region(regionAtacama)
                .comuna(comunaFreirina)
                .build());

        CaletaModel caletaCaldera = caletaRepository.saveAndFlush(CaletaModel.builder()
                .nombre("Caleta Caldera IT")
                .region(regionAtacama)
                .comuna(comunaCaldera)
                .build());

        EspecieModel especie = especieRepository.saveAndFlush(EspecieModel.builder()
                .nombre("Huiro negro Atacama IT")
                .build());

        ExtraccionTipoModel metodo = extraccionTipoRepository.saveAndFlush(ExtraccionTipoModel.builder()
                .nombre("Varado Atacama IT")
                .build());

        HumedadEstadoModel humedad = humedadEstadoRepository.saveAndFlush(HumedadEstadoModel.builder()
                .nombre("Húmedo Atacama IT")
                .build());

        // 2. Usuarios con comuna de inscripción
        UsuarioModel userFreirina = usuarioRepository.saveAndFlush(UsuarioModel.builder()
                .rut("33.000.001-1")
                .nombres("Recolector")
                .apellidop("Freirina")
                .perfil(perfilRecolector)
                .comuna(comunaFreirina)
                .build());

        UsuarioModel userCaldera = usuarioRepository.saveAndFlush(UsuarioModel.builder()
                .rut("33.000.002-2")
                .nombres("Recolector")
                .apellidop("Caldera")
                .perfil(perfilRecolector)
                .comuna(comunaCaldera)
                .build());

        // 3. Cuota provincial de Huasco
        CuotaExtraccionModel cuotaProvincial = cuotaRepository.saveAndFlush(CuotaExtraccionModel.builder()
                .nivelAgregacion("PROVINCIA")
                .ambito("AREA_LIBRE")
                .perfil("RECOLECTOR")
                .region(regionAtacama)
                .provincia(provinciaHuasco)
                .especie(especie)
                .extraccionTipo(metodo)
                .periodo("MENSUAL")
                .fechaInicio(Date.valueOf("2026-10-01"))
                .fechaFin(Date.valueOf("2026-10-31"))
                .limiteKg(25000.0)
                .metrica("CAPTURA")
                .modoAccion("SOLO_ALERTA")
                .resolucion("RES-HUA-09OCT")
                .activo(true)
                .estado("ABIERTA")
                .build());

        // 4. Inserción de declaraciones
        // Freirina: 8.000 kg (dentro de la Provincia de Huasco)
        crearDeclaracionRecolector(userFreirina, especie, metodo, humedad, comunaFreirina, caletaFreirina,
                Date.valueOf("2026-10-08"), new BigDecimal("8000.00"));

        // Caldera: 6.000 kg (en Provincia de Copiapó, fuera de Huasco)
        crearDeclaracionRecolector(userCaldera, especie, metodo, humedad, comunaCaldera, caletaCaldera,
                Date.valueOf("2026-10-09"), new BigDecimal("6000.00"));

        entityManager.clear();
        cuotaExtraccionService.invalidarCacheConsumo();

        // 5. Verificación de consumo acumulado
        BigDecimal consumo = cuotaExtraccionService.calcularConsumoAcumulado(cuotaProvincial, Date.valueOf("2026-10-15"));

        assertEquals(0, new BigDecimal("8000.00").compareTo(consumo),
                "La cuota de Provincia de Huasco debe sumar exactamente los 8.000 kg de Freirina y descartar los 6.000 kg de Caldera");
    }

    @Test
    @DisplayName("TM.4 Caso 3: Plantilla Antofagasta (TM.2) factor 3,58, sobrepaso Persona A (110%) y resumen consolidado")
    void testCasoPlantillaAntofagasta_TM2_SobrepasoPersonaA_Y_ResumenPlantilla() {
        // 1. Maestros
        RegionModel regionAntofagasta = regionRepository.saveAndFlush(RegionModel.builder()
                .nombre("Región de Antofagasta IT")
                .codigo("02")
                .build());

        ProvinciaModel provinciaTocopilla = provinciaRepository.saveAndFlush(ProvinciaModel.builder()
                .nombre("Provincia de Tocopilla IT")
                .region(regionAntofagasta)
                .build());

        ProvinciaModel provinciaAntofagasta = provinciaRepository.saveAndFlush(ProvinciaModel.builder()
                .nombre("Provincia de Antofagasta IT")
                .region(regionAntofagasta)
                .build());

        ComunaModel comunaTocopilla = comunaRepository.saveAndFlush(ComunaModel.builder()
                .nombre("Tocopilla IT")
                .region(regionAntofagasta)
                .provincia(provinciaTocopilla)
                .build());

        ComunaModel comunaMejillones = comunaRepository.saveAndFlush(ComunaModel.builder()
                .nombre("Mejillones IT")
                .region(regionAntofagasta)
                .provincia(provinciaAntofagasta)
                .build());

        CaletaModel caletaTocopilla = caletaRepository.saveAndFlush(CaletaModel.builder()
                .nombre("Caleta Tocopilla IT")
                .region(regionAntofagasta)
                .comuna(comunaTocopilla)
                .build());

        CaletaModel caletaMejillones = caletaRepository.saveAndFlush(CaletaModel.builder()
                .nombre("Caleta Mejillones IT")
                .region(regionAntofagasta)
                .comuna(comunaMejillones)
                .build());

        EspecieModel especieHuiro = especieRepository.saveAndFlush(EspecieModel.builder()
                .nombre("Huiro negro Antofagasta IT")
                .build());

        ExtraccionTipoModel metodoVarado = extraccionTipoRepository.saveAndFlush(ExtraccionTipoModel.builder()
                .nombre("Varado Antofagasta IT")
                .build());

        HumedadEstadoModel humedadSeco = humedadEstadoRepository.saveAndFlush(HumedadEstadoModel.builder()
                .nombre("Seco Antofagasta IT")
                .build());

        // Factor de conversión oficial de huiro negro seco a captura = 3.58
        factorConversionRepository.saveAndFlush(FactorConversionModel.builder()
                .especie(especieHuiro)
                .humedadEstado(humedadSeco)
                .factor(new BigDecimal("3.5800"))
                .vigenciaInicio(Date.valueOf("2026-01-01"))
                .resolucion("RES-CONV-3.58")
                .activo(true)
                .build());

        // 2. Personas A y B
        UsuarioModel personaA = usuarioRepository.saveAndFlush(UsuarioModel.builder()
                .rut("11.111.111-1")
                .nombres("Persona")
                .apellidop("A Tocopilla")
                .perfil(perfilRecolector)
                .comuna(comunaTocopilla)
                .build());

        UsuarioModel personaB = usuarioRepository.saveAndFlush(UsuarioModel.builder()
                .rut("22.222.222-2")
                .nombres("Persona")
                .apellidop("B Mejillones")
                .perfil(perfilRecolector)
                .comuna(comunaMejillones)
                .build());

        // 3. Plantilla individual para la Región de Antofagasta
        // Límite de 5.000 kg secos por persona -> con factor 3,58 equivale a 17.900 kg de captura
        CuotaExtraccionModel cuotaPlantilla = cuotaRepository.saveAndFlush(CuotaExtraccionModel.builder()
                .nivelAgregacion("INDIVIDUAL")
                .esPlantilla(true)
                .ambito("AREA_LIBRE")
                .perfil("RECOLECTOR")
                .region(regionAntofagasta)
                .especie(especieHuiro)
                .extraccionTipo(metodoVarado)
                .humedadEstado(humedadSeco)
                .periodo("MENSUAL")
                .fechaInicio(Date.valueOf("2026-10-01"))
                .fechaFin(Date.valueOf("2026-10-31"))
                .limiteKg(5000.0)
                .metrica("CAPTURA")
                .modoAccion("SOLO_ALERTA")
                .resolucion("RES-ANTOF-PLANTILLA")
                .activo(true)
                .estado("ABIERTA")
                .build());

        BigDecimal limiteEfectivo = cuotaExtraccionService.calcularLimiteEfectivo(cuotaPlantilla, Date.valueOf("2026-10-15"));
        assertEquals(0, new BigDecimal("17900.00").compareTo(limiteEfectivo),
                "5.000 kg secos con factor 3,58 deben resultar en 17.900 kg de captura de límite efectivo");

        // 4. Actividad Persona A:
        // Declaración 1: 4.000 kg secos = 14.320 kg de captura (80%)
        BigDecimal captura1A = new BigDecimal("4000.00").multiply(new BigDecimal("3.58")).setScale(2, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("14320.00"), captura1A);

        EvaluacionCuotaResult eval1A = cuotaExtraccionService.evaluarCuotaDeclaracion(
                "RECOLECTOR", personaA.getId(), null, especieHuiro.getId(), metodoVarado.getId(),
                comunaTocopilla.getId(), Date.valueOf("2026-10-05"), Date.valueOf("2026-10-05"),
                new BigDecimal("4000.00"), captura1A, false);

        assertFalse(eval1A.isExcedeLimite(), "La primera declaración de 14.320 kg está al 80% y no debe exceder");

        crearDeclaracionRecolector(personaA, especieHuiro, metodoVarado, humedadSeco, comunaTocopilla, caletaTocopilla,
                Date.valueOf("2026-10-05"), new BigDecimal("4000.00"), captura1A);

        cuotaExtraccionService.invalidarCacheConsumo();

        // Declaración 2: 1.500 kg secos = 5.370 kg de captura.
        // Acumulado previo: 14.320 + 5.370 = 19.690 kg (110% de 17.900 kg).
        BigDecimal captura2A = new BigDecimal("1500.00").multiply(new BigDecimal("3.58")).setScale(2, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("5370.00"), captura2A);

        EvaluacionCuotaResult eval2A = cuotaExtraccionService.evaluarCuotaDeclaracion(
                "RECOLECTOR", personaA.getId(), null, especieHuiro.getId(), metodoVarado.getId(),
                comunaTocopilla.getId(), Date.valueOf("2026-10-10"), Date.valueOf("2026-10-10"),
                new BigDecimal("1500.00"), captura2A, false);

        assertTrue(eval2A.isExcedeLimite(), "La segunda declaración de Persona A debe superar el límite efectivo");
        assertTrue(eval2A.getMarcas().stream().anyMatch(m -> "CUOTA_EXCEDIDA".equals(m.getMarca())),
                "Debe generar marca CUOTA_EXCEDIDA para Persona A");

        crearDeclaracionRecolector(personaA, especieHuiro, metodoVarado, humedadSeco, comunaTocopilla, caletaTocopilla,
                Date.valueOf("2026-10-10"), new BigDecimal("1500.00"), captura2A);

        // 5. Actividad Persona B:
        // Declaración 1: 2.000 kg secos = 7.160 kg de captura (40%)
        BigDecimal captura1B = new BigDecimal("2000.00").multiply(new BigDecimal("3.58")).setScale(2, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("7160.00"), captura1B);

        EvaluacionCuotaResult eval1B = cuotaExtraccionService.evaluarCuotaDeclaracion(
                "RECOLECTOR", personaB.getId(), null, especieHuiro.getId(), metodoVarado.getId(),
                comunaMejillones.getId(), Date.valueOf("2026-10-11"), Date.valueOf("2026-10-11"),
                new BigDecimal("2000.00"), captura1B, false);

        assertFalse(eval1B.isExcedeLimite(), "La declaración de Persona B está al 40% y no debe exceder");

        crearDeclaracionRecolector(personaB, especieHuiro, metodoVarado, humedadSeco, comunaMejillones, caletaMejillones,
                Date.valueOf("2026-10-11"), new BigDecimal("2000.00"), captura1B);

        entityManager.clear();
        cuotaExtraccionService.invalidarCacheConsumo();

        // 6. Verificación consolidada de plantilla (TM.2)
        ResumenPlantillaDTO resumen = consumoIndividualQuery.resumenPlantilla(cuotaPlantilla, Date.valueOf("2026-10-15"));

        assertNotNull(resumen);
        assertEquals(2, resumen.getPersonasConActividad(), "Debe haber 2 personas con actividad en la plantilla");
        assertEquals(1, resumen.getPersonasSobreLimite(), "Exactamente 1 de 2 personas está sobre su tope");
        assertEquals(110.0, resumen.getMaxPorcentaje(), 0.01, "El porcentaje máximo sobre el tope debe ser 110 %");
        assertEquals("1 de 2 personas sobre su tope (máx. 110 %)", resumen.getTextoConsumo());

        BigDecimal totalConsumoEsperado = captura1A.add(captura2A).add(captura1B); // 14.320 + 5.370 + 7.160 = 26.850
        assertEquals(0, totalConsumoEsperado.compareTo(resumen.getConsumoTotal()),
                "El consumo total acumulado debe ser 26.850 kg");
    }

    private DeclaracionRecolectorModel crearDeclaracionRecolector(
            UsuarioModel usuario,
            EspecieModel especie,
            ExtraccionTipoModel metodo,
            HumedadEstadoModel humedad,
            ComunaModel comuna,
            CaletaModel caleta,
            Date fecha,
            BigDecimal desembarqueKg) {
        return crearDeclaracionRecolector(usuario, especie, metodo, humedad, comuna, caleta, fecha, desembarqueKg, desembarqueKg);
    }

    private DeclaracionRecolectorModel crearDeclaracionRecolector(
            UsuarioModel usuario,
            EspecieModel especie,
            ExtraccionTipoModel metodo,
            HumedadEstadoModel humedad,
            ComunaModel comuna,
            CaletaModel caleta,
            Date fecha,
            BigDecimal desembarqueKg,
            BigDecimal capturaKg) {
        DeclaracionRecolectorModel decl = DeclaracionRecolectorModel.builder()
                .usuario(usuario)
                .especie(especie)
                .extraccionTipo(metodo)
                .humedadEstado(humedad)
                .comuna(comuna)
                .caleta(caleta)
                .fechaDeclaracion(fecha)
                .fechaExtraccion(fecha)
                .hora("09:30:00")
                .nombre(usuario.getNombreCompleto())
                .codigoSernapesca("RPA-" + usuario.getId())
                .folioDesembarqueRo("RO-" + usuario.getId() + "-" + System.nanoTime())
                .codigoDestinatario("DEST-01")
                .nombreDestinatario("Planta Destino")
                .usuarioDestinatario(destinatarioDefault)
                .desembarque(desembarqueKg)
                .captura(capturaKg)
                .build();
        return declaracionRecolectorRepository.saveAndFlush(decl);
    }
}
