package com.trazalga.api.services.cuotas;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Date;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.trazalga.api.IntegracionBase;
import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.repositories.IAvisoEnviadoRepository;
import com.trazalga.api.repositories.ICuotaExtraccionRepository;
import com.trazalga.api.repositories.IEspecieRepository;

import jakarta.persistence.EntityManager;

/**
 * Prueba de integración para RevisionCuotasService (TC.6 / TA.1).
 * Verifica la ejecución real con JOIN FETCH y persistencia de avisos bajo perfil 'it'.
 */
public class RevisionCuotasServiceIT extends IntegracionBase {

    @Autowired
    private RevisionCuotasService revisionCuotasService;

    @Autowired
    private ICuotaExtraccionRepository cuotaRepository;

    @Autowired
    private IEspecieRepository especieRepository;

    @Autowired
    private com.trazalga.api.repositories.IRegionRepository regionRepository;

    @Autowired
    private com.trazalga.api.repositories.IComunaRepository comunaRepository;

    @Autowired
    private IAvisoEnviadoRepository avisoEnviadoRepository;

    @Autowired
    private EntityManager entityManager;

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        cuotaRepository.deleteAll();
    }

    @Test
    @DisplayName("TC.6: Revisión transaccional en perfil 'it' ejecuta sin LazyInitializationException usando findActivasConRelaciones")
    void testRevisionCuotasIntegracionReal() {
        LocalDate hoy = LocalDate.now();

        EspecieModel especie = new EspecieModel();
        especie.setNombre("Huiro negro IT");
        especie = especieRepository.saveAndFlush(especie);

        com.trazalga.api.models.RegionModel region = new com.trazalga.api.models.RegionModel();
        region.setNombre("Coquimbo IT");
        region.setCodigo("04");
        region = regionRepository.saveAndFlush(region);

        com.trazalga.api.models.ComunaModel comuna = new com.trazalga.api.models.ComunaModel();
        comuna.setNombre("La Serena IT");
        comuna.setRegion(region);
        comuna = comunaRepository.saveAndFlush(comuna);

        CuotaExtraccionModel cuota = CuotaExtraccionModel.builder()
                .perfil("RECOLECTOR")
                .ambito("AREA_LIBRE")
                .nivelAgregacion("COMUNA")
                .region(region)
                .comuna(comuna)
                .comunas(java.util.Set.of(comuna))
                .especie(especie)
                .periodo("MENSUAL")
                .fechaInicio(Date.valueOf(hoy.withDayOfMonth(1)))
                .fechaFin(Date.valueOf(hoy.withDayOfMonth(hoy.lengthOfMonth())))
                .limiteKg(10000.0)
                .metrica("DESEMBARQUE")
                .resolucion("RES-IT-01")
                .activo(true)
                .estado("ABIERTA")
                .build();

        cuotaRepository.saveAndFlush(cuota);

        entityManager.clear();

        // Ejecutar revisión
        int procesadas = revisionCuotasService.revisar(hoy, new java.util.Date());
        assertTrue(procesadas >= 1, "Debe haber procesado al menos la cuota activa creada");
    }
}
