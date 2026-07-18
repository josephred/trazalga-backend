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
            sql.append(", udc.nombres as p_abast_nombres, udc.rut as p_abast_rut, dc.fecha_declaracion as fecha_comercializador ");
            sql.append(", udpa.nombres as p_prod_nombres, udpa.rut as p_prod_rut, dpa.fecha_ingreso_planta as fecha_planta_abast ");
        } else {
            sql.append(", NULL as folio_comercializador ");
            sql.append(", NULL as p_abast_nombres, NULL as p_abast_rut, NULL as fecha_comercializador ");
            sql.append(", NULL as p_prod_nombres, NULL as p_prod_rut, NULL as fecha_planta_abast ");
        }
        
        if (tipoReporte >= 1 && tipoReporte <= 5) {
            sql.append(", c.nombre as composicion_nombre, h.nombre as humedad_nombre ");
        } else if (tipoReporte == 6) {
            sql.append(", NULL as composicion_nombre, h.nombre as humedad_nombre ");
        } else {
            sql.append(", NULL as composicion_nombre, NULL as humedad_nombre ");
        }
        
        sql.append(", d.latitud, d.longitud ");

        sql.append("FROM ").append(tableName).append(" d ");
        sql.append("INNER JOIN usuario u ON d.usuario_id = u.id ");
        sql.append("LEFT JOIN usuario ud ON d.usuario_destinatario_id = ud.id ");
        sql.append("INNER JOIN especie e ON d.especie_id = e.id ");

        if (tipoReporte >= 1 && tipoReporte <= 5) {
            sql.append("LEFT JOIN composicion c ON d.composicion_id = c.id ");
            sql.append("LEFT JOIN humedad_estado h ON d.humedad_estado_id = h.id ");
        } else if (tipoReporte == 6) {
            sql.append("LEFT JOIN humedad_estado h ON d.humedad_estado_id = h.id ");
        }

        if (tipoReporte == 1) {
            sql.append("LEFT JOIN declaracion_comercializador dc ON d.declaracion_destinatario_id = dc.id ");
            sql.append("LEFT JOIN usuario udc ON dc.usuario_destinatario_id = udc.id ");
            sql.append("LEFT JOIN declaracion_planta_abastecimiento dpa ON dc.declaracion_destinatario_id = dpa.id ");
            sql.append("LEFT JOIN usuario udpa ON dpa.usuario_destinatario_id = udpa.id ");
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

            String pAbastNombre = (row[15] != null ? row[15].toString() : "");
            String pAbastRut = (row[16] != null ? row[16].toString() : "");
            String pAbastCompleto = pAbastNombre.isEmpty() ? "" : pAbastNombre + " (" + pAbastRut + ")";

            Date fechaComercializador = null;
            if (row[17] instanceof java.sql.Timestamp) fechaComercializador = new Date(((java.sql.Timestamp) row[17]).getTime());
            else if (row[17] instanceof Date) fechaComercializador = (Date) row[17];

            String pProdNombre = (row[18] != null ? row[18].toString() : "");
            String pProdRut = (row[19] != null ? row[19].toString() : "");
            String pProdCompleto = pProdNombre.isEmpty() ? "" : pProdNombre + " (" + pProdRut + ")";

            Date fechaPlantaAbast = null;
            if (row[20] instanceof java.sql.Timestamp) fechaPlantaAbast = new Date(((java.sql.Timestamp) row[20]).getTime());
            else if (row[20] instanceof Date) fechaPlantaAbast = (Date) row[20];

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
                    .plantaAbastecimiento(pAbastCompleto)
                    .fechaComercializador(fechaComercializador)
                    .plantaProduccion(pProdCompleto)
                    .fechaPlantaAbastecimiento(fechaPlantaAbast)
                    .composicion(row.length > 21 && row[21] != null ? row[21].toString() : null)
                    .estadoHumedad(row.length > 22 && row[22] != null ? row[22].toString() : null)
                    .latitud(row.length > 23 && row[23] != null ? ((Number) row[23]).doubleValue() : null)
                    .longitud(row.length > 24 && row[24] != null ? ((Number) row[24]).doubleValue() : null)
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

    public List<java.util.Map<String, Object>> getIndicadoresRecolector(Date startDate, Date endDate) {
        String dateFilter = "";
        if (startDate != null && endDate != null) {
            dateFilter = " WHERE d.fecha_declaracion BETWEEN :startDate AND :endDate";
        } else if (startDate != null) {
            dateFilter = " WHERE d.fecha_declaracion >= :startDate";
        } else if (endDate != null) {
            dateFilter = " WHERE d.fecha_declaracion <= :endDate";
        }

        String sql = "SELECT DATE(fecha_declaracion) as fecha, " +
            "COUNT(id) as decDiarias, " +
            "COALESCE(SUM(desembarque), 0) as totDiario " +
            "FROM (" +
            "    SELECT id, desembarque, fecha_declaracion FROM declaracion_recolector " +
            "    UNION ALL " +
            "    SELECT id, desembarque, fecha_declaracion FROM declaracion_armador " +
            "    UNION ALL " +
            "    SELECT id, desembarque, fecha_declaracion FROM declaracion_area " +
            ") as d" + dateFilter + " " +
            "GROUP BY DATE(fecha_declaracion) ORDER BY DATE(fecha_declaracion) ASC";
        
        Query query = entityManager.createNativeQuery(sql);
        if (startDate != null) query.setParameter("startDate", startDate);
        if (endDate != null) query.setParameter("endDate", endDate);

        List<Object[]> results = query.getResultList();
        
        return results.stream().map(row -> {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("fecha", row[0] != null ? row[0].toString() : "");
            map.put("declaracionesDiarias", row[1] != null ? ((Number) row[1]).longValue() : 0);
            map.put("totalDiario", row[2] != null ? ((Number) row[2]).doubleValue() : 0.0);
            return map;
        }).collect(Collectors.toList());
    }

    public java.util.Map<String, Object> getExtraccionVedaMetrics(Date startDate, Date endDate) {
        String dateFilter = "";
        if (startDate != null && endDate != null) {
            dateFilter = " AND decl.fecha_declaracion BETWEEN :startDate AND :endDate";
        } else if (startDate != null) {
            dateFilter = " AND decl.fecha_declaracion >= :startDate";
        } else if (endDate != null) {
            dateFilter = " AND decl.fecha_declaracion <= :endDate";
        }

        String sql = "SELECT " +
            "COUNT(decl.id) as total_declaraciones_veda, " +
            "COALESCE(SUM(decl.desembarque), 0) as total_kg_veda " +
            "FROM (" +
            "    SELECT id, desembarque, especie_id, fecha_declaracion, comuna_id FROM declaracion_recolector " +
            "    UNION ALL " +
            "    SELECT id, desembarque, especie_id, fecha_declaracion, comuna_id FROM declaracion_armador " +
            "    UNION ALL " +
            // declaracion_area no tiene comuna_id: sin comuna solo aplican vedas nacionales (region_id NULL)
            "    SELECT id, desembarque, especie_id, fecha_declaracion, NULL as comuna_id FROM declaracion_area " +
            ") as decl " +
            "LEFT JOIN comuna c ON decl.comuna_id = c.id " +
            "INNER JOIN veda_especie v ON decl.especie_id = v.especie_id " +
            "    AND decl.fecha_declaracion BETWEEN v.fecha_inicio AND v.fecha_fin " +
            "    AND (v.region_id IS NULL OR v.region_id = c.region_id) " +
            "WHERE 1=1" + dateFilter;
        
        Query query = entityManager.createNativeQuery(sql);
        if (startDate != null) query.setParameter("startDate", startDate);
        if (endDate != null) query.setParameter("endDate", endDate);

        Object[] result = (Object[]) query.getSingleResult();
        
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("declaracionesVeda", result[0] != null ? ((Number) result[0]).longValue() : 0);
        map.put("totalKgVeda", result[1] != null ? ((Number) result[1]).doubleValue() : 0.0);
        
        return map;
    }

    public List<java.util.Map<String, Object>> getExtraccionVedaDetalle(Date startDate, Date endDate) {
        String dateFilter = "";
        if (startDate != null && endDate != null) {
            dateFilter = " AND decl.fecha_declaracion BETWEEN :startDate AND :endDate";
        } else if (startDate != null) {
            dateFilter = " AND decl.fecha_declaracion >= :startDate";
        } else if (endDate != null) {
            dateFilter = " AND decl.fecha_declaracion <= :endDate";
        }

        String sql = "SELECT decl.id, decl.tipo_perfil, decl.fecha_declaracion, decl.desembarque, " +
            "e.nombre as especie_nombre, u.rut, u.nombres, u.apellidop " +
            "FROM (" +
            "    SELECT id, desembarque, especie_id, fecha_declaracion, usuario_id, comuna_id, 'RECOLECTOR' as tipo_perfil FROM declaracion_recolector " +
            "    UNION ALL " +
            "    SELECT id, desembarque, especie_id, fecha_declaracion, usuario_id, comuna_id, 'ARMADOR' as tipo_perfil FROM declaracion_armador " +
            "    UNION ALL " +
            // declaracion_area no tiene comuna_id: sin comuna solo aplican vedas nacionales (region_id NULL)
            "    SELECT id, desembarque, especie_id, fecha_declaracion, usuario_id, NULL as comuna_id, 'AREA' as tipo_perfil FROM declaracion_area " +
            ") as decl " +
            "LEFT JOIN comuna c ON decl.comuna_id = c.id " +
            "INNER JOIN veda_especie v ON decl.especie_id = v.especie_id " +
            "    AND decl.fecha_declaracion BETWEEN v.fecha_inicio AND v.fecha_fin " +
            "    AND (v.region_id IS NULL OR v.region_id = c.region_id) " +
            "INNER JOIN especie e ON decl.especie_id = e.id " +
            "INNER JOIN usuario u ON decl.usuario_id = u.id " +
            "WHERE 1=1" + dateFilter + " ORDER BY decl.fecha_declaracion DESC";
        
        Query query = entityManager.createNativeQuery(sql);
        if (startDate != null) query.setParameter("startDate", startDate);
        if (endDate != null) query.setParameter("endDate", endDate);

        List<Object[]> results = query.getResultList();
        
        return results.stream().map(row -> {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", row[0]);
            map.put("perfil", row[1]);
            
            Date dateVal = null;
            if (row[2] instanceof java.sql.Timestamp) {
                dateVal = new Date(((java.sql.Timestamp) row[2]).getTime());
            } else if (row[2] instanceof Date) {
                dateVal = (Date) row[2];
            }
            map.put("fecha", dateVal);
            map.put("kg", row[3] != null ? ((Number) row[3]).doubleValue() : 0.0);
            map.put("especie", row[4]);
            
            String nombreActor = (row[6] != null ? row[6].toString() : "") + " " + (row[7] != null ? row[7].toString() : "");
            map.put("actor", row[5] + " - " + nombreActor.trim());
            
            return map;
        }).collect(Collectors.toList());
    }

    public List<java.util.Map<String, Object>> getVolumenPorEspecie(Date startDate, Date endDate, String perfil) {
        String dateFilter = "";
        if (startDate != null && endDate != null) {
            dateFilter = " AND decl.fecha_declaracion BETWEEN :startDate AND :endDate";
        } else if (startDate != null) {
            dateFilter = " AND decl.fecha_declaracion >= :startDate";
        } else if (endDate != null) {
            dateFilter = " AND decl.fecha_declaracion <= :endDate";
        }

        String perfilNormalizado = (perfil == null || perfil.isEmpty() || "TODOS".equalsIgnoreCase(perfil))
            ? null : perfil.toUpperCase();
        String perfilFilter = perfilNormalizado != null ? " AND decl.tipo_perfil = :perfil" : "";

        String sql = "SELECT e.nombre as especie_nombre, " +
            "COUNT(decl.id) as total_declaraciones, " +
            "COALESCE(SUM(decl.desembarque), 0) as volumen_kg " +
            "FROM (" +
            "    SELECT id, desembarque, especie_id, fecha_declaracion, 'RECOLECTOR' as tipo_perfil FROM declaracion_recolector " +
            "    UNION ALL " +
            "    SELECT id, desembarque, especie_id, fecha_declaracion, 'ARMADOR' as tipo_perfil FROM declaracion_armador " +
            "    UNION ALL " +
            "    SELECT id, desembarque, especie_id, fecha_declaracion, 'AREA' as tipo_perfil FROM declaracion_area " +
            ") as decl " +
            "INNER JOIN especie e ON decl.especie_id = e.id " +
            "WHERE 1=1" + dateFilter + perfilFilter + " " +
            "GROUP BY e.nombre " +
            "ORDER BY volumen_kg DESC";

        Query query = entityManager.createNativeQuery(sql);
        if (startDate != null) query.setParameter("startDate", startDate);
        if (endDate != null) query.setParameter("endDate", endDate);
        if (perfilNormalizado != null) query.setParameter("perfil", perfilNormalizado);

        List<Object[]> results = query.getResultList();

        return results.stream().map(row -> {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("especie", row[0] != null ? row[0].toString() : "");
            map.put("declaraciones", row[1] != null ? ((Number) row[1]).longValue() : 0);
            map.put("volumenKg", row[2] != null ? ((Number) row[2]).doubleValue() : 0.0);
            return map;
        }).collect(Collectors.toList());
    }

    public java.util.Map<String, Object> getResumenGlobal(Date startDate, Date endDate) {
        String dateFilter = "";
        if (startDate != null && endDate != null) {
            dateFilter = " WHERE fecha_declaracion BETWEEN :startDate AND :endDate";
        } else if (startDate != null) {
            dateFilter = " WHERE fecha_declaracion >= :startDate";
        } else if (endDate != null) {
            dateFilter = " WHERE fecha_declaracion <= :endDate";
        }

        // Base sobre declaraciones de origen (recolector/armador/área):
        // total, volumen, actores distintos y casos de gestión abiertos (estado).
        String sql = "SELECT COUNT(id) as total_declaraciones, COALESCE(SUM(desembarque), 0) as total_volumen, " +
            "COUNT(DISTINCT usuario_id) as actores_distintos, " +
            "SUM(CASE WHEN estado IN ('NEGOCIACION','RECHAZADA') THEN 1 ELSE 0 END) as casos_abiertos, " +
            "SUM(CASE WHEN estado = 'RECHAZADA' THEN 1 ELSE 0 END) as rechazadas FROM (" +
            "    SELECT id, desembarque, fecha_declaracion, usuario_id, estado FROM declaracion_recolector " +
            "    UNION ALL " +
            "    SELECT id, desembarque, fecha_declaracion, usuario_id, estado FROM declaracion_armador " +
            "    UNION ALL " +
            "    SELECT id, desembarque, fecha_declaracion, usuario_id, estado FROM declaracion_area " +
            ") as decl " + dateFilter;

        Query query = entityManager.createNativeQuery(sql);
        if (startDate != null) query.setParameter("startDate", startDate);
        if (endDate != null) query.setParameter("endDate", endDate);

        Object[] result = (Object[]) query.getSingleResult();

        long total = result[0] != null ? ((Number) result[0]).longValue() : 0;
        long actores = result[2] != null ? ((Number) result[2]).longValue() : 0;
        long casosAbiertos = result[3] != null ? ((Number) result[3]).longValue() : 0;
        long rechazadas = result[4] != null ? ((Number) result[4]).longValue() : 0;

        // Métricas de riesgo, compuestas desde los indicadores ya existentes
        long enVeda = ((Number) getExtraccionVedaMetrics(startDate, endDate)
                .getOrDefault("declaracionesVeda", 0L)).longValue();
        long fueraUmbralPeso = ((Number) getVariacionPesoMetrics(startDate, endDate, null)
                .getOrDefault("fueraUmbral", 0L)).longValue();

        // Alertas activas: incidentes críticos del período (extracción en veda + variación de peso
        // fuera de umbral). El atraso de validación >48h se muestra en su propio widget y no se
        // suma aquí para no dominar el KPI mientras el flujo de consumo esté poco usado.
        long alertasActivas = enVeda + fueraUmbralPeso;
        // Inconsistencias: declaraciones en veda o rechazadas, sobre el total del período
        double inconsistenciasPct = total > 0
                ? Math.round((enVeda + rechazadas) * 1000.0 / total) / 10.0 : 0.0;

        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("declaracionesTotales", total);
        map.put("volumenTotal", result[1] != null ? ((Number) result[1]).doubleValue() : 0.0);
        map.put("actoresFiscalizados", actores);
        map.put("casosAbiertos", casosAbiertos);
        map.put("alertasActivas", alertasActivas);
        map.put("inconsistenciasPct", inconsistenciasPct);
        return map;
    }

    // Indicador "Tiempo entre extracción y validación".
    // Definición de "validación" (según observación SERNAPESCA "A Comercializador"): momento en que la
    // declaración de origen (recolector/armador/área) es consumida por la declaración de un comercializador.
    // Origen del intervalo: fecha_declaracion + hora de la declaración de origen.
    // Si el cliente define otro eslabón como cierre (ej. planta), basta cambiar el JOIN de esta consulta.
    private static final String SQL_ORIGENES_VALIDACION =
        "    SELECT id, especie_id, usuario_id, fecha_declaracion, hora, desembarque, declaracion_destinatario_id, consumida_por_tipo, 'RECOLECTOR' as tipo_perfil FROM declaracion_recolector " +
        "    UNION ALL " +
        "    SELECT id, especie_id, usuario_id, fecha_declaracion, hora, desembarque, declaracion_destinatario_id, consumida_por_tipo, 'ARMADOR' as tipo_perfil FROM declaracion_armador " +
        "    UNION ALL " +
        "    SELECT id, especie_id, usuario_id, fecha_declaracion, hora, desembarque, declaracion_destinatario_id, consumida_por_tipo, 'AREA' as tipo_perfil FROM declaracion_area ";

    public java.util.Map<String, Object> getTiempoValidacionMetrics(Date startDate, Date endDate) {
        String dateFilter = "";
        if (startDate != null && endDate != null) {
            dateFilter = " AND decl.fecha_declaracion BETWEEN :startDate AND :endDate";
        } else if (startDate != null) {
            dateFilter = " AND decl.fecha_declaracion >= :startDate";
        } else if (endDate != null) {
            dateFilter = " AND decl.fecha_declaracion <= :endDate";
        }

        String sql = "SELECT " +
            "COUNT(decl.id) as total, " +
            "SUM(CASE WHEN c.id IS NOT NULL THEN 1 ELSE 0 END) as validadas, " +
            "AVG(CASE WHEN c.id IS NOT NULL THEN TIMESTAMPDIFF(MINUTE, TIMESTAMP(decl.fecha_declaracion, decl.hora), TIMESTAMP(c.fecha_declaracion, c.hora)) END) as prom_min, " +
            "MAX(CASE WHEN c.id IS NOT NULL THEN TIMESTAMPDIFF(MINUTE, TIMESTAMP(decl.fecha_declaracion, decl.hora), TIMESTAMP(c.fecha_declaracion, c.hora)) END) as max_min, " +
            "SUM(CASE WHEN c.id IS NULL AND TIMESTAMP(decl.fecha_declaracion, decl.hora) < NOW() - INTERVAL 48 HOUR THEN 1 ELSE 0 END) as pendientes_48h " +
            "FROM (" + SQL_ORIGENES_VALIDACION + ") as decl " +
            "LEFT JOIN declaracion_comercializador c ON decl.declaracion_destinatario_id = c.id AND decl.consumida_por_tipo = 'COMERCIALIZADOR' " +
            "WHERE 1=1" + dateFilter;

        Query query = entityManager.createNativeQuery(sql);
        if (startDate != null) query.setParameter("startDate", startDate);
        if (endDate != null) query.setParameter("endDate", endDate);

        Object[] result = (Object[]) query.getSingleResult();

        long total = result[0] != null ? ((Number) result[0]).longValue() : 0;
        long validadas = result[1] != null ? ((Number) result[1]).longValue() : 0;
        Double promHoras = result[2] != null ? Math.round(((Number) result[2]).doubleValue() / 60.0 * 10.0) / 10.0 : null;
        Double maxHoras = result[3] != null ? Math.round(((Number) result[3]).doubleValue() / 60.0 * 10.0) / 10.0 : null;
        long pendientes48h = result[4] != null ? ((Number) result[4]).longValue() : 0;

        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("definicionValidacion", "Recepción por comercializador (declaración que consume el documento de origen)");
        map.put("totalDeclaraciones", total);
        map.put("validadas", validadas);
        map.put("pendientes", total - validadas);
        map.put("pendientesMas48h", pendientes48h);
        map.put("promedioHoras", promHoras);
        map.put("maxHoras", maxHoras);
        return map;
    }

    public List<java.util.Map<String, Object>> getTiempoValidacionDetalle(Date startDate, Date endDate) {
        String dateFilter = "";
        if (startDate != null && endDate != null) {
            dateFilter = " AND decl.fecha_declaracion BETWEEN :startDate AND :endDate";
        } else if (startDate != null) {
            dateFilter = " AND decl.fecha_declaracion >= :startDate";
        } else if (endDate != null) {
            dateFilter = " AND decl.fecha_declaracion <= :endDate";
        }

        // Para pendientes, las horas transcurridas se miden contra NOW().
        // Se devuelven las 50 validaciones más lentas y los 50 pendientes más antiguos,
        // para que ninguno de los dos estados quede fuera del detalle.
        String cuerpo = "SELECT decl.id, decl.tipo_perfil, decl.fecha_declaracion, decl.hora, decl.desembarque, " +
            "e.nombre as especie_nombre, u.rut, u.nombres, u.apellidop, " +
            "CASE WHEN c.id IS NULL THEN 'PENDIENTE' ELSE 'VALIDADA' END as estado, " +
            "ROUND(TIMESTAMPDIFF(MINUTE, TIMESTAMP(decl.fecha_declaracion, decl.hora), COALESCE(TIMESTAMP(c.fecha_declaracion, c.hora), NOW())) / 60.0, 1) as horas " +
            "FROM (" + SQL_ORIGENES_VALIDACION + ") as decl " +
            "LEFT JOIN declaracion_comercializador c ON decl.declaracion_destinatario_id = c.id AND decl.consumida_por_tipo = 'COMERCIALIZADOR' " +
            "INNER JOIN especie e ON decl.especie_id = e.id " +
            "INNER JOIN usuario u ON decl.usuario_id = u.id " +
            "WHERE 1=1" + dateFilter;

        String sql = "SELECT * FROM (" +
            "(" + cuerpo + " AND c.id IS NOT NULL ORDER BY horas DESC LIMIT 50) " +
            "UNION ALL " +
            "(" + cuerpo + " AND c.id IS NULL ORDER BY horas DESC LIMIT 50)" +
            ") as t ORDER BY estado DESC, horas DESC";

        Query query = entityManager.createNativeQuery(sql);
        if (startDate != null) query.setParameter("startDate", startDate);
        if (endDate != null) query.setParameter("endDate", endDate);

        List<Object[]> results = query.getResultList();

        return results.stream().map(row -> {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", row[0]);
            map.put("perfil", row[1]);

            Date dateVal = null;
            if (row[2] instanceof java.sql.Timestamp) {
                dateVal = new Date(((java.sql.Timestamp) row[2]).getTime());
            } else if (row[2] instanceof Date) {
                dateVal = (Date) row[2];
            }
            map.put("fecha", dateVal);
            map.put("hora", row[3] != null ? row[3].toString() : "");
            map.put("kg", row[4] != null ? ((Number) row[4]).doubleValue() : 0.0);
            map.put("especie", row[5]);

            String nombreActor = (row[7] != null ? row[7].toString() : "") + " " + (row[8] != null ? row[8].toString() : "");
            map.put("actor", nombreActor.trim());
            map.put("rut", row[6] != null ? row[6].toString() : "");
            map.put("estado", row[9]);
            map.put("horas", row[10] != null ? ((Number) row[10]).doubleValue() : null);
            return map;
        }).collect(Collectors.toList());
    }

    // Indicador "Variación de peso" (posible adulteración).
    // Dos niveles de conciliación:
    //  - PESAJE: peso_recepcionado registrado por el receptor vs lo declarado en origen (por transacción).
    //  - DOCUMENTO: cantidad del documento receptor vs suma de lo declarado en los documentos que consume
    //    (origen→comercializador y comercializador→planta abastecimiento).
    private static final String SQL_PESAJES =
        "SELECT decl.tipo_registro, decl.eslabon, decl.fecha, u.nombres, u.apellidop, e.nombre as especie, " +
        "decl.kg_declarado as kg_origen, decl.peso_recepcionado as kg_destino, " +
        "ROUND((decl.peso_recepcionado - decl.kg_declarado) / decl.kg_declarado * 100, 1) as pct " +
        "FROM (" +
        "    SELECT id, especie_id, usuario_id, fecha_declaracion as fecha, desembarque as kg_declarado, peso_recepcionado, 'PESAJE' as tipo_registro, 'RECOLECTOR' as eslabon FROM declaracion_recolector " +
        "    UNION ALL " +
        "    SELECT id, especie_id, usuario_id, fecha_declaracion, desembarque, peso_recepcionado, 'PESAJE', 'ARMADOR' FROM declaracion_armador " +
        "    UNION ALL " +
        "    SELECT id, especie_id, usuario_id, fecha_declaracion, desembarque, peso_recepcionado, 'PESAJE', 'AREA' FROM declaracion_area " +
        "    UNION ALL " +
        "    SELECT id, especie_id, usuario_id, fecha_declaracion, cantidad, peso_recepcionado, 'PESAJE', 'COMERCIALIZADOR' FROM declaracion_comercializador " +
        ") as decl " +
        "INNER JOIN especie e ON decl.especie_id = e.id " +
        "INNER JOIN usuario u ON decl.usuario_id = u.id " +
        "WHERE decl.peso_recepcionado IS NOT NULL AND decl.kg_declarado > 0";

    private static final String SQL_ORIGENES_KG =
        "    SELECT desembarque, declaracion_destinatario_id, consumida_por_tipo FROM declaracion_recolector " +
        "    UNION ALL " +
        "    SELECT desembarque, declaracion_destinatario_id, consumida_por_tipo FROM declaracion_armador " +
        "    UNION ALL " +
        "    SELECT desembarque, declaracion_destinatario_id, consumida_por_tipo FROM declaracion_area ";

    private static final String SQL_DOCS_COMERCIALIZADOR =
        "SELECT 'DOCUMENTO' as tipo_registro, 'ORIGEN-COMERCIALIZADOR' as eslabon, c.fecha_declaracion as fecha, " +
        "u.nombres, u.apellidop, e.nombre as especie, " +
        "SUM(o.desembarque) as kg_origen, c.cantidad as kg_destino, " +
        "ROUND((c.cantidad - SUM(o.desembarque)) / SUM(o.desembarque) * 100, 1) as pct " +
        "FROM declaracion_comercializador c " +
        "INNER JOIN (" + SQL_ORIGENES_KG + ") as o " +
        "    ON o.declaracion_destinatario_id = c.id AND o.consumida_por_tipo = 'COMERCIALIZADOR' " +
        "INNER JOIN especie e ON c.especie_id = e.id " +
        "INNER JOIN usuario u ON c.usuario_id = u.id " +
        "WHERE 1=1 %s " +
        "GROUP BY c.id, c.cantidad, c.fecha_declaracion, u.nombres, u.apellidop, e.nombre " +
        "HAVING SUM(o.desembarque) > 0";

    private static final String SQL_DOCS_PLANTA =
        "SELECT 'DOCUMENTO' as tipo_registro, 'COMERCIALIZADOR-PLANTA' as eslabon, p.fecha_ingreso_planta as fecha, " +
        "u.nombres, u.apellidop, e.nombre as especie, " +
        "SUM(c2.cantidad) as kg_origen, p.cantidad as kg_destino, " +
        "ROUND((p.cantidad - SUM(c2.cantidad)) / SUM(c2.cantidad) * 100, 1) as pct " +
        "FROM declaracion_planta_abastecimiento p " +
        "INNER JOIN declaracion_comercializador c2 " +
        "    ON c2.declaracion_destinatario_id = p.id AND c2.consumida_por_tipo = 'PLANTA_ABASTECIMIENTO' " +
        "INNER JOIN especie e ON p.especie_id = e.id " +
        "INNER JOIN usuario u ON p.usuario_id = u.id " +
        "WHERE 1=1 %s " +
        "GROUP BY p.id, p.cantidad, p.fecha_ingreso_planta, u.nombres, u.apellidop, e.nombre " +
        "HAVING SUM(c2.cantidad) > 0";

    private String sqlVariacionPesoUnion(Date startDate, Date endDate) {
        String filtroPesaje = "";
        String filtroComercializador = "";
        String filtroPlanta = "";
        if (startDate != null && endDate != null) {
            filtroPesaje = " AND decl.fecha BETWEEN :startDate AND :endDate";
            filtroComercializador = " AND c.fecha_declaracion BETWEEN :startDate AND :endDate";
            filtroPlanta = " AND p.fecha_ingreso_planta BETWEEN :startDate AND :endDate";
        } else if (startDate != null) {
            filtroPesaje = " AND decl.fecha >= :startDate";
            filtroComercializador = " AND c.fecha_declaracion >= :startDate";
            filtroPlanta = " AND p.fecha_ingreso_planta >= :startDate";
        } else if (endDate != null) {
            filtroPesaje = " AND decl.fecha <= :endDate";
            filtroComercializador = " AND c.fecha_declaracion <= :endDate";
            filtroPlanta = " AND p.fecha_ingreso_planta <= :endDate";
        }

        return "(" + SQL_PESAJES + filtroPesaje + ") " +
               "UNION ALL (" + SQL_DOCS_COMERCIALIZADOR.replace("%s", filtroComercializador) + ") " +
               "UNION ALL (" + SQL_DOCS_PLANTA.replace("%s", filtroPlanta) + ")";
    }

    public java.util.Map<String, Object> getVariacionPesoMetrics(Date startDate, Date endDate, Double umbralPct) {
        double umbral = umbralPct != null ? umbralPct : 5.0;

        String sql = "SELECT COUNT(*), AVG(ABS(t.pct)), " +
            "SUM(CASE WHEN ABS(t.pct) > :umbral THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN t.tipo_registro = 'PESAJE' THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN t.tipo_registro = 'DOCUMENTO' THEN 1 ELSE 0 END) " +
            "FROM (" + sqlVariacionPesoUnion(startDate, endDate) + ") as t";

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("umbral", umbral);
        if (startDate != null) query.setParameter("startDate", startDate);
        if (endDate != null) query.setParameter("endDate", endDate);

        Object[] result = (Object[]) query.getSingleResult();

        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("umbralPct", umbral);
        map.put("totalConciliaciones", result[0] != null ? ((Number) result[0]).longValue() : 0);
        map.put("promedioVariacionPct", result[1] != null ? Math.round(((Number) result[1]).doubleValue() * 10.0) / 10.0 : null);
        map.put("fueraUmbral", result[2] != null ? ((Number) result[2]).longValue() : 0);
        map.put("pesajes", result[3] != null ? ((Number) result[3]).longValue() : 0);
        map.put("documentos", result[4] != null ? ((Number) result[4]).longValue() : 0);
        return map;
    }

    public List<java.util.Map<String, Object>> getVariacionPesoDetalle(Date startDate, Date endDate) {
        String sql = "SELECT * FROM (" + sqlVariacionPesoUnion(startDate, endDate) + ") as t " +
            "ORDER BY ABS(t.pct) DESC LIMIT 100";

        Query query = entityManager.createNativeQuery(sql);
        if (startDate != null) query.setParameter("startDate", startDate);
        if (endDate != null) query.setParameter("endDate", endDate);

        List<Object[]> results = query.getResultList();

        return results.stream().map(row -> {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("tipoRegistro", row[0]);
            map.put("eslabon", row[1]);

            Date dateVal = null;
            if (row[2] instanceof java.sql.Timestamp) {
                dateVal = new Date(((java.sql.Timestamp) row[2]).getTime());
            } else if (row[2] instanceof Date) {
                dateVal = (Date) row[2];
            }
            map.put("fecha", dateVal);

            String actor = (row[3] != null ? row[3].toString() : "") + " " + (row[4] != null ? row[4].toString() : "");
            map.put("actor", actor.trim());
            map.put("especie", row[5]);
            map.put("kgOrigen", row[6] != null ? ((Number) row[6]).doubleValue() : null);
            map.put("kgDestino", row[7] != null ? ((Number) row[7]).doubleValue() : null);
            map.put("variacionPct", row[8] != null ? ((Number) row[8]).doubleValue() : null);
            return map;
        }).collect(Collectors.toList());
    }

    public List<java.util.Map<String, Object>> getCasosAbiertosDetalle(Date startDate, Date endDate) {
        String dateFilter = "";
        if (startDate != null && endDate != null) {
            dateFilter = " AND d.fecha_declaracion BETWEEN :startDate AND :endDate";
        } else if (startDate != null) {
            dateFilter = " AND d.fecha_declaracion >= :startDate";
        } else if (endDate != null) {
            dateFilter = " AND d.fecha_declaracion <= :endDate";
        }

        String sql = "SELECT d.id, 'RECOLECTOR' as tipo_perfil, d.folio_origen as folio, d.fecha_declaracion, d.estado, " +
            "u.nombres as dec_nombres, u.apellidop as dec_apellidop, dest.nombres as dest_nombres, dest.apellidop as dest_apellidop, " +
            "(SELECT mensaje FROM gestion_mensaje gm WHERE gm.declaracion_id = d.id AND gm.declaracion_tipo = 'RECOLECTOR' ORDER BY gm.fecha_envio DESC LIMIT 1) as motivo, " +
            "(SELECT fecha_envio FROM gestion_mensaje gm WHERE gm.declaracion_id = d.id AND gm.declaracion_tipo = 'RECOLECTOR' ORDER BY gm.fecha_envio DESC LIMIT 1) as fecha_mensaje " +
            "FROM declaracion_recolector d LEFT JOIN usuario u ON d.usuario_id = u.id LEFT JOIN usuario dest ON d.usuario_destinatario_id = dest.id " +
            "WHERE d.estado IN ('NEGOCIACION', 'RECHAZADA')" + dateFilter + " " +
            "UNION ALL " +
            "SELECT d.id, 'ARMADOR', d.folio_origen, d.fecha_declaracion, d.estado, " +
            "u.nombres, u.apellidop, dest.nombres, dest.apellidop, " +
            "(SELECT mensaje FROM gestion_mensaje gm WHERE gm.declaracion_id = d.id AND gm.declaracion_tipo = 'ARMADOR' ORDER BY gm.fecha_envio DESC LIMIT 1), " +
            "(SELECT fecha_envio FROM gestion_mensaje gm WHERE gm.declaracion_id = d.id AND gm.declaracion_tipo = 'ARMADOR' ORDER BY gm.fecha_envio DESC LIMIT 1) " +
            "FROM declaracion_armador d LEFT JOIN usuario u ON d.usuario_id = u.id LEFT JOIN usuario dest ON d.usuario_destinatario_id = dest.id " +
            "WHERE d.estado IN ('NEGOCIACION', 'RECHAZADA')" + dateFilter + " " +
            "UNION ALL " +
            "SELECT d.id, 'AREA', d.folio_origen, d.fecha_declaracion, d.estado, " +
            "u.nombres, u.apellidop, dest.nombres, dest.apellidop, " +
            "(SELECT mensaje FROM gestion_mensaje gm WHERE gm.declaracion_id = d.id AND gm.declaracion_tipo = 'AREA' ORDER BY gm.fecha_envio DESC LIMIT 1), " +
            "(SELECT fecha_envio FROM gestion_mensaje gm WHERE gm.declaracion_id = d.id AND gm.declaracion_tipo = 'AREA' ORDER BY gm.fecha_envio DESC LIMIT 1) " +
            "FROM declaracion_area d LEFT JOIN usuario u ON d.usuario_id = u.id LEFT JOIN usuario dest ON d.usuario_destinatario_id = dest.id " +
            "WHERE d.estado IN ('NEGOCIACION', 'RECHAZADA')" + dateFilter + " " +
            "UNION ALL " +
            "SELECT d.id, 'COMERCIALIZADOR', d.folio_origen, d.fecha_declaracion, d.estado, " +
            "u.nombres, u.apellidop, dest.nombres, dest.apellidop, " +
            "(SELECT mensaje FROM gestion_mensaje gm WHERE gm.declaracion_id = d.id AND gm.declaracion_tipo = 'COMERCIALIZADOR' ORDER BY gm.fecha_envio DESC LIMIT 1), " +
            "(SELECT fecha_envio FROM gestion_mensaje gm WHERE gm.declaracion_id = d.id AND gm.declaracion_tipo = 'COMERCIALIZADOR' ORDER BY gm.fecha_envio DESC LIMIT 1) " +
            "FROM declaracion_comercializador d LEFT JOIN usuario u ON d.usuario_id = u.id LEFT JOIN usuario dest ON d.usuario_destinatario_id = dest.id " +
            "WHERE d.estado IN ('NEGOCIACION', 'RECHAZADA')" + dateFilter + " " +
            "ORDER BY fecha_mensaje DESC";

        Query query = entityManager.createNativeQuery(sql);
        if (startDate != null) query.setParameter("startDate", startDate);
        if (endDate != null) query.setParameter("endDate", endDate);

        List<Object[]> results = query.getResultList();

        return results.stream().map(row -> {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", row[0]);
            map.put("perfil", row[1]);
            map.put("folio", row[2] != null ? row[2].toString() : "");
            
            Date dateVal = null;
            if (row[3] instanceof java.sql.Timestamp) {
                dateVal = new Date(((java.sql.Timestamp) row[3]).getTime());
            } else if (row[3] instanceof Date) {
                dateVal = (Date) row[3];
            }
            map.put("fecha", dateVal);
            map.put("estado", row[4]);

            String declarante = (row[5] != null ? row[5].toString() : "") + " " + (row[6] != null ? row[6].toString() : "");
            map.put("declarante", declarante.trim());

            String destinatario = (row[7] != null ? row[7].toString() : "") + " " + (row[8] != null ? row[8].toString() : "");
            map.put("destinatario", destinatario.trim());

            map.put("motivo", row[9] != null ? row[9].toString() : "Sin motivo registrado");
            
            Date fechaMsg = null;
            if (row[10] instanceof java.sql.Timestamp) {
                fechaMsg = new Date(((java.sql.Timestamp) row[10]).getTime());
            } else if (row[10] instanceof Date) {
                fechaMsg = (Date) row[10];
            }
            map.put("fechaMensaje", fechaMsg);
            
            return map;
        }).collect(Collectors.toList());
    }

    private static class RawNode {
        com.trazalga.api.dto.TrazabilidadNodoDTO dto;
        String declaracionesSeleccionadas;
        Long declaracionDestinatarioId;
        String consumidaPorTipo;
    }

    private String getTipoString(Integer tipo) {
        switch (tipo) {
            case 1: return "RECOLECTOR";
            case 2: return "ARMADOR";
            case 3: return "AREA";
            case 4: return "COMERCIALIZADOR";
            case 5: return "PLANTA_ABASTECIMIENTO";
            case 6: return "PLANTA_PRODUCCION";
            case 7: return "PLANTA_DESTINO";
            default: return "DESCONOCIDO";
        }
    }

    private List<String> getPossibleParentTypes(String tipo) {
        switch (tipo) {
            case "COMERCIALIZADOR": 
            case "PLANTA_ABASTECIMIENTO":
                return java.util.Arrays.asList("RECOLECTOR", "ARMADOR", "AREA", "COMERCIALIZADOR");
            case "PLANTA_PRODUCCION": 
                return java.util.Arrays.asList("PLANTA_ABASTECIMIENTO");
            case "PLANTA_DESTINO": 
                return java.util.Arrays.asList("PLANTA_PRODUCCION", "PLANTA_ABASTECIMIENTO");
            default: 
                return new java.util.ArrayList<>();
        }
    }

    private RawNode fetchRawNode(String tipoStr, Long id) {
        String tableName = "";
        String roleName = "";
        String dateCol = "fecha_declaracion";
        String amountCol = "desembarque";
        String folioCol = "folio_origen";
        boolean hasSeleccionadas = false;
        
        switch (tipoStr) {
            case "RECOLECTOR": tableName = "declaracion_recolector"; roleName = "Recolector"; break;
            case "ARMADOR": tableName = "declaracion_armador"; roleName = "Armador"; break;
            case "AREA": tableName = "declaracion_area"; roleName = "Área de Manejo"; break;
            case "COMERCIALIZADOR": tableName = "declaracion_comercializador"; roleName = "Comercializador"; amountCol = "cantidad"; hasSeleccionadas = true; break;
            case "PLANTA_ABASTECIMIENTO": tableName = "declaracion_planta_abastecimiento"; roleName = "Planta Abastecimiento"; amountCol = "cantidad"; dateCol = "fecha_ingreso_planta"; folioCol = "folio_declaracion_a_pla"; hasSeleccionadas = true; break;
            case "PLANTA_PRODUCCION": tableName = "declaracion_planta_produccion"; roleName = "Planta Producción"; amountCol = "cantidad_producto"; dateCol = "fecha_produccion"; folioCol = "folio_declaracion_p_pla"; hasSeleccionadas = true; break;
            case "PLANTA_DESTINO": tableName = "declaracion_planta_destino"; roleName = "Planta Destino"; amountCol = "cantidad"; dateCol = "fecha_declaracion_destino"; folioCol = "folio_declaracion_destino"; hasSeleccionadas = true; break;
            default: return null;
        }

        String selCol = hasSeleccionadas ? "d.declaraciones_seleccionadas" : "NULL as declaraciones_seleccionadas";
        
        String sql = "SELECT d.id, u.nombres, u.apellidop, u.rut, d." + dateCol + ", d." + amountCol + ", d." + folioCol + ", " +
                     "d.declaracion_destinatario_id, d.consumida_por_tipo, " + selCol + " " +
                     "FROM " + tableName + " d " +
                     "INNER JOIN usuario u ON d.usuario_id = u.id " +
                     "WHERE d.id = :id";
                     
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("id", id);
        List<Object[]> results = query.getResultList();
        
        if (results.isEmpty()) return null;
        
        Object[] row = results.get(0);
        String actor = (row[1] != null ? row[1].toString() : "") + " " + (row[2] != null ? row[2].toString() : "");
        Date d = null;
        if (row[4] instanceof java.sql.Timestamp) d = new Date(((java.sql.Timestamp) row[4]).getTime());
        else if (row[4] instanceof Date) d = (Date) row[4];
        
        com.trazalga.api.dto.TrazabilidadNodoDTO dto = com.trazalga.api.dto.TrazabilidadNodoDTO.builder()
            .idUnico(tipoStr + ":" + id)
            .idDeclaracion(((Number)row[0]).longValue())
            .tipoNodo(roleName)
            .nombreActor(actor.trim())
            .rutActor(row[3] != null ? row[3].toString() : "")
            .fecha(d)
            .cantidad(row[5] != null ? new java.math.BigDecimal(row[5].toString()) : java.math.BigDecimal.ZERO)
            .descripcionEvento("Declaración de tipo " + roleName)
            .folio(row[6] != null ? row[6].toString() : "")
            .build();
            
        RawNode raw = new RawNode();
        raw.dto = dto;
        raw.declaracionDestinatarioId = row[7] != null ? ((Number)row[7]).longValue() : null;
        raw.consumidaPorTipo = row[8] != null ? row[8].toString() : null;
        raw.declaracionesSeleccionadas = row[9] != null ? row[9].toString() : null;
        
        return raw;
    }

    public com.trazalga.api.dto.TrazabilidadResponseDTO getTrazabilidad(Integer tipo, Long id) {
        java.util.Queue<String> queue = new java.util.LinkedList<>();
        java.util.Set<String> visited = new java.util.HashSet<>();
        
        List<com.trazalga.api.dto.TrazabilidadNodoDTO> nodos = new java.util.ArrayList<>();
        List<com.trazalga.api.dto.TrazabilidadEdgeDTO> enlaces = new java.util.ArrayList<>();

        String initialTipoStr = getTipoString(tipo);
        String initialKey = initialTipoStr + ":" + id;
        queue.add(initialKey);
        visited.add(initialKey);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            String[] parts = current.split(":");
            String currTipo = parts[0];
            Long currId = Long.parseLong(parts[1]);

            RawNode raw = fetchRawNode(currTipo, currId);
            if (raw == null) continue;

            nodos.add(raw.dto);

            // Forward edges (child)
            if (raw.declaracionDestinatarioId != null && raw.consumidaPorTipo != null) {
                String childKey = raw.consumidaPorTipo + ":" + raw.declaracionDestinatarioId;
                enlaces.add(new com.trazalga.api.dto.TrazabilidadEdgeDTO(current, childKey));
                if (!visited.contains(childKey)) {
                    visited.add(childKey);
                    queue.add(childKey);
                }
            }

            // Backward edges (parents)
            if (raw.declaracionesSeleccionadas != null && !raw.declaracionesSeleccionadas.isEmpty()) {
                java.util.Map<String, List<Long>> parsed = com.trazalga.api.services.trazabilidad.SeleccionTokens.parse(raw.declaracionesSeleccionadas);
                
                List<String> possibleParentTypes = getPossibleParentTypes(currTipo);
                for (String pType : possibleParentTypes) {
                    List<Long> pIds = com.trazalga.api.services.trazabilidad.SeleccionTokens.idsParaTipo(parsed, pType);
                    for (Long pId : pIds) {
                        String parentKey = pType + ":" + pId;
                        enlaces.add(new com.trazalga.api.dto.TrazabilidadEdgeDTO(parentKey, current));
                        if (!visited.contains(parentKey)) {
                            visited.add(parentKey);
                            queue.add(parentKey);
                        }
                    }
                }
            }
        }
        
        java.util.Set<String> validNodeIds = nodos.stream().map(com.trazalga.api.dto.TrazabilidadNodoDTO::getIdUnico).collect(Collectors.toSet());
        enlaces.removeIf(e -> !validNodeIds.contains(e.getSource()) || !validNodeIds.contains(e.getTarget()));

        return new com.trazalga.api.dto.TrazabilidadResponseDTO(nodos, enlaces);
    }
}
