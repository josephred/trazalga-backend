package com.trazalga.api.services.indicadores;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.trazalga.api.dto.SubreporteHuiroPaloDTO;
import com.trazalga.api.models.ComposicionModel;
import com.trazalga.api.services.indicadores.SubreporteHuiroPaloService.Fila;
import com.trazalga.api.services.indicadores.SubreporteHuiroPaloService.Seleccion;
import com.trazalga.api.services.parametros.ParametrosSubreporteHuiroPalo;

/**
 * Refinamiento 23-10 · punto 3: cálculo del Subreporte Huiro Palo sin base de datos.
 */
class SubreporteHuiroPaloServiceTest {

    private static final double EPS = 0.0001;

    private static ParametrosSubreporteHuiroPalo params(double pct, String formula) {
        return new ParametrosSubreporteHuiroPalo(true, "HUIRO PALO", "BARRETEADO", "SIN FRONDA", pct, formula);
    }

    @Test
    @DisplayName("Fórmula sobre lo sin fronda: 22 % de la captura; sobre el total: 22/78 de la captura")
    void multiplicador_segunFormula() {
        assertEquals(0.22, params(22, "SOBRE_SIN_FRONDA").multiplicador(), EPS);
        assertEquals(0.22 / 0.78, params(22, "SOBRE_TOTAL").multiplicador(), EPS);
        assertEquals(0.22, params(22, "sobre_total_mal_escrito").multiplicador(), EPS, "Una fórmula desconocida usa SOBRE_SIN_FRONDA");
        assertEquals(0.0, params(0, "SOBRE_SIN_FRONDA").multiplicador(), EPS);
        assertEquals(0.0, params(100, "SOBRE_TOTAL").multiplicador(), EPS, "100 % no es válido: dividiría por cero");
        assertFalse(params(100, "SOBRE_TOTAL").factorValido());
    }

    @Test
    @DisplayName("Nombres de los maestros: sin importar tildes, mayúsculas ni plural; parcial sólo si es único")
    void resolverIds_equivalencias() {
        List<ComposicionModel> comps = List.of(
                ComposicionModel.builder().id(1L).nombre("Con frondas").build(),
                ComposicionModel.builder().id(2L).nombre("Sin  Frondas ").build());
        assertEquals(List.of(2L), SubreporteHuiroPaloService.resolverIds(comps, ComposicionModel::getId, ComposicionModel::getNombre, "SIN FRONDA", false));
        assertEquals(List.of(1L), SubreporteHuiroPaloService.resolverIds(comps, ComposicionModel::getId, ComposicionModel::getNombre, "con fronda", false));
        assertTrue(SubreporteHuiroPaloService.resolverIds(comps, ComposicionModel::getId, ComposicionModel::getNombre, "FRONDA", false).isEmpty(),
                "Sin búsqueda parcial para composiciones");
        assertTrue(SubreporteHuiroPaloService.resolverIds(comps, ComposicionModel::getId, ComposicionModel::getNombre, "FRONDA", true).isEmpty(),
                "Una búsqueda parcial ambigua no elige ninguna");

        List<ComposicionModel> especies = List.of(
                ComposicionModel.builder().id(7L).nombre("Huiro palo (Lessonia trabeculata)").build(),
                ComposicionModel.builder().id(9L).nombre("HUIRO NEGRO O CHASCÓN").build());
        assertEquals(List.of(7L), SubreporteHuiroPaloService.resolverIds(especies, ComposicionModel::getId, ComposicionModel::getNombre, "HUIRO PALO", true));
        assertEquals(List.of(9L), SubreporteHuiroPaloService.resolverIds(especies, ComposicionModel::getId, ComposicionModel::getNombre, "huiro negro o chascon", true));
    }

    @Test
    @DisplayName("Sólo la captura sin fronda entra en el cálculo; sin composición y con fronda se informan aparte")
    void agregar_totalesYDesgloses() {
        ParametrosSubreporteHuiroPalo p = params(22, "SOBRE_SIN_FRONDA");
        SubreporteHuiroPaloDTO dto = SubreporteHuiroPaloService.base(p, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31), "EXTRACCION");
        List<Fila> filas = List.of(
                new Fila("RECOLECTOR", 2L, 36L, "La Higuera", 2026, 10, 3, 1000.0),
                new Fila("RECOLECTOR", 2L, 40L, "Coquimbo", 2026, 9, 1, 500.0),
                new Fila("ARMADOR", 2L, 36L, "La Higuera", 2026, 9, 2, 2000.0),
                new Fila("ARMADOR", 1L, 36L, "La Higuera", 2026, 10, 4, 9000.0),   // con fronda
                new Fila("AREA", null, 40L, "Coquimbo", 2026, 10, 5, 7000.0));     // sin composición

