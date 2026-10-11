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

import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import com.trazalga.api.security.JwtRequestFilter;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Autowired
    private JwtRequestFilter jwtRequestFilter;

    @Autowired
    private com.trazalga.api.security.RateLimitFilter rateLimitFilter;

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

                        // 2. Rutas públicas de autenticación y consulta ciudadana
                        .requestMatchers("/api/auth/**", "/auth/**").permitAll()
                        .requestMatchers("/api/public/**", "/public/**", "/patente/**").permitAll()

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
                        .requestMatchers(HttpMethod.PUT, "/api/cuotas/*/cerrar", "/cuota/*/cerrar").hasAnyRole("ADMIN", "FISCALIZADOR")
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
                        .requestMatchers("/api/admin/**", "/admin/**").hasRole("ADMIN")

                        // 5. Hallazgos e indicadores normativos (TA.3 / TX.1): ADMIN, FISCALIZADOR o AUDITOR
                        .requestMatchers("/api/hallazgos/**", "/hallazgos/**").hasAnyRole("ADMIN", "FISCALIZADOR", "AUDITOR")

                        // 6. Reportes analíticos de trazabilidad (TX.1): ADMIN, FISCALIZADOR o AUDITOR
                        .requestMatchers(HttpMethod.GET, "/api/reportes/**", "/reportes/**").hasAnyRole("ADMIN", "FISCALIZADOR", "AUDITOR")

                        // 7. Cuotas (consulta): ADMIN, FISCALIZADOR o AUDITOR (TX.1)
                        .requestMatchers(HttpMethod.GET, "/api/cuotas/**", "/cuota/**").hasAnyRole("ADMIN", "FISCALIZADOR", "AUDITOR")
                        .requestMatchers(HttpMethod.GET, "/api/usuarios/buscar", "/usuario/buscar").hasAnyRole("ADMIN", "FISCALIZADOR", "AUDITOR")

                        // 8. Gestión y resolución de hallazgos/marcas: ADMINISTRADOR o FISCALIZADOR (TX.1)
                        .requestMatchers(HttpMethod.PUT, 
                                "/api/declaracion-marcas/*/resolver", 
                                "/declaracion-marcas/*/resolver", 
                                "/declaracionmarca/*/resolver",
                                "/api/declaracion-marcas/**/resolver").hasAnyRole("ADMIN", "FISCALIZADOR")
                        .requestMatchers(HttpMethod.PUT, 
                                "/api/declaracion-marcas/*/derivar-citacion", 
                                "/declaracion-marcas/*/derivar-citacion", 
                                "/declaracionmarca/*/derivar-citacion",
                                "/api/declaracion-marcas/**/derivar-citacion",
                                "/api/declaracion-marcas/declaracion/**/derivar-citacion",
                                "/declaracion-marcas/declaracion/**/derivar-citacion",
                                "/declaracionmarca/declaracion/**/derivar-citacion").hasAnyRole("ADMIN", "FISCALIZADOR")
                        .requestMatchers(HttpMethod.PUT, 
                                "/api/declaracion-marcas/*/reabrir", 
                                "/declaracion-marcas/*/reabrir", 
                                "/declaracionmarca/*/reabrir",
                                "/api/declaracion-marcas/**/reabrir").hasAnyRole("ADMIN", "FISCALIZADOR")

                        // 9. Consulta de marcas: ADMINISTRADOR, FISCALIZADOR o AUDITOR (TX.1)
                        .requestMatchers(HttpMethod.GET, 
                                "/api/declaracion-marcas/**", 
                                "/declaracion-marcas/**", 
                                "/declaracionmarca/**").hasAnyRole("ADMIN", "FISCALIZADOR", "AUDITOR")

                        // 10. Búsqueda de usuarios con RUT y datos personales (TX.1)
                        .requestMatchers(HttpMethod.GET, "/api/usuarios/buscar", "/usuario/buscar").hasAnyRole("ADMIN", "FISCALIZADOR", "AUDITOR")

                        // 11. Consultas de trazabilidad y búsqueda por folio: ADMINISTRADOR, FISCALIZADOR o AUDITOR (T2.2)
                        .requestMatchers("/api/consultas/**", "/consultas/**").hasAnyRole("ADMIN", "FISCALIZADOR", "AUDITOR")

                        // 12. Todo lo demás permitido para compatibilidad operativa de la app móvil
                        .anyRequest().permitAll()
                )
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());

        http.addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);
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