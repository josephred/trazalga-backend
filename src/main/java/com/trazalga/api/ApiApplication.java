package com.trazalga.api;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;
import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class ApiApplication {

    public static final ZoneId ZONA_CHILE = ZoneId.of("America/Santiago");

    @PostConstruct
    public void initTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZONA_CHILE));
    }

    @Bean
    public Clock clock() {
        return Clock.system(ZONA_CHILE);
    }

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone(ZONA_CHILE));
        SpringApplication.run(ApiApplication.class, args);
    }
}
