package com.trazalga.api.services.retencion;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.trazalga.api.IntegracionBase;
import com.trazalga.api.dto.MotivoBloqueoDTO;
import com.trazalga.api.models.*;
import com.trazalga.api.repositories.IDeclaracionMarcaRepository;
import com.trazalga.api.services.BloqueoCargaService;
import com.trazalga.api.services.ConfiguracionGeneralService;

import jakarta.persistence.EntityManager;

/**
 * TX.5 / TR.3: Pruebas de integración para el control de retención en custodia:
 * 1. Con el interruptor apagado no se retiene ni bloquea nada.
 * 2. Carga formalmente LIBERADA por fiscalizador no se vuelve a retener ni bloquear.
 */
class RetencionCustodiaIT extends IntegracionBase {

    @Autowired
    private EntityManager em;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private RetencionCustodiaService retencionCustodiaService;

    @Autowired
    private BloqueoCargaService bloqueoCargaService;

    @Autowired
    private ConfiguracionGeneralService config;

    @Autowired
    private IDeclaracionMarcaRepository marcaRepository;

    private Long crearDeclaracionHumedaHaceDias(int dias) {
        return new TransactionTemplate(txManager).execute(s -> {
            PerfilModel pCom = PerfilModel.builder().nombre("COMERCIALIZADOR").build();
            em.persist(pCom);

            UsuarioModel rec = new UsuarioModel();
            rec.setRut("TX5-REC-" + System.nanoTime());
            rec.setNombres("Recolector TX5");
            em.persist(rec);

            UsuarioModel com = new UsuarioModel();
            com.setRut("TX5-COM-" + System.nanoTime());
            com.setNombres("Comerciante TX5");
            com.setPerfil(pCom);
            em.persist(com);

            EspecieModel esp = new EspecieModel();
            esp.setNombre("Huiro negro");
            em.persist(esp);

            HumedadEstadoModel hum = new HumedadEstadoModel();
            hum.setNombre("HÚMEDO");
            em.persist(hum);

            ComunaModel comuna = new ComunaModel();
            comuna.setNombre("Coquimbo");
            em.persist(comuna);

            CaletaModel caleta = new CaletaModel();
            caleta.setNombre("Caleta TX5");
            em.persist(caleta);

            ExtraccionTipoModel ext = new ExtraccionTipoModel();
            ext.setNombre("Varado");
            em.persist(ext);

            LocalDateTime haceDias = LocalDateTime.now().minusDays(dias);
            DeclaracionRecolectorModel d = new DeclaracionRecolectorModel();
            d.setUsuario(rec);
            d.setUsuarioDestinatario(com);
            d.setCodigoDestinatario("TX5-DEST");
            d.setNombreDestinatario("Destinatario TX5");
            d.setEspecie(esp);
            d.setHumedadEstado(hum);
            d.setComuna(comuna);
            d.setCaleta(caleta);
            d.setExtraccionTipo(ext);
            d.setFechaDeclaracion(java.sql.Date.valueOf(haceDias.toLocalDate()));
            d.setFechaExtraccion(java.sql.Date.valueOf(haceDias.toLocalDate()));
            d.setHora(haceDias.format(DateTimeFormatter.ofPattern("HH:mm")));
            d.setNombre("Recolector TX5");
            d.setCodigoSernapesca("RPA-TX5");
            d.setDesembarque(new BigDecimal("1000.00"));
            d.setCaptura(new BigDecimal("1000.00"));
            d.setEstado("NEGOCIACION");
            em.persist(d);
            return d.getId();
        });
    }

