package com.trazalga.api.dto.sernapesca;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Envoltura estándar de las respuestas del API de Sernapesca:
 * { success, total, data, error }.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SernapescaResponse<T> {

    private boolean success;
    private Integer total;
    private List<T> data;
    private String error;
}
