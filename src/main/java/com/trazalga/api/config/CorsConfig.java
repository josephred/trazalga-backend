// src/main/java/com/trazalga/api/config/CorsConfig.java
package com.trazalga.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**") // Permitir todas las rutas de la API
                        .allowedOrigins(
                                "http://localhost:5173",
                                "https://trazalga-web.vercel.app", // Tu dominio principal de Vercel
                                "https://trazalga-3vechqvi1-josephreds-projects.vercel.app" // La URL específica del
                                                                                            // error
                )
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }
}