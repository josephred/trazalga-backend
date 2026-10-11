package com.trazalga.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.trazalga.api.exceptions.ValidacionPlantaException;
import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IDeclaracionPlantaAbastecimientoRepository;

@ExtendWith(MockitoExtension.class)
public class DeclaracionPlantaAbastecimientoValidationTest {

    @Mock
    private IDeclaracionPlantaAbastecimientoRepository repository;

    @Mock
    private ConfiguracionGeneralService configuracionGeneralService;

    @Mock
    private BloqueoCargaService bloqueoCargaService;

    @InjectMocks
    private DeclaracionPlantaAbastecimientoService service;

    private DeclaracionPlantaAbastecimientoModel declaracion;

    @BeforeEach
    void setUp() {
        RequestContextHolder.resetRequestAttributes();

        declaracion = new DeclaracionPlantaAbastecimientoModel();
        declaracion.setId(1L);
        declaracion.setFolioOrigen("RO-202610-001");
        declaracion.setFolioDeclaracionAPla("DAPLA-001");
        declaracion.setFechaIngresoPlanta(new Date());

        UsuarioModel usuario = new UsuarioModel();
        usuario.setId(5L);
        declaracion.setUsuario(usuario);

        HumedadEstadoModel humOrigen = new HumedadEstadoModel().setId(1L).setNombre("Húmedo");
        declaracion.setHumedadEstado(humOrigen);
    }

    // --- TP.2: Desacoplamiento de Peso en Romana y Voucher ---

    @Test
    @DisplayName("TP.2: Falla con ValidacionPlantaException si planta_exige_peso_romana=true y peso es null o cero")
    void testValidarPesoRomanaObligatorioFalla() {
        when(configuracionGeneralService.getBoolean(eq("planta_exige_peso_romana"), anyBoolean())).thenReturn(true);
        declaracion.setPesoRomanaKg(null);

        ValidacionPlantaException ex = assertThrows(ValidacionPlantaException.class, () -> {
            service.validarPesoRomana(declaracion);
        });

        assertTrue(ex.getMessage().contains("planta_exige_peso_romana=true"));

        declaracion.setPesoRomanaKg(BigDecimal.ZERO);
        assertThrows(ValidacionPlantaException.class, () -> {
            service.validarPesoRomana(declaracion);
        });
    }

    @Test
    @DisplayName("TP.2: Pasa exitosamente si planta_exige_peso_romana=true y pesoRomanaKg > 0")
    void testValidarPesoRomanaExitoso() {
        when(configuracionGeneralService.getBoolean(eq("planta_exige_peso_romana"), anyBoolean())).thenReturn(true);
        declaracion.setPesoRomanaKg(new BigDecimal("1250.50"));

        assertDoesNotThrow(() -> {
            service.validarPesoRomana(declaracion);
        });
    }

    @Test
    @DisplayName("TP.2: Desacoplamiento - Peso en romana es válido sin exigir voucher cuando variacion_peso_exige_voucher=false")
    void testDesacoplamientoRomanaSinVoucher() {
        when(configuracionGeneralService.getBoolean(eq("planta_exige_peso_romana"), anyBoolean())).thenReturn(true);
        when(configuracionGeneralService.getBoolean(eq("variacion_peso_exige_voucher"), anyBoolean())).thenReturn(false);

        declaracion.setPesoRomanaKg(new BigDecimal("2100.00"));
        declaracion.setVoucherRomanaNumero(null); // Sin voucher

        assertDoesNotThrow(() -> {
            service.validarVoucherRomana(declaracion);
        });
    }

    @Test
    @DisplayName("TP.2: Falla con ValidacionPlantaException si variacion_peso_exige_voucher=true y no se ingresa voucher")
    void testValidarVoucherExigeVoucherFalla() {
        when(configuracionGeneralService.getBoolean(eq("variacion_peso_exige_voucher"), anyBoolean())).thenReturn(true);
        declaracion.setVoucherRomanaNumero("");

        ValidacionPlantaException ex = assertThrows(ValidacionPlantaException.class, () -> {
            service.validarVoucher(declaracion);
        });

        assertTrue(ex.getMessage().contains("variacion_peso_exige_voucher=true"));
    }

    @Test
    @DisplayName("TP.2: Pasa exitosamente si variacion_peso_exige_voucher=true y se ingresa voucher de romana")
    void testValidarVoucherExitoso() {
        when(configuracionGeneralService.getBoolean(eq("variacion_peso_exige_voucher"), anyBoolean())).thenReturn(true);
        declaracion.setVoucherRomanaNumero("VOUCHER-9876");

        assertDoesNotThrow(() -> {
            service.validarVoucher(declaracion);
        });
    }

    // --- TP.3: Humedad Verificada y Versión Mínima ---

    @Test
    @DisplayName("TP.3: Falla con ValidacionPlantaException si planta_exige_humedad_recepcion=true y no hay humedadRecepcion")
    void testValidarHumedadRecepcionFalla() {
        when(configuracionGeneralService.getBoolean(eq("planta_exige_humedad_recepcion"), anyBoolean())).thenReturn(true);
        declaracion.setHumedadEstadoRecepcion(null);

        ValidacionPlantaException ex = assertThrows(ValidacionPlantaException.class, () -> {
            service.validarHumedadRecepcion(declaracion);
        });

        assertTrue(ex.getMessage().contains("planta_exige_humedad_recepcion=true"));
    }

    @Test
    @DisplayName("TP.3: Versión mínima configurada en 1.1.0 rechaza peticiones con app anterior o sin cabecera X-App-Version")
    void testValidarVersionMinimaRechazaVersionAnterior() {
        when(configuracionGeneralService.getValor(eq("planta_version_minima"), anyString())).thenReturn("1.1.0");

        // Caso 1: Sin cabecera X-App-Version
        MockHttpServletRequest requestSinHeader = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(requestSinHeader));

        ValidacionPlantaException exSinHeader = assertThrows(ValidacionPlantaException.class, () -> {
            service.validarVersionMinima();
        });
        assertTrue(exSinHeader.getMessage().contains("Actualice TrazAlga a la versión 1.1.0 o superior"));

        // Caso 2: Con versión 1.0.8 (< 1.1.0)
        MockHttpServletRequest requestVersionBaja = new MockHttpServletRequest();
        requestVersionBaja.addHeader("X-App-Version", "1.0.8");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(requestVersionBaja));

        ValidacionPlantaException exBaja = assertThrows(ValidacionPlantaException.class, () -> {
            service.validarVersionMinima();
        });
        assertTrue(exBaja.getMessage().contains("Actualice TrazAlga a la versión 1.1.0 o superior"));
    }

    @Test
    @DisplayName("TP.3: Versión mínima configurada en 1.1.0 acepta peticiones con versión igual o superior")
    void testValidarVersionMinimaAceptaVersionValida() {
        when(configuracionGeneralService.getValor(eq("planta_version_minima"), anyString())).thenReturn("1.1.0");

        // Versión exacta 1.1.0
        MockHttpServletRequest request110 = new MockHttpServletRequest();
        request110.addHeader("X-App-Version", "1.1.0");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request110));

        assertDoesNotThrow(() -> {
            service.validarVersionMinima();
        });

        // Versión superior 1.2.0
        MockHttpServletRequest request120 = new MockHttpServletRequest();
        request120.addHeader("X-App-Version", "1.2.0");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request120));

        assertDoesNotThrow(() -> {
            service.validarVersionMinima();
        });
    }

    @Test
    @DisplayName("TP.3: Si planta_version_minima está vacía, permite registro con cualquier versión o sin cabecera")
    void testValidarVersionMinimaDesactivada() {
        when(configuracionGeneralService.getValor(eq("planta_version_minima"), anyString())).thenReturn("");

        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertDoesNotThrow(() -> {
            service.validarVersionMinima();
        });
    }
}
