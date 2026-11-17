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
import com.trazalga.api.exceptions.ValidacionPlantaException;
import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.models.DeclaracionAreaModel;
import com.trazalga.api.models.DeclaracionArmadorModel;
import com.trazalga.api.models.DeclaracionComercializadorModel;
import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import com.trazalga.api.models.DeclaracionPlantaDestinoModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.PerfilModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IDeclaracionAreaRepository;
import com.trazalga.api.repositories.IDeclaracionArmadorRepository;
import com.trazalga.api.repositories.IDeclaracionComercializadorRepository;
import com.trazalga.api.repositories.IDeclaracionPlantaAbastecimientoRepository;
import com.trazalga.api.repositories.IDeclaracionPlantaDestinoRepository;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;
import com.trazalga.api.services.EstadoTrasladoPatente.EvaluacionPatente;

@ExtendWith(MockitoExtension.class)
public class EstadoTrasladoPatenteTest {

    @Mock
    private IDeclaracionComercializadorRepository comercializadorRepository;

    @Mock
    private IDeclaracionPlantaAbastecimientoRepository plantaRepository;

    @Mock
    private IDeclaracionPlantaDestinoRepository destinoRepository;

    @Mock
    private IDeclaracionRecolectorRepository recolectorRepository;

    @Mock
    private IDeclaracionArmadorRepository armadorRepository;

    @Mock
    private IDeclaracionAreaRepository areaRepository;

    @Mock
    private BloqueoCargaService bloqueoCargaService;

    @Mock
    private ConfiguracionGeneralService configService;

    @InjectMocks
    private EstadoTrasladoPatente estadoTrasladoPatente;

    private static final LocalDateTime AHORA_REFERENCIA = LocalDateTime.of(2026, 10, 22, 18, 0, 0);

