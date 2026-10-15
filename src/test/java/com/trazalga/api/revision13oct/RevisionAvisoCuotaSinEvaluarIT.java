package com.trazalga.api.revision13oct;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.trazalga.api.IntegracionBase;
import com.trazalga.api.models.CaletaModel;
import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.ExtraccionTipoModel;
import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.models.PerfilModel;
import com.trazalga.api.models.ProvinciaModel;
import com.trazalga.api.models.RegionModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.ICaletaRepository;
import com.trazalga.api.repositories.IComunaRepository;
import com.trazalga.api.repositories.ICuotaExtraccionRepository;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;
import com.trazalga.api.repositories.IEspecieRepository;
import com.trazalga.api.repositories.IExtraccionTipoRepository;
import com.trazalga.api.repositories.IHumedadEstadoRepository;
import com.trazalga.api.repositories.IPerfilRepository;
import com.trazalga.api.repositories.IProvinciaRepository;
import com.trazalga.api.repositories.IRegionRepository;
import com.trazalga.api.repositories.IUsuarioRepository;
import com.trazalga.api.services.cuotas.RecalculoCuotaService;

import jakarta.persistence.EntityManager;

/**
 * TQ.3 · Aceptación:
 * "Prueba de integración: una cuota creada el día 10 cuenta las tres declaraciones
 * del 1 al 9 y no las del 11, con una consulta por tabla."
 */
class RevisionAvisoCuotaSinEvaluarIT extends IntegracionBase {

    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private RecalculoCuotaService recalculoService;
    @Autowired private ICuotaExtraccionRepository cuotaRepository;
    @Autowired private IDeclaracionRecolectorRepository declaracionRecolectorRepository;
    @Autowired private IRegionRepository regionRepository;
    @Autowired private IProvinciaRepository provinciaRepository;
    @Autowired private IComunaRepository comunaRepository;
    @Autowired private ICaletaRepository caletaRepository;
    @Autowired private IEspecieRepository especieRepository;
    @Autowired private IExtraccionTipoRepository extraccionTipoRepository;
    @Autowired private IHumedadEstadoRepository humedadEstadoRepository;
    @Autowired private IPerfilRepository perfilRepository;
    @Autowired private IUsuarioRepository usuarioRepository;

