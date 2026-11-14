package com.trazalga.api.services.catalogo;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lector unificado de planillas CSV ({@code codigo;nombre}) y Excel OOXML ({@code .xlsx})
 * para la importación del catálogo oficial Sernapesca (QF.3).
 */
public final class LectorArchivoCatalogo {

    public record FilaCruda(int numeroFila, String codigo, String nombre) {
    }

    private LectorArchivoCatalogo() {
    }

    public static List<FilaCruda> leer(InputStream inputStream, String filename) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("El archivo está vacío.");
        }
        byte[] bytes = inputStream.readAllBytes();
        if (bytes.length == 0) {
            throw new IllegalArgumentException("El archivo no contiene datos.");
        }

        if (esOle2Antiguo(bytes)) {
            throw new IllegalArgumentException("Formato Excel antiguo (.xls/.xlt) no soportado; guarde el archivo como .xlsx o .csv.");
        }
        if (esZipXlsx(bytes, filename)) {
            return leerPrimeraHojaXlsx(bytes);
        }
        return leerCsv(bytes);
    }

    private static boolean esZipXlsx(byte[] bytes, String filename) {
        boolean tieneFirmaZip = bytes.length >= 4
                && bytes[0] == 0x50 && bytes[1] == 0x4B
                && bytes[2] == 0x03 && bytes[3] == 0x04;
        boolean extensionXlsx = filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".xlsx");
        return tieneFirmaZip || extensionXlsx;
    }

    private static boolean esOle2Antiguo(byte[] bytes) {
        return bytes.length >= 4
                && bytes[0] == (byte) 0xD0 && bytes[1] == (byte) 0xCF
                && bytes[2] == (byte) 0x11 && bytes[3] == (byte) 0xE0;
    }

    private static List<FilaCruda> leerPrimeraHojaXlsx(byte[] bytes) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("El libro Excel no contiene hojas.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.ROOT);

            Integer idxCodigo = null;
            Integer idxNombre = null;
            List<FilaCruda> filas = new ArrayList<>();

            for (Row row : sheet) {
                int maxCols = Math.max(row.getLastCellNum(), (short) 0);
                if (maxCols == 0) continue;

                List<String> cells = new ArrayList<>(maxCols);
                boolean todasVacias = true;
                for (int c = 0; c < maxCols; c++) {
                    Cell cell = row.getCell(c);
                    String val = cell != null ? formatter.formatCellValue(cell).trim() : "";
                    if (!val.isEmpty()) todasVacias = false;
                    cells.add(val);
                }
                if (todasVacias) continue;

                if (idxCodigo == null || idxNombre == null) {
                    int[] indices = detectarColumnas(cells);
                    idxCodigo = indices[0];
                    idxNombre = indices[1];
                    continue;
                }

                String codigo = idxCodigo < cells.size() ? cells.get(idxCodigo) : "";
                String nombre = idxNombre < cells.size() ? cells.get(idxNombre) : "";
                if (codigo.isEmpty() && nombre.isEmpty()) continue;
                filas.add(new FilaCruda(row.getRowNum() + 1, codigo, nombre));
            }
            if (idxCodigo == null || idxNombre == null) {
                throw new IllegalArgumentException("No se encontró fila de encabezados con columnas 'codigo' y 'nombre'.");
            }
            return filas;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("No se pudo leer el archivo .xlsx: " + ex.getMessage(), ex);
        }
    }

    private static List<FilaCruda> leerCsv(byte[] bytes) throws IOException {
        String contenido = decodificarTexto(bytes);
        if (contenido.startsWith("\uFEFF")) {
            contenido = contenido.substring(1);
        }

        List<FilaCruda> filas = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(contenido))) {
            String line;
            int lineNum = 0;
            String separador = ";";
            Integer idxCodigo = null;
            Integer idxNombre = null;

            while ((line = reader.readLine()) != null) {
                lineNum++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

                if (idxCodigo == null || idxNombre == null) {
                    if (!trimmed.contains(";") && trimmed.contains(",")) {
                        separador = ",";
                    }
                    List<String> headers = parsearLineaCsv(trimmed, separador.charAt(0));
                    int[] indices = detectarColumnas(headers);
                    idxCodigo = indices[0];
                    idxNombre = indices[1];
                    continue;
                }

                List<String> cols = parsearLineaCsv(trimmed, separador.charAt(0));
                String codigo = idxCodigo < cols.size() ? cols.get(idxCodigo) : "";
                String nombre = idxNombre < cols.size() ? cols.get(idxNombre) : "";
                if (codigo.isEmpty() && nombre.isEmpty()) continue;
                filas.add(new FilaCruda(lineNum, codigo, nombre));
            }
            if (idxCodigo == null || idxNombre == null) {
                throw new IllegalArgumentException("El archivo CSV no contiene encabezado con columnas 'codigo' y 'nombre'.");
            }
        }
        return filas;
    }

    private static int[] detectarColumnas(List<String> headers) {
        Integer idxCodigo = null;
        Integer idxNombre = null;
        for (int i = 0; i < headers.size(); i++) {
            String h = normalizarHeader(headers.get(i));
            if (idxCodigo == null && (h.equals("codigo") || h.contains("codigo"))) {
                idxCodigo = i;
            } else if (idxNombre == null && (h.equals("nombre") || h.contains("nombre"))) {
                idxNombre = i;
            }
        }
        if (idxCodigo == null || idxNombre == null) {
            throw new IllegalArgumentException(
                    "Encabezados inválidos: se esperan columnas 'Código Recurso/Producto' (o 'codigo') y 'Nombre Recurso/Producto' (o 'nombre').");
        }
        return new int[]{idxCodigo, idxNombre};
    }

    private static String normalizarHeader(String raw) {
        if (raw == null) return "";
        String sinAcentos = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return sinAcentos.toLowerCase(Locale.ROOT);
    }

    private static List<String> parsearLineaCsv(String line, char delim) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == delim && !inQuotes) {
                result.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        result.add(current.toString().trim());
        return result;
    }

    private static String decodificarTexto(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException ex) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }
}
