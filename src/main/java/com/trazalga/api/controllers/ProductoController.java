package com.trazalga.api.controllers;

import com.trazalga.api.models.ProductoModel;
import com.trazalga.api.repositories.IProductoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
    @Operation(summary = "Obtiene todos los productos registrados en el sistema")
    public List<ProductoModel> getProductos() {
        List<ProductoModel> productos = repository.findAll();
        if (productos.isEmpty()) {
            return List.of(
                ProductoModel.builder().id(1L).nombre("Alga Seca").descripcion("Producto de alga deshidratada").build(),
                ProductoModel.builder().id(2L).nombre("Alga Picada / Picadillo").descripcion("Producto de alga troceada o picada").build(),
                ProductoModel.builder().id(3L).nombre("Harina de Alga").descripcion("Producto procesado de harina o polvo de alga").build(),
                ProductoModel.builder().id(4L).nombre("Alga Frecho / Molienda").descripcion("Molienda o subproducto procesado").build(),
                ProductoModel.builder().id(5L).nombre("Alga Fila / Tira").descripcion("Corte en tiras o filas").build()
            );
        }
        return productos;
    }
}
