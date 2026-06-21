package com.trazalga.api.controllers;

import java.util.ArrayList;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.trazalga.api.dto.ArmadorDto;
import com.trazalga.api.services.ArmadorService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/armador")
@Tag(name = "Armador", description = "Consultas de armadores (proxy a Sernapesca)")
public class ArmadorController {

    private final ArmadorService armadorService;

    public ArmadorController(ArmadorService armadorService) {
        this.armadorService = armadorService;
    }

    @GetMapping
    @Operation(summary = "Obtiene las embarcaciones de un armador según su RUT")
    public List<ArmadorDto> getArmadores(@RequestParam(value = "rut", required = false) String rut) {
        if (rut == null || rut.trim().isEmpty()) {
            // Si el front no envía rut, devolvemos lista vacía en lugar de fallar
            return new ArrayList<>();
        }
        return armadorService.obtenerArmadorPorRut(rut);
    }
}
