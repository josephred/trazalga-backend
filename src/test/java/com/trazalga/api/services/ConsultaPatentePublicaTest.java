package com.trazalga.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.*;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.dto.ConsultaPatentePublicaDTO;
import com.trazalga.api.repositories.IConsultaPatenteLogRepository;
import com.trazalga.api.security.RateLimitFilter;
import com.trazalga.api.services.EstadoTrasladoPatente.EvaluacionPatente;

@ExtendWith(MockitoExtension.class)
public class ConsultaPatentePublicaTest {

    @Mock
    private EstadoTrasladoPatente estadoTrasladoPatente;

    @Mock
    private ConfiguracionGeneralService configService;

    @Mock
    private IConsultaPatenteLogRepository logRepository;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private ConsultaPatenteService patenteService;

    private static final LocalDateTime AHORA_REFERENCIA = LocalDateTime.of(2026, 10, 22, 18, 0, 0);

    @BeforeEach
    void setUp() {
        lenient().when(configService.getInt(eq("patente_vigencia_horas"), anyInt())).thenReturn(48);
        lenient().when(configService.getInt(eq("patente_consulta_max_por_minuto"), anyInt())).thenReturn(20);
        lenient().when(configService.getValor(eq("consulta_publica_texto_proteccion"), anyString())).thenReturn("Texto legal");
        lenient().when(request.getRemoteAddr()).thenReturn("192.168.1.50");
    }

    private static final Set<String> WHITELIST_AUTORIZADO = Set.of(
            "patente", "resultado", "fechaHoraAutorizacion", "comunaDestino", "tipoAlga", "texto", "consultadoEn"
    );

    private static final Set<String> WHITELIST_SIN_AUTORIZACION = Set.of(
            "patente", "resultado", "texto", "consultadoEn"
    );

    @Test
    @DisplayName("TN.6 Caso 1: Autorizado -> respuesta pública contiene exactamente la lista blanca de AUTORIZADO")
    void testCaso1_Autorizado() {
        EvaluacionPatente ev = EvaluacionPatente.builder()
                .patente("ABCD12")
                .estado(EstadoTrasladoPatente.ESTADO_AUTORIZADO)
                .resultadoPublico(EstadoTrasladoPatente.RESULTADO_PUBLICO_AUTORIZADO)
                .fechaHoraAutorizacion(LocalDateTime.of(2026, 10, 22, 8, 0, 0))
                .comunaDestino("Coquimbo")
                .tipoAlga("Huiro negro")
                .consultadoEn(AHORA_REFERENCIA)
                .build();

        when(estadoTrasladoPatente.evaluar(eq("ABCD12"), any())).thenReturn(ev);

        ConsultaPatentePublicaDTO dto = patenteService.consultarPublica("ABCD12", request, AHORA_REFERENCIA);
        Map<String, Object> map = dto.toMap();

        assertEquals("ABCD12", map.get("patente"));
        assertEquals("AUTORIZADO", map.get("resultado"));
        assertEquals("2026-10-22T08:00:00", map.get("fechaHoraAutorizacion"));
        assertEquals("Coquimbo", map.get("comunaDestino"));
        assertEquals("Huiro negro", map.get("tipoAlga"));
        assertEquals("Texto legal", map.get("texto"));
        assertEquals("2026-10-22T18:00:00", map.get("consultadoEn"));

        assertEquals(WHITELIST_AUTORIZADO, map.keySet(), "Las claves deben coincidir estrictamente con la lista blanca");
        verificarAusenciaDatosPersonales(map);
    }

