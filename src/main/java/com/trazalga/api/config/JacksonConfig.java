package com.trazalga.api.config;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de Jackson para el manejo de relaciones LAZY de Hibernate.
 *
 * <p><b>Por qué existe esta clase.</b> En la rama {@code perf/stress-tests} las
 * 68 relaciones {@code @ManyToOne} pasaron de EAGER (el default de JPA) a
 * {@code FetchType.LAZY}, para eliminar el problema N+1 que hacía que listar
 * declaraciones disparara hasta 8 consultas adicionales por fila.
 *
 * <p>Ese cambio tiene una consecuencia: como los controllers devuelven
 * entidades JPA directamente y {@code spring.jpa.open-in-view=false}, Jackson
 * intenta serializar proxies no inicializados fuera de la transacción y lanza
 * {@code LazyInitializationException}, que el cliente ve como HTTP 500.
 *
 * <p><b>Qué hace este módulo.</b> Serializa las relaciones no cargadas como
 * {@code null} en lugar de lanzar la excepción. Es una red de seguridad, no
 * una solución.
 *
 * <h2>⚠️ Cómo interpretar un null en la respuesta</h2>
 *
 * <p>Si un endpoint devuelve {@code "caleta": null} y en la base de datos SÍ
 * hay una caleta asociada, eso <b>no</b> es un dato faltante: es una relación
 * que faltó declarar en el {@code @EntityGraph} de la consulta que alimenta ese
 * endpoint. La corrección es añadir el atributo al grafo del repositorio
 * correspondiente, no revertir la relación a EAGER.
 *
 * <p>El escenario {@code k6/00-smoke.js} verifica explícitamente que las
 * relaciones clave no vengan nulas, justamente para que estos casos aparezcan
 * como fallos visibles y no pasen inadvertidos.
 *
 * <h2>FORCE_LAZY_LOADING queda desactivado a propósito</h2>
 *
 * <p>Activarlo haría que Jackson cargara cada relación al serializar,
 * reintroduciendo exactamente el N+1 que este trabajo busca eliminar — con el
 * agravante de que las consultas ocurrirían fuera de la transacción.
 *
 * @see com.trazalga.api.repositories.IDeclaracionRecolectorRepository
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Module hibernate6Module() {
        Hibernate6Module module = new Hibernate6Module();

        // NO forzar la carga al serializar: reintroduciría el N+1.
        module.disable(Hibernate6Module.Feature.FORCE_LAZY_LOADING);

        // Serializa los identificadores de las relaciones no cargadas en vez
        // de null cuando es posible. Así el frontend al menos recibe el id y
        // el problema es más fácil de diagnosticar que con un null pelado.
        module.enable(Hibernate6Module.Feature.SERIALIZE_IDENTIFIER_FOR_LAZY_NOT_LOADED_OBJECTS);

        return module;
    }
}
