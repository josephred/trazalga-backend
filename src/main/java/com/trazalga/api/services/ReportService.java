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

    public List<java.util.Map<String, Object>> getIndicadoresRecolector(Date startDate, Date endDate) {
        return reportRepository.getIndicadoresRecolector(startDate, endDate);
    }

    public java.util.Map<String, Object> getExtraccionVedaMetrics(Date startDate, Date endDate) {
        return reportRepository.getExtraccionVedaMetrics(startDate, endDate);
    }

    public List<java.util.Map<String, Object>> getExtraccionVedaDetalle(Date startDate, Date endDate) {
        return reportRepository.getExtraccionVedaDetalle(startDate, endDate);
    }

    public java.util.Map<String, Object> getResumenGlobal(Date startDate, Date endDate) {
        return reportRepository.getResumenGlobal(startDate, endDate);
    }

    public List<java.util.Map<String, Object>> getVolumenPorEspecie(Date startDate, Date endDate, String perfil) {
        return reportRepository.getVolumenPorEspecie(startDate, endDate, perfil);
    }

    public java.util.Map<String, Object> getTiempoValidacionMetrics(Date startDate, Date endDate) {
        return reportRepository.getTiempoValidacionMetrics(startDate, endDate);
    }

    public List<java.util.Map<String, Object>> getTiempoValidacionDetalle(Date startDate, Date endDate) {
        return reportRepository.getTiempoValidacionDetalle(startDate, endDate);
    }

    public List<com.trazalga.api.dto.TrazabilidadNodoDTO> getTrazabilidad(Integer tipo, Long id) {
        return reportRepository.getTrazabilidad(tipo, id);
    }
}