    @Test
    @DisplayName("TN.6 Caso 2: Finalizado -> resultado público es SIN_AUTORIZACION_VIGENTE y no expone destino ni fechas")
    void testCaso2_Finalizado() {
        EvaluacionPatente ev = EvaluacionPatente.builder()
                .patente("ABCD12")
                .estado(EstadoTrasladoPatente.ESTADO_FINALIZADO)
                .resultadoPublico(EstadoTrasladoPatente.RESULTADO_PUBLICO_SIN_AUTORIZACION)
                .fechaHoraAutorizacion(LocalDateTime.of(2026, 10, 22, 8, 0, 0))
                .comunaDestino("Coquimbo")
                .tipoAlga("Huiro negro")
                .consultadoEn(AHORA_REFERENCIA)
                .build();

        when(estadoTrasladoPatente.evaluar(eq("ABCD12"), any())).thenReturn(ev);

        Map<String, Object> map = patenteService.consultar("ABCD12", request, AHORA_REFERENCIA);

        assertEquals("ABCD12", map.get("patente"));
        assertEquals("SIN_AUTORIZACION_VIGENTE", map.get("resultado"));
        assertNull(map.get("fechaHoraAutorizacion"));
        assertNull(map.get("comunaDestino"));
        assertNull(map.get("tipoAlga"));

        assertEquals(WHITELIST_SIN_AUTORIZACION, map.keySet());
        verificarAusenciaDatosPersonales(map);
    }

    @Test
    @DisplayName("TN.6 Caso 3: Vencido -> resultado público es SIN_AUTORIZACION_VIGENTE")
    void testCaso3_Vencido() {
        EvaluacionPatente ev = EvaluacionPatente.builder()
                .patente("ABCD12")
                .estado(EstadoTrasladoPatente.ESTADO_VENCIDO)
                .resultadoPublico(EstadoTrasladoPatente.RESULTADO_PUBLICO_SIN_AUTORIZACION)
                .consultadoEn(AHORA_REFERENCIA)
                .build();

        when(estadoTrasladoPatente.evaluar(eq("ABCD12"), any())).thenReturn(ev);

        Map<String, Object> map = patenteService.consultar("ABCD12", request, AHORA_REFERENCIA);

        assertEquals("SIN_AUTORIZACION_VIGENTE", map.get("resultado"));
        assertEquals(WHITELIST_SIN_AUTORIZACION, map.keySet());
        verificarAusenciaDatosPersonales(map);
    }

    @Test
    @DisplayName("TN.6 Caso 4: Retenido -> resultado público es SIN_AUTORIZACION_VIGENTE")
    void testCaso4_Retenido() {
        EvaluacionPatente ev = EvaluacionPatente.builder()
                .patente("ABCD12")
                .estado(EstadoTrasladoPatente.ESTADO_RETENIDO)
                .resultadoPublico(EstadoTrasladoPatente.RESULTADO_PUBLICO_SIN_AUTORIZACION)
                .motivoRetencion("Retenido por RETENCION_EXCEDIDA")
                .hallazgoMarca("RETENCION_EXCEDIDA")
                .consultadoEn(AHORA_REFERENCIA)
                .build();

        when(estadoTrasladoPatente.evaluar(eq("ABCD12"), any())).thenReturn(ev);

        Map<String, Object> map = patenteService.consultar("ABCD12", request, AHORA_REFERENCIA);

        assertEquals("SIN_AUTORIZACION_VIGENTE", map.get("resultado"));
        assertFalse(map.containsKey("motivoRetencion"), "No debe exponer motivoRetencion al público");
        assertFalse(map.containsKey("hallazgoMarca"), "No debe exponer marcas al público");
        assertEquals(WHITELIST_SIN_AUTORIZACION, map.keySet());
        verificarAusenciaDatosPersonales(map);
    }

    @Test
    @DisplayName("TN.6 Caso 5: Anulado / Inexistente -> resultado público es SIN_AUTORIZACION_VIGENTE")
    void testCaso5_Inexistente() {
        EvaluacionPatente ev = EvaluacionPatente.builder()
                .patente("ZZ9999")
                .estado(EstadoTrasladoPatente.ESTADO_SIN_REGISTROS)
                .resultadoPublico(EstadoTrasladoPatente.RESULTADO_PUBLICO_SIN_AUTORIZACION)
                .consultadoEn(AHORA_REFERENCIA)
                .build();

        when(estadoTrasladoPatente.evaluar(eq("ZZ9999"), any())).thenReturn(ev);

        Map<String, Object> map = patenteService.consultar("ZZ-99-99", request, AHORA_REFERENCIA);

        assertEquals("ZZ9999", map.get("patente"));
        assertEquals("SIN_AUTORIZACION_VIGENTE", map.get("resultado"));
        assertEquals(WHITELIST_SIN_AUTORIZACION, map.keySet());
        verificarAusenciaDatosPersonales(map);
    }

