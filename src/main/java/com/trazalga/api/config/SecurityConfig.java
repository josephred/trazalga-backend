package com.trazalga.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1. Habilitar CORS con la configuración que definiremos abajo
                .cors(Customizer.withDefaults())
                // 2. Deshabilitar CSRF (necesario para APIs que reciben POST)
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/api/stress-test/**").permitAll()
                        .requestMatchers(
                                "/comuna", "/comuna/**",
                                "/extracciontipo", "/extracciontipo/**",
                                "/especie", "/especie/**",
                                "/composicion", "/composicion/**",
                                "/caleta", "/caleta/**",
                                "/humedadestado", "/humedadestado/**",
                                "/perfil", "/perfil/**",
                                "/usuario", "/usuario/**",
                                "/region", "/region/**",
                                "/embarcacion", "/embarcacion/**",
                                "/buzo", "/buzo/**",
                                "/amerb", "/amerb/**",
                                "/patente", "/patente/**",
                                "/planta", "/planta/**",
                                "/producto", "/producto/**",
                                "/declaracionrecolector/**",
                                "/declaracionarmador/**",
                                "/declaracionarea/**",
                                "/declaracioncomercializador/**",
                                "/declaracionplantaabastecimiento/**",
                                "/declaracionplantaproduccion/**",
                                "/declaracionplantadestino/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    // MEJORA CRÍTICA: Configuración explícita de CORS para evitar el 403 en el
    // "Preflight" (OPTIONS)
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Permitir localhost (desarrollo) y tus dominios de producción (Vercel)
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With", "Accept"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}