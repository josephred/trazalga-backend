package com.trazalga.api.revision13oct;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.trazalga.api.IntegracionBase;
import com.trazalga.api.models.DeclaracionComercializadorModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.models.PerfilModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.services.retencion.FiltroRetencionBodega;
import com.trazalga.api.services.retencion.LoteBodegaDTO;
import com.trazalga.api.services.retencion.RetencionCustodiaService;

import jakarta.persistence.EntityManager;

/**
 * Revisión 13-10 · TR.1 del plan y ADR-002, rama de declaraciones de comercializador.
 * La bodega de C son las declaraciones con usuario_destinatario_id = C (C con perfil comercializador),
 * no despachadas y no anuladas ni rechazadas.
 * - Lo que C ya despachó a una planta y la planta no ha recibido está «en tránsito» (lo mide el Indicador 6).
 * - Lo que C1 despachó a otro comercializador C2 y C2 no ha consumido está en la bodega de C2 (varios saltos).
 * La consulta T0.3 de auditoria_09oct.sql ya usa esta definición; RetencionCustodiaService.buildSqlLotesBodega no.
 */
class RevisionBodegaComercializadorTest extends IntegracionBase {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private RetencionCustodiaService retencionCustodiaService;

    @Test
    @DisplayName("TR.1: un despacho de C a una planta, aún no recibido, no está en la bodega de C (está en tránsito)")
    void despachoEnTransitoNoEstaEnBodega() {
        long[] ids = crearDespachoHumedoDeNueveDias("PLANTA"); // {despacho, emisor C, receptor}
        Optional<LoteBodegaDTO> lote = lotesDeComercializador().stream().filter(l -> l.getId() == ids[0]).findFirst();
        System.out.println("REVISION13_BODEGA despacho C->planta id=" + ids[0] + " | en bodega: "
                + lote.map(l -> "sí, titular=" + l.getComercializadorId() + " (emisor C=" + ids[1] + ")").orElse("no"));
        assertTrue(lote.isEmpty(),
                "Un despacho a planta no recibido está en tránsito: no debe contarse en la bodega ni retenerse por TR.3");
    }

    @Test
    @DisplayName("TR.1: un despacho de C1 a otro comercializador C2, aún no consumido, está en la bodega de C2")
    void despachoEntreComercializadoresEsDeLaBodegaDelReceptor() {
        long[] ids = crearDespachoHumedoDeNueveDias("COMERCIALIZADOR"); // {despacho, emisor C1, receptor C2}
        LoteBodegaDTO lote = lotesDeComercializador().stream().filter(l -> l.getId() == ids[0]).findFirst()
                .orElseThrow(() -> new AssertionError("El despacho de C1 a C2 debe estar en la bodega de C2"));
        System.out.println("REVISION13_BODEGA despacho C1->C2 id=" + ids[0] + " | titular informado=" + lote.getComercializadorId()
                + " (C1=" + ids[1] + ", C2=" + ids[2] + ")");
        assertEquals(ids[2], lote.getComercializadorId(),
                "El titular del lote en bodega es el receptor C2, no el emisor C1");
    }

    private List<LoteBodegaDTO> lotesDeComercializador() {
        return retencionCustodiaService.lotesEnBodega(FiltroRetencionBodega.builder().soloEnBodega(true).build())
                .stream().filter(l -> "COMERCIALIZADOR".equals(l.getTipo())).toList();
    }

    private long[] crearDespachoHumedoDeNueveDias(String perfilReceptor) {
        long[] ids = new TransactionTemplate(txManager).execute(s -> {
            int n = SEQ.incrementAndGet();
            PerfilModel pCom = perfil("COMERCIALIZADOR");
            PerfilModel pRec = perfil(perfilReceptor);
            UsuarioModel emisor = usuario("REV13-B-EMI-" + n, pCom);
            UsuarioModel receptor = usuario("REV13-B-REC-" + n, pRec);
            EspecieModel esp = new EspecieModel(); esp.setNombre("Huiro negro R13B-" + n); em.persist(esp);
            HumedadEstadoModel hum = humedad("HÚMEDO");

            java.sql.Date hace9 = java.sql.Date.valueOf(LocalDate.now().minusDays(9));
            DeclaracionComercializadorModel d = new DeclaracionComercializadorModel();
            d.setUsuario(emisor); d.setUsuarioDestinatario(receptor);
            d.setCodigoDestinatario("X"); d.setNombreDestinatario("Receptor " + perfilReceptor);
            d.setFechaDeclaracion(hace9); d.setFechaTraslado(hace9); d.setHora("08:00");
            d.setEspecie(esp); d.setHumedadEstado(hum); d.setCantidad(new BigDecimal("1000"));
            d.setFolioDesembarqueAc("REV13-AC-" + n);
            em.persist(d);
            return new long[]{d.getId(), emisor.getId(), receptor.getId()};
        });
        em.clear();
        return ids;
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

    private UsuarioModel usuario(String rut, PerfilModel perfil) {
        UsuarioModel u = new UsuarioModel();
        u.setRut(rut); u.setNombres("Usuario"); u.setApellidop(rut); u.setPerfil(perfil);
        em.persist(u);
        return u;
    }
}
