package com.trazalga.api.services.cuotas;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.trazalga.api.dto.ContextoDeclaracion;
import com.trazalga.api.dto.ResultadoValidacion;
import com.trazalga.api.dto.ResultadoValidacion.DecisionValidacion;
import com.trazalga.api.dto.ResultadoValidacion.MarcaItem;
import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.ExtraccionTipoModel;
import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.models.RegionModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.DeclaracionBuzosRepository;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;
import com.trazalga.api.services.AlertaTriggerService;
import com.trazalga.api.services.CapturaService;
import com.trazalga.api.services.DeclaracionRecolectorService;
import com.trazalga.api.services.ValidacionDeclaracionService;

import jakarta.persistence.EntityManager;

/**
 * TC.5: Pruebas unitarias para revalidación al editar declaraciones (K12).
 * Verifica que updateById invoque el pipeline de validación con esEdicion = true,
 * impida persistir cambios que violen un bloqueo (422) y registre marcas de forma idempotente.
 */
@ExtendWith(MockitoExtension.class)
public class CuotaRevalidacionEdicionTest {

    @Mock
    private IDeclaracionRecolectorRepository declaracionRecolectorRepository;

    @Mock
    private DeclaracionBuzosRepository declaracionBuzosRepository;

    @Mock
    private com.trazalga.api.repositories.IBuzoRepository buzoRepository;

    @Mock
    private ValidacionDeclaracionService validacionDeclaracionService;

    @Mock
    private AlertaTriggerService alertaTriggerService;

    @Mock
    private com.trazalga.api.services.GestionMensajeService gestionMensajeService;

    @Mock
    private CapturaService capturaService;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private DeclaracionRecolectorService declaracionRecolectorService;

    private UsuarioModel usuario;
    private EspecieModel especie;
    private ExtraccionTipoModel extraccionTipo;
    private HumedadEstadoModel humedadEstado;
    private ComunaModel comuna;

    @BeforeEach
    void setUp() {
        usuario = UsuarioModel.builder().id(10L).build();
        especie = EspecieModel.builder().id(1L).nombre("Huiro negro").build();
        extraccionTipo = ExtraccionTipoModel.builder().id(2L).nombre("Varado").build();
        humedadEstado = HumedadEstadoModel.builder().id(3L).nombre("Húmedo").build();
        RegionModel region = RegionModel.builder().id(4L).nombre("Coquimbo").build();
        comuna = ComunaModel.builder().id(4101L).nombre("La Serena").region(region).build();

        lenient().when(entityManager.find(eq(EspecieModel.class), any())).thenReturn(especie);
        lenient().when(entityManager.find(eq(ComunaModel.class), any())).thenReturn(comuna);
        lenient().when(entityManager.find(eq(ExtraccionTipoModel.class), any())).thenReturn(extraccionTipo);
        lenient().when(entityManager.find(eq(HumedadEstadoModel.class), any())).thenReturn(humedadEstado);
        lenient().when(entityManager.find(eq(UsuarioModel.class), any())).thenReturn(usuario);
    }

    @Test
    @DisplayName("TC.5: updateById revalida pipeline; si es rechazado por bloqueo lanza 422 y NO persiste")
    void testUpdateById_RechazoBloqueante_Lanza422YNoPersiste() {
        DeclaracionRecolectorModel existente = new DeclaracionRecolectorModel();
        existente.setId(100L);
        existente.setUsuario(usuario);
        existente.setEspecie(especie);
        existente.setComuna(comuna);
        existente.setExtraccionTipo(extraccionTipo);
        existente.setHumedadEstado(humedadEstado);
        existente.setFechaExtraccion(Date.valueOf("2026-03-10"));
        existente.setFechaDeclaracion(Date.valueOf("2026-03-10"));
        existente.setDesembarque(new BigDecimal("1000.00"));

        when(declaracionRecolectorRepository.findById(100L)).thenReturn(Optional.of(existente));

        // Request modificando fecha para caer en veda o periodo cerrado bloqueado
        DeclaracionRecolectorModel request = new DeclaracionRecolectorModel();
        request.setFechaExtraccion(Date.valueOf("2026-03-15"));
        request.setDesembarque(new BigDecimal("1500.00"));
        request.setEspecie(especie);
        request.setComuna(comuna);
        request.setExtraccionTipo(extraccionTipo);
        request.setHumedadEstado(humedadEstado);

        ResultadoValidacion rechazo = ResultadoValidacion.builder()
                .decision(DecisionValidacion.RECHAZAR)
                .motivoRechazo("Bloqueo estricto: La especie se encuentra en veda biológica en la región.")
                .build();

        when(validacionDeclaracionService.validar(argThat(ctx ->
                Boolean.TRUE.equals(ctx.getEsEdicion()) &&
                "RECOLECTOR".equals(ctx.getTipoDeclaracion())
        ))).thenReturn(rechazo);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            declaracionRecolectorService.updateById(request, 100L);
        });

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Bloqueo estricto"));

        // Verificar que los cambios NO se persisten en base de datos
        verify(declaracionRecolectorRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC.5: updateById exitoso con marcas no bloqueantes persiste y procesa marcas de forma idempotente")
    void testUpdateById_ExitosoConMarcas_PersisteYProcesaMarcas() {
        DeclaracionRecolectorModel existente = new DeclaracionRecolectorModel();
        existente.setId(200L);
        existente.setUsuario(usuario);
        existente.setEspecie(especie);
        existente.setComuna(comuna);
        existente.setExtraccionTipo(extraccionTipo);
        existente.setHumedadEstado(humedadEstado);
        existente.setFechaExtraccion(Date.valueOf("2026-03-10"));
        existente.setDesembarque(new BigDecimal("1000.00"));

        when(declaracionRecolectorRepository.findById(200L)).thenReturn(Optional.of(existente));
        when(declaracionRecolectorRepository.save(any(DeclaracionRecolectorModel.class))).thenAnswer(i -> i.getArgument(0));

        DeclaracionRecolectorModel request = new DeclaracionRecolectorModel();
        request.setFechaExtraccion(Date.valueOf("2026-03-15"));
        request.setDesembarque(new BigDecimal("2000.00"));
        request.setEspecie(especie);
        request.setComuna(comuna);
        request.setExtraccionTipo(extraccionTipo);
        request.setHumedadEstado(humedadEstado);

        MarcaItem marcaExtemp = MarcaItem.builder()
                .marca("DECLARACION_EXTEMPORANEA")
                .detalle("Modificación posterior al cierre: La cuota cerró el 2026-03-31.")
                .reglaId(50L)
                .build();

        ResultadoValidacion aprobacionConAlerta = ResultadoValidacion.builder()
                .decision(DecisionValidacion.MARCAR)
                .marcas(List.of(marcaExtemp))
                .capturaCalculada(new BigDecimal("2000.00"))
                .factorAplicado(new BigDecimal("1.0000"))
                .build();

        when(validacionDeclaracionService.validar(argThat(ctx ->
                Boolean.TRUE.equals(ctx.getEsEdicion())
        ))).thenReturn(aprobacionConAlerta);

        DeclaracionRecolectorModel resultado = declaracionRecolectorService.updateById(request, 200L);

        assertNotNull(resultado);
        verify(declaracionRecolectorRepository).save(existente);
        verify(alertaTriggerService).procesarMarcas(eq("RECOLECTOR"), eq(200L), any(), eq(List.of(marcaExtemp)));
    }
}
