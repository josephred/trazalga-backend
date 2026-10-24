package com.trazalga.api.services;

import com.trazalga.api.models.TransportistaAliasModel;
import com.trazalga.api.models.TransportistaModel;
import com.trazalga.api.models.VehiculoModel;
import com.trazalga.api.repositories.ITransportistaAliasRepository;
import com.trazalga.api.repositories.ITransportistaRepository;
import com.trazalga.api.repositories.IVehiculoRepository;
import com.trazalga.api.services.TransporteResolverService.TransporteResolucionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TransporteResolverServiceTest {

    @Mock
    private ITransportistaRepository transportistaRepository;

    @Mock
    private ITransportistaAliasRepository aliasRepository;

    @Mock
    private IVehiculoRepository vehiculoRepository;

    @InjectMocks
    private TransporteResolverService resolverService;

    @Test
    @DisplayName("TT.8: Validación de RUTs chilenos con módulo 11")
    void testValidacionRut() {
        assertTrue(TransporteResolverService.validarRut("12.345.678-5"));
        assertTrue(TransporteResolverService.validarRut("11.111.111-1"));
        assertTrue(TransporteResolverService.validarRut("15.432.876-9"));
        assertTrue(TransporteResolverService.validarRut("9.876.543-3"));
        assertTrue(TransporteResolverService.validarRut("123456785"));
        assertTrue(TransporteResolverService.validarRut("12345678-5"));

        assertFalse(TransporteResolverService.validarRut("12.345.678-9"));
        assertFalse(TransporteResolverService.validarRut("11.111.111-2"));
        assertFalse(TransporteResolverService.validarRut(""));
        assertFalse(TransporteResolverService.validarRut(null));
        assertFalse(TransporteResolverService.validarRut("ABC"));
    }

    @Test
    @DisplayName("TT.8: Alias -> 3 despachos con Juan Pérez, JUAN PEREZ y Juan Peres con RUT 12.345.678-5")
    void testAliasTresDespachosMismoRut() {
        String rutCanonico = "12345678-5";
        TransportistaModel t = TransportistaModel.builder()
                .id(1L)
                .rut(rutCanonico)
                .rutValido(true)
                .nombreCanonico("Juan Pérez")
                .nombreNormalizado("JUAN PEREZ")
                .estado(TransportistaModel.ESTADO_ACTIVO)
                .build();

        when(transportistaRepository.findByRut(rutCanonico)).thenReturn(Optional.of(t));
        when(aliasRepository.findByTransportistaIdAndNombreNormalizado(eq(1L), anyString()))
                .thenReturn(Optional.empty());

        // 1er despacho: "Juan Pérez"
        TransportistaModel r1 = resolverService.resolverTransportista(10L, "Juan Pérez", "12.345.678-5", new Date());
        assertNotNull(r1);
        assertEquals(rutCanonico, r1.getRut());

        // 2do despacho: "JUAN PEREZ"
        TransportistaModel r2 = resolverService.resolverTransportista(10L, "JUAN PEREZ", "12345678-5", new Date());
        assertNotNull(r2);
        assertEquals(rutCanonico, r2.getRut());

        // 3er despacho: "Juan Peres"
        TransportistaModel r3 = resolverService.resolverTransportista(10L, "Juan Peres", "12.345.678-5", new Date());
        assertNotNull(r3);
        assertEquals(rutCanonico, r3.getRut());

        // Debe registrar los 3 alias
        verify(aliasRepository, times(3)).save(any(TransportistaAliasModel.class));
    }

    @Test
    @DisplayName("TT.8: Sin RUT en el mismo comercializador -> Pedro Soto crea transportista REVISAR y se reutiliza")
    void testSinRutMismoComercializador() {
        Long comId = 10L;
        String nombre = "Pedro Soto";
        String norm = "PEDRO SOTO";

        // Primer despacho: no existe en C1
        when(transportistaRepository.findByNombreNormalizadoAndComercializadorRef(norm, comId))
                .thenReturn(Collections.emptyList());

        TransportistaModel nuevo = TransportistaModel.builder()
                .id(20L)
                .nombreCanonico(nombre)
                .nombreNormalizado(norm)
                .comercializadorRef(comId)
                .estado(TransportistaModel.ESTADO_REVISAR)
                .rutValido(false)
                .build();
        when(transportistaRepository.save(any(TransportistaModel.class))).thenReturn(nuevo);

        TransportistaModel t1 = resolverService.resolverTransportista(comId, nombre, null, new Date());
        assertNotNull(t1);
        assertEquals(TransportistaModel.ESTADO_REVISAR, t1.getEstado());
        assertEquals(comId, t1.getComercializadorRef());

        // Segundo despacho en C1: ya existe
        when(transportistaRepository.findByNombreNormalizadoAndComercializadorRef(norm, comId))
                .thenReturn(Collections.singletonList(nuevo));

        TransportistaModel t2 = resolverService.resolverTransportista(comId, nombre, "", new Date());
        assertNotNull(t2);
        assertEquals(20L, t2.getId());
    }

    @Test
    @DisplayName("TT.8: Sin RUT en otro comercializador -> Pedro Soto en C2 crea otro transportista")
    void testSinRutDistintoComercializador() {
        String norm = "PEDRO SOTO";

        // En C2 no existe aún
        when(transportistaRepository.findByNombreNormalizadoAndComercializadorRef(norm, 20L))
                .thenReturn(Collections.emptyList());

        TransportistaModel nuevoC2 = TransportistaModel.builder()
                .id(21L)
                .nombreCanonico("Pedro Soto")
                .nombreNormalizado(norm)
                .comercializadorRef(20L)
                .estado(TransportistaModel.ESTADO_REVISAR)
                .rutValido(false)
                .build();
        when(transportistaRepository.save(any(TransportistaModel.class))).thenReturn(nuevoC2);

        TransportistaModel tC2 = resolverService.resolverTransportista(20L, "Pedro Soto", null, new Date());
        assertNotNull(tC2);
        assertEquals(21L, tC2.getId());
        assertEquals(20L, tC2.getComercializadorRef());
    }

    @Test
    @DisplayName("TT.8: Patente normalizada -> ab-1234 como camión de C1 y rampla de C2 es un vehículo con ambos roles")
    void testPatenteNormalizadaCamionYRampla() {
        VehiculoModel vehiculoExistente = VehiculoModel.builder()
                .id(50L)
                .patente("AB1234")
                .formatoValido(true)
                .usadoComoCamion(true)
                .usadoComoRampla(false)
                .primeraVez(new Date())
                .ultimaVez(new Date())
                .build();

        // Primer uso como camión
        when(vehiculoRepository.findByPatente("AB1234"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(vehiculoExistente));
        when(vehiculoRepository.save(any(VehiculoModel.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        VehiculoModel vCamion = resolverService.resolverVehiculo("ab-1234", false, "CAMION", new Date());
        assertNotNull(vCamion);
        assertEquals("AB1234", vCamion.getPatente());
        assertTrue(vCamion.getUsadoComoCamion());

        // Segundo uso como rampla
        VehiculoModel vRampla = resolverService.resolverVehiculo("AB-12-34", true, "RAMPLA", new Date());
        assertNotNull(vRampla);
        assertEquals("AB1234", vRampla.getPatente());
        assertTrue(vRampla.getUsadoComoCamion());
        assertTrue(vRampla.getUsadoComoRampla());
    }
}
