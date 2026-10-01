package com.trazalga.api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.trazalga.api.dto.ResultadoValidacion;
import com.trazalga.api.models.AmerbModel;
import com.trazalga.api.models.BuzoModel;
import com.trazalga.api.models.CaletaModel;
import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.models.DeclaracionAreaModel;
import com.trazalga.api.models.DeclaracionArmadorModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.models.EmbarcacionModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.DeclaracionBuzosRepository;
import com.trazalga.api.repositories.IAmerbRepository;
import com.trazalga.api.repositories.IBuzoRepository;
import com.trazalga.api.repositories.ICaletaRepository;
import com.trazalga.api.repositories.IComunaRepository;
import com.trazalga.api.repositories.IDeclaracionAreaRepository;
import com.trazalga.api.repositories.IDeclaracionArmadorRepository;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;
import com.trazalga.api.repositories.IEmbarcacionRepository;
import com.trazalga.api.repositories.IEspecieRepository;
import com.trazalga.api.repositories.IHumedadEstadoRepository;
import com.trazalga.api.repositories.IUsuarioRepository;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
public class DeclaracionTerritorialValidationTest {

    // --- Mocks para DeclaracionRecolectorService ---
    @Mock
    private IDeclaracionRecolectorRepository recolectorRepository;
    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private DeclaracionRecolectorService recolectorService;

    // --- Mocks para DeclaracionArmadorService ---
    @Mock
    private IDeclaracionArmadorRepository armadorRepository;
    @Mock
    private ICaletaRepository caletaRepository;
    @Mock
    private IComunaRepository comunaRepository;
    @Mock
    private IEspecieRepository especieRepository;
    @Mock
    private IHumedadEstadoRepository humedadEstadoRepository;
    @Mock
    private IUsuarioRepository usuarioRepository;
    @Mock
    private IEmbarcacionRepository embarcacionRepository;
    @Mock
    private IBuzoRepository buzoRepository;

    @InjectMocks
    private DeclaracionArmadorService armadorService;

    // --- Mocks para DeclaracionAreaService ---
    @Mock
    private IDeclaracionAreaRepository areaRepository;
    @Mock
    private IAmerbRepository amerbRepository;

    @InjectMocks
    private DeclaracionAreaService areaService;

    // --- Mocks compartidos ---
    @Mock
    private ValidacionDeclaracionService validacionDeclaracionService;
    @Mock
    private AlertaTriggerService alertaTriggerService;
    @Mock
    private GestionMensajeService gestionMensajeService;
    @Mock
    private DeclaracionBuzosRepository declaracionBuzosRepository;

    private ComunaModel comunaFreirina;
    private ComunaModel comunaAndacollo;
    private CaletaModel caletaChepiquillaFreirina;
    private UsuarioModel usuarioMock;
    private EspecieModel especieMock;
    private HumedadEstadoModel humedadMock;

    @BeforeEach
    void setUp() {
        comunaFreirina = new ComunaModel();
        comunaFreirina.setId(100L);
        comunaFreirina.setNombre("Freirina");

        comunaAndacollo = new ComunaModel();
        comunaAndacollo.setId(200L);
        comunaAndacollo.setNombre("Andacollo");

        caletaChepiquillaFreirina = new CaletaModel();
        caletaChepiquillaFreirina.setId(10L);
        caletaChepiquillaFreirina.setNombre("Chepiquilla");
        caletaChepiquillaFreirina.setComuna(comunaFreirina);

        usuarioMock = new UsuarioModel();
        usuarioMock.setId(1L);
        usuarioMock.setRut("11.111.111-1");
        usuarioMock.setComuna(comunaFreirina);

        especieMock = new EspecieModel();
        especieMock.setId(1L);
        especieMock.setNombre("Huiro Negro");

        humedadMock = new HumedadEstadoModel();
        humedadMock.setId(1L);
        humedadMock.setNombre("HÚMEDO");
    }

