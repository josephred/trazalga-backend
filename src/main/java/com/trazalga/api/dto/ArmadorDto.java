package com.trazalga.api.dto;

import java.util.List;
import com.trazalga.api.dto.sernapesca.EmbarcacionDto;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ArmadorDto {
    private String rut;
    private String nombre;
    private List<EmbarcacionDto> embarcaciones;
}
