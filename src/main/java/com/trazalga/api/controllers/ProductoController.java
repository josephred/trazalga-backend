package com.trazalga.api.controllers;

import com.trazalga.api.models.ProductoModel;
import com.trazalga.api.repositories.IProductoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@RestController
@RequestMapping("/producto")
@Tag(name = "Productos", description = "Catálogo de Productos Resultantes de Procesamiento")
public class ProductoController {

    @Autowired
    private IProductoRepository repository;

    @GetMapping
    @Operation(summary = "Obtiene los formatos de producto registrados en el sistema (activos por defecto; filtrable por especieId)")
    public List<ProductoModel> getProductos(
            @RequestParam(required = false, defaultValue = "false") boolean incluirInactivos,
            @RequestParam(required = false) Long especieId) {
        if (especieId != null) {
            return incluirInactivos
                    ? repository.findByEspecieIdOrderByNombreAsc(especieId)
                    : repository.findActivosByEspecieId(especieId);
        }
        return incluirInactivos
                ? repository.findAllByOrderByNombreAsc()
                : repository.findByActivoTrueOrActivoIsNullOrderByNombreAsc();
    }
}
