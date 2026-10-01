package com.trazalga.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.repositories.IConsultaPatenteLogRepository;
import com.trazalga.api.security.RateLimitFilter;

@ExtendWith(MockitoExtension.class)
public class ConsultaPatentePublicaTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private ConfiguracionGeneralService configService;

    @Mock
    private IConsultaPatenteLogRepository logRepository;

    @Mock
    private Query nativeQuery;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private ConsultaPatenteService patenteService;

    @BeforeEach
    void setUp() {
        lenient().when(configService.getInt(eq("patente_vigencia_horas"), anyInt())).thenReturn(48);
        lenient().when(configService.getInt(eq("patente_consulta_max_por_minuto"), anyInt())).thenReturn(20);
        lenient().when(request.getRemoteAddr()).thenReturn("192.168.1.50");
    }

    /**
     * Caso 1: Patente con movimiento hace 17 horas -> vigente = true
     */
    @Test
    void testCaso1_Movimiento17Horas_RetornaVigenteTrue() {
        LocalDateTime mov17h = LocalDateTime.now().minusHours(17);
        Date fechaMov = Date.from(mov17h.atZone(ZoneId.systemDefault()).toInstant());
        String horaMov = String.format("%02d:%02d:00", mov17h.getHour(), mov17h.getMinute());

        Object[] row = new Object[]{
                fechaMov,
                horaMov,
                "COMERCIALIZADOR_A_PLANTA",
                "DC-1234",
                "Huiro Negro",
                5200.0,
                "Caldera",
                "Planta Pacific Algas"
        };

        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.getResultList()).thenReturn(Collections.singletonList(row));

        Map<String, Object> result = patenteService.consultar("AB-CD-12", request);

        assertNotNull(result);
        assertEquals("ABCD12", result.get("patente"));
        assertEquals(true, result.get("vigente"));
        assertFalse((Boolean) result.getOrDefault("sinRegistros", false));
        assertEquals("Caldera", result.get("comunaOrigen"));
        assertEquals("Planta Pacific Algas", result.get("destino"));
        assertEquals("Huiro Negro", result.get("especie"));
        assertEquals(5200.0, (Double) result.get("kg"), 0.5);

        Double horas = (Double) result.get("horasTranscurridas");
        assertTrue(horas >= 16.5 && horas <= 17.5, "Debería calcular aprox 17 horas");
    }

    /**
     * Caso 2: Patente con movimiento hace 60 horas -> vigente = false con fecha de movimiento
     */
    @Test
    void testCaso2_Movimiento60Horas_RetornaVigenteFalse() {
        LocalDateTime mov60h = LocalDateTime.now().minusHours(60);
        Date fechaMov = Date.from(mov60h.atZone(ZoneId.systemDefault()).toInstant());
        String horaMov = String.format("%02d:%02d:00", mov60h.getHour(), mov60h.getMinute());

        Object[] row = new Object[]{
                fechaMov,
                horaMov,
                "RECEPCION_EN_PLANTA",
                "DPA-99",
                "Parda",
                8000.0,
                "Coquimbo",
                "Planta Norte"
        };

        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.getResultList()).thenReturn(Collections.singletonList(row));

        Map<String, Object> result = patenteService.consultar("gh-jk-34", request);

        assertNotNull(result);
        assertEquals("GHJK34", result.get("patente"));
        assertEquals(false, result.get("vigente"));
        assertNotNull(result.get("fechaHoraMovimiento"));

        Double horas = (Double) result.get("horasTranscurridas");
        assertTrue(horas >= 59.5 && horas <= 60.5, "Debería calcular aprox 60 horas");
    }

    /**
     * Caso 3: Patente no registrada en el sistema -> sinRegistros = true, vigente = false
     */
    @Test
    void testCaso3_PatenteNoRegistrada_RetornaSinRegistros() {
        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.getResultList()).thenReturn(Collections.emptyList());

        Map<String, Object> result = patenteService.consultar("XX-99-99", request);

        assertNotNull(result);
        assertEquals("XX9999", result.get("patente"));
        assertEquals(false, result.get("vigente"));
        assertEquals(true, result.get("sinRegistros"));
    }

    /**
     * Caso 4: Privacidad estricta -> verificar que NO contenga campos como rut, rutChofer, choferTransporte, nombreComercializador
     */
    @Test
    void testCaso4_PrivacidadEstricta_NoContieneDatosPersonales() {
        LocalDateTime mov10h = LocalDateTime.now().minusHours(10);
        Date fechaMov = Date.from(mov10h.atZone(ZoneId.systemDefault()).toInstant());

        // Simulamos un destino que pudiera contener RUT por error humano en texto libre
        Object[] row = new Object[]{
                fechaMov,
                "10:00:00",
                "COMERCIALIZADOR_A_PLANTA",
                "DC-555",
                "Luga Negra",
                3000.0,
                "Los Vilos",
                "Sociedad Algas Ltda 76.123.456-7"
        };

        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.getResultList()).thenReturn(Collections.singletonList(row));

        Map<String, Object> result = patenteService.consultar("KJ-88-22", request);

        assertNotNull(result);
        assertFalse(result.containsKey("rut"), "No debe exponer 'rut'");
        assertFalse(result.containsKey("rutChofer"), "No debe exponer 'rutChofer'");
        assertFalse(result.containsKey("choferTransporte"), "No debe exponer 'choferTransporte'");
        assertFalse(result.containsKey("nombreComercializador"), "No debe exponer 'nombreComercializador'");
        assertFalse(result.containsKey("usuarioId"), "No debe exponer 'usuarioId'");

        String destino = (String) result.get("destino");
        assertFalse(destino.contains("76.123.456-7"), "El RUT debe ser sanitizado del destino");
    }

    /**
     * Caso 5: Rate Limiting -> 21 consultas desde la misma IP en menos de 1 minuto retornan HTTP 429
     */
    @Test
    void testCaso5_RateLimiting_21ConsultasRetornan429() throws Exception {
        RateLimitFilter rateLimiter = new RateLimitFilter();
        // Inyectamos configService simulado
        org.springframework.test.util.ReflectionTestUtils.setField(rateLimiter, "configService", configService);

        HttpServletRequest mockReq = mock(HttpServletRequest.class);
        HttpServletResponse mockRes = mock(HttpServletResponse.class);
        FilterChain mockChain = mock(FilterChain.class);

        when(mockReq.getRequestURI()).thenReturn("/api/public/patentes/ABCD12");
        when(mockReq.getRemoteAddr()).thenReturn("200.1.2.3");
        when(mockReq.getHeader("X-Forwarded-For")).thenReturn(null);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        lenient().when(mockRes.getWriter()).thenReturn(pw);

        // Primeras 20 consultas deben pasar
        for (int i = 1; i <= 20; i++) {
            rateLimiter.doFilter(mockReq, mockRes, mockChain);
        }
        verify(mockChain, times(20)).doFilter(mockReq, mockRes);
        verify(mockRes, never()).setStatus(429);

        // La consulta 21 debe ser bloqueada con 429
        rateLimiter.doFilter(mockReq, mockRes, mockChain);
        verify(mockRes).setStatus(429);
        verify(mockChain, times(20)).doFilter(mockReq, mockRes); // No se incrementa
    }

    /**
     * Caso 6: Cambio dinámico de parámetro -> cambiar patente_vigencia_horas de 48 a 72
     * hace que la patente con movimiento hace 60 horas pase a vigente = true
     */
    @Test
    void testCaso6_CambioDinamicoVigenciaHoras_De48a72_PasaAVigenteTrue() {
        LocalDateTime mov60h = LocalDateTime.now().minusHours(60);
        Date fechaMov = Date.from(mov60h.atZone(ZoneId.systemDefault()).toInstant());
        String horaMov = String.format("%02d:%02d:00", mov60h.getHour(), mov60h.getMinute());

        Object[] row = new Object[]{
                fechaMov,
                horaMov,
                "RECEPCION_EN_PLANTA",
                "DPA-888",
                "Huiro Flotador",
                4500.0,
                "Mejillones",
                "Planta Puerto Angamos"
        };

        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.getResultList()).thenReturn(Collections.singletonList(row));

        // Con 48 horas (default) -> vigente = false
        when(configService.getInt(eq("patente_vigencia_horas"), anyInt())).thenReturn(48);
        Map<String, Object> result48 = patenteService.consultar("LP-12-34", request);
        assertEquals(false, result48.get("vigente"));

        // Se reconfigura dinámicamente a 72 horas -> vigente = true
        when(configService.getInt(eq("patente_vigencia_horas"), anyInt())).thenReturn(72);
        Map<String, Object> result72 = patenteService.consultar("LP-12-34", request);
        assertEquals(true, result72.get("vigente"));
    }
}
