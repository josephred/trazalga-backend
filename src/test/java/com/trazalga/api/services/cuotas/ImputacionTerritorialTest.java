package com.trazalga.api.services.cuotas;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.services.parametros.ParametrosCuota;
import com.trazalga.api.services.parametros.ParametrosService;

/**
 * Pruebas unitarias para el motor de imputación territorial (TC.8).
 * Valida reglas de imputación por inscripción (recolectores con fallback K15),
 * caleta de desembarque, configuración de armadores (D1) y resolución dinámica.
 */
@ExtendWith(MockitoExtension.class)
public class ImputacionTerritorialTest {

    @Mock
    private ParametrosService parametrosService;

    private ReglaImputacionInscripcion reglaInscripcion;
    private ReglaImputacionCaletaDesembarque reglaCaleta;
    private ResolutorImputacionTerritorial resolutor;

    @BeforeEach
    void setUp() {
        reglaInscripcion = new ReglaImputacionInscripcion();
        reglaCaleta = new ReglaImputacionCaletaDesembarque();
        resolutor = new ResolutorImputacionTerritorial(reglaInscripcion, reglaCaleta, parametrosService);
    }

    @Test
    void testReglaInscripcion_GeneraSqlConFallbackK15() {
        assertEquals("INSCRIPCION", reglaInscripcion.clave());
        String sql = reglaInscripcion.sqlComuna("d", "u");
        assertEquals("COALESCE(u.comuna_id, d.comuna_id)", sql);
    }

    @Test
    void testReglaCaletaDesembarque_GeneraSqlDirecto() {
        assertEquals("CALETA_DESEMBARQUE", reglaCaleta.clave());
        String sql = reglaCaleta.sqlComuna("d", "u");
        assertEquals("d.comuna_id", sql);
    }

    @Test
    void testResolutor_RecolectorSiempreUsaInscripcion() {
        ReglaImputacionTerritorial regla = resolutor.resolver("RECOLECTOR");
        assertNotNull(regla);
        assertEquals("INSCRIPCION", regla.clave());
        assertEquals("COALESCE(u.comuna_id, d.comuna_id)", regla.sqlComuna("d", "u"));
    }

    @Test
    void testResolutor_AreaSiempreUsaCaletaDesembarque() {
        ReglaImputacionTerritorial regla = resolutor.resolver("AREA");
        assertNotNull(regla);
        assertEquals("CALETA_DESEMBARQUE", regla.clave());
        assertEquals("d.comuna_id", regla.sqlComuna("d", "u"));
    }

    @Test
    void testResolutor_ArmadorPorDefectoUsaInscripcion_ReglaD1() {
        ParametrosCuota paramsDefault = new ParametrosCuota(false, 0, "ALERTA", "INSCRIPCION", 10.0);
        when(parametrosService.cuotas()).thenReturn(paramsDefault);

        ReglaImputacionTerritorial regla = resolutor.resolver("ARMADOR");
        assertNotNull(regla);
        assertEquals("INSCRIPCION", regla.clave());
        assertEquals("COALESCE(u.comuna_id, d.comuna_id)", regla.sqlComuna("d", "u"));
    }

    @Test
    void testResolutor_ArmadorConfiguradoEnCaletaDesembarque() {
        ParametrosCuota paramsDesembarque = new ParametrosCuota(false, 0, "ALERTA", "CALETA_DESEMBARQUE", 10.0);
        when(parametrosService.cuotas()).thenReturn(paramsDesembarque);

        ReglaImputacionTerritorial regla = resolutor.resolver("ARMADOR");
        assertNotNull(regla);
        assertEquals("CALETA_DESEMBARQUE", regla.clave());
        assertEquals("d.comuna_id", regla.sqlComuna("d", "u"));
    }

    @Test
    void testResolutor_ArmadorParametrosNulos_FallbackSeguroAInscripcion() {
        when(parametrosService.cuotas()).thenReturn(null);

        ReglaImputacionTerritorial regla = resolutor.resolver("ARMADOR");
        assertNotNull(regla);
        assertEquals("INSCRIPCION", regla.clave());
    }

    @Test
    void testResolutor_PerfilNuloOInvalido_FallbackSeguroACaletaDesembarque() {
        ReglaImputacionTerritorial reglaNulo = resolutor.resolver(null);
        assertNotNull(reglaNulo);
        assertEquals("CALETA_DESEMBARQUE", reglaNulo.clave());

        ReglaImputacionTerritorial reglaDesconocido = resolutor.resolver("OTRO_PERFIL");
        assertNotNull(reglaDesconocido);
        assertEquals("CALETA_DESEMBARQUE", reglaDesconocido.clave());
    }
}
