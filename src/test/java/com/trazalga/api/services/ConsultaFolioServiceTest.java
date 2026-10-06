package com.trazalga.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.dto.ResultadoFolioDTO;
import com.trazalga.api.models.ConsultaFolioLogModel;
import com.trazalga.api.repositories.IConsultaFolioLogRepository;

@ExtendWith(MockitoExtension.class)
public class ConsultaFolioServiceTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private ConfiguracionGeneralService configService;

    @Mock
    private IConsultaFolioLogRepository logRepository;

    @Mock
    private Query nativeQuery;

    @InjectMocks
    private ConsultaFolioService service;

    @BeforeEach
    void setUp() {
        lenient().when(configService.getInt(eq("consulta_folio_dias_embarcacion"), anyInt())).thenReturn(30);
        lenient().when(configService.getInt(eq("consulta_folio_max_resultados"), anyInt())).thenReturn(20);
        lenient().when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        lenient().when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
    }

    @Test
    @DisplayName("Normalización de búsqueda: mayúsculas, sin espacios ni guiones")
    void testNormalizarBusqueda() {
        assertEquals("DA12345", service.normalizarBusqueda(" da-12345 "));
        assertEquals("RO9988", service.normalizarBusqueda("ro - 9988"));
        assertEquals("ABCD12", service.normalizarBusqueda("ab-cd-12"));
        assertEquals("", service.normalizarBusqueda(null));
        assertEquals("", service.normalizarBusqueda("   "));
    }

    @Test
    @DisplayName("Generación de términos: antepone prefijos oficiales para entradas numéricas")
    void testGenerarTerminos_SoloDigitos() {
        Set<String> terms = service.generarTerminosBusqueda("123456", "123456");

        assertTrue(terms.contains("123456"));
        assertTrue(terms.contains("RO123456"));
        assertTrue(terms.contains("DA123456"));
        assertTrue(terms.contains("AC123456"));
        assertTrue(terms.contains("AMERB123456"));
        assertTrue(terms.contains("DAPLA123456"));
        assertTrue(terms.contains("A-PLA123456"));
        assertTrue(terms.contains("A_PLA123456"));
    }

    @Test
    @DisplayName("Generación de términos: incluye patente si coincide con formato vehicular")
    void testGenerarTerminos_PatenteVehicular() {
        Set<String> terms = service.generarTerminosBusqueda("ABCD12", "AB-CD-12");

        assertTrue(terms.contains("ABCD12"));
        assertFalse(terms.contains("ROABCD12"));
    }

    @Test
    @DisplayName("T2.2 Aceptación: Buscar folio DA de un armador devuelve coincidencia con datos completos")
    void testBuscar_FolioDAArmador() {
        Date fecha = new Date();
        Object[] row = new Object[]{
                "ARMADOR",
                101L,
                "Folio DA",
                "DA-9090",
                fecha,
                "Juan Pérez",
                "Caleta Los Vilos",
                "Huiro negro",
                new BigDecimal("1500.50"),
                "ENVIADA"
        };

        when(nativeQuery.getResultList()).thenReturn(Collections.singletonList(row));

        List<ResultadoFolioDTO> resultados = service.buscar("DA-9090", "11.111.111-1");

        assertNotNull(resultados);
        assertEquals(1, resultados.size());

        ResultadoFolioDTO dto = resultados.get(0);
        assertEquals("ARMADOR", dto.getTipo());
        assertEquals(101L, dto.getId());
        assertEquals("Folio DA", dto.getCampo());
        assertEquals("DA-9090", dto.getValor());
        assertEquals("Juan Pérez", dto.getActor());
        assertEquals("Caleta Los Vilos", dto.getComunaOCaleta());
        assertEquals("Huiro negro", dto.getEspecie());
        assertEquals(new BigDecimal("1500.50"), dto.getKg());
        assertEquals("ENVIADA", dto.getEstado());
    }

    @Test
    @DisplayName("T2.2 Aceptación: Buscar sólo dígitos busca con prefijos derivados y encuentra el folio")
    void testBuscar_SoloDigitos_DerivaPrefijos() {
        Date fecha = new Date();
        Object[] row = new Object[]{
                "ARMADOR",
                202L,
                "Folio DA",
                "DA9090",
                fecha,
                "Carlos Armador",
                "Caleta Coquimbo",
                "Huiro flotador",
                new BigDecimal("800.00"),
                "ENVIADA"
        };

        when(nativeQuery.getResultList()).thenReturn(Collections.singletonList(row));

        List<ResultadoFolioDTO> resultados = service.buscar("9090", "11.111.111-1");

        assertNotNull(resultados);
        assertEquals(1, resultados.size());
        assertEquals("DA9090", resultados.get(0).getValor());

        // Verificar que los términos enviados a la query contenían DA9090, RO9090, etc.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> termsCaptor = ArgumentCaptor.forClass(List.class);
        verify(nativeQuery).setParameter(eq("terms"), termsCaptor.capture());

        List<String> termsEnviados = termsCaptor.getValue();
        assertTrue(termsEnviados.contains("9090"));
        assertTrue(termsEnviados.contains("DA9090"));
        assertTrue(termsEnviados.contains("RO9090"));
        assertTrue(termsEnviados.contains("AC9090"));
        assertTrue(termsEnviados.contains("AMERB9090"));
        assertTrue(termsEnviados.contains("DAPLA9090"));
    }

    @Test
    @DisplayName("T2.2 Aceptación: Guía de despacho en dos comercializadores devuelve ambas con actor y fecha")
    void testBuscar_GuiaDespachoEnDosComercializadores_RetornaAmbas() {
        Calendar cal1 = Calendar.getInstance();
        cal1.set(2026, Calendar.OCTOBER, 1, 10, 0);
        Date fecha1 = cal1.getTime();

        Calendar cal2 = Calendar.getInstance();
        cal2.set(2026, Calendar.OCTOBER, 2, 12, 0);
        Date fecha2 = cal2.getTime();

        Object[] row1 = new Object[]{
                "COMERCIALIZADOR",
                301L,
                "Guía de despacho N.º",
                "4455",
                fecha1,
                "Comercializadora Norte Ltda",
                "La Serena",
                "Huiro negro",
                new BigDecimal("2000.00"),
                "ENVIADA"
        };

        Object[] row2 = new Object[]{
                "COMERCIALIZADOR",
                302L,
                "Guía de despacho N.º",
                "4455",
                fecha2,
                "Algas del Sur SpA",
                "Coquimbo",
                "Huiro negro",
                new BigDecimal("1800.00"),
                "ENVIADA"
        };

        // Devolver ambas en la consulta
        when(nativeQuery.getResultList()).thenReturn(Arrays.asList(row1, row2));

        List<ResultadoFolioDTO> resultados = service.buscar("4455", "22.222.222-2");

        assertNotNull(resultados);
        assertEquals(2, resultados.size());

        // Ordenados del más reciente al más antiguo: row2 (fecha2) primero, luego row1 (fecha1)
        assertEquals(302L, resultados.get(0).getId());
        assertEquals("Algas del Sur SpA", resultados.get(0).getActor());

        assertEquals(301L, resultados.get(1).getId());
        assertEquals("Comercializadora Norte Ltda", resultados.get(1).getActor());
    }

    @Test
    @DisplayName("T2.2: Coincidencia por el final si exacta está vacía y longitud >= 6")
    void testBuscar_FallbackEndsWith_CuandoExactaVaciaYLongitudMayorIgual6() {
        Date fecha = new Date();
        Object[] rowFallback = new Object[]{
                "PLANTA_ABASTECIMIENTO",
                401L,
                "Folio DAPLA",
                "DAPLA987654",
                fecha,
                "Planta Biopacífico",
                "Planta Caldera",
                "Parda",
                new BigDecimal("5000.00"),
                "ENVIADA"
        };

        // Primera llamada (exacta) devuelve vacía, segunda (LIKE %suffix) devuelve resultado
        when(nativeQuery.getResultList())
                .thenReturn(Collections.emptyList())
                .thenReturn(Collections.singletonList(rowFallback));

        List<ResultadoFolioDTO> resultados = service.buscar("987654", "33.333.333-3");

        assertNotNull(resultados);
        assertEquals(1, resultados.size());
        assertEquals(401L, resultados.get(0).getId());
        assertEquals("DAPLA987654", resultados.get(0).getValor());

        // Verificar que se invocó setParameter con suffixPattern = "%987654"
        verify(nativeQuery).setParameter(eq("suffixPattern"), eq("%987654"));
    }

    @Test
    @DisplayName("T2.2: Coincidencia por el final NO se ejecuta si longitud < 6")
    void testBuscar_NoEjecutaFallback_SiLongitudMenor6() {
        when(nativeQuery.getResultList()).thenReturn(Collections.emptyList());

        List<ResultadoFolioDTO> resultados = service.buscar("12345", "33.333.333-3");

        assertTrue(resultados.isEmpty());
        // Se debe haber ejecutado solo la fase exacta, nunca la de sufijo
        verify(nativeQuery, never()).setParameter(eq("suffixPattern"), anyString());
    }

    @Test
    @DisplayName("T2.2: Búsqueda con código de embarcación usa los días configurados")
    void testBuscar_Embarcacion_UsaDiasConfigurados() {
        when(configService.getInt(eq("consulta_folio_dias_embarcacion"), anyInt())).thenReturn(45);
        when(nativeQuery.getResultList()).thenReturn(Collections.emptyList());

        service.buscar("RPA-1234", "11.111.111-1");

        ArgumentCaptor<Date> fechaCaptor = ArgumentCaptor.forClass(Date.class);
        verify(nativeQuery, atLeastOnce()).setParameter(eq("fechaDesde"), fechaCaptor.capture());

        Date fechaDesde = fechaCaptor.getValue();
        assertNotNull(fechaDesde);

        // Validar que la fecha es aproximadamente hace 45 días (+/- 1 día)
        Calendar calExpected = Calendar.getInstance();
        calExpected.add(Calendar.DAY_OF_YEAR, -45);
        long diffMs = Math.abs(fechaDesde.getTime() - calExpected.getTimeInMillis());
        assertTrue(diffMs < 24 * 60 * 60 * 1000L, "fechaDesde debe estar dentro de las últimas 45 jornadas");
    }

    @Test
    @DisplayName("T2.2: Búsqueda registra log de auditoría en consulta_folio_log")
    void testBuscar_RegistraAuditoria() {
        Date fecha = new Date();
        Object[] row = new Object[]{
                "RECOLECTOR",
                501L,
                "Folio Origen",
                "RO-123",
                fecha,
                "Pedro Pescador",
                "Chañaral",
                "Huiro negro",
                new BigDecimal("600.00"),
                "ENVIADA"
        };
        when(nativeQuery.getResultList()).thenReturn(Collections.singletonList(row));

        service.buscar("RO-123", "18.888.888-8");

        ArgumentCaptor<ConsultaFolioLogModel> logCaptor = ArgumentCaptor.forClass(ConsultaFolioLogModel.class);
        verify(logRepository).save(logCaptor.capture());

        ConsultaFolioLogModel logModel = logCaptor.getValue();
        assertNotNull(logModel);
        assertEquals("18.888.888-8", logModel.getUsuarioRut());
        assertEquals("RO-123", logModel.getTextoBuscado());
        assertEquals(1, logModel.getCantidadResultados());
        assertNotNull(logModel.getFecha());
    }

    @Test
    @DisplayName("T2.2: Declaraciones rechazadas o anuladas conservan su estado en el resultado")
    void testBuscar_DeclaracionesRechazadasOAnuladas_ConservanEstado() {
        Date fecha = new Date();
        Object[] rowRechazada = new Object[]{
                "ARMADOR",
                601L,
                "Folio DA",
                "DA-RECHAZADO",
                fecha,
                "Mario Armador",
                "Caleta Chañaral",
                "Huiro negro",
                new BigDecimal("1200.00"),
                "RECHAZADA"
        };
        Object[] rowAnulada = new Object[]{
                "COMERCIALIZADOR",
                602L,
                "Folio AC",
                "AC-ANULADO",
                fecha,
                "Comercializadora X",
                "Coquimbo",
                "Huiro negro",
                new BigDecimal("1000.00"),
                "ANULADA"
        };

        when(nativeQuery.getResultList()).thenReturn(Arrays.asList(rowRechazada, rowAnulada));

        List<ResultadoFolioDTO> res = service.buscar("RECHAZADO", "11.111.111-1");

        assertEquals(2, res.size());
        assertEquals("ANULADA", res.get(0).getEstado());
        assertEquals("RECHAZADA", res.get(1).getEstado());
    }

    @Test
    @DisplayName("T2.2: Trunca resultados si superan consulta_folio_max_resultados")
    void testBuscar_TruncaAlMaximoResultados() {
        when(configService.getInt(eq("consulta_folio_max_resultados"), anyInt())).thenReturn(3);

        List<Object[]> rows = new ArrayList<>();
        Date now = new Date();
        for (int i = 1; i <= 5; i++) {
            rows.add(new Object[]{
                    "RECOLECTOR",
                    (long) i,
                    "Folio Origen",
                    "RO-TEST-" + i,
                    new Date(now.getTime() - i * 1000L),
                    "Actor " + i,
                    "Caleta",
                    "Especie",
                    new BigDecimal("100"),
                    "ENVIADA"
            });
        }

        when(nativeQuery.getResultList()).thenReturn(rows);

        List<ResultadoFolioDTO> res = service.buscar("RO-TEST", "11.111.111-1");

        assertEquals(3, res.size(), "Debe truncar al máximo configurado de 3 resultados");
    }
}
