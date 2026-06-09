package com.trazalga.api.services;

import com.trazalga.api.dto.ReportDTO;
import com.trazalga.api.repositories.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;

    public List<ReportDTO> getReport(Date fechaInicio, Date fechaFin, Integer tipoReporte, String rut) {
        return reportRepository.generateReport(fechaInicio, fechaFin, tipoReporte, rut);
    }

    public java.util.Map<String, Object> getIndicadoresRecolector(Date startDate, Date endDate) {
        return reportRepository.getIndicadoresRecolector(startDate, endDate);
    }

    public java.util.Map<String, Object> getExtraccionVedaMetrics(Date startDate, Date endDate) {
        return reportRepository.getExtraccionVedaMetrics(startDate, endDate);
    }

    public java.util.Map<String, Object> getResumenGlobal(Date startDate, Date endDate) {
        return reportRepository.getResumenGlobal(startDate, endDate);
    }
}
