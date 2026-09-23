package com.trazalga.api.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.trazalga.api.security.JwtRequestFilter;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtRequestFilter jwtRequestFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\": \"No autorizado\", \"message\": \"" + authException.getMessage() + "\"}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\": \"Acceso denegado\", \"message\": \"No tiene permisos suficientes para realizar esta acción.\"}");
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        // 1. Opciones CORS preflight siempre permitidas
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // 2. Rutas públicas de autenticación
                        .requestMatchers("/api/auth/**", "/auth/**").permitAll()

                        // 3. Endpoint de usuario actual
                        .requestMatchers("/api/usuarios/me", "/usuario/me").authenticated()

                        // 4. Endpoints normativos de configuración y administración: solo ADMINISTRADOR
                        .requestMatchers(HttpMethod.POST, "/api/factores-conversion/recalcular-historico", "/factorconversion/recalcular-historico").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/configuracion-general/**", "/configuraciongeneral/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/configuracion-general/**", "/configuraciongeneral/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/configuracion-general/**", "/configuraciongeneral/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/factores-conversion/**", "/factorconversion/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/factores-conversion/**", "/factorconversion/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/factores-conversion/**", "/factorconversion/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/cuotas/**", "/cuota/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/cuotas/**", "/cuota/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/cuotas/**", "/cuota/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/limites-extraccion-diario/**", "/limiteextracciondiario/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/limites-extraccion-diario/**", "/limiteextracciondiario/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/limites-extraccion-diario/**", "/limiteextracciondiario/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/vedas/**", "/veda/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/vedas/**", "/veda/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/vedas/**", "/veda/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/macrozonas/**", "/macrozona/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/macrozonas/**", "/macrozona/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/macrozonas/**", "/macrozona/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/amerb-especies-habilitadas/**", "/amerbespecieshabilitada/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/amerb-especies-habilitadas/**", "/amerbespecieshabilitada/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/amerb-especies-habilitadas/**", "/amerbespecieshabilitada/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/usuarios/**", "/usuario/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/usuarios/**", "/usuario/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/usuarios/**", "/usuario/**").hasRole("ADMIN")

                        // 5. Resolución de hallazgos: ADMINISTRADOR o FISCALIZADOR
                        .requestMatchers(HttpMethod.PUT, "/api/declaracion-marcas/*/resolver", "/declaracion-marcas/*/resolver", "/api/declaracion-marcas/**/resolver").hasAnyRole("ADMIN", "FISCALIZADOR")

                        // 6. Todo lo demás por ahora permitido para compatibilidad operativa
                        .anyRequest().permitAll()
                )
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());

        http.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}