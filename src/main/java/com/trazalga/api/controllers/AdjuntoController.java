package com.trazalga.api.controllers;

import com.trazalga.api.models.AdjuntoModel;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.repositories.IUsuarioRepository;
import com.trazalga.api.services.AdjuntoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Endpoints autenticados para carga y descarga de evidencia adjunta en disco (NA.4).
 */
@RestController
@RequestMapping("/api/adjuntos")
@RequiredArgsConstructor
public class AdjuntoController {

    private final AdjuntoService adjuntoService;
    private final IUsuarioRepository usuarioRepository;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> subir(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "tipo", required = false) String tipoDocumento,
            Principal principal) {
        Long usuarioId = resolverUsuarioId(principal);
        AdjuntoModel saved = adjuntoService.guardar(file, usuarioId, tipoDocumento);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", saved.getId());
        body.put("uuid", saved.getUuid());
        body.put("url", "/api/adjuntos/" + saved.getUuid());
        body.put("nombreOriginal", saved.getNombreOriginal());
        body.put("mimeType", saved.getMimeType());
        body.put("tamanoBytes", saved.getTamanoBytes());
        body.put("sha256", saved.getSha256());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> descargar(@PathVariable String uuid) {
        return adjuntoService.cargarPorUuid(uuid)
                .<ResponseEntity<?>>map(contenido -> {
                    AdjuntoModel meta = contenido.metadata();
                    MediaType mediaType;
                    try {
                        mediaType = MediaType.parseMediaType(meta.getMimeType());
                    } catch (Exception ex) {
                        mediaType = MediaType.APPLICATION_OCTET_STREAM;
                    }
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(mediaType);
                    headers.setContentDisposition(ContentDisposition.inline()
                            .filename(meta.getNombreOriginal(), StandardCharsets.UTF_8)
                            .build());
                    headers.setCacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate());
                    return ResponseEntity.ok()
                            .headers(headers)
                            .body(contenido.resource());
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Long resolverUsuarioId(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            return null;
        }
        return usuarioRepository.findByRut(principal.getName())
                .map(UsuarioModel::getId)
                .orElse(null);
    }
}
