package com.trazalga.api.controllers;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trazalga.api.models.DeclaracionAutorizacionModel;
import com.trazalga.api.services.declaraciones.AutorizacionDeclaracionService;

import lombok.RequiredArgsConstructor;

/**
 * Procedimiento del fiscalizador sobre declaraciones congeladas por una alerta (refinamiento 30-10, punto 3).
 * <ul>
 *   <li>{@code GET /api/declaraciones/{tipo}/{id}/edicion}: estado e historial (ADMIN, FISCALIZADOR, AUDITOR).</li>
 *   <li>{@code POST .../autorizar-edicion}: el dueño puede editar una vez (ADMIN, FISCALIZADOR).</li>
 *   <li>{@code POST .../eliminar}: el fiscalizador elimina con respaldo (ADMIN, FISCALIZADOR).</li>
 * </ul>
 * {@code tipo}: RECOLECTOR, ARMADOR o AREA. El cuerpo de los POST: {@code { motivo, actaVerificacion, horas }}.
 */
@RestController
@RequestMapping("/api/declaraciones")
@RequiredArgsConstructor
public class AutorizacionDeclaracionController {

    private final AutorizacionDeclaracionService service;

    @GetMapping("/{tipo}/{id}/edicion")
    public ResponseEntity<Map<String, Object>> estado(@PathVariable String tipo, @PathVariable Long id) {
        return ResponseEntity.ok(service.estado(tipo, id));
    }

    @PostMapping("/{tipo}/{id}/autorizar-edicion")
    public ResponseEntity<Map<String, Object>> autorizarEdicion(@PathVariable String tipo, @PathVariable Long id,
                                                               @RequestBody Map<String, Object> body) {
        DeclaracionAutorizacionModel a = service.autorizarEdicion(tipo, id, texto(body, "motivo"),
                texto(body, "actaVerificacion"), entero(body, "horas"));
        return ResponseEntity.ok(resumen(a, "Edición autorizada. El usuario puede modificar la declaración una vez antes del vencimiento."));
    }

    @PostMapping("/{tipo}/{id}/eliminar")
    public ResponseEntity<Map<String, Object>> eliminar(@PathVariable String tipo, @PathVariable Long id,
                                                       @RequestBody Map<String, Object> body) {
        DeclaracionAutorizacionModel a = service.eliminarConAutorizacion(tipo, id, texto(body, "motivo"),
                texto(body, "actaVerificacion"));
        return ResponseEntity.ok(resumen(a, "Declaración eliminada por fiscalización. Se guardó un respaldo con sus marcas."));
    }

    private static Map<String, Object> resumen(DeclaracionAutorizacionModel a, String mensaje) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("tipo", a.getTipo());
        m.put("declaracionTipo", a.getDeclaracionTipo());
        m.put("declaracionId", a.getDeclaracionId());
        m.put("actaVerificacion", a.getActaVerificacion());
        m.put("autorizadoEn", a.getAutorizadoEn());
        m.put("venceEn", a.getVenceEn());
        m.put("message", mensaje);
        return m;
    }

    private static String texto(Map<String, Object> body, String clave) {
        Object v = body != null ? body.get(clave) : null;
        return v != null ? v.toString() : null;
    }

    private static Integer entero(Map<String, Object> body, String clave) {
        Object v = body != null ? body.get(clave) : null;
        if (v == null) return null;
        try {
            return Integer.valueOf(v.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
