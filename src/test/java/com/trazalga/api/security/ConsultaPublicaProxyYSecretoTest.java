package com.trazalga.api.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.trazalga.api.ApiApplication;
import com.trazalga.api.services.ConsultaPatenteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * YC.3 · Pruebas de arranque de perfiles ({@code prod} vs {@code dev}) sin secreto HMAC,
 * aviso {@code INFO} de proxy vacío y consistencia de huella HMAC entre instancias.
 */
class ConsultaPublicaProxyYSecretoTest {

    private static Map<String, Object> propsH2Base() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("spring.datasource.url",
                "jdbc:h2:mem:yc3db_" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH");
        props.put("spring.datasource.driverClassName", "org.h2.Driver");
        props.put("spring.datasource.username", "sa");
        props.put("spring.datasource.password", "");
        props.put("spring.jpa.database-platform", "org.hibernate.dialect.H2Dialect");
        props.put("spring.jpa.hibernate.ddl-auto", "create-drop");
        props.put("spring.jpa.open-in-view", "false");
        props.put("spring.jpa.show-sql", "false");
        props.put("logging.file.name", "");
        props.put("trazalga.consulta.ip-hash-secret", "");
        props.put("trazalga.proxy.confiables", "");
        return props;
    }

    @Test
    @DisplayName("YC.3: Con perfil prod y sin secreto HMAC, el contexto no arranca")
    void perfilProdSinSecreto_fallaElArranque() {
        Throwable thrown = assertThrows(Throwable.class, () -> {
            try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(ApiApplication.class)
                    .web(WebApplicationType.NONE)
                    .profiles("prod")
                    .properties(propsH2Base())
                    .run()) {
                fail("El contexto en perfil prod sin secreto HMAC no debería haber arrancado: " + ctx.getId());
            }
        });

        String cadenaMensajes = concatenarMensajes(thrown);
        assertTrue(cadenaMensajes.contains("la huella de IP cambia en cada reinicio"),
                "El error de arranque debe explicar el riesgo del secreto faltante, pero fue: " + cadenaMensajes);
    }

    @Test
    @DisplayName("YC.3: Con perfil dev y sin secreto HMAC, el contexto arranca y registra WARN + INFO de proxy vacío")
    void perfilDevSinSecreto_arrancaYRegistraWarnEInfoProxy() {
        ListAppender<ILoggingEvent> appenderServicio = new ListAppender<>();
        ListAppender<ILoggingEvent> appenderFiltro = new ListAppender<>();
        appenderServicio.start();
        appenderFiltro.start();

        // Se adjuntan en un ApplicationContextInitializer (después de que Spring Boot
        // inicializa logback-spring.xml y antes de crear los beans y ejecutar @PostConstruct).
        ApplicationContextInitializer<ConfigurableApplicationContext> adjuntarAppenders = ctx -> {
            ((Logger) LoggerFactory.getLogger(ConsultaPatenteService.class)).addAppender(appenderServicio);
            ((Logger) LoggerFactory.getLogger(RateLimitFilter.class)).addAppender(appenderFiltro);
        };

        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(ApiApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("dev")
                .properties(propsH2Base())
                .initializers(adjuntarAppenders)
                .run()) {
            assertTrue(ctx.isActive(), "El contexto dev debe arrancar aun sin secreto HMAC");

            boolean hayWarnSecreto = appenderServicio.list.stream()
                    .anyMatch(e -> e.getLevel() == Level.WARN
                            && e.getFormattedMessage().contains("la huella de IP cambia en cada reinicio"));
            assertTrue(hayWarnSecreto, "Debe registrarse WARN al arrancar sin ip-hash-secret en dev");

            boolean hayInfoProxyVacio = appenderFiltro.list.stream()
                    .anyMatch(e -> e.getLevel() == Level.INFO
                            && e.getFormattedMessage().contains("TRAZALGA_PROXY_CONFIABLES")
                            && e.getFormattedMessage().contains("getRemoteAddr()"));
            assertTrue(hayInfoProxyVacio, "Debe registrarse INFO al arrancar con trazalga.proxy.confiables vacío");
        } finally {
            ((Logger) LoggerFactory.getLogger(ConsultaPatenteService.class)).detachAppender(appenderServicio);
            ((Logger) LoggerFactory.getLogger(RateLimitFilter.class)).detachAppender(appenderFiltro);
        }
    }

    @Test
    @DisplayName("YC.3: La misma IP produce la misma huella HMAC en dos instancias con el mismo secreto")
    void mismaIp_mismaHuellaEnDosInstanciasConMismoSecreto() {
        String secreto = "secreto-compartido-yc3-32-bytes-minimo-2026";
        String ip = "200.14.88.19";

        ConsultaPatenteService instancia1 = new ConsultaPatenteService();
        instancia1.setIpHashSecret(secreto);

        ConsultaPatenteService instancia2 = new ConsultaPatenteService();
        instancia2.setIpHashSecret(secreto);

        ConsultaPatenteService instanciaSinSecreto = new ConsultaPatenteService();

        String h1 = instancia1.hashIp(ip);
        String h2 = instancia2.hashIp(ip);
        String hEfimera = instanciaSinSecreto.hashIp(ip);

        assertEquals(64, h1.length(), "La huella HMAC-SHA-256 debe tener 64 caracteres hexadecimales");
        assertEquals(h1, h2, "Dos instancias con el mismo secreto deben generar exactamente la misma huella");
        assertNotEquals(h1, hEfimera, "Una instancia sin secreto usa clave efímera y produce otra huella");
    }

    private static String concatenarMensajes(Throwable t) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = t;
        while (cur != null) {
            if (cur.getMessage() != null) {
                sb.append(cur.getMessage()).append(" | ");
            }
            cur = cur.getCause();
        }
        return sb.toString();
    }
}
