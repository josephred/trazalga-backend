package com.trazalga.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.disable()) // Deshabilitar CORS para pruebas o configurar correctamente
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll() // ABRIMOS TODO PARA PROBAR
                )
                .httpBasic(basic -> basic.disable()) // ESTO ELIMINA LA VENTANITA
                .formLogin(form -> form.disable());

        return http.build();
    }
}

// package com.trazalga.api.config;

// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;
// import org.springframework.security.config.Customizer;
// import
// org.springframework.security.config.annotation.web.builders.HttpSecurity;
// import
// org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
// import org.springframework.security.config.http.SessionCreationPolicy;
// import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
// import org.springframework.security.web.SecurityFilterChain;
// import org.springframework.web.cors.CorsConfiguration;
// import org.springframework.web.cors.CorsConfigurationSource;
// import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

// import java.util.Arrays;

// @Configuration
// @EnableWebSecurity
// public class SecurityConfig {

// @Bean
// public BCryptPasswordEncoder passwordEncoder() {
// return new BCryptPasswordEncoder();
// }

// @Bean
// public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
// http
// .cors(Customizer.withDefaults())
// .csrf(csrf -> csrf.disable())
// // 1. DESACTIVAR la sesión (obligatorio para APIs con JWT)
// .sessionManagement(session ->
// session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
// .authorizeHttpRequests(auth -> auth
// // Rutas de autenticación
// .requestMatchers("/api/auth/**", "/api/stress-test/**").permitAll()

// // Rutas maestras (permitimos raíz y subrutas)
// .requestMatchers(
// "/comuna", "/comuna/**",
// "/extracciontipo", "/extracciontipo/**",
// "/especie", "/especie/**",
// "/composicion", "/composicion/**",
// "/caleta", "/caleta/**",
// "/humedadestado", "/humedadestado/**",
// "/perfil", "/perfil/**",
// "/usuario", "/usuario/**",
// "/region", "/region/**",
// "/embarcacion", "/embarcacion/**",
// "/buzo", "/buzo/**",
// "/amerb", "/amerb/**",
// "/patente", "/patente/**",
// "/planta", "/planta/**",
// "/producto", "/producto/**",
// "/declaracionrecolector/**",
// "/declaracionarmador/**",
// "/declaracionarea/**",
// "/declaracioncomercializador/**",
// "/declaracionplantaabastecimiento/**",
// "/declaracionplantaproduccion/**",
// "/declaracionplantadestino/**")
// .permitAll()

// .anyRequest().authenticated())
// // 2. DESACTIVAR explícitamente Basic Auth y Form Login para que no salga la
// // ventana
// .httpBasic(basic -> basic.disable())
// .formLogin(form -> form.disable());

// return http.build();
// }

// @Bean
// public CorsConfigurationSource corsConfigurationSource() {
// CorsConfiguration configuration = new CorsConfiguration();
// configuration.setAllowedOriginPatterns(Arrays.asList("*"));
// configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE",
// "OPTIONS", "HEAD"));
// configuration.setAllowedHeaders(Arrays.asList("Authorization",
// "Content-Type", "X-Requested-With", "Accept"));
// configuration.setAllowCredentials(true);

// UrlBasedCorsConfigurationSource source = new
// UrlBasedCorsConfigurationSource();
// source.registerCorsConfiguration("/**", configuration);
// return source;
// }
// }