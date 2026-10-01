package com.trazalga.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.dto.MotivoBloqueoDTO;
import com.trazalga.api.exceptions.CargaBloqueadaException;
import com.trazalga.api.models.DeclaracionArmadorModel;
import com.trazalga.api.models.DeclaracionComercializadorModel;
import com.trazalga.api.models.DeclaracionMarcaModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IDeclaracionArmadorRepository;
import com.trazalga.api.repositories.IDeclaracionComercializadorRepository;
import com.trazalga.api.repositories.IDeclaracionMarcaRepository;

@ExtendWith(MockitoExtension.class)
public class BloqueoCargaServiceTest {

    @Mock
    private IDeclaracionMarcaRepository marcaRepository;

    @Mock
    private ConfiguracionGeneralService configService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private IDeclaracionArmadorRepository armadorRepository;

    @Mock
    private IDeclaracionComercializadorRepository comercializadorRepository;

    @InjectMocks
    private BloqueoCargaService bloqueoCargaService;

    @InjectMocks
    private DeclaracionMarcaService declaracionMarcaService;

    @InjectMocks
    private DeclaracionComercializadorService comercializadorService;

    private DeclaracionMarcaModel marcaLed;

    @BeforeEach
    void setUp() {
        marcaLed = DeclaracionMarcaModel.builder()
                .id(101L)
                .declaracionTipo("ARMADOR")
                .declaracionId(1L)
                .marca("LED_EXCEDIDO")
                .detalle("Exceso de 500 kg sobre LED de 2.000 kg")
                .resuelta(false)
                .estadoGestion("PENDIENTE")
                .createdAt(new Date())
                .build();
    }

    // Paso 1 & 2 de Aceptación: Armador con exceso LED -> marca creada y detectada como bloqueada
    @Test
    void testArmadorDeclaraExcesoLed_MarcaCreadaYBloqueada() {
        when(configService.getBoolean("bloqueo_carga_activo", true)).thenReturn(true);
        when(configService.getValor("marcas_bloqueantes_carga", "LED_EXCEDIDO")).thenReturn("LED_EXCEDIDO");
        when(marcaRepository.isCargaBloqueada(eq("ARMADOR"), eq(1L), anyCollection())).thenReturn(true);

        boolean bloqueada = bloqueoCargaService.estaBloqueada("ARMADOR", 1L);
        assertTrue(bloqueada, "La carga del armador con marca LED_EXCEDIDO debe estar bloqueada");
    }

    // Paso 3 de Aceptación: Comercializador intenta despachar carga bloqueada -> HTTP 409
    @Test
    void testComercializadorIntentaDespachar_Lanza409CargaBloqueada() {
        when(configService.getBoolean("bloqueo_carga_activo", true)).thenReturn(true);
        when(configService.getValor("marcas_bloqueantes_carga", "LED_EXCEDIDO")).thenReturn("LED_EXCEDIDO");
        when(marcaRepository.findMarcasBloqueantesPorTipoEIds(eq("ARMADOR"), eq(List.of(1L)), anyCollection()))
                .thenReturn(List.of(marcaLed));

        // Inyectar bloqueoCargaService en comercializadorService
        org.springframework.test.util.ReflectionTestUtils.setField(
                comercializadorService, "bloqueoCargaService", bloqueoCargaService);

        DeclaracionComercializadorModel despacho = DeclaracionComercializadorModel.builder()
                .folioOrigen("DESP-001")
                .declaracionesSeleccionadas("ARMADOR:1")
                .usuario(UsuarioModel.builder().id(50L).build())
                .build();

        CargaBloqueadaException ex = assertThrows(CargaBloqueadaException.class, () -> {
            comercializadorService.saveDeclaracionComercializador(despacho);
        });

        assertTrue(ex.getMessage().contains("ARMADOR:1"));
        assertTrue(ex.getMessage().contains("LED_EXCEDIDO"));
        assertTrue(ex.getMessage().contains("hallazgo #101"));

        // Verificar que no se guardó en BD
        verify(comercializadorRepository, never()).save(any());
    }

