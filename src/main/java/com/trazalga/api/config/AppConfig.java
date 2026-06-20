package com.trazalga.api.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    @Value("${sernapesca.api.connect-timeout-ms:15000}")
    private long connectTimeoutMs;

    @Value("${sernapesca.api.read-timeout-ms:60000}")
    private long readTimeoutMs;

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Cliente HTTP usado para consumir el API público de Sernapesca
     * (poblamiento de datos maestros).
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }
}