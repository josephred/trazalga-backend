package com.trazalga.api.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FichaAlertasDTO {
    private int totalMarcasActivas;
    private boolean hayCargasRetenidas;

    @Builder.Default
    private List<FichaMarcaDTO> marcasActivas = new ArrayList<>();

    @Builder.Default
    private List<String> motivosRetencion = new ArrayList<>();
}
