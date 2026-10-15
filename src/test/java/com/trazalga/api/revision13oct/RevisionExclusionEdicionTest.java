package com.trazalga.api.revision13oct;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.trazalga.api.IntegracionBase;
import com.trazalga.api.models.BuzoModel;
import com.trazalga.api.models.CaletaModel;
import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.models.DeclaracionArmadorModel;
import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.models.EmbarcacionModel;
import com.trazalga.api.models.EspecieModel;
import com.trazalga.api.models.ExtraccionTipoModel;
import com.trazalga.api.models.HumedadEstadoModel;
import com.trazalga.api.models.PerfilModel;
import com.trazalga.api.models.ProvinciaModel;
import com.trazalga.api.models.RegionModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.services.CuotaExtraccionService;

import jakarta.persistence.EntityManager;

/**
 * Revisión 13-10 · TC.5 (defecto 6): al editar, el consumo debe excluir la declaración editada y nada más.
 * Las declaraciones de recolector y de armador están en tablas distintas, con secuencias de id independientes:
 * el mismo número de id puede existir en las dos. En una cuota de área libre se suman las dos tablas.
 */
class RevisionExclusionEdicionTest extends IntegracionBase {

    private static final java.sql.Date FECHA = java.sql.Date.valueOf("2026-10-10");

    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private CuotaExtraccionService cuotaService;

    @Test
    @DisplayName("TC.5: al editar una declaración de recolector, no se excluye la de armador que tiene el mismo id")
    void exclusionNoAlcanzaAOtraTabla() {
        long[] ids = new TransactionTemplate(txManager).execute(s -> {
            RegionModel region = RegionModel.builder().nombre("Coquimbo R13X").codigo("04").build(); em.persist(region);
            ProvinciaModel prov = ProvinciaModel.builder().nombre("Elqui R13X").region(region).build(); em.persist(prov);
            ComunaModel comuna = ComunaModel.builder().nombre("Coquimbo R13X").region(region).provincia(prov).build(); em.persist(comuna);
            CaletaModel caleta = CaletaModel.builder().nombre("Caleta R13X").region(region).comuna(comuna).build(); em.persist(caleta);
            EspecieModel esp = EspecieModel.builder().nombre("Huiro negro R13X").build(); em.persist(esp);
            EspecieModel relleno = EspecieModel.builder().nombre("Relleno R13X").build(); em.persist(relleno);
            ExtraccionTipoModel ext = ExtraccionTipoModel.builder().nombre("Varado R13X").build(); em.persist(ext);
            HumedadEstadoModel hum = HumedadEstadoModel.builder().nombre("Húmedo R13X").build(); em.persist(hum);
            UsuarioModel rec = usuario("REV13-X-REC", perfil("RECOLECTOR"), comuna);
            UsuarioModel arm = usuario("REV13-X-ARM", perfil("ARMADOR"), comuna);
            UsuarioModel com = usuario("REV13-X-COM", perfil("COMERCIALIZADOR"), comuna);
            EmbarcacionModel emb = new EmbarcacionModel(); emb.setNombre("Don Lucho R13X"); em.persist(emb);
            BuzoModel buzo = new BuzoModel(); buzo.setNombre("Buzo R13X"); em.persist(buzo);

            Set<ComunaModel> comunas = new LinkedHashSet<>();
            comunas.add(comuna);
            CuotaExtraccionModel cuota = CuotaExtraccionModel.builder()
                    .nivelAgregacion("COMUNA").ambito("AREA_LIBRE").perfil("RECOLECTOR").region(region).comuna(comuna).comunas(comunas)
                    .especie(esp).periodo("MENSUAL")
                    .fechaInicio(java.sql.Date.valueOf("2026-10-01")).fechaFin(java.sql.Date.valueOf("2026-10-31"))
                    .limiteKg(100_000.0).metrica("DESEMBARQUE").resolucion("REV-13OCT-X").activo(true).estado("ABIERTA")
                    .build();
            em.persist(cuota);

            DeclaracionRecolectorModel r = recolectora(rec, com, esp, ext, hum, comuna, caleta, "100");
            DeclaracionArmadorModel a = armadora(arm, com, esp, ext, hum, comuna, caleta, emb, buzo, "300");
            // Igualar los id de las dos tablas: las filas que se adelantan pasan a otra especie y no suman.
            int guarda = 0;
            while (!a.getId().equals(r.getId()) && guarda++ < 50) {
                if (a.getId() < r.getId()) {
                    a.setEspecie(relleno);
                    a = armadora(arm, com, esp, ext, hum, comuna, caleta, emb, buzo, "300");
                } else {
                    r.setEspecie(relleno);
                    r = recolectora(rec, com, esp, ext, hum, comuna, caleta, "100");
                }
            }
            em.flush();
            return new long[]{cuota.getId(), r.getId(), a.getId(), rec.getId()};
        });
        em.clear();
        assertEquals(ids[1], ids[2], "Precondición: la recolectora y la del armador tienen el mismo id");

        BigDecimal sinExcluir = consumo(ids[0], ids[3], null);
        BigDecimal editandoRecolectora = consumo(ids[0], ids[3], ids[1]);
        System.out.println("REVISION13_TC5 id compartido=" + ids[1] + " | consumo sin excluir=" + sinExcluir
                + " | consumo al editar la recolectora=" + editandoRecolectora);
        assertEquals(0, new BigDecimal("400").compareTo(sinExcluir),
                "Precondición: la cuota de área libre suma la recolectora (100 kg) y la del armador (300 kg)");
        assertEquals(0, new BigDecimal("300").compareTo(editandoRecolectora),
                "Al editar la recolectora sólo se excluye ella; los 300 kg del armador deben seguir sumando");
    }

