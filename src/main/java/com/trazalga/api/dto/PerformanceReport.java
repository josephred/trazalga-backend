package com.trazalga.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.text.SimpleDateFormat;
import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PerformanceReport {
    private int cantidad;
    private long startTime; // Milisegundos Unix
    private long endTime; // Milisegundos Unix

    // --- CÁLCULOS DE DURACIÓN ---

    public long getDuracionMs() {
        return endTime - startTime;
    }

    public double getDuracionSegundos() {
        return (endTime - startTime) / 1000.0;
    }

    // --- FECHA Y HORA FORMATEADA (Lectura humana) ---

    public String getInicioFormateado() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        return sdf.format(new Date(startTime));
    }

    public String getFinFormateado() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        return sdf.format(new Date(endTime));
    }

    // --- INDICADORES ADICIONALES (KPIs) ---

    public double getPromedioPorRegistroMs() {
        if (cantidad == 0)
            return 0;
        return (double) getDuracionMs() / cantidad;
    }

    public double getRegistrosPorSegundo() {
        double segundos = getDuracionSegundos();
        if (segundos == 0)
            return 0;
        return (double) cantidad / segundos;
    }
}