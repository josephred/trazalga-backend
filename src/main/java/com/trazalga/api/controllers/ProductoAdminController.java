package com.trazalga.api.controllers;

import com.trazalga.api.dto.ProductoAdminDTO;
import com.trazalga.api.dto.ProductoAdminDTO.ProductoAdminRequest;
import com.trazalga.api.services.ProductoAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/admin/productos", "/admin/productos"})
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administración · Formatos de Producto", description = "Gestión administrativa del catálogo oficial de formatos de producto (NF.1/NF.3)")
public class ProductoAdminController {

    @Autowired
    private ProductoAdminService service;

    @GetMapping
    @Operation(summary = "Lista todos los formatos de producto con su conteo de usos en Producción y Destino")
    public List<ProductoAdminDTO> listar() {
        return service.listarConUsos();
    }

    @PostMapping
    @Operation(summary = "Crea un nuevo formato de producto")
    public ResponseEntity<?> crear(@RequestBody ProductoAdminRequest request, Authentication auth) {
        try {
            String usuario = auth != null ? auth.getName() : "ADMIN";
            return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(request, usuario));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "CONFLICTO_FORMATO", "message", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "VALIDACION_FORMATO", "message", ex.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualiza un formato de producto existente")
    public ResponseEntity<?> actualizar(@PathVariable Long id, @RequestBody ProductoAdminRequest request, Authentication auth) {
        try {
            String usuario = auth != null ? auth.getName() : "ADMIN";
            return ResponseEntity.ok(service.actualizar(id, request, usuario));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "CONFLICTO_FORMATO", "message", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "VALIDACION_FORMATO", "message", ex.getMessage()));
        }
    }

    @PutMapping("/{id}/activo")
    @Operation(summary = "Activa o desactiva un formato de producto")
    public ResponseEntity<?> cambiarActivo(@PathVariable Long id, @RequestBody Map<String, Boolean> body, Authentication auth) {
        try {
            boolean activo = body != null && Boolean.TRUE.equals(body.get("activo"));
            String usuario = auth != null ? auth.getName() : "ADMIN";
            return ResponseEntity.ok(service.cambiarActivo(id, activo, usuario));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "VALIDACION_FORMATO", "message", ex.getMessage()));
        }
    }

    @PostMapping(value = "/importar", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_OCTET_STREAM_VALUE, "*/*"})
    @Operation(summary = "Vista previa o carga masiva del catálogo oficial Sernapesca en CSV o XLSX (QF.3)")
    public ResponseEntity<?> importar(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestBody(required = false) String csvBody,
            @RequestParam(value = "aplicar", defaultValue = "false") boolean aplicar,
            Authentication auth) {
        try {
            String usuario = auth != null ? auth.getName() : "ADMIN";
            InputStream stream;
            String nombreArchivo;
            if (file != null && !file.isEmpty()) {
                stream = file.getInputStream();
                nombreArchivo = file.getOriginalFilename();
            } else if (csvBody != null && !csvBody.isBlank()) {
                stream = new ByteArrayInputStream(csvBody.getBytes(StandardCharsets.UTF_8));
                nombreArchivo = "catalogo.csv";
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "IMPORTACION_CATALOGO", "message", "Debe adjuntar un archivo CSV o XLSX."));
            }
            return ResponseEntity.ok(service.importarCatalogo(stream, nombreArchivo, aplicar, usuario));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "IMPORTACION_CATALOGO", "message", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "IMPORTACION_CATALOGO", "message", "Error al procesar el catálogo: " + ex.getMessage()));
        }
    }
}

