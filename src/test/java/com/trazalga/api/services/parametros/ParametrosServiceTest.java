package com.trazalga.api.services.parametros;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.services.ConfiguracionGeneralService;

@ExtendWith(MockitoExtension.class)
public class ParametrosServiceTest {

    @Mock
    private ConfiguracionGeneralService configService;

    @InjectMocks
    private ParametrosService parametrosService;

    @BeforeEach
    void setUp() {
        parametrosService.invalidar();
    }

    @Test
    @DisplayName("TA.2: Valores por defecto se inicializan correctamente en los 4 registros tipados")
    void testValoresPorDefecto() {
        when(configService.getValor(anyString(), anyString())).thenAnswer(inv -> inv.getArgument(1));
        when(configService.getBoolean(anyString(), anyBoolean())).thenAnswer(inv -> inv.getArgument(1));
        when(configService.getInt(anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(1));
        when(configService.getDouble(anyString(), anyDouble())).thenAnswer(inv -> inv.getArgument(1));

        ParametrosCuota cuotas = parametrosService.cuotas();
        assertNotNull(cuotas);
        assertFalse(cuotas.cierreAutomaticoVencimiento());
        assertEquals(0, cuotas.diasGraciaDeclaracion());
        assertEquals("ALERTA_CRITICA", cuotas.accionExtemporanea());
        assertEquals("INSCRIPCION", cuotas.imputacionArmador());
        assertEquals(10.0, cuotas.umbralRestantePct());

        ParametrosRetencion retencion = parametrosService.retencion();
        assertNotNull(retencion);
        assertFalse(retencion.bloqueoActivo());
        assertEquals(120, retencion.bloqueoHumedoHoras());
        assertEquals("TODAS", retencion.bloqueoEspecies());

        ParametrosVariacion variacion = parametrosService.variacion();
        assertNotNull(variacion);
        assertTrue(variacion.bioPerdidaActivo());
        assertEquals(3, variacion.bioHumedoDiasMinimosTransito());

        ParametrosDesembarque desembarque = parametrosService.desembarque();
        assertNotNull(desembarque);
        assertEquals(5000.0, desembarque.umbralAtipicoKg());
        assertEquals(2.5, desembarque.sigma());
    }

    @Test
    @DisplayName("TA.2: Invalida y recarga dinámicamente cuando cambia un parámetro sin reiniciar")
    void testRecargaDinamicaConInvalidacion() {
        when(configService.getInt(eq("retencion_bloqueo_humedo_horas"), anyInt()))
                .thenReturn(120)
                .thenReturn(72);

        ParametrosRetencion inicial = parametrosService.retencion();
        assertEquals(120, inicial.bloqueoHumedoHoras());

        // Invalidar caché
        parametrosService.invalidar();

        ParametrosRetencion actualizado = parametrosService.retencion();
        assertEquals(72, actualizado.bloqueoHumedoHoras());
    }
}
