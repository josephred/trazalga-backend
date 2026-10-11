package com.trazalga.api.services.cuotas;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Date;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.trazalga.api.IntegracionBase;
import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.repositories.ICuotaExtraccionRepository;
import com.trazalga.api.services.CuotaExtraccionService;

import org.springframework.security.test.context.support.WithMockUser;
import jakarta.persistence.EntityManager;

/**
 * Prueba de integración transaccional real para el cierre de cuotas (TC.1 / K1).
 * Corre bajo perfil "it" (application-it.properties), sin enable_lazy_load_no_trans.
 */
@WithMockUser(username = "admin", roles = {"ADMIN"})
public class CierreCuotaIntegrationTest extends IntegracionBase {

    @Autowired
    private CierreCuotaService cierreCuotaService;

    @Autowired
    private CuotaExtraccionService cuotaExtraccionService;

    @Autowired
    private ICuotaExtraccionRepository cuotaRepository;

    @Autowired
    private com.trazalga.api.repositories.ICuotaExtraccionEventoRepository cuotaEventoRepository;

    @Autowired
    private EntityManager entityManager;

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        cuotaEventoRepository.deleteAll();
        cuotaRepository.deleteAll();
    }

    @Test
    @DisplayName("TC.1: cerrar persiste en base de datos tras limpiar el EntityManager (CERRADA, fecha_cierre hoy, ADMINISTRATIVO)")
    void testCerrarCuotaPersisteEstadoYMotivo() {
        LocalDate hoy = LocalDate.now();
        CuotaExtraccionModel cuota = CuotaExtraccionModel.builder()
                .perfil("RECOLECTOR")
                .ambito("AREA_LIBRE")
                .nivelAgregacion("COMUNA")
                .periodo("MENSUAL")
                .fechaInicio(Date.valueOf(hoy.withDayOfMonth(1)))
                .fechaFin(Date.valueOf(hoy.withDayOfMonth(hoy.lengthOfMonth())))
                .limiteKg(5000.0)
                .metrica("DESEMBARQUE")
                .resolucion("RES-TEST-01")
                .activo(true)
                .estado("ABIERTA")
                .build();

        cuota = cuotaRepository.saveAndFlush(cuota);
        Long cuotaId = cuota.getId();
        assertNotNull(cuotaId);

        // Ejecutar cierre
        CuotaExtraccionModel cerrada = cierreCuotaService.cerrar(cuotaId, "ADMINISTRATIVO", "Cierre manual prueba", 1L);
        assertNotNull(cerrada);

        // Limpiar el contexto de persistencia para forzar lectura real desde la BD
        entityManager.clear();

        // Volver a leer desde la base de datos
        CuotaExtraccionModel recargada = cuotaRepository.findById(cuotaId)
                .orElseThrow(() -> new AssertionError("Cuota no encontrada en BD tras cerrar"));

        assertEquals("CERRADA", recargada.getEstado());
        assertEquals("ADMINISTRATIVO", recargada.getMotivoCierre());
        assertNotNull(recargada.getFechaCierre());
    }

    @Test
    @DisplayName("TC.1: cerrarCuota delegado desde CuotaExtraccionService también persiste atómicamente")
    void testCerrarCuotaDelegadoPersiste() {
        LocalDate hoy = LocalDate.now();
        CuotaExtraccionModel cuota = CuotaExtraccionModel.builder()
                .perfil("RECOLECTOR")
                .ambito("AREA_LIBRE")
                .nivelAgregacion("COMUNA")
                .periodo("MENSUAL")
                .fechaInicio(Date.valueOf(hoy.withDayOfMonth(1)))
                .fechaFin(Date.valueOf(hoy.withDayOfMonth(hoy.lengthOfMonth())))
                .limiteKg(8000.0)
                .metrica("DESEMBARQUE")
                .resolucion("RES-TEST-02")
                .activo(true)
                .estado("ABIERTA")
                .build();

        cuota = cuotaRepository.saveAndFlush(cuota);
        Long cuotaId = cuota.getId();

        // Delegar vía CuotaExtraccionService
        cuotaExtraccionService.cerrarCuota(cuotaId);

        entityManager.clear();

        CuotaExtraccionModel recargada = cuotaRepository.findById(cuotaId).orElseThrow();
        assertEquals("CERRADA", recargada.getEstado());
        assertEquals("ADMINISTRATIVO", recargada.getMotivoCierre());
        assertNotNull(recargada.getFechaCierre());
    }
}
