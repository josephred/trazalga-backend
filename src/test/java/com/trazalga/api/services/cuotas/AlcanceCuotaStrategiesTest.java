package com.trazalga.api.services.cuotas;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.models.*;
import com.trazalga.api.repositories.*;

/**
 * Pruebas unitarias de las 8 estrategias de AlcanceCuota y el registry (TM.1).
 * Valida validación, normalización, solapamiento, prioridad persona vs plantilla (D6)
 * y exclusión de cuotas individuales de la jerarquía territorial.
 */
@ExtendWith(MockitoExtension.class)
public class AlcanceCuotaStrategiesTest {

    @Mock
    private IComunaRepository comunaRepository;

    @Mock
    private IProvinciaRepository provinciaRepository;

    @Mock
    private IUsuarioRepository usuarioRepository;

    private RegionModel regionCoquimbo;
    private RegionModel regionValparaiso;
    private ProvinciaModel provElqui;
    private ProvinciaModel provLimari;
    private ComunaModel comLaSerena;
    private ComunaModel comCoquimbo;
    private ComunaModel comValpo;
    private UsuarioModel userJuan;
    private UsuarioModel userPedro;

    private AlcanceCuotaRegistry registry;

    @BeforeEach
    void setUp() {
        regionCoquimbo = new RegionModel();
        regionCoquimbo.setId(4L);
        regionCoquimbo.setNombre("Coquimbo");

        regionValparaiso = new RegionModel();
        regionValparaiso.setId(5L);
        regionValparaiso.setNombre("Valparaíso");

        provElqui = new ProvinciaModel();
        provElqui.setId(41L);
        provElqui.setNombre("Elqui");
        provElqui.setRegion(regionCoquimbo);

        provLimari = new ProvinciaModel();
        provLimari.setId(42L);
        provLimari.setNombre("Limarí");
        provLimari.setRegion(regionCoquimbo);

        comLaSerena = new ComunaModel();
        comLaSerena.setId(4101L);
        comLaSerena.setNombre("La Serena");
        comLaSerena.setProvincia(provElqui);
        comLaSerena.setRegion(regionCoquimbo);

        comCoquimbo = new ComunaModel();
        comCoquimbo.setId(4102L);
        comCoquimbo.setNombre("Coquimbo");
        comCoquimbo.setProvincia(provElqui);
        comCoquimbo.setRegion(regionCoquimbo);

        comValpo = new ComunaModel();
        comValpo.setId(5101L);
        comValpo.setNombre("Valparaíso");
        comValpo.setRegion(regionValparaiso);

        userJuan = new UsuarioModel();
        userJuan.setId(101L);
        userJuan.setNombres("Juan");
        userJuan.setApellidop("Pérez");

        userPedro = new UsuarioModel();
        userPedro.setId(102L);
        userPedro.setNombres("Pedro");
        userPedro.setApellidop("Soto");

        registry = AlcanceCuotaRegistry.crearPorDefecto(comunaRepository, provinciaRepository, usuarioRepository);
    }

