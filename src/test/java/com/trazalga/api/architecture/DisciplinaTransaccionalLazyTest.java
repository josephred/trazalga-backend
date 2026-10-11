package com.trazalga.api.architecture;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Date;
import java.time.LocalDate;

import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.trazalga.api.IntegracionBase;
import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.repositories.ICuotaExtraccionRepository;
import com.trazalga.api.repositories.IEspecieRepository;

import jakarta.persistence.EntityManager;

/**
 * Prueba de verificación de disciplina transaccional en perfil 'it' (TA.1).
 * Confirma que open-in-view=false y la ausencia de enable_lazy_load_no_trans
 * producen LazyInitializationException al navegar relaciones @ManyToOne fuera de sesión JPA.
 */
public class DisciplinaTransaccionalLazyTest extends IntegracionBase {

    @Autowired
    private ICuotaExtraccionRepository cuotaRepository;

    @Autowired
    private IEspecieRepository especieRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("TA.1: Navegar relación LAZY fuera de transacción en perfil 'it' lanza LazyInitializationException")
    void testAccesoLazyFueraDeTransaccion_LanzaLazyInitializationException() {
        // 1. Guardar especie y cuota
        EspecieModel especie = new EspecieModel();
        especie.setNombre("Huiro negro prueba lazy");
        especie = especieRepository.saveAndFlush(especie);

        CuotaExtraccionModel cuota = CuotaExtraccionModel.builder()
                .perfil("RECOLECTOR")
                .ambito("AREA_LIBRE")
                .nivelAgregacion("COMUNA")
                .periodo("MENSUAL")
                .fechaInicio(Date.valueOf(LocalDate.now()))
                .fechaFin(Date.valueOf(LocalDate.now().plusMonths(1)))
                .limiteKg(1000.0)
                .metrica("DESEMBARQUE")
                .resolucion("RES-LAZY")
                .especie(especie)
                .activo(true)
                .estado("ABIERTA")
                .build();

        cuota = cuotaRepository.saveAndFlush(cuota);
        Long cuotaId = cuota.getId();

        // 2. Limpiar sesión JPA por completo
        entityManager.clear();

        // 3. Leer cuota mediante findById simple (sin JOIN FETCH)
        CuotaExtraccionModel cuotaLeida = cuotaRepository.findById(cuotaId)
                .orElseThrow(() -> new AssertionError("Cuota no encontrada"));

        // Desasociar explícitamente para asegurar que estamos fuera de sesión JPA
        entityManager.detach(cuotaLeida);

        // 4. Intentar acceder a especie.getNombre() sobre el proxy LAZY fuera de sesión
        assertThrows(LazyInitializationException.class, () -> {
            cuotaLeida.getEspecie().getNombre();
        }, "Debe lanzar LazyInitializationException porque el perfil 'it' no tiene enable_lazy_load_no_trans");
    }
}
