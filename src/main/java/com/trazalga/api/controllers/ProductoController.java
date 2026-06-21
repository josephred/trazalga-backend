package com.trazalga.api.controllers;

import java.util.Collections;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/producto")
@Tag(name = "Productos", description = "Productos (Placeholder)")
public class ProductoController {

    @GetMapping
    @Operation(summary = "Obtiene todos los productos (actualmente retorna lista vacía)")
    public List<Object> getProductos() {
        // Retorna lista vacía para evitar error 404 en el móvil
        return Collections.emptyList();
    }
}