        SubreporteHuiroPaloService.agregar(dto, filas, new Seleccion(List.of(1L), List.of(3L), List.of(2L)), p.multiplicador());

        assertTrue(dto.isConfiguracionValida());
        assertEquals(6, dto.getDeclaraciones());
        assertEquals(3500.0, dto.getCapturaSinFrondaKg(), EPS);
        assertEquals(770.0, dto.getSubreporteKg(), EPS);
        assertEquals(4270.0, dto.getCapturaEstimadaKg(), EPS);
        assertEquals(5, dto.getDeclaracionesSinComposicion());
        assertEquals(7000.0, dto.getCapturaSinComposicionKg(), EPS);
        assertEquals(4, dto.getDeclaracionesOtraComposicion());
        assertEquals(9000.0, dto.getCapturaOtraComposicionKg(), EPS);
        assertNull(dto.getMensaje());

        // Comunas ordenadas por captura
        assertEquals("La Higuera", dto.getPorComuna().get(0).getEtiqueta());
        assertEquals(3000.0, dto.getPorComuna().get(0).getCapturaSinFrondaKg(), EPS);
        assertEquals(660.0, dto.getPorComuna().get(0).getSubreporteKg(), EPS);
        assertEquals(5, dto.getPorComuna().get(0).getDeclaraciones());

        // Meses en orden cronológico
        assertEquals(List.of("2026-09", "2026-10"), dto.getPorMes().stream().map(SubreporteHuiroPaloDTO.Grupo::getClave).toList());
        assertEquals("sep 2026", dto.getPorMes().get(0).getEtiqueta());
        assertEquals(2500.0, dto.getPorMes().get(0).getCapturaSinFrondaKg(), EPS);

        assertEquals("Armador", dto.getPorPerfil().get(0).getEtiqueta());
        assertEquals(440.0, dto.getPorPerfil().get(0).getSubreporteKg(), EPS);
    }

    @Test
    @DisplayName("Fórmula sobre el total: 1.000 kg sin fronda son 282,05 kg de fronda no declarada")
    void agregar_formulaSobreTotal() {
        ParametrosSubreporteHuiroPalo p = params(22, "SOBRE_TOTAL");
        SubreporteHuiroPaloDTO dto = SubreporteHuiroPaloService.base(p, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "EXTRACCION");
        SubreporteHuiroPaloService.agregar(dto,
                List.of(new Fila("RECOLECTOR", 2L, 36L, "La Higuera", 2026, 10, 1, 1000.0)),
                new Seleccion(List.of(1L), List.of(3L), List.of(2L)), p.multiplicador());

        assertEquals("SOBRE_TOTAL", dto.getFormula());
        assertEquals(0.2821, dto.getMultiplicador(), EPS);
        assertEquals(282.05, dto.getSubreporteKg(), EPS);
        assertEquals(1282.05, dto.getCapturaEstimadaKg(), EPS);
        // La fronda es el 22 % del total estimado
        assertEquals(0.22, dto.getSubreporteKg() / dto.getCapturaEstimadaKg(), 0.0001);
    }

    @Test
    @DisplayName("Sin declaraciones en el rango: totales en cero y un mensaje")
    void agregar_sinDeclaraciones() {
        ParametrosSubreporteHuiroPalo p = params(22, "SOBRE_SIN_FRONDA");
        SubreporteHuiroPaloDTO dto = SubreporteHuiroPaloService.base(p, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "EXTRACCION");
        SubreporteHuiroPaloService.agregar(dto, List.of(), new Seleccion(List.of(1L), List.of(3L), List.of(2L)), p.multiplicador());

        assertTrue(dto.isConfiguracionValida());
        assertEquals(0, dto.getDeclaraciones());
        assertEquals(0.0, dto.getSubreporteKg(), EPS);
        assertEquals("No hay declaraciones de huiro palo barreteado sin fronda en el rango.", dto.getMensaje());
        assertTrue(dto.getPorComuna().isEmpty());
    }
}
