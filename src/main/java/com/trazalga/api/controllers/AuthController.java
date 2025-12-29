package com.trazalga.api.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trazalga.api.dto.AuthResponse;
import com.trazalga.api.dto.LoginRequest;
import com.trazalga.api.dto.UsuarioRegistroDTO;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.services.AuthService;
import com.trazalga.api.services.UsuarioService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private UsuarioService usuarioService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        // Le pide al servicio que valide al usuario
        String token = authService.validarCredenciales(request.getRut(), request.getClave());
        return ResponseEntity.ok(new AuthResponse(token));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody UsuarioRegistroDTO registroDTO) {
        try {
            UsuarioModel usuarioCreado = usuarioService.registrarUsuario(registroDTO);
            return ResponseEntity.ok("Usuario " + usuarioCreado.getRut() + " registrado exitosamente.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