    @Test
    void testAlcanceComunal_ValidacionExigeComuna() {
        AlcanceComunal alcance = new AlcanceComunal(comunaRepository);
        CuotaExtraccionModel c = new CuotaExtraccionModel();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> alcance.validarYNormalizar(c));
        assertTrue(ex.getMessage().contains("deben tener al menos una comuna"));
    }

    @Test
    void testAlcanceComunal_RechazaComunasDeDistintasRegiones() {
        AlcanceComunal alcance = new AlcanceComunal(comunaRepository);
        CuotaExtraccionModel c = new CuotaExtraccionModel();
        c.setComunas(Set.of(comLaSerena, comValpo));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> alcance.validarYNormalizar(c));
        assertTrue(ex.getMessage().contains("misma región"));
    }

    @Test
    void testAlcanceComunal_NormalizaRegionYComunaCabecera() {
        AlcanceComunal alcance = new AlcanceComunal(comunaRepository);
        CuotaExtraccionModel c = new CuotaExtraccionModel();
        c.setComunas(new LinkedHashSet<>(List.of(comLaSerena, comCoquimbo)));

        alcance.validarYNormalizar(c);
        assertNotNull(c.getRegion());
        assertEquals(regionCoquimbo.getId(), c.getRegion().getId());
        assertEquals(comLaSerena.getId(), c.getComuna().getId());
        assertNull(c.getProvincia());
        assertNull(c.getMacrozona());
    }

    @Test
    void testAlcanceComunal_MismoAlcanceDetectaInterseccion() {
        AlcanceComunal alcance = new AlcanceComunal(comunaRepository);
        CuotaExtraccionModel c1 = new CuotaExtraccionModel();
        c1.setComunas(Set.of(comLaSerena, comCoquimbo));

        CuotaExtraccionModel c2 = new CuotaExtraccionModel();
        c2.setComunas(Set.of(comCoquimbo));

        CuotaExtraccionModel c3 = new CuotaExtraccionModel();
        c3.setComunas(Set.of(comValpo));

        assertTrue(alcance.mismoAlcance(c1, c2));
        assertFalse(alcance.mismoAlcance(c1, c3));
        assertTrue(alcance.comparableEnJerarquia());
    }

    @Test
    void testAlcanceProvincial_ValidacionYNormalizacion() {
        AlcanceProvincial alcance = new AlcanceProvincial(provinciaRepository);
        CuotaExtraccionModel c = new CuotaExtraccionModel();

        assertThrows(IllegalArgumentException.class, () -> alcance.validarYNormalizar(c));

        c.setProvincia(provElqui);
        alcance.validarYNormalizar(c);

        assertEquals("PROVINCIA", c.getNivelAgregacion());
        assertNotNull(c.getRegion());
        assertEquals(regionCoquimbo.getId(), c.getRegion().getId());
        assertNull(c.getComuna());
        assertTrue(c.getComunas().isEmpty());
        assertTrue(alcance.comparableEnJerarquia());

        CuotaExtraccionModel cOtra = new CuotaExtraccionModel();
        cOtra.setProvincia(provLimari);
        assertFalse(alcance.mismoAlcance(c, cOtra));
        cOtra.setProvincia(provElqui);
        assertTrue(alcance.mismoAlcance(c, cOtra));
    }

    @Test
    void testAlcanceRegional_ValidacionYNormalizacion() {
        AlcanceRegional alcance = new AlcanceRegional();
        CuotaExtraccionModel c = new CuotaExtraccionModel();

        assertThrows(IllegalArgumentException.class, () -> alcance.validarYNormalizar(c));

        c.setRegion(regionCoquimbo);
        alcance.validarYNormalizar(c);

        assertEquals("REGION", c.getNivelAgregacion());
        assertNull(c.getProvincia());
        assertNull(c.getComuna());
        assertTrue(alcance.comparableEnJerarquia());
    }

    @Test
    void testAlcanceIndividualPlantilla_ValidacionYExclusionJerarquia() {
        AlcanceIndividualPlantilla alcance = new AlcanceIndividualPlantilla(provinciaRepository);
        CuotaExtraccionModel c = new CuotaExtraccionModel();
        c.setNivelAgregacion("INDIVIDUAL");
        c.setEsPlantilla(true);

        // Sin ámbito territorial base debe fallar
        assertThrows(IllegalArgumentException.class, () -> alcance.validarYNormalizar(c));

        // Con provincia base es válida
        c.setProvincia(provElqui);
        alcance.validarYNormalizar(c);

        assertEquals(true, c.getEsPlantilla());
        assertNull(c.getUsuario());
        assertFalse(alcance.comparableEnJerarquia(), "Las cuotas plantilla no deben evaluarse contra jerarquía comunal/provincial");
    }

    @Test
    void testAlcanceIndividualPersona_ValidacionYExclusionJerarquia() {
        AlcanceIndividualPersona alcance = new AlcanceIndividualPersona(usuarioRepository);
        CuotaExtraccionModel c = new CuotaExtraccionModel();
        c.setNivelAgregacion("INDIVIDUAL");
        c.setEsPlantilla(false);

        // Sin usuario debe fallar
        assertThrows(IllegalArgumentException.class, () -> alcance.validarYNormalizar(c));

        c.setUsuario(userJuan);
        alcance.validarYNormalizar(c);

        assertEquals(false, c.getEsPlantilla());
        assertEquals("INDIVIDUAL", c.getNivelAgregacion());
        assertFalse(alcance.comparableEnJerarquia(), "Las cuotas de persona específica no deben evaluarse contra jerarquía territorial");

        CuotaExtraccionModel cMismoUser = new CuotaExtraccionModel();
        cMismoUser.setUsuario(userJuan);
        assertTrue(alcance.mismoAlcance(c, cMismoUser));

        CuotaExtraccionModel cOtroUser = new CuotaExtraccionModel();
        cOtroUser.setUsuario(userPedro);
        assertFalse(alcance.mismoAlcance(c, cOtroUser));
    }

    @Test
    void testPrioridadPersonaVsPlantilla_D6_NoGeneranFalsoSolape() {
        // Regla D6: Si existe una plantilla provincial y se crea una cuota individual
        // para Juan Pérez en esa provincia, no deben ser consideradas del mismo alcance.
        // La cuota de Juan Pérez prevalece sobre la plantilla sin conflicto de solape.
        AlcanceCuota estrategiaPlantilla = registry.getPorClave(AlcanceIndividualPlantilla.CLAVE);
        AlcanceCuota estrategiaPersona = registry.getPorClave(AlcanceIndividualPersona.CLAVE);

        CuotaExtraccionModel cPlantilla = new CuotaExtraccionModel();
        cPlantilla.setEsPlantilla(true);
        cPlantilla.setNivelAgregacion("INDIVIDUAL");
        cPlantilla.setProvincia(provElqui);

        CuotaExtraccionModel cPersona = new CuotaExtraccionModel();
        cPersona.setEsPlantilla(false);
        cPersona.setNivelAgregacion("INDIVIDUAL");
        cPersona.setUsuario(userJuan);
        cPersona.setProvincia(provElqui);

        assertFalse(estrategiaPlantilla.mismoAlcance(cPlantilla, cPersona),
                "Plantilla y persona específica no deben considerarse mismo alcance");
        assertFalse(estrategiaPersona.mismoAlcance(cPersona, cPlantilla),
                "Persona específica y plantilla no deben solaparse");
    }

    @Test
    void testRegistry_ResolucionCorrecta() {
        CuotaExtraccionModel c1 = new CuotaExtraccionModel();
        c1.setNivelAgregacion("PROVINCIA");
        c1.setProvincia(provElqui);
        assertEquals(AlcanceProvincial.CLAVE, registry.resolver(c1).clave());

        CuotaExtraccionModel c2 = new CuotaExtraccionModel();
        c2.setNivelAgregacion("INDIVIDUAL");
        c2.setEsPlantilla(true);
        assertEquals(AlcanceIndividualPlantilla.CLAVE, registry.resolver(c2).clave());

        CuotaExtraccionModel c3 = new CuotaExtraccionModel();
        c3.setUsuario(userPedro);
        assertEquals(AlcanceIndividualPersona.CLAVE, registry.resolver(c3).clave());

        CuotaExtraccionModel c4 = new CuotaExtraccionModel();
        c4.setRegion(regionCoquimbo);
        c4.setNivelAgregacion("REGION");
        assertEquals(AlcanceRegional.CLAVE, registry.resolver(c4).clave());
    }
}
