package com.trazalga.api.services.cuotas;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.dto.ResumenRecalculoCuotaDTO;
import com.trazalga.api.models.*;
import com.trazalga.api.repositories.*;
import com.trazalga.api.services.ConfiguracionGeneralService;
import com.trazalga.api.services.CuotaExtraccionService;

@ExtendWith(MockitoExtension.class)
public class RecalculoCuotaServiceTest {

    @Mock
    private EntityManager em;

    @Mock
    private ICuotaExtraccionRepository cuotaRepository;

    @Mock
    private IDeclaracionMarcaRepository declaracionMarcaRepository;

    @Mock
    private ICuotaExtraccionEventoRepository cuotaEventoRepository;

    @Mock
    private ConfiguracionGeneralService configService;

    @Mock
    private CuotaExtraccionService cuotaService;

    @InjectMocks
    private RecalculoCuotaService recalculoService;

    private CuotaExtraccionModel cuota;
    private EspecieModel especie;
    private ExtraccionTipoModel metodo;
    private ComunaModel comuna;

    @BeforeEach
    void setUp() {
        especie = new EspecieModel();
        especie.setId(1L);
        especie.setNombre("Huiro negro");

        metodo = new ExtraccionTipoModel();
        metodo.setId(2L);
        metodo.setNombre("Barreteado");

        comuna = new ComunaModel();
        comuna.setId(10L);
        comuna.setNombre("Coquimbo");

        cuota = CuotaExtraccionModel.builder()
                .id(100L)
                .especie(especie)
                .extraccionTipo(metodo)
                .comuna(comuna)
                .comunas(new HashSet<>(Collections.singletonList(comuna)))
                .limiteKg(1000.0)
                .metrica("CAPTURA")
                .ambito("AREA_LIBRE")
                .nivelAgregacion("COMUNA")
                .fechaInicio(Date.valueOf(LocalDate.of(2026, 3, 1)))
                .fechaFin(Date.valueOf(LocalDate.of(2026, 3, 31)))
                .estado("ABIERTA")
                .activo(true)
                .createdAt(Date.valueOf(LocalDate.of(2026, 3, 15)))
                .build();

        lenient().when(cuotaRepository.findById(100L)).thenReturn(Optional.of(cuota));
        lenient().when(cuotaService.calcularLimiteEfectivo(eq(cuota), any())).thenReturn(BigDecimal.valueOf(1000.0));
        lenient().when(cuotaService.contieneComuna(eq(cuota), anyLong())).thenReturn(true);
        lenient().when(cuotaService.describirAlcance(any())).thenReturn("Comuna Coquimbo");
        lenient().when(cuotaService.especieAplica(any(), any())).thenReturn(true);
        lenient().when(cuotaService.metodoAplica(any(), any())).thenReturn(true);
        lenient().when(cuotaService.ambitoAplica(any(), any())).thenReturn(true);
        lenient().when(cuotaService.amerbAplica(any(), any())).thenReturn(true);
        lenient().when(cuotaService.usuarioAplica(any(), any())).thenReturn(true);
        lenient().when(cuotaService.getModoImputacion()).thenReturn("EXTRACCION");
        lenient().when(configService.getInt(eq("cuota_dias_gracia_declaracion"), anyInt())).thenReturn(0);
        lenient().when(configService.getValor(eq("cuota_fecha_imputacion"), anyString())).thenReturn("EXTRACCION");
    }

    private Object[] crearFila(Long id, Long usuarioId, Long comunaId, Long especieId, Long extraccionTipoId,
                               Date fExt, Date fDec, String hora, Double desembarque, Double captura, Date fCreacion) {
        return new Object[] {
            id, usuarioId, comunaId, especieId, extraccionTipoId,
            fExt, fDec, hora, BigDecimal.valueOf(desembarque), BigDecimal.valueOf(captura), fCreacion
        };
    }

