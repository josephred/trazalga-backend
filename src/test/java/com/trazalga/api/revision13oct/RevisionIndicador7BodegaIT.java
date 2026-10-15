package com.trazalga.api.revision13oct;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.trazalga.api.IntegracionBase;
import com.trazalga.api.models.*;
import com.trazalga.api.services.CadenaCustodiaService;
import com.trazalga.api.services.ReportService;
import com.trazalga.api.services.retencion.FiltroRetencionBodega;
import com.trazalga.api.services.retencion.LoteBodegaDTO;
import com.trazalga.api.services.retencion.RetencionCustodiaService;

import jakarta.persistence.EntityManager;

/**
 * TR.2 del plan de correcciones 13-oct:
 * - El tiempo de un lote es el mismo en el Indicador 7, en la ficha y en el barrido.
 * - Lo asignado a comercializador y no despachado cuenta «en bodega».
 * - Lo despachado con destino a planta y aún no recibido cuenta «en tránsito a planta».
 */
class RevisionIndicador7BodegaIT extends IntegracionBase {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private RetencionCustodiaService retencionCustodiaService;

    @Test
    @DisplayName("TR.2: Lo asignado cuenta en bodega, lo despachado a planta cuenta en tránsito y las horas son idénticas")
    void metricasCanonicaBodegaYTransito() {
        int n = SEQ.incrementAndGet();
        java.sql.Date hace3Dias = java.sql.Date.valueOf(LocalDate.now().minusDays(3));

        long[] ids = new TransactionTemplate(txManager).execute(s -> {
            PerfilModel pCom = perfil("COMERCIALIZADOR");
            PerfilModel pPla = perfil("PLANTA");
            PerfilModel pRec = perfil("RECOLECTOR");

            UsuarioModel recolector = usuario("REV13-TR2-REC-" + n, pRec);
            UsuarioModel comercializador = usuario("REV13-TR2-COM-" + n, pCom);
            UsuarioModel planta = usuario("REV13-TR2-PLA-" + n, pPla);

            EspecieModel esp = new EspecieModel(); esp.setNombre("Huiro negro TR2-" + n); em.persist(esp);
            HumedadEstadoModel hum = humedad("HÚMEDO");
            ComunaModel com = comuna("Caldera TR2-" + n);
            CaletaModel cal = caleta("Caleta TR2-" + n, com);
            ExtraccionTipoModel ext = extraccion("Varado TR2-" + n);

            // 1. Asignado a Comercializador y no despachado -> EN BODEGA
            DeclaracionRecolectorModel enBodega = new DeclaracionRecolectorModel();
            enBodega.setUsuario(recolector);
            enBodega.setUsuarioDestinatario(comercializador);
            enBodega.setCodigoDestinatario("COM");
            enBodega.setNombreDestinatario("Comercializador");
            enBodega.setEspecie(esp);
            enBodega.setHumedadEstado(hum);
            enBodega.setComuna(com);
            enBodega.setCaleta(cal);
            enBodega.setExtraccionTipo(ext);
            enBodega.setFechaDeclaracion(hace3Dias);
            enBodega.setFechaExtraccion(hace3Dias);
            enBodega.setHora("10:00");
            enBodega.setNombre("Recolector TR2");
            enBodega.setCodigoSernapesca("RPA-TR2-" + n);
            enBodega.setDesembarque(new BigDecimal("1500"));
            em.persist(enBodega);

            // 2. Despachado por Comercializador con destino a PLANTA y aún no recibido -> EN TRÁNSITO A PLANTA
            DeclaracionComercializadorModel enTransito = new DeclaracionComercializadorModel();
            enTransito.setUsuario(comercializador);
            enTransito.setUsuarioDestinatario(planta);
            enTransito.setCodigoDestinatario("PLA");
            enTransito.setNombreDestinatario("Planta Harinera");
            enTransito.setEspecie(esp);
            enTransito.setHumedadEstado(hum);
            enTransito.setFechaDeclaracion(hace3Dias);
            enTransito.setFechaTraslado(hace3Dias);
            enTransito.setHora("11:00");
            enTransito.setCantidad(new BigDecimal("2500"));
            enTransito.setFolioDesembarqueAc("AC-TR2-" + n);
            em.persist(enTransito);

            return new long[]{enBodega.getId(), enTransito.getId()};
        });
        em.clear();

        // Evaluar Indicador 7
        Map<String, Object> metricas = retencionCustodiaService.obtenerMetricasRetencion(null, null);

        assertNotNull(metricas, "Métricas de retención deben estar presentes");
        assertEquals(true, metricas.get("activo"));
        assertEquals(false, metricas.get("controlDesactivado"));

        // 1. Lo asignado a comercializador cuenta en bodega
        long totalBodega = ((Number) metricas.get("totalLotesEnBodega")).longValue();
        assertTrue(totalBodega >= 1, "Debe haber al menos 1 lote en bodega");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lotes = (List<Map<String, Object>>) metricas.get("lotes");
        Map<String, Object> loteEnBodega = lotes.stream()
                .filter(l -> "RECOLECTOR".equals(l.get("tipo")) && Long.valueOf(ids[0]).equals(l.get("id")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("La declaración de recolector debe figurar en lotes en bodega"));

        // 2. Lo despachado a planta cuenta en tránsito a planta
        long totalTransito = ((Number) metricas.get("totalLotesEnTransitoAPlanta")).longValue();
        assertTrue(totalTransito >= 1, "El despacho hacia planta debe contarse en totalLotesEnTransitoAPlanta");

        // 3. El tiempo es el mismo en el Indicador 7 que en CadenaCustodiaService.horasEnBodega
        LocalDateTime inicioTs = ReportService.parseTimestamp(hace3Dias, "10:00");
        double horasEsperadas = CadenaCustodiaService.horasEnBodega(inicioTs, LocalDateTime.now());
        double horasReportadas = ((Number) loteEnBodega.get("horasTranscurridas")).doubleValue();

        assertEquals(horasEsperadas, horasReportadas, 0.5,
                "El tiempo en horas de un lote en el Indicador 7 debe ser idéntico al cálculo de CadenaCustodiaService");

        // 4. Matriz 4x3 calculada sobre los lotes en bodega
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> matriz = (Map<String, Map<String, Object>>) metricas.get("matriz");
        assertNotNull(matriz, "La matriz debe estar poblada");
        assertTrue(matriz.containsKey("HUMEDO"), "La matriz debe contener fila HUMEDO");
    }

    private PerfilModel perfil(String nombre) {
        return em.createQuery("SELECT p FROM PerfilModel p WHERE p.nombre = :n", PerfilModel.class)
                .setParameter("n", nombre).getResultStream().findFirst()
                .orElseGet(() -> { PerfilModel p = PerfilModel.builder().nombre(nombre).build(); em.persist(p); return p; });
    }

    private HumedadEstadoModel humedad(String nombre) {
        return em.createQuery("SELECT h FROM HumedadEstadoModel h WHERE h.nombre = :n", HumedadEstadoModel.class)
                .setParameter("n", nombre).getResultStream().findFirst()
                .orElseGet(() -> { HumedadEstadoModel h = new HumedadEstadoModel(); h.setNombre(nombre); em.persist(h); return h; });
    }

    private ComunaModel comuna(String nombre) {
        return em.createQuery("SELECT c FROM ComunaModel c WHERE c.nombre = :n", ComunaModel.class)
                .setParameter("n", nombre).getResultStream().findFirst()
                .orElseGet(() -> { ComunaModel c = new ComunaModel(); c.setNombre(nombre); em.persist(c); return c; });
    }

    private CaletaModel caleta(String nombre, ComunaModel comuna) {
        return em.createQuery("SELECT c FROM CaletaModel c WHERE c.nombre = :n", CaletaModel.class)
                .setParameter("n", nombre).getResultStream().findFirst()
                .orElseGet(() -> { CaletaModel c = new CaletaModel(); c.setNombre(nombre); c.setComuna(comuna); em.persist(c); return c; });
    }

    private ExtraccionTipoModel extraccion(String nombre) {
        return em.createQuery("SELECT e FROM ExtraccionTipoModel e WHERE e.nombre = :n", ExtraccionTipoModel.class)
                .setParameter("n", nombre).getResultStream().findFirst()
                .orElseGet(() -> { ExtraccionTipoModel e = new ExtraccionTipoModel(); e.setNombre(nombre); em.persist(e); return e; });
    }

    private UsuarioModel usuario(String rut, PerfilModel perfil) {
        UsuarioModel u = new UsuarioModel();
        u.setRut(rut); u.setNombres("Usuario"); u.setApellidop(rut); u.setPerfil(perfil);
        em.persist(u);
        return u;
    }
}
