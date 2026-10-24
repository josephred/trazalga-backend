package com.trazalga.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InformeReconstruccionDTO {
    private LocalDateTime fechaEjecucion;
    private int declaracionesComercializadorProcesadas;
    private int declaracionesPlantaProcesadas;
    private int transportistasCreadosConRut;
    private int transportistasCreadosSinRut;
    private int rutInvalidosDetectados;
    private int rutConMasDeUnNombre;
    private int nombresConMasDeUnRut;
    private int vehiculosCreados;
    private int patentesFormatoInvalido;
    private int patentesComoCamionYRampla;
    private List<String> detallesObservaciones;
}