    @Test
    @DisplayName("TX.5 / TR.3: Con interruptor de retención apagado, una carga de 9 días no se retiene ni bloquea")
    void conInterruptorApagadoNoSeRetieneNada() {
        Long id = crearDeclaracionHumedaHaceDias(9);
        em.clear();

        // Asegurar que el bloqueo esté apagado
        config.updateConfig("retencion_bloqueo_activo", "false");

        // Ejecutar barrido y verificación en el punto de uso
        retencionCustodiaService.barrer(LocalDateTime.now());
        Map<String, MotivoBloqueoDTO> bloqueos = bloqueoCargaService.bloqueosPara("RECOLECTOR:" + id);
        boolean estaBloqueada = bloqueoCargaService.estaBloqueada("RECOLECTOR", id);

        // Validaciones: no se bloquea ni se crea marca de retención
        assertFalse(estaBloqueada, "Con el interruptor apagado la carga no debe figurar bloqueada");
        assertTrue(bloqueos.isEmpty(), "Con el interruptor apagado no debe haber motivos de bloqueo");

        List<DeclaracionMarcaModel> marcas = marcaRepository.findByDeclaracionTipoAndDeclaracionIdAndMarca(
                "RECOLECTOR", id, "RETENCION_EXCEDIDA");
        assertTrue(marcas.isEmpty(), "Con el interruptor apagado no debe crearse ninguna marca RETENCION_EXCEDIDA");
    }

    @Test
    @DisplayName("TX.5 / TR.3: Una carga formalmente LIBERADA por fiscalizador no se vuelve a retener ni bloquear")
    void cargaLiberadaNoSeVuelveARetener() {
        Long id = crearDeclaracionHumedaHaceDias(9);
        em.clear();

        // Registrar previamente la marca con resolución LIBERADA por fiscalizador
        new TransactionTemplate(txManager).executeWithoutResult(s -> {
            DeclaracionMarcaModel marcaLiberada = DeclaracionMarcaModel.builder()
                    .declaracionTipo("RECOLECTOR")
                    .declaracionId(id)
                    .marca("RETENCION_EXCEDIDA")
                    .reglaId(3L)
                    .criterioParametro("retencion_bloqueo_humedo_horas")
                    .criterioUmbral("120")
                    .criterioValor("216.0")
                    .criterioUnidad("h")
                    .detalle("Carga excedió retención pero fue inspeccionada y liberada")
                    .resuelta(true)
                    .estadoGestion("RESUELTO")
                    .resolucionTipo("LIBERADA")
                    .observacionResolucion("Inspección en terreno conforme. Carga apta para consumo.")
                    .fechaResolucion(new java.util.Date())
                    .resueltaPorUsuarioId(1L)
                    .origen("BARRIDO")
                    .claveIdempotencia("RECOLECTOR:" + id + ":RETENCION_EXCEDIDA:LIBERADA")
                    .build();
            em.persist(marcaLiberada);
        });
        em.clear();

        config.updateConfig("retencion_bloqueo_activo", "true");
        config.updateConfig("bloqueo_carga_activo", "true");
        try {
            // Intentar re-evaluar y re-bloquear en punto de uso y en barrido
            retencionCustodiaService.evaluarYBloquearJustInTime("RECOLECTOR", id);
            retencionCustodiaService.barrer(LocalDateTime.now());
            Map<String, MotivoBloqueoDTO> bloqueos = bloqueoCargaService.bloqueosPara("RECOLECTOR:" + id);
            boolean estaBloqueada = bloqueoCargaService.estaBloqueada("RECOLECTOR", id);

            // Validar que la resolución del fiscalizador se respeta estrictamente:
            assertFalse(estaBloqueada, "Una carga con resolución LIBERADA no debe bloquearse");
            assertTrue(bloqueos.isEmpty(), "No debe haber bloqueos activos para una carga liberada");

            List<DeclaracionMarcaModel> marcas = marcaRepository.findByDeclaracionTipoAndDeclaracionIdAndMarca(
                    "RECOLECTOR", id, "RETENCION_EXCEDIDA");
            assertEquals(1, marcas.size(), "No debe insertarse una segunda marca de retención si ya estaba LIBERADA");
            assertTrue(marcas.get(0).getResuelta(), "La marca existente debe permanecer resuelta");
            assertEquals("LIBERADA", marcas.get(0).getResolucionTipo(), "La marca debe conservar la resolución LIBERADA");
        } finally {
            config.updateConfig("retencion_bloqueo_activo", "false");
        }
    }
}
