package com.trazalga.api.services;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
public class DatabaseAdminService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void vaciarTablas() {
        List<String> tablas = Arrays.asList(
                "declaracion_area",
                "declaracion_armador",
                "declaracion_buzos",
                "declaracion_comercializador",
                "declaracion_planta_abastecimiento",
                "declaracion_planta_destino",
                "declaracion_planta_produccion",
                "declaracion_recolector",
                "amerb",
                "buzo",
                "caleta",
                "comuna",
                "embarcacion",
                "especie",
                "extraccion_tipo"
        );

        // Desactivar restricciones de llaves foráneas para evitar errores de integridad al hacer TRUNCATE
        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();

        for (String tabla : tablas) {
            try {
                entityManager.createNativeQuery("TRUNCATE TABLE " + tabla).executeUpdate();
            } catch (Exception e) {
                // Se ignora el error individual para continuar con el vaciado de las demás tablas
                System.err.println("Error al truncar tabla " + tabla + ": " + e.getMessage());
            }
        }

        // Reactivar restricciones de llaves foráneas
        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();
    }
}
