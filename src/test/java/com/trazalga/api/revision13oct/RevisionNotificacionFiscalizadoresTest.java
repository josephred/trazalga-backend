package com.trazalga.api.revision13oct;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.trazalga.api.IntegracionBase;
import com.trazalga.api.models.PerfilModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.services.NotificationService;

import jakarta.persistence.EntityManager;

/**
 * Revisión 13-10 · TA.5: NotificacionesListener atiende HallazgoRegistrado, CuotaCerrada, CuotaUmbralAlcanzado y
 * CargaRetenida con @Async("notificacionesExecutor") y @TransactionalEventListener(AFTER_COMMIT): corre en un hilo del
 * ejecutor, sin transacción ni sesión. Los cuatro llaman NotificationService.notificarFiscalizadores, que recorre los
 * usuarios y lee su perfil (LAZY). La prueba hace esa misma llamada en ese mismo ejecutor, con el perfil «it»
 * (open-in-view=false y sin enable_lazy_load_no_trans, como producción).
 */
class RevisionNotificacionFiscalizadoresTest extends IntegracionBase {

    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private NotificationService notificationService;
    @Autowired @Qualifier("notificacionesExecutor") private Executor notificacionesExecutor;

    @Test
    @DisplayName("TA.5: las notificaciones a fiscalización funcionan en el hilo de los oyentes AFTER_COMMIT")
    void notificarFiscalizadoresDesdeElEjecutorDeNotificaciones() {
        new TransactionTemplate(txManager).executeWithoutResult(s -> {
            PerfilModel p = PerfilModel.builder().nombre("FISCALIZADOR").build(); em.persist(p);
            UsuarioModel u = new UsuarioModel(); u.setRut("REV13-N-FIS"); u.setNombres("Fiscalizador"); u.setPerfil(p);
            em.persist(u);
        });
        em.clear();

        Throwable error = null;
        try {
            CompletableFuture.runAsync(() -> notificationService.notificarFiscalizadores(
                    null, "Prueba", "Mensaje de prueba", Map.of("tipo", "PRUEBA")), notificacionesExecutor).join();
        } catch (CompletionException e) {
            error = e.getCause();
        }
        System.out.println("REVISION13_NOTIF notificarFiscalizadores en NotifAsync -> "
                + (error == null ? "sin error" : error.getClass().getSimpleName() + ": " + error.getMessage()));
        assertNull(error, "Las notificaciones a fiscalización no deben fallar por carga perezosa en el hilo asíncrono");
    }
}
