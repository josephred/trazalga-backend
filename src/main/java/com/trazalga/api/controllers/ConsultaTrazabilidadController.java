package com.trazalga.api.controllers;

import com.trazalga.api.dto.ResultadoFolioDTO;
import com.trazalga.api.services.ConsultaFolioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador para consultas puntuales de trazabilidad y búsqueda por folio (T2.2).
 */
@RestController
@RequestMapping({"/api/consultas", "/consultas"})
@RequiredArgsConstructor
@Slf4j
public class ConsultaTrazabilidadController {

    private final ConsultaFolioService consultaFolioService;

    /**
     * Búsqueda puntual por folio, patente, embarcación o documento tributario.
     *
     * @param q              Texto o folio a buscar
     * @param authentication Información de autenticación del usuario (JWT)
     * @return Lista de resultados ordenados del más reciente al más antiguo
     */
    @GetMapping("/folio")
    public ResponseEntity<List<ResultadoFolioDTO>> buscarPorFolio(
            @RequestParam(name = "q", required = false) String q,
            Authentication authentication) {
        String userRut = authentication != null ? authentication.getName() : "ANONIMO";
        List<ResultadoFolioDTO> resultados = consultaFolioService.buscar(q, userRut);
        return ResponseEntity.ok(resultados);
    }
}
