package com.trazalga.api.controllers;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trazalga.api.services.ConsultaPatenteService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping({"/api/public/patentes", "/public/patentes"})
@Tag(name = "Consulta Pública de Patentes", description = "Servicio público de verificación de vigencia de traslado de algas por patente de camión o carro")
public class ConsultaPatentePublicaController {

    @Autowired
    private ConsultaPatenteService patenteService;

    @GetMapping({"/comerciantes", "/camiones-comerciantes"})
    @Operation(summary = "Lista todas las patentes de los camiones de los comerciantes registradas en la base de datos")
    public ResponseEntity<List<Map<String, Object>>> listarPatentesComerciantes() {
        List<Map<String, Object>> lista = patenteService.obtenerPatentesCamionesComerciantes();
        return ResponseEntity.ok(lista);
    }

    @GetMapping("/{patente}")
    @Operation(summary = "Consulta el último movimiento y vigencia de traslado para una patente de camión o carro (sin datos personales)")
    public ResponseEntity<Map<String, Object>> consultarPatente(
            @PathVariable String patente,
            HttpServletRequest request) {
        Map<String, Object> resultado = patenteService.consultar(patente, request);
        return ResponseEntity.ok(resultado);
    }
}
