package com.trazalga.api.dto;

import lombok.Data;

@Data // Esto genera los getters y setters automáticamente con Lombok
public class LoginRequest {
    private String rut;
    private String clave;
}