    @BeforeEach
    void setUp() {
        estadoTrasladoPatente.setComercializadorRepository(comercializadorRepository);
        estadoTrasladoPatente.setPlantaRepository(plantaRepository);
        estadoTrasladoPatente.setDestinoRepository(destinoRepository);
        estadoTrasladoPatente.setRecolectorRepository(recolectorRepository);
        estadoTrasladoPatente.setArmadorRepository(armadorRepository);
        estadoTrasladoPatente.setAreaRepository(areaRepository);
        estadoTrasladoPatente.setBloqueoCargaService(bloqueoCargaService);
        estadoTrasladoPatente.setConfigService(configService);
        lenient().when(configService.getInt(eq("patente_vigencia_horas"), anyInt())).thenReturn(48);
        lenient().when(configService.getBoolean(eq("patente_vigencia_termina_en_recepcion"), anyBoolean())).thenReturn(true);
        lenient().when(configService.getBoolean(eq("patente_incluye_despacho_planta"), anyBoolean())).thenReturn(true);
        lenient().when(configService.getValor(eq("patente_traslado_max_dias"), anyString())).thenReturn("1");
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

    @Test
    @DisplayName("ZT.4: Despacho nocturno 22:30 (con fecha corregida a hoy) consultado a las 22:35 responde AUTORIZADO")
    void testDespachoNocturno2230Consultado2235Autorizado() {
        DeclaracionComercializadorModel dc = crearDespacho(
                107L, "BBCC11", null,
                LocalDate.of(2026, 11, 16), "22:30:00",
                "Coquimbo", "Huiro negro", "ACTIVA");

        when(comercializadorRepository.findCandidatosPorPatente("BBCC11"))
                .thenReturn(Collections.singletonList(dc));

        LocalDateTime momentoConsulta = LocalDateTime.of(2026, 11, 16, 22, 35, 0);
        EvaluacionPatente ev = estadoTrasladoPatente.evaluar("BBCC11", momentoConsulta);

        assertNotNull(ev);
        assertEquals(EstadoTrasladoPatente.ESTADO_AUTORIZADO, ev.getEstado());
        assertEquals(EstadoTrasladoPatente.RESULTADO_PUBLICO_AUTORIZADO, ev.getResultadoPublico());
    }

    @Test
    @DisplayName("ZC.2: fechaTraslado 5 días después se acota a fechaDeclaracion + 1 día para evaluar vigencia")
    void testComercializadorAcotaFechaTrasladoA1Dia() {
        DeclaracionComercializadorModel dc = crearDespacho(
                108L, "TRAS12", null,
                LocalDate.of(2026, 10, 20), "10:00:00",
                "Coquimbo", "Huiro negro", "ACTIVA");
        dc.setFechaTraslado(toDate(LocalDate.of(2026, 10, 25)));

        when(comercializadorRepository.findCandidatosPorPatente("TRAS12"))
                .thenReturn(Collections.singletonList(dc));

        EvaluacionPatente evDentro = estadoTrasladoPatente.evaluar(
                "TRAS12", LocalDateTime.of(2026, 10, 22, 10, 0, 0));
        assertEquals(EstadoTrasladoPatente.ESTADO_AUTORIZADO, evDentro.getEstado());
        assertEquals(LocalDateTime.of(2026, 10, 21, 10, 0, 0), evDentro.getFechaHoraSalida());

        // > 48 h desde salida acotada (2026-10-21 10:00 -> vence 2026-10-23 10:00), aunque antes del 2026-10-25
        EvaluacionPatente evVencido = estadoTrasladoPatente.evaluar(
                "TRAS12", LocalDateTime.of(2026, 10, 23, 12, 0, 0));
        assertEquals(EstadoTrasladoPatente.ESTADO_VENCIDO, evVencido.getEstado());
        assertEquals(EstadoTrasladoPatente.RESULTADO_PUBLICO_SIN_AUTORIZACION, evVencido.getResultadoPublico());
    }

    @Test
    @DisplayName("ZC.3: Despacho de planta (PLANTA_DESTINO) vigente -> AUTORIZADO sin comuna ni datos comprador; bloqueado -> RETENIDO")
    void testPlantaDestinoAutorizadoSinComunaYRetenidoConBloqueo() {
        ComunaModel comComprador = ComunaModel.builder().id(8L).nombre("Talcahuano").build();
        UsuarioModel comprador = UsuarioModel.builder().id(88L).rut("99.999.999-9").nombres("Comprador Secreto").comuna(comComprador).build();
        EspecieModel esp = EspecieModel.builder().id(1L).nombre("Huiro palo").build();
        DeclaracionPlantaDestinoModel dpd = DeclaracionPlantaDestinoModel.builder()
                .id(201L)
                .folioDeclaracionDestino("DD-201")
                .placaPatente("PD1234")
                .fechaDeclaracionDestino(toDate(LocalDate.of(2026, 10, 22)))
                .hora("08:00:00")
                .nombreDestino("Comprador Privado S.A.")
                .rutDestino("77.777.777-7")
                .usuarioDestinatario(comprador)
                .especie(esp)
                .declaracionesSeleccionadas("901")
                .estado("ACTIVA")
                .build();

        when(destinoRepository.findCandidatosPorPatente("PD1234"))
                .thenReturn(Collections.singletonList(dpd));
        when(bloqueoCargaService.bloqueosPorTipoEIds(eq("PLANTA_DESTINO"), anyList()))
                .thenReturn(Collections.emptyMap());
        when(bloqueoCargaService.bloqueosPara("901"))
                .thenReturn(Collections.emptyMap());

        EvaluacionPatente evAut = estadoTrasladoPatente.evaluar("PD1234", AHORA_REFERENCIA);
        assertEquals(EstadoTrasladoPatente.ESTADO_AUTORIZADO, evAut.getEstado());
        assertEquals(EstadoTrasladoPatente.RESULTADO_PUBLICO_AUTORIZADO, evAut.getResultadoPublico());
        assertNull(evAut.getComunaDestino(), "PLANTA_DESTINO no debe exponer comuna de destino en evaluación pública");
        assertEquals("Huiro palo", evAut.getTipoAlga());

        MotivoBloqueoDTO bloqueo = MotivoBloqueoDTO.builder()
                .marcaId(55L)
                .marca("RENDIMIENTO_ANOMALO")
                .detalle("Producción bloqueada")
                .build();
        when(bloqueoCargaService.bloqueosPara("901"))
                .thenReturn(Map.of("PLANTA_PRODUCCION:901", bloqueo));

        EvaluacionPatente evRet = estadoTrasladoPatente.evaluar("PD1234", AHORA_REFERENCIA);
        assertEquals(EstadoTrasladoPatente.ESTADO_RETENIDO, evRet.getEstado());
        assertEquals(EstadoTrasladoPatente.RESULTADO_PUBLICO_SIN_AUTORIZACION, evRet.getResultadoPublico());
        assertEquals(55L, evRet.getHallazgoId());
    }

    @Test
    @DisplayName("ZC.9: Traslados directos a planta desde Recolector, Armador y Area con patente -> AUTORIZADO; a comercializador -> SIN_REGISTROS")
    void testOrigenDirectoPlantaAutorizadoYNoPlantaSinRegistros() {
        PerfilModel perfilPlanta = PerfilModel.builder().id(5L).nombre("PLANTA").build();
        PerfilModel perfilCom = PerfilModel.builder().id(4L).nombre("COMERCIALIZADOR").build();
        ComunaModel comPlanta = ComunaModel.builder().id(4L).nombre("Ovalle").build();
        UsuarioModel plantaUser = UsuarioModel.builder().id(50L).rut("76.500.500-5").perfil(perfilPlanta).comuna(comPlanta).build();
        UsuarioModel comUser = UsuarioModel.builder().id(40L).rut("76.400.400-4").perfil(perfilCom).comuna(comPlanta).build();
        EspecieModel esp = EspecieModel.builder().id(2L).nombre("Huiro negro").build();

        DeclaracionRecolectorModel rec = DeclaracionRecolectorModel.builder()
                .id(301L).folioDesembarqueRo("RO-301").placaPatente("RE1234")
                .fechaDeclaracion(toDate(LocalDate.of(2026, 10, 22))).hora("09:00:00")
                .usuarioDestinatario(plantaUser).especie(esp).estado("ACTIVA").build();
        when(recolectorRepository.findCandidatosPorPatente("RE1234")).thenReturn(Collections.singletonList(rec));
        EvaluacionPatente evRec = estadoTrasladoPatente.evaluar("RE1234", AHORA_REFERENCIA);
        assertEquals(EstadoTrasladoPatente.ESTADO_AUTORIZADO, evRec.getEstado());
        assertEquals("Ovalle", evRec.getComunaDestino());

        DeclaracionArmadorModel arm = DeclaracionArmadorModel.builder()
                .id(302L).folioDesembarqueDa("DA-302").placaPatente("AR1234")
                .fechaDeclaracion(toDate(LocalDate.of(2026, 10, 22))).hora("09:00:00")
                .tipoDestinatario("planta").usuarioDestinatario(plantaUser).especie(esp).estado("ACTIVA").build();
        when(armadorRepository.findCandidatosPorPatente("AR1234")).thenReturn(Collections.singletonList(arm));
        EvaluacionPatente evArm = estadoTrasladoPatente.evaluar("AR1234", AHORA_REFERENCIA);
        assertEquals(EstadoTrasladoPatente.ESTADO_AUTORIZADO, evArm.getEstado());

        DeclaracionAreaModel area = DeclaracionAreaModel.builder()
                .id(303L).folioDesembarqueAmerb("AM-303").placaPatente("AE1234")
                .fechaDeclaracion(toDate(LocalDate.of(2026, 10, 22))).hora("09:00:00")
                .tipoDestinatario("planta").usuarioDestinatario(plantaUser).especie(esp).estado("ACTIVA").build();
        when(areaRepository.findCandidatosPorPatente("AE1234")).thenReturn(Collections.singletonList(area));
        EvaluacionPatente evArea = estadoTrasladoPatente.evaluar("AE1234", AHORA_REFERENCIA);
        assertEquals(EstadoTrasladoPatente.ESTADO_AUTORIZADO, evArea.getEstado());

        // Origen dirigido a comercializador (perfil=4) con patente no se toma como despacho directo a planta
        DeclaracionRecolectorModel noPlanta = DeclaracionRecolectorModel.builder()
                .id(304L).folioDesembarqueRo("RO-304").placaPatente("NP1234")
                .fechaDeclaracion(toDate(LocalDate.of(2026, 10, 22))).hora("09:00:00")
                .usuarioDestinatario(comUser).especie(esp).estado("ACTIVA").build();
        when(recolectorRepository.findCandidatosPorPatente("NP1234")).thenReturn(Collections.singletonList(noPlanta));
        EvaluacionPatente evNoPlanta = estadoTrasladoPatente.evaluar("NP1234", AHORA_REFERENCIA);
        assertEquals(EstadoTrasladoPatente.ESTADO_SIN_REGISTROS, evNoPlanta.getEstado());
        assertEquals(EstadoTrasladoPatente.RESULTADO_PUBLICO_SIN_AUTORIZACION, evNoPlanta.getResultadoPublico());
    }

    @Test
    @DisplayName("ZC.9: origen_directo_exige_patente=true rechaza con ValidacionPlantaException si falta patente en traslado a planta")
    void testNormalizarYValidarPatenteOrigenExigePatenteCuandoParametroActivo() {
        when(configService.getBoolean("origen_directo_exige_patente", false)).thenReturn(true);

        assertThrows(ValidacionPlantaException.class, () ->
                EstadoTrasladoPatente.normalizarYValidarPatenteOrigen(
                        configService, true, "  ", null, v -> {}, v -> {}));

        List<String> capturado = new ArrayList<>();
        EstadoTrasladoPatente.normalizarYValidarPatenteOrigen(
                configService, true, "ab-1234", "cd5678", capturado::add, capturado::add);
        assertEquals(List.of("AB1234", "CD5678"), capturado);
    }
}
