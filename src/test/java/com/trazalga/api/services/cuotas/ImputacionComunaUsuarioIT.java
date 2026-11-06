package com.trazalga.api.services.cuotas;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

import com.trazalga.api.IntegracionBase;
import com.trazalga.api.dto.ResumenRecalculoCuotaDTO;
import com.trazalga.api.models.AmerbModel;
import com.trazalga.api.models.BuzoModel;
import com.trazalga.api.models.CaletaModel;
import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.models.DeclaracionArmadorModel;
import com.trazalga.api.models.DeclaracionAreaModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.models.EmbarcacionModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.ExtraccionTipoModel;
import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.models.ProvinciaModel;
import com.trazalga.api.models.RegionModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IAmerbRepository;
import com.trazalga.api.repositories.IAvisoEnviadoRepository;
import com.trazalga.api.repositories.IBuzoRepository;
import com.trazalga.api.repositories.ICaletaRepository;
import com.trazalga.api.repositories.IComunaRepository;
import com.trazalga.api.repositories.ICuotaExtraccionEventoRepository;
import com.trazalga.api.repositories.ICuotaExtraccionRepository;
import com.trazalga.api.repositories.IDeclaracionArmadorRepository;
import com.trazalga.api.repositories.IDeclaracionAreaRepository;
import com.trazalga.api.repositories.IDeclaracionMarcaRepository;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;
import com.trazalga.api.repositories.IEmbarcacionRepository;
import com.trazalga.api.repositories.IEspecieRepository;
import com.trazalga.api.repositories.IExtraccionTipoRepository;
import com.trazalga.api.repositories.IHumedadEstadoRepository;
import com.trazalga.api.repositories.IProvinciaRepository;
import com.trazalga.api.repositories.IRegionRepository;
import com.trazalga.api.repositories.IUsuarioRepository;
import com.trazalga.api.services.CuotaExtraccionService;

import jakarta.persistence.EntityManager;

