package com.trazalga.api.revision09oct;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.trazalga.api.IntegracionBase;
import com.trazalga.api.models.*;
import com.trazalga.api.services.retencion.FiltroRetencionBodega;
import com.trazalga.api.services.retencion.LoteBodegaDTO;
import com.trazalga.api.services.retencion.RetencionCustodiaService;

import jakarta.persistence.EntityManager;

/**
 * Revisión 12-10 · TR.1 del plan: la bodega del comercializador C son las declaraciones con
 * usuario_destinatario_id = C (perfil comercializador), no despachadas y no anuladas ni rechazadas.
 * En la base, «estado» queda NULL en la gran mayoría de las declaraciones (el código lo trata como ENVIADA).
 */
class RevisionBodegaIT extends IntegracionBase {

    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private RetencionCustodiaService retencionCustodiaService;

    @Test
    @DisplayName("TR.1: entra lo asignado a un comercializador (aunque estado sea NULL) y no lo asignado a una planta")
    void definicionDeBodega() {
        long[] ids = new TransactionTemplate(txManager).execute(s -> {
            PerfilModel pCom = PerfilModel.builder().nombre("COMERCIALIZADOR").build(); em.persist(pCom);
            PerfilModel pPla = PerfilModel.builder().nombre("PLANTA").build(); em.persist(pPla);
            PerfilModel pRec = PerfilModel.builder().nombre("RECOLECTOR").build(); em.persist(pRec);
            UsuarioModel rec = new UsuarioModel(); rec.setRut("REV-B-REC"); rec.setNombres("Recolector"); rec.setPerfil(pRec); em.persist(rec);
            UsuarioModel com = new UsuarioModel(); com.setRut("REV-B-COM"); com.setNombres("Comerciante"); com.setPerfil(pCom); em.persist(com);
            UsuarioModel pla = new UsuarioModel(); pla.setRut("REV-B-PLA"); pla.setNombres("Planta"); pla.setPerfil(pPla); em.persist(pla);
            EspecieModel esp = new EspecieModel(); esp.setNombre("Huiro negro"); em.persist(esp);
            HumedadEstadoModel hum = new HumedadEstadoModel(); hum.setNombre("HÚMEDO"); em.persist(hum);
            ComunaModel comuna = new ComunaModel(); comuna.setNombre("La Serena"); em.persist(comuna);
            CaletaModel caleta = new CaletaModel(); caleta.setNombre("Caleta Revision"); em.persist(caleta);
            ExtraccionTipoModel ext = new ExtraccionTipoModel(); ext.setNombre("Varado"); em.persist(ext);

            java.sql.Date hace9 = java.sql.Date.valueOf(LocalDate.now().minusDays(9));
            DeclaracionRecolectorModel d1 = decl(rec, com, esp, hum, comuna, caleta, ext, hace9, null);       // C, estado NULL
            DeclaracionRecolectorModel d2 = decl(rec, pla, esp, hum, comuna, caleta, ext, hace9, "NEGOCIACION"); // planta (venta directa)
            DeclaracionRecolectorModel d3 = decl(rec, com, esp, hum, comuna, caleta, ext, hace9, "NEGOCIACION"); // C, estado no nulo
            em.persist(d1); em.persist(d2); em.persist(d3);
            return new long[]{d1.getId(), d2.getId(), d3.getId()};
        });
        em.clear();

        List<LoteBodegaDTO> lotes = retencionCustodiaService.lotesEnBodega(FiltroRetencionBodega.builder().soloEnBodega(true).build());
        Set<Long> devueltos = lotes.stream().filter(l -> "RECOLECTOR".equals(l.getTipo())).map(LoteBodegaDTO::getId).collect(Collectors.toSet());
        System.out.println("REVISION_BODEGA d1(C, estado NULL)=" + ids[0] + " d2(planta)=" + ids[1] + " d3(C, NEGOCIACION)=" + ids[2]
                + " -> devueltos " + devueltos);
        assertEquals(Set.of(ids[0], ids[2]), devueltos, "La bodega de C debe contener d1 y d3, y no d2");
    }

    private DeclaracionRecolectorModel decl(UsuarioModel rec, UsuarioModel dest, EspecieModel esp, HumedadEstadoModel hum,
                                            ComunaModel comuna, CaletaModel caleta, ExtraccionTipoModel ext,
                                            java.sql.Date fecha, String estado) {
        DeclaracionRecolectorModel d = new DeclaracionRecolectorModel();
        d.setUsuario(rec); d.setUsuarioDestinatario(dest); d.setCodigoDestinatario("X"); d.setNombreDestinatario("X");
        d.setEspecie(esp); d.setHumedadEstado(hum); d.setComuna(comuna); d.setCaleta(caleta); d.setExtraccionTipo(ext);
        d.setFechaDeclaracion(fecha); d.setFechaExtraccion(fecha); d.setHora("08:00");
        d.setNombre("Recolector"); d.setCodigoSernapesca("RPA-REV"); d.setDesembarque(new BigDecimal("1000"));
        d.setEstado(estado);
        return d;
    }
}
