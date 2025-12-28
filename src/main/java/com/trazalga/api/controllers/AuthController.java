package com.trazalga.api.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trazalga.api.dto.AuthResponse;
import com.trazalga.api.dto.LoginRequest;
import com.trazalga.api.services.AuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService; // El cerebro

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        // Le pide al servicio que valide al usuario
        String token = authService.validarCredenciales(request.getRut(), request.getClave());
        return ResponseEntity.ok(new AuthResponse(token));
    }
}