    @Test
    @DisplayName("TN.6 Caso 6: Rampla -> consulta con JK1234 retorna AUTORIZADO con lista blanca")
    void testCaso6_Rampla() {
        EvaluacionPatente ev = EvaluacionPatente.builder()
                .patente("JK1234")
                .estado(EstadoTrasladoPatente.ESTADO_AUTORIZADO)
                .resultadoPublico(EstadoTrasladoPatente.RESULTADO_PUBLICO_AUTORIZADO)
                .fechaHoraAutorizacion(LocalDateTime.of(2026, 10, 22, 8, 0, 0))
                .comunaDestino("Coquimbo")
                .tipoAlga("Huiro negro")
                .consultadoEn(AHORA_REFERENCIA)
                .build();

        when(estadoTrasladoPatente.evaluar(eq("JK1234"), any())).thenReturn(ev);

        Map<String, Object> map = patenteService.consultar("JK1234", request, AHORA_REFERENCIA);

        assertEquals("JK1234", map.get("patente"));
        assertEquals("AUTORIZADO", map.get("resultado"));
        assertEquals(WHITELIST_AUTORIZADO, map.keySet());
        verificarAusenciaDatosPersonales(map);
    }

    @Test
    @DisplayName("TN.6: Rate Limiting -> consulta número 21 en un minuto desde la misma IP responde 429")
    void testCasoRateLimiting_21ConsultasRetornan429() throws Exception {
        RateLimitFilter rateLimiter = new RateLimitFilter();
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

        for (int i = 1; i <= 20; i++) {
            rateLimiter.doFilter(mockReq, mockRes, mockChain);
        }
        verify(mockChain, times(20)).doFilter(mockReq, mockRes);
        verify(mockRes, never()).setStatus(429);

        // Intento 21
        rateLimiter.doFilter(mockReq, mockRes, mockChain);
        verify(mockRes).setStatus(429);
        verify(mockChain, times(20)).doFilter(mockReq, mockRes);
    }

    @Test
    @DisplayName("QC.2: consulta_publica_muestra_comuna=false omite comunaDestino incluso cuando está AUTORIZADO")
    void testAutorizado_OcultaComunaSiParametroFalse() {
        EvaluacionPatente ev = EvaluacionPatente.builder()
                .patente("ABCD12")
                .estado(EstadoTrasladoPatente.ESTADO_AUTORIZADO)
                .resultadoPublico(EstadoTrasladoPatente.RESULTADO_PUBLICO_AUTORIZADO)
                .fechaHoraAutorizacion(LocalDateTime.of(2026, 9, 25, 14, 30, 0))
                .comunaDestino("Coquimbo")
                .tipoAlga("Lessonia trabeculata")
                .consultadoEn(AHORA_REFERENCIA)
                .build();

        when(estadoTrasladoPatente.evaluar(eq("ABCD12"), any())).thenReturn(ev);
        when(configService.getValor(eq("consulta_publica_muestra_comuna"), anyString())).thenReturn("false");
        when(configService.getValor(eq("consulta_publica_grafica"), anyString())).thenReturn("");

        Map<String, Object> map = patenteService.consultar("ABCD12", null, LocalDateTime.of(2026, 9, 25, 15, 0, 0));

        assertEquals("AUTORIZADO", map.get("resultado"));
        assertFalse(map.containsKey("comunaDestino"), "comunaDestino debe omitirse si consulta_publica_muestra_comuna=false");
        Set<String> esperadoSinComuna = new java.util.HashSet<>(WHITELIST_AUTORIZADO);
        esperadoSinComuna.remove("comunaDestino");
        assertEquals(esperadoSinComuna, map.keySet());
        verificarAusenciaDatosPersonales(map);
    }

