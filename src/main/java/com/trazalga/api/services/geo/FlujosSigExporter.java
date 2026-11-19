package com.trazalga.api.services.geo;

import com.trazalga.api.ApiApplication;
import com.trazalga.api.services.ReportService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ZE.1 · Exportador SIG de flujos origen -> [comercializador] -> planta.
 * Genera libro Excel georreferenciado (SXSSFWorkbook) con 4 hojas compatibles
 * con conversión a shapefile (campos ASCII en mayúsculas <= 10 caracteres)
 * y exportación CSV alineada.
 */
public final class FlujosSigExporter {

    public static final String[] HEADERS_DECLARACIONES = {
            "FOLIO_ORI", "TIPO_ORI", "FECHA_ORI", "ESPECIE", "DESEMB_KG", "CAPT_KG",
            "NOM_ORI", "LAT_ORI", "LON_ORI", "FTE_ORI",
            "FOLIO_COM", "LAT_COM", "LON_COM", "FTE_COM",
            "NOM_PLA", "COD_PLA", "COM_PLA", "LAT_PLA", "LON_PLA", "FTE_PLA",
            "FOLIO_REC", "FECHA_REC", "ROMANA_KG", "REF_ORI"
    };

    public static final String[] HEADERS_TRAMOS = {
            "ID_TRAMO", "FOLIO_ORI", "FECHA_ORI", "TRAMO", "FOLIO_INI", "FOLIO_FIN",
            "X_INI", "Y_INI", "X_FIN", "Y_FIN", "ESPECIE", "KG"
    };

    public static final String[] HEADERS_SIN_COORD = {
            "FOLIO_ORI", "TIPO_ORI", "FECHA_ORI", "ESLABON", "FOLIO_ESL", "NOM_ESL", "COM_PLA", "MOTIVO"
    };

    private static final DateTimeFormatter TS_CHILE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    private FlujosSigExporter() {
    }

    private record PuntoNodo(String codigoTipo, String folio, double lat, double lon) {
    }

    private record TramoRow(
            int idTramo,
            String folioOri,
            String fechaOri,
            String tramo,
            String folioIni,
            String folioFin,
            double xIni,
            double yIni,
            double xFin,
            double yFin,
            String especie,
            Double kg) {
    }

    private record SinCoordRow(
            String folioOri,
            String tipoOri,
            String fechaOri,
            String eslabon,
            String folioEsl,
            String nomEsl,
            String comPla,
            String motivo) {
    }