    @Test
    @DisplayName("TQ.3: Una cuota creada el día 10 cuenta las 3 declaraciones del 1 al 9 y no las del 11")
    void testCuotaCreadaDia10_CuentaDeclaracionesPreviasSinEvaluar() {
        RegionModel region = regionRepository.saveAndFlush(RegionModel.builder().nombre("Región TQ3").codigo("04").build());
        ProvinciaModel prov = provinciaRepository.saveAndFlush(ProvinciaModel.builder().nombre("Provincia TQ3").region(region).build());
        ComunaModel comuna = comunaRepository.saveAndFlush(ComunaModel.builder().nombre("Comuna TQ3").region(region).provincia(prov).build());
        CaletaModel caleta = caletaRepository.saveAndFlush(CaletaModel.builder().nombre("Caleta TQ3").region(region).comuna(comuna).build());

        EspecieModel esp = especieRepository.saveAndFlush(EspecieModel.builder().nombre("Huiro negro TQ3").build());
        ExtraccionTipoModel ext = extraccionTipoRepository.saveAndFlush(ExtraccionTipoModel.builder().nombre("Varado TQ3").build());
        HumedadEstadoModel hum = humedadEstadoRepository.saveAndFlush(HumedadEstadoModel.builder().nombre("Húmedo TQ3").build());

        PerfilModel perfilRec = perfilRepository.findAll().stream()
                .filter(p -> "RECOLECTOR".equalsIgnoreCase(p.getNombre()))
                .findFirst()
                .orElseGet(() -> perfilRepository.saveAndFlush(PerfilModel.builder().nombre("RECOLECTOR").build()));

        UsuarioModel usuario = usuarioRepository.saveAndFlush(UsuarioModel.builder()
                .rut("15.111.222-3")
                .nombres("Recolector")
                .apellidop("TQ3")
                .perfil(perfilRec)
                .comuna(comuna)
                .build());

        UsuarioModel dest = usuarioRepository.saveAndFlush(UsuarioModel.builder()
                .rut("76.111.222-4")
                .nombres("Destinatario")
                .apellidop("TQ3")
                .perfil(perfilRec)
                .comuna(comuna)
                .build());

        // 3 Declaraciones entre los días 1 y 9
        crearDecl(usuario, dest, esp, ext, hum, comuna, caleta, "2026-10-02", "08:30:00", "100.00");
        crearDecl(usuario, dest, esp, ext, hum, comuna, caleta, "2026-10-05", "10:15:00", "200.00");
        crearDecl(usuario, dest, esp, ext, hum, comuna, caleta, "2026-10-09", "16:45:00", "300.00");

        // 1 Declaración el día 11 (posterior a la creación de la cuota)
        crearDecl(usuario, dest, esp, ext, hum, comuna, caleta, "2026-10-11", "09:00:00", "150.00");

        Set<ComunaModel> comunas = new LinkedHashSet<>();
        comunas.add(comuna);

        // Cuota creada el día 10 a las 12:00:00
        Timestamp fechaCreacionCuota = Timestamp.valueOf(LocalDateTime.of(2026, 10, 10, 12, 0, 0));

        CuotaExtraccionModel cuota = CuotaExtraccionModel.builder()
                .nivelAgregacion("COMUNA")
                .ambito("AREA_LIBRE")
                .perfil("RECOLECTOR")
                .region(region)
                .comuna(comuna)
                .comunas(comunas)
                .especie(esp)
                .extraccionTipo(ext)
                .periodo("MENSUAL")
                .fechaInicio(Date.valueOf("2026-10-01"))
                .fechaFin(Date.valueOf("2026-10-31"))
                .limiteKg(10000.0)
                .metrica("DESEMBARQUE")
                .resolucion("RES-TQ3-10OCT")
                .activo(true)
                .estado("ABIERTA")
                .build();

        cuota = cuotaRepository.saveAndFlush(cuota);

        // Forzar createdAt exacto al 10-10-2026 12:00:00
        final Long cuotaId = cuota.getId();
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            em.createNativeQuery("UPDATE cuota_extraccion SET created_at = :cat WHERE id = :id")
                    .setParameter("cat", fechaCreacionCuota)
                    .setParameter("id", cuotaId)
                    .executeUpdate();
        });
        em.clear();

        CuotaExtraccionModel cuotaCargada = cuotaRepository.findById(cuotaId).orElseThrow();

        int previasSinEvaluar = recalculoService.contarDeclaracionesPreviasSinEvaluar(cuotaCargada);

        assertEquals(3, previasSinEvaluar,
                "Una cuota creada el día 10 debe contar las 3 declaraciones de los días 2, 5 y 9, y excluir la del día 11");
    }

    private void crearDecl(UsuarioModel u, UsuarioModel dest, EspecieModel esp, ExtraccionTipoModel ext,
                           HumedadEstadoModel hum, ComunaModel com, CaletaModel cal,
                           String fechaStr, String horaStr, String kg) {
        DeclaracionRecolectorModel d = DeclaracionRecolectorModel.builder()
                .usuario(u)
                .especie(esp)
                .extraccionTipo(ext)
                .humedadEstado(hum)
                .comuna(com)
                .caleta(cal)
                .fechaDeclaracion(Date.valueOf(fechaStr))
                .fechaExtraccion(Date.valueOf(fechaStr))
                .hora(horaStr)
                .nombre("Recolector TQ3")
                .codigoSernapesca("RPA-TQ3-" + System.nanoTime())
                .folioDesembarqueRo("RO-TQ3-" + System.nanoTime())
                .codigoDestinatario("DEST-TQ3")
                .nombreDestinatario("Planta TQ3")
                .usuarioDestinatario(dest)
                .desembarque(new BigDecimal(kg))
                .captura(new BigDecimal(kg))
                .build();
        declaracionRecolectorRepository.saveAndFlush(d);
    }
}
