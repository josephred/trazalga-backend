package com.trazalga.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogoImportacionDTO {

    private boolean aplicado;
    private String archivo;
    private String fuente;
    private int totalFilas;
    private int nuevos;
    private int actualizados;
    private int sinCambio;
    private int recursos;
    private int porRevisar;
    private int descartadosDuplicados;
    private int localesNoEnCatalogoCount;

    @Builder.Default
    private List<FilaResultadoCatalogoDTO> filas = new ArrayList<>();

    @Builder.Default
    private List<ProductoAdminDTO> localesNoEnCatalogo = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilaResultadoCatalogoDTO {
        private int numeroFila;
        private String tipo;
        private String estado;
        private String codigo;
        private String nombreOficial;
        private String especieNombre;
        private String humedadNombre;
        private String presentacion;
        private String calibre;
        private String mensaje;
    }
}
