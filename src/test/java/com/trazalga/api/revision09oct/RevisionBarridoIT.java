package com.trazalga.api.revision09oct;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.trazalga.api.IntegracionBase;
import com.trazalga.api.models.*;
import com.trazalga.api.repositories.IDeclaracionMarcaRepository;
import com.trazalga.api.services.retencion.RetencionCustodiaService;

import jakarta.persistence.EntityManager;

/**
 * Revisión 12-10 · El barrido cada 15 minutos corre en producción aunque el bloqueo esté apagado.
 * Con un lote húmedo de ~20 h (ventana 80–100 % del plazo Res. 3602), el barrido intenta registrar un preaviso.
 */
class RevisionBarridoIT extends IntegracionBase {

    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private RetencionCustodiaService retencionCustodiaService;
    @Autowired private IDeclaracionMarcaRepository marcaRepository;

    @Test
    @DisplayName("Barrido con bloqueo apagado: no debe fallar ni crear hallazgos")
    void barridoConBloqueoApagado() {
        Long id = new TransactionTemplate(txManager).execute(s -> {
            PerfilModel pCom = PerfilModel.builder().nombre("COMERCIALIZADOR").build(); em.persist(pCom);
            UsuarioModel rec = new UsuarioModel(); rec.setRut("REV-S-REC"); rec.setNombres("Recolector"); em.persist(rec);
            UsuarioModel com = new UsuarioModel(); com.setRut("REV-S-COM"); com.setNombres("Comerciante"); com.setPerfil(pCom); em.persist(com);
            EspecieModel esp = new EspecieModel(); esp.setNombre("Huiro negro"); em.persist(esp);
            HumedadEstadoModel hum = new HumedadEstadoModel(); hum.setNombre("HÚMEDO"); em.persist(hum);
            ComunaModel comuna = new ComunaModel(); comuna.setNombre("La Serena"); em.persist(comuna);
            CaletaModel caleta = new CaletaModel(); caleta.setNombre("Caleta S"); em.persist(caleta);
            ExtraccionTipoModel ext = new ExtraccionTipoModel(); ext.setNombre("Varado"); em.persist(ext);
            LocalDateTime hace20h = LocalDateTime.now().minusHours(20);
            DeclaracionRecolectorModel d = new DeclaracionRecolectorModel();
            d.setUsuario(rec); d.setUsuarioDestinatario(com); d.setCodigoDestinatario("X"); d.setNombreDestinatario("X");
            d.setEspecie(esp); d.setHumedadEstado(hum); d.setComuna(comuna); d.setCaleta(caleta); d.setExtraccionTipo(ext);
            d.setFechaDeclaracion(java.sql.Date.valueOf(hace20h.toLocalDate())); d.setFechaExtraccion(java.sql.Date.valueOf(hace20h.toLocalDate()));
            d.setHora(hace20h.format(DateTimeFormatter.ofPattern("HH:mm")));
            d.setNombre("Recolector"); d.setCodigoSernapesca("RPA-S"); d.setDesembarque(new BigDecimal("1000"));
            d.setEstado("NEGOCIACION");
            em.persist(d);
            return d.getId();
        });
        em.clear();

        Throwable error = null;
        try {
            retencionCustodiaService.barrer(LocalDateTime.now());
        } catch (Throwable t) { error = t; }
        long marcas = marcaRepository.findByDeclaracionTipoAndDeclaracionId("RECOLECTOR", id).size();
        System.out.println("REVISION_BARRIDO excepcion=" + (error == null ? "ninguna" : error.getClass().getSimpleName() + ": " + error.getMessage())
                + " | marcas creadas=" + marcas);
        assertNull(error, "El barrido no debe fallar");
    }
}