    @Test
    @DisplayName("QC.2: AUTORIZADO nunca expone fonoDenuncia ni whatsappDenuncia aunque estén configurados")
    void testAutorizado_NuncaExponeDenunciaAunqueConfigurados() {
        EvaluacionPatente ev = EvaluacionPatente.builder()
                .patente("ABCD12")
                .estado(EstadoTrasladoPatente.ESTADO_AUTORIZADO)
                .resultadoPublico(EstadoTrasladoPatente.RESULTADO_PUBLICO_AUTORIZADO)
                .fechaHoraAutorizacion(LocalDateTime.of(2026, 9, 25, 14, 30, 0))
                .comunaDestino("Coquimbo")
                .tipoAlga("Alga parda")
                .consultadoEn(AHORA_REFERENCIA)
                .build();

        when(estadoTrasladoPatente.evaluar(eq("ABCD12"), any())).thenReturn(ev);
        lenient().when(configService.getValor(eq("consulta_publica_fono_denuncia"), anyString())).thenReturn("800123456");
        lenient().when(configService.getValor(eq("consulta_publica_whatsapp"), anyString())).thenReturn("56911112222");
        when(configService.getValor(eq("consulta_publica_grafica"), anyString())).thenReturn("");

        Map<String, Object> map = patenteService.consultar("ABCD12", null, LocalDateTime.of(2026, 9, 25, 15, 0, 0));

        assertFalse(map.containsKey("fonoDenuncia"));
        assertFalse(map.containsKey("whatsappDenuncia"));
        assertEquals(WHITELIST_AUTORIZADO, map.keySet());
        verificarAusenciaDatosPersonales(map);
    }

    @Test
    @DisplayName("QC.2: SIN_AUTORIZACION_VIGENTE agrega fonoDenuncia y whatsappDenuncia solo cuando no están vacíos manteniendo lista blanca")
    void testSinAutorizacion_IncluyeDenunciaSoloSiNoVacios() {
        EvaluacionPatente ev = EvaluacionPatente.builder()
                .patente("ABCD12")
                .estado(EstadoTrasladoPatente.ESTADO_VENCIDO)
                .resultadoPublico(EstadoTrasladoPatente.RESULTADO_PUBLICO_SIN_AUTORIZACION)
                .consultadoEn(AHORA_REFERENCIA)
                .build();

        when(estadoTrasladoPatente.evaluar(eq("ABCD12"), any())).thenReturn(ev);
        when(configService.getValor(eq("consulta_publica_fono_denuncia"), anyString())).thenReturn(" +56 800 111 222 ");
        when(configService.getValor(eq("consulta_publica_whatsapp"), anyString())).thenReturn("56911112222");
        when(configService.getValor(eq("consulta_publica_grafica"), anyString())).thenReturn("");

        Map<String, Object> mapAmbos = patenteService.consultar("ABCD12", null, LocalDateTime.of(2026, 9, 25, 15, 0, 0));
        assertEquals("+56 800 111 222", mapAmbos.get("fonoDenuncia"));
        assertEquals("56911112222", mapAmbos.get("whatsappDenuncia"));
        Set<String> esperadoAmbos = new java.util.HashSet<>(WHITELIST_SIN_AUTORIZACION);
        esperadoAmbos.add("fonoDenuncia");
        esperadoAmbos.add("whatsappDenuncia");
        assertEquals(esperadoAmbos, mapAmbos.keySet());
        verificarAusenciaDatosPersonales(mapAmbos);

        when(configService.getValor(eq("consulta_publica_whatsapp"), anyString())).thenReturn("   ");
        Map<String, Object> mapSoloFono = patenteService.consultar("ABCD12", null, LocalDateTime.of(2026, 9, 25, 15, 1, 0));
        assertTrue(mapSoloFono.containsKey("fonoDenuncia"));
        assertFalse(mapSoloFono.containsKey("whatsappDenuncia"));
    }

    private void verificarAusenciaDatosPersonales(Map<String, Object> map) {
        List<String> camposProhibidos = List.of(
                "rut", "rutChofer", "chofer", "choferTransporte", "nombreComercializador",
                "usuarioId", "folio", "kg", "kilos", "destino", "comunaOrigen", "planta", "valor",
                "ipHash", "ip_hash"
        );
        for (String campo : camposProhibidos) {
            assertFalse(map.containsKey(campo), "La respuesta pública NO debe contener '" + campo + "'");
        }
    }
}
