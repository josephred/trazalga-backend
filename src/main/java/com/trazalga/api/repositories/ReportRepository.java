package com.trazalga.api.repositories;

import com.trazalga.api.dto.ReportDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class ReportRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public List<ReportDTO> generateReport(Date fechaInicio, Date fechaFin, Integer tipoReporte, String rut) {
        String tableName;
        String dateColumn = "fecha_declaracion";
        String amountColumn = "desembarque";
        
        switch (tipoReporte) {
            case 1: tableName = "declaracion_recolector"; break;
            case 2: tableName = "declaracion_armador"; break;
            case 3: tableName = "declaracion_area"; break;
            case 4: tableName = "declaracion_comercializador"; amountColumn = "cantidad"; break;
            case 5: tableName = "declaracion_planta_abastecimiento"; amountColumn = "cantidad"; dateColumn = "fecha_ingreso_planta"; break;
            case 6: tableName = "declaracion_planta_produccion"; amountColumn = "cantidad_producto"; dateColumn = "fecha_produccion"; break;
            case 7: tableName = "declaracion_planta_destino"; amountColumn = "cantidad"; dateColumn = "fecha_declaracion_destino"; break;
            default: throw new IllegalArgumentException("Tipo de reporte no válido: " + tipoReporte);
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT d.id, d.folio_origen, d.").append(dateColumn).append(", d.hora, ");
        sql.append("u.nombres, u.apellidop, u.apellidom, u.rut, ");
        sql.append("ud.nombres as r_nombres, ud.apellidop as r_apellidop, ud.apellidom as r_apellidom, ud.rut as r_rut, ");
        sql.append("d.").append(amountColumn).append(", e.nombre as especie_nombre ");
        
        if (tipoReporte == 1) {
            sql.append(", dc.folio_origen as folio_comercializador ");
        } else {
            sql.append(", NULL as folio_comercializador ");
        }

        sql.append("FROM ").append(tableName).append(" d ");
        sql.append("INNER JOIN usuario u ON d.usuario_id = u.id ");
        sql.append("LEFT JOIN usuario ud ON d.usuario_destinatario_id = ud.id ");
        sql.append("INNER JOIN especie e ON d.especie_id = e.id ");

        if (tipoReporte == 1) {
            sql.append("LEFT JOIN declaracion_comercializador dc ON d.declaracion_destinatario_id = dc.id ");
        }

        sql.append("WHERE d.").append(dateColumn).append(" BETWEEN :fechaInicio AND :fechaFin ");

        if (rut != null && !rut.isEmpty()) {
            sql.append("AND u.rut = :rut ");
        }

        Query query = entityManager.createNativeQuery(sql.toString());
        query.setParameter("fechaInicio", fechaInicio);
        query.setParameter("fechaFin", fechaFin);
        if (rut != null && !rut.isEmpty()) {
            query.setParameter("rut", rut);
        }

        List<Object[]> results = query.getResultList();

        return results.stream().map(row -> {
            String emisorNombre = (row[4] != null ? row[4].toString() : "") + " " +
                                 (row[5] != null ? row[5].toString() : "") + " " +
                                 (row[6] != null ? row[6].toString() : "");
            
            String receptorNombre = (row[8] != null ? row[8].toString() : "") + " " +
                                   (row[9] != null ? row[9].toString() : "") + " " +
                                   (row[10] != null ? row[10].toString() : "");

            Date dateVal = null;
            if (row[2] instanceof java.sql.Timestamp) {
                dateVal = new Date(((java.sql.Timestamp) row[2]).getTime());
            } else if (row[2] instanceof Date) {
                dateVal = (Date) row[2];
            }

            return ReportDTO.builder()
                    .id(((Number) row[0]).longValue())
                    .folio(row[1] != null ? row[1].toString() : "")
                    .fecha(dateVal)
                    .hora(row[3] != null ? row[3].toString() : "")
                    .emisorNombre(emisorNombre.trim())
                    .emisorRut(row[7] != null ? row[7].toString() : "")
                    .receptorNombre(receptorNombre.trim())
                    .receptorRut(row[11] != null ? row[11].toString() : "")
                    .cantidad(row[12] != null ? new BigDecimal(row[12].toString()) : BigDecimal.ZERO)
                    .especie(row[13] != null ? row[13].toString() : "")
                    .tipoReporte(getReportLabel(tipoReporte))
                    .folioRelacionado(row[14] != null ? row[14].toString() : "")
                    .build();
        }).collect(Collectors.toList());
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

    public java.util.Map<String, Object> getIndicadoresRecolector() {
        String sql = "SELECT " +
            "SUM(CASE WHEN DATE(d.fecha_declaracion) = CURRENT_DATE() THEN 1 ELSE 0 END) as decDiarias, " +
            "COALESCE(SUM(CASE WHEN DATE(d.fecha_declaracion) = CURRENT_DATE() THEN d.desembarque ELSE 0 END), 0) as totDiario, " +
            "SUM(CASE WHEN YEARWEEK(d.fecha_declaracion, 1) = YEARWEEK(CURRENT_DATE(), 1) THEN 1 ELSE 0 END) as decSemanales, " +
            "COALESCE(SUM(CASE WHEN YEARWEEK(d.fecha_declaracion, 1) = YEARWEEK(CURRENT_DATE(), 1) THEN d.desembarque ELSE 0 END), 0) as totSemanal, " +
            "SUM(CASE WHEN MONTH(d.fecha_declaracion) = MONTH(CURRENT_DATE()) AND YEAR(d.fecha_declaracion) = YEAR(CURRENT_DATE()) THEN 1 ELSE 0 END) as decMensuales, " +
            "COALESCE(SUM(CASE WHEN MONTH(d.fecha_declaracion) = MONTH(CURRENT_DATE()) AND YEAR(d.fecha_declaracion) = YEAR(CURRENT_DATE()) THEN d.desembarque ELSE 0 END), 0) as totMensual " +
            "FROM declaracion_recolector d";
        
        Query query = entityManager.createNativeQuery(sql);
        Object[] result = (Object[]) query.getSingleResult();
        
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("declaracionesDiarias", result[0] != null ? ((Number) result[0]).longValue() : 0);
        map.put("totalDiario", result[1] != null ? ((Number) result[1]).doubleValue() : 0.0);
        map.put("declaracionesSemanales", result[2] != null ? ((Number) result[2]).longValue() : 0);
        map.put("totalSemanal", result[3] != null ? ((Number) result[3]).doubleValue() : 0.0);
        map.put("declaracionesMensuales", result[4] != null ? ((Number) result[4]).longValue() : 0);
        map.put("totalMensual", result[5] != null ? ((Number) result[5]).doubleValue() : 0.0);
        
        return map;
    }
}
