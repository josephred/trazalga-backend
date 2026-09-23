package com.trazalga.api.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class JwtRequestFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtils jwtUtils;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        String rut = null;
        String jwt = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwt = authHeader.substring(7);
            try {
                rut = jwtUtils.extractRut(jwt);
            } catch (Exception e) {
                log.warn("Token JWT inválido o expirado: {}", e.getMessage());
            }
        }

        if (rut != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            if (jwtUtils.validateToken(jwt)) {
                String perfil = jwtUtils.extractPerfil(jwt);
                List<GrantedAuthority> authorities = new ArrayList<>();
                authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

                if (perfil != null) {
                    String norm = perfil.trim().toUpperCase();
                    if (norm.contains("ADMIN") || norm.contains("ADMINISTRADOR")) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                    } else if (norm.contains("FISC") || norm.contains("FISCALIZADOR")) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_FISCALIZADOR"));
                    } else if (norm.contains("AUDIT") || norm.contains("AUDITOR")) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_AUDITOR"));
                    }
                }

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(rut, null, authorities);
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }
}
