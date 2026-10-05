package com.trazalga.api.tasks;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trazalga.api.dto.ControlCuotaDiariaDTO;
import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.ExtraccionTipoModel;
import com.trazalga.api.models.RegionModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.ICuotaExtraccionRepository;
import com.trazalga.api.repositories.IUsuarioRepository;
import com.trazalga.api.services.ConfiguracionGeneralService;
import com.trazalga.api.services.CuotaExtraccionService;
import com.trazalga.api.services.NotificationService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/**
 * T1.7: Pruebas de aceptación para tablero y alertas programadas
 * operando exclusivamente sobre cuotas vigentes.
 */
@ExtendWith(MockitoExtension.class)
public class AlertaProgramadaTaskCuotasVigentesTest {

    @Mock
    private ICuotaExtraccionRepository cuotaRepository;

    @Mock
    private CuotaExtraccionService cuotaExtraccionService;

    @Mock
    private ConfiguracionGeneralService configuracionGeneralService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private IUsuarioRepository usuarioRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private AlertaProgramadaTask task;

    private RegionModel regionCoquimbo;
    private ComunaModel comunaLaSerena;
    private EspecieModel especieHuiro;
    private ExtraccionTipoModel metodoVarado;
    private UsuarioModel admin;

    @BeforeEach
    void setUp() {
        regionCoquimbo = RegionModel.builder().id(4L).nombre("Coquimbo").build();
        comunaLaSerena = ComunaModel.builder().id(4101L).nombre("La Serena").region(regionCoquimbo).build();
        especieHuiro = EspecieModel.builder().id(1L).nombre("Huiro negro").build();
        metodoVarado = ExtraccionTipoModel.builder().id(10L).nombre("Varado").build();

        admin = UsuarioModel.builder().id(1L).nombres("Admin").build();

        lenient().when(configuracionGeneralService.getValor("cuota_umbral_restante_pct", "10.0")).thenReturn("10.0");
        lenient().when(configuracionGeneralService.getValor("cuota_dias_previos_expiracion", "5")).thenReturn("5");
        lenient().when(configuracionGeneralService.getValor("cuota_desvio_velocidad_pct", "25.0")).thenReturn("25.0");
        lenient().when(usuarioRepository.findByPerfilId(1L)).thenReturn(List.of(admin));
        lenient().when(cuotaExtraccionService.idsComunas(any(CuotaExtraccionModel.class))).thenAnswer(inv -> {
            CuotaExtraccionModel c = inv.getArgument(0);
            if (c.getComunas() != null && !c.getComunas().isEmpty()) {
                return Set.of(4101L);
            }
            return Collections.emptySet();
        });
    }

    private CuotaExtraccionModel crearCuotaMes(long id, int anio, int mes) {
        LocalDate ini = LocalDate.of(anio, mes, 1);
        LocalDate fin = ini.withDayOfMonth(ini.lengthOfMonth());
        return CuotaExtraccionModel.builder()
                .id(id)
                .ambito("AREA_LIBRE")
                .nivelAgregacion("COMUNA")
                .region(regionCoquimbo)
                .comunas(Set.of(comunaLaSerena))
                .especie(especieHuiro)
                .extraccionTipo(metodoVarado)
                .periodo("MENSUAL")
                .fechaInicio(Date.valueOf(ini))
                .fechaFin(Date.valueOf(fin))
                .limiteKg(10_000.0)
                .metrica("DESEMBARQUE")
                .activo(true)
                .estado("ABIERTA")
                .build();
    }

    @Test
    @DisplayName("T1.7: Una cuota de marzo excedida no genera notificaciones en octubre")
    void testCuotaMarzoExcedida_NoGeneraNotificacionesEnOctubre() {
        CuotaExtraccionModel cuotaMarzo = crearCuotaMes(3L, 2026, 3);
        when(cuotaRepository.findByActivoTrue()).thenReturn(List.of(cuotaMarzo));

        LocalDate hoyOctubre = LocalDate.of(2026, 10, 15);
        Date hoyDate = Date.valueOf(hoyOctubre);

        task.revisarCuotas(hoyDate, hoyOctubre);

        // No debe haberse enviado ninguna notificación porque marzo expiró
        verify(notificationService, never()).sendPushNotificationToUser(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("T1.7: La cuota de octubre no avisa expiración si existe la cuota sucesora de noviembre")
    void testCuotaOctubreConSucesoraNoviembre_NoAvisaExpiracion() {
        CuotaExtraccionModel cuotaOctubre = crearCuotaMes(10L, 2026, 10);
        CuotaExtraccionModel cuotaNoviembre = crearCuotaMes(11L, 2026, 11);

        when(cuotaRepository.findByActivoTrue()).thenReturn(List.of(cuotaOctubre, cuotaNoviembre));

        // 28 de octubre: 3 días antes del fin de vigencia (31 de octubre), dentro del umbral de 5 días
        LocalDate hoy28Oct = LocalDate.of(2026, 10, 28);
        Date hoyDate = Date.valueOf(hoy28Oct);

        when(cuotaExtraccionService.calcularLimiteEfectivo(eq(cuotaOctubre), any(Date.class)))
                .thenReturn(BigDecimal.valueOf(10_000.0));
        // Consumo normal del 50%, no dispara alerta de saldo
        when(cuotaExtraccionService.calcularConsumoAcumulado(eq(cuotaOctubre), any(Date.class)))
                .thenReturn(BigDecimal.valueOf(5_000.0));
        when(cuotaExtraccionService.describirAlcance(cuotaOctubre)).thenReturn("Comuna La Serena");

        task.revisarCuotas(hoyDate, hoy28Oct);

        // Al existir cuota de noviembre, se suprime el aviso de expiración
        verify(notificationService, never()).sendPushNotificationToUser(
                anyLong(),
                contains("Expiración"),
                anyString()
        );
    }

    @Test
    @DisplayName("T1.7: La cuota de diciembre, sin sucesora para enero, sí avisa expiración")
    void testCuotaDiciembreSinSucesora_SiAvisaExpiracion() {
        CuotaExtraccionModel cuotaDiciembre = crearCuotaMes(12L, 2026, 12);

        when(cuotaRepository.findByActivoTrue()).thenReturn(List.of(cuotaDiciembre));

        // 28 de diciembre: 3 días antes del término (31 de diciembre)
        LocalDate hoy28Dic = LocalDate.of(2026, 12, 28);
        Date hoyDate = Date.valueOf(hoy28Dic);

        when(cuotaExtraccionService.calcularLimiteEfectivo(eq(cuotaDiciembre), any(Date.class)))
                .thenReturn(BigDecimal.valueOf(10_000.0));
        when(cuotaExtraccionService.calcularConsumoAcumulado(eq(cuotaDiciembre), any(Date.class)))
                .thenReturn(BigDecimal.valueOf(5_000.0));
        when(cuotaExtraccionService.describirAlcance(cuotaDiciembre)).thenReturn("Comuna La Serena");

        task.revisarCuotas(hoyDate, hoy28Dic);

        // Sin sucesora contigua para el 1 de enero, debe enviar aviso de expiración
        verify(notificationService, atLeastOnce()).sendPushNotificationToUser(
                eq(1L),
                contains("Aviso de Expiración de Cuota"),
                contains("vencerá en 3 día(s)")
        );
    }
}
