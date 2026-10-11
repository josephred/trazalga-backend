package com.trazalga.api.services.cuotas;

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

import com.trazalga.api.dto.ConsumoPersonaDTO;
import com.trazalga.api.dto.CuotaSobrepasoDTO;
import com.trazalga.api.dto.FiltroSobrepasos;
import com.trazalga.api.models.*;
import com.trazalga.api.repositories.ICuotaExtraccionRepository;
import com.trazalga.api.services.CuotaExtraccionService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CuotaSobrepasoServiceTest {

    @Mock
    private EntityManager em;

    @Mock
    private CuotaExtraccionService cuotaService;

    @Mock
    private ICuotaExtraccionRepository cuotaRepository;

    @Mock
    private ConsumoIndividualQuery consumoIndividualQuery;

    @Mock
    private TypedQuery<DeclaracionMarcaModel> marcaQuery;

    @Mock
    private TypedQuery<CuotaExtraccionModel> cuotaQuery;

    @Mock
    private TypedQuery<DeclaracionRecolectorModel> recQuery;

    @InjectMocks
    private CuotaSobrepasoService service;

    private CuotaExtraccionModel cuotaComunal;
    private DeclaracionMarcaModel marcaC;
    private DeclaracionMarcaModel marcaD;
    private DeclaracionRecolectorModel declC;
    private DeclaracionRecolectorModel declD;

    @BeforeEach
    void setUp() {
        cuotaComunal = CuotaExtraccionModel.builder()
                .id(1L)
                .limiteKg(10000.0)
                .metrica("CAPTURA")
                .nivelAgregacion("COMUNA")
                .periodo("2026-10")
                .build();

        // Declaración C (101.7% -> exceso 170 kg)
        marcaC = DeclaracionMarcaModel.builder()
                .id(101L)
                .reglaId(1L)
                .marca("CUOTA_EXCEDIDA")
                .declaracionTipo("RECOLECTOR")
                .declaracionId(3L)
                .criterioParametro("limite_kg")
                .criterioUmbral("10000.0")
                .criterioValor("10170.0")
                .criterioUnidad("kg")
                .detalle("Cuota comunal agotada: 10.170 kg supera el umbral de 10.000 kg")
                .createdAt(new Date())
                .resuelta(false)
                .estadoGestion("PENDIENTE")
                .build();

        declC = DeclaracionRecolectorModel.builder()
                .id(3L)
                .folioDesembarqueRo("DR-C")
                .desembarque(new BigDecimal("1500.00"))
                .captura(new BigDecimal("1695.00"))
                .fechaDeclaracion(new Date())
                .build();

        // Declaración D (107.4% -> exceso 735 kg)
        marcaD = DeclaracionMarcaModel.builder()
                .id(102L)
                .reglaId(1L)
                .marca("CUOTA_EXCEDIDA")
                .declaracionTipo("RECOLECTOR")
                .declaracionId(4L)
                .criterioParametro("limite_kg")
                .criterioUmbral("10000.0")
                .criterioValor("10735.0")
                .criterioUnidad("kg")
                .detalle("Cuota comunal agotada: 10.735 kg supera el umbral de 10.000 kg")
                .createdAt(new Date())
                .resuelta(false)
                .estadoGestion("PENDIENTE")
                .build();

        declD = DeclaracionRecolectorModel.builder()
                .id(4L)
                .folioDesembarqueRo("DR-D")
                .desembarque(new BigDecimal("500.00"))
                .captura(new BigDecimal("565.00"))
                .fechaDeclaracion(new Date())
                .build();
    }

    @Test
    @DisplayName("TQ.1: Caso de aceptación: C (101.7%, exceso 170 kg) y D (107.4%, exceso 735 kg) aparecen, y A y B no")
    void testAceptacionSobrepasos_CD_AparecenConExceso() {
        when(em.createQuery(contains("DeclaracionMarcaModel"), eq(DeclaracionMarcaModel.class))).thenReturn(marcaQuery);
        when(marcaQuery.getResultList()).thenReturn(List.of(marcaC, marcaD));

        when(em.createQuery(contains("CuotaExtraccionModel c"), eq(CuotaExtraccionModel.class))).thenReturn(cuotaQuery);
        when(cuotaQuery.setParameter(eq("cuotaIds"), any())).thenReturn(cuotaQuery);
        when(cuotaQuery.getResultList()).thenReturn(List.of(cuotaComunal));

        when(em.createQuery(contains("DeclaracionRecolectorModel d"), eq(DeclaracionRecolectorModel.class))).thenReturn(recQuery);
        when(recQuery.setParameter(eq("ids"), any())).thenReturn(recQuery);
        when(recQuery.getResultList()).thenReturn(List.of(declC, declD));

        when(cuotaService.calcularLimiteEfectivo(eq(cuotaComunal), any())).thenReturn(new BigDecimal("10000.00"));
        when(cuotaService.describirAlcance(cuotaComunal)).thenReturn("Comunal: Coquimbo + La Serena + La Higuera");

        FiltroSobrepasos filtro = FiltroSobrepasos.builder().cuotaId(1L).build();
        Page<CuotaSobrepasoDTO> page = service.buscar(filtro, PageRequest.of(0, 20));

        assertEquals(2, page.getTotalElements(), "Solo deben aparecer C y D (las que excedieron la cuota)");

        CuotaSobrepasoDTO dtoC = page.getContent().stream().filter(r -> "DR-C".equals(r.getFolio())).findFirst().orElse(null);
        assertNotNull(dtoC);
        assertEquals(10170.0, dtoC.getConsumoAcumulado());
        assertEquals(101.7, dtoC.getPorcentajeConsumo());
        assertEquals(170.0, dtoC.getExcesoKg());
        assertEquals("CUOTA_EXCEDIDA", dtoC.getMarca());

        CuotaSobrepasoDTO dtoD = page.getContent().stream().filter(r -> "DR-D".equals(r.getFolio())).findFirst().orElse(null);
        assertNotNull(dtoD);
        assertEquals(10735.0, dtoD.getConsumoAcumulado());
        assertEquals(107.4, dtoD.getPorcentajeConsumo());
        assertEquals(735.0, dtoD.getExcesoKg());
        assertEquals("CUOTA_EXCEDIDA", dtoD.getMarca());
    }

    @Test
    @DisplayName("TQ.1: Endpoint /api/reportes/cuotas/{id}/personas filtra por soloSobreLimite")
    void testObtenerPersonasCuota_SoloSobreLimite() {
        when(cuotaRepository.findById(1L)).thenReturn(Optional.of(cuotaComunal));

        ConsumoPersonaDTO p1 = ConsumoPersonaDTO.builder()
                .usuarioId(10L)
                .rut("11.111.111-1")
                .nombre("Persona A (Dentro de límite)")
                .consumo(new BigDecimal("4000.00"))
                .limiteEfectivo(new BigDecimal("5000.00"))
                .porcentaje(80.0)
                .exceso(BigDecimal.ZERO)
                .build();

        ConsumoPersonaDTO p2 = ConsumoPersonaDTO.builder()
                .usuarioId(20L)
                .rut("22.222.222-2")
                .nombre("Persona B (Excedida)")
                .consumo(new BigDecimal("5500.00"))
                .limiteEfectivo(new BigDecimal("5000.00"))
                .porcentaje(110.0)
                .exceso(new BigDecimal("500.00"))
                .build();

        when(consumoIndividualQuery.porPersona(eq(cuotaComunal), any())).thenReturn(List.of(p1, p2));

        // 1. Sin filtro: devuelve ambas
        List<ConsumoPersonaDTO> todas = service.obtenerPersonasCuota(1L, false);
        assertEquals(2, todas.size());

        // 2. Con soloSobreLimite = true: solo devuelve a Persona B
        List<ConsumoPersonaDTO> soloSobre = service.obtenerPersonasCuota(1L, true);
        assertEquals(1, soloSobre.size());
        assertEquals(20L, soloSobre.get(0).getUsuarioId());
        assertEquals(new BigDecimal("500.00"), soloSobre.get(0).getExceso());
    }
}
