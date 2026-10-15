package com.trazalga.api.revision13oct;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.trazalga.api.IntegracionBase;
import com.trazalga.api.dto.ResultadoVariacionPesoDTO;
import com.trazalga.api.models.CaletaModel;
import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.models.DeclaracionComercializadorModel;
import com.trazalga.api.models.DeclaracionPlantaAbastecimientoModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.ExtraccionTipoModel;
import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.models.PerfilModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.services.ReportService;
import com.trazalga.api.services.indicadores.VariacionPesoService;

import jakarta.persistence.EntityManager;

/**
 * Revisión 13-10 · TV.4 (defecto 9): «Para una misma recepción, el Indicador 6, la ficha, el Indicador 8 y el
 * Perfilador muestran el mismo Δ%». Caso E de TV.6: 1.200 + 800 kg húmedos de dos comercializadores llegan en un
 * camión que la romana pesa en 1.970 kg; Δ −1,5 %. Cada despacho viene de un recolector.
 * getTrazabilidadLoteDetalle es la fuente del Indicador 8 y de PerfiladorRiesgoService.
 */
class RevisionIndicador8Test extends IntegracionBase {

    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private VariacionPesoService variacionPesoService;
    @Autowired private ReportService reportService;

    @Test
    @DisplayName("TV.4: en el caso E, el Indicador 8 muestra para cada lote el mismo Δ % de la recepción que el Indicador 6")
    void mismoDeltaQueElIndicador6() {
        Long plantaId = new TransactionTemplate(txManager).execute(s -> {
            PerfilModel pRec = PerfilModel.builder().nombre("RECOLECTOR").build(); em.persist(pRec);
            PerfilModel pCom = PerfilModel.builder().nombre("COMERCIALIZADOR").build(); em.persist(pCom);
            PerfilModel pPla = PerfilModel.builder().nombre("PLANTA").build(); em.persist(pPla);
            UsuarioModel rec1 = usuario("REV13-E-REC1", pRec), rec2 = usuario("REV13-E-REC2", pRec);
            UsuarioModel com1 = usuario("REV13-E-COM1", pCom), com2 = usuario("REV13-E-COM2", pCom);
            UsuarioModel planta = usuario("REV13-E-PLA", pPla);
            EspecieModel esp = EspecieModel.builder().nombre("Huiro negro R13E").build(); em.persist(esp);
            HumedadEstadoModel hum = HumedadEstadoModel.builder().nombre("HÚMEDO").build(); em.persist(hum);
            ComunaModel comuna = ComunaModel.builder().nombre("Caldera R13E").build(); em.persist(comuna);
            CaletaModel caleta = CaletaModel.builder().nombre("Caleta R13E").comuna(comuna).build(); em.persist(caleta);
            ExtraccionTipoModel ext = ExtraccionTipoModel.builder().nombre("Varado R13E").build(); em.persist(ext);

            DeclaracionPlantaAbastecimientoModel p = new DeclaracionPlantaAbastecimientoModel();
            p.setUsuario(planta); p.setFechaIngresoPlanta(java.sql.Date.valueOf("2026-10-10")); p.setHora("12:00");
            p.setEspecie(esp); p.setHumedadEstado(hum); p.setCantidad(new BigDecimal("1970"));
            p.setPesoRomanaKg(new BigDecimal("1970")); p.setFolioDeclaracionAPla("REV13-APLA-E");
            em.persist(p);

            DeclaracionComercializadorModel dc1 = despacho(com1, planta, esp, hum, "1200", p.getId(), "REV13-AC-E1");
            DeclaracionComercializadorModel dc2 = despacho(com2, planta, esp, hum, "800", p.getId(), "REV13-AC-E2");
            origen(rec1, com1, esp, hum, comuna, caleta, ext, "1200", dc1.getId(), "REV13-RO-E1");
            origen(rec2, com2, esp, hum, comuna, caleta, ext, "800", dc2.getId(), "REV13-RO-E2");
            return p.getId();
        });
        em.clear();

        ResultadoVariacionPesoDTO ind6 = variacionPesoService.evaluarRecepcionPorId(plantaId);
        assertEquals(-1.5, ind6.getDeltaPct(), 0.05, "Precondición: Indicador 6, caso E del plan (1.970 frente a 2.000 kg)");

        List<Map<String, Object>> filas = reportService.getTrazabilidadLoteDetalle(
                java.sql.Date.valueOf("2026-10-01"), java.sql.Date.valueOf("2026-10-31"), null);
        for (String folio : List.of("REV13-RO-E1", "REV13-RO-E2")) {
            Map<String, Object> fila = filas.stream().filter(m -> folio.equals(m.get("folioOrigen"))).findFirst()
                    .orElseThrow(() -> new AssertionError("El lote " + folio + " debe aparecer en el Indicador 8"));
            Number delta = (Number) fila.get("deltaPct");
            System.out.println("REVISION13_IND8 lote " + folio + ": kgComercializador=" + fila.get("kgComercializador")
                    + " kgPlanta=" + fila.get("kgPlanta") + " tramo=" + fila.get("tramoCerrado") + " Δ%=" + delta
                    + " | Indicador 6 Δ%=" + ind6.getDeltaPct());
            assertNotNull(delta, "El lote tiene tramo cerrado (la planta recibió)");
            assertEquals(ind6.getDeltaPct(), delta.doubleValue(), 0.05,
                    "Lote " + folio + ": el Indicador 8 debe mostrar el Δ % de la recepción del Indicador 6");
        }
    }

