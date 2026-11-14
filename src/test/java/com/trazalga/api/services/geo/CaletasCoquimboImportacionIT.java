package com.trazalga.api.services.geo;

import com.trazalga.api.IntegracionBase;
import com.trazalga.api.dto.geo.EstadoFilaCoordenada;
import com.trazalga.api.dto.geo.ResumenImportacionDto;
import com.trazalga.api.models.CaletaModel;
import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.models.RegionModel;
import com.trazalga.api.repositories.ICaletaRepository;
import com.trazalga.api.repositories.IComunaRepository;
import com.trazalga.api.repositories.ICoordenadaCambioLogRepository;
import com.trazalga.api.repositories.IRegionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CaletasCoquimboImportacionIT extends IntegracionBase {

    private static final String CSV_RESOURCE = "caletas_iv_region_importar.csv";
    private static final String FUENTE_ESPERADA = "Sernapesca - Trazalga_Caletas_IVRegion.xlt (13-11-2026)";

    @Autowired
    private ImportarCoordenadasService importarCoordenadasService;
    @Autowired
    private ICaletaRepository caletaRepository;
    @Autowired
    private IComunaRepository comunaRepository;
    @Autowired
    private IRegionRepository regionRepository;
    @Autowired
    private ICoordenadaCambioLogRepository cambioLogRepository;

    private final List<Long> caletasCreadasIds = new ArrayList<>();
    private final List<Long> comunasCreadasIds = new ArrayList<>();
    private RegionModel regionCoquimbo;
    private List<CsvRow> filasCsv;

    private record CsvRow(String nombre, String comuna, double latitud, double longitud) {
    }

    @BeforeEach
    void setup() throws Exception {
        cambioLogRepository.deleteAll();
        caletaRepository.deleteAll();
        comunaRepository.deleteAll();
        regionRepository.deleteAll();
        caletasCreadasIds.clear();
        comunasCreadasIds.clear();

        regionCoquimbo = regionRepository.saveAndFlush(
                RegionModel.builder().nombre("Coquimbo").codigo("IV").build());

        filasCsv = leerCsvDesdeClasspath();
        assertEquals(33, filasCsv.size(), "El CSV oficial de Coquimbo debe contener 33 caletas");

        Map<String, ComunaModel> comunas = new LinkedHashMap<>();
        for (CsvRow row : filasCsv) {
            ComunaModel comuna = comunas.computeIfAbsent(row.comuna(), nombre -> {
                ComunaModel c = comunaRepository.saveAndFlush(
                        ComunaModel.builder().nombre(nombre).region(regionCoquimbo).build());
                comunasCreadasIds.add(c.getId());
                return c;
            });
            CaletaModel caleta = caletaRepository.saveAndFlush(CaletaModel.builder()
                    .nombre(row.nombre())
                    .region(regionCoquimbo)
                    .comuna(comuna)
                    .build());
            caletasCreadasIds.add(caleta.getId());
        }
    }

    @AfterEach
    void cleanup() {
        cambioLogRepository.deleteAll();
        for (Long id : caletasCreadasIds) {
            caletaRepository.deleteById(id);
        }
        for (Long id : comunasCreadasIds) {
            comunaRepository.deleteById(id);
        }
        if (regionCoquimbo != null && regionCoquimbo.getId() != null) {
            regionRepository.deleteById(regionCoquimbo.getId());
        }
    }

    @Test
    @DisplayName("QG.5: vista previa no persiste, aplicar puebla las 33 caletas con fuente, segunda pasada idempotente y UTF-8 PEÑUELAS")
    void flujoCompletoCoquimboCsvReal() throws Exception {
        // 1. Vista previa (aplicar = false): todas calzan y nada se escribe en BD
        ResumenImportacionDto preview;
        try (InputStream is = new ClassPathResource(CSV_RESOURCE).getInputStream()) {
            preview = importarCoordenadasService.procesarPlanilla(is, false, false, "qg5-it");
        }

        assertFalse(preview.isAplicado());
        assertEquals(33, preview.getTotalFilas());
        assertEquals(33, preview.getCalzadas());
        assertEquals(0, preview.getSinCalce());
        assertEquals(0, preview.getAmbiguas());
        assertEquals(0, preview.getFueraDeRango());
        assertEquals(0, preview.getEnConflicto());
        assertEquals(0, preview.getAplicadas());
        assertEquals(0, cambioLogRepository.count());

        for (CaletaModel c : caletaRepository.findAll()) {
            assertNull(c.getLatitud(), "Vista previa no debe escribir latitud: " + c.getNombre());
            assertNull(c.getLongitud(), "Vista previa no debe escribir longitud: " + c.getNombre());
            assertNull(c.getFuenteCoordenada(), "Vista previa no debe escribir fuente: " + c.getNombre());
        }

        // 2. Aplicar (aplicar = true): puebla las 33 caletas y sus logs
        ResumenImportacionDto applied;
        try (InputStream is = new ClassPathResource(CSV_RESOURCE).getInputStream()) {
            applied = importarCoordenadasService.procesarPlanilla(is, true, false, "qg5-it");
        }

        assertTrue(applied.isAplicado());
        assertEquals(33, applied.getCalzadas());
        assertEquals(33, applied.getAplicadas());
        assertEquals(33, cambioLogRepository.count());

        Map<String, CaletaModel> porNombre = caletaRepository.findAll().stream()
                .collect(Collectors.toMap(CaletaModel::getNombre, c -> c));

        for (CsvRow expected : filasCsv) {
            CaletaModel persisted = porNombre.get(expected.nombre());
            assertNotNull(persisted, "Debe existir caleta " + expected.nombre());
            assertEquals(expected.latitud(), persisted.getLatitud(), 1e-6);
            assertEquals(expected.longitud(), persisted.getLongitud(), 1e-6);
            assertEquals(FUENTE_ESPERADA, persisted.getFuenteCoordenada());
        }

        // Verificación explícita UTF-8 para eñe y tilde
        CaletaModel penuelas = porNombre.get("Caleta Peñuelas");
        assertNotNull(penuelas, "Caleta Peñuelas debe preservar UTF-8 'ñ'");
        assertEquals(-29.952613, penuelas.getLatitud(), 1e-6);
        assertEquals(-71.300502, penuelas.getLongitud(), 1e-6);

        // 3. Segunda ejecución idempotente (mismo archivo, mismas coordenadas)
        ResumenImportacionDto secondRun;
        try (InputStream is = new ClassPathResource(CSV_RESOURCE).getInputStream()) {
            secondRun = importarCoordenadasService.procesarPlanilla(is, true, false, "qg5-it");
        }

        assertTrue(secondRun.isAplicado());
        assertEquals(33, secondRun.getCalzadas());
        assertEquals(0, secondRun.getEnConflicto());

        Map<String, CaletaModel> trasSegunda = caletaRepository.findAll().stream()
                .collect(Collectors.toMap(CaletaModel::getNombre, c -> c));
        for (CsvRow expected : filasCsv) {
            CaletaModel c = trasSegunda.get(expected.nombre());
            assertEquals(expected.latitud(), c.getLatitud(), 1e-6);
            assertEquals(expected.longitud(), c.getLongitud(), 1e-6);
            assertEquals(FUENTE_ESPERADA, c.getFuenteCoordenada());
        }
    }

    @Test
    @DisplayName("QG.5: calce UTF-8 exacto de 'PEÑUELAS' contra maestro 'PEÑUELAS'")
    void calceUtf8NombrePenuelasExacto() {
        ComunaModel coquimbo = comunaRepository.findAll().stream()
                .filter(c -> "COQUIMBO".equals(c.getNombre()))
                .findFirst()
                .orElseThrow();

        CaletaModel sinPrefijo = caletaRepository.saveAndFlush(CaletaModel.builder()
                .nombre("PEÑUELAS")
                .region(regionCoquimbo)
                .comuna(coquimbo)
                .build());
        caletasCreadasIds.add(sinPrefijo.getId());

        String csvMini = "tipo;codigo;nombre;comuna;region;latitud;longitud;datum;fuente\n"
                + "CALETA;;PEÑUELAS;COQUIMBO;COQUIMBO;-29.952613;-71.300502;;" + FUENTE_ESPERADA + "\n";

        ResumenImportacionDto res = importarCoordenadasService.procesarPlanilla(
                new ByteArrayInputStream(csvMini.getBytes(StandardCharsets.UTF_8)), true, false, "qg5-it");

        assertEquals(1, res.getCalzadas());
        assertEquals(1, res.getAplicadas());
        assertEquals(EstadoFilaCoordenada.CALZADA, res.getFilas().get(0).getEstado());
        CaletaModel updated = caletaRepository.findById(sinPrefijo.getId()).orElseThrow();
        assertEquals(-29.952613, updated.getLatitud(), 1e-6);
        assertEquals(-71.300502, updated.getLongitud(), 1e-6);
    }

    private List<CsvRow> leerCsvDesdeClasspath() throws Exception {
        List<CsvRow> rows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new ClassPathResource(CSV_RESOURCE).getInputStream(), StandardCharsets.UTF_8))) {
            String header = br.readLine();
            assertNotNull(header);
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty())
                    continue;
                String[] cols = line.split(";", -1);
                rows.add(new CsvRow(
                        cols[2].trim(),
                        cols[3].trim(),
                        Double.parseDouble(cols[5].trim()),
                        Double.parseDouble(cols[6].trim())));
            }
        }
        return rows;
    }
}
