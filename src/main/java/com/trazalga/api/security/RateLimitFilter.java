package com.trazalga.api.security;

import java.io.IOException;
import java.util.Arrays;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.trazalga.api.services.ConfiguracionGeneralService;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Deque<Long>> requestsByIp = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private ConfiguracionGeneralService configService;

    @Value("${trazalga.proxy.confiables:}")
    private String proxiesConfiables = "";

    public void setProxiesConfiables(String proxiesConfiables) {
        this.proxiesConfiables = proxiesConfiables != null ? proxiesConfiables : "";
    }

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

    public int trackedIpCount() {
        return requestsByIp.size();
    }

    /**
     * ZC.6: Purga cada 5 minutos las IP que ya no tienen peticiones dentro de la ventana de 1 minuto.
     */
    @Scheduled(fixedRate = 300_000)
    public void purgarInactivas() {
        long ahora = System.currentTimeMillis();
        requestsByIp.entrySet().removeIf(entry -> {
            Deque<Long> ts = entry.getValue();
            ts.removeIf(t -> ahora - t > 60_000);
            return ts.isEmpty();
        });
    }

    private String getClientIp(HttpServletRequest request) {
        return resolverIpCliente(request, proxiesConfiables);
    }

    /**
     * ZC.6: Solo acepta {@code X-Forwarded-For} si {@code getRemoteAddr()} corresponde
     * a un proxy configurado en {@code trazalga.proxy.confiables}.
     */
    public static String resolverIpCliente(HttpServletRequest request, String proxiesConfiablesRaw) {
        if (request == null) return "127.0.0.1";
        String remoteAddr = request.getRemoteAddr() != null ? request.getRemoteAddr().trim() : "127.0.0.1";
        if (proxiesConfiablesRaw != null && !proxiesConfiablesRaw.isBlank()) {
            Set<String> trusted = Arrays.stream(proxiesConfiablesRaw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
            if (trusted.contains(remoteAddr)) {
                String xf = request.getHeader("X-Forwarded-For");
                if (xf != null && !xf.isBlank()) {
                    String primera = xf.split(",")[0].trim();
                    if (!primera.isEmpty()) {
                        return primera;
                    }
                }
            }
        }
        return remoteAddr;
    }
}
