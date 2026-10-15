package com.trazalga.api.revision13oct;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.trazalga.api.IntegracionBase;
import com.trazalga.api.models.AvisoEnviadoModel;
import com.trazalga.api.models.CaletaModel;
import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.ExtraccionTipoModel;
import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.models.PerfilModel;
import com.trazalga.api.models.RegionModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IAvisoEnviadoRepository;
import com.trazalga.api.services.CuotaExtraccionService;

import jakarta.persistence.EntityManager;

/**
 * Revisión 13-10 · TC.6 «Umbral en tiempo real» y defecto 16.
 * Cuota comunal de 1.000 kg con 900 kg consumidos; una declaración de 20 kg la lleva al 92 % (umbral: 90 %).
 * El plan: el aviso sale una vez por cuota y tipo, con clave CUOTA:<id>:UMBRAL:<personaId o 0>, la misma que usa
 * la revisión diaria (RevisionCuotasService), para no duplicar. «No escribir dentro de una transacción readOnly».
 */
class RevisionAvisoUmbralCuotaTest extends IntegracionBase {

    private static final java.sql.Date FECHA = java.sql.Date.valueOf("2026-10-10");
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private CuotaExtraccionService cuotaService;
    @Autowired private IAvisoEnviadoRepository avisoRepository;

    @Test
    @DisplayName("TC.6: en el alta de una declaración, el aviso de umbral queda registrado en aviso_enviado")
    void avisoDeUmbralAlDeclararQuedaRegistrado() {
        long[] f = crearCuotaAl90();   // {cuota, usuario1, usuario2, especie, comuna}
        cuotaService.invalidarCacheConsumo();
        // La misma sobrecarga (10 argumentos) que usa ValidacionDeclaracionService.validar en el alta.
        CuotaExtraccionService.EvaluacionCuotaResult r = cuotaService.evaluarCuotaDeclaracion(
                "RECOLECTOR", f[1], null, f[3], null, f[4], FECHA, FECHA, new BigDecimal("20"), new BigDecimal("20"));
        List<String> claves = clavesUmbral(f[0]);
        System.out.println("REVISION13_AVISO alta: avisos de umbral registrados para la cuota " + f[0] + " = " + claves
                + " | resultado: " + r.getMensaje());
        assertFalse(claves.isEmpty(),
                "El aviso debe quedar registrado; si no, se repite con cada declaración siguiente y otra vez en la revisión diaria");
    }

    @Test
    @DisplayName("TC.6: en una cuota comunal, dos declarantes que cruzan el umbral dejan un solo aviso, CUOTA:<id>:UMBRAL:0")
    void unSoloAvisoPorCuotaComunal() {
        long[] f = crearCuotaAl90();
        cuotaService.invalidarCacheConsumo();
        // Sobrecarga de 12 argumentos (corre en transacción de escritura), para aislar la clave del caso anterior.
        cuotaService.evaluarCuotaDeclaracion("RECOLECTOR", f[1], null, f[3], null, f[4], FECHA, FECHA,
                new BigDecimal("20"), new BigDecimal("20"), false, null);
        cuotaService.evaluarCuotaDeclaracion("RECOLECTOR", f[2], null, f[3], null, f[4], FECHA, FECHA,
                new BigDecimal("20"), new BigDecimal("20"), false, null);
        List<String> claves = clavesUmbral(f[0]);
        System.out.println("REVISION13_AVISO comunal: claves de umbral = " + claves);
        assertEquals(List.of("CUOTA:" + f[0] + ":UMBRAL:0"), claves,
                "Una cuota comunal avisa una vez, con la clave que también usa la revisión diaria");
    }

    private List<String> clavesUmbral(long cuotaId) {
        String prefijo = "CUOTA:" + cuotaId + ":UMBRAL:";
        return avisoRepository.findAll().stream().map(AvisoEnviadoModel::getClave)
                .filter(c -> c.startsWith(prefijo)).sorted().toList();
    }

    private long[] crearCuotaAl90() {
        long[] f = new TransactionTemplate(txManager).execute(s -> {
            int n = SEQ.incrementAndGet();
            RegionModel region = RegionModel.builder().nombre("Coquimbo R13U-" + n).codigo("04").build(); em.persist(region);
            ComunaModel comuna = ComunaModel.builder().nombre("La Serena R13U-" + n).region(region).build(); em.persist(comuna);
            CaletaModel caleta = CaletaModel.builder().nombre("Caleta R13U-" + n).region(region).comuna(comuna).build(); em.persist(caleta);
            EspecieModel esp = EspecieModel.builder().nombre("Huiro negro R13U-" + n).build(); em.persist(esp);
            ExtraccionTipoModel ext = ExtraccionTipoModel.builder().nombre("Varado R13U-" + n).build(); em.persist(ext);
            HumedadEstadoModel hum = HumedadEstadoModel.builder().nombre("Húmedo R13U-" + n).build(); em.persist(hum);
            PerfilModel pRec = PerfilModel.builder().nombre("RECOLECTOR").build(); em.persist(pRec);
            UsuarioModel u0 = UsuarioModel.builder().rut("REV13-U0-" + n).nombres("Previo").perfil(pRec).comuna(comuna).build(); em.persist(u0);
            UsuarioModel u1 = UsuarioModel.builder().rut("REV13-U1-" + n).nombres("Primero").perfil(pRec).comuna(comuna).build(); em.persist(u1);
            UsuarioModel u2 = UsuarioModel.builder().rut("REV13-U2-" + n).nombres("Segundo").perfil(pRec).comuna(comuna).build(); em.persist(u2);

            Set<ComunaModel> comunas = new LinkedHashSet<>();
            comunas.add(comuna);
            CuotaExtraccionModel cuota = CuotaExtraccionModel.builder()
                    .nivelAgregacion("COMUNA").ambito("AREA_LIBRE").perfil("RECOLECTOR").region(region).comuna(comuna).comunas(comunas)
                    .especie(esp).periodo("MENSUAL")
                    .fechaInicio(java.sql.Date.valueOf("2026-10-01")).fechaFin(java.sql.Date.valueOf("2026-10-31"))
                    .limiteKg(1000.0).metrica("DESEMBARQUE").modoAccion("SOLO_ALERTA")
                    .resolucion("REV-13OCT-U-" + n).activo(true).estado("ABIERTA")
                    .build();
            em.persist(cuota);

            java.sql.Date previa = java.sql.Date.valueOf("2026-10-05");
            em.persist(DeclaracionRecolectorModel.builder()
                    .usuario(u0).especie(esp).extraccionTipo(ext).humedadEstado(hum).comuna(comuna).caleta(caleta)
                    .fechaDeclaracion(previa).fechaExtraccion(previa).hora("09:00")
                    .nombre("Previo").codigoSernapesca("RPA-R13U").folioDesembarqueRo("REV13-RO-U-" + n)
                    .codigoDestinatario("X").nombreDestinatario("Destino").usuarioDestinatario(u0)
                    .desembarque(new BigDecimal("900")).captura(new BigDecimal("900")).build());
            return new long[]{cuota.getId(), u1.getId(), u2.getId(), esp.getId(), comuna.getId()};
        });
        em.clear();
        return f;
    }
}
