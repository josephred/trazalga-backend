package com.trazalga.api.security;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.trazalga.api.services.UsuarioVersionService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Filtro HTTP que intercepta la cabecera X-App-Version y actualiza de forma asíncrona/en caché
 * la versión de app móvil del usuario autenticado (TP.1).
 */
@Component
@Slf4j
public class AppVersionFilter extends OncePerRequestFilter {

    public static final String HEADER_APP_VERSION = "X-App-Version";

    @Autowired
    private UsuarioVersionService usuarioVersionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String appVersion = request.getHeader(HEADER_APP_VERSION);
            if (appVersion != null && !appVersion.isBlank()) {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated() && !"anonymousUser".equalsIgnoreCase(auth.getName())) {
                    String rut = auth.getName();
                    usuarioVersionService.registrarVersion(rut, appVersion.trim());
                }
            }
        } catch (Exception ex) {
            log.debug("No se pudo registrar X-App-Version: {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
