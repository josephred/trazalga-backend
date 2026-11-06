package com.trazalga.api.services.cuotas;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.trazalga.api.dto.ContextoDeclaracion;

/**
 * Pruebas unitarias de la imputación territorial (TC.8; ajuste del 06-11-2026).
 * Toda declaración de origen descuenta cuota en la comuna del usuario que declara
 * ({@code usuario.comuna_id}); si el usuario no tiene comuna, en la de desembarque (K15).
 */
public class ImputacionTerritorialTest {

    private ReglaImputacionInscripcion reglaInscripcion;
    private ResolutorImputacionTerritorial resolutor;

    @BeforeEach
    void setUp() {
        reglaInscripcion = new ReglaImputacionInscripcion();
        resolutor = new ResolutorImputacionTerritorial(reglaInscripcion);
    }

    @Test
    void testReglaInscripcion_SqlRecolectorYArmador_UsaComunaDelUsuarioConRespaldoK15() {
        assertEquals("INSCRIPCION", reglaInscripcion.clave());
        assertEquals("COALESCE(u.comuna_id, d.comuna_id)",
                reglaInscripcion.sqlComuna(ReglaImputacionInscripcion.TABLA_RECOLECTOR, "d", "u"));
        assertEquals("COALESCE(u_arm.comuna_id, da.comuna_id)",
                reglaInscripcion.sqlComuna(ReglaImputacionInscripcion.TABLA_ARMADOR, "da", "u_arm"));
    }

    @Test
    void testReglaInscripcion_SqlArea_RespaldoAmerbYCaleta_SinComunaIdEnDeclaracion() {
        String sql = reglaInscripcion.sqlComuna(ReglaImputacionInscripcion.TABLA_AREA, "d", "u");
        assertTrue(sql.startsWith("COALESCE(u.comuna_id, "), sql);
        assertTrue(sql.contains("FROM amerb am_imp WHERE am_imp.id = d.amerb_id"), sql);
        assertTrue(sql.contains("FROM caleta ca_imp WHERE ca_imp.id = d.caleta_id"), sql);
        assertFalse(sql.contains("d.comuna_id"), "declaracion_area no tiene comuna_id: " + sql);
    }

    @Test
    void testResolutor_TodosLosPerfilesUsanLaComunaDelUsuario() {
        for (String tipo : new String[] {"RECOLECTOR", "ARMADOR", "AREA", "recolector", null, "DESCONOCIDO"}) {
            ReglaImputacionTerritorial regla = resolutor.resolver(tipo);
            assertNotNull(regla, "tipo " + tipo);
            assertEquals("INSCRIPCION", regla.clave(), "tipo " + tipo);
        }
    }

    @Test
    void testComunaImputacion_ComunaDelUsuarioAunqueDesembarqueSeaOtra() {
        ContextoDeclaracion ctx = ContextoDeclaracion.builder()
                .tipoDeclaracion("ARMADOR")
                .comunaInscripcionId(36L)
                .comunaDesembarqueId(40L)
                .build();
        assertEquals(36L, resolutor.resolver("ARMADOR").comunaImputacion(ctx));

        ContextoDeclaracion area = ContextoDeclaracion.builder()
                .tipoDeclaracion("AREA")
                .comunaInscripcionId(36L)
                .comunaDesembarqueId(40L)
                .build();
        assertEquals(36L, resolutor.resolver("AREA").comunaImputacion(area));
    }

    @Test
    void testComunaImputacion_UsuarioSinComuna_RespaldoDesembarque() {
        ContextoDeclaracion ctx = ContextoDeclaracion.builder()
                .tipoDeclaracion("RECOLECTOR")
                .comunaInscripcionId(null)
                .comunaDesembarqueId(40L)
                .build();
        assertEquals(40L, resolutor.resolver("RECOLECTOR").comunaImputacion(ctx));
        assertNull(reglaInscripcion.comunaImputacion(null));
    }
}
