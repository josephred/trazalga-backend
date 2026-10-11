package com.trazalga.api.services.cuotas;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.dto.ConsumoPersonaDTO;
import com.trazalga.api.dto.ResumenPlantillaDTO;
import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.models.ProvinciaModel;
import com.trazalga.api.repositories.ICuotaExtraccionRepository;
import com.trazalga.api.services.ConfiguracionGeneralService;
import com.trazalga.api.services.CuotaExtraccionService;

/**
 * Pruebas unitarias de ConsumoIndividualQuery (TM.2 / K8).
 * Valida agregación de extracciones por persona y cálculo de sobrepasos en plantillas.
 */
@ExtendWith(MockitoExtension.class)
public class ConsumoIndividualQueryTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private CuotaExtraccionService cuotaExtraccionService;

    @Mock
    private ICuotaExtraccionRepository cuotaRepository;

    @Mock
    private ResolutorImputacionTerritorial resolutorImputacion;

    @Mock
    private ConfiguracionGeneralService configService;

    @Mock
    private Query nativeQuery;

    @InjectMocks
    private ConsumoIndividualQuery consumoIndividualQuery;

    private CuotaExtraccionModel cuotaPlantilla;

    @BeforeEach
    void setUp() {
        cuotaPlantilla = new CuotaExtraccionModel();
        cuotaPlantilla.setId(50L);
        cuotaPlantilla.setNivelAgregacion("INDIVIDUAL");
        cuotaPlantilla.setEsPlantilla(true);
        cuotaPlantilla.setLimiteKg(1000.0);
        cuotaPlantilla.setProvincia(new ProvinciaModel());

        when(resolutorImputacion.resolver("RECOLECTOR")).thenReturn(new ReglaImputacionInscripcion());
        when(resolutorImputacion.resolver("ARMADOR")).thenReturn(new ReglaImputacionCaletaDesembarque());
    }

    @Test
    void testResumenPlantilla_CasoDisenoTocopillaMejillones() {
        // Simular 2 personas con actividad:
        // Persona 1: extrajo 1,100 kg (110% de 1,000 kg -> sobre el tope)
        // Persona 2: extrajo 400 kg (40% de 1,000 kg -> bajo el tope)
        // Esperado: «1 de 2 personas sobre su tope (máx. 110 %)»

        Date fechaEval = new Date();
        when(cuotaExtraccionService.calcularLimiteEfectivo(cuotaPlantilla, fechaEval))
                .thenReturn(new BigDecimal("1000.00"));
        when(cuotaExtraccionService.calcularRangoFechas(cuotaPlantilla, fechaEval))
                .thenReturn(new java.sql.Date[]{new java.sql.Date(System.currentTimeMillis() - 86400000), new java.sql.Date(System.currentTimeMillis())});

        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);

        // Fila 1: uId=10, rut="11.111.111-1", nombre="Recolector Tocopilla", perfil="RECOLECTOR", comuna="Tocopilla", embarcaciones=null, cantDecl=3, volTotal=1100.0
        Object[] fila1 = new Object[]{10L, "11.111.111-1", "Recolector Tocopilla", "RECOLECTOR", "Tocopilla", null, 3L, 1100.0};
        // Fila 2: uId=20, rut="22.222.222-2", nombre="Armador Mejillones", perfil="ARMADOR", comuna="Mejillones", embarcaciones="Barca 1", cantDecl=1, volTotal=400.0
        Object[] fila2 = new Object[]{20L, "22.222.222-2", "Armador Mejillones", "ARMADOR", "Mejillones", "Barca 1", 1L, 400.0};

        when(nativeQuery.getResultList()).thenReturn(List.of(fila1, fila2));

        ResumenPlantillaDTO resumen = consumoIndividualQuery.resumenPlantilla(cuotaPlantilla, fechaEval);

        assertNotNull(resumen);
        assertEquals(2, resumen.getPersonasConActividad());
        assertEquals(1, resumen.getPersonasSobreLimite());
        assertEquals(110.0, resumen.getMaxPorcentaje());
        assertEquals(0, new BigDecimal("1500.0").compareTo(resumen.getConsumoTotal()));
        assertEquals("1 de 2 personas sobre su tope (máx. 110 %)", resumen.getTextoConsumo());

        List<ConsumoPersonaDTO> detalle = resumen.getDetallePersonas();
        assertEquals(2, detalle.size());

        ConsumoPersonaDTO p1 = detalle.get(0);
        assertEquals(10L, p1.getUsuarioId());
        assertEquals(0, new BigDecimal("1100.0").compareTo(p1.getConsumo()));
        assertEquals(110.0, p1.getPorcentaje());
        assertEquals(0, new BigDecimal("100.00").compareTo(p1.getExceso()));

        ConsumoPersonaDTO p2 = detalle.get(1);
        assertEquals(20L, p2.getUsuarioId());
        assertEquals(0, new BigDecimal("400.0").compareTo(p2.getConsumo()));
        assertEquals(40.0, p2.getPorcentaje());
        assertEquals(0, BigDecimal.ZERO.compareTo(p2.getExceso()));
    }

    @Test
    void testResumenPlantilla_SinActividad() {
        Date fechaEval = new Date();
        when(cuotaExtraccionService.calcularLimiteEfectivo(cuotaPlantilla, fechaEval))
                .thenReturn(new BigDecimal("1000.00"));
        when(cuotaExtraccionService.calcularRangoFechas(cuotaPlantilla, fechaEval))
                .thenReturn(new java.sql.Date[]{new java.sql.Date(System.currentTimeMillis() - 86400000), new java.sql.Date(System.currentTimeMillis())});

        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.getResultList()).thenReturn(Collections.emptyList());

        ResumenPlantillaDTO resumen = consumoIndividualQuery.resumenPlantilla(cuotaPlantilla, fechaEval);

        assertNotNull(resumen);
        assertEquals(0, resumen.getPersonasConActividad());
        assertEquals(0, resumen.getPersonasSobreLimite());
        assertEquals(0.0, resumen.getMaxPorcentaje());
        assertEquals("0 personas con actividad", resumen.getTextoConsumo());
        assertEquals(BigDecimal.ZERO, resumen.getConsumoTotal());
    }
}