    @Test
    @DisplayName("TC.10: Recálculo cronológico genera CUOTA_EXCEDIDA e idempotencia no duplica marcas")
    void testRecalculo_CronologicoEIdempotente() {
        Object[] row1 = crearFila(101L, 10L, 10L, 1L, 2L,
                Date.valueOf(LocalDate.of(2026, 3, 2)), Date.valueOf(LocalDate.of(2026, 3, 2)), "10:00",
                600.0, 600.0, Date.valueOf(LocalDate.of(2026, 3, 2)));

        Object[] row2 = crearFila(102L, 11L, 10L, 1L, 2L,
                Date.valueOf(LocalDate.of(2026, 3, 5)), Date.valueOf(LocalDate.of(2026, 3, 5)), "11:00",
                500.0, 500.0, Date.valueOf(LocalDate.of(2026, 3, 5)));

        Query qRec = mock(Query.class);
        Query qArm = mock(Query.class);
        Query qArea = mock(Query.class);

        when(em.createNativeQuery(contains("declaracion_recolector"))).thenReturn(qRec);
        when(em.createNativeQuery(contains("declaracion_armador"))).thenReturn(qArm);
        when(em.createNativeQuery(contains("declaracion_area"))).thenReturn(qArea);

        when(qRec.getResultList()).thenReturn(Arrays.asList(row1, row2));
        when(qArm.getResultList()).thenReturn(Collections.emptyList());
        when(qArea.getResultList()).thenReturn(Collections.emptyList());

        // Primera corrida: ninguna marca preexistente
        when(declaracionMarcaRepository.findByClaveIdempotencia(anyString())).thenReturn(Optional.empty());

        ResumenRecalculoCuotaDTO resumen = recalculoService.recalcularHallazgos(100L, null, null);

        assertNotNull(resumen);
        assertEquals(2, resumen.getDeclaracionesEvaluadas());
        assertEquals(1, resumen.getMarcasNuevas());
        assertEquals(0, resumen.getMarcasExistentes());
        assertEquals(1100.0, resumen.getConsumoFinalKg().doubleValue());
        assertEquals(110.0, resumen.getPorcentajeConsumoFinal());

        // Verificar que guardó la marca con origen RECALCULO
        verify(declaracionMarcaRepository, times(1)).save(argThat(m -> 
            "CUOTA_EXCEDIDA".equals(m.getMarca()) &&
            "RECALCULO".equals(m.getOrigen()) &&
            m.getClaveIdempotencia().contains("102")
        ));

        // Verificar registro de evento RECALCULADA
        verify(cuotaEventoRepository, times(1)).save(argThat(ev -> 
            "RECALCULADA".equals(ev.getTipo()) &&
            ev.getCuota().getId().equals(100L)
        ));

        // Segunda corrida: la marca ya existe en base de datos (idempotencia)
        DeclaracionMarcaModel marcaExistente = DeclaracionMarcaModel.builder()
                .id(999L)
                .marca("CUOTA_EXCEDIDA")
                .claveIdempotencia("RECOLECTOR:102:CUOTA_EXCEDIDA:100")
                .origen("RECALCULO")
                .build();
        when(declaracionMarcaRepository.findByClaveIdempotencia(eq("RECOLECTOR:102:CUOTA_EXCEDIDA:100")))
                .thenReturn(Optional.of(marcaExistente));

        ResumenRecalculoCuotaDTO resumen2 = recalculoService.recalcularHallazgos(100L, null, null);

        assertEquals(2, resumen2.getDeclaracionesEvaluadas());
        assertEquals(0, resumen2.getMarcasNuevas(), "La segunda corrida no debe crear marcas nuevas");
        assertEquals(1, resumen2.getMarcasExistentes(), "Debe identificar la marca preexistente");
    }

