package com.trazalga.api.revision13oct;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.trazalga.api.IntegracionBase;
import com.trazalga.api.models.CaletaModel;
import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.models.DeclaracionMarcaModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.ExtraccionTipoModel;
import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.models.PerfilModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IAvisoEnviadoRepository;
import com.trazalga.api.repositories.IDeclaracionMarcaRepository;
import com.trazalga.api.services.BloqueoCargaService;
import com.trazalga.api.services.ConfiguracionGeneralService;
import com.trazalga.api.services.retencion.RetencionCustodiaService;

import jakarta.persistence.EntityManager;

/**
 * Revisión 13-10 · TR.3 del plan, criterio y preaviso del bloqueo por retención (con retencion_bloqueo_activo = true).
 * - La marca guarda el plazo que bloqueó la carga: «plazo de bloqueo 120 h (retencion_bloqueo_humedo_horas)».
 * - El preaviso es un aviso al comercializador al llegar a retencion_bloqueo_preaviso_pct (80 % de 120 h = 96 h),
 *   una vez por carga, con clave RETENCION_PREAVISO:<TIPO>:<ID> en aviso_enviado. No es un hallazgo.
 */
class RevisionRetencionCriterioTest extends IntegracionBase {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private BloqueoCargaService bloqueoCargaService;
    @Autowired private RetencionCustodiaService retencionCustodiaService;
    @Autowired private ConfiguracionGeneralService config;
    @Autowired private IDeclaracionMarcaRepository marcaRepository;
    @Autowired private IAvisoEnviadoRepository avisoRepository;

    @Test
    @DisplayName("TR.3: la marca RETENCION_EXCEDIDA registra como umbral el plazo de bloqueo (120 h), no el plazo de la Res. 3602 (24 h)")
    void marcaGuardaElPlazoDeBloqueo() {
        Long id = crearLoteHumedoEnBodega(LocalDateTime.now().minusDays(9));
        config.updateConfig("retencion_bloqueo_activo", "true");
        try {
            new TransactionTemplate(txManager).executeWithoutResult(s -> bloqueoCargaService.bloqueosPara("RECOLECTOR:" + id));
        } finally {
            config.updateConfig("retencion_bloqueo_activo", "false");
        }
        List<DeclaracionMarcaModel> marcas = marcaRepository.findByDeclaracionTipoAndDeclaracionIdAndMarca("RECOLECTOR", id, "RETENCION_EXCEDIDA");
        assertEquals(1, marcas.size(), "Precondición: la marca se crea en el punto de uso");
        DeclaracionMarcaModel m = marcas.get(0);
        System.out.println("REVISION13_RETENCION criterio: parametro=" + m.getCriterioParametro() + " umbral=" + m.getCriterioUmbral()
                + " valor=" + m.getCriterioValor() + " unidad=" + m.getCriterioUnidad() + " | detalle=" + m.getDetalle());
        assertEquals(120.0, Double.parseDouble(m.getCriterioUmbral()), 0.01,
                "El umbral registrado debe ser retencion_bloqueo_humedo_horas (120 h), que es el plazo que bloqueó la carga");
    }

    @Test
    @DisplayName("TR.3: con el bloqueo activo, un lote húmedo con 20 h en bodega no genera hallazgos de preaviso")
    void sinHallazgoDePreavisoLejosDelBloqueo() {
        Long id = crearLoteHumedoEnBodega(LocalDateTime.now().minusHours(20));
        barrerConBloqueoActivo();
        List<DeclaracionMarcaModel> preavisos = marcaRepository.findByDeclaracionTipoAndDeclaracionIdAndMarca("RECOLECTOR", id, "RETENCION_PREAVISO");
        System.out.println("REVISION13_RETENCION lote de 20 h -> hallazgos RETENCION_PREAVISO=" + preavisos.size()
                + preavisos.stream().map(p -> " [" + p.getClaveIdempotencia() + ", umbral " + p.getCriterioUmbral() + "]").toList());
        assertTrue(preavisos.isEmpty(),
                "A 20 h de un bloqueo de 120 h no corresponde preaviso, y el preaviso no es un hallazgo de fiscalización");
    }

    @Test
    @DisplayName("TR.3: a las 100 h (sobre el 80 % de 120 h) se avisa una sola vez al comercializador en aviso_enviado, sin hallazgo")
    void preavisoAlComercializadorEnAvisoEnviado() {
        Long id = crearLoteHumedoEnBodega(LocalDateTime.now().minusHours(100));
        barrerConBloqueoActivo();
        barrerConBloqueoActivo();
        String clave = "RETENCION_PREAVISO:RECOLECTOR:" + id;
        boolean aviso = avisoRepository.existsById(clave);
        List<DeclaracionMarcaModel> hallazgos = marcaRepository.findByDeclaracionTipoAndDeclaracionIdAndMarca("RECOLECTOR", id, "RETENCION_PREAVISO");
        System.out.println("REVISION13_RETENCION lote de 100 h -> aviso_enviado[" + clave + "]=" + aviso
                + " | hallazgos RETENCION_PREAVISO=" + hallazgos.size()
                + hallazgos.stream().map(p -> " [regla " + p.getClaveIdempotencia() + ", umbral " + p.getCriterioUmbral() + "]").toList());
        assertTrue(aviso, "El preaviso debe quedar en aviso_enviado con la clave " + clave);
        assertTrue(hallazgos.isEmpty(), "El preaviso es un aviso al comercializador, no un hallazgo");
    }

    private void barrerConBloqueoActivo() {
        config.updateConfig("retencion_bloqueo_activo", "true");
        try {
            retencionCustodiaService.barrer(LocalDateTime.now());
        } finally {
            config.updateConfig("retencion_bloqueo_activo", "false");
        }
    }

    private Long crearLoteHumedoEnBodega(LocalDateTime entrada) {
        Long id = new TransactionTemplate(txManager).execute(s -> {
            int n = SEQ.incrementAndGet();
            PerfilModel pCom = perfil("COMERCIALIZADOR");
            UsuarioModel rec = new UsuarioModel(); rec.setRut("REV13-R-REC-" + n); rec.setNombres("Recolector"); em.persist(rec);
            UsuarioModel com = new UsuarioModel(); com.setRut("REV13-R-COM-" + n); com.setNombres("Comerciante"); com.setPerfil(pCom); em.persist(com);
            EspecieModel esp = new EspecieModel(); esp.setNombre("Huiro negro R13R-" + n); em.persist(esp);
            HumedadEstadoModel hum = humedad("HÚMEDO");
            ComunaModel comuna = new ComunaModel(); comuna.setNombre("La Serena R13R-" + n); em.persist(comuna);
            CaletaModel caleta = new CaletaModel(); caleta.setNombre("Caleta R13R-" + n); em.persist(caleta);
            ExtraccionTipoModel ext = new ExtraccionTipoModel(); ext.setNombre("Varado R13R-" + n); em.persist(ext);

            java.sql.Date fecha = java.sql.Date.valueOf(entrada.toLocalDate());
            DeclaracionRecolectorModel d = new DeclaracionRecolectorModel();
            d.setUsuario(rec); d.setUsuarioDestinatario(com); d.setCodigoDestinatario("X"); d.setNombreDestinatario("X");
            d.setEspecie(esp); d.setHumedadEstado(hum); d.setComuna(comuna); d.setCaleta(caleta); d.setExtraccionTipo(ext);
            d.setFechaDeclaracion(fecha); d.setFechaExtraccion(fecha); d.setHora(entrada.format(DateTimeFormatter.ofPattern("HH:mm")));
            d.setNombre("Recolector"); d.setCodigoSernapesca("RPA-R13-" + n); d.setDesembarque(new BigDecimal("1000"));
            d.setFolioDesembarqueRo("REV13-RO-RET-" + n);
            em.persist(d);
            return d.getId();
        });
        em.clear();
        return id;
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
}
