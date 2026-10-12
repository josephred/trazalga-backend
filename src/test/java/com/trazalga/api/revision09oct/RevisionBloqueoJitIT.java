package com.trazalga.api.revision09oct;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.trazalga.api.IntegracionBase;
import com.trazalga.api.models.*;
import com.trazalga.api.repositories.IDeclaracionMarcaRepository;
import com.trazalga.api.services.BloqueoCargaService;
import com.trazalga.api.services.ConfiguracionGeneralService;

import jakarta.persistence.EntityManager;

/**
 * Revisión 12-10 · TR.3 del plan, verificación en el punto de uso: «Con la tarea detenida, C intenta despachar
 * a las 120,5 h: la marca se crea en ese momento, el despacho se rechaza y la marca queda en la base».
 * Se simula el despacho con una transacción que llama a bloqueosPara, como hace validarCargasNoBloqueadas.
 */
class RevisionBloqueoJitIT extends IntegracionBase {

    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private BloqueoCargaService bloqueoCargaService;
    @Autowired private ConfiguracionGeneralService config;
    @Autowired private IDeclaracionMarcaRepository marcaRepository;

    @Test
    @DisplayName("TR.3: con bloqueo activo, una carga húmeda de 9 días queda retenida al intentar despacharla")
    void bloqueoEnElPuntoDeUso() {
        Long id = new TransactionTemplate(txManager).execute(s -> {
            PerfilModel pCom = PerfilModel.builder().nombre("COMERCIALIZADOR").build(); em.persist(pCom);
            UsuarioModel rec = new UsuarioModel(); rec.setRut("REV-J-REC"); rec.setNombres("Recolector"); em.persist(rec);
            UsuarioModel com = new UsuarioModel(); com.setRut("REV-J-COM"); com.setNombres("Comerciante"); com.setPerfil(pCom); em.persist(com);
            EspecieModel esp = new EspecieModel(); esp.setNombre("Huiro negro"); em.persist(esp);
            HumedadEstadoModel hum = new HumedadEstadoModel(); hum.setNombre("HÚMEDO"); em.persist(hum);
            ComunaModel comuna = new ComunaModel(); comuna.setNombre("La Serena"); em.persist(comuna);
            CaletaModel caleta = new CaletaModel(); caleta.setNombre("Caleta J"); em.persist(caleta);
            ExtraccionTipoModel ext = new ExtraccionTipoModel(); ext.setNombre("Varado"); em.persist(ext);
            DeclaracionRecolectorModel d = new DeclaracionRecolectorModel();
            d.setUsuario(rec); d.setUsuarioDestinatario(com); d.setCodigoDestinatario("X"); d.setNombreDestinatario("X");
            d.setEspecie(esp); d.setHumedadEstado(hum); d.setComuna(comuna); d.setCaleta(caleta); d.setExtraccionTipo(ext);
            java.sql.Date hace9 = java.sql.Date.valueOf(LocalDate.now().minusDays(9));
            d.setFechaDeclaracion(hace9); d.setFechaExtraccion(hace9); d.setHora("08:00");
            d.setNombre("Recolector"); d.setCodigoSernapesca("RPA-J"); d.setDesembarque(new BigDecimal("1000"));
            em.persist(d);
            return d.getId();
        });
        em.clear();

        config.updateConfig("retencion_bloqueo_activo", "true");
        Throwable alCommit = null;
        Map<String, ?> bloqueos = null;
        try {
            final Map<String, ?>[] holder = new Map[1];
            new TransactionTemplate(txManager).executeWithoutResult(s -> holder[0] = bloqueoCargaService.bloqueosPara("RECOLECTOR:" + id));
            bloqueos = holder[0];
        } catch (Throwable t) {
            alCommit = t;
        } finally {
            config.updateConfig("retencion_bloqueo_activo", "false");
        }
        long marcas = marcaRepository.findByDeclaracionTipoAndDeclaracionIdAndMarca("RECOLECTOR", id, "RETENCION_EXCEDIDA").size();
        System.out.println("REVISION_JIT bloqueos=" + bloqueos + " | excepcion al cerrar la transacción=" +
                (alCommit == null ? "ninguna" : alCommit.getClass().getSimpleName() + ": " + alCommit.getMessage()) +
                " | marcas RETENCION_EXCEDIDA en la base=" + marcas);
        assertNull(alCommit, "El despacho debe terminar en un rechazo controlado (409), no en un error de transacción");
        assertEquals(1, marcas, "La marca debe quedar en la base");
    }
}
