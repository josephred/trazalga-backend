package com.trazalga.api.controllers;

import com.trazalga.api.dto.ReportDTO;
import com.trazalga.api.dto.sernapesca.ComboIntDto;
import com.trazalga.api.dto.sernapesca.PescadorDto;
import com.trazalga.api.models.UsuarioModel;
import com.trazalga.api.models.PerfilModel;
import com.trazalga.api.repositories.IPerfilRepository;
import com.trazalga.api.repositories.IUsuarioRepository;
import com.trazalga.api.services.ReportService;
import com.trazalga.api.services.sync.SernapescaApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reportes")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final IUsuarioRepository usuarioRepository;
    private final IPerfilRepository perfilRepository;
    private final SernapescaApiClient sernapescaApiClient;

    @GetMapping
    public ResponseEntity<?> generateReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date fechaFin,
            @RequestParam String tipoReporte,
            @RequestParam(required = false) String rut,
            @RequestParam(required = false) String format,
            @RequestHeader(value = "Accept", required = false) String acceptHeader) {
        
        try {
            Date endDate = fechaFin != null ? fechaFin : new Date();
            
            // Si quieren HTML o se especifica formato HTML
            boolean wantsHtml = (acceptHeader != null && acceptHeader.contains("text/html")) || "html".equalsIgnoreCase(format);
            
            String[] types = tipoReporte.split(",");
            List<Integer> selectedTypes = new ArrayList<>();
            for (String type : types) {
                try {
                    selectedTypes.add(Integer.parseInt(type.trim()));
                } catch (NumberFormatException e) {
                    // Ignorar tipos no válidos
                }
            }

            if (wantsHtml) {
                String htmlReport = generateHtmlReport(fechaInicio, endDate, selectedTypes, rut);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, "text/html; charset=UTF-8")
                        .body(htmlReport);
            } else {
                // Si piden JSON, si es un solo tipo devolvemos una lista plana para compatibilidad con la web
                if (selectedTypes.size() == 1) {
                    List<ReportDTO> report = reportService.getReport(fechaInicio, endDate, selectedTypes.get(0), rut);
                    return ResponseEntity.ok(report);
                } else {
                    // Si son múltiples tipos, devolvemos un Map agrupado por tipo
                    Map<String, List<ReportDTO>> reports = new LinkedHashMap<>();
                    for (Integer t : selectedTypes) {
                        List<ReportDTO> report = reportService.getReport(fechaInicio, endDate, t, rut);
                        reports.put(getReportLabel(t), report);
                    }
                    return ResponseEntity.ok(reports);
                }
            }
        } catch (Exception e) {
            System.err.println("Error generando reporte: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error interno: " + e.getMessage());
        }
    }

    private String getReportLabel(Integer tipo) {
        switch (tipo) {
            case 1: return "Recolector";
            case 2: return "Armador";
            case 3: return "Área de Manejo";
            case 4: return "Comercializador";
            case 5: return "Planta Abastecimiento";
            case 6: return "Planta Producción";
            case 7: return "Planta Destino";
            default: return "Desconocido";
        }
    }

    private String formatDate(Date date) {
        if (date == null) return "-";
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        return sdf.format(date);
    }

    private String generateHtmlReport(Date fechaInicio, Date fechaFin, List<Integer> selectedTypes, String rut) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"es\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>Consola de Reportes - Trazalga</title>\n");
        html.append("    <link href=\"https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap\" rel=\"stylesheet\">\n");
        html.append("    <style>\n");
        html.append("        :root {\n");
        html.append("            --primary: #1e3a8a;\n");
        html.append("            --primary-light: #3b82f6;\n");
        html.append("            --bg: #f8fafc;\n");
        html.append("            --text: #0f172a;\n");
        html.append("            --text-secondary: #475569;\n");
        html.append("            --border: #e2e8f0;\n");
        html.append("            --card-bg: #ffffff;\n");
        html.append("        }\n");
        html.append("        * { box-sizing: border-box; margin: 0; padding: 0; }\n");
        html.append("        body {\n");
        html.append("            font-family: 'Inter', sans-serif;\n");
        html.append("            background-color: var(--bg);\n");
        html.append("            color: var(--text);\n");
        html.append("            line-height: 1.5;\n");
        html.append("            padding: 40px 20px;\n");
        html.append("        }\n");
        html.append("        .container {\n");
        html.append("            max-width: 1400px;\n");
        html.append("            margin: 0 auto;\n");
        html.append("        }\n");
        html.append("        .header {\n");
        html.append("            background: linear-gradient(135deg, #1e3a8a 0%, #0f172a 100%);\n");
        html.append("            color: white;\n");
        html.append("            padding: 30px 40px;\n");
        html.append("            border-radius: 16px;\n");
        html.append("            margin-bottom: 30px;\n");
        html.append("            box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1);\n");
        html.append("        }\n");
        html.append("        .header h1 { font-size: 1.875rem; font-weight: 700; margin-bottom: 8px; }\n");
        html.append("        .header p { font-size: 0.95rem; opacity: 0.85; }\n");
        html.append("        .summary-grid {\n");
        html.append("            display: grid;\n");
        html.append("            grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));\n");
        html.append("            gap: 20px;\n");
        html.append("            margin-bottom: 30px;\n");
        html.append("        }\n");
        html.append("        .card {\n");
        html.append("            background-color: var(--card-bg);\n");
        html.append("            border: 1px solid var(--border);\n");
        html.append("            border-radius: 12px;\n");
        html.append("            padding: 24px;\n");
        html.append("            box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05);\n");
        html.append("            display: flex;\n");
        html.append("            flex-direction: column;\n");
        html.append("            position: relative;\n");
        html.append("            overflow: hidden;\n");
        html.append("        }\n");
        html.append("        .card::before {\n");
        html.append("            content: '';\n");
        html.append("            position: absolute;\n");
        html.append("            top: 0; left: 0; width: 4px; height: 100%;\n");
        html.append("            background-color: var(--primary-light);\n");
        html.append("        }\n");
        html.append("        .card.c-1::before { background-color: #0d9488; }\n");
        html.append("        .card.c-2::before { background-color: #4f46e5; }\n");
        html.append("        .card.c-3::before { background-color: #059669; }\n");
        html.append("        .card.c-4::before { background-color: #7c3aed; }\n");
        html.append("        .card.c-5::before { background-color: #d97706; }\n");
        html.append("        .card.c-6::before { background-color: #ea580c; }\n");
        html.append("        .card.c-7::before { background-color: #e11d48; }\n");
        html.append("        .card-title { font-size: 0.875rem; font-weight: 600; color: var(--text-secondary); margin-bottom: 8px; text-transform: uppercase; letter-spacing: 0.05em; }\n");
        html.append("        .card-value { font-size: 2rem; font-weight: 700; color: var(--text); margin-bottom: 4px; }\n");
        html.append("        .card-desc { font-size: 0.85rem; color: var(--text-secondary); }\n");
        html.append("        .section-card {\n");
        html.append("            background-color: var(--card-bg);\n");
        html.append("            border: 1px solid var(--border);\n");
        html.append("            border-radius: 12px;\n");
        html.append("            padding: 30px;\n");
        html.append("            margin-bottom: 40px;\n");
        html.append("            box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05);\n");
        html.append("        }\n");
        html.append("        .section-header {\n");
        html.append("            display: flex;\n");
        html.append("            align-items: center;\n");
        html.append("            justify-content: space-between;\n");
        html.append("            border-bottom: 2px solid var(--border);\n");
        html.append("            padding-bottom: 15px;\n");
        html.append("            margin-bottom: 20px;\n");
        html.append("        }\n");
        html.append("        .section-title {\n");
        html.append("            font-size: 1.25rem;\n");
        html.append("            font-weight: 700;\n");
        html.append("            color: var(--text);\n");
        html.append("            display: flex;\n");
        html.append("            align-items: center;\n");
        html.append("            gap: 10px;\n");
        html.append("        }\n");
        html.append("        .badge-count {\n");
        html.append("            background-color: #f1f5f9;\n");
        html.append("            color: var(--text-secondary);\n");
        html.append("            font-size: 0.8rem;\n");
        html.append("            font-weight: 600;\n");
        html.append("            padding: 4px 10px;\n");
        html.append("            border-radius: 20px;\n");
        html.append("            border: 1px solid var(--border);\n");
        html.append("        }\n");
        html.append("        .table-responsive {\n");
        html.append("            overflow-x: auto;\n");
        html.append("            overflow-y: auto;\n");
        html.append("            max-height: 580px;\n");
        html.append("            margin-top: 15px;\n");
        html.append("            border: 1px solid var(--border);\n");
        html.append("            border-radius: 8px;\n");
        html.append("        }\n");
        html.append("        thead th {\n");
        html.append("            position: sticky;\n");
        html.append("            top: 0;\n");
        html.append("            z-index: 10;\n");
        html.append("            background-color: #f8fafc;\n");
        html.append("            box-shadow: 0 1px 0 var(--border);\n");
        html.append("        }\n");
        html.append("        .pagination-container {\n");
        html.append("            display: flex;\n");
        html.append("            align-items: center;\n");
        html.append("            justify-content: space-between;\n");
        html.append("            padding: 15px 0 0 0;\n");
        html.append("            margin-top: 15px;\n");
        html.append("            border-top: 1px solid var(--border);\n");
        html.append("        }\n");
        html.append("        .pagination-info {\n");
        html.append("            font-size: 0.85rem;\n");
        html.append("            color: var(--text-secondary);\n");
        html.append("        }\n");
        html.append("        .pagination-buttons {\n");
        html.append("            display: flex;\n");
        html.append("            gap: 5px;\n");
        html.append("        }\n");
        html.append("        .page-btn {\n");
        html.append("            background-color: var(--card-bg);\n");
        html.append("            border: 1px solid var(--border);\n");
        html.append("            color: var(--text-secondary);\n");
        html.append("            padding: 6px 12px;\n");
        html.append("            font-size: 0.85rem;\n");
        html.append("            font-weight: 500;\n");
        html.append("            border-radius: 6px;\n");
        html.append("            cursor: pointer;\n");
        html.append("            transition: all 0.2s ease;\n");
        html.append("        }\n");
        html.append("        .page-btn:hover:not(:disabled) {\n");
        html.append("            border-color: var(--primary-light);\n");
        html.append("            color: var(--primary-light);\n");
        html.append("        }\n");
        html.append("        .page-btn.active {\n");
        html.append("            background-color: var(--primary);\n");
        html.append("            color: white;\n");
        html.append("            border-color: var(--primary);\n");
        html.append("        }\n");
        html.append("        .page-btn:disabled {\n");
        html.append("            opacity: 0.5;\n");
        html.append("            cursor: not-allowed;\n");
        html.append("        }\n");
        html.append("        table {\n");
        html.append("            width: 100%;\n");
        html.append("            border-collapse: collapse;\n");
        html.append("            text-align: left;\n");
        html.append("        }\n");
        html.append("        th {\n");
        html.append("            background-color: #f8fafc;\n");
        html.append("            color: var(--text-secondary);\n");
        html.append("            font-size: 0.75rem;\n");
        html.append("            font-weight: 600;\n");
        html.append("            text-transform: uppercase;\n");
        html.append("            letter-spacing: 0.05em;\n");
        html.append("            padding: 12px 16px;\n");
        html.append("            border-bottom: 1px solid var(--border);\n");
        html.append("            white-space: nowrap;\n");
        html.append("        }\n");
        html.append("        td {\n");
        html.append("            padding: 14px 16px;\n");
        html.append("            border-bottom: 1px solid #f1f5f9;\n");
        html.append("            font-size: 0.875rem;\n");
        html.append("            color: #334155;\n");
        html.append("            white-space: nowrap;\n");
        html.append("        }\n");
        html.append("        tr:hover td {\n");
        html.append("            background-color: #f8fafc;\n");
        html.append("        }\n");
        html.append("        .empty-state {\n");
        html.append("            text-align: center;\n");
        html.append("            padding: 40px 20px;\n");
        html.append("            color: var(--text-secondary);\n");
        html.append("            font-size: 0.9rem;\n");
        html.append("        }\n");
        html.append("        .btn-map {\n");
        html.append("            display: inline-flex;\n");
        html.append("            align-items: center;\n");
        html.append("            gap: 6px;\n");
        html.append("            color: var(--primary-light);\n");
        html.append("            text-decoration: none;\n");
        html.append("            font-weight: 500;\n");
        html.append("        }\n");
        html.append("        .btn-map:hover { text-decoration: underline; }\n");
        html.append("        .tabs-container {\n");
        html.append("            display: flex;\n");
        html.append("            gap: 8px;\n");
        html.append("            border-bottom: 2px solid var(--border);\n");
        html.append("            margin-bottom: 30px;\n");
        html.append("            overflow-x: auto;\n");
        html.append("            scrollbar-width: none;\n");
        html.append("        }\n");
        html.append("        .tabs-container::-webkit-scrollbar { display: none; }\n");
        html.append("        .tab-btn {\n");
        html.append("            background: none;\n");
        html.append("            border: none;\n");
        html.append("            padding: 12px 20px;\n");
        html.append("            font-size: 0.95rem;\n");
        html.append("            font-weight: 600;\n");
        html.append("            color: var(--text-secondary);\n");
        html.append("            cursor: pointer;\n");
        html.append("            border-bottom: 3px solid transparent;\n");
        html.append("            transition: all 0.2s ease;\n");
        html.append("            white-space: nowrap;\n");
        html.append("            display: inline-flex;\n");
        html.append("            align-items: center;\n");
        html.append("            gap: 8px;\n");
        html.append("        }\n");
        html.append("        .tab-btn:hover {\n");
        html.append("            color: var(--primary-light);\n");
        html.append("        }\n");
        html.append("        .tab-btn.active {\n");
        html.append("            color: var(--primary);\n");
        html.append("            border-bottom-color: var(--primary);\n");
        html.append("        }\n");
        html.append("        .tab-panel {\n");
        html.append("            display: none;\n");
        html.append("        }\n");
        html.append("        .tab-panel.active {\n");
        html.append("            display: block;\n");
        html.append("            animation: fadeIn 0.2s ease-in-out;\n");
        html.append("        }\n");
        html.append("        @keyframes fadeIn {\n");
        html.append("            from { opacity: 0; transform: translateY(4px); }\n");
        html.append("            to { opacity: 1; transform: translateY(0); }\n");
        html.append("        }\n");
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div class=\"container\">\n");
        
        // Header
        html.append("        <div class=\"header\">\n");
        html.append("            <h1>Consola de Reportes - Trazalga</h1>\n");
        html.append("            <p>Periodo: <strong>").append(formatDate(fechaInicio)).append("</strong> al <strong>").append(formatDate(fechaFin)).append("</strong></p>\n");
        if (rut != null && !rut.isEmpty()) {
            html.append("            <p style=\"margin-top: 4px;\">Filtrado por RUT del Emisor: <strong>").append(rut).append("</strong></p>\n");
        }
        html.append("        </div>\n");
        
        // Query data for all selected profiles
        Map<Integer, List<ReportDTO>> reports = new LinkedHashMap<>();
        int totalDeclaraciones = 0;
        BigDecimal totalVolume = BigDecimal.ZERO;
        
        for (Integer type : selectedTypes) {
            try {
                List<ReportDTO> list = reportService.getReport(fechaInicio, fechaFin, type, rut);
                reports.put(type, list);
                totalDeclaraciones += list.size();
                for (ReportDTO d : list) {
                    if (d.getCantidad() != null) {
                        totalVolume = totalVolume.add(d.getCantidad());
                    }
                }
            } catch (Exception ex) {
                System.err.println("Error obteniendo datos para tipo " + type + ": " + ex.getMessage());
            }
        }
        
        // Primera fila: tarjetas por perfil (Recolector, Armador, Área, Comercializador, etc.)
        html.append("        <div class=\"summary-grid\">\n");

        for (Integer type : selectedTypes) {
            List<ReportDTO> list = reports.getOrDefault(type, new ArrayList<>());
            double vol = list.stream()
                .mapToDouble(d -> d.getCantidad() != null ? d.getCantidad().doubleValue() : 0.0)
                .sum();

            html.append("            <div class=\"card c-").append(type).append("\">\n");
            html.append("                <span class=\"card-title\">").append(getReportLabel(type)).append("</span>\n");
            html.append("                <span class=\"card-value\">").append(list.size()).append("</span>\n");
            html.append("                <span class=\"card-desc\">").append(String.format("%,.1f", vol)).append(" Kg totales</span>\n");
            html.append("            </div>\n");
        }

        html.append("        </div>\n");

        // Segunda fila: totales globales (Total Declaraciones y Volumen Total)
        html.append("        <div class=\"summary-grid\">\n");

        html.append("            <div class=\"card\">\n");
        html.append("                <span class=\"card-title\">Total Declaraciones</span>\n");
        html.append("                <span class=\"card-value\">").append(totalDeclaraciones).append("</span>\n");
        html.append("                <span class=\"card-desc\">En el periodo especificado</span>\n");
        html.append("            </div>\n");

        html.append("            <div class=\"card\">\n");
        html.append("                <span class=\"card-title\">Volumen Total</span>\n");
        html.append("                <span class=\"card-value\">").append(String.format("%,.0f", totalVolume.doubleValue())).append(" Kg</span>\n");
        html.append("                <span class=\"card-desc\">Suma de cantidades declaradas</span>\n");
        html.append("            </div>\n");

        html.append("        </div>\n");
        
        // Tabs container
        html.append("        <div class=\"tabs-container\">\n");
        boolean isFirstTab = true;
        for (Integer type : selectedTypes) {
            List<ReportDTO> list = reports.getOrDefault(type, new ArrayList<>());
            String activeClass = isFirstTab ? " active" : "";
            html.append("            <button class=\"tab-btn").append(activeClass).append("\" onclick=\"showTab(event, ").append(type).append(")\">\n");
            html.append("                <span>").append(getReportLabel(type)).append("</span>\n");
            html.append("                <span class=\"badge-count\">").append(list.size()).append("</span>\n");
            html.append("            </button>\n");
            isFirstTab = false;
        }
        html.append("        </div>\n");

        // Tables for each profile (tab panels)
        isFirstTab = true;
        for (Integer type : selectedTypes) {
            List<ReportDTO> list = reports.getOrDefault(type, new ArrayList<>());
            String activeClass = isFirstTab ? " active" : "";
            String displayStyle = isFirstTab ? "block" : "none";
            
            html.append("        <div id=\"tab-panel-").append(type).append("\" class=\"tab-panel").append(activeClass).append("\" style=\"display: ").append(displayStyle).append(";\">\n");
            html.append("            <div class=\"section-card\">\n");
            html.append("                <div class=\"section-header\">\n");
            html.append("                    <h2 class=\"section-title\">\n");
            html.append("                        <span>Detalle de ").append(getReportLabel(type)).append("</span>\n");
            html.append("                        <span class=\"badge-count\">").append(list.size()).append(" registros</span>\n");
            html.append("                    </h2>\n");
            html.append("                </div>\n");
            
            if (list.isEmpty()) {
                html.append("                <div class=\"empty-state\">\n");
                html.append("                    <p>No se encontraron declaraciones registradas para este perfil en el periodo seleccionado.</p>\n");
                html.append("                </div>\n");
            } else {
                html.append("                <div class=\"table-responsive\">\n");
                html.append("                    <table>\n");
                html.append("                        <thead>\n");
                html.append("                            <tr>\n");
                html.append("                                <th>Folio</th>\n");
                html.append("                                <th>Fecha / Hora</th>\n");
                html.append("                                <th>Emisor (RUT)</th>\n");
                html.append("                                <th>Receptor (RUT)</th>\n");
                html.append("                                <th>Cantidad</th>\n");
                html.append("                                <th>Especie</th>\n");
                
                if (type <= 5) {
                    html.append("                                <th>Composición</th>\n");
                    html.append("                                <th>Humedad</th>\n");
                }
                
                html.append("                                <th>Georreferencia</th>\n");
                
                if (type == 1) {
                    html.append("                                <th>Folio Comercializador</th>\n");
                }
                
                html.append("                            </tr>\n");
                html.append("                        </thead>\n");
                html.append("                        <tbody>\n");
                
                for (ReportDTO d : list) {
                    html.append("                            <tr>\n");
                    html.append("                                <td><strong>").append(d.getFolio()).append("</strong></td>\n");
                    html.append("                                <td>").append(formatDate(d.getFecha())).append(" ").append(d.getHora() != null ? d.getHora() : "").append("</td>\n");
                    html.append("                                <td>").append(d.getEmisorNombre() != null ? d.getEmisorNombre() : "-").append("<br><small style=\"color: #64748b;\">").append(d.getEmisorRut() != null ? d.getEmisorRut() : "").append("</small></td>\n");
                    html.append("                                <td>").append(d.getReceptorNombre() != null && !d.getReceptorNombre().isEmpty() ? d.getReceptorNombre() : "-").append("<br><small style=\"color: #64748b;\">").append(d.getReceptorRut() != null ? d.getReceptorRut() : "").append("</small></td>\n");
                    html.append("                                <td><strong>").append(d.getCantidad() != null ? String.format("%,.1f", d.getCantidad().doubleValue()) : "0").append(" Kg</strong></td>\n");
                    html.append("                                <td>").append(d.getEspecie() != null ? d.getEspecie() : "-").append("</td>\n");
                    
                    if (type <= 5) {
                        html.append("                                <td>").append(d.getComposicion() != null ? d.getComposicion() : "-").append("</td>\n");
                        html.append("                                <td>").append(d.getEstadoHumedad() != null ? d.getEstadoHumedad() : "-").append("</td>\n");
                    }
                    
                    html.append("                                <td>\n");
                    if (d.getLatitud() != null && d.getLongitud() != null) {
                        html.append("                                    <a href=\"https://www.google.com/maps?q=").append(d.getLatitud()).append(",").append(d.getLongitud()).append("\" target=\"_blank\" class=\"btn-map\">\n");
                        html.append("                                        <svg width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z\"></path><circle cx=\"12\" cy=\"10\" r=\"3\"></circle></svg>\n");
                        html.append("                                        Ver ubicación\n");
                        html.append("                                    </a>\n");
                    } else {
                        html.append("                                    -\n");
                    }
                    html.append("                                </td>\n");
                    
                    if (type == 1) {
                        html.append("                                <td>").append(d.getFolioRelacionado() != null && !d.getFolioRelacionado().isEmpty() ? d.getFolioRelacionado() : "-").append("</td>\n");
                    }
                    
                    html.append("                            </tr>\n");
                }
                
                html.append("                        </tbody>\n");
                html.append("                    </table>\n");
                html.append("                </div>\n");
            }
            
            html.append("            </div>\n");
            html.append("        </div>\n");
            isFirstTab = false;
        }
        
        html.append("    </div>\n");
        html.append("    <script>\n");
        html.append("        function showTab(event, typeId) {\n");
        html.append("            var panels = document.querySelectorAll('.tab-panel');\n");
        html.append("            panels.forEach(function(panel) {\n");
        html.append("                panel.style.display = 'none';\n");
        html.append("                panel.classList.remove('active');\n");
        html.append("            });\n");
        html.append("            var activePanel = document.getElementById('tab-panel-' + typeId);\n");
        html.append("            if (activePanel) {\n");
        html.append("                activePanel.style.display = 'block';\n");
        html.append("                activePanel.classList.add('active');\n");
        html.append("            }\n");
        html.append("            var buttons = document.querySelectorAll('.tab-btn');\n");
        html.append("            buttons.forEach(function(btn) {\n");
        html.append("                btn.classList.remove('active');\n");
        html.append("            });\n");
        html.append("            event.currentTarget.classList.add('active');\n");
        html.append("        }\n");
        html.append("\n");
        html.append("        var PAGE_SIZE = 15;\n");
        html.append("        var paginationStates = {};\n");
        html.append("\n");
        html.append("        function initPagination() {\n");
        html.append("            var panels = document.querySelectorAll('.tab-panel');\n");
        html.append("            panels.forEach(function(panel) {\n");
        html.append("                var table = panel.querySelector('table');\n");
        html.append("                if (!table) return;\n");
        html.append("                \n");
        html.append("                var rows = Array.from(table.querySelectorAll('tbody tr'));\n");
        html.append("                var totalRows = rows.length;\n");
        html.append("                if (totalRows <= PAGE_SIZE) return;\n");
        html.append("\n");
        html.append("                var panelId = panel.id;\n");
        html.append("                paginationStates[panelId] = {\n");
        html.append("                    currentPage: 1,\n");
        html.append("                    rows: rows,\n");
        html.append("                    totalPages: Math.ceil(totalRows / PAGE_SIZE)\n");
        html.append("                };\n");
        html.append("\n");
        html.append("                var tableResponsive = panel.querySelector('.table-responsive');\n");
        html.append("                var pagContainer = document.createElement('div');\n");
        html.append("                pagContainer.className = 'pagination-container';\n");
        html.append("                \n");
        html.append("                var pagInfo = document.createElement('div');\n");
        html.append("                pagInfo.className = 'pagination-info';\n");
        html.append("                \n");
        html.append("                var pagButtons = document.createElement('div');\n");
        html.append("                pagButtons.className = 'pagination-buttons';\n");
        html.append("\n");
        html.append("                pagContainer.appendChild(pagInfo);\n");
        html.append("                pagContainer.appendChild(pagButtons);\n");
        html.append("                tableResponsive.after(pagContainer);\n");
        html.append("\n");
        html.append("                renderPage(panelId);\n");
        html.append("            });\n");
        html.append("        }\n");
        html.append("\n");
        html.append("        function renderPage(panelId) {\n");
        html.append("            var state = paginationStates[panelId];\n");
        html.append("            if (!state) return;\n");
        html.append("\n");
        html.append("            var startIdx = (state.currentPage - 1) * PAGE_SIZE;\n");
        html.append("            var endIdx = startIdx + PAGE_SIZE;\n");
        html.append("\n");
        html.append("            state.rows.forEach(function(row, idx) {\n");
        html.append("                if (idx >= startIdx && idx < endIdx) {\n");
        html.append("                    row.style.display = '';\n");
        html.append("                } else {\n");
        html.append("                    row.style.display = 'none';\n");
        html.append("                }\n");
        html.append("            });\n");
        html.append("\n");
        html.append("            var panel = document.getElementById(panelId);\n");
        html.append("            var pagInfo = panel.querySelector('.pagination-info');\n");
        html.append("            var totalRows = state.rows.length;\n");
        html.append("            var actualEnd = Math.min(endIdx, totalRows);\n");
        html.append("            pagInfo.innerHTML = 'Mostrando <strong>' + (startIdx + 1) + '-' + actualEnd + '</strong> de <strong>' + totalRows + '</strong> registros';\n");
        html.append("\n");
        html.append("            var pagButtons = panel.querySelector('.pagination-buttons');\n");
        html.append("            pagButtons.innerHTML = '';\n");
        html.append("\n");
        html.append("            var prevBtn = document.createElement('button');\n");
        html.append("            prevBtn.className = 'page-btn';\n");
        html.append("            prevBtn.innerHTML = '&laquo;';\n");
        html.append("            prevBtn.disabled = state.currentPage === 1;\n");
        html.append("            prevBtn.onclick = function() {\n");
        html.append("                state.currentPage--;\n");
        html.append("                renderPage(panelId);\n");
        html.append("            };\n");
        html.append("            pagButtons.appendChild(prevBtn);\n");
        html.append("\n");
        html.append("            var maxVisible = 5;\n");
        html.append("            var startPage = Math.max(1, state.currentPage - 2);\n");
        html.append("            var endPage = Math.min(state.totalPages, startPage + maxVisible - 1);\n");
        html.append("            if (endPage - startPage < maxVisible - 1) {\n");
        html.append("                startPage = Math.max(1, endPage - maxVisible + 1);\n");
        html.append("            }\n");
        html.append("\n");
        html.append("            for (var i = startPage; i <= endPage; i++) {\n");
        html.append("                (function(pageNum) {\n");
        html.append("                    var btn = document.createElement('button');\n");
        html.append("                    btn.className = 'page-btn' + (pageNum === state.currentPage ? ' active' : '');\n");
        html.append("                    btn.innerText = pageNum;\n");
        html.append("                    btn.onclick = function() {\n");
        html.append("                        state.currentPage = pageNum;\n");
        html.append("                        renderPage(panelId);\n");
        html.append("                    };\n");
        html.append("                    pagButtons.appendChild(btn);\n");
        html.append("                })(i);\n");
        html.append("            }\n");
        html.append("\n");
        html.append("            var nextBtn = document.createElement('button');\n");
        html.append("            nextBtn.className = 'page-btn';\n");
        html.append("            nextBtn.innerHTML = '&raquo;';\n");
        html.append("            nextBtn.disabled = state.currentPage === state.totalPages;\n");
        html.append("            nextBtn.onclick = function() {\n");
        html.append("                state.currentPage++;\n");
        html.append("                renderPage(panelId);\n");
        html.append("            };\n");
        html.append("            pagButtons.appendChild(nextBtn);\n");
        html.append("        }\n");
        html.append("\n");
        html.append("        document.addEventListener('DOMContentLoaded', initPagination);\n");
        html.append("    </script>\n");
        html.append("</body>\n");
        html.append("</html>");
        
        return html.toString();
    }

    @GetMapping("/indicadores-recolector")
    public ResponseEntity<?> getIndicadoresRecolector(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date endDate) {
        try {
            return ResponseEntity.ok(reportService.getIndicadoresRecolector(startDate, endDate));
        } catch (Exception e) {
            System.err.println("Error obteniendo indicadores de recolector: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error interno: " + e.getMessage());
        }
    }
    @GetMapping("/extraccion-veda")
    public ResponseEntity<?> getExtraccionVedaMetrics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date endDate) {
        try {
            return ResponseEntity.ok(reportService.getExtraccionVedaMetrics(startDate, endDate));
        } catch (Exception e) {
            System.err.println("Error obteniendo indicadores de veda: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error interno: " + e.getMessage());
        }
    }

    @GetMapping("/extraccion-veda-detalle")
    public ResponseEntity<?> getExtraccionVedaDetalle(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date endDate) {
        try {
            return ResponseEntity.ok(reportService.getExtraccionVedaDetalle(startDate, endDate));
        } catch (Exception e) {
            System.err.println("Error obteniendo detalle de veda: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error interno: " + e.getMessage());
        }
    }

    @GetMapping("/resumen-global")
    public ResponseEntity<?> getResumenGlobal(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date endDate) {
        try {
            return ResponseEntity.ok(reportService.getResumenGlobal(startDate, endDate));
        } catch (Exception e) {
            System.err.println("Error obteniendo resumen global: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error interno: " + e.getMessage());
        }
    }

    @GetMapping("/casos-abiertos")
    public ResponseEntity<?> getCasosAbiertosDetalle(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date endDate) {
        try {
            return ResponseEntity.ok(reportService.getCasosAbiertosDetalle(startDate, endDate));
        } catch (Exception e) {
            System.err.println("Error obteniendo detalle de casos abiertos: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error interno: " + e.getMessage());
        }
    }

    @GetMapping("/variacion-peso")
    public ResponseEntity<?> getVariacionPeso(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date endDate,
            @RequestParam(required = false) Double umbral) {
        try {
            return ResponseEntity.ok(reportService.getVariacionPesoMetrics(startDate, endDate, umbral));
        } catch (Exception e) {
            System.err.println("Error obteniendo variación de peso: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error interno: " + e.getMessage());
        }
    }

    @GetMapping("/variacion-peso-detalle")
    public ResponseEntity<?> getVariacionPesoDetalle(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date endDate) {
        try {
            return ResponseEntity.ok(reportService.getVariacionPesoDetalle(startDate, endDate));
        } catch (Exception e) {
            System.err.println("Error obteniendo detalle de variación de peso: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error interno: " + e.getMessage());
        }
    }

    @GetMapping("/tiempo-validacion")
    public ResponseEntity<?> getTiempoValidacion(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date endDate) {
        try {
            return ResponseEntity.ok(reportService.getTiempoValidacionMetrics(startDate, endDate));
        } catch (Exception e) {
            System.err.println("Error obteniendo tiempo de validación: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error interno: " + e.getMessage());
        }
    }

    @GetMapping("/tiempo-validacion-detalle")
    public ResponseEntity<?> getTiempoValidacionDetalle(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date endDate) {
        try {
            return ResponseEntity.ok(reportService.getTiempoValidacionDetalle(startDate, endDate));
        } catch (Exception e) {
            System.err.println("Error obteniendo detalle de tiempo de validación: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error interno: " + e.getMessage());
        }
    }

    @GetMapping("/volumen-por-especie")
    public ResponseEntity<?> getVolumenPorEspecie(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date endDate,
            @RequestParam(required = false, defaultValue = "TODOS") String perfil) {
        try {
            return ResponseEntity.ok(reportService.getVolumenPorEspecie(startDate, endDate, perfil));
        } catch (Exception e) {
            System.err.println("Error obteniendo volumen por especie: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error interno: " + e.getMessage());
        }
    }

    @GetMapping("/trazabilidad/{tipo}/{id}")
    public ResponseEntity<?> getTrazabilidad(
            @PathVariable Integer tipo,
            @PathVariable Long id) {
        try {
            com.trazalga.api.dto.TrazabilidadResponseDTO res = reportService.getTrazabilidad(tipo, id);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            System.err.println("Error obteniendo trazabilidad: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error interno: " + e.getMessage());
        }
    }

    @GetMapping(value = "/usuarios", produces = MediaType.TEXT_HTML_VALUE)
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<String> getUsuariosHtml(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        try {
            List<UsuarioModel> todos = usuarioRepository.findAll();
            List<UsuarioModel> filtered = new java.util.ArrayList<>();
            for (UsuarioModel u : todos) {
                if (u.getId() > 4000) {
                    filtered.add(u);
                }
            }
            filtered.sort((u1, u2) -> u1.getId().compareTo(u2.getId()));

            int total = filtered.size();
            int fromIndex = page * size;
            int toIndex = Math.min(fromIndex + size, total);

            List<UsuarioModel> usuarios = new java.util.ArrayList<>();
            if (fromIndex < total) {
                usuarios = filtered.subList(fromIndex, toIndex);
            }
            
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html>\n");
            html.append("<html lang=\"es\">\n");
            html.append("<head>\n");
            html.append("    <meta charset=\"UTF-8\">\n");
            html.append("    <title>Listado de Usuarios</title>\n");
            html.append("    <link href=\"https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap\" rel=\"stylesheet\">\n");
            html.append("    <style>\n");
            html.append("        body {\n");
            html.append("            font-family: 'Inter', sans-serif;\n");
            html.append("            background-color: #f8fafc;\n");
            html.append("            color: #0f172a;\n");
            html.append("            padding: 40px 20px;\n");
            html.append("        }\n");
            html.append("        .container {\n");
            html.append("            max-width: 1200px;\n");
            html.append("            margin: 0 auto;\n");
            html.append("            background: white;\n");
            html.append("            padding: 30px;\n");
            html.append("            border-radius: 12px;\n");
            html.append("            box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);\n");
            html.append("        }\n");
            html.append("        h2 { color: #1e3a8a; margin-bottom: 20px; }\n");
            html.append("        table { width: 100%; border-collapse: collapse; margin-top: 20px; }\n");
            html.append("        th, td { border-bottom: 1px solid #e2e8f0; padding: 12px 15px; text-align: left; }\n");
            html.append("        th { background-color: #f1f5f9; color: #475569; font-weight: 600; text-transform: uppercase; font-size: 0.85rem; }\n");
            html.append("        tr:hover td { background-color: #f8fafc; }\n");
            html.append("        .status-badge {\n");
            html.append("            padding: 4px 8px;\n");
            html.append("            border-radius: 12px;\n");
            html.append("            font-size: 0.75rem;\n");
            html.append("            font-weight: 600;\n");
            html.append("        }\n");
            html.append("        .status-activo { background-color: #dcfce7; color: #166534; }\n");
            html.append("        .status-inactivo { background-color: #fee2e2; color: #991b1b; }\n");
            html.append("        .pagination {\n");
            html.append("            margin-top: 25px;\n");
            html.append("            display: flex;\n");
            html.append("            justify-content: space-between;\n");
            html.append("            align-items: center;\n");
            html.append("            padding: 10px 0;\n");
            html.append("        }\n");
            html.append("        .pagination a, .pagination span {\n");
            html.append("            padding: 8px 16px;\n");
            html.append("            text-decoration: none;\n");
            html.append("            background-color: #e2e8f0;\n");
            html.append("            color: #1e293b;\n");
            html.append("            border-radius: 6px;\n");
            html.append("            font-weight: 500;\n");
            html.append("        }\n");
            html.append("        .pagination a:hover {\n");
            html.append("            background-color: #cbd5e1;\n");
            html.append("        }\n");
            html.append("        .pagination .disabled {\n");
            html.append("            color: #94a3b8;\n");
            html.append("            background-color: #f1f5f9;\n");
            html.append("            cursor: not-allowed;\n");
            html.append("        }\n");
            html.append("    </style>\n");
            html.append("</head>\n");
            html.append("<body>\n");
            html.append("    <div class=\"container\">\n");
            html.append("        <h2>Listado de Usuarios de la Base de Datos</h2>\n");
            html.append("        <table>\n");
            html.append("            <thead>\n");
            html.append("                <tr>\n");
            html.append("                    <th>ID</th>\n");
            html.append("                    <th>RUT</th>\n");
            html.append("                    <th>Nombres</th>\n");
            html.append("                    <th>Apellidos</th>\n");
            html.append("                    <th>Correo</th>\n");
            html.append("                    <th>Perfil Actual</th>\n");
            html.append("                    <th>Estado</th>\n");
            html.append("                    <th>Categorías Sernapesca</th>\n");
            html.append("                    <th>Perfil Sugerido (Sernapesca)</th>\n");
            html.append("                </tr>\n");
            html.append("            </thead>\n");
            html.append("            <tbody>\n");
            
            java.util.List<String> rutsNoMapeados = new java.util.ArrayList<>();
            for (UsuarioModel u : usuarios) {
                String perfil = (u.getPerfil() != null) ? u.getPerfil().getNombre() : "-";
                String estado = (u.getEstado() != null) ? u.getEstado() : "INACTIVO";
                String statusClass = estado.equalsIgnoreCase("ACTIVO") ? "status-activo" : "status-inactivo";
                
                String categoriasStr = "-";
                String perfilSugeridoStr = "-";
                
                Integer rutInt = extractRutNumber(u.getRut());
                if (rutInt != null) {
                    // Delay of 250ms to prevent Sernapesca rate-limiting
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    
                    PescadorDto pescador = sernapescaApiClient.getPescadorPorRut(rutInt);
                    if (pescador != null && pescador.getCategorias() != null && !pescador.getCategorias().isEmpty()) {
                        StringBuilder catBuilder = new StringBuilder();
                        for (ComboIntDto cat : pescador.getCategorias()) {
                            catBuilder.append("<span class=\"status-badge\" style=\"background:#e0e7ff; color:#3730a3; margin-right:4px; margin-bottom:4px; display:inline-block;\">")
                                      .append(cat.getValor()).append("</span>");
                        }
                        categoriasStr = catBuilder.toString();
                        
                        Integer pId = determinePerfilSugerido(pescador.getCategorias());
                        if (pId != null) {
                            perfilSugeridoStr = "<span class=\"status-badge\" style=\"background:#fef3c7; color:#92400e;\">Perfil " + pId + "</span>";
                            System.out.println("RUT: " + u.getRut() + " (ID: " + u.getId() + ") -> Sugiere Perfil: " + pId);
                            PerfilModel nuevoPerfil = perfilRepository.findById(Long.valueOf(pId)).orElse(null);
                            if (nuevoPerfil != null) {
                                System.out.println("Perfil encontrado en BD: " + nuevoPerfil.getNombre() + " (ID: " + nuevoPerfil.getId() + ")");
                                Long actualPerfilId = (u.getPerfil() != null) ? u.getPerfil().getId() : null;
                                System.out.println("Perfil actual de usuario en Java: " + actualPerfilId);
                                if (actualPerfilId == null || !actualPerfilId.equals(nuevoPerfil.getId())) {
                                    System.out.println("Actualizando perfil de " + u.getRut() + " a " + nuevoPerfil.getNombre());
                                    u.setPerfil(nuevoPerfil);
                                    usuarioRepository.save(u);
                                    perfil = nuevoPerfil.getNombre();
                                } else {
                                    System.out.println("El usuario ya tiene ese perfil.");
                                }
                            } else {
                                System.out.println("ALERTA: El perfil " + pId + " no existe en la base de datos.");
                            }
                        } else {
                            rutsNoMapeados.add(u.getRut());
                            perfilSugeridoStr = "<span class=\"status-badge\" style=\"background:#fee2e2; color:#991b1b;\">No Mapeado</span>";
                        }
                    }
                }
                
                html.append("                <tr>\n");
                html.append("                    <td>").append(u.getId()).append("</td>\n");
                html.append("                    <td><strong>").append(u.getRut()).append("</strong></td>\n");
                html.append("                    <td>").append(u.getNombres() != null ? u.getNombres() : "").append("</td>\n");
                html.append("                    <td>").append(u.getApellidop() != null ? u.getApellidop() : "").append(" ").append(u.getApellidom() != null ? u.getApellidom() : "").append("</td>\n");
                html.append("                    <td>").append(u.getCorreo() != null ? u.getCorreo() : "").append("</td>\n");
                html.append("                    <td>").append(perfil).append("</td>\n");
                html.append("                    <td><span class=\"status-badge ").append(statusClass).append("\">").append(estado).append("</span></td>\n");
                html.append("                    <td>").append(categoriasStr).append("</td>\n");
                html.append("                    <td>").append(perfilSugeridoStr).append("</td>\n");
                html.append("                </tr>\n");
            }
            
            html.append("            </tbody>\n");
            html.append("        </table>\n");
            
            // Pagination UI
            html.append("        <div class=\"pagination\">\n");
            html.append("            <div>\n");
            if (page > 0) {
                html.append("                <a href=\"/api/reportes/usuarios?page=").append(page - 1).append("&size=").append(size).append("\">&laquo; Anterior</a>\n");
            } else {
                html.append("                <span class=\"disabled\">&laquo; Anterior</span>\n");
            }
            html.append("            </div>\n");
            html.append("            <div style=\"color: #475569; font-size: 0.9rem;\">\n");
            html.append("                Mostrando usuarios del ").append(total == 0 ? 0 : fromIndex + 1).append(" al ").append(toIndex).append(" de ").append(total).append("\n");
            html.append("            </div>\n");
            html.append("            <div>\n");
            if (toIndex < total) {
                html.append("                <a href=\"/api/reportes/usuarios?page=").append(page + 1).append("&size=").append(size).append("\">Siguiente &raquo;</a>\n");
            } else {
                html.append("                <span class=\"disabled\">Siguiente &raquo;</span>\n");
            }
            html.append("            </div>\n");
            html.append("        </div>\n");
            
            if (!rutsNoMapeados.isEmpty()) {
                html.append("        <div style=\"margin-top: 30px; padding: 20px; background-color: #fef2f2; border: 1px solid #fca5a5; border-radius: 8px;\">\n");
                html.append("            <h3 style=\"color: #991b1b; margin-top: 0;\">Usuarios con combinaciones de categorías no mapeadas</h3>\n");
                html.append("            <ul style=\"color: #7f1d1d;\">\n");
                for (String r : rutsNoMapeados) {
                    html.append("                <li>").append(r).append("</li>\n");
                }
                html.append("            </ul>\n");
                html.append("        </div>\n");
            }
            
            html.append("    </div>\n");
            html.append("</body>\n");
            html.append("</html>");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "text/html; charset=UTF-8")
                    .body(html.toString());
        } catch (Exception e) {
            System.err.println("Error obteniendo listado de usuarios HTML: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error interno: " + e.getMessage());
        }
    }

    private Integer extractRutNumber(String rutCompleto) {
        if (rutCompleto == null) return null;
        String rutClean = rutCompleto.replace(".", "").trim();
        if (rutClean.contains("-")) {
            rutClean = rutClean.split("-")[0];
        }
        try {
            return Integer.parseInt(rutClean);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer determinePerfilSugerido(List<ComboIntDto> categorias) {
        if (categorias == null || categorias.isEmpty()) return null;
        
        boolean c2 = false, c3 = false, c4 = false;
        for (ComboIntDto cat : categorias) {
            if (cat.getCodigo() == 2) c2 = true;
            if (cat.getCodigo() == 3) c3 = true;
            if (cat.getCodigo() == 4) c4 = true;
        }
        
        if (c2 && c3 && c4) return 10;
        if (!c2 && c3 && c4) return 10;
        if (c2 && c3 && !c4) return 9;
        if (c2 && !c3 && !c4) return 8;
        if (!c2 && c3 && !c4) return 1;
        if (!c2 && !c3 && c4) return 2;
        
        return null;
    }
}