    private ResultadoValidacion buildAprobado(BigDecimal captura) {
        return ResultadoValidacion.builder()
                .decision(ResultadoValidacion.DecisionValidacion.PERMITIR)
                .capturaCalculada(captura)
                .factorAplicado(BigDecimal.ONE)
                .build();
    }

    // =========================================================================
    // RECOLECTOR TESTS (T1.3)
    // =========================================================================

    @Test
    void testRecolector_CaletaComunaCoinciden_PermiteGuardar() {
        DeclaracionRecolectorModel req = new DeclaracionRecolectorModel();
        req.setFechaDeclaracion(new Date());
        req.setFechaExtraccion(new Date());
        req.setCaleta(caletaChepiquillaFreirina);
        req.setComuna(comunaFreirina);
        req.setUsuario(usuarioMock);
        req.setEspecie(especieMock);
        req.setHumedadEstado(humedadMock);
        req.setDesembarque(BigDecimal.valueOf(1500.0));

        when(entityManager.find(CaletaModel.class, 10L)).thenReturn(caletaChepiquillaFreirina);
        when(entityManager.find(ComunaModel.class, 100L)).thenReturn(comunaFreirina);
        when(entityManager.find(UsuarioModel.class, 1L)).thenReturn(usuarioMock);
        when(entityManager.find(EspecieModel.class, 1L)).thenReturn(especieMock);
        when(entityManager.find(HumedadEstadoModel.class, 1L)).thenReturn(humedadMock);

        when(validacionDeclaracionService.validar(any())).thenReturn(buildAprobado(BigDecimal.valueOf(1500.0)));
        when(recolectorRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DeclaracionRecolectorModel result = recolectorService.saveDeclaracionRecolector(req);

        assertNotNull(result);
        assertEquals(comunaFreirina.getId(), result.getComuna().getId());
        assertEquals(caletaChepiquillaFreirina.getId(), result.getCaleta().getId());
    }

    @Test
    void testRecolector_CaletaComunaDiscrepan_Lanza422ConMensajeDescriptivo() {
        DeclaracionRecolectorModel req = new DeclaracionRecolectorModel();
        req.setFechaDeclaracion(new Date());
        req.setFechaExtraccion(new Date());
        req.setCaleta(caletaChepiquillaFreirina);
        req.setComuna(comunaAndacollo);

        when(entityManager.find(CaletaModel.class, 10L)).thenReturn(caletaChepiquillaFreirina);
        when(entityManager.find(ComunaModel.class, 200L)).thenReturn(comunaAndacollo);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            recolectorService.saveDeclaracionRecolector(req);
        });

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
        assertEquals("La caleta Chepiquilla pertenece a Freirina, no a Andacollo", ex.getReason());
    }

    @Test
    void testRecolector_CaletaSinComuna_AutocompletaComunaDesdeCaleta() {
        DeclaracionRecolectorModel req = new DeclaracionRecolectorModel();
        req.setFechaDeclaracion(new Date());
        req.setFechaExtraccion(new Date());
        req.setCaleta(caletaChepiquillaFreirina);
        req.setComuna(null);
        req.setUsuario(usuarioMock);
        req.setEspecie(especieMock);
        req.setHumedadEstado(humedadMock);
        req.setDesembarque(BigDecimal.valueOf(1000.0));

        when(entityManager.find(CaletaModel.class, 10L)).thenReturn(caletaChepiquillaFreirina);
        when(entityManager.find(UsuarioModel.class, 1L)).thenReturn(usuarioMock);
        when(entityManager.find(EspecieModel.class, 1L)).thenReturn(especieMock);
        when(entityManager.find(HumedadEstadoModel.class, 1L)).thenReturn(humedadMock);

        when(validacionDeclaracionService.validar(any())).thenReturn(buildAprobado(BigDecimal.valueOf(1000.0)));
        when(recolectorRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DeclaracionRecolectorModel result = recolectorService.saveDeclaracionRecolector(req);

        assertNotNull(result);
        assertNotNull(result.getComuna());
        assertEquals(comunaFreirina.getId(), result.getComuna().getId());
        assertEquals("Freirina", result.getComuna().getNombre());
    }

    // =========================================================================
    // ARMADOR TESTS (T1.3)
    // =========================================================================

    @Test
    void testArmador_CaletaComunaCoinciden_PermiteGuardar() {
        DeclaracionArmadorModel req = new DeclaracionArmadorModel();
        req.setFechaDeclaracion(new Date());
        req.setFechaExtraccion(new Date());
        req.setHora("12:00:00");
        req.setDesembarque(BigDecimal.valueOf(2000.0));
        req.setCaleta(caletaChepiquillaFreirina);
        req.setComuna(comunaFreirina);
        req.setUsuario(usuarioMock);
        req.setUsuarioDestinatario(usuarioMock);
        req.setEspecie(especieMock);
        req.setHumedadEstado(humedadMock);

        EmbarcacionModel emb = new EmbarcacionModel();
        emb.setId(5L);
        emb.setNombre("Bote 1");
        req.setEmbarcacion(emb);

        BuzoModel buzo = new BuzoModel();
        buzo.setId(6L);
        buzo.setNombre("Buzo 1");
        req.setBuzo(buzo);

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuarioMock));
        when(caletaRepository.findById(10L)).thenReturn(Optional.of(caletaChepiquillaFreirina));
        when(comunaRepository.findById(100L)).thenReturn(Optional.of(comunaFreirina));
        when(especieRepository.findById(1L)).thenReturn(Optional.of(especieMock));
        when(humedadEstadoRepository.findById(1L)).thenReturn(Optional.of(humedadMock));
        when(embarcacionRepository.findById(5L)).thenReturn(Optional.of(emb));
        when(buzoRepository.findById(6L)).thenReturn(Optional.of(buzo));

        when(validacionDeclaracionService.validar(any())).thenReturn(buildAprobado(BigDecimal.valueOf(2000.0)));
        when(armadorRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DeclaracionArmadorModel result = armadorService.saveDeclaracionArmador(req);

        assertNotNull(result);
        assertEquals(comunaFreirina.getId(), result.getComuna().getId());
        assertEquals(caletaChepiquillaFreirina.getId(), result.getCaleta().getId());
    }

    @Test
    void testArmador_CaletaComunaDiscrepan_Lanza422ConMensajeDescriptivo() {
        DeclaracionArmadorModel req = new DeclaracionArmadorModel();
        req.setFechaDeclaracion(new Date());
        req.setFechaExtraccion(new Date());
        req.setHora("12:00:00");
        req.setDesembarque(BigDecimal.valueOf(2000.0));
        req.setCaleta(caletaChepiquillaFreirina);
        req.setComuna(comunaAndacollo);
        req.setUsuario(usuarioMock);
        req.setUsuarioDestinatario(usuarioMock);

        EmbarcacionModel emb = new EmbarcacionModel();
        emb.setId(5L);
        req.setEmbarcacion(emb);

        BuzoModel buzo = new BuzoModel();
        buzo.setId(6L);
        req.setBuzo(buzo);

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuarioMock));
        when(embarcacionRepository.findById(5L)).thenReturn(Optional.of(emb));
        when(buzoRepository.findById(6L)).thenReturn(Optional.of(buzo));
        when(caletaRepository.findById(10L)).thenReturn(Optional.of(caletaChepiquillaFreirina));
        when(comunaRepository.findById(200L)).thenReturn(Optional.of(comunaAndacollo));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            armadorService.saveDeclaracionArmador(req);
        });

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
        assertEquals("La caleta Chepiquilla pertenece a Freirina, no a Andacollo", ex.getReason());
    }

    @Test
    void testArmador_CaletaSinComuna_AutocompletaComunaDesdeCaleta() {
        DeclaracionArmadorModel req = new DeclaracionArmadorModel();
        req.setFechaDeclaracion(new Date());
        req.setFechaExtraccion(new Date());
        req.setHora("12:00:00");
        req.setDesembarque(BigDecimal.valueOf(2000.0));
        req.setCaleta(caletaChepiquillaFreirina);
        req.setComuna(null);
        req.setUsuario(usuarioMock);
        req.setUsuarioDestinatario(usuarioMock);
        req.setEspecie(especieMock);
        req.setHumedadEstado(humedadMock);

        EmbarcacionModel emb = new EmbarcacionModel();
        emb.setId(5L);
        req.setEmbarcacion(emb);

        BuzoModel buzo = new BuzoModel();
        buzo.setId(6L);
        req.setBuzo(buzo);

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuarioMock));
        when(embarcacionRepository.findById(5L)).thenReturn(Optional.of(emb));
        when(buzoRepository.findById(6L)).thenReturn(Optional.of(buzo));
        when(caletaRepository.findById(10L)).thenReturn(Optional.of(caletaChepiquillaFreirina));
        when(especieRepository.findById(1L)).thenReturn(Optional.of(especieMock));
        when(humedadEstadoRepository.findById(1L)).thenReturn(Optional.of(humedadMock));

        when(validacionDeclaracionService.validar(any())).thenReturn(buildAprobado(BigDecimal.valueOf(2000.0)));
        when(armadorRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DeclaracionArmadorModel result = armadorService.saveDeclaracionArmador(req);

        assertNotNull(result);
        assertNotNull(result.getComuna());
        assertEquals(comunaFreirina.getId(), result.getComuna().getId());
        assertEquals("Freirina", result.getComuna().getNombre());
    }

    // =========================================================================
    // AREA TESTS (T1.3)
    // =========================================================================

    @Test
    void testArea_CaletaAmerbComunaCoinciden_PermiteGuardar() {
        AmerbModel amerb = new AmerbModel();
        amerb.setId(1L);
        amerb.setNombre("AMERB Punta Freirina");
        amerb.setComuna(comunaFreirina);

        DeclaracionAreaModel req = new DeclaracionAreaModel();
        req.setFechaDeclaracion(new Date());
        req.setFechaExtraccion(new Date());
        req.setAmerb(amerb);
        req.setCaleta(caletaChepiquillaFreirina);
        req.setUsuario(usuarioMock);
        req.setEspecie(especieMock);
        req.setHumedadEstado(humedadMock);
        req.setDesembarque(3000.0);

        when(caletaRepository.findById(10L)).thenReturn(Optional.of(caletaChepiquillaFreirina));
        when(amerbRepository.findById(1L)).thenReturn(Optional.of(amerb));

        when(validacionDeclaracionService.validar(any())).thenReturn(buildAprobado(BigDecimal.valueOf(3000.0)));
        when(areaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DeclaracionAreaModel result = areaService.saveDeclaracion(req);

        assertNotNull(result);
        assertEquals(caletaChepiquillaFreirina.getId(), result.getCaleta().getId());
        assertEquals(amerb.getId(), result.getAmerb().getId());
    }

    @Test
    void testArea_CaletaAmerbComunaDiscrepan_Lanza422ConMensajeDescriptivo() {
        AmerbModel amerbAndacollo = new AmerbModel();
        amerbAndacollo.setId(2L);
        amerbAndacollo.setNombre("AMERB Andacollo Costa");
        amerbAndacollo.setComuna(comunaAndacollo);

        DeclaracionAreaModel req = new DeclaracionAreaModel();
        req.setFechaDeclaracion(new Date());
        req.setFechaExtraccion(new Date());
        req.setAmerb(amerbAndacollo);
        req.setCaleta(caletaChepiquillaFreirina);

        when(amerbRepository.findById(2L)).thenReturn(Optional.of(amerbAndacollo));
        when(caletaRepository.findById(10L)).thenReturn(Optional.of(caletaChepiquillaFreirina));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            areaService.saveDeclaracion(req);
        });

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
        assertEquals("La caleta Chepiquilla pertenece a Freirina, no a Andacollo", ex.getReason());
    }
}
