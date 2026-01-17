package com.trazalga.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // http
        // .cors(Customizer.withDefaults()) // <--- AGREGA ESTA LÍNEA (Importante)
        // .csrf(csrf -> csrf.disable())
        // .authorizeHttpRequests(auth -> auth
        // .requestMatchers("/api/auth/**").permitAll()
        // .requestMatchers("/api/stress-test/**").permitAll()
        // .anyRequest().authenticated());

        // return http.build();

        http
                .csrf(csrf -> csrf.disable()) // Asegúrate de que esto esté deshabilitado para peticiones POST
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        // Permitimos el login y registro
                        .requestMatchers("/api/auth/**").permitAll()

                        // --- AGREGA ESTA LÍNEA ---
                        .requestMatchers("/api/stress-test/**").permitAll()

                        // Todo lo demás sigue requiriendo token
                        .anyRequest().authenticated());

        return http.build();

    }
}