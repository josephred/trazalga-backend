package com.trazalga.api.security;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.trazalga.api.services.ConfiguracionGeneralService;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final long VENTANA_WARN_XFF_MS = 3_600_000L;

    private final Map<String, Deque<Long>> requestsByIp = new ConcurrentHashMap<>();
    private final Map<String, Long> ultimoWarnXffPorIp = new ConcurrentHashMap<>();
    private final Set<String> ipsReportadasInfo = ConcurrentHashMap.newKeySet();
    private LongSupplier relojMs = System::currentTimeMillis;

    @Autowired(required = false)
    private ConfiguracionGeneralService configService;

    @Value("${trazalga.proxy.confiables:}")
    private String proxiesConfiables = "";

    public void setProxiesConfiables(String proxiesConfiables) {
        this.proxiesConfiables = proxiesConfiables != null ? proxiesConfiables : "";
    }

    void setRelojMs(LongSupplier relojMs) {
        this.relojMs = relojMs != null ? relojMs : System::currentTimeMillis;
    }

    /**
     * YC.1: Registra en INFO cuando {@code trazalga.proxy.confiables} está vacío
     * para que el operador compare la IP que verá el filtro con la de su proxy.
     */
    @PostConstruct
    public void registrarConfiguracionProxy() {
        Set<String> trusted = parsearProxiesConfiables(proxiesConfiables);
        if (trusted.isEmpty()) {
            log.info("YC.1: trazalga.proxy.confiables (TRAZALGA_PROXY_CONFIABLES) está vacío. "
                    + "El filtro usará directamente request.getRemoteAddr() (IP local detectada: {}; "
                    + "en loopback verá 127.0.0.1 o ::1). Compárela con la IP de su proxy inverso.",
                    detectarIpLocal());
        } else {
            log.info("YC.1: trazalga.proxy.confiables configurado con {} proxy(s) confiable(s).", trusted.size());
        }
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

        long ahora = relojMs.getAsLong();
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
        ultimoWarnXffPorIp.clear();
        ipsReportadasInfo.clear();
    }

    public int trackedIpCount() {
        return requestsByIp.size();
    }

    /**
     * ZC.6 / YC.1: Purga cada 5 minutos las IP inactivas en la ventana de 1 minuto
     * y las entradas vencidas (> 1 hora) del aviso de X-Forwarded-For no confiable.
     */
    @Scheduled(fixedRate = 300_000)
    public void purgarInactivas() {
        long ahora = relojMs.getAsLong();
        requestsByIp.entrySet().removeIf(entry -> {
            Deque<Long> ts = entry.getValue();
            ts.removeIf(t -> ahora - t > 60_000);
            return ts.isEmpty();
        });
        ultimoWarnXffPorIp.entrySet().removeIf(entry -> ahora - entry.getValue() >= VENTANA_WARN_XFF_MS);
    }

    private String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "127.0.0.1";
        }
        String remoteAddr = request.getRemoteAddr() != null ? request.getRemoteAddr().trim() : "127.0.0.1";
        Set<String> trusted = parsearProxiesConfiables(proxiesConfiables);
        if (trusted.isEmpty() && ipsReportadasInfo.add(remoteAddr)) {
            log.info("YC.1: trazalga.proxy.confiables vacío — el filtro está viendo la IP remota '{}' "
                    + "en request.getRemoteAddr(); compárela con la IP de su proxy inverso.", remoteAddr);
        }
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank() && !trusted.contains(remoteAddr)) {
            registrarWarnXffNoConfiable(remoteAddr);
        }
        return resolverIpCliente(request, proxiesConfiables);
    }

    private void registrarWarnXffNoConfiable(String remoteAddr) {
        long ahora = relojMs.getAsLong();
        boolean[] emitir = {false};
        ultimoWarnXffPorIp.compute(remoteAddr, (k, ultimo) -> {
            if (ultimo == null || (ahora - ultimo) >= VENTANA_WARN_XFF_MS) {
                emitir[0] = true;
                return ahora;
            }
            return ultimo;
        });
        if (emitir[0]) {
            log.warn("YC.1: Se recibió cabecera X-Forwarded-For desde IP remota no confiable '{}'. "
                    + "Se ignoró la cabecera; si corresponde a su proxy inverso, configúrela en TRAZALGA_PROXY_CONFIABLES.",
                    remoteAddr);
        }
    }

    private static Set<String> parsearProxiesConfiables(String proxiesConfiablesRaw) {
        if (proxiesConfiablesRaw == null || proxiesConfiablesRaw.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(proxiesConfiablesRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private static String detectarIpLocal() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ignored) {
            return "127.0.0.1";
        }
    }

    /**
     * ZC.6: Solo acepta {@code X-Forwarded-For} si {@code getRemoteAddr()} corresponde
     * a un proxy configurado en {@code trazalga.proxy.confiables}.
     */
    public static String resolverIpCliente(HttpServletRequest request, String proxiesConfiablesRaw) {
        if (request == null) return "127.0.0.1";
        String remoteAddr = request.getRemoteAddr() != null ? request.getRemoteAddr().trim() : "127.0.0.1";
        Set<String> trusted = parsearProxiesConfiables(proxiesConfiablesRaw);
        if (!trusted.isEmpty() && trusted.contains(remoteAddr)) {
            String xf = request.getHeader("X-Forwarded-For");
            if (xf != null && !xf.isBlank()) {
                String primera = xf.split(",")[0].trim();
                if (!primera.isEmpty()) {
                    return primera;
                }
            }
        }
        return remoteAddr;
    }
}