    public static byte[] exportarExcel(
            Map<String, Object> flujosData,
            Date startDate,
            Date endDate,
            Long especieId,
            Long regionId,
            Long regionDestinoId) {
        List<Map<String, Object>> declaraciones = extraerDeclaraciones(flujosData);
        List<TramoRow> tramos = new ArrayList<>();
        List<SinCoordRow> sinCoords = new ArrayList<>();
        construirTramosYSinCoords(declaraciones, tramos, sinCoords);

        try (SXSSFWorkbook wb = new SXSSFWorkbook(100); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle headerStyle = crearEstiloEncabezado(wb);

            Sheet shDecl = wb.createSheet("DECLARACIONES");
            escribirFilaEncabezado(shDecl, HEADERS_DECLARACIONES, headerStyle);
            int rowIdx = 1;
            for (Map<String, Object> d : declaraciones) {
                Row row = shDecl.createRow(rowIdx++);
                String fOri = strOrNull(d.get("origenFolio"));
                String tipoOri = strOrNull(d.get("origenTipo"));
                String fechaOri = formatearFechaIso(d.get("origenFecha"));
                String especie = strOrNull(d.get("especie"));
                Double desembKg = toDoubleOrNull(d.get("desembarqueKg"));
                Double captKg = toDoubleOrNull(d.get("capturaKg"));
                String nomOri = strOrNull(d.get("origenNombre"));
                Double latOri = coordValidaOrNull(d.get("origenLatitud"), d.get("origenLongitud"), true);
                Double lonOri = coordValidaOrNull(d.get("origenLatitud"), d.get("origenLongitud"), false);
                String fteOri = strOrNull(d.get("origenFuente"));

                String folioCom = strOrNull(d.get("comercializadorFolio"));
                Double latCom = coordValidaOrNull(d.get("comercializadorLatitud"), d.get("comercializadorLongitud"), true);
                Double lonCom = coordValidaOrNull(d.get("comercializadorLatitud"), d.get("comercializadorLongitud"), false);
                String fteCom = strOrNull(d.get("comercializadorFuente"));

                String nomPla = strOrNull(d.get("plantaNombre"));
                String codPla = strOrNull(d.get("plantaCodigo"));
                String comPla = strOrNull(d.get("plantaComuna"));
                Double latPla = coordValidaOrNull(d.get("plantaLatitud"), d.get("plantaLongitud"), true);
                Double lonPla = coordValidaOrNull(d.get("plantaLatitud"), d.get("plantaLongitud"), false);
                String ftePla = strOrNull(d.get("plantaFuente"));

                String fRec = strOrNull(d.get("recepcionFolio"));
                String fechaRec = formatearFechaIso(d.get("recepcionFecha"));
                Double romanaKg = toDoubleOrNull(d.get("pesoRomanaKg"));
                String refOri = resolverRefOri(d);

                setTextCell(row, 0, fOri);
                setTextCell(row, 1, tipoOri);
                setTextCell(row, 2, fechaOri);
                setTextCell(row, 3, especie);
                setNumericCell(row, 4, desembKg);
                setNumericCell(row, 5, captKg);
                setTextCell(row, 6, nomOri);
                setNumericCell(row, 7, latOri);
                setNumericCell(row, 8, lonOri);
                setTextCell(row, 9, fteOri);
                setTextCell(row, 10, folioCom);
                setNumericCell(row, 11, latCom);
                setNumericCell(row, 12, lonCom);
                setTextCell(row, 13, fteCom);
                setTextCell(row, 14, nomPla);
                setTextCell(row, 15, codPla);
                setTextCell(row, 16, comPla);
                setNumericCell(row, 17, latPla);
                setNumericCell(row, 18, lonPla);
                setTextCell(row, 19, ftePla);
                setTextCell(row, 20, fRec);
                setTextCell(row, 21, fechaRec);
                setNumericCell(row, 22, romanaKg);
                setTextCell(row, 23, refOri);
            }
            configurarAnchos(shDecl, HEADERS_DECLARACIONES.length);

            Sheet shTramos = wb.createSheet("TRAMOS");
            escribirFilaEncabezado(shTramos, HEADERS_TRAMOS, headerStyle);
            int rT = 1;
            for (TramoRow t : tramos) {
                Row row = shTramos.createRow(rT++);
                setNumericCell(row, 0, (double) t.idTramo());
                setTextCell(row, 1, t.folioOri());
                setTextCell(row, 2, t.fechaOri());
                setTextCell(row, 3, t.tramo());
                setTextCell(row, 4, t.folioIni());
                setTextCell(row, 5, t.folioFin());
                setNumericCell(row, 6, t.xIni());
                setNumericCell(row, 7, t.yIni());
                setNumericCell(row, 8, t.xFin());
                setNumericCell(row, 9, t.yFin());
                setTextCell(row, 10, t.especie());
                setNumericCell(row, 11, t.kg());
            }
            configurarAnchos(shTramos, HEADERS_TRAMOS.length);

            Sheet shSin = wb.createSheet("SIN_COORD");
            escribirFilaEncabezado(shSin, HEADERS_SIN_COORD, headerStyle);
            int rS = 1;
            for (SinCoordRow s : sinCoords) {
                Row row = shSin.createRow(rS++);
                setTextCell(row, 0, s.folioOri());
                setTextCell(row, 1, s.tipoOri());
                setTextCell(row, 2, s.fechaOri());
                setTextCell(row, 3, s.eslabon());
                setTextCell(row, 4, s.folioEsl());
                setTextCell(row, 5, s.nomEsl());
                setTextCell(row, 6, s.comPla());
                setTextCell(row, 7, s.motivo());
            }
            configurarAnchos(shSin, HEADERS_SIN_COORD.length);

            Sheet shLeeme = wb.createSheet("LEEME");
            escribirHojaLeeme(shLeeme, headerStyle, startDate, endDate, especieId, regionId, regionDestinoId,
                    declaraciones.size(), tramos.size(), sinCoords.size());
            configurarAnchos(shLeeme, 4);

            wb.write(out);
            wb.dispose();
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Error generando libro Excel georreferenciado", ex);
        }
    }

