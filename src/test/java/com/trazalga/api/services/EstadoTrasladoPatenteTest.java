package com.trazalga.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.dto.MotivoBloqueoDTO;
import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.models.DeclaracionComercializadorModel;
import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IDeclaracionComercializadorRepository;
import com.trazalga.api.repositories.IDeclaracionPlantaAbastecimientoRepository;
import com.trazalga.api.services.EstadoTrasladoPatente.EvaluacionPatente;

@ExtendWith(MockitoExtension.class)
public class EstadoTrasladoPatenteTest {

    @Mock
    private IDeclaracionComercializadorRepository comercializadorRepository;

    @Mock
    private IDeclaracionPlantaAbastecimientoRepository plantaRepository;

    @Mock
    private BloqueoCargaService bloqueoCargaService;

    @Mock
    private ConfiguracionGeneralService configService;

    @InjectMocks
    private EstadoTrasladoPatente estadoTrasladoPatente;

    private static final LocalDateTime AHORA_REFERENCIA = LocalDateTime.of(2026, 10, 22, 18, 0, 0);

    @BeforeEach
    void setUp() {
        lenient().when(configService.getInt(eq("patente_vigencia_horas"), anyInt())).thenReturn(48);
        lenient().when(configService.getBoolean(eq("patente_vigencia_termina_en_recepcion"), anyBoolean())).thenReturn(true);
    }

    private Date toDate(LocalDate ld) {
        return Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private DeclaracionComercializadorModel crearDespacho(
            Long id,
            String camion,
            String carro,
            LocalDate fecha,
            String hora,
            String comunaDestino,
            String especieNombre,
            String estado) {
        ComunaModel com = ComunaModel.builder().id(1L).nombre(comunaDestino).build();
        UsuarioModel dest = UsuarioModel.builder().id(10L).rut("76.123.456-7").comuna(com).build();
        EspecieModel esp = EspecieModel.builder().id(2L).nombre(especieNombre).build();

        return DeclaracionComercializadorModel.builder()
                .id(id)
                .folioDesembarqueAc("DC-" + id)
                .placaPatente(camion)
                .placaPatenteCarro(carro)
                .fechaDeclaracion(toDate(fecha))
                .fechaTraslado(toDate(fecha))
                .hora(hora)
                .usuarioDestinatario(dest)
                .especie(esp)
                .estado(estado)
                .build();
    }

    @Test
    @DisplayName("TN.6 Caso 1: Autorizado -> despacho declarado hoy 08:00, no recibido, destino Coquimbo, Huiro negro")
    void testCasoAutorizado() {
        DeclaracionComercializadorModel dc = crearDespacho(
                101L, "ABCD12", null,
                LocalDate.of(2026, 10, 22), "08:00:00",
                "Coquimbo", "Huiro negro", "ACTIVA");

        when(comercializadorRepository.findCandidatosPorPatente("ABCD12"))
                .thenReturn(Collections.singletonList(dc));

        EvaluacionPatente ev = estadoTrasladoPatente.evaluar("ABCD12", AHORA_REFERENCIA);

        assertNotNull(ev);
        assertEquals(EstadoTrasladoPatente.ESTADO_AUTORIZADO, ev.getEstado());
        assertEquals(EstadoTrasladoPatente.RESULTADO_PUBLICO_AUTORIZADO, ev.getResultadoPublico());
        assertEquals(LocalDateTime.of(2026, 10, 22, 8, 0, 0), ev.getFechaHoraAutorizacion());
        assertEquals("Coquimbo", ev.getComunaDestino());
        assertEquals("Huiro negro", ev.getTipoAlga());
    }

    @Test
    @DisplayName("TN.6 Caso 2: Finalizado -> recibido en planta a las 14:00")
    void testCasoFinalizado() {
        DeclaracionComercializadorModel dc = crearDespacho(
                102L, "ABCD12", null,
                LocalDate.of(2026, 10, 22), "08:00:00",
                "Coquimbo", "Huiro negro", "ACTIVA");
        dc.setDeclaracionDestinatario(501L);
        dc.setConsumidaPorTipo("PLANTA_ABASTECIMIENTO");

        DeclaracionPlantaAbastecimientoModel dpa = DeclaracionPlantaAbastecimientoModel.builder()
                .id(501L)
                .nombrePlanta("Planta Pacific Algas")
                .fechaIngresoPlanta(toDate(LocalDate.of(2026, 10, 22)))
                .hora("14:00:00")
                .build();

        when(comercializadorRepository.findCandidatosPorPatente("ABCD12"))
                .thenReturn(Collections.singletonList(dc));
        when(plantaRepository.findById(501L)).thenReturn(Optional.of(dpa));

        EvaluacionPatente ev = estadoTrasladoPatente.evaluar("ABCD12", AHORA_REFERENCIA);

        assertNotNull(ev);
        assertEquals(EstadoTrasladoPatente.ESTADO_FINALIZADO, ev.getEstado());
        assertEquals(EstadoTrasladoPatente.RESULTADO_PUBLICO_SIN_AUTORIZACION, ev.getResultadoPublico());
        assertEquals("Planta Pacific Algas", ev.getNombrePlantaRecepcion());
    }

    @Test
    @DisplayName("TN.6 Caso 3: Vencido -> despacho con 50 h desde la salida (límite 48 h)")
    void testCasoVencido() {
        // Despacho declarado el 20-10-2026 16:00:00. Al 22-10-2026 18:00:00 han pasado 50 horas
        DeclaracionComercializadorModel dc = crearDespacho(
                103L, "ABCD12", null,
                LocalDate.of(2026, 10, 20), "16:00:00",
                "Caldera", "Huiro negro", "ACTIVA");

        when(comercializadorRepository.findCandidatosPorPatente("ABCD12"))
                .thenReturn(Collections.singletonList(dc));

        EvaluacionPatente ev = estadoTrasladoPatente.evaluar("ABCD12", AHORA_REFERENCIA);

        assertNotNull(ev);
        assertEquals(EstadoTrasladoPatente.ESTADO_VENCIDO, ev.getEstado());
        assertEquals(EstadoTrasladoPatente.RESULTADO_PUBLICO_SIN_AUTORIZACION, ev.getResultadoPublico());
    }

    @Test
    @DisplayName("TN.6 Caso 4: Retenido -> marca bloqueante RETENCION_EXCEDIDA sin liberar")
    void testCasoRetenido() {
        DeclaracionComercializadorModel dc = crearDespacho(
                104L, "ABCD12", null,
                LocalDate.of(2026, 10, 22), "08:00:00",
                "Coquimbo", "Huiro negro", "ACTIVA");

        MotivoBloqueoDTO bloqueo = MotivoBloqueoDTO.builder()
                .marca("RETENCION_EXCEDIDA")
                .marcaId(77L)
                .detalle("Excedió retención legal")
                .build();

        when(comercializadorRepository.findCandidatosPorPatente("ABCD12"))
                .thenReturn(Collections.singletonList(dc));
        when(bloqueoCargaService.bloqueosPorTipoEIds(eq("COMERCIALIZADOR"), anyList()))
                .thenReturn(Map.of(104L, bloqueo));

        EvaluacionPatente ev = estadoTrasladoPatente.evaluar("ABCD12", AHORA_REFERENCIA);

        assertNotNull(ev);
        assertEquals(EstadoTrasladoPatente.ESTADO_RETENIDO, ev.getEstado());
        assertEquals(EstadoTrasladoPatente.RESULTADO_PUBLICO_SIN_AUTORIZACION, ev.getResultadoPublico());
        assertEquals("RETENCION_EXCEDIDA", ev.getHallazgoMarca());
        assertEquals(77L, ev.getHallazgoId());
    }

    @Test
    @DisplayName("TN.6 Caso 5: Anulado -> un despacho con estado ANULADA no cuenta")
    void testCasoAnulado() {
        DeclaracionComercializadorModel dc = crearDespacho(
                105L, "ABCD12", null,
                LocalDate.of(2026, 10, 22), "08:00:00",
                "Coquimbo", "Huiro negro", "ANULADA");

        when(comercializadorRepository.findCandidatosPorPatente("ABCD12"))
                .thenReturn(Collections.singletonList(dc));

        EvaluacionPatente ev = estadoTrasladoPatente.evaluar("ABCD12", AHORA_REFERENCIA);

        assertNotNull(ev);
        assertEquals(EstadoTrasladoPatente.ESTADO_SIN_REGISTROS, ev.getEstado());
        assertEquals(EstadoTrasladoPatente.RESULTADO_PUBLICO_SIN_AUTORIZACION, ev.getResultadoPublico());
    }

    @Test
    @DisplayName("TN.6 Caso 6: Rampla -> JK1234 como placaPatenteCarro de despacho autorizado")
    void testCasoRampla() {
        DeclaracionComercializadorModel dc = crearDespacho(
                106L, "ABCD12", "JK1234",
                LocalDate.of(2026, 10, 22), "08:00:00",
                "Coquimbo", "Huiro negro", "ACTIVA");

        when(comercializadorRepository.findCandidatosPorPatente("JK1234"))
                .thenReturn(Collections.singletonList(dc));

        EvaluacionPatente ev = estadoTrasladoPatente.evaluar("JK1234", AHORA_REFERENCIA);

        assertNotNull(ev);
        assertEquals(EstadoTrasladoPatente.ESTADO_AUTORIZADO, ev.getEstado());
        assertEquals(EstadoTrasladoPatente.RESULTADO_PUBLICO_AUTORIZADO, ev.getResultadoPublico());
        assertEquals("Coquimbo", ev.getComunaDestino());
    }

    @Test
    @DisplayName("TN.6 Caso 7: Inexistente -> patente inexistente retorna SIN_REGISTROS")
    void testCasoInexistente() {
        when(comercializadorRepository.findCandidatosPorPatente("ZZ9999"))
                .thenReturn(Collections.emptyList());

        EvaluacionPatente ev = estadoTrasladoPatente.evaluar("ZZ9999", AHORA_REFERENCIA);

        assertNotNull(ev);
        assertEquals(EstadoTrasladoPatente.ESTADO_SIN_REGISTROS, ev.getEstado());
        assertEquals(EstadoTrasladoPatente.RESULTADO_PUBLICO_SIN_AUTORIZACION, ev.getResultadoPublico());
    }
}
