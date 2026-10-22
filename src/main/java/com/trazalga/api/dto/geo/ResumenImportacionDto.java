package com.trazalga.api.dto.geo;

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
public class ResumenImportacionDto {
    private boolean aplicado;        // true si se aplicó a BD, false si es solo vista previa
    private boolean forzado;         // true si se forzó la sobreescritura de conflictos manuales
    private String fuentePorDefecto; // Fuente asignada
    private int totalFilas;
    private int calzadas;
    private int ambiguas;
    private int sinCalce;
    private int fueraDeRango;
    private int enConflicto;
    private int aplicadas;

    @Builder.Default
    private List<FilaResultadoImportacionDto> filas = new ArrayList<>();
}
