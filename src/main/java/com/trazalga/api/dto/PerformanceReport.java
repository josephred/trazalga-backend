package com.trazalga.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PerformanceReport {
    private int cantidad;
    private long startTime;
    private long endTime;

    public long getDuracionTotal() {
        return endTime - startTime;
    }
}
