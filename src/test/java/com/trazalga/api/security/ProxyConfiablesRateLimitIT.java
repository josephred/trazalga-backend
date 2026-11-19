package com.trazalga.api.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.trazalga.api.IntegracionBase;
import com.trazalga.api.services.ConfiguracionGeneralService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * YC.2 / YC.3 · Verifica que {@code TRAZALGA_PROXY_CONFIABLES=10.0.0.5} se vincula al filtro:
 * <ul>
 *   <li>dos pedidos desde {@code 10.0.0.5} con {@code X-Forwarded-For} distintos cuentan por separado;</li>
 *   <li>pedidos desde otra IP con {@code X-Forwarded-For} falso cuentan como esa IP remota;</li>
 *   <li>el {@code WARN} por {@code X-Forwarded-For} no confiable se limita a una vez por hora y por IP remota.</li>
 * </ul>
 */
@AutoConfigureMockMvc
class ProxyConfiablesRateLimitIT extends IntegracionBase {

    private static final String PROP_PROXY = "TRAZALGA_PROXY_CONFIABLES";
    private static final String VALOR_PREVIO;

    static {
        VALOR_PREVIO = System.getProperty(PROP_PROXY);
        System.setProperty(PROP_PROXY, "10.0.0.5");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @Autowired
    private ConfiguracionGeneralService configService;

    private String origMaxPorMinuto;

    @AfterAll
    static void restaurarPropiedadSistema() {
        if (VALOR_PREVIO == null) {
            System.clearProperty(PROP_PROXY);
        } else {
            System.setProperty(PROP_PROXY, VALOR_PREVIO);
        }
    }

    @BeforeEach
    void preparar() {
        rateLimitFilter.clearAll();
        rateLimitFilter.setRelojMs(System::currentTimeMillis);
        origMaxPorMinuto = configService.getValor("patente_consulta_max_por_minuto", "20");
    }

    @AfterEach
    void limpiar() {
        rateLimitFilter.clearAll();
        rateLimitFilter.setRelojMs(System::currentTimeMillis);
        configService.updateConfig("patente_consulta_max_por_minuto", origMaxPorMinuto);
    }

    @Test
    @DisplayName("YC.3: TRAZALGA_PROXY_CONFIABLES=10.0.0.5 separa límites por X-Forwarded-For y rechaza XFF falso de otra IP")
    void proxyConfiableSeparaPorXff_yOtraIpCuentaPorSuRemoteAddrConWarnAcotado() throws Exception {
        configService.updateConfig("patente_consulta_max_por_minuto", "1");

        AtomicLong reloj = new AtomicLong(1_700_000_000_000L);
        rateLimitFilter.setRelojMs(reloj::get);

        Logger loggerFiltro = (Logger) LoggerFactory.getLogger(RateLimitFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        loggerFiltro.addAppender(appender);

        try {
            // 1) Desde proxy confiable 10.0.0.5: dos X-Forwarded-For distintos cuentan por separado
            mockMvc.perform(get("/api/public/patentes/AB1234")
                            .with(req -> {
                                req.setRemoteAddr("10.0.0.5");
                                return req;
                            })
                            .header("X-Forwarded-For", "203.0.113.10"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/public/patentes/AB1234")
                            .with(req -> {
                                req.setRemoteAddr("10.0.0.5");
                                return req;
                            })
                            .header("X-Forwarded-For", "203.0.113.11"))
                    .andExpect(status().isOk());

            // Segundo pedido de la primera IP ciudadana detrás de 10.0.0.5 excede su propio límite (1/min) -> 429
            mockMvc.perform(get("/api/public/patentes/AB1234")
                            .with(req -> {
                                req.setRemoteAddr("10.0.0.5");
                                return req;
                            })
                            .header("X-Forwarded-For", "203.0.113.10"))
                    .andExpect(status().isTooManyRequests());

            // Sin WARN porque 10.0.0.5 es confiable
            long warnsProxyConfiable = appender.list.stream()
                    .filter(e -> e.getLevel() == Level.WARN && e.getFormattedMessage().contains("10.0.0.5"))
                    .count();
            assertEquals(0L, warnsProxyConfiable, "No debe emitir WARN para un proxy confiable");

            // 2) Desde IP NO confiable 198.51.100.7 con X-Forwarded-For falso: cuenta bajo 198.51.100.7
            mockMvc.perform(get("/api/public/patentes/AB1234")
                            .with(req -> {
                                req.setRemoteAddr("198.51.100.7");
                                return req;
                            })
                            .header("X-Forwarded-For", "1.1.1.1"))
                    .andExpect(status().isOk());

            // Segundo pedido desde 198.51.100.7 cambiando el XFF falso a 2.2.2.2 igual recibe 429
            mockMvc.perform(get("/api/public/patentes/AB1234")
                            .with(req -> {
                                req.setRemoteAddr("198.51.100.7");
                                return req;
                            })
                            .header("X-Forwarded-For", "2.2.2.2"))
                    .andExpect(status().isTooManyRequests());

            // WARN limitado a 1 por hora para 198.51.100.7
            long warnsAntesDeHora = appender.list.stream()
                    .filter(e -> e.getLevel() == Level.WARN && e.getFormattedMessage().contains("198.51.100.7"))
                    .count();
            assertEquals(1L, warnsAntesDeHora, "Debe emitir exactamente 1 WARN por hora para la misma IP remota");

            // Tras avanzar 61 minutos (> 1 hora), vuelve a emitir 1 WARN y se renueva la ventana de rate limit
            reloj.addAndGet(61 * 60_000L);
            mockMvc.perform(get("/api/public/patentes/AB1234")
                            .with(req -> {
                                req.setRemoteAddr("198.51.100.7");
                                return req;
                            })
                            .header("X-Forwarded-For", "3.3.3.3"))
                    .andExpect(status().isOk());

            long warnsDespuesDeHora = appender.list.stream()
                    .filter(e -> e.getLevel() == Level.WARN && e.getFormattedMessage().contains("198.51.100.7"))
                    .count();
            assertEquals(2L, warnsDespuesDeHora, "Tras pasar 1 hora debe emitir un nuevo WARN");
        } finally {
            loggerFiltro.detachAppender(appender);
        }
    }
}
