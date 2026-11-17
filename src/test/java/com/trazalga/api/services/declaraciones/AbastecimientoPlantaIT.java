package com.trazalga.api.services.declaraciones;

import com.trazalga.api.IntegracionBase;
import com.trazalga.api.dto.ReportDTO;
import com.trazalga.api.dto.TrazabilidadNodoDTO;
import com.trazalga.api.exceptions.LoteInternoDuplicadoException;
import com.trazalga.api.exceptions.ValidacionPlantaException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import com.trazalga.api.models.DeclaracionPlantaProduccionModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.models.ProductoModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IDeclaracionPlantaAbastecimientoRepository;
import com.trazalga.api.repositories.IDeclaracionPlantaProduccionRepository;
import com.trazalga.api.repositories.IEspecieRepository;
import com.trazalga.api.repositories.IHumedadEstadoRepository;
import com.trazalga.api.repositories.IProductoRepository;
import com.trazalga.api.repositories.IUsuarioRepository;
import com.trazalga.api.services.CadenaCustodiaService;
import com.trazalga.api.services.ConsultaFolioService;
import com.trazalga.api.services.DeclaracionPlantaAbastecimientoService;
import com.trazalga.api.services.DeclaracionPlantaProduccionService;
import com.trazalga.api.services.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AbastecimientoPlantaIT extends IntegracionBase {

    private static final AtomicInteger SEQ = new AtomicInteger(10);

    @Autowired private DeclaracionPlantaAbastecimientoService abastecimientoService;
    @Autowired private DeclaracionPlantaProduccionService produccionService;
    @Autowired private IDeclaracionPlantaAbastecimientoRepository abastecimientoRepository;
    @Autowired private IDeclaracionPlantaProduccionRepository produccionRepository;
    @Autowired private IUsuarioRepository usuarioRepository;
    @Autowired private IEspecieRepository especieRepository;
    @Autowired private IHumedadEstadoRepository humedadEstadoRepository;
    @Autowired private IProductoRepository productoRepository;
    @Autowired private ReportService reportService;
    @Autowired private CadenaCustodiaService cadenaCustodiaService;
    @Autowired private ConsultaFolioService consultaFolioService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UsuarioModel plantaA;
    private UsuarioModel plantaB;
    private EspecieModel especie;
    private HumedadEstadoModel humedad;
    private ProductoModel producto;
    private Date fecha;
    private int seq;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("ALTER TABLE declaracion_planta_abastecimiento ADD COLUMN IF NOT EXISTS folio_declaracion_a_pla VARCHAR(50)");
        jdbcTemplate.execute("ALTER TABLE declaracion_planta_produccion ADD COLUMN IF NOT EXISTS folio_declaracion_p_pla VARCHAR(50)");

        seq = SEQ.incrementAndGet();
        fecha = Date.valueOf("2026-11-06");
        plantaA = usuarioRepository.saveAndFlush(UsuarioModel.builder()
                .rut("76001" + seq + "-K").nombres("Planta").apellidop("Alfa" + seq).estado("ACTIVO").build());
        plantaB = usuarioRepository.saveAndFlush(UsuarioModel.builder()
                .rut("76002" + seq + "-K").nombres("Planta").apellidop("Beta" + seq).estado("ACTIVO").build());
        especie = especieRepository.findAll().stream().findFirst().orElseGet(() ->
                especieRepository.saveAndFlush(EspecieModel.builder()
                        .nombre("Lessonia spicata").descripcion("Huiro negro").activo(true).build()));
        humedad = humedadEstadoRepository.findAll().stream().findFirst().orElseGet(() ->
                humedadEstadoRepository.saveAndFlush(HumedadEstadoModel.builder()
                        .nombre("Seco").rangoInicio(0).rangoFin(20).build()));
        producto = productoRepository.findAll().stream()
                .filter(p -> p.getEspecie() == null || p.getEspecie().getId().equals(especie.getId()))
                .findFirst()
                .orElseGet(() -> productoRepository.saveAndFlush(ProductoModel.builder()
                        .nombre("Alga Picada Test " + seq).descripcion("Picada").build()));
    }

    @Test
    void na1_saveYUpdate_asignanUsuarioDestinatarioAPropiaPlanta() {
        DeclaracionPlantaAbastecimientoModel entradaA = abastecimiento(plantaA, "FOLIO-NA1-A-" + seq);
        entradaA.setUsuarioDestinatario(plantaB);
        DeclaracionPlantaAbastecimientoModel guardadaA = abastecimientoService.save(entradaA);
        assertNotNull(guardadaA.getUsuarioDestinatario());
        assertEquals(plantaA.getId(), guardadaA.getUsuarioDestinatario().getId());

        DeclaracionPlantaAbastecimientoModel cambioA = abastecimiento(plantaA, "FOLIO-NA1-A-" + seq);
        cambioA.setUsuarioDestinatario(plantaB);
        cambioA.setCantidad(new BigDecimal("1200.00"));
        DeclaracionPlantaAbastecimientoModel editadaA = abastecimientoService.updateById(cambioA, guardadaA.getId());
        assertEquals(plantaA.getId(), editadaA.getUsuarioDestinatario().getId());

        DeclaracionPlantaProduccionModel entradaP = produccion(
                plantaA, "PLANTA_ABASTECIMIENTO:" + guardadaA.getId());
        entradaP.setUsuarioDestinatario(plantaB);
        DeclaracionPlantaProduccionModel guardadaP = produccionService.save(entradaP);
        assertNotNull(guardadaP.getUsuarioDestinatario());
        assertEquals(plantaA.getId(), guardadaP.getUsuarioDestinatario().getId());

        DeclaracionPlantaProduccionModel cambioP = produccion(
                plantaA, "PLANTA_ABASTECIMIENTO:" + guardadaA.getId());
        cambioP.setUsuarioDestinatario(plantaB);
        cambioP.setCantidadProducto(new BigDecimal("420.00"));
        DeclaracionPlantaProduccionModel editadaP = produccionService.updateById(cambioP, guardadaP.getId());
        assertEquals(plantaA.getId(), editadaP.getUsuarioDestinatario().getId());
    }

    @Test
    void na1_politicaEscrituraPlanta_soloDuenoNoDestinatario() {
        DeclaracionPlantaAbastecimientoModel loteDeA = abastecimientoRepository.saveAndFlush(
                abastecimiento(plantaA, "FOLIO-NA1-OWN-" + seq));
        loteDeA.setUsuarioDestinatario(plantaB);
        abastecimientoRepository.saveAndFlush(loteDeA);

        DeclaracionPlantaProduccionModel intentoDeB = produccion(
                plantaB, "PLANTA_ABASTECIMIENTO:" + loteDeA.getId());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> produccionService.save(intentoDeB));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void na1_reporteYCadenaCustodia_omitenDestinatarioEnPlantaInterna() {
        DeclaracionPlantaAbastecimientoModel a = abastecimientoService.save(
                abastecimiento(plantaA, "FOLIO-NA1-RPT-" + seq));
        produccionService.save(produccion(plantaA, "PLANTA_ABASTECIMIENTO:" + a.getId()));

        List<ReportDTO> repAbast = reportService.getReport(fecha, fecha, 5, plantaA.getRut());
        assertFalse(repAbast.isEmpty());
        repAbast.forEach(r -> {
            assertEquals("", r.getReceptorNombre());
            assertEquals("", r.getReceptorRut());
        });

        List<ReportDTO> repProd = reportService.getReport(fecha, fecha, 6, plantaA.getRut());
        assertFalse(repProd.isEmpty());
        repProd.forEach(r -> {
            assertEquals("", r.getReceptorNombre());
            assertEquals("", r.getReceptorRut());
        });

        var nodoAbast = cadenaCustodiaService.getTrazabilidad("PLANTA_ABASTECIMIENTO", a.getId());
        assertEquals(2, nodoAbast.getNodos().size());
        for (TrazabilidadNodoDTO n : nodoAbast.getNodos()) {
            String t = n.getTipoNodo() != null ? n.getTipoNodo().toUpperCase() : "";
            if (t.contains("ABASTECIMIENTO") || t.contains("PRODUCCION") || t.contains("PRODUCCIÓN")) {
                assertNull(n.getNombreDestinatario());
                assertNull(n.getRutDestinatario());
            }
        }
    }

    @Test
    void na2_lugarAcopio_exigeDesde260YExcluyeConsumidosOAnuladosEnStock() {
        try {
            MockHttpServletRequest req260 = new MockHttpServletRequest();
            req260.addHeader("X-App-Version", "2.6.0");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req260));

            DeclaracionPlantaAbastecimientoModel sinAcopio = abastecimiento(plantaA, "FOLIO-NA2-ERR-" + seq);
            sinAcopio.setLugarAcopio("   ");
            assertThrows(ValidacionPlantaException.class, () -> abastecimientoService.save(sinAcopio));

            DeclaracionPlantaAbastecimientoModel conAcopio = abastecimiento(plantaA, "FOLIO-NA2-OK-" + seq);
            conAcopio.setLugarAcopio("  Galpón Norte 1  ");
            conAcopio.setLoteInterno("LOT-NA2-" + seq);
            DeclaracionPlantaAbastecimientoModel guardada = abastecimientoService.save(conAcopio);
            assertEquals("Galpón Norte 1", guardada.getLugarAcopio());

            // Legacy < 2.6.0 permite recepción sin lugar_acopio (compatibilidad hacia atrás)
            MockHttpServletRequest reqLegacy = new MockHttpServletRequest();
            reqLegacy.addHeader("X-App-Version", "2.5.9");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(reqLegacy));

            DeclaracionPlantaAbastecimientoModel legacySinAcopio = abastecimiento(plantaA, "FOLIO-NA2-LEG-" + seq);
            DeclaracionPlantaAbastecimientoModel guardadaLegacy = abastecimientoService.save(legacySinAcopio);
            assertNull(guardadaLegacy.getLugarAcopio());

            // Anulada excluida de stock
            DeclaracionPlantaAbastecimientoModel anulada = abastecimiento(plantaA, "FOLIO-NA2-ANU-" + seq);
            anulada.setEstado("ANULADA");
            anulada.setLugarAcopio("Galpón Sur");
            DeclaracionPlantaAbastecimientoModel guardadaAnulada = abastecimientoService.save(anulada);

            // Cadena de custodia propaga lugarAcopio
            var cadena = cadenaCustodiaService.getTrazabilidad("PLANTA_ABASTECIMIENTO", guardada.getId());
            assertEquals("Galpón Norte 1", cadena.getNodos().get(0).getLugarAcopio());

            // Stock en planta incluye solo disponibles no consumidos y no anulados
            var stockInicial = reportService.getStockEnPlanta(plantaA.getId());
            @SuppressWarnings("unchecked")
            List<java.util.Map<String, Object>> lotesInicial =
                    (List<java.util.Map<String, Object>>) stockInicial.get("lotes");
            assertTrue(lotesInicial.stream().anyMatch(m ->
                    guardada.getId().equals(m.get("id")) && "Galpón Norte 1".equals(m.get("lugarAcopio"))));
            assertTrue(lotesInicial.stream().anyMatch(m -> guardadaLegacy.getId().equals(m.get("id"))));
            assertFalse(lotesInicial.stream().anyMatch(m -> guardadaAnulada.getId().equals(m.get("id"))));

            // Tras consumir con Producción, sale del stock disponible
            produccionService.save(produccion(plantaA, "PLANTA_ABASTECIMIENTO:" + guardada.getId()));
            var stockTrasConsumo = reportService.getStockEnPlanta(plantaA.getId());
            @SuppressWarnings("unchecked")
            List<java.util.Map<String, Object>> lotesTras =
                    (List<java.util.Map<String, Object>>) stockTrasConsumo.get("lotes");
            assertFalse(lotesTras.stream().anyMatch(m -> guardada.getId().equals(m.get("id"))));
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void na3_loteInterno_exigeDesde260NormalizaYBloqueaDuplicadosEnMismaPlanta() {
        try {
            MockHttpServletRequest req260 = new MockHttpServletRequest();
            req260.addHeader("X-App-Version", "2.6.0");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req260));

            DeclaracionPlantaAbastecimientoModel sinLote = abastecimiento(plantaA, "FOLIO-NA3-ERR-" + seq);
            sinLote.setLugarAcopio("Bodega Central");
            sinLote.setLoteInterno("   ");
            assertThrows(ValidacionPlantaException.class, () -> abastecimientoService.save(sinLote));

            DeclaracionPlantaAbastecimientoModel lote1 = abastecimiento(plantaA, "FOLIO-NA3-1-" + seq);
            lote1.setLugarAcopio("Bodega Central");
            lote1.setLoteInterno("  lot-" + seq + "-a  ");
            DeclaracionPlantaAbastecimientoModel guardada = abastecimientoService.save(lote1);
            assertEquals("LOT-" + seq + "-A", guardada.getLoteInterno());

            // Mismo lote (incluso minúsculas) en la misma planta => 409 Conflict
            DeclaracionPlantaAbastecimientoModel duplicado = abastecimiento(plantaA, "FOLIO-NA3-DUP-" + seq);
            duplicado.setLugarAcopio("Bodega Sur");
            duplicado.setLoteInterno("lot-" + seq + "-a");
            assertThrows(LoteInternoDuplicadoException.class, () -> abastecimientoService.save(duplicado));

            // Mismo lote en distinta planta => permitido
            DeclaracionPlantaAbastecimientoModel otraPlanta = abastecimiento(plantaB, "FOLIO-NA3-OTRA-" + seq);
            otraPlanta.setLugarAcopio("Galpón B");
            otraPlanta.setLoteInterno("lot-" + seq + "-a");
            DeclaracionPlantaAbastecimientoModel guardadaB = abastecimientoService.save(otraPlanta);
            assertEquals("LOT-" + seq + "-A", guardadaB.getLoteInterno());

            // Edición de la propia declaración conserva su lote sin conflicto
            DeclaracionPlantaAbastecimientoModel cambioMismo = abastecimiento(plantaA, "FOLIO-NA3-1-" + seq);
            cambioMismo.setLugarAcopio("Bodega Norte");
            cambioMismo.setLoteInterno("LOT-" + seq + "-A");
            DeclaracionPlantaAbastecimientoModel actualizada = abastecimientoService.updateById(cambioMismo, guardada.getId());
            assertEquals("LOT-" + seq + "-A", actualizada.getLoteInterno());

            // Anulada libera el número de lote en la misma planta
            guardada.setEstado("ANULADA");
            abastecimientoRepository.saveAndFlush(guardada);
            DeclaracionPlantaAbastecimientoModel reutilizada = abastecimiento(plantaA, "FOLIO-NA3-REU-" + seq);
            reutilizada.setLugarAcopio("Bodega Central");
            reutilizada.setLoteInterno("LOT-" + seq + "-A");
            DeclaracionPlantaAbastecimientoModel guardadaReu = abastecimientoService.save(reutilizada);
            assertEquals("LOT-" + seq + "-A", guardadaReu.getLoteInterno());

            // Cadena de custodia, stock en planta y búsqueda por folio exponen el lote interno
            var cadena = cadenaCustodiaService.getTrazabilidad("PLANTA_ABASTECIMIENTO", guardadaReu.getId());
            assertEquals("LOT-" + seq + "-A", cadena.getNodos().get(0).getLoteInterno());

            var stock = reportService.getStockEnPlanta(plantaA.getId());
            @SuppressWarnings("unchecked")
            List<java.util.Map<String, Object>> lotes = (List<java.util.Map<String, Object>>) stock.get("lotes");
            assertTrue(lotes.stream().anyMatch(m ->
                    guardadaReu.getId().equals(m.get("id")) && ("LOT-" + seq + "-A").equals(m.get("loteInterno"))));

            var buscados = consultaFolioService.buscar("LOT-" + seq + "-A", "76001" + seq + "-K");
            assertTrue(buscados.stream().anyMatch(r ->
                    "PLANTA_ABASTECIMIENTO".equals(r.getTipo())
                            && guardadaReu.getId().equals(r.getId())
                            && "Lote Interno".equals(r.getCampo())));

            // Legacy < 2.6.0 permite recepción sin lote_interno
            MockHttpServletRequest reqLegacy = new MockHttpServletRequest();
            reqLegacy.addHeader("X-App-Version", "2.5.9");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(reqLegacy));

            DeclaracionPlantaAbastecimientoModel legacy = abastecimiento(plantaA, "FOLIO-NA3-LEG-" + seq);
            DeclaracionPlantaAbastecimientoModel guardadaLeg = abastecimientoService.save(legacy);
            assertNull(guardadaLeg.getLoteInterno());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    private DeclaracionPlantaAbastecimientoModel abastecimiento(UsuarioModel owner, String folioOrigen) {
        return DeclaracionPlantaAbastecimientoModel.builder()
                .usuario(owner)
                .folioOrigen(folioOrigen)
                .fechaIngresoPlanta(fecha)
                .fechaTraslado(fecha)
                .hora("10:00:00")
                .especie(especie)
                .humedadEstado(humedad)
                .humedadEstadoRecepcion(humedad)
                .cantidad(new BigDecimal("1000.00"))
                .pesoRomanaKg(new BigDecimal("1000.00"))
                .voucherRomanaNumero("VCH-" + folioOrigen)
                .estado("CONGELADA")
                .build();
    }

    private DeclaracionPlantaProduccionModel produccion(UsuarioModel owner, String seleccion) {
        return DeclaracionPlantaProduccionModel.builder()
                .usuario(owner)
                .fechaProduccion(fecha)
                .fechaTraslado(fecha)
                .hora("11:00:00")
                .materiaPrimaEspecie(especie)
                .humedadEstado(humedad)
                .cantidadMateriaPrima(new BigDecimal("1000.00"))
                .productoResultante(producto)
                .cantidadProducto(new BigDecimal("400.00"))
                .declaracionesSeleccionadas(seleccion)
                .estado("CONGELADA")
                .build();
    }
}