/**
 * Ajuste del 06-11-2026 (refinamiento 30-10, punto 1): la cuota se descuenta en la comuna del usuario
 * que declara ({@code usuario.comuna_id}) para recolectores, armadores y áreas de manejo, aunque
 * desembarquen en otra comuna. Sin comuna del usuario, se usa la de desembarque.
 * <p>
 * Usuarios inscritos en La Higuera que desembarcan en Coquimbo: consumen la cuota de La Higuera y no
 * la de Coquimbo, en la evaluación en línea y en el recálculo de hallazgos.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImputacionComunaUsuarioIT extends IntegracionBase {

    @Autowired private CuotaExtraccionService cuotaExtraccionService;

    @Autowired private ICuotaExtraccionRepository cuotaRepository;
    @Autowired private ICuotaExtraccionEventoRepository cuotaEventoRepository;
    @Autowired private IDeclaracionMarcaRepository declaracionMarcaRepository;
    @Autowired private IAvisoEnviadoRepository avisoEnviadoRepository;
    @Autowired private IDeclaracionRecolectorRepository declaracionRecolectorRepository;
    @Autowired private IDeclaracionArmadorRepository declaracionArmadorRepository;
    @Autowired private IDeclaracionAreaRepository declaracionAreaRepository;
    @Autowired private IEmbarcacionRepository embarcacionRepository;
    @Autowired private IBuzoRepository buzoRepository;
    @Autowired private IAmerbRepository amerbRepository;
    @Autowired private IEspecieRepository especieRepository;
    @Autowired private IExtraccionTipoRepository extraccionTipoRepository;
    @Autowired private IHumedadEstadoRepository humedadEstadoRepository;
    @Autowired private IRegionRepository regionRepository;
    @Autowired private IProvinciaRepository provinciaRepository;
    @Autowired private IComunaRepository comunaRepository;
    @Autowired private ICaletaRepository caletaRepository;
    @Autowired private IUsuarioRepository usuarioRepository;
    @Autowired private EntityManager entityManager;

    private static final Date FECHA = Date.valueOf("2026-10-10");
    private static final Date EVAL = Date.valueOf("2026-10-15");

    private RegionModel region;
    private ComunaModel laHiguera;
    private ComunaModel coquimbo;
    private CaletaModel caletaCoquimbo;
    private EspecieModel especie;
    private ExtraccionTipoModel metodo;
    private HumedadEstadoModel humedad;
    private EmbarcacionModel embarcacion;
    private BuzoModel buzo;
    private UsuarioModel recolectorHiguera;
    private UsuarioModel armadorHiguera;
    private UsuarioModel armadorSinComuna;
    private UsuarioModel areaHiguera;
    private UsuarioModel planta;

    @BeforeEach
    void setUp() {
        cuotaExtraccionService.invalidarCacheConsumo();
        if (region != null) {
            return;
        }
        region = regionRepository.saveAndFlush(RegionModel.builder().nombre("Coquimbo IC06").codigo("04").build());
        ProvinciaModel elqui = provinciaRepository.saveAndFlush(ProvinciaModel.builder().nombre("Elqui IC06").region(region).build());
        laHiguera = comunaRepository.saveAndFlush(ComunaModel.builder().nombre("La Higuera IC06").region(region).provincia(elqui).build());
        coquimbo = comunaRepository.saveAndFlush(ComunaModel.builder().nombre("Coquimbo IC06").region(region).provincia(elqui).build());
        caletaCoquimbo = caletaRepository.saveAndFlush(CaletaModel.builder().nombre("Caleta Coquimbo IC06").region(region).comuna(coquimbo).build());

        especie = especieRepository.saveAndFlush(EspecieModel.builder().nombre("Huiro negro IC06").build());
        metodo = extraccionTipoRepository.saveAndFlush(ExtraccionTipoModel.builder().nombre("Varado IC06").build());
        humedad = humedadEstadoRepository.saveAndFlush(HumedadEstadoModel.builder().nombre("Húmedo IC06").build());
        embarcacion = embarcacionRepository.saveAndFlush(EmbarcacionModel.builder().nombre("Embarcación IC06").build());
        buzo = buzoRepository.saveAndFlush(BuzoModel.builder().nombre("Buzo IC06").build());

        recolectorHiguera = usuario("16.161.616-1", "Recolector", laHiguera);
        armadorHiguera = usuario("17.171.717-1", "Armador", laHiguera);
        armadorSinComuna = usuario("18.181.818-1", "ArmadorSinComuna", null);
        areaHiguera = usuario("19.191.919-1", "Organizacion", laHiguera);
        planta = usuarioRepository.saveAndFlush(UsuarioModel.builder()
                .rut("20.202.020-2").nombres("Planta").apellidop("IC06").correo("planta-ic06@trazalga.it").estado("ACTIVO").build());
    }

    @AfterEach
    void tearDown() {
        declaracionMarcaRepository.deleteAll();
        avisoEnviadoRepository.deleteAll();
        declaracionRecolectorRepository.deleteAll();
        declaracionArmadorRepository.deleteAll();
        declaracionAreaRepository.deleteAll();
        cuotaEventoRepository.deleteAll();
        cuotaRepository.deleteAll();
        cuotaExtraccionService.invalidarCacheConsumo();
    }

    @Test
    @DisplayName("Armador inscrito en La Higuera que desembarca en Coquimbo consume la cuota de La Higuera, no la de Coquimbo")
    void armador_descuentaEnLaComunaDelUsuario() {
        declararArmador(armadorHiguera, "1500.00");
        CuotaExtraccionModel cuotaHiguera = cuotaComunal(laHiguera, "ARMADOR", 1000.0);
        CuotaExtraccionModel cuotaCoquimbo = cuotaComunal(coquimbo, "ARMADOR", 1000.0);
        entityManager.clear();

        assertKg("1500.00", cuotaExtraccionService.calcularConsumoAcumulado(cuotaHiguera, EVAL));
        assertKg("0", cuotaExtraccionService.calcularConsumoAcumulado(cuotaCoquimbo, EVAL));
    }

    @Test
    @DisplayName("El recálculo de hallazgos usa la misma comuna que la evaluación en línea (antes usaba el desembarque para armadores)")
    void recalculoHallazgos_armador_usaLaComunaDelUsuario() {
        declararArmador(armadorHiguera, "1500.00");
        CuotaExtraccionModel cuotaHiguera = cuotaComunal(laHiguera, "ARMADOR", 1000.0);
        CuotaExtraccionModel cuotaCoquimbo = cuotaComunal(coquimbo, "ARMADOR", 1000.0);
        entityManager.clear();

        ResumenRecalculoCuotaDTO higuera = cuotaExtraccionService.recalcularHallazgos(cuotaHiguera.getId(), null, null);
        ResumenRecalculoCuotaDTO coq = cuotaExtraccionService.recalcularHallazgos(cuotaCoquimbo.getId(), null, null);

        assertKg("1500.00", higuera.getConsumoFinalKg());
        assertEquals(1, higuera.getMarcasNuevas(), "La declaración excede la cuota de La Higuera");
        assertKg("0", coq.getConsumoFinalKg());
        assertEquals(0, coq.getMarcasNuevas());
    }

    @Test
    @DisplayName("Recolector inscrito en La Higuera que desembarca en Coquimbo consume la cuota de La Higuera")
    void recolector_descuentaEnLaComunaDelUsuario() {
        declaracionRecolectorRepository.saveAndFlush(DeclaracionRecolectorModel.builder()
                .usuario(recolectorHiguera).especie(especie).extraccionTipo(metodo).humedadEstado(humedad)
                .comuna(coquimbo).caleta(caletaCoquimbo)
                .fechaDeclaracion(FECHA).fechaExtraccion(FECHA).hora("09:00:00")
                .nombre("Recolector IC06").codigoSernapesca("RPA-IC06")
                .folioDesembarqueRo("RO-IC06-" + System.nanoTime())
                .codigoDestinatario("DEST-IC06").nombreDestinatario("Planta IC06").usuarioDestinatario(planta)
                .desembarque(new BigDecimal("800.00")).captura(new BigDecimal("800.00"))
                .build());
        CuotaExtraccionModel cuotaHiguera = cuotaComunal(laHiguera, "RECOLECTOR", 5000.0);
        CuotaExtraccionModel cuotaCoquimbo = cuotaComunal(coquimbo, "RECOLECTOR", 5000.0);
        entityManager.clear();

        assertKg("800.00", cuotaExtraccionService.calcularConsumoAcumulado(cuotaHiguera, EVAL));
        assertKg("0", cuotaExtraccionService.calcularConsumoAcumulado(cuotaCoquimbo, EVAL));
    }

    @Test
    @DisplayName("Armador sin comuna: se descuenta en la comuna de desembarque, para no quedar fuera de toda cuota")
    void armadorSinComuna_respaldoDesembarque() {
        declararArmador(armadorSinComuna, "400.00");
        CuotaExtraccionModel cuotaHiguera = cuotaComunal(laHiguera, "ARMADOR", 1000.0);
        CuotaExtraccionModel cuotaCoquimbo = cuotaComunal(coquimbo, "ARMADOR", 1000.0);
        entityManager.clear();

        assertKg("0", cuotaExtraccionService.calcularConsumoAcumulado(cuotaHiguera, EVAL));
        assertKg("400.00", cuotaExtraccionService.calcularConsumoAcumulado(cuotaCoquimbo, EVAL));
    }

    @Test
    @DisplayName("Área de manejo: una cuota comunal sin AMERB se evalúa sin error SQL y descuenta en la comuna del usuario")
    void area_cuotaComunalSinAmerb_descuentaEnLaComunaDelUsuario() {
        AmerbModel amerb = amerbRepository.saveAndFlush(AmerbModel.builder()
                .nombre("AMERB Coquimbo IC06").comuna(coquimbo).regionModel(region).codigoSernapesca("AM-IC06").build());
        declaracionAreaRepository.saveAndFlush(DeclaracionAreaModel.builder()
                .usuario(areaHiguera).especie(especie).extraccionTipo(metodo).humedadEstado(humedad)
                .caleta(caletaCoquimbo).amerb(amerb)
                .fechaDeclaracion(FECHA).fechaExtraccion(FECHA).hora("12:00:00")
                .folioDesembarqueAmerb("AM-IC06-" + System.nanoTime()).codigoSernapescaAmerb("AM-IC06")
                .desembarque(600.0).captura(600.0)
                .tipoDestinatario("comercializador").usuarioDestinatario(planta)
                .build());
        CuotaExtraccionModel cuotaHiguera = cuotaComunalAmerb(laHiguera);
        CuotaExtraccionModel cuotaCoquimbo = cuotaComunalAmerb(coquimbo);
        entityManager.clear();

        assertKg("600.00", cuotaExtraccionService.calcularConsumoAcumulado(cuotaHiguera, EVAL));
        assertKg("0", cuotaExtraccionService.calcularConsumoAcumulado(cuotaCoquimbo, EVAL));
    }

    // -------------------------------------------------------------------------

    private UsuarioModel usuario(String rut, String nombre, ComunaModel comuna) {
        return usuarioRepository.saveAndFlush(UsuarioModel.builder()
                .rut(rut).nombres(nombre).apellidop("IC06").comuna(comuna).estado("ACTIVO").build());
    }

    private void declararArmador(UsuarioModel armador, String kg) {
        declaracionArmadorRepository.saveAndFlush(DeclaracionArmadorModel.builder()
                .usuario(armador).especie(especie).extraccionTipo(metodo).humedadEstado(humedad)
                .comuna(coquimbo).caleta(caletaCoquimbo)
                .embarcacion(embarcacion).buzo(buzo)
                .fechaDeclaracion(FECHA).fechaExtraccion(FECHA).hora("08:00")
                .folioDesembarqueDa("DA-IC06-" + System.nanoTime())
                .tipoDestinatario("comercializador").codigoDestinatario("DEST-IC06").usuarioDestinatario(planta)
                .desembarque(new BigDecimal(kg)).captura(Double.valueOf(kg))
                .build());
    }

    private CuotaExtraccionModel cuotaComunal(ComunaModel comuna, String perfil, double limiteKg) {
        Set<ComunaModel> comunas = new LinkedHashSet<>();
        comunas.add(comuna);
        return cuotaRepository.saveAndFlush(CuotaExtraccionModel.builder()
                .nivelAgregacion("COMUNA").ambito("AREA_LIBRE").perfil(perfil)
                .region(region).comuna(comuna).comunas(comunas)
                .especie(especie).extraccionTipo(metodo)
                .periodo("MENSUAL").fechaInicio(Date.valueOf("2026-10-01")).fechaFin(Date.valueOf("2026-10-31"))
                .limiteKg(limiteKg).metrica("CAPTURA").modoAccion("SOLO_ALERTA")
                .resolucion("RES-IC06-" + comuna.getNombre()).activo(true).estado("ABIERTA")
                .build());
    }

    private CuotaExtraccionModel cuotaComunalAmerb(ComunaModel comuna) {
        Set<ComunaModel> comunas = new LinkedHashSet<>();
        comunas.add(comuna);
        return cuotaRepository.saveAndFlush(CuotaExtraccionModel.builder()
                .nivelAgregacion("COMUNA").ambito("AMERB").perfil("AREA")
                .region(region).comuna(comuna).comunas(comunas)
                .especie(especie).extraccionTipo(metodo)
                .periodo("MENSUAL").fechaInicio(Date.valueOf("2026-10-01")).fechaFin(Date.valueOf("2026-10-31"))
                .limiteKg(10000.0).metrica("CAPTURA").modoAccion("SOLO_ALERTA")
                .resolucion("RES-IC06-AM-" + comuna.getNombre()).activo(true).estado("ABIERTA")
                .build());
    }

    private static void assertKg(String esperado, BigDecimal real) {
        assertNotNull(real);
        assertEquals(0, new BigDecimal(esperado).compareTo(real), "esperado " + esperado + " kg, real " + real);
    }
}
