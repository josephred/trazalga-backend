package com.trazalga.api.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.ServletException;

@ExtendWith(MockitoExtension.class)
public class SecurityRecalculoTest {

    @Mock
    private JwtUtils jwtUtils;

    @InjectMocks
    private JwtRequestFilter jwtRequestFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testRequestWithoutToken_leavesContextUnauthenticated() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/factores-conversion/recalcular-historico");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        jwtRequestFilter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "Petición anónima no debe tener autenticación en SecurityContext");
    }

    @Test
    void testRequestWithAdminToken_authenticatesWithAdminRole() throws ServletException, IOException {
        String token = "valid-admin-jwt-token";
        String rut = "11.111.111-1";

        when(jwtUtils.extractRut(token)).thenReturn(rut);
        when(jwtUtils.validateToken(token)).thenReturn(true);
        when(jwtUtils.extractPerfil(token)).thenReturn("ADMINISTRADOR");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        request.setRequestURI("/api/factores-conversion/recalcular-historico");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        jwtRequestFilter.doFilter(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication(),
                "Petición con token válido debe autenticarse");
        assertEquals(rut, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")),
                "El token de perfil ADMINISTRADOR debe otorgar ROLE_ADMIN");
    }

    @Test
    void testRequestWithNonAdminToken_doesNotGrantAdminRole() throws ServletException, IOException {
        String token = "valid-recolector-jwt-token";
        String rut = "22.222.222-2";

        when(jwtUtils.extractRut(token)).thenReturn(rut);
        when(jwtUtils.validateToken(token)).thenReturn(true);
        when(jwtUtils.extractPerfil(token)).thenReturn("RECOLECTOR DE ORILLA");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        request.setRequestURI("/api/factores-conversion/recalcular-historico");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        jwtRequestFilter.doFilter(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertFalse(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")),
                "El token de perfil no administrador NO debe otorgar ROLE_ADMIN");
    }

    @Test
    void testRequestWithFiscalizadorToken_grantsFiscalizadorRole() throws ServletException, IOException {
        String token = "valid-fisc-jwt-token";
        String rut = "222-2";

        when(jwtUtils.extractRut(token)).thenReturn(rut);
        when(jwtUtils.validateToken(token)).thenReturn(true);
        when(jwtUtils.extractPerfil(token)).thenReturn("FISCALIZADOR");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        request.setRequestURI("/api/declaracion-marcas/1/resolver");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        jwtRequestFilter.doFilter(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_FISCALIZADOR")),
                "El token de perfil FISCALIZADOR debe otorgar ROLE_FISCALIZADOR");
        assertFalse(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")),
                "El token de perfil FISCALIZADOR NO debe otorgar ROLE_ADMIN");
    }

    @Test
    void testRequestWithAuditorToken_grantsAuditorRole() throws ServletException, IOException {
        String token = "valid-auditor-jwt-token";
        String rut = "333-3";

        when(jwtUtils.extractRut(token)).thenReturn(rut);
        when(jwtUtils.validateToken(token)).thenReturn(true);
        when(jwtUtils.extractPerfil(token)).thenReturn("AUDITOR");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        request.setRequestURI("/api/reportes/desembarque-fisico");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        jwtRequestFilter.doFilter(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_AUDITOR")),
                "El token de perfil AUDITOR debe otorgar ROLE_AUDITOR");
        assertFalse(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")),
                "El token de perfil AUDITOR NO debe otorgar ROLE_ADMIN");
    }
}
