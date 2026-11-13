package com.trazalga.api.repositories;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

import com.trazalga.api.IntegracionBase;
import com.trazalga.api.models.AmerbModel;
import com.trazalga.api.models.BuzoModel;
import com.trazalga.api.models.CaletaModel;
import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.models.DeclaracionAreaModel;
import com.trazalga.api.models.DeclaracionArmadorModel;
import com.trazalga.api.models.DeclaracionComercializadorModel;
import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.models.EmbarcacionModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.ExtraccionTipoModel;
import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.models.ProvinciaModel;
import com.trazalga.api.models.RegionModel;
import com.trazalga.api.models.UsuarioModel;

/**
 * QX.3 (integración H2, perfil "it"): verifica que ReportRepository.getCadenaOrigenPlanta,
 * getVariacionPesoMetrics y getResumenGlobal resuelvan cadenas de custodia a 2 saltos
 * (recolector -> comercializador -> planta, área -> comercializador -> planta y
 * armador -> comercializador -> planta) sin consultar columnas inexistentes (*.cantidad)
 * en las tablas de origen.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CadenaOrigenPlantaIT extends IntegracionBase {

    @Autowired private ReportRepository reportRepository;
    @Autowired private IDeclaracionRecolectorRepository recolectorRepository;
    @Autowired private IDeclaracionArmadorRepository armadorRepository;
    @Autowired private IDeclaracionAreaRepository areaRepository;
    @Autowired private IDeclaracionComercializadorRepository comercializadorRepository;
    @Autowired private IDeclaracionPlantaAbastecimientoRepository abastecimientoRepository;

    @Autowired private IRegionRepository regionRepository;
    @Autowired private IProvinciaRepository provinciaRepository;
    @Autowired private IComunaRepository comunaRepository;
    @Autowired private ICaletaRepository caletaRepository;
    @Autowired private IEspecieRepository especieRepository;
    @Autowired private IExtraccionTipoRepository extraccionTipoRepository;
    @Autowired private IHumedadEstadoRepository humedadEstadoRepository;
    @Autowired private IUsuarioRepository usuarioRepository;
    @Autowired private IEmbarcacionRepository embarcacionRepository;
    @Autowired private IBuzoRepository buzoRepository;
    @Autowired private IAmerbRepository amerbRepository;

    private Date dia;
    private RegionModel region;
    private ProvinciaModel provincia;
    private ComunaModel comuna;
    private CaletaModel caleta;
    private EspecieModel especie;
    private ExtraccionTipoModel metodo;
    private HumedadEstadoModel humedad;
    private UsuarioModel recolector;
    private UsuarioModel armador;
    private UsuarioModel organizacionArea;
    private UsuarioModel comercializador;
    private UsuarioModel planta;
    private EmbarcacionModel embarcacion;
    private BuzoModel buzo;
    private AmerbModel amerb;

    private final List<Long> createdRecIds = new ArrayList<>();
    private final List<Long> createdArmIds = new ArrayList<>();
    private final List<Long> createdAreaIds = new ArrayList<>();
    private final List<Long> createdComIds = new ArrayList<>();
    private final List<Long> createdPlantaIds = new ArrayList<>();

    @BeforeAll
    void setUpFixture() {
        dia = Date.valueOf("2026-10-10");
        region = regionRepository.saveAndFlush(RegionModel.builder().nombre("Coquimbo QX3").codigo("04").build());
        provincia = provinciaRepository.saveAndFlush(ProvinciaModel.builder().nombre("Elqui QX3").region(region).build());
        comuna = comunaRepository.saveAndFlush(ComunaModel.builder().nombre("La Serena QX3").region(region).provincia(provincia).build());
        caleta = caletaRepository.saveAndFlush(CaletaModel.builder().nombre("Caleta QX3").region(region).comuna(comuna).build());
        especie = especieRepository.saveAndFlush(EspecieModel.builder().nombre("Huiro negro QX3").build());
        metodo = extraccionTipoRepository.saveAndFlush(ExtraccionTipoModel.builder().nombre("Barreteado QX3").build());
        humedad = humedadEstadoRepository.saveAndFlush(HumedadEstadoModel.builder().nombre("Húmedo QX3").rangoInicio(30).rangoFin(60).build());

        recolector = crearUsuario("91.111.111-1", "Juan", "Recolector");
        armador = crearUsuario("92.222.222-2", "Pedro", "Armador");
        organizacionArea = crearUsuario("93.333.333-3", "Sindicato", "Area");
        comercializador = crearUsuario("94.444.444-4", "Carlos", "Comercializador");
        planta = crearUsuario("95.555.555-5", "Planta", "QX3");

        embarcacion = embarcacionRepository.saveAndFlush(EmbarcacionModel.builder().nombre("Bote QX3").codigo("QX3-EMB-1").build());
        buzo = buzoRepository.saveAndFlush(BuzoModel.builder().nombre("Buzo QX3").codigo("QX3-BZ-1").build());
        amerb = amerbRepository.saveAndFlush(AmerbModel.builder().nombre("AMERB QX3").build());
    }

    @AfterEach
    void cleanDeclarations() {
        if (!createdRecIds.isEmpty()) {
            recolectorRepository.deleteAllByIdInBatch(createdRecIds);
            createdRecIds.clear();
        }
        if (!createdArmIds.isEmpty()) {
            armadorRepository.deleteAllByIdInBatch(createdArmIds);
            createdArmIds.clear();
        }
        if (!createdAreaIds.isEmpty()) {
            areaRepository.deleteAllByIdInBatch(createdAreaIds);
            createdAreaIds.clear();
        }
        if (!createdComIds.isEmpty()) {
            comercializadorRepository.deleteAllByIdInBatch(createdComIds);
            createdComIds.clear();
        }
        if (!createdPlantaIds.isEmpty()) {
            abastecimientoRepository.deleteAllByIdInBatch(createdPlantaIds);
            createdPlantaIds.clear();
        }
    }

    @AfterAll
    void cleanFixture() {
        if (amerb != null) amerbRepository.deleteById(amerb.getId());
        if (buzo != null) buzoRepository.deleteById(buzo.getId());
        if (embarcacion != null) embarcacionRepository.deleteById(embarcacion.getId());
        for (UsuarioModel u : List.of(planta, comercializador, organizacionArea, armador, recolector)) {
            if (u != null) usuarioRepository.deleteById(u.getId());
        }
        if (humedad != null) humedadEstadoRepository.deleteById(humedad.getId());
        if (metodo != null) extraccionTipoRepository.deleteById(metodo.getId());
        if (especie != null) especieRepository.deleteById(especie.getId());
        if (caleta != null) caletaRepository.deleteById(caleta.getId());
        if (comuna != null) comunaRepository.deleteById(comuna.getId());
        if (provincia != null) provinciaRepository.deleteById(provincia.getId());
        if (region != null) regionRepository.deleteById(region.getId());
    }

    @Test
    @DisplayName("QX.3 · Cadena 2 saltos (recolector, área y armador -> comercializador -> planta) responde sin columnas inexistentes")
    void cadenaDosSaltos_tresTiposOrigen_retornaKgYCaptura() {
        // 1) Cadena RECOLECTOR -> COMERCIALIZADOR -> PLANTA (1000 kg desembarque, 1130 kg captura -> 960 kg romana)
        DeclaracionRecolectorModel rec = crearRecolector(new BigDecimal("1000.00"), new BigDecimal("1130.00"), "OR-REC-QX3");
        DeclaracionComercializadorModel comRec = crearComercializador("RECOLECTOR:" + rec.getId(), new BigDecimal("1000.00"), "COM-REC-QX3");
        DeclaracionPlantaAbastecimientoModel plaRec = crearPlanta("COMERCIALIZADOR:" + comRec.getId(), new BigDecimal("960.00"), "PLA-REC-QX3");
        enlazarComercializador(comRec, plaRec.getId());
        rec.setDeclaracionDestinatario(comRec.getId()).setConsumidaPorTipo("COMERCIALIZADOR");
        recolectorRepository.saveAndFlush(rec);

        // 2) Cadena AREA -> COMERCIALIZADOR -> PLANTA (2000 kg desembarque, 2260 kg captura -> 1960 kg romana)
        DeclaracionAreaModel area = crearArea(2000.0, 2260.0, "OR-AREA-QX3");
        DeclaracionComercializadorModel comArea = crearComercializador("AREA:" + area.getId(), new BigDecimal("2000.00"), "COM-AREA-QX3");
        DeclaracionPlantaAbastecimientoModel plaArea = crearPlanta("COMERCIALIZADOR:" + comArea.getId(), new BigDecimal("1960.00"), "PLA-AREA-QX3");
        enlazarComercializador(comArea, plaArea.getId());
        area.setDeclaracionDestinatario(comArea.getId()).setConsumidaPorTipo("COMERCIALIZADOR");
        areaRepository.saveAndFlush(area);

        // 3) Cadena ARMADOR -> COMERCIALIZADOR -> PLANTA (1500 kg desembarque, 1695 kg captura -> 1500 kg romana)
        DeclaracionArmadorModel arm = crearArmador(new BigDecimal("1500.00"), 1695.0, "OR-ARM-QX3");
        DeclaracionComercializadorModel comArm = crearComercializador("ARMADOR:" + arm.getId(), new BigDecimal("1500.00"), "COM-ARM-QX3");
        DeclaracionPlantaAbastecimientoModel plaArm = crearPlanta("COMERCIALIZADOR:" + comArm.getId(), new BigDecimal("1500.00"), "PLA-ARM-QX3");
        enlazarComercializador(comArm, plaArm.getId());
        arm.setDeclaracionDestinatario(comArm.getId()).setConsumidaPorTipo("COMERCIALIZADOR");
        armadorRepository.saveAndFlush(arm);

        List<Map<String, Object>> cadena = reportRepository.getCadenaOrigenPlanta(dia, dia);
        assertEquals(3, cadena.size(), "Deben aparecer las 3 recepciones de planta en la fecha");

        assertCadena(cadena, plaRec.getId(), "RECOLECTOR", rec.getId(), comRec.getId(), 1000.0, 1130.0, 960.0, -4.0);
        assertCadena(cadena, plaArea.getId(), "AREA", area.getId(), comArea.getId(), 2000.0, 2260.0, 1960.0, -2.0);
        assertCadena(cadena, plaArm.getId(), "ARMADOR", arm.getId(), comArm.getId(), 1500.0, 1695.0, 1500.0, 0.0);

        Map<String, Object> variacion = reportRepository.getVariacionPesoMetrics(dia, dia, 5.0);
        assertNotNull(variacion);
        @SuppressWarnings("unchecked")
        Map<String, Object> cadenaResumen = (Map<String, Object>) variacion.get("cadenaOrigenPlantaResumen");
        assertNotNull(cadenaResumen);
        assertEquals(3L, ((Number) cadenaResumen.get("total")).longValue());

        Map<String, Object> resumenGlobal = reportRepository.getResumenGlobal(dia, dia);
        assertNotNull(resumenGlobal);
        assertTrue(((Number) resumenGlobal.get("declaracionesTotales")).longValue() >= 6L);
    }

    @Test
    @DisplayName("QX.3 · Recolector con captura nula usa desembarque como respaldo vía COALESCE(r.captura, r.desembarque)")
    void cadenaRecolectorCapturaNula_usaDesembarque() {
        DeclaracionRecolectorModel recSinCaptura = crearRecolector(new BigDecimal("850.00"), null, "OR-REC-NULLCAP");
        DeclaracionComercializadorModel com = crearComercializador("RECOLECTOR:" + recSinCaptura.getId(), new BigDecimal("850.00"), "COM-NULLCAP");
        DeclaracionPlantaAbastecimientoModel pla = crearPlanta("COMERCIALIZADOR:" + com.getId(), new BigDecimal("850.00"), "PLA-NULLCAP");
        enlazarComercializador(com, pla.getId());
        recSinCaptura.setDeclaracionDestinatario(com.getId()).setConsumidaPorTipo("COMERCIALIZADOR");
        recolectorRepository.saveAndFlush(recSinCaptura);

        List<Map<String, Object>> cadena = reportRepository.getCadenaOrigenPlanta(dia, dia);
        assertCadena(cadena, pla.getId(), "RECOLECTOR", recSinCaptura.getId(), com.getId(), 850.0, 850.0, 850.0, 0.0);
    }

    @SuppressWarnings("unchecked")
    private void assertCadena(
            List<Map<String, Object>> cadena,
            Long plantaId,
            String tipoOrigenEsperado,
            Long origenIdEsperado,
            Long comIdEsperado,
            double kgOrigenEsperado,
            double capturaOrigenEsperada,
            double kgPlantaEsperado,
            double variacionEsperada) {
        Map<String, Object> row = cadena.stream()
                .filter(m -> plantaId.equals(((Number) m.get("plantaId")).longValue()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No se encontró fila de cadena para plantaId=" + plantaId));

        assertEquals(kgOrigenEsperado, ((Number) row.get("kgOrigen")).doubleValue(), 0.01);
        assertEquals(capturaOrigenEsperada, ((Number) row.get("capturaOrigen")).doubleValue(), 0.01);
        assertEquals(kgPlantaEsperado, ((Number) row.get("kgPlanta")).doubleValue(), 0.01);
        assertEquals(variacionEsperada, ((Number) row.get("variacionPct")).doubleValue(), 0.01);

        Map<String, Object> inter = (Map<String, Object>) row.get("intermediario");
        assertNotNull(inter, "Debe incluir el comercializador intermediario");
        assertEquals("COMERCIALIZADOR", inter.get("tipo"));
        assertEquals(comIdEsperado, ((Number) inter.get("id")).longValue());

        List<Map<String, Object>> origenes = (List<Map<String, Object>>) row.get("origenes");
        assertNotNull(origenes);
        assertEquals(1, origenes.size());
        assertEquals(tipoOrigenEsperado, origenes.get(0).get("tipo"));
        assertEquals(origenIdEsperado, ((Number) origenes.get(0).get("id")).longValue());
        assertEquals(kgOrigenEsperado, ((Number) origenes.get(0).get("kg")).doubleValue(), 0.01);
        assertEquals(capturaOrigenEsperada, ((Number) origenes.get(0).get("captura")).doubleValue(), 0.01);
    }

    private UsuarioModel crearUsuario(String rut, String nombres, String apellido) {
        return usuarioRepository.saveAndFlush(UsuarioModel.builder()
                .rut(rut).nombres(nombres).apellidop(apellido).comuna(comuna).estado("ACTIVO").build());
    }

    private DeclaracionRecolectorModel crearRecolector(BigDecimal desembarque, BigDecimal captura, String folio) {
        DeclaracionRecolectorModel saved = recolectorRepository.saveAndFlush(DeclaracionRecolectorModel.builder()
                .usuario(recolector).folioOrigen(folio).fechaExtraccion(dia).fechaDeclaracion(dia).hora("09:00:00")
                .nombre("Juan Recolector").codigoSernapesca("RPA-REC-QX3").caleta(caleta).especie(especie)
                .comuna(comuna).extraccionTipo(metodo).humedadEstado(humedad)
                .desembarque(desembarque).captura(captura)
                .codigoDestinatario(comercializador.getRut()).nombreDestinatario("Carlos Comercializador")
                .usuarioDestinatario(comercializador)
                .build());
        createdRecIds.add(saved.getId());
        return saved;
    }

    private DeclaracionAreaModel crearArea(double desembarque, double captura, String folio) {
        DeclaracionAreaModel saved = areaRepository.saveAndFlush(DeclaracionAreaModel.builder()
                .usuario(organizacionArea).folioOrigen(folio).fechaExtraccion(dia).fechaDeclaracion(dia).hora("09:30:00")
                .amerb(amerb).caleta(caleta).especie(especie).extraccionTipo(metodo).humedadEstado(humedad)
                .desembarque(desembarque).captura(captura)
                .tipoDestinatario("comercializador").codigoDestinatario(comercializador.getRut())
                .usuarioDestinatario(comercializador)
                .build());
        createdAreaIds.add(saved.getId());
        return saved;
    }

    private DeclaracionArmadorModel crearArmador(BigDecimal desembarque, double captura, String folio) {
        DeclaracionArmadorModel saved = armadorRepository.saveAndFlush(DeclaracionArmadorModel.builder()
                .usuario(armador).folioOrigen(folio).fechaExtraccion(dia).fechaDeclaracion(dia).hora("10:00:00")
                .embarcacion(embarcacion).buzo(buzo).caleta(caleta).comuna(comuna)
                .especie(especie).extraccionTipo(metodo).humedadEstado(humedad)
                .desembarque(desembarque).captura(captura)
                .tipoDestinatario("comercializador").codigoDestinatario(comercializador.getRut())
                .usuarioDestinatario(comercializador)
                .build());
        createdArmIds.add(saved.getId());
        return saved;
    }

    private DeclaracionComercializadorModel crearComercializador(String seleccionados, BigDecimal cantidad, String folio) {
        DeclaracionComercializadorModel saved = comercializadorRepository.saveAndFlush(DeclaracionComercializadorModel.builder()
                .usuario(comercializador).folioOrigen(folio).fechaDeclaracion(dia).hora("11:00:00")
                .especie(especie).humedadEstado(humedad).cantidad(cantidad)
                .codigoDestinatario(planta.getRut()).nombreDestinatario("Planta QX3")
                .usuarioDestinatario(planta).declaracionesSeleccionadas(seleccionados)
                .build());
        createdComIds.add(saved.getId());
        return saved;
    }

    private DeclaracionPlantaAbastecimientoModel crearPlanta(String seleccionados, BigDecimal pesoRomanaKg, String folio) {
        DeclaracionPlantaAbastecimientoModel saved = abastecimientoRepository.saveAndFlush(DeclaracionPlantaAbastecimientoModel.builder()
                .usuario(planta).folioOrigen(folio).fechaIngresoPlanta(dia).hora("14:00:00")
                .especie(especie).humedadEstado(humedad).cantidad(pesoRomanaKg).pesoRomanaKg(pesoRomanaKg)
                .fechaPesaje(dia).voucherRomanaNumero("VCH-" + folio).declaracionesSeleccionadas(seleccionados)
                .build());
        createdPlantaIds.add(saved.getId());
        return saved;
    }

    private void enlazarComercializador(DeclaracionComercializadorModel com, Long plantaId) {
        com.setDeclaracionDestinatario(plantaId).setConsumidaPorTipo("PLANTA_ABASTECIMIENTO");
        comercializadorRepository.saveAndFlush(com);
    }
}
