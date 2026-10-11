package com.trazalga.api.services.indicadores;

import java.math.BigDecimal;
import java.util.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.trazalga.api.dto.DesembarqueAtipicoDTO;
import com.trazalga.api.dto.FiltroDesembarqueAtipico;
import com.trazalga.api.models.*;
import com.trazalga.api.services.ConfiguracionGeneralService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DesembarqueAtipicoServiceTest {

    @Mock
    private EntityManager em;

    @Mock
    private ConfiguracionGeneralService configService;

    @Mock
    private TypedQuery<DeclaracionMarcaModel> marcaQuery;

    @Mock
    private TypedQuery<DeclaracionRecolectorModel> recQuery;

    @Mock
    private TypedQuery<DeclaracionArmadorModel> armQuery;

    @Mock
    private TypedQuery<DeclaracionAreaModel> areaQuery;

    @InjectMocks
    private DesembarqueAtipicoService service;

    private DeclaracionMarcaModel marcaDonLucho;
    private DeclaracionMarcaModel marcaRec5001;
    private DeclaracionArmadorModel armadorDonLucho;
    private DeclaracionRecolectorModel rec5001;
    private DeclaracionRecolectorModel rec5500Sql; // sin marca

    @BeforeEach
    void setUp() {
        EmbarcacionModel embDonLucho = EmbarcacionModel.builder()
                .id(10L)
                .codigo("DL-1234")
                .nombre("Don Lucho")
                .build();

        armadorDonLucho = DeclaracionArmadorModel.builder()
                .id(1L)
                .folioDesembarqueDa("DA-101")
                .desembarque(new BigDecimal("6200.00"))
                .embarcacion(embDonLucho)
                .fechaDeclaracion(new Date())
                .build();

        rec5001 = DeclaracionRecolectorModel.builder()
                .id(2L)
                .folioDesembarqueRo("DR-201")
                .desembarque(new BigDecimal("5001.00"))
                .fechaDeclaracion(new Date())
                .build();

        rec5500Sql = DeclaracionRecolectorModel.builder()
                .id(3L)
                .folioDesembarqueRo("DR-301")
                .desembarque(new BigDecimal("5500.00"))
                .fechaDeclaracion(new Date())
                .build();

        marcaDonLucho = DeclaracionMarcaModel.builder()
                .id(101L)
                .declaracionTipo("ARMADOR")
                .declaracionId(1L)
                .marca("DESEMBARQUE_ATIPICO")
                .criterioParametro("desembarque_umbral_atipico_kg")
                .criterioUmbral("5000.0")
                .criterioValor("6200.0")
                .criterioUnidad("kg")
                .detalle("Desembarque de 6.200 kg supera el umbral de 5.000 kg")
                .createdAt(new Date())
                .resuelta(false)
                .estadoGestion("PENDIENTE")
                .build();

        marcaRec5001 = DeclaracionMarcaModel.builder()
                .id(102L)
                .declaracionTipo("RECOLECTOR")
                .declaracionId(2L)
                .marca("DESEMBARQUE_ATIPICO")
                .criterioParametro("desembarque_umbral_atipico_kg")
                .criterioUmbral("5000.0")
                .criterioValor("5001.0")
                .criterioUnidad("kg")
                .detalle("Desembarque de 5.001 kg supera el umbral de 5.000 kg")
                .createdAt(new Date())
                .resuelta(false)
                .estadoGestion("PENDIENTE")
                .build();
    }

    @Test
    @DisplayName("TD.1: Con umbral 5.000 kg, aparecen Don Lucho (6.200 kg) con su embarcación y recolector (5.001 kg)")
    void testAceptacionUmbral5000_AparecenMarcasYEmbarcacion() {
        when(configService.getDouble("desembarque_umbral_atipico_kg", 5000.0)).thenReturn(5000.0);

        // Consulta de marcas
        when(em.createQuery(contains("DeclaracionMarcaModel"), eq(DeclaracionMarcaModel.class))).thenReturn(marcaQuery);
        when(marcaQuery.getResultList()).thenReturn(List.of(marcaDonLucho, marcaRec5001));

        // Hidrataciones
        TypedQuery<DeclaracionArmadorModel> armHydrateQuery = mock(TypedQuery.class);
        when(em.createQuery(contains("DeclaracionArmadorModel d LEFT JOIN FETCH d.usuario"), eq(DeclaracionArmadorModel.class)))
                .thenReturn(armHydrateQuery);
        when(armHydrateQuery.setParameter(eq("ids"), any())).thenReturn(armHydrateQuery);
        when(armHydrateQuery.getResultList()).thenReturn(List.of(armadorDonLucho));

        TypedQuery<DeclaracionRecolectorModel> recHydrateQuery = mock(TypedQuery.class);
        when(em.createQuery(contains("DeclaracionRecolectorModel d LEFT JOIN FETCH d.usuario"), eq(DeclaracionRecolectorModel.class)))
                .thenReturn(recHydrateQuery);
        when(recHydrateQuery.setParameter(eq("ids"), any())).thenReturn(recHydrateQuery);
        when(recHydrateQuery.getResultList()).thenReturn(List.of(rec5001));

        // Fuente 2 (sin marcas sobre umbral): vacía para esta prueba
        when(em.createQuery(contains("WHERE d.desembarque > :umbral"), eq(DeclaracionRecolectorModel.class))).thenReturn(recQuery);
        when(recQuery.setParameter(eq("umbral"), any())).thenReturn(recQuery);
        when(recQuery.getResultList()).thenReturn(Collections.emptyList());

        when(em.createQuery(contains("WHERE d.desembarque > :umbral"), eq(DeclaracionArmadorModel.class))).thenReturn(armQuery);
        when(armQuery.setParameter(eq("umbral"), any())).thenReturn(armQuery);
        when(armQuery.getResultList()).thenReturn(Collections.emptyList());

        when(em.createQuery(contains("WHERE d.desembarque > :umbral"), eq(DeclaracionAreaModel.class))).thenReturn(areaQuery);
        when(areaQuery.setParameter(eq("umbral"), any())).thenReturn(areaQuery);
        when(areaQuery.getResultList()).thenReturn(Collections.emptyList());

        Page<DesembarqueAtipicoDTO> page = service.buscar(new FiltroDesembarqueAtipico(), PageRequest.of(0, 20));

        assertEquals(2, page.getTotalElements(), "Deben aparecer exactamente dos filas");

        DesembarqueAtipicoDTO donLucho = page.getContent().stream()
                .filter(d -> "DA-101".equals(d.getFolio()))
                .findFirst().orElse(null);
        assertNotNull(donLucho);
        assertEquals("ARMADOR", donLucho.getPerfil());
        assertEquals(6200.0, donLucho.getKilos());
        assertEquals("DL-1234", donLucho.getEmbarcacionCodigo());
        assertEquals("Don Lucho", donLucho.getEmbarcacionNombre());
        assertTrue(donLucho.getCriterioTexto().contains("5.000 kg (registrado)"));

        DesembarqueAtipicoDTO rec = page.getContent().stream()
                .filter(d -> "DR-201".equals(d.getFolio()))
                .findFirst().orElse(null);
        assertNotNull(rec);
        assertEquals(5001.0, rec.getKilos());
        assertTrue(rec.getCriterioTexto().contains("5.000 kg (registrado)"));
    }

    @Test
    @DisplayName("TD.1: Al subir el umbral a 6.000 kg, las dos marcas siguen mostrando umbral 5.000 kg (registrado)")
    void testAceptacionUmbralAumentado_MarcasConservanHistorico() {
        when(configService.getDouble("desembarque_umbral_atipico_kg", 5000.0)).thenReturn(6000.0);

        when(em.createQuery(contains("DeclaracionMarcaModel"), eq(DeclaracionMarcaModel.class))).thenReturn(marcaQuery);
        when(marcaQuery.getResultList()).thenReturn(List.of(marcaDonLucho, marcaRec5001));

        TypedQuery<DeclaracionArmadorModel> armHydrateQuery = mock(TypedQuery.class);
        when(em.createQuery(contains("DeclaracionArmadorModel d LEFT JOIN FETCH d.usuario"), eq(DeclaracionArmadorModel.class)))
                .thenReturn(armHydrateQuery);
        when(armHydrateQuery.setParameter(eq("ids"), any())).thenReturn(armHydrateQuery);
        when(armHydrateQuery.getResultList()).thenReturn(List.of(armadorDonLucho));

        TypedQuery<DeclaracionRecolectorModel> recHydrateQuery = mock(TypedQuery.class);
        when(em.createQuery(contains("DeclaracionRecolectorModel d LEFT JOIN FETCH d.usuario"), eq(DeclaracionRecolectorModel.class)))
                .thenReturn(recHydrateQuery);
        when(recHydrateQuery.setParameter(eq("ids"), any())).thenReturn(recHydrateQuery);
        when(recHydrateQuery.getResultList()).thenReturn(List.of(rec5001));

        // Fuente 2 vacía
        when(em.createQuery(contains("WHERE d.desembarque > :umbral"), eq(DeclaracionRecolectorModel.class))).thenReturn(recQuery);
        when(recQuery.setParameter(eq("umbral"), any())).thenReturn(recQuery);
        when(recQuery.getResultList()).thenReturn(Collections.emptyList());

        when(em.createQuery(contains("WHERE d.desembarque > :umbral"), eq(DeclaracionArmadorModel.class))).thenReturn(armQuery);
        when(armQuery.setParameter(eq("umbral"), any())).thenReturn(armQuery);
        when(armQuery.getResultList()).thenReturn(Collections.emptyList());

        when(em.createQuery(contains("WHERE d.desembarque > :umbral"), eq(DeclaracionAreaModel.class))).thenReturn(areaQuery);
        when(areaQuery.setParameter(eq("umbral"), any())).thenReturn(areaQuery);
        when(areaQuery.getResultList()).thenReturn(Collections.emptyList());

        Page<DesembarqueAtipicoDTO> page = service.buscar(new FiltroDesembarqueAtipico(), PageRequest.of(0, 20));

        assertEquals(2, page.getTotalElements());
        for (DesembarqueAtipicoDTO dto : page.getContent()) {
            assertTrue(dto.getCriterioTexto().contains("5.000 kg (registrado)"),
                    "Debe conservar el umbral registrado de 5.000 kg");
        }
    }

    @Test
    @DisplayName("TD.1: Declaración sin marca de 5.500 kg cargada por SQL aparece con umbral 5.000 y desaparece con 6.000")
    void testAceptacionDeclaracionSql_ApareceSegunUmbralVigente() {
        // 1. Caso con umbral 5.000 kg: 5.500 > 5.000 -> aparece con rótulo umbral vigente
        when(configService.getDouble("desembarque_umbral_atipico_kg", 5000.0)).thenReturn(5000.0);

        // Sin marcas registradas
        when(em.createQuery(contains("DeclaracionMarcaModel"), eq(DeclaracionMarcaModel.class))).thenReturn(marcaQuery);
        when(marcaQuery.getResultList()).thenReturn(Collections.emptyList());

        when(em.createQuery(contains("WHERE d.desembarque > :umbral"), eq(DeclaracionRecolectorModel.class))).thenReturn(recQuery);
        when(recQuery.setParameter(eq("umbral"), any())).thenReturn(recQuery);
        when(recQuery.getResultList()).thenReturn(List.of(rec5500Sql));

        when(em.createQuery(contains("WHERE d.desembarque > :umbral"), eq(DeclaracionArmadorModel.class))).thenReturn(armQuery);
        when(armQuery.setParameter(eq("umbral"), any())).thenReturn(armQuery);
        when(armQuery.getResultList()).thenReturn(Collections.emptyList());

        when(em.createQuery(contains("WHERE d.desembarque > :umbral"), eq(DeclaracionAreaModel.class))).thenReturn(areaQuery);
        when(areaQuery.setParameter(eq("umbral"), any())).thenReturn(areaQuery);
        when(areaQuery.getResultList()).thenReturn(Collections.emptyList());

        Page<DesembarqueAtipicoDTO> page1 = service.buscar(new FiltroDesembarqueAtipico(), PageRequest.of(0, 20));
        assertEquals(1, page1.getTotalElements());
        DesembarqueAtipicoDTO sqlRow = page1.getContent().get(0);
        assertEquals("VIGENTE", sqlRow.getFuente());
        assertNull(sqlRow.getMarcaId());
        assertEquals("SIN_MARCA", sqlRow.getEstadoGestion());
        assertTrue(sqlRow.getCriterioTexto().contains("umbral vigente de 5.000 kg"),
                "Debe rotular con umbral vigente: " + sqlRow.getCriterioTexto());

        // 2. Caso con umbral 6.000 kg: 5.500 <= 6.000 -> la consulta WHERE d.desembarque > 6000 no la trae
        when(configService.getDouble("desembarque_umbral_atipico_kg", 5000.0)).thenReturn(6000.0);
        when(recQuery.getResultList()).thenReturn(Collections.emptyList());

        Page<DesembarqueAtipicoDTO> page2 = service.buscar(new FiltroDesembarqueAtipico(), PageRequest.of(0, 20));
        assertEquals(0, page2.getTotalElements(), "Con umbral 6.000 kg la declaración de 5.500 kg desaparece");
    }
}