    public static byte[] exportarCsv(Map<String, Object> flujosData) {
        List<Map<String, Object>> declaraciones = extraerDeclaraciones(flujosData);
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        for (int i = 0; i < HEADERS_DECLARACIONES.length; i++) {
            if (i > 0) sb.append(';');
            sb.append(escaparCsv(HEADERS_DECLARACIONES[i]));
        }
        sb.append("\r\n");

        for (Map<String, Object> d : declaraciones) {
            String[] cols = {
                    strOrNull(d.get("origenFolio")),
                    strOrNull(d.get("origenTipo")),
                    formatearFechaIso(d.get("origenFecha")),
                    strOrNull(d.get("especie")),
                    formatearNumero(toDoubleOrNull(d.get("desembarqueKg")), 2),
                    formatearNumero(toDoubleOrNull(d.get("capturaKg")), 2),
                    strOrNull(d.get("origenNombre")),
                    formatearNumero(coordValidaOrNull(d.get("origenLatitud"), d.get("origenLongitud"), true), 6),
                    formatearNumero(coordValidaOrNull(d.get("origenLatitud"), d.get("origenLongitud"), false), 6),
                    strOrNull(d.get("origenFuente")),
                    strOrNull(d.get("comercializadorFolio")),
                    formatearNumero(coordValidaOrNull(d.get("comercializadorLatitud"), d.get("comercializadorLongitud"), true), 6),
                    formatearNumero(coordValidaOrNull(d.get("comercializadorLatitud"), d.get("comercializadorLongitud"), false), 6),
                    strOrNull(d.get("comercializadorFuente")),
                    strOrNull(d.get("plantaNombre")),
                    strOrNull(d.get("plantaCodigo")),
                    strOrNull(d.get("plantaComuna")),
                    formatearNumero(coordValidaOrNull(d.get("plantaLatitud"), d.get("plantaLongitud"), true), 6),
                    formatearNumero(coordValidaOrNull(d.get("plantaLatitud"), d.get("plantaLongitud"), false), 6),
                    strOrNull(d.get("plantaFuente")),
                    strOrNull(d.get("recepcionFolio")),
                    formatearFechaIso(d.get("recepcionFecha")),
                    formatearNumero(toDoubleOrNull(d.get("pesoRomanaKg")), 2),
                    resolverRefOri(d)
            };
            for (int i = 0; i < cols.length; i++) {
                if (i > 0) sb.append(';');
                sb.append(escaparCsv(cols[i]));
            }
            sb.append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extraerDeclaraciones(Map<String, Object> flujosData) {
        if (flujosData == null) {
            return List.of();
        }
        Object raw = flujosData.get("declaraciones");
        if (raw instanceof List<?> lista) {
            List<Map<String, Object>> res = new ArrayList<>(lista.size());
            for (Object item : lista) {
                if (item instanceof Map<?, ?> map) {
                    res.add((Map<String, Object>) map);
                }
            }
            return res;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static void construirTramosYSinCoords(
            List<Map<String, Object>> declaraciones,
            List<TramoRow> tramos,
            List<SinCoordRow> sinCoords) {
        int nextIdTramo = 1;

        for (Map<String, Object> d : declaraciones) {
            String folioOri = strOrNull(d.get("origenFolio"));
            String tipoOri = strOrNull(d.get("origenTipo"));
            String fechaOri = formatearFechaIso(d.get("origenFecha"));
            String especie = strOrNull(d.get("especie"));
            Double kg = toDoubleOrNull(d.get("desembarqueKg"));

            String nomOri = strOrNull(d.get("origenNombre"));
            Double origLat = toDoubleOrNull(d.get("origenLatitud"));
            Double origLon = toDoubleOrNull(d.get("origenLongitud"));
            boolean origOk = ReportService.esCoordenadaChileValida(origLat, origLon);

            String nomPla = strOrNull(d.get("plantaNombre"));
            String codPla = strOrNull(d.get("plantaCodigo"));
            String comPla = strOrNull(d.get("plantaComuna"));
            String fRec = strOrNull(d.get("recepcionFolio"));
            Double plLat = toDoubleOrNull(d.get("plantaLatitud"));
            Double plLon = toDoubleOrNull(d.get("plantaLongitud"));
            boolean plOk = ReportService.esCoordenadaChileValida(plLat, plLon);

            if (!origOk) {
                sinCoords.add(new SinCoordRow(
                        folioOri, tipoOri, fechaOri, "ORIGEN", folioOri, nomOri, comPla,
                        "Origen sin coordenadas validas en Chile"));
            }

            List<Map<String, Object>> hops = new ArrayList<>();
            Object rawHops = d.get("comercializadores");
            if (rawHops instanceof List<?> listaHops) {
                for (Object h : listaHops) {
                    if (h instanceof Map<?, ?> hm) {
                        hops.add((Map<String, Object>) hm);
                    }
                }
            }

            PuntoNodo prev = origOk ? new PuntoNodo("ORI", folioOri, origLat, origLon) : null;
            for (Map<String, Object> hop : hops) {
                String cFolio = strOrNull(hop.get("folio"));
                Double cLat = toDoubleOrNull(hop.get("latitud"));
                Double cLon = toDoubleOrNull(hop.get("longitud"));
                if (ReportService.esCoordenadaChileValida(cLat, cLon)) {
                    PuntoNodo curr = new PuntoNodo("COM", cFolio, cLat, cLon);
                    if (prev != null) {
                        tramos.add(new TramoRow(
                                nextIdTramo++,
                                folioOri,
                                fechaOri,
                                prev.codigoTipo() + "-COM",
                                prev.folio(),
                                curr.folio(),
                                prev.lon(),
                                prev.lat(),
                                curr.lon(),
                                curr.lat(),
                                especie,
                                kg));
                    }
                    prev = curr;
                } else {
                    sinCoords.add(new SinCoordRow(
                            folioOri, tipoOri, fechaOri, "COMERCIALIZADOR", cFolio, "Comercializador", comPla,
                            "Comercializador sin coordenadas validas en Chile"));
                }
            }

            if (!plOk) {
                sinCoords.add(new SinCoordRow(
                        folioOri, tipoOri, fechaOri, "PLANTA",
                        firstNonBlank(fRec, codPla, nomPla), nomPla, comPla,
                        "Planta sin coordenadas validas en Chile"));
            } else if (prev != null) {
                tramos.add(new TramoRow(
                        nextIdTramo++,
                        folioOri,
                        fechaOri,
                        prev.codigoTipo() + "-PLA",
                        prev.folio(),
                        firstNonBlank(fRec, codPla, nomPla),
                        prev.lon(),
                        prev.lat(),
                        plLon,
                        plLat,
                        especie,
                        kg));
            }
        }
    }

    private static CellStyle crearEstiloEncabezado(SXSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private static void escribirFilaEncabezado(Sheet sheet, String[] headers, CellStyle headerStyle) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private static void configurarAnchos(Sheet sheet, int columnas) {
        for (int i = 0; i < columnas; i++) {
            sheet.setColumnWidth(i, 18 * 256);
        }
    }

    private static void escribirHojaLeeme(
            Sheet sheet,
            CellStyle headerStyle,
            Date startDate,
            Date endDate,
            Long especieId,
            Long regionId,
            Long regionDestinoId,
            int totalDecl,
            int totalTramos,
            int totalSinCoord) {
        int r = 0;
        Row titleRow = sheet.createRow(r++);
        Cell c0 = titleRow.createCell(0);
        c0.setCellValue("TRAZALGA - EXPORTACION SIG DE FLUJOS ORIGEN-DESTINO");
        c0.setCellStyle(headerStyle);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        r = addInfoRow(sheet, r, "Sistema espacial", "WGS84 (EPSG:4326) - grados decimales con punto decimal");
        r = addInfoRow(sheet, r, "Generado en", ZonedDateTime.now(ApiApplication.ZONA_CHILE).format(TS_CHILE_FMT));
        r = addInfoRow(sheet, r, "Filtro startDate", startDate != null ? sdf.format(startDate) : "Sin filtro");
        r = addInfoRow(sheet, r, "Filtro endDate", endDate != null ? sdf.format(endDate) : "Sin filtro");
        r = addInfoRow(sheet, r, "Filtro especieId", especieId != null ? String.valueOf(especieId) : "Todas");
        r = addInfoRow(sheet, r, "Filtro regionId (origen)", regionId != null ? String.valueOf(regionId) : "Todas");
        r = addInfoRow(sheet, r, "Filtro regionDestinoId (planta)", regionDestinoId != null ? String.valueOf(regionDestinoId) : "Todas");
        r = addInfoRow(sheet, r, "Filas DECLARACIONES", String.valueOf(totalDecl));
        r = addInfoRow(sheet, r, "Filas TRAMOS", String.valueOf(totalTramos));
        r = addInfoRow(sheet, r, "Filas SIN_COORD", String.valueOf(totalSinCoord));
        r++;

        Row dictHead = sheet.createRow(r++);
        String[] dictCols = {"HOJA", "CAMPO", "TIPO", "DESCRIPCION"};
        for (int i = 0; i < dictCols.length; i++) {
            Cell c = dictHead.createCell(i);
            c.setCellValue(dictCols[i]);
            c.setCellStyle(headerStyle);
        }

        String[][] dictRows = {
                {"DECLARACIONES", "FOLIO_ORI", "Texto", "Folio o identificador de la declaracion de origen"},
                {"DECLARACIONES", "TIPO_ORI", "Texto", "Tipo de actor de origen (RECOLECTOR, ARMADOR, AREA)"},
                {"DECLARACIONES", "FECHA_ORI", "Texto", "Fecha de declaracion origen en formato AAAA-MM-DD"},
                {"DECLARACIONES", "ESPECIE", "Texto", "Nombre de la especie declarada"},
                {"DECLARACIONES", "DESEMB_KG", "Numero", "Peso desembarcado o declarado en kilogramos"},
                {"DECLARACIONES", "CAPT_KG", "Numero", "Peso capturado en kilogramos"},
                {"DECLARACIONES", "NOM_ORI", "Texto", "Caleta, varadero o AMERB de origen"},
                {"DECLARACIONES", "LAT_ORI", "Numero", "Latitud WGS84 del origen (grados decimales)"},
                {"DECLARACIONES", "LON_ORI", "Numero", "Longitud WGS84 del origen (grados decimales)"},
                {"DECLARACIONES", "FTE_ORI", "Texto", "Fuente de coordenada de origen (REFERENCIA, GPS, SIN_COORDENADAS)"},
                {"DECLARACIONES", "FOLIO_COM", "Texto", "Folio(s) de comercializador(es) en la cadena"},
                {"DECLARACIONES", "LAT_COM", "Numero", "Latitud WGS84 del comercializador principal"},
                {"DECLARACIONES", "LON_COM", "Numero", "Longitud WGS84 del comercializador principal"},
                {"DECLARACIONES", "FTE_COM", "Texto", "Fuente de coordenada de comercializador"},
                {"DECLARACIONES", "NOM_PLA", "Texto", "Nombre de la planta de destino"},
                {"DECLARACIONES", "COD_PLA", "Texto", "Codigo Sernapesca de la planta"},
                {"DECLARACIONES", "COM_PLA", "Texto", "Comuna de la planta segun maestro"},
                {"DECLARACIONES", "LAT_PLA", "Numero", "Latitud WGS84 de la planta"},
                {"DECLARACIONES", "LON_PLA", "Numero", "Longitud WGS84 de la planta"},
                {"DECLARACIONES", "FTE_PLA", "Texto", "Fuente de coordenada de la planta"},
                {"DECLARACIONES", "FOLIO_REC", "Texto", "Folio de recepcion en planta (A-PLA)"},
                {"DECLARACIONES", "FECHA_REC", "Texto", "Fecha de ingreso a planta en formato AAAA-MM-DD"},
                {"DECLARACIONES", "ROMANA_KG", "Numero", "Peso verificado en romana en kilogramos"},
                {"DECLARACIONES", "REF_ORI", "Texto", "Fuente especifica del punto de origen (VARADERO, CALETA, AMERB, GPS, SIN_COORD)"},
                {"TRAMOS", "ID_TRAMO", "Numero", "Identificador correlativo del segmento"},
                {"TRAMOS", "FOLIO_ORI", "Texto", "Folio de declaracion origen asociada"},
                {"TRAMOS", "FECHA_ORI", "Texto", "Fecha de declaracion origen AAAA-MM-DD"},
                {"TRAMOS", "TRAMO", "Texto", "Tipo de tramo completo: ORI-COM, COM-COM, COM-PLA o ORI-PLA"},
                {"TRAMOS", "FOLIO_INI", "Texto", "Folio del punto inicial del tramo"},
                {"TRAMOS", "FOLIO_FIN", "Texto", "Folio o codigo del punto final del tramo"},
                {"TRAMOS", "X_INI", "Numero", "Longitud inicial WGS84 (X para XY To Line)"},
                {"TRAMOS", "Y_INI", "Numero", "Latitud inicial WGS84 (Y para XY To Line)"},
                {"TRAMOS", "X_FIN", "Numero", "Longitud final WGS84 (X para XY To Line)"},
                {"TRAMOS", "Y_FIN", "Numero", "Latitud final WGS84 (Y para XY To Line)"},
                {"TRAMOS", "ESPECIE", "Texto", "Especie movilizada en el tramo"},
                {"TRAMOS", "KG", "Numero", "Kilogramos declarados asociados al tramo"},
                {"SIN_COORD", "ESLABON", "Texto", "Eslabon sin coordenadas validas: ORIGEN, COMERCIALIZADOR o PLANTA"},
                {"SIN_COORD", "FOLIO_ESL", "Texto", "Folio o codigo del eslabon sin coordenadas"},
                {"SIN_COORD", "NOM_ESL", "Texto", "Nombre del punto o planta sin coordenadas"},
                {"SIN_COORD", "MOTIVO", "Texto", "Motivo por el cual no entro en TRAMOS"}
        };
        for (String[] dr : dictRows) {
            Row row = sheet.createRow(r++);
            for (int i = 0; i < dr.length; i++) {
                row.createCell(i).setCellValue(dr[i]);
            }
        }
    }

    private static int addInfoRow(Sheet sheet, int rowNum, String label, String val) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(val != null ? val : "");
        return rowNum + 1;
    }

    /**
     * YR.3: Resuelve el valor compacto (<= 10 caracteres) para {@code REF_ORI}.
     */
    private static String resolverRefOri(Map<String, Object> d) {
        String explicita = strOrNull(d.get("origenReferenciaFuente"));
        if (explicita != null) {
            String up = explicita.trim().toUpperCase(Locale.ROOT);
            return up.length() > 10 ? up.substring(0, 10) : up;
        }
        String detalle = strOrNull(d.get("origenFuenteDetalle"));
        String fte = strOrNull(d.get("origenFuente"));
        if ("GPS".equalsIgnoreCase(fte)) {
            return "GPS";
        }
        if (detalle != null) {
            return ReportService.normalizarFuenteReferenciaOrigen(null, detalle);
        }
        return "SIN_COORD";
    }

    private static void setTextCell(Row row, int col, String value) {
        if (value != null && !value.isEmpty()) {
            row.createCell(col).setCellValue(value);
        }
    }

    private static void setNumericCell(Row row, int col, Double value) {
        if (value != null && Double.isFinite(value)) {
            row.createCell(col).setCellValue(value);
        }
    }

    private static Double coordValidaOrNull(Object rawLat, Object rawLon, boolean pedirLat) {
        Double lat = toDoubleOrNull(rawLat);
        Double lon = toDoubleOrNull(rawLon);
        if (!ReportService.esCoordenadaChileValida(lat, lon)) {
            return null;
        }
        return pedirLat ? lat : lon;
    }

    private static Double toDoubleOrNull(Object obj) {
        if (obj instanceof Number n) {
            double v = n.doubleValue();
            return Double.isFinite(v) ? v : null;
        }
        return null;
    }

    private static String strOrNull(Object obj) {
        if (obj == null) return null;
        String s = obj.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static String firstNonBlank(String... candidates) {
        if (candidates == null) return null;
        for (String c : candidates) {
            if (c != null && !c.isBlank()) {
                return c.trim();
            }
        }
        return null;
    }

    private static String formatearFechaIso(Object raw) {
        if (raw == null) return null;
        String s = raw.toString().trim();
        if (s.length() >= 10 && s.charAt(4) == '-' && s.charAt(7) == '-') {
            return s.substring(0, 10);
        }
        return s.isEmpty() ? null : s;
    }

    private static String formatearNumero(Double val, int decimales) {
        if (val == null || !Double.isFinite(val)) {
            return "";
        }
        return String.format(Locale.ROOT, "%." + decimales + "f", val);
    }

    private static String escaparCsv(String val) {
        String safe = val == null ? "" : val;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
