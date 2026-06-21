package com.trazalga.api.controllers;

import com.trazalga.api.services.DatabaseAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/database")
@Tag(name = "Administración de Base de Datos", description = "Operaciones de mantenimiento de la base de datos")
public class DatabaseAdminController {

    @Autowired
    private DatabaseAdminService databaseAdminService;

    @DeleteMapping("/vaciar-tablas")
    @Operation(summary = "Vacía las tablas de declaraciones y maestros seleccionados")
    public ResponseEntity<Map<String, String>> vaciarTablas() {
        try {
            databaseAdminService.vaciarTablas();
            return ResponseEntity.ok(Collections.singletonMap("mensaje", "Tablas vaciadas correctamente"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Collections.singletonMap("error", "Error al vaciar las tablas: " + e.getMessage()));
        }
    }
}
