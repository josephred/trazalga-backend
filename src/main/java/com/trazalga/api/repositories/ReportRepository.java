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

    /**
     * Curva Snake acumulada (AMERB #2): serie temporal del volumen extraído en
     * áreas de manejo, con suma acumulada por fecha ("curva snake" de explotación).
     * Filtrable por AMERB y/o especie. Si existe una cuota AREA aplicable para la
     * especie (priorizando la del AMERB específico), se devuelve como línea de
     * referencia. Definición pendiente de formalizar con SERNAPESCA: eje = fecha,
     * acumulado = suma corrida de desembarque; referencia = límite de la cuota.
     */
    public java.util.Map<String, Object> getCurvaSnake(Date startDate, Date endDate, Long amerbId, Long especieId) {
        String dateFilter = "";
        if (startDate != null && endDate != null) {
            dateFilter = " AND d.fecha_declaracion BETWEEN :startDate AND :endDate";
        } else if (startDate != null) {
            dateFilter = " AND d.fecha_declaracion >= :startDate";
        } else if (endDate != null) {
            dateFilter = " AND d.fecha_declaracion <= :endDate";
        }
        String amerbFilter = amerbId != null ? " AND d.amerb_id = :amerbId" : "";
        String especieFilter = especieId != null ? " AND d.especie_id = :especieId" : "";

        // 1. Serie diaria de volumen extraído en AMERB
        String sql = "SELECT DATE(d.fecha_declaracion) as fecha, COALESCE(SUM(d.desembarque), 0) as vol " +
            "FROM declaracion_area d WHERE 1=1" + dateFilter + amerbFilter + especieFilter + " " +
            "GROUP BY DATE(d.fecha_declaracion) ORDER BY DATE(d.fecha_declaracion) ASC";

        Query query = entityManager.createNativeQuery(sql);
        if (startDate != null) query.setParameter("startDate", startDate);
        if (endDate != null) query.setParameter("endDate", endDate);
        if (amerbId != null) query.setParameter("amerbId", amerbId);
        if (especieId != null) query.setParameter("especieId", especieId);

        List<Object[]> rows = query.getResultList();
        List<java.util.Map<String, Object>> serie = new java.util.ArrayList<>();
        double acumulado = 0.0;
        for (Object[] row : rows) {
            double vol = row[1] != null ? ((Number) row[1]).doubleValue() : 0.0;
            acumulado += vol;
            java.util.Map<String, Object> punto = new java.util.HashMap<>();
            punto.put("fecha", row[0] != null ? row[0].toString() : "");
            punto.put("volumenDiario", vol);
            punto.put("acumulado", Math.round(acumulado * 100.0) / 100.0);
            serie.add(punto);
        }

        // 2. Cuota de referencia (solo si se especifica especie), priorizando la del AMERB
        Double limiteKg = null;
        String cuotaPeriodo = null;
        if (especieId != null) {
            String cuotaCondAmerb = amerbId != null
                    ? " AND (amerb_id = :amerbId OR amerb_id IS NULL)" : " AND amerb_id IS NULL";
            String cuotaSql = "SELECT limite_kg, periodo FROM cuota_extraccion " +
                "WHERE perfil = 'AREA' AND activo = 1 AND especie_id = :especieId" + cuotaCondAmerb + " " +
                "ORDER BY (amerb_id IS NOT NULL) DESC LIMIT 1";
            Query cq = entityManager.createNativeQuery(cuotaSql);
            cq.setParameter("especieId", especieId);
            if (amerbId != null) cq.setParameter("amerbId", amerbId);
            List<Object[]> cuotaRows = cq.getResultList();
            if (!cuotaRows.isEmpty()) {
                Object[] cr = cuotaRows.get(0);
                limiteKg = cr[0] != null ? ((Number) cr[0]).doubleValue() : null;
                cuotaPeriodo = cr[1] != null ? cr[1].toString() : null;
            }
        }

        // 3. Opciones de filtro: AMERBs y especies con declaraciones de área
        List<java.util.Map<String, Object>> amerbsDisponibles = listarOpciones(
            "SELECT DISTINCT a.id, a.nombre FROM declaracion_area d INNER JOIN amerb a ON a.id = d.amerb_id ORDER BY a.nombre");
        List<java.util.Map<String, Object>> especiesDisponibles = listarOpciones(
            "SELECT DISTINCT e.id, e.nombre FROM declaracion_area d INNER JOIN especie e ON e.id = d.especie_id ORDER BY e.nombre");

        java.util.Map<String, Object> out = new java.util.HashMap<>();
        out.put("serie", serie);
        out.put("totalAcumulado", Math.round(acumulado * 100.0) / 100.0);
        out.put("limiteKg", limiteKg);
        out.put("cuotaPeriodo", cuotaPeriodo);
        out.put("porcentajeCuota", (limiteKg != null && limiteKg > 0)
                ? Math.round(acumulado / limiteKg * 1000.0) / 10.0 : null);
        out.put("amerbsDisponibles", amerbsDisponibles);
        out.put("especiesDisponibles", especiesDisponibles);
        return out;
    }

    private List<java.util.Map<String, Object>> listarOpciones(String sql) {
        List<Object[]> rows = entityManager.createNativeQuery(sql).getResultList();
        return rows.stream().map(r -> {
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", r[0] != null ? ((Number) r[0]).longValue() : null);
            m.put("nombre", r[1] != null ? r[1].toString() : "");
            return m;
        }).collect(Collectors.toList());
    }

    /**
     * Indicador "Doble Operación" (AMERB #5): posibles duplicidades de captura del
     * mismo actor y especie, el mismo día, en ALA (recolector/armador) y AMERB (area),
     * con volúmenes dentro de una tolerancia. Solo lectura; para revisión del fiscalizador.
     * El criterio (mismo día + volumen ±tolerancia) es una definición inicial pendiente
     * de formalizar con SERNAPESCA; la tolerancia llega por parámetro (default 5%).
     */
    public java.util.Map<String, Object> getDobleOperacion(Date startDate, Date endDate, Double toleranciaPct) {
        double tolerancia = (toleranciaPct != null && toleranciaPct >= 0) ? toleranciaPct : 5.0;

        String dateFilter = "";
        if (startDate != null && endDate != null) {
            dateFilter = " AND area.fecha_declaracion BETWEEN :startDate AND :endDate";
        } else if (startDate != null) {
            dateFilter = " AND area.fecha_declaracion >= :startDate";
        } else if (endDate != null) {
            dateFilter = " AND area.fecha_declaracion <= :endDate";
        }

        String sql = "SELECT " +
            "area.fecha_declaracion AS fecha, " +
            "u.rut, u.nombres, u.apellidop, " +
            "e.nombre AS especie, " +
            "ala.tipo_ala, " +
            "ala.id AS ala_id, ala.desembarque AS kg_ala, " +
            "area.id AS area_id, area.desembarque AS kg_amerb, " +
            "am.nombre AS amerb_nombre, " +
            "ROUND(ABS(area.desembarque - ala.desembarque) / ala.desembarque * 100, 1) AS variacion_pct " +
            "FROM declaracion_area area " +
            "INNER JOIN (" +
            "    SELECT id, usuario_id, especie_id, fecha_declaracion, desembarque, 'RECOLECTOR' AS tipo_ala FROM declaracion_recolector " +
            "    UNION ALL " +
            "    SELECT id, usuario_id, especie_id, fecha_declaracion, desembarque, 'ARMADOR' AS tipo_ala FROM declaracion_armador " +
            ") ala " +
            "    ON ala.usuario_id = area.usuario_id " +
            "    AND ala.especie_id = area.especie_id " +
            "    AND ala.fecha_declaracion = area.fecha_declaracion " +
            "    AND ala.desembarque > 0 " +
            "    AND (ABS(area.desembarque - ala.desembarque) / ala.desembarque * 100) <= :tolerancia " +
            "INNER JOIN especie e ON e.id = area.especie_id " +
            "INNER JOIN usuario u ON u.id = area.usuario_id " +
            "LEFT JOIN amerb am ON am.id = area.amerb_id " +
            "WHERE 1=1" + dateFilter + " " +
            "ORDER BY area.fecha_declaracion DESC, u.rut";

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("tolerancia", tolerancia);
        if (startDate != null) query.setParameter("startDate", startDate);
        if (endDate != null) query.setParameter("endDate", endDate);

        List<Object[]> rows = query.getResultList();

        List<java.util.Map<String, Object>> detalle = rows.stream().map(row -> {
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("fecha", row[0] != null ? row[0].toString() : "");
            String actor = (row[2] != null ? row[2].toString() : "") + " " + (row[3] != null ? row[3].toString() : "");
            m.put("actor", actor.trim());
            m.put("rut", row[1] != null ? row[1].toString() : "");
            m.put("especie", row[4] != null ? row[4].toString() : "");
            m.put("tipoAla", row[5] != null ? row[5].toString() : "");
            m.put("alaId", row[6] != null ? ((Number) row[6]).longValue() : null);
            m.put("kgAla", row[7] != null ? ((Number) row[7]).doubleValue() : 0.0);
            m.put("areaId", row[8] != null ? ((Number) row[8]).longValue() : null);
            m.put("kgAmerb", row[9] != null ? ((Number) row[9]).doubleValue() : 0.0);
            m.put("amerb", row[10] != null ? row[10].toString() : "");
            m.put("variacionPct", row[11] != null ? ((Number) row[11]).doubleValue() : 0.0);
            return m;
        }).collect(Collectors.toList());

        java.util.Map<String, Object> out = new java.util.HashMap<>();
        out.put("toleranciaPct", tolerancia);
        out.put("totalCoincidencias", detalle.size());
        out.put("detalle", detalle);
        return out;
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

        // Base sobre todas las declaraciones para total y actores. 
        // Volumen, inconsistencias y casos abiertos se mantienen enfocados en el origen.
        String sql = "SELECT COUNT(id) as total_declaraciones, " +
            "COALESCE(SUM(CASE WHEN is_origen = 1 THEN desembarque ELSE 0 END), 0) as total_volumen, " +
            "COUNT(DISTINCT usuario_id) as actores_distintos, " +
            "SUM(CASE WHEN estado IN ('NEGOCIACION','RECHAZADA') THEN 1 ELSE 0 END) as casos_abiertos, " +
            "SUM(CASE WHEN estado = 'RECHAZADA' THEN 1 ELSE 0 END) as rechazadas, " +
            "SUM(CASE WHEN tipo_decl = 1 THEN 1 ELSE 0 END) as total_recolector, " +
            "SUM(CASE WHEN tipo_decl = 2 THEN 1 ELSE 0 END) as total_armador, " +
            "SUM(CASE WHEN tipo_decl = 3 THEN 1 ELSE 0 END) as total_area, " +
            "SUM(CASE WHEN tipo_decl = 4 THEN 1 ELSE 0 END) as total_comercializador, " +
            // Volumen comprado por el comercializador = suma de la cantidad de sus declaraciones
            "COALESCE(SUM(CASE WHEN tipo_decl = 4 THEN desembarque ELSE 0 END), 0) as volumen_comprado FROM (" +
            "    SELECT id, desembarque, fecha_declaracion, usuario_id, estado, 1 as is_origen, 1 as tipo_decl FROM declaracion_recolector " +
            "    UNION ALL " +
            "    SELECT id, desembarque, fecha_declaracion, usuario_id, estado, 1 as is_origen, 2 as tipo_decl FROM declaracion_armador " +
            "    UNION ALL " +
            "    SELECT id, desembarque, fecha_declaracion, usuario_id, estado, 1 as is_origen, 3 as tipo_decl FROM declaracion_area " +
            "    UNION ALL " +
            "    SELECT id, cantidad as desembarque, fecha_declaracion, usuario_id, estado, 0 as is_origen, 4 as tipo_decl FROM declaracion_comercializador " +
            "    UNION ALL " +
            "    SELECT id, cantidad as desembarque, fecha_ingreso_planta as fecha_declaracion, usuario_id, 'ACEPTADA' as estado, 0 as is_origen, 5 as tipo_decl FROM declaracion_planta_abastecimiento " +
            "    UNION ALL " +
            "    SELECT id, cantidad_producto as desembarque, fecha_produccion as fecha_declaracion, usuario_id, 'ACEPTADA' as estado, 0 as is_origen, 6 as tipo_decl FROM declaracion_planta_produccion " +
            "    UNION ALL " +
            "    SELECT id, cantidad as desembarque, fecha_declaracion_destino as fecha_declaracion, usuario_id, 'ACEPTADA' as estado, 0 as is_origen, 7 as tipo_decl FROM declaracion_planta_destino " +
            ") as decl " + dateFilter;
            
        Query query = entityManager.createNativeQuery(sql);
        if (startDate != null) query.setParameter("startDate", startDate);
        if (endDate != null) query.setParameter("endDate", endDate);

        Object[] result = (Object[]) query.getSingleResult();

        long total = result[0] != null ? ((Number) result[0]).longValue() : 0;
        long actores = result[2] != null ? ((Number) result[2]).longValue() : 0;
        long casosAbiertos = result[3] != null ? ((Number) result[3]).longValue() : 0;
        long rechazadas = result[4] != null ? ((Number) result[4]).longValue() : 0;
        long totalRecolector = result[5] != null ? ((Number) result[5]).longValue() : 0;
        long totalArmador = result[6] != null ? ((Number) result[6]).longValue() : 0;
        long totalArea = result[7] != null ? ((Number) result[7]).longValue() : 0;
        long totalComercializador = result[8] != null ? ((Number) result[8]).longValue() : 0;

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
        map.put("totalRecolector", totalRecolector);
        map.put("totalArmador", totalArmador);
        map.put("totalArea", totalArea);
        map.put("totalComercializador", totalComercializador);
        map.put("volumenComprado", result[9] != null ? ((Number) result[9]).doubleValue() : 0.0);
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

    // =========================================================================
    // FASE 4 — INDICADORES Y TRAZABILIDAD POR LOTE (folio_origen)
    // =========================================================================

    private Date toReportDate(Object o) {
        if (o == null) return null;
        if (o instanceof java.sql.Timestamp) return new Date(((java.sql.Timestamp) o).getTime());
        if (o instanceof java.sql.Date) return new Date(((java.sql.Date) o).getTime());
        if (o instanceof Date) return (Date) o;
        return null;
    }

    private int diffDays(Date start, Date end) {
        if (start == null || end == null) return 0;
        long diff = end.getTime() - start.getTime();
        if (diff <= 0) return 0;
        return (int) (diff / (1000L * 60 * 60 * 24));
    }

    private String sqlTrazabilidadLoteBase(Date startDate, Date endDate) {
        String filterRecolector = "";
        String filterArmador = "";
        String filterArea = "";

        if (startDate != null && endDate != null) {
            filterRecolector = " AND r.fecha_declaracion BETWEEN :startDate AND :endDate ";
            filterArmador = " AND a.fecha_declaracion BETWEEN :startDate AND :endDate ";
            filterArea = " AND ar.fecha_declaracion BETWEEN :startDate AND :endDate ";
        } else if (startDate != null) {
            filterRecolector = " AND r.fecha_declaracion >= :startDate ";
            filterArmador = " AND a.fecha_declaracion >= :startDate ";
            filterArea = " AND ar.fecha_declaracion >= :startDate ";
        } else if (endDate != null) {
            filterRecolector = " AND r.fecha_declaracion <= :endDate ";
            filterArmador = " AND a.fecha_declaracion <= :endDate ";
            filterArea = " AND ar.fecha_declaracion <= :endDate ";
        }

        return "SELECT " +
            "orig.folio_origen, orig.eslabon_origen, orig.actor_origen, orig.rut_origen, orig.especie, " +
            "orig.humedad_origen, orig.fecha_origen, orig.kg_origen, orig.captura_origen, orig.factor_aplicado, " +
            "orig.embarcacion, c.kg_comercializador, c.fecha_comercializador, c.actor_comercializador, " +
            "p.kg_planta, p.fecha_planta, p.actor_planta " +
            "FROM (" +
            "    SELECT 'RECOLECTOR' as eslabon_origen, r.folio_origen, MIN(r.fecha_declaracion) as fecha_origen, " +
            "    SUM(r.desembarque) as kg_origen, SUM(r.captura) as captura_origen, MAX(r.factor_aplicado) as factor_aplicado, " +
            "    MAX(e.nombre) as especie, COALESCE(MAX(h.nombre), 'HÚMEDO') as humedad_origen, " +
            "    MAX(TRIM(CONCAT(COALESCE(u.nombres, ''), ' ', COALESCE(u.apellidop, '')))) as actor_origen, " +
            "    MAX(u.rut) as rut_origen, NULL as embarcacion " +
            "    FROM declaracion_recolector r " +
            "    INNER JOIN usuario u ON r.usuario_id = u.id " +
            "    INNER JOIN especie e ON r.especie_id = e.id " +
            "    LEFT JOIN humedad_estado h ON r.humedad_estado_id = h.id " +
            "    WHERE r.folio_origen IS NOT NULL AND r.folio_origen <> '' " + filterRecolector +
            "    GROUP BY r.folio_origen " +
            "    UNION ALL " +
            "    SELECT 'ARMADOR' as eslabon_origen, a.folio_origen, MIN(a.fecha_declaracion) as fecha_origen, " +
            "    SUM(a.desembarque) as kg_origen, SUM(a.captura) as captura_origen, MAX(a.factor_aplicado) as factor_aplicado, " +
            "    MAX(e.nombre) as especie, COALESCE(MAX(h.nombre), 'HÚMEDO') as humedad_origen, " +
            "    MAX(TRIM(CONCAT(COALESCE(u.nombres, ''), ' ', COALESCE(u.apellidop, '')))) as actor_origen, " +
            "    MAX(u.rut) as rut_origen, MAX(emb.nombre) as embarcacion " +
            "    FROM declaracion_armador a " +
            "    INNER JOIN usuario u ON a.usuario_id = u.id " +
            "    INNER JOIN especie e ON a.especie_id = e.id " +
            "    LEFT JOIN humedad_estado h ON a.humedad_estado_id = h.id " +
            "    LEFT JOIN embarcacion emb ON a.embarcacion_id = emb.id " +
            "    WHERE a.folio_origen IS NOT NULL AND a.folio_origen <> '' " + filterArmador +
            "    GROUP BY a.folio_origen " +
            "    UNION ALL " +
            "    SELECT 'AREA' as eslabon_origen, ar.folio_origen, MIN(ar.fecha_declaracion) as fecha_origen, " +
            "    SUM(ar.desembarque) as kg_origen, SUM(ar.captura) as captura_origen, MAX(ar.factor_aplicado) as factor_aplicado, " +
            "    MAX(e.nombre) as especie, COALESCE(MAX(h.nombre), 'HÚMEDO') as humedad_origen, " +
            "    MAX(TRIM(CONCAT(COALESCE(u.nombres, ''), ' ', COALESCE(u.apellidop, '')))) as actor_origen, " +
            "    MAX(u.rut) as rut_origen, NULL as embarcacion " +
            "    FROM declaracion_area ar " +
            "    INNER JOIN usuario u ON ar.usuario_id = u.id " +
            "    INNER JOIN especie e ON ar.especie_id = e.id " +
            "    LEFT JOIN humedad_estado h ON ar.humedad_estado_id = h.id " +
            "    WHERE ar.folio_origen IS NOT NULL AND ar.folio_origen <> '' " + filterArea +
            "    GROUP BY ar.folio_origen " +
            ") orig " +
            "LEFT JOIN (" +
            "    SELECT dc.folio_origen, SUM(dc.cantidad) as kg_comercializador, MIN(dc.fecha_declaracion) as fecha_comercializador, " +
            "    MAX(TRIM(CONCAT(COALESCE(uc.nombres, ''), ' ', COALESCE(uc.apellidop, '')))) as actor_comercializador " +
            "    FROM declaracion_comercializador dc " +
            "    LEFT JOIN usuario uc ON dc.usuario_id = uc.id " +
            "    WHERE dc.folio_origen IS NOT NULL AND dc.folio_origen <> '' " +
            "    GROUP BY dc.folio_origen " +
            ") c ON orig.folio_origen = c.folio_origen " +
            "LEFT JOIN (" +
            "    SELECT pa.folio_origen, SUM(pa.cantidad) as kg_planta, MIN(pa.fecha_ingreso_planta) as fecha_planta, " +
            "    MAX(TRIM(CONCAT(COALESCE(up.nombres, ''), ' ', COALESCE(up.apellidop, '')))) as actor_planta " +
            "    FROM declaracion_planta_abastecimiento pa " +
            "    LEFT JOIN usuario up ON pa.usuario_id = up.id " +
            "    WHERE pa.folio_origen IS NOT NULL AND pa.folio_origen <> '' " +
            "    GROUP BY pa.folio_origen " +
            ") p ON orig.folio_origen = p.folio_origen";
    }

    public List<java.util.Map<String, Object>> getTrazabilidadLoteDetalle(
            Date startDate, Date endDate, String semaforoFiltro,
            Double umbralVariacion, Double mermaMinHumedo, Double mermaMaxSeco,
            int diasMinHumedo, int diasAmarilla, int diasNaranja, int diasRoja,
            String estadosSujetos, boolean bioPerdidaActivo) {

        String sql = sqlTrazabilidadLoteBase(startDate, endDate) + " ORDER BY orig.fecha_origen DESC LIMIT 200";
        Query query = entityManager.createNativeQuery(sql);
        if (startDate != null) query.setParameter("startDate", startDate);
        if (endDate != null) query.setParameter("endDate", endDate);

        List<Object[]> results = query.getResultList();
        Date now = new Date();
        List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();

        for (Object[] row : results) {
            String folioOrigen = row[0] != null ? row[0].toString() : "";
            String eslabonOrigen = row[1] != null ? row[1].toString() : "";
            String actorOrigen = row[2] != null ? row[2].toString() : "";
            String rutOrigen = row[3] != null ? row[3].toString() : "";
            String especie = row[4] != null ? row[4].toString() : "";
            String humedadOrigen = row[5] != null ? row[5].toString() : "HÚMEDO";
            Date fechaOrigen = toReportDate(row[6]);
            double kgOrigen = row[7] != null ? ((Number) row[7]).doubleValue() : 0.0;
            double capturaOrigen = row[8] != null ? ((Number) row[8]).doubleValue() : kgOrigen;
            Double factorAplicado = row[9] != null ? ((Number) row[9]).doubleValue() : null;
            String embarcacion = row[10] != null ? row[10].toString() : null;

            Double kgComercializador = row[11] != null ? ((Number) row[11]).doubleValue() : null;
            Date fechaComercializador = toReportDate(row[12]);
            String actorComercializador = row[13] != null ? row[13].toString() : null;

            Double kgPlanta = row[14] != null ? ((Number) row[14]).doubleValue() : null;
            Date fechaPlanta = toReportDate(row[15]);
            String actorPlanta = row[16] != null ? row[16].toString() : null;

            double kgDestino = kgPlanta != null ? kgPlanta : (kgComercializador != null ? kgComercializador : kgOrigen);
            double deltaKg = kgDestino - kgOrigen;
            double deltaPct = kgOrigen > 0 ? (deltaKg / kgOrigen) * 100.0 : 0.0;

            int diasTranscurridos = fechaPlanta != null
                    ? diffDays(fechaOrigen, fechaPlanta)
                    : (fechaComercializador != null ? diffDays(fechaOrigen, now) : 0);

            int diasEnBodega = (fechaComercializador != null && fechaPlanta != null)
                    ? diffDays(fechaComercializador, fechaPlanta)
                    : (fechaComercializador != null ? diffDays(fechaComercializador, now) : 0);

            boolean enBodegaVirtual = (fechaComercializador != null && fechaPlanta == null);

            // Semáforo de retención
            String semaforo = "VERDE";
            boolean esEstadoSujeto = estadosSujetos != null && estadosSujetos.toUpperCase().contains(humedadOrigen.toUpperCase());
            if (esEstadoSujeto && diasEnBodega >= diasRoja) {
                semaforo = "ROJA";
            } else if (esEstadoSujeto && diasEnBodega >= diasNaranja) {
                semaforo = "NARANJA";
            } else if (esEstadoSujeto && diasEnBodega >= diasAmarilla) {
                semaforo = "AMARILLA";
            }

            if (semaforoFiltro != null && !semaforoFiltro.isEmpty() && !semaforoFiltro.equalsIgnoreCase("TODOS")) {
                if (!semaforo.equalsIgnoreCase(semaforoFiltro)) {
                    continue;
                }
            }

            // Alerta de merma biológica (sólo si el interruptor maestro bio_perdida_activo está encendido)
            boolean alertaMerma = false;
            String motivoMerma = null;
            if (bioPerdidaActivo) {
                String humUpper = humedadOrigen.toUpperCase();
                if (humUpper.contains("HUMED") || humUpper.contains("HÚMED")) {
                    if (diasTranscurridos >= diasMinHumedo) {
                        if (deltaPct > -mermaMinHumedo) {
                            alertaMerma = true;
                            motivoMerma = String.format("Merma húmeda biológicamente inconsistente tras %d días (variación: %.1f%%, min esperado: -%.1f%%)",
                                    diasTranscurridos, deltaPct, mermaMinHumedo);
                        }
                    }
                } else if (humUpper.contains("SEC")) {
                    if (deltaPct < -mermaMaxSeco) {
                        alertaMerma = true;
                        motivoMerma = String.format("Merma seca anómala: alga deshidratada pierde más peso del tolerado (variación: %.1f%%, máx: -%.1f%%)",
                                deltaPct, mermaMaxSeco);
                    }
                }
            }

            boolean alertaVariacion = Math.abs(deltaPct) > (umbralVariacion != null ? umbralVariacion : 5.0);

            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("folioOrigen", folioOrigen);
            map.put("eslabonOrigen", eslabonOrigen);
            map.put("actorOrigen", actorOrigen);
            map.put("rutOrigen", rutOrigen);
            map.put("especie", especie);
            map.put("humedadOrigen", humedadOrigen);
            map.put("fechaOrigen", fechaOrigen);
            map.put("kgOrigen", Math.round(kgOrigen * 100.0) / 100.0);
            map.put("capturaOrigen", Math.round(capturaOrigen * 100.0) / 100.0);
            map.put("factorAplicado", factorAplicado);
            map.put("embarcacion", embarcacion);
            map.put("kgComercializador", kgComercializador != null ? Math.round(kgComercializador * 100.0) / 100.0 : null);
            map.put("fechaComercializador", fechaComercializador);
            map.put("actorComercializador", actorComercializador);
            map.put("kgPlanta", kgPlanta != null ? Math.round(kgPlanta * 100.0) / 100.0 : null);
            map.put("fechaPlanta", fechaPlanta);
            map.put("actorPlanta", actorPlanta);
            map.put("kgDestino", Math.round(kgDestino * 100.0) / 100.0);
            map.put("deltaKg", Math.round(deltaKg * 100.0) / 100.0);
            map.put("deltaPct", Math.round(deltaPct * 10.0) / 10.0);
            map.put("diasTranscurridos", diasTranscurridos);
            map.put("diasEnBodega", diasEnBodega);
            map.put("enBodegaVirtual", enBodegaVirtual);
            map.put("semaforo", semaforo);
            map.put("alertaMerma", alertaMerma);
            map.put("motivoMerma", motivoMerma);
            map.put("alertaVariacion", alertaVariacion);

            list.add(map);
        }

        return list;
    }

    public List<java.util.Map<String, Object>> getTrazabilidadLoteDetalle(
            Date startDate, Date endDate, String semaforoFiltro,
            Double umbralVariacion, Double mermaMinHumedo, Double mermaMaxSeco,
            int diasMinHumedo, int diasAmarilla, int diasNaranja, int diasRoja,
            String estadosSujetos) {
        return getTrazabilidadLoteDetalle(startDate, endDate, semaforoFiltro, umbralVariacion, mermaMinHumedo, mermaMaxSeco,
                diasMinHumedo, diasAmarilla, diasNaranja, diasRoja, estadosSujetos, true);
    }

    public java.util.Map<String, Object> getTrazabilidadLoteMetrics(
            Date startDate, Date endDate,
            Double umbralVariacion, Double mermaMinHumedo, Double mermaMaxSeco,
            int diasMinHumedo, int diasAmarilla, int diasNaranja, int diasRoja,
            String estadosSujetos, boolean bioPerdidaActivo) {

        List<java.util.Map<String, Object>> detalle = getTrazabilidadLoteDetalle(
                startDate, endDate, null,
                umbralVariacion, mermaMinHumedo, mermaMaxSeco,
                diasMinHumedo, diasAmarilla, diasNaranja, diasRoja,
                estadosSujetos, bioPerdidaActivo);

        long totalLotes = detalle.size();
        double sumKgOrigen = 0;
        double sumKgDestino = 0;
        double sumDeltaPct = 0;
        long enBodegaVirtual = 0;
        long verde = 0;
        long amarillo = 0;
        long naranja = 0;
        long rojo = 0;
        long alertasMerma = 0;
        long alertasVariacion = 0;

        for (java.util.Map<String, Object> m : detalle) {
            sumKgOrigen += (Double) m.get("kgOrigen");
            sumKgDestino += (Double) m.get("kgDestino");
            sumDeltaPct += (Double) m.get("deltaPct");
            if (Boolean.TRUE.equals(m.get("enBodegaVirtual"))) enBodegaVirtual++;
            String sem = (String) m.get("semaforo");
            if ("VERDE".equals(sem)) verde++;
            else if ("AMARILLA".equals(sem)) amarillo++;
            else if ("NARANJA".equals(sem)) naranja++;
            else if ("ROJA".equals(sem)) rojo++;
            if (Boolean.TRUE.equals(m.get("alertaMerma"))) alertasMerma++;
            if (Boolean.TRUE.equals(m.get("alertaVariacion"))) alertasVariacion++;
        }

        java.util.Map<String, Object> metrics = new java.util.HashMap<>();
        metrics.put("totalLotes", totalLotes);
        metrics.put("totalKgOrigen", Math.round(sumKgOrigen * 100.0) / 100.0);
        metrics.put("totalKgDestino", Math.round(sumKgDestino * 100.0) / 100.0);
        metrics.put("promedioVariacionPct", totalLotes > 0 ? Math.round((sumDeltaPct / totalLotes) * 10.0) / 10.0 : 0.0);
        metrics.put("lotesEnBodegaVirtual", enBodegaVirtual);
        metrics.put("semaforoVerde", verde);
        metrics.put("semaforoAmarillo", amarillo);
        metrics.put("semaforoNaranja", naranja);
        metrics.put("semaforoRojo", rojo);
        metrics.put("alertasMerma", alertasMerma);
        metrics.put("alertasVariacion", alertasVariacion);
        metrics.put("umbralVariacionPct", umbralVariacion);
        metrics.put("bioPerdidaActivo", bioPerdidaActivo);

        return metrics;
    }

    public java.util.Map<String, Object> getTrazabilidadLoteMetrics(
            Date startDate, Date endDate,
            Double umbralVariacion, Double mermaMinHumedo, Double mermaMaxSeco,
            int diasMinHumedo, int diasAmarilla, int diasNaranja, int diasRoja,
            String estadosSujetos) {
        return getTrazabilidadLoteMetrics(startDate, endDate, umbralVariacion, mermaMinHumedo, mermaMaxSeco,
                diasMinHumedo, diasAmarilla, diasNaranja, diasRoja, estadosSujetos, true);
    }

    // =========================================================================
    // INDICADOR 1 — DESEMBARQUE FÍSICO
    // =========================================================================

    private String sqlDesembarqueFisicoBase(Date startDate, Date endDate, Long especieId, Long comunaId, Long regionId,
                                           Long provinciaId, Long caletaId, Long usuarioId, Long macrozonaId, String perfil,
                                           boolean fRecolector, boolean fArmador, boolean fArea) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;

        if (fRecolector && (perfil == null || "TODOS".equalsIgnoreCase(perfil) || "RECOLECTOR".equalsIgnoreCase(perfil))) {
            sb.append("SELECT r.id, 'RECOLECTOR' as perfil, r.folio_origen as folio, r.fecha_declaracion as fecha, r.hora, ")
              .append("r.desembarque as kg, e.nombre as especie, COALESCE(h.nombre, 'HÚMEDO') as humedad, ")
              .append("TRIM(CONCAT(COALESCE(u.nombres, ''), ' ', COALESCE(u.apellidop, ''))) as actor, u.rut, ")
              .append("COALESCE(c.nombre, 'Sin Comuna') as comuna, COALESCE(prov.nombre, 'Sin Provincia') as provincia, COALESCE(reg.nombre, 'Sin Región') as region, ")
              .append("COALESCE(cal.nombre, 'Sin Caleta') as caleta, ")
              .append("u.id as usuario_id, cal.id as caleta_id, c.id as comuna_id, prov.id as provincia_id, reg.id as region_id, e.id as especie_id ")
              .append("FROM declaracion_recolector r ")
              .append("INNER JOIN usuario u ON r.usuario_id = u.id ")
              .append("INNER JOIN especie e ON r.especie_id = e.id ")
              .append("LEFT JOIN humedad_estado h ON r.humedad_estado_id = h.id ")
              .append("LEFT JOIN caleta cal ON r.caleta_id = cal.id ")
              .append("LEFT JOIN comuna c ON COALESCE(r.comuna_id, cal.comuna_id) = c.id ")
              .append("LEFT JOIN provincia prov ON c.provincia_id = prov.id ")
              .append("LEFT JOIN region reg ON COALESCE(c.region_id, cal.region_id) = reg.id ")
              .append("WHERE 1=1 ");
            if (startDate != null) sb.append("AND r.fecha_declaracion >= :startDate ");
            if (endDate != null) sb.append("AND r.fecha_declaracion <= :endDate ");
            if (especieId != null) sb.append("AND r.especie_id = :especieId ");
            if (comunaId != null) sb.append("AND c.id = :comunaId ");
            if (provinciaId != null) sb.append("AND prov.id = :provinciaId ");
            if (regionId != null) sb.append("AND reg.id = :regionId ");
            if (caletaId != null) sb.append("AND cal.id = :caletaId ");
            if (usuarioId != null) sb.append("AND u.id = :usuarioId ");
            if (macrozonaId != null) {
                sb.append("AND (reg.id IN (SELECT mr.region_id FROM macrozona_region mr WHERE mr.macrozona_id = :macrozonaId) OR EXISTS (SELECT 1 FROM macrozona mz WHERE mz.id = :macrozonaId AND mz.es_nacional = true)) ");
            }
            first = false;
        }

        if (fArmador && (perfil == null || "TODOS".equalsIgnoreCase(perfil) || "ARMADOR".equalsIgnoreCase(perfil))) {
            if (!first) sb.append(" UNION ALL ");
            sb.append("SELECT a.id, 'ARMADOR' as perfil, a.folio_origen as folio, a.fecha_declaracion as fecha, a.hora, ")
              .append("a.desembarque as kg, e.nombre as especie, COALESCE(h.nombre, 'HÚMEDO') as humedad, ")
              .append("TRIM(CONCAT(COALESCE(u.nombres, ''), ' ', COALESCE(u.apellidop, ''))) as actor, u.rut, ")
              .append("COALESCE(c.nombre, 'Sin Comuna') as comuna, COALESCE(prov.nombre, 'Sin Provincia') as provincia, COALESCE(reg.nombre, 'Sin Región') as region, ")
              .append("COALESCE(cal.nombre, 'Sin Caleta') as caleta, ")
              .append("u.id as usuario_id, cal.id as caleta_id, c.id as comuna_id, prov.id as provincia_id, reg.id as region_id, e.id as especie_id ")
              .append("FROM declaracion_armador a ")
              .append("INNER JOIN usuario u ON a.usuario_id = u.id ")
              .append("INNER JOIN especie e ON a.especie_id = e.id ")
              .append("LEFT JOIN humedad_estado h ON a.humedad_estado_id = h.id ")
              .append("LEFT JOIN caleta cal ON a.caleta_id = cal.id ")
              .append("LEFT JOIN comuna c ON cal.comuna_id = c.id ")
              .append("LEFT JOIN provincia prov ON c.provincia_id = prov.id ")
              .append("LEFT JOIN region reg ON COALESCE(c.region_id, cal.region_id) = reg.id ")
              .append("WHERE 1=1 ");
            if (startDate != null) sb.append("AND a.fecha_declaracion >= :startDate ");
            if (endDate != null) sb.append("AND a.fecha_declaracion <= :endDate ");
            if (especieId != null) sb.append("AND a.especie_id = :especieId ");
            if (comunaId != null) sb.append("AND c.id = :comunaId ");
            if (provinciaId != null) sb.append("AND prov.id = :provinciaId ");
            if (regionId != null) sb.append("AND reg.id = :regionId ");
            if (caletaId != null) sb.append("AND cal.id = :caletaId ");
            if (usuarioId != null) sb.append("AND u.id = :usuarioId ");
            if (macrozonaId != null) {
                sb.append("AND (reg.id IN (SELECT mr.region_id FROM macrozona_region mr WHERE mr.macrozona_id = :macrozonaId) OR EXISTS (SELECT 1 FROM macrozona mz WHERE mz.id = :macrozonaId AND mz.es_nacional = true)) ");
            }
            first = false;
        }

        if (fArea && (perfil == null || "TODOS".equalsIgnoreCase(perfil) || "AREA".equalsIgnoreCase(perfil))) {
            if (!first) sb.append(" UNION ALL ");
            sb.append("SELECT ar.id, 'AREA' as perfil, ar.folio_origen as folio, ar.fecha_declaracion as fecha, ar.hora, ")
              .append("ar.desembarque as kg, e.nombre as especie, COALESCE(h.nombre, 'HÚMEDO') as humedad, ")
              .append("TRIM(CONCAT(COALESCE(u.nombres, ''), ' ', COALESCE(u.apellidop, ''))) as actor, u.rut, ")
              .append("COALESCE(c.nombre, 'Sin Comuna') as comuna, COALESCE(prov.nombre, 'Sin Provincia') as provincia, COALESCE(reg.nombre, 'Sin Región') as region, ")
              .append("COALESCE(cal.nombre, 'Sin Caleta') as caleta, ")
              .append("u.id as usuario_id, cal.id as caleta_id, c.id as comuna_id, prov.id as provincia_id, reg.id as region_id, e.id as especie_id ")
              .append("FROM declaracion_area ar ")
              .append("INNER JOIN usuario u ON ar.usuario_id = u.id ")
              .append("INNER JOIN especie e ON ar.especie_id = e.id ")
              .append("LEFT JOIN humedad_estado h ON ar.humedad_estado_id = h.id ")
              .append("LEFT JOIN caleta cal ON ar.caleta_id = cal.id ")
              .append("LEFT JOIN amerb am ON ar.amerb_id = am.id ")
              .append("LEFT JOIN comuna c ON COALESCE(am.comuna_id, cal.comuna_id) = c.id ")
              .append("LEFT JOIN provincia prov ON c.provincia_id = prov.id ")
              .append("LEFT JOIN region reg ON COALESCE(c.region_id, cal.region_id) = reg.id ")
              .append("WHERE 1=1 ");
            if (startDate != null) sb.append("AND ar.fecha_declaracion >= :startDate ");
            if (endDate != null) sb.append("AND ar.fecha_declaracion <= :endDate ");
            if (especieId != null) sb.append("AND ar.especie_id = :especieId ");
            if (comunaId != null) sb.append("AND c.id = :comunaId ");
            if (provinciaId != null) sb.append("AND prov.id = :provinciaId ");
            if (regionId != null) sb.append("AND reg.id = :regionId ");
            if (caletaId != null) sb.append("AND cal.id = :caletaId ");
            if (usuarioId != null) sb.append("AND u.id = :usuarioId ");
            if (macrozonaId != null) {
                sb.append("AND (reg.id IN (SELECT mr.region_id FROM macrozona_region mr WHERE mr.macrozona_id = :macrozonaId) OR EXISTS (SELECT 1 FROM macrozona mz WHERE mz.id = :macrozonaId AND mz.es_nacional = true)) ");
            }
        }

        return sb.length() > 0 ? sb.toString() : "SELECT 1 WHERE 1=0";
    }

    private void bindDesembarqueFisicoParams(Query query, Date startDate, Date endDate, Long especieId,
                                            Long comunaId, Long regionId, Long provinciaId, Long caletaId,
                                            Long usuarioId, Long macrozonaId) {
        if (startDate != null) query.setParameter("startDate", startDate);
        if (endDate != null) query.setParameter("endDate", endDate);
        if (especieId != null) query.setParameter("especieId", especieId);
        if (comunaId != null) query.setParameter("comunaId", comunaId);
        if (regionId != null) query.setParameter("regionId", regionId);
        if (provinciaId != null) query.setParameter("provinciaId", provinciaId);
        if (caletaId != null) query.setParameter("caletaId", caletaId);
        if (usuarioId != null) query.setParameter("usuarioId", usuarioId);
        if (macrozonaId != null) query.setParameter("macrozonaId", macrozonaId);
    }

    public List<java.util.Map<String, Object>> getDesembarqueFisicoDetalle(
            Date startDate, Date endDate, Long especieId, Long comunaId, Long regionId,
            Long provinciaId, Long caletaId, Long usuarioId, Long macrozonaId, String perfil,
            Double umbralAtipico, boolean fRecolector, boolean fArmador, boolean fArea) {

        String baseSql = sqlDesembarqueFisicoBase(
                startDate, endDate, especieId, comunaId, regionId,
                provinciaId, caletaId, usuarioId, macrozonaId, perfil,
                fRecolector, fArmador, fArea);

        String sql = "SELECT * FROM (" + baseSql + ") as t ORDER BY fecha DESC, id DESC LIMIT 200";

        Query query = entityManager.createNativeQuery(sql);
        bindDesembarqueFisicoParams(query, startDate, endDate, especieId, comunaId, regionId,
                provinciaId, caletaId, usuarioId, macrozonaId);

        List<Object[]> results = query.getResultList();
        List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();
        double threshold = umbralAtipico != null ? umbralAtipico : 5000.0;

        for (Object[] row : results) {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", row[0]);
            map.put("perfil", row[1]);
            map.put("folio", row[2]);
            map.put("fecha", toReportDate(row[3]));
            map.put("hora", row[4] != null ? row[4].toString() : "");
            double kg = row[5] != null ? ((Number) row[5]).doubleValue() : 0.0;
            map.put("desembarqueKg", Math.round(kg * 100.0) / 100.0);
            map.put("especie", row[6]);
            map.put("humedad", row[7]);
            map.put("actor", row[8]);
            map.put("rut", row[9]);
            map.put("comuna", row[10] != null ? row[10] : "—");
            map.put("provincia", row[11] != null ? row[11] : "—");
            map.put("region", row[12] != null ? row[12] : "—");
            map.put("caleta", row[13] != null ? row[13] : "—");
            map.put("usuarioId", row[14]);
            map.put("caletaId", row[15]);
            map.put("comunaId", row[16]);
            map.put("provinciaId", row[17]);
            map.put("regionId", row[18]);
            map.put("especieId", row[19]);
            map.put("esAtipico", kg > threshold);
            list.add(map);
        }

        return list;
    }

    public List<java.util.Map<String, Object>> getDesembarqueFisicoDetalle(
            Date startDate, Date endDate, Long especieId, Long comunaId, Long regionId, String perfil,
            Double umbralAtipico, boolean fRecolector, boolean fArmador, boolean fArea) {
        return getDesembarqueFisicoDetalle(startDate, endDate, especieId, comunaId, regionId,
                null, null, null, null, perfil, umbralAtipico, fRecolector, fArmador, fArea);
    }

    public java.util.Map<String, Object> getDesembarqueFisicoMetrics(
            Date startDate, Date endDate, Long especieId, Long comunaId, Long regionId,
            Long provinciaId, Long caletaId, Long usuarioId, Long macrozonaId, String agruparPor, String perfil,
            Double umbralAtipico, boolean fRecolector, boolean fArmador, boolean fArea) {

        String baseSql = sqlDesembarqueFisicoBase(
                startDate, endDate, especieId, comunaId, regionId,
                provinciaId, caletaId, usuarioId, macrozonaId, perfil,
                fRecolector, fArmador, fArea);

        String sql = "SELECT " +
                     "especie, " +
                     "comuna, " +
                     "provincia, " +
                     "region, " +
                     "caleta, " +
                     "actor, " +
                     "rut, " +
                     "DATE_FORMAT(fecha, '%Y-%m-%d'), " +
                     "SUM(kg), " +
                     "COUNT(*), " +
                     "SUM(CASE WHEN kg > :umbral THEN 1 ELSE 0 END), " +
                     "SUM(CASE WHEN kg > :umbral THEN kg ELSE 0 END) " +
                     "FROM (" + baseSql + ") as t " +
                     "GROUP BY especie, comuna, provincia, region, caleta, actor, rut, DATE_FORMAT(fecha, '%Y-%m-%d')";

        double threshold = umbralAtipico != null ? umbralAtipico : 5000.0;
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("umbral", threshold);
        bindDesembarqueFisicoParams(query, startDate, endDate, especieId, comunaId, regionId,
                provinciaId, caletaId, usuarioId, macrozonaId);

        List<Object[]> rows = query.getResultList();

        double totalKg = 0;
        long totalDeclaraciones = 0;
        long totalAtipicos = 0;
        double volumenAtipico = 0;

        class Agregado {
            double kg = 0;
            long count = 0;
            long atip = 0;
            double volAtip = 0;
        }

        java.util.Map<String, Agregado> porEspecieMap = new java.util.HashMap<>();
        java.util.Map<String, Agregado> porCaletaMap = new java.util.HashMap<>();
        java.util.Map<String, Agregado> porRecolectorMap = new java.util.HashMap<>();
        java.util.Map<String, Agregado> porComunaMap = new java.util.HashMap<>();
        java.util.Map<String, Agregado> porProvinciaMap = new java.util.HashMap<>();
        java.util.Map<String, Agregado> porRegionMap = new java.util.HashMap<>();
        java.util.Map<String, Double> porFechaMap = new java.util.TreeMap<>();

        for (Object[] r : rows) {
            String esp = r[0] != null ? r[0].toString() : "Otros";
            String com = r[1] != null ? r[1].toString() : "Sin Comuna";
            String prov = r[2] != null ? r[2].toString() : "Sin Provincia";
            String reg = r[3] != null ? r[3].toString() : "Sin Región";
            String cal = r[4] != null ? r[4].toString() : "Sin Caleta";
            String act = r[5] != null ? r[5].toString() : "Anónimo";
            String rut = r[6] != null ? r[6].toString() : "";
            String fec = r[7] != null ? r[7].toString() : "Fecha no def";
            double kg = r[8] != null ? ((Number) r[8]).doubleValue() : 0.0;
            long count = r[9] != null ? ((Number) r[9]).longValue() : 0;
            long atip = r[10] != null ? ((Number) r[10]).longValue() : 0;
            double volAtip = r[11] != null ? ((Number) r[11]).doubleValue() : 0.0;

            totalKg += kg;
            totalDeclaraciones += count;
            totalAtipicos += atip;
            volumenAtipico += volAtip;

            String actorEtiqueta = (rut != null && !rut.isEmpty()) ? act + " (" + rut + ")" : act;

            Agregado agEsp = porEspecieMap.computeIfAbsent(esp, k -> new Agregado());
            agEsp.kg += kg; agEsp.count += count; agEsp.atip += atip; agEsp.volAtip += volAtip;

            Agregado agCal = porCaletaMap.computeIfAbsent(cal, k -> new Agregado());
            agCal.kg += kg; agCal.count += count; agCal.atip += atip; agCal.volAtip += volAtip;

            Agregado agRec = porRecolectorMap.computeIfAbsent(actorEtiqueta, k -> new Agregado());
            agRec.kg += kg; agRec.count += count; agRec.atip += atip; agRec.volAtip += volAtip;

            Agregado agCom = porComunaMap.computeIfAbsent(com, k -> new Agregado());
            agCom.kg += kg; agCom.count += count; agCom.atip += atip; agCom.volAtip += volAtip;

            Agregado agProv = porProvinciaMap.computeIfAbsent(prov, k -> new Agregado());
            agProv.kg += kg; agProv.count += count; agProv.atip += atip; agProv.volAtip += volAtip;

            Agregado agReg = porRegionMap.computeIfAbsent(reg, k -> new Agregado());
            agReg.kg += kg; agReg.count += count; agReg.atip += atip; agReg.volAtip += volAtip;

            porFechaMap.put(fec, porFechaMap.getOrDefault(fec, 0.0) + kg);
        }

        final double finalTotalKg = totalKg;

        java.util.function.BiFunction<String, java.util.Map<String, Agregado>, List<java.util.Map<String, Object>>> toListHelper =
                (propName, map) -> map.entrySet().stream()
                        .map(e -> {
                            java.util.Map<String, Object> m = new java.util.HashMap<>();
                            m.put(propName, e.getKey());
                            m.put("grupo", e.getKey());
                            m.put("totalKg", Math.round(e.getValue().kg * 100.0) / 100.0);
                            m.put("totalDeclaraciones", e.getValue().count);
                            m.put("promedioDeclaracionKg", e.getValue().count > 0 ? Math.round((e.getValue().kg / e.getValue().count) * 100.0) / 100.0 : 0.0);
                            m.put("declaracionesAtipicas", e.getValue().atip);
                            m.put("volumenAtipicoKg", Math.round(e.getValue().volAtip * 100.0) / 100.0);
                            m.put("porcentaje", finalTotalKg > 0 ? Math.round((e.getValue().kg / finalTotalKg) * 1000.0) / 10.0 : 0.0);
                            return m;
                        })
                        .sorted((a, b) -> Double.compare(((Number) b.get("totalKg")).doubleValue(), ((Number) a.get("totalKg")).doubleValue()))
                        .collect(Collectors.toList());

        List<java.util.Map<String, Object>> porEspecieList = toListHelper.apply("especie", porEspecieMap);
        List<java.util.Map<String, Object>> porCaletaList = toListHelper.apply("caleta", porCaletaMap);
        List<java.util.Map<String, Object>> porRecolectorList = toListHelper.apply("recolector", porRecolectorMap);
        List<java.util.Map<String, Object>> porComunaList = toListHelper.apply("comuna", porComunaMap);
        List<java.util.Map<String, Object>> porProvinciaList = toListHelper.apply("provincia", porProvinciaMap);
        List<java.util.Map<String, Object>> porRegionList = toListHelper.apply("region", porRegionMap);

        List<java.util.Map<String, Object>> porFechaList = porFechaMap.entrySet().stream().map(e -> {
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("fecha", e.getKey());
            m.put("totalKg", Math.round(e.getValue() * 100.0) / 100.0);
            return m;
        }).collect(Collectors.toList());

        String modoAgrupacion = (agruparPor != null && !agruparPor.isBlank()) ? agruparPor.toUpperCase() : "ESPECIE";
        List<java.util.Map<String, Object>> datosAgrupados;
        switch (modoAgrupacion) {
            case "CALETA":
                datosAgrupados = porCaletaList;
                break;
            case "RECOLECTOR":
            case "PERSONA":
                datosAgrupados = porRecolectorList;
                break;
            case "COMUNA":
                datosAgrupados = porComunaList;
                break;
            case "PROVINCIA":
                datosAgrupados = porProvinciaList;
                break;
            case "REGION":
                datosAgrupados = porRegionList;
                break;
            case "ESPECIE":
            default:
                modoAgrupacion = "ESPECIE";
                datosAgrupados = porEspecieList;
                break;
        }

        java.util.Map<String, Object> out = new java.util.HashMap<>();
        out.put("totalDesembarqueKg", Math.round(totalKg * 100.0) / 100.0);
        out.put("totalDeclaraciones", totalDeclaraciones);
        out.put("promedioDeclaracionKg", totalDeclaraciones > 0 ? Math.round((totalKg / totalDeclaraciones) * 100.0) / 100.0 : 0.0);
        out.put("declaracionesAtipicas", totalAtipicos);
        out.put("volumenAtipicoKg", Math.round(volumenAtipico * 100.0) / 100.0);
        out.put("umbralAtipicoKg", threshold);
        out.put("agruparPor", modoAgrupacion);
        out.put("datosAgrupados", datosAgrupados);
        out.put("porEspecie", porEspecieList);
        out.put("porCaleta", porCaletaList);
        out.put("porRecolector", porRecolectorList);
        out.put("porComuna", porComunaList);
        out.put("porProvincia", porProvinciaList);
        out.put("porRegion", porRegionList);
        out.put("porFecha", porFechaList);

        return out;
    }

    public java.util.Map<String, Object> getDesembarqueFisicoMetrics(
            Date startDate, Date endDate, Long especieId, Long comunaId, Long regionId, String perfil,
            Double umbralAtipico, boolean fRecolector, boolean fArmador, boolean fArea) {
        return getDesembarqueFisicoMetrics(startDate, endDate, especieId, comunaId, regionId,
                null, null, null, null, "ESPECIE", perfil, umbralAtipico, fRecolector, fArmador, fArea);
    }

    // =========================================================================
    // INDICADOR 2 — CAPTURA CORREGIDA
    // =========================================================================

    public java.util.Map<String, Object> getCapturaCorregidaMetrics(Date startDate, Date endDate, Long especieId) {
        String filterRec = "";
        String filterArm = "";
        String filterArea = "";

        if (startDate != null && endDate != null) {
            filterRec += " AND r.fecha_declaracion BETWEEN :startDate AND :endDate ";
            filterArm += " AND a.fecha_declaracion BETWEEN :startDate AND :endDate ";
            filterArea += " AND ar.fecha_declaracion BETWEEN :startDate AND :endDate ";
        } else if (startDate != null) {
            filterRec += " AND r.fecha_declaracion >= :startDate ";
            filterArm += " AND a.fecha_declaracion >= :startDate ";
            filterArea += " AND ar.fecha_declaracion >= :startDate ";
        } else if (endDate != null) {
            filterRec += " AND r.fecha_declaracion <= :endDate ";
            filterArm += " AND a.fecha_declaracion <= :endDate ";
            filterArea += " AND ar.fecha_declaracion <= :endDate ";
        }
        if (especieId != null) {
            filterRec += " AND r.especie_id = :especieId ";
            filterArm += " AND a.especie_id = :especieId ";
            filterArea += " AND ar.especie_id = :especieId ";
        }

        String sql = "SELECT e.nombre as especie, COALESCE(h.nombre, 'HÚMEDO') as humedad, " +
            "SUM(t.desembarque) as kg_desembarque, SUM(t.captura) as kg_captura, " +
            "AVG(COALESCE(t.factor_aplicado, 1.0)) as factor_promedio, COUNT(*) as declaraciones " +
            "FROM (" +
            "    SELECT r.especie_id, r.humedad_estado_id, r.desembarque, r.captura, r.factor_aplicado FROM declaracion_recolector r WHERE 1=1 " + filterRec +
            "    UNION ALL " +
            "    SELECT a.especie_id, a.humedad_estado_id, a.desembarque, a.captura, a.factor_aplicado FROM declaracion_armador a WHERE 1=1 " + filterArm +
            "    UNION ALL " +
            "    SELECT ar.especie_id, ar.humedad_estado_id, ar.desembarque, ar.captura, ar.factor_aplicado FROM declaracion_area ar WHERE 1=1 " + filterArea +
            ") as t " +
            "INNER JOIN especie e ON t.especie_id = e.id " +
            "LEFT JOIN humedad_estado h ON t.humedad_estado_id = h.id " +
            "GROUP BY e.nombre, h.nombre";

        Query query = entityManager.createNativeQuery(sql);
        if (startDate != null) query.setParameter("startDate", startDate);
        if (endDate != null) query.setParameter("endDate", endDate);
        if (especieId != null) query.setParameter("especieId", especieId);

        List<Object[]> rows = query.getResultList();
        double totalDesembarque = 0;
        double totalCaptura = 0;

        List<java.util.Map<String, Object>> desglose = new java.util.ArrayList<>();
        for (Object[] r : rows) {
            String esp = r[0] != null ? r[0].toString() : "";
            String hum = r[1] != null ? r[1].toString() : "HÚMEDO";
            double des = r[2] != null ? ((Number) r[2]).doubleValue() : 0.0;
            double cap = r[3] != null ? ((Number) r[3]).doubleValue() : des;
            double factor = r[4] != null ? ((Number) r[4]).doubleValue() : 1.0;
            long count = r[5] != null ? ((Number) r[5]).longValue() : 0;

            totalDesembarque += des;
            totalCaptura += cap;

            java.util.Map<String, Object> item = new java.util.HashMap<>();
            item.put("especie", esp);
            item.put("humedad", hum);
            item.put("desembarqueKg", Math.round(des * 100.0) / 100.0);
            item.put("capturaKg", Math.round(cap * 100.0) / 100.0);
            item.put("factorPromedio", Math.round(factor * 1000.0) / 1000.0);
            item.put("declaraciones", count);
            desglose.add(item);
        }

        java.util.Map<String, Object> out = new java.util.HashMap<>();
        out.put("totalDesembarqueKg", Math.round(totalDesembarque * 100.0) / 100.0);
        out.put("totalCapturaKg", Math.round(totalCaptura * 100.0) / 100.0);
        out.put("factorPromedioGlobal", totalDesembarque > 0 ? Math.round((totalCaptura / totalDesembarque) * 1000.0) / 100.0 : 1.0);
        out.put("desglose", desglose);

        return out;
    }

    // =========================================================================
    // INDICADOR 4 — LÍMITE DE EXTRACCIÓN DIARIO (LED)
    // =========================================================================

    public java.util.Map<String, Object> getLimiteExtraccionDiarioMetrics(Date fecha) {
        Date targetDate = fecha != null ? fecha : new Date();

        // 1. Obtener todas las reglas LED activas y vigentes
        String sqlReglas = "SELECT id, nombre_regla, limite_kg, margen_tolerancia_pct, modo_accion, unidad_agregacion, " +
                           "especie_id, extraccion_tipo_id, region_id, perfil_aplicable " +
                           "FROM limite_extraccion_diario_config WHERE activo = true " +
                           "AND (vigencia_inicio IS NULL OR vigencia_inicio <= :fecha) " +
                           "AND (vigencia_fin IS NULL OR vigencia_fin >= :fecha) " +
                           "ORDER BY id ASC";
        Query qReglas = entityManager.createNativeQuery(sqlReglas);
        qReglas.setParameter("fecha", targetDate);
        List<Object[]> listaReglas = qReglas.getResultList();

        if (listaReglas.isEmpty()) {
            java.util.Map<String, Object> outVacio = new java.util.HashMap<>();
            outVacio.put("fecha", targetDate);
            outVacio.put("sinReglaConfigurada", true);
            outVacio.put("nombreRegla", "Sin regla LED configurada");
            outVacio.put("limiteOficialKg", null);
            outVacio.put("toleranciaPct", 0.0);
            outVacio.put("limiteConToleranciaKg", null);
            outVacio.put("modoAccion", "INACTIVO");
            outVacio.put("unidadAgregacion", "EMBARCACION");
            outVacio.put("totalMonitoreados", 0);
            outVacio.put("dentroLimite", 0);
            outVacio.put("advertencia", 0);
            outVacio.put("enTolerancia", 0);
            outVacio.put("excedidos", 0);
            outVacio.put("detalle", java.util.Collections.emptyList());
            return outVacio;
        }

        // Regla representativa para la cabecera
        Object[] reglaCabecera = listaReglas.get(0);
        String nombreReglaGlobal = reglaCabecera[1] != null ? reglaCabecera[1].toString() : "Límite Oficial Diario";
        double limiteOficialGlobal = reglaCabecera[2] != null ? ((Number) reglaCabecera[2]).doubleValue() : 0.0;
        double toleranciaGlobal = reglaCabecera[3] != null ? ((Number) reglaCabecera[3]).doubleValue() : 0.0;
        String modoAccionGlobal = reglaCabecera[4] != null ? reglaCabecera[4].toString() : "SOLO_ALERTA";
        String unidadAgregacionGlobal = reglaCabecera[5] != null ? reglaCabecera[5].toString() : "EMBARCACION";
        double limiteConToleranciaGlobal = limiteOficialGlobal * (1.0 + (toleranciaGlobal / 100.0));

        // 2. Consultar faenas del día agrupadas por embarcación, especie, método y región
        String sqlArmador = "SELECT emb.id, emb.nombre, emb.matricula, e.id as especie_id, e.nombre as especie, " +
            "ext.id as extraccion_tipo_id, ext.nombre as metodo, " +
            "cal.region_id as region_id, SUM(a.desembarque) as kg_total " +
            "FROM declaracion_armador a " +
            "INNER JOIN embarcacion emb ON a.embarcacion_id = emb.id " +
            "INNER JOIN especie e ON a.especie_id = e.id " +
            "LEFT JOIN extraccion_tipo ext ON a.extraccion_tipo_id = ext.id " +
            "LEFT JOIN caleta cal ON a.caleta_id = cal.id " +
            "WHERE a.fecha_declaracion = :fecha " +
            "GROUP BY emb.id, emb.nombre, emb.matricula, e.id, e.nombre, ext.id, ext.nombre, cal.region_id";

        Query qArm = entityManager.createNativeQuery(sqlArmador);
        qArm.setParameter("fecha", targetDate);
        List<Object[]> rows = qArm.getResultList();

        List<java.util.Map<String, Object>> detalle = new java.util.ArrayList<>();
        int dentro = 0;
        int advertencia = 0;
        int enTolerancia = 0;
        int excedidos = 0;

        for (Object[] r : rows) {
            String embNom = r[1] != null ? r[1].toString() : "Embarcación " + r[0];
            String mat = r[2] != null ? r[2].toString() : "—";
            Long espId = r[3] != null ? ((Number) r[3]).longValue() : null;
            String esp = r[4] != null ? r[4].toString() : "—";
            Long extId = r[5] != null ? ((Number) r[5]).longValue() : null;
            String met = r[6] != null ? r[6].toString() : "No especificado";
            Long regId = r[7] != null ? ((Number) r[7]).longValue() : null;
            double kg = r[8] != null ? ((Number) r[8]).doubleValue() : 0.0;

            // Buscar la regla más específica aplicable a esta faena
            Object[] reglaEspecifica = null;
            int mejorScore = -1;

            for (Object[] reg : listaReglas) {
                Long rEspId = reg[6] != null ? ((Number) reg[6]).longValue() : null;
                Long rExtId = reg[7] != null ? ((Number) reg[7]).longValue() : null;
                Long rRegId = reg[8] != null ? ((Number) reg[8]).longValue() : null;
                String rPerfil = reg[9] != null ? reg[9].toString() : null;

                if (rPerfil != null && !"TODOS".equalsIgnoreCase(rPerfil) && !"ARMADOR".equalsIgnoreCase(rPerfil)) {
                    continue;
                }
                if (rEspId != null && (espId == null || !rEspId.equals(espId))) {
                    continue;
                }
                if (rExtId != null && (extId == null || !rExtId.equals(extId))) {
                    continue;
                }
                if (rRegId != null && (regId == null || !rRegId.equals(regId))) {
                    continue;
                }

                int score = (rEspId != null ? 4 : 0) + (rExtId != null ? 2 : 0) + (rRegId != null ? 1 : 0);
                if (score > mejorScore) {
                    mejorScore = score;
                    reglaEspecifica = reg;
                }
            }

            double limKg = limiteOficialGlobal;
            double tolPct = toleranciaGlobal;
            String modo = modoAccionGlobal;
            String nomRegla = nombreReglaGlobal;

            if (reglaEspecifica != null) {
                nomRegla = reglaEspecifica[1] != null ? reglaEspecifica[1].toString() : nomRegla;
                limKg = reglaEspecifica[2] != null ? ((Number) reglaEspecifica[2]).doubleValue() : limKg;
                tolPct = reglaEspecifica[3] != null ? ((Number) reglaEspecifica[3]).doubleValue() : tolPct;
                modo = reglaEspecifica[4] != null ? reglaEspecifica[4].toString() : modo;
            }

            double limConTol = limKg * (1.0 + (tolPct / 100.0));
            double pct = limKg > 0 ? (kg / limKg) * 100.0 : 0.0;

            String estado;
            if (kg > limConTol) {
                estado = "EXCEDIDO";
                excedidos++;
            } else if (kg > limKg) {
                estado = "EN_TOLERANCIA";
                enTolerancia++;
            } else if (pct >= 80.0) {
                estado = "ADVERTENCIA";
                advertencia++;
            } else {
                estado = "NORMAL";
                dentro++;
            }

            java.util.Map<String, Object> item = new java.util.HashMap<>();
            item.put("id", r[0]);
            item.put("nombre", embNom);
            item.put("matricula", mat);
            item.put("especie", esp);
            item.put("metodo", met);
            item.put("kgDesembarcados", Math.round(kg * 100.0) / 100.0);
            item.put("limiteKg", limKg);
            item.put("limiteConToleranciaKg", Math.round(limConTol * 100.0) / 100.0);
            item.put("porcentajeConsumido", Math.round(pct * 10.0) / 10.0);
            item.put("estado", estado);
            item.put("modoAccion", modo);
            item.put("nombreRegla", nomRegla);
            detalle.add(item);
        }

        java.util.Map<String, Object> out = new java.util.HashMap<>();
        out.put("fecha", targetDate);
        out.put("sinReglaConfigurada", false);
        out.put("nombreRegla", nombreReglaGlobal);
        out.put("limiteOficialKg", limiteOficialGlobal);
        out.put("toleranciaPct", toleranciaGlobal);
        out.put("limiteConToleranciaKg", Math.round(limiteConToleranciaGlobal * 100.0) / 100.0);
        out.put("modoAccion", modoAccionGlobal);
        out.put("unidadAgregacion", unidadAgregacionGlobal);
        out.put("totalMonitoreados", detalle.size());
        out.put("dentroLimite", dentro);
        out.put("advertencia", advertencia);
        out.put("enTolerancia", enTolerancia);
        out.put("excedidos", excedidos);
        out.put("detalle", detalle);

        return out;
    }

    // =========================================================================
    // INDICADOR 6 — RETENCIÓN EN BODEGA VIRTUAL
    // =========================================================================

    public java.util.Map<String, Object> getRetencionBodegaMetrics(
            int diasAmarilla, int diasNaranja, int diasRoja, String estadosSujetos) {

        List<java.util.Map<String, Object>> lotes = getTrazabilidadLoteDetalle(
                null, null, null,
                5.0, 5.0, 3.0, 3, diasAmarilla, diasNaranja, diasRoja, estadosSujetos);

        List<java.util.Map<String, Object>> retenidos = lotes.stream()
                .filter(l -> Boolean.TRUE.equals(l.get("enBodegaVirtual")))
                .collect(Collectors.toList());

        long verde = 0;
        long amarillo = 0;
        long naranja = 0;
        long rojo = 0;
        double totalKg = 0;

        for (java.util.Map<String, Object> r : retenidos) {
            totalKg += (Double) r.get("kgDestino");
            String sem = (String) r.get("semaforo");
            if ("VERDE".equals(sem)) verde++;
            else if ("AMARILLA".equals(sem)) amarillo++;
            else if ("NARANJA".equals(sem)) naranja++;
            else if ("ROJA".equals(sem)) rojo++;
        }

        java.util.Map<String, Object> out = new java.util.HashMap<>();
        out.put("totalLotesEnBodega", retenidos.size());
        out.put("totalKgEnBodega", Math.round(totalKg * 100.0) / 100.0);
        out.put("diasAmarilla", diasAmarilla);
        out.put("diasNaranja", diasNaranja);
        out.put("diasRoja", diasRoja);
        out.put("estadosSujetos", estadosSujetos);
        out.put("semaforoVerde", verde);
        out.put("semaforoAmarillo", amarillo);
        out.put("semaforoNaranja", naranja);
        out.put("semaforoRojo", rojo);
        out.put("lotes", retenidos);

        return out;
    }
}

