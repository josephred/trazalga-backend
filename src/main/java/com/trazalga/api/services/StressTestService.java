package com.trazalga.api.services;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Random;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trazalga.api.models.*;
import com.trazalga.api.dto.PerformanceReport;

@Service
public class StressTestService {

    @PersistenceContext
    private EntityManager entityManager;

    private static final int BATCH_SIZE = 500;

    @Transactional // La transacción es necesaria para el batching
    public PerformanceReport generarCargaMasiva(int cantidad) {
        long startTime = System.currentTimeMillis();
        Random random = new Random();

        System.out.println("🚀 Iniciando carga masiva de " + cantidad + " registros...");

        for (int i = 1; i <= cantidad; i++) {
            // Usamos UUID o Timestamps para Folios únicos
            String uniqueFolio = UUID.randomUUID().toString().substring(0, 15);

            DeclaracionRecolectorModel data = new DeclaracionRecolectorModel();
            // data.setId(...) -> NO asignar si es AUTO_INCREMENT
            data.setFolioOrigen("STR-" + uniqueFolio);
            data.setFolioDesembarqueRo("RO-" + uniqueFolio);
            data.setDesembarque(new BigDecimal(100 + (1000 - 100) * random.nextDouble()));
            data.setCaptura(new BigDecimal(100 + (1000 - 100) * random.nextDouble()));
            data.setFechaDeclaracion(new Date());
            data.setFechaExtraccion(new Date());
            data.setHora("12:00:00");
            data.setNombre("Recolector Test " + i);
            data.setCodigoSernapesca("RPA-" + i);
            data.setVaradero("Varadero Stress Test");
            data.setLatitud(-33.4489);
            data.setLongitud(-70.6693);
            data.setHumedad("15%");
            data.setCodigoDestinatario("DEST-" + i);
            data.setNombreDestinatario("Destinatario Test " + i);
            data.setDeclaracionDestinatario(1000L + i);

            // IMPORTANTE: Referencias a IDs existentes para no violar llaves foráneas
            // Usamos getReference para no cargar el objeto completo de la BD
            data.setCaleta(entityManager.getReference(CaletaModel.class, 1L));
            data.setUsuario(entityManager.getReference(UsuarioModel.class, 1L));
            data.setUsuarioDestinatario(entityManager.getReference(UsuarioModel.class, 2L));
            data.setEspecie(entityManager.getReference(EspecieModel.class, 1L));
            data.setComuna(entityManager.getReference(ComunaModel.class, 33L));
            data.setExtraccionTipo(entityManager.getReference(ExtraccionTipoModel.class, 1L));
            data.setComposicion(entityManager.getReference(ComposicionModel.class, 1L));
            data.setHumedadEstado(entityManager.getReference(HumedadEstadoModel.class, 1L));

            entityManager.persist(data);

            // Cada BATCH_SIZE registros, enviamos a la DB y LIMPIAMOS la memoria RAM
            if (i % BATCH_SIZE == 0) {
                entityManager.flush();
                entityManager.clear();
                System.out.println("✅ Progreso: " + i + " registros procesados...");
            }
        }

        long endTime = System.currentTimeMillis();
        return new PerformanceReport(cantidad, startTime, endTime);
    }
}