package com.trazalga.api.dto.sernapesca;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Elemento genérico de combo de Sernapesca: { codigo, valor }.
 * Usado por regiones, especies autorizadas, etc.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ComboIntDto {

    private Integer codigo;
    private String valor;
}
