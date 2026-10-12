package com.trazalga.api.revision09oct;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.trazalga.api.IntegracionBase;
import com.trazalga.api.dto.ResultadoVariacionPesoDTO;
import com.trazalga.api.dto.UsuarioBusquedaDTO;
import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.models.DeclaracionComercializadorModel;
import com.trazalga.api.models.DeclaracionMarcaModel;
import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.models.PerfilModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.ICuotaExtraccionRepository;
import com.trazalga.api.repositories.IDeclaracionMarcaRepository;
import com.trazalga.api.services.UsuarioService;
import com.trazalga.api.services.indicadores.VariacionPesoService;
import com.trazalga.api.services.retencion.RetencionCustodiaService;

import jakarta.persistence.EntityManager;

/**
 * Revisión 12-10 · Pruebas de integración en el perfil «it» (H2 MODE=MySQL, open-in-view=false,
 * sin enable_lazy_load_no_trans), que es la configuración que el plan exige para validar
 * transacciones y carga perezosa como en producción.
 */
class RevisionIntegracionTest extends IntegracionBase {

    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private ICuotaExtraccionRepository cuotaRepository;
    @Autowired private IDeclaracionMarcaRepository marcaRepository;
    @Autowired private VariacionPesoService variacionPesoService;
    @Autowired private RetencionCustodiaService retencionCustodiaService;
    @Autowired private UsuarioService usuarioService;

    @Test
    @DisplayName("Precondición: Hibernate devuelve java.sql.Date en los campos @Temporal(DATE) al leer desde la base")
    void hibernateDevuelveSqlDate() {
        CuotaExtraccionModel c = CuotaExtraccionModel.builder()
                .perfil("RECOLECTOR").ambito("AREA_LIBRE").nivelAgregacion("COMUNA").periodo("MENSUAL")
                .fechaInicio(java.sql.Date.valueOf("2026-10-01")).fechaFin(java.sql.Date.valueOf("2026-10-31"))
                .limiteKg(1000.0).metrica("DESEMBARQUE").resolucion("REV-12OCT").activo(true).estado("ABIERTA")
                .build();
        Long id = cuotaRepository.saveAndFlush(c).getId();
        entityManager.clear();
        CuotaExtraccionModel r = cuotaRepository.findById(id).orElseThrow();
        System.out.println("REVISION_IT clase de fechaFin al recargar: " + r.getFechaFin().getClass().getName());
        assertTrue(r.getFechaFin() instanceof java.sql.Date);
        cuotaRepository.deleteById(id);
    }

    @Test
    @DisplayName("TV.1 con datos reales: una recepción leída desde la base se evalúa sin error (caso A del plan)")
    void variacionPesoConRecepcionPersistida() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        Long plantaId = tx.execute(s -> {
            PerfilModel perfil = PerfilModel.builder().nombre("PLANTA").build();
            entityManager.persist(perfil);
            UsuarioModel planta = new UsuarioModel();
            planta.setRut("REV-PLANTA-1"); planta.setNombres("Planta"); planta.setApellidop("Revision"); planta.setPerfil(perfil);
            entityManager.persist(planta);
            UsuarioModel com = new UsuarioModel();
            com.setRut("REV-COM-1"); com.setNombres("Comercial"); com.setApellidop("Revision"); com.setPerfil(perfil);
            entityManager.persist(com);
            EspecieModel esp = new EspecieModel(); esp.setNombre("Huiro negro"); entityManager.persist(esp);
            HumedadEstadoModel hum = new HumedadEstadoModel(); hum.setNombre("HÚMEDO"); entityManager.persist(hum);

            DeclaracionPlantaAbastecimientoModel p = new DeclaracionPlantaAbastecimientoModel();
            p.setUsuario(planta); p.setFechaIngresoPlanta(java.sql.Date.valueOf("2026-10-10")); p.setHora("22:00");
            p.setEspecie(esp); p.setHumedadEstado(hum); p.setCantidad(new BigDecimal("2330"));
            p.setPesoRomanaKg(new BigDecimal("2330")); p.setFolioDeclaracionAPla("REV-APLA-1");
            entityManager.persist(p);

            DeclaracionComercializadorModel d = new DeclaracionComercializadorModel();
            d.setUsuario(com); d.setFechaDeclaracion(java.sql.Date.valueOf("2026-10-10"));
            d.setFechaTraslado(java.sql.Date.valueOf("2026-10-10")); d.setHora("08:00");
            d.setEspecie(esp); d.setHumedadEstado(hum); d.setCantidad(new BigDecimal("2400"));
            d.setCodigoDestinatario("REV"); d.setNombreDestinatario("Planta Revision"); d.setUsuarioDestinatario(planta);
            d.setConsumidaPorTipo("PLANTA_ABASTECIMIENTO"); d.setDeclaracionDestinatario(p.getId());
            entityManager.persist(d);
            return p.getId();
        });
        entityManager.clear();

        ResultadoVariacionPesoDTO res = assertDoesNotThrow(() -> variacionPesoService.evaluarRecepcionPorId(plantaId),
                "Evaluar una recepción real no debe lanzar excepción");
        assertEquals(-2.9, res.getDeltaPct(), 0.05);
    }

    @Test
    @DisplayName("TR.3: la marca RETENCION_EXCEDIDA se persiste con el texto que genera PoliticaRetencion")
    void marcaRetencionExcedidaSePersiste() {
        String motivo = "Retención excedida: 126 h en bodega superan el límite de bloqueo de 120 h para recurso HÚMEDO";
        Throwable error = null;
        try {
            retencionCustodiaService.registrarMarcaRetencionExcedida("RECOLECTOR", 990001L, motivo, null, null);
        } catch (Throwable t) {
            error = t;
            Throwable root = t; while (root.getCause() != null) root = root.getCause();
            System.out.println("REVISION_IT RETENCION_EXCEDIDA -> " + t.getClass().getSimpleName() + " | causa raíz: " + root.getMessage());
        }
        List<DeclaracionMarcaModel> marcas = marcaRepository.findByDeclaracionTipoAndDeclaracionId("RECOLECTOR", 990001L);
        assertNull(error, "Registrar la marca de retención no debe fallar");
        assertEquals(1, marcas.size());
    }

    @Test
    @DisplayName("TM.1: GET /api/usuarios/buscar (UsuarioService.buscarUsuarios) responde fuera de una transacción")
    void buscarUsuariosFueraDeTransaccion() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(s -> {
            PerfilModel perfil = PerfilModel.builder().nombre("RECOLECTOR").build();
            entityManager.persist(perfil);
            UsuarioModel u = new UsuarioModel();
            u.setRut("REV-BUSCAR-1"); u.setNombres("Persona"); u.setApellidop("Buscada"); u.setPerfil(perfil);
            entityManager.persist(u);
        });
        entityManager.clear();

        List<UsuarioBusquedaDTO> r = assertDoesNotThrow(() -> usuarioService.buscarUsuarios("buscada", null, 20),
                "La búsqueda de personas del formulario de cuotas no debe fallar");
        assertEquals(1, r.size());
    }
}
