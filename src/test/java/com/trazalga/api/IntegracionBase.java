package com.trazalga.api;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Clase base para pruebas de integración con disciplina transaccional idéntica a producción (TA.1):
 * - Perfil "it" activo (application-it.properties).
 * - spring.jpa.open-in-view=false.
 * - Sin hibernate.enable_lazy_load_no_trans.
 */
@SpringBootTest
@ActiveProfiles("it")
public abstract class IntegracionBase {
}
