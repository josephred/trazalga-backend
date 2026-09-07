package com.trazalga.api.config;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Caché de resultados de reportes.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * POR QUÉ EXISTE ESTA CLASE
 *
 * Las pruebas de carga con 500.000 registros dejaron un diagnóstico claro:
 *
 *   - Un reporte cuesta ~1,14 s de CPU (medido en dos corridas
 *     independientes, con 1,8% de discrepancia entre ambas).
 *   - Los reportes son ~20% del tráfico y ~80% del consumo de CPU.
 *   - Con 2 cores, eso fija el techo del sistema en ~7 peticiones/segundo,
 *     equivalente a ~20 usuarios concurrentes.
 *
 * Las consultas examinan entre 173.000 y 500.000 filas para devolver 1 a 4
 * filas de agregados. Ese ratio no se arregla con índices: para sumar hay
 * que leer. La única salida es no volver a leer, y los agregados sobre datos
 * históricos son el caso ideal de caché.
 *
 * IMPACTO ESPERADO (a verificar midiendo, no asumiendo):
 *   coste medio por petición: 277 ms -> ~65 ms
 *   techo del sistema:        ~7 req/s -> ~30 req/s
 * ─────────────────────────────────────────────────────────────────────────
 *
 * DECISIONES Y SUS MOTIVOS
 *
 * 1. Caffeine y no Redis. No hay un segundo servidor donde poner Redis, y un
 *    caché en proceso no añade infraestructura ni latencia de red. La
 *    contrapartida: si algún día se escala a varias instancias, cada una
 *    tendrá su propio caché y podrán servir valores distintos durante la
 *    ventana del TTL.
 *
 * 2. TTL de 5 minutos. Es el compromiso entre frescura y coste. Ver
 *    TTL_MINUTOS más abajo para el razonamiento completo.
 *
 * 3. `maximumSize` además del TTL. Un caché acotado solo por tiempo es una
 *    fuga de memoria con retardo. El heap disponible es 1 GB y en las
 *    pruebas llegó a 340 MB de pico, así que el margen existe pero no es
 *    infinito. Los reportes de detalle devuelven listas que pueden tener
 *    miles de filas, por eso van en un caché aparte y más pequeño.
 *
 * 4. `recordStats()` activado. Sin estadísticas no hay forma de saber si el
 *    caché sirve de algo. Se exponen en /actuator/caches y /actuator/metrics.
 *    Un hit ratio bajo significaría que las claves no se repiten y que este
 *    cambio no aporta — es justo lo que hay que poder comprobar.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Caché de métricas: resultados pequeños (1 a 4 filas de agregados). */
    public static final String CACHE_METRICAS = "reportesMetricas";

    /** Caché de detalles: listas que pueden tener miles de filas. */
    public static final String CACHE_DETALLE = "reportesDetalle";

    /** Caché de trazabilidad: recorre el grafo de declaraciones. */
    public static final String CACHE_TRAZABILIDAD = "trazabilidad";

    /**
     * Ventana de obsolescencia aceptada.
     *
     * Los reportes de Trazalga son agregados sobre declaraciones ya emitidas:
     * volúmenes por especie, extracción en veda, variación de peso. Una
     * declaración registrada hace 30 segundos puede tardar hasta 5 minutos en
     * aparecer en un reporte.
     *
     * ⚠️ ESTA ES UNA DECISIÓN DE NEGOCIO, NO TÉCNICA. Si algún reporte se usa
     * para una decisión operativa en tiempo real —autorizar un despacho,
     * validar un cupo antes de emitir— 5 minutos es demasiado y ese método
     * concreto debe quedar FUERA del caché. Confirmar con el área usuaria
     * antes de llevar esto a producción.
     */
    private static final long TTL_MINUTOS = 5;

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(Arrays.asList(
                construir(CACHE_METRICAS, 300),
                // Menos entradas: cada una puede pesar miles de filas.
                construir(CACHE_DETALLE, 50),
                construir(CACHE_TRAZABILIDAD, 500)
        ));
        return manager;
    }

    private CaffeineCache construir(String nombre, int maximoEntradas) {
        return new CaffeineCache(nombre, Caffeine.newBuilder()
                .expireAfterWrite(TTL_MINUTOS, TimeUnit.MINUTES)
                .maximumSize(maximoEntradas)
                .recordStats()
                .build());
    }
}
