package com.trazalga.api.revision13oct;

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
import com.trazalga.api.dto.ContextoDeclaracion;
import com.trazalga.api.dto.ResultadoValidacion;
import com.trazalga.api.models.CaletaModel;
import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.models.DeclaracionMarcaModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.ExtraccionTipoModel;
import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.models.PerfilModel;
import com.trazalga.api.models.RegionModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IDeclaracionMarcaRepository;
import com.trazalga.api.services.AlertaTriggerService;
import com.trazalga.api.services.ConfiguracionGeneralService;
import com.trazalga.api.services.ValidacionDeclaracionService;

import jakarta.persistence.EntityManager;

/**
 * Revisión 13-10 · TD.1 y TD.2 (defecto 7): «una declaración puede quedar marcada por los dos criterios».
 * Se recorre el camino real del alta: ValidacionDeclaracionService.validar arma las marcas y
 * AlertaTriggerService.procesarMarcas las guarda, como hace DeclaracionRecolectorService.
 * Grupo de comparación: 12 declaraciones de 700 y 900 kg en la misma comuna, especie y humedad (media 800 kg).
 */
class RevisionAtipicoDosCriteriosTest extends IntegracionBase {

    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private ValidacionDeclaracionService validacionDeclaracionService;
    @Autowired private AlertaTriggerService alertaTriggerService;
    @Autowired private ConfiguracionGeneralService config;
    @Autowired private IDeclaracionMarcaRepository marcaRepository;

    @Test
    @DisplayName("TD.1/TD.2: una declaración atípica por umbral (6.000 > 5.000 kg) y por desviación estadística queda con las dos marcas")
    void dosCriteriosDejanDosMarcas() {
        long[] f = new TransactionTemplate(txManager).execute(s -> {
            RegionModel region = RegionModel.builder().nombre("Atacama R13A").codigo("03").build(); em.persist(region);
            ComunaModel comuna = ComunaModel.builder().nombre("Caldera R13A").region(region).build(); em.persist(comuna);
            CaletaModel caleta = CaletaModel.builder().nombre("Caleta R13A").region(region).comuna(comuna).build(); em.persist(caleta);
            EspecieModel esp = EspecieModel.builder().nombre("Huiro negro R13A").build(); em.persist(esp);
            ExtraccionTipoModel ext = ExtraccionTipoModel.builder().nombre("Varado R13A").build(); em.persist(ext);
            HumedadEstadoModel hum = HumedadEstadoModel.builder().nombre("Húmedo R13A").build(); em.persist(hum);
            PerfilModel pRec = PerfilModel.builder().nombre("RECOLECTOR").build(); em.persist(pRec);
            PerfilModel pCom = PerfilModel.builder().nombre("COMERCIALIZADOR").build(); em.persist(pCom);
            UsuarioModel rec = UsuarioModel.builder().rut("REV13-A-REC").nombres("Recolector").perfil(pRec).comuna(comuna).build(); em.persist(rec);
            UsuarioModel com = UsuarioModel.builder().rut("REV13-A-COM").nombres("Comerciante").perfil(pCom).comuna(comuna).build(); em.persist(com);
            for (int i = 1; i <= 12; i++) {
                java.sql.Date fecha = java.sql.Date.valueOf(LocalDate.now().minusDays(i));
                BigDecimal kg = new BigDecimal(i % 2 == 0 ? "700" : "900");
                em.persist(DeclaracionRecolectorModel.builder()
                        .usuario(rec).especie(esp).extraccionTipo(ext).humedadEstado(hum).comuna(comuna).caleta(caleta)
                        .fechaDeclaracion(fecha).fechaExtraccion(fecha).hora("09:00")
                        .nombre("Recolector").codigoSernapesca("RPA-R13A").folioDesembarqueRo("REV13-RO-A-" + i)
                        .codigoDestinatario("X").nombreDestinatario("Comercializador").usuarioDestinatario(com)
                        .desembarque(kg).captura(kg).build());
            }
            return new long[]{rec.getId(), esp.getId(), ext.getId(), hum.getId(), comuna.getId(), caleta.getId()};
        });
        em.clear();

        java.sql.Date hoy = java.sql.Date.valueOf(LocalDate.now());
        ContextoDeclaracion ctx = ContextoDeclaracion.builder()
                .tipoDeclaracion("RECOLECTOR").usuarioId(f[0]).especieId(f[1]).extraccionTipoId(f[2]).humedadEstadoId(f[3])
                .comunaInscripcionId(f[4]).comunaDesembarqueId(f[4]).caletaId(f[5])
                .fechaExtraccion(hoy).fechaDeclaracion(hoy).desembarqueKg(new BigDecimal("6000")).esEdicion(false)
                .build();

        config.updateConfig("desembarque_atipico_estadistico_activo", "true");
        ResultadoValidacion res;
        try {
            res = validacionDeclaracionService.validar(ctx);
        } finally {
            config.updateConfig("desembarque_atipico_estadistico_activo", "false");
        }
        List<ResultadoValidacion.MarcaItem> atipicas = res.getMarcas().stream()
                .filter(m -> "DESEMBARQUE_ATIPICO".equals(m.getMarca())).toList();
        System.out.println("REVISION13_ATIPICO marcas armadas por validar: " + atipicas.stream()
                .map(m -> m.getCriterio().parametro() + " (regla " + m.getReglaId() + ")").toList());
        assertEquals(2, atipicas.size(), "Precondición: el pipeline detecta el umbral y la desviación estadística");

        long declaracionId = 990_013L;
        alertaTriggerService.procesarMarcas("RECOLECTOR", declaracionId, f[0], res.getMarcas());
        List<DeclaracionMarcaModel> guardadas = marcaRepository
                .findByDeclaracionTipoAndDeclaracionIdAndMarca("RECOLECTOR", declaracionId, "DESEMBARQUE_ATIPICO");
        System.out.println("REVISION13_ATIPICO marcas guardadas: " + guardadas.stream()
                .map(m -> m.getClaveIdempotencia() + " -> " + m.getCriterioParametro()).toList());
        assertEquals(2, guardadas.size(),
                "Deben quedar las dos marcas; con la misma clave idempotente la segunda se descarta y el filtro «Estadístico» no la ve");
    }
}
