package com.trazalga.api.dto;

import lombok.Data;

@Data
public class UsuarioRegistroDTO {
    private String rut;
    private String clave;
    private String nombres;
    private String apellidop;
    private String apellidom;
    private String correo;
    private Long perfilId;
    private Long comunaId;
}