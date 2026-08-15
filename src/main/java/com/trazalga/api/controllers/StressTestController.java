package com.trazalga.api.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.trazalga.api.dto.PerformanceReport;
import com.trazalga.api.services.StressTestService;

/**
 * Endpoint de generación de carga para pruebas.
 *
 * <h2>⚠️ Restringido al perfil "perf"</h2>
 *
 * <p>Antes de este cambio, {@code POST /api/stress-test/run?cantidad=N} estaba
 * disponible en <b>todos</b> los perfiles, incluido producción. Cualquiera con
 * un token válido podía insertar un número arbitrario de registros en la base
 * productiva — un vector de denegación de servicio y de corrupción de datos.
 *
 * <p>Con {@code @Profile("perf")} el bean solo se registra cuando la aplicación
 * arranca con {@code --spring.profiles.active=perf}, es decir, únicamente en la
 * instancia apuntada al schema {@code trazalga_perf}. En dev y prod la ruta
 * simplemente no existe.
 *
 * <h2>Este endpoint NO se usa para cargar el volumen de prueba</h2>
 *
 * <p>La generación masiva se hace con {@code stress-tests/datagen/generate.py}
 * y {@code LOAD DATA LOCAL INFILE} (~200.000 filas/s, frente a ~1.000 filas/s
 * vía JPA). Además, {@link StressTestService} asigna los mismos IDs de caleta,
 * usuario y especie a todos los registros: esa cardinalidad artificial hace que
 * el optimizador de MySQL descarte los índices y elija planes de ejecución que
 * nunca ocurrirían en producción.
 *
 * <p>Se conserva solo como utilidad de humo para verificar que la instancia
 * puede escribir en el schema de pruebas.
 */
@RestController
@RequestMapping("/api/stress-test")
@Profile("perf")
public class StressTestController {

    @Autowired
    private StressTestService stressTestService;

    @PostMapping("/run")
    public PerformanceReport runStressTest(@RequestParam(defaultValue = "1000") int cantidad) {
        // Tope defensivo: evita que un typo en el parámetro llene el disco del
        // servidor .199, que es compartido con producción.
        if (cantidad > 50_000) {
            throw new IllegalArgumentException(
                "cantidad > 50.000 no está permitida por este endpoint. "
                + "Para volúmenes mayores usa stress-tests/datagen/generate.py "
                + "con LOAD DATA INFILE, que es dos órdenes de magnitud más rápido.");
        }
        return stressTestService.generarCargaMasiva(cantidad);
    }
}
