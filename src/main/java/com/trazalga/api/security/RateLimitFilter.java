package com.trazalga.api.security;

import java.io.IOException;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.trazalga.api.services.ConfiguracionGeneralService;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Deque<Long>> requestsByIp = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private ConfiguracionGeneralService configService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (!uri.contains("/public/patentes") && !uri.startsWith("/api/public/")) {
            chain.doFilter(request, response);
            return;
        }

        int maxPorMinuto = 20;
        if (configService != null) {
            maxPorMinuto = configService.getInt("patente_consulta_max_por_minuto", 20);
        }

        String ip = getClientIp(request);
        Deque<Long> timestamps = requestsByIp.computeIfAbsent(ip, k -> new ConcurrentLinkedDeque<>());

        long ahora = System.currentTimeMillis();
        timestamps.removeIf(t -> ahora - t > 60_000);

        if (timestamps.size() >= maxPorMinuto) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Demasiadas consultas. Intente en un minuto.\",\"status\":429}");
            return;
        }

        timestamps.addLast(ahora);
        chain.doFilter(request, response);
    }

    public void resetForIp(String ip) {
        requestsByIp.remove(ip);
    }

    public void clearAll() {
        requestsByIp.clear();
    }

    private String getClientIp(HttpServletRequest request) {
        if (request == null) return "127.0.0.1";
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
    }
}
