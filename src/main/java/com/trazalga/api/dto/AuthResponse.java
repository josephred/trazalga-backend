package com.trazalga.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuthResponse {
    private String token;
    private String perfil;
    private String nombre;
    private Long usuarioId;

    public AuthResponse(String token) {
        this.token = token;
    }
}