    private BigDecimal consumo(long cuotaId, long usuarioId, Long excluida) {
        cuotaService.invalidarCacheConsumo();
        return new TransactionTemplate(txManager).execute(s -> {
            CuotaExtraccionModel c = em.find(CuotaExtraccionModel.class, cuotaId);
            return cuotaService.ejecutarConsultaConsumo(c, FECHA, "RECOLECTOR", usuarioId, excluida);
        });
    }

    private DeclaracionRecolectorModel recolectora(UsuarioModel u, UsuarioModel dest, EspecieModel esp, ExtraccionTipoModel ext,
                                                   HumedadEstadoModel hum, ComunaModel comuna, CaletaModel caleta, String kg) {
        DeclaracionRecolectorModel d = DeclaracionRecolectorModel.builder()
                .usuario(u).especie(esp).extraccionTipo(ext).humedadEstado(hum).comuna(comuna).caleta(caleta)
                .fechaDeclaracion(FECHA).fechaExtraccion(FECHA).hora("09:30")
                .nombre("Recolector").codigoSernapesca("RPA-R13X").folioDesembarqueRo("REV13-RO-X-" + System.nanoTime())
                .codigoDestinatario("X").nombreDestinatario("Comercializador").usuarioDestinatario(dest)
                .desembarque(new BigDecimal(kg)).captura(new BigDecimal(kg))
                .build();
        em.persist(d);
        em.flush();
        return d;
    }

    private DeclaracionArmadorModel armadora(UsuarioModel u, UsuarioModel dest, EspecieModel esp, ExtraccionTipoModel ext,
                                             HumedadEstadoModel hum, ComunaModel comuna, CaletaModel caleta,
                                             EmbarcacionModel emb, BuzoModel buzo, String kg) {
        DeclaracionArmadorModel d = DeclaracionArmadorModel.builder()
                .usuario(u).especie(esp).extraccionTipo(ext).humedadEstado(hum).comuna(comuna).caleta(caleta)
                .embarcacion(emb).buzo(buzo)
                .fechaDeclaracion(FECHA).fechaExtraccion(FECHA).hora("10:00")
                .folioDesembarqueDa("REV13-DA-X-" + System.nanoTime())
                .tipoDestinatario("comercializador").codigoDestinatario("X").usuarioDestinatario(dest)
                .desembarque(new BigDecimal(kg)).captura(Double.valueOf(kg))
                .build();
        em.persist(d);
        em.flush();
        return d;
    }

    private PerfilModel perfil(String nombre) {
        return em.createQuery("SELECT p FROM PerfilModel p WHERE p.nombre = :n", PerfilModel.class)
                .setParameter("n", nombre).getResultStream().findFirst()
                .orElseGet(() -> { PerfilModel p = PerfilModel.builder().nombre(nombre).build(); em.persist(p); return p; });
    }

    private UsuarioModel usuario(String rut, PerfilModel perfil, ComunaModel comuna) {
        UsuarioModel u = UsuarioModel.builder().rut(rut).nombres("Usuario").apellidop(rut).perfil(perfil).comuna(comuna).build();
        em.persist(u);
        return u;
    }
}