    @Test
    @DisplayName("TC.10: Recálculo detecta POSTERIOR_CIERRE y DECLARACION_EXTEMPORANEA")
    void testRecalculo_CierreYExtemporanea() {
        // Cuota cerrada el 20-03
        cuota.cerrar("ADMINISTRATIVO", Date.valueOf(LocalDate.of(2026, 3, 20)), null);

        // d1: Extracción posterior al cierre (22-03) -> POSTERIOR_CIERRE
        Object[] row1 = crearFila(201L, 10L, 10L, 1L, 2L,
                Date.valueOf(LocalDate.of(2026, 3, 22)), Date.valueOf(LocalDate.of(2026, 3, 22)), "08:00",
                100.0, 100.0, Date.valueOf(LocalDate.of(2026, 3, 22)));

        // d2: Extracción antes del cierre (19-03) pero declarada tarde (23-03 con 0 días gracia) -> DECLARACION_EXTEMPORANEA
        Object[] row2 = crearFila(202L, 11L, 10L, 1L, 2L,
                Date.valueOf(LocalDate.of(2026, 3, 19)), Date.valueOf(LocalDate.of(2026, 3, 23)), "09:00",
                100.0, 100.0, Date.valueOf(LocalDate.of(2026, 3, 23)));

        Query qRec = mock(Query.class);
        Query qArm = mock(Query.class);
        Query qArea = mock(Query.class);

        when(em.createNativeQuery(contains("declaracion_recolector"))).thenReturn(qRec);
        when(em.createNativeQuery(contains("declaracion_armador"))).thenReturn(qArm);
        when(em.createNativeQuery(contains("declaracion_area"))).thenReturn(qArea);

        when(qRec.getResultList()).thenReturn(Arrays.asList(row1, row2));
        when(qArm.getResultList()).thenReturn(Collections.emptyList());
        when(qArea.getResultList()).thenReturn(Collections.emptyList());

        when(declaracionMarcaRepository.findByClaveIdempotencia(anyString())).thenReturn(Optional.empty());

        ResumenRecalculoCuotaDTO resumen = recalculoService.recalcularHallazgos(100L, null, null);

        assertEquals(2, resumen.getDeclaracionesEvaluadas());
        assertEquals(2, resumen.getMarcasNuevas());

        verify(declaracionMarcaRepository).save(argThat(m -> 
            "POSTERIOR_CIERRE".equals(m.getMarca()) && m.getClaveIdempotencia().contains("201")
        ));
        verify(declaracionMarcaRepository).save(argThat(m -> 
            "DECLARACION_EXTEMPORANEA".equals(m.getMarca()) && m.getClaveIdempotencia().contains("202")
        ));
    }

    @Test
    @DisplayName("TQ.3: Conteo de declaraciones del período anteriores a la carga de la cuota")
    void testContarDeclaracionesPreviasSinEvaluar() {
        // Cuota creada el 15-03-2026
        cuota.setCreatedAt(Date.valueOf(LocalDate.of(2026, 3, 15)));

        // dPrevia: creada el 05-03-2026 (< 15-03)
        Object[] rowPrevia = crearFila(301L, 10L, 10L, 1L, 2L,
                Date.valueOf(LocalDate.of(2026, 3, 5)), Date.valueOf(LocalDate.of(2026, 3, 5)), "08:00",
                100.0, 100.0, Date.valueOf(LocalDate.of(2026, 3, 5)));

        // dPosterior: creada el 20-03-2026 (> 15-03)
        Object[] rowPosterior = crearFila(302L, 11L, 10L, 1L, 2L,
                Date.valueOf(LocalDate.of(2026, 3, 20)), Date.valueOf(LocalDate.of(2026, 3, 20)), "09:00",
                100.0, 100.0, Date.valueOf(LocalDate.of(2026, 3, 20)));

        Query qRec = mock(Query.class);
        Query qArm = mock(Query.class);
        Query qArea = mock(Query.class);

        when(em.createNativeQuery(contains("declaracion_recolector"))).thenReturn(qRec);
        when(em.createNativeQuery(contains("declaracion_armador"))).thenReturn(qArm);
        when(em.createNativeQuery(contains("declaracion_area"))).thenReturn(qArea);

        when(qRec.getResultList()).thenReturn(Arrays.asList(rowPrevia, rowPosterior));
        when(qArm.getResultList()).thenReturn(Collections.emptyList());
        when(qArea.getResultList()).thenReturn(Collections.emptyList());

        int previasSinEvaluar = recalculoService.contarDeclaracionesPreviasSinEvaluar(cuota);

        assertEquals(1, previasSinEvaluar, "Solo la declaración rowPrevia fue ingresada antes de la carga de la cuota");
    }
}