    private UsuarioModel usuario(String rut, PerfilModel perfil) {
        UsuarioModel u = UsuarioModel.builder().rut(rut).nombres("Usuario").apellidop(rut).perfil(perfil).build();
        em.persist(u);
        return u;
    }

    private DeclaracionComercializadorModel despacho(UsuarioModel com, UsuarioModel planta, EspecieModel esp,
                                                     HumedadEstadoModel hum, String kg, Long plantaId, String folio) {
        DeclaracionComercializadorModel d = new DeclaracionComercializadorModel();
        d.setUsuario(com); d.setFechaDeclaracion(java.sql.Date.valueOf("2026-10-09"));
        d.setFechaTraslado(java.sql.Date.valueOf("2026-10-09")); d.setHora("12:00");
        d.setEspecie(esp); d.setHumedadEstado(hum); d.setCantidad(new BigDecimal(kg));
        d.setCodigoDestinatario("PLA"); d.setNombreDestinatario("Planta"); d.setUsuarioDestinatario(planta);
        d.setConsumidaPorTipo("PLANTA_ABASTECIMIENTO"); d.setDeclaracionDestinatario(plantaId);
        d.setFolioDesembarqueAc(folio);
        em.persist(d);
        return d;
    }

    private void origen(UsuarioModel rec, UsuarioModel com, EspecieModel esp, HumedadEstadoModel hum, ComunaModel comuna,
                        CaletaModel caleta, ExtraccionTipoModel ext, String kg, Long despachoId, String folio) {
        java.sql.Date fecha = java.sql.Date.valueOf("2026-10-08");
        em.persist(DeclaracionRecolectorModel.builder()
                .usuario(rec).especie(esp).extraccionTipo(ext).humedadEstado(hum).comuna(comuna).caleta(caleta)
                .fechaDeclaracion(fecha).fechaExtraccion(fecha).hora("09:00")
                .nombre("Recolector").codigoSernapesca("RPA-R13E").folioDesembarqueRo(folio)
                .codigoDestinatario("X").nombreDestinatario("Comercializador").usuarioDestinatario(com)
                .desembarque(new BigDecimal(kg)).captura(new BigDecimal(kg))
                .consumidaPorTipo("COMERCIALIZADOR").declaracionDestinatario(despachoId)
                .build());
    }
}