    // Paso 4 de Aceptación: Fiscalizador resuelve con LIBERADA -> carga se desbloquea y notifica
    @Test
    void testFiscalizadorResuelveConLiberada_DesbloqueaCargaYNotifica() {
        when(marcaRepository.findById(101L)).thenReturn(Optional.of(marcaLed));
        when(marcaRepository.save(any(DeclaracionMarcaModel.class))).thenAnswer(inv -> inv.getArgument(0));

        DeclaracionArmadorModel armadorDecl = DeclaracionArmadorModel.builder()
                .id(1L)
                .usuarioDestinatario(UsuarioModel.builder().id(88L).build())
                .build();
        when(armadorRepository.findById(1L)).thenReturn(Optional.of(armadorDecl));

        // Inyectar notificationService y repos en declaracionMarcaService
        org.springframework.test.util.ReflectionTestUtils.setField(
                declaracionMarcaService, "notificationService", notificationService);
        org.springframework.test.util.ReflectionTestUtils.setField(
                declaracionMarcaService, "armadorRepository", armadorRepository);

        DeclaracionMarcaModel resuelta = declaracionMarcaService.resolverMarca(
                101L, "LIBERADA", "Pesaje verificado en terreno conforme", 99L);

        assertTrue(resuelta.getResuelta());
        assertEquals("LIBERADA", resuelta.getResolucionTipo());
        assertEquals("RESUELTA", resuelta.getEstadoGestion());
        assertEquals("Pesaje verificado en terreno conforme", resuelta.getObservacionResolucion());
        assertEquals(99L, resuelta.getResueltaPorUsuarioId());
        assertNotNull(resuelta.getFechaResolucion());

        // Verificar notificación push al comercializador destinatario (id 88)
        verify(notificationService, times(1)).sendPushNotificationToUser(
                eq(88L), contains("liberada"), contains("ARMADOR #1"));
    }

    // Paso 5 de Aceptación: Fiscalizador resuelve con DECOMISO o SANCION -> carga sigue bloqueada
    @Test
    void testFiscalizadorResuelveConDecomiso_CargaSigueBloqueada() {
        when(marcaRepository.findById(101L)).thenReturn(Optional.of(marcaLed));
        when(marcaRepository.save(any(DeclaracionMarcaModel.class))).thenAnswer(inv -> inv.getArgument(0));

        DeclaracionMarcaModel decomisada = declaracionMarcaService.resolverMarca(
                101L, "DECOMISO", "Decomiso oficial de excedente por Sernapesca", 99L);

        assertTrue(decomisada.getResuelta());
        assertEquals("DECOMISO", decomisada.getResolucionTipo());

        // Repositorio evalúa isCargaBloqueada: resolucionTipo != LIBERADA ni DESCARTADA -> bloqueada
        when(configService.getBoolean("bloqueo_carga_activo", true)).thenReturn(true);
        when(configService.getValor("marcas_bloqueantes_carga", "LED_EXCEDIDO")).thenReturn("LED_EXCEDIDO");
        when(marcaRepository.isCargaBloqueada(eq("ARMADOR"), eq(1L), anyCollection())).thenReturn(true);

        assertTrue(bloqueoCargaService.estaBloqueada("ARMADOR", 1L),
                "Una carga con DECOMISO debe permanecer bloqueada");
    }

    // Paso 6 de Aceptación: Parametro maestro bloqueo_carga_activo = false -> despacho pasa
    @Test
    void testBloqueoCargaDesactivado_PermiteDespacho() {
        when(configService.getBoolean("bloqueo_carga_activo", true)).thenReturn(false);

        boolean bloqueada = bloqueoCargaService.estaBloqueada("ARMADOR", 1L);
        assertFalse(bloqueada, "Con bloqueo_carga_activo=false no debe bloquear");

        Map<String, MotivoBloqueoDTO> bloqueos = bloqueoCargaService.bloqueosPara("ARMADOR:1");
        assertTrue(bloqueos.isEmpty(), "No deben retornar bloqueos si el switch maestro está desactivado");
    }

    // Validación de obligatoriedad de observación al resolver
    @Test
    void testResolucionSinObservacion_LanzaError() {
        when(marcaRepository.findById(101L)).thenReturn(Optional.of(marcaLed));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            declaracionMarcaService.resolverMarca(101L, "LIBERADA", "   ", 99L);
        });

        assertTrue(ex.getMessage().contains("observación es obligatoria"));
    }

    // Enriquecimiento de lista en bodega virtual
    @Test
    void testEnriquecerArmadores_MarcaItemsBloqueados() {
        DeclaracionArmadorModel item1 = DeclaracionArmadorModel.builder().id(1L).build();
        DeclaracionArmadorModel item2 = DeclaracionArmadorModel.builder().id(2L).build();
        List<DeclaracionArmadorModel> list = new ArrayList<>(List.of(item1, item2));

        when(configService.getBoolean("bloqueo_carga_activo", true)).thenReturn(true);
        when(configService.getValor("marcas_bloqueantes_carga", "LED_EXCEDIDO")).thenReturn("LED_EXCEDIDO");
        when(marcaRepository.findMarcasBloqueantesPorTipoEIds(eq("ARMADOR"), anyCollection(), anyCollection()))
                .thenReturn(List.of(marcaLed)); // Solo el 1 está bloqueado

        bloqueoCargaService.enriquecerArmadores(list);

        assertTrue(item1.getBloqueada());
        assertNotNull(item1.getMotivoBloqueo());
        assertTrue(item1.getMotivoBloqueo().contains("LED_EXCEDIDO"));

        assertFalse(item2.getBloqueada());
        assertNull(item2.getMotivoBloqueo());
    }
}
