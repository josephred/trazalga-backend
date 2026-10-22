package com.trazalga.api.services;

import com.trazalga.api.services.geo.CoordenadaNormalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TG.1 - Pruebas Unitarias de Normalización de Coordenadas")
class CoordenadaNormalizerTest {

    @Test
    @DisplayName("Parseo de grados decimales con punto y coma decimal")
    void testGradosDecimales() {
        Double lat1 = CoordenadaNormalizer.parsearCoordenada("-29.9027", true);
        assertNotNull(lat1);
        assertEquals(-29.9027, lat1, 0.0001);

        Double lat2 = CoordenadaNormalizer.parsearCoordenada("-29,9027", true);
        assertNotNull(lat2);
        assertEquals(-29.9027, lat2, 0.0001);

        // Si se ingresa positivo sin letra en Chile, debe transformarse a negativo
        Double lon1 = CoordenadaNormalizer.parsearCoordenada("71.2500", false);
        assertNotNull(lon1);
        assertEquals(-71.2500, lon1, 0.0001);
    }

    @Test
    @DisplayName("Parseo de grados, minutos y segundos (DMS)")
    void testGradosMinutosSegundos() {
        // 29°54'09.7"S -> -(29 + 54/60 + 9.7/3600) = -29.902694
        Double lat = CoordenadaNormalizer.parsearCoordenada("29°54'09.7\"S", true);
        assertNotNull(lat);
        assertEquals(-29.902694, lat, 0.0001);

        // Con coma decimal en los segundos: 71°15'10,2"W -> -(71 + 15/60 + 10.2/3600) = -71.252833
        Double lon = CoordenadaNormalizer.parsearCoordenada("71°15'10,2\"W", false);
        assertNotNull(lon);
        assertEquals(-71.252833, lon, 0.0001);

        // Con letra O de Oeste
        Double lonOeste = CoordenadaNormalizer.parsearCoordenada("71°15'10.2\"O", false);
        assertNotNull(lonOeste);
        assertEquals(-71.252833, lonOeste, 0.0001);
    }

    @Test
    @DisplayName("Validación de rango geográfico de Chile continental")
    void testRangoChile() {
        // En rango Coquimbo: (-29.90, -71.25)
        assertTrue(CoordenadaNormalizer.estaEnRangoChile(-29.90, -71.25));

        // Fuera de rango por longitud tipeada como -7.12
        assertFalse(CoordenadaNormalizer.estaEnRangoChile(-29.90, -7.12));

        // Fuera de rango por latitud positiva (hemisferio norte)
        assertFalse(CoordenadaNormalizer.estaEnRangoChile(10.5, -71.25));
    }

    @Test
    @DisplayName("Validación de Datum WGS84")
    void testDatumWgs84() {
        assertTrue(CoordenadaNormalizer.esDatumWgs84("WGS84"));
        assertTrue(CoordenadaNormalizer.esDatumWgs84("wgs-84"));
        assertTrue(CoordenadaNormalizer.esDatumWgs84("WGS 84"));
        assertTrue(CoordenadaNormalizer.esDatumWgs84("EPSG:4326"));
        assertTrue(CoordenadaNormalizer.esDatumWgs84(null)); // Asume default

        assertFalse(CoordenadaNormalizer.esDatumWgs84("SAD69"));
        assertFalse(CoordenadaNormalizer.esDatumWgs84("PSAD56"));
    }

    @Test
    @DisplayName("Normalización de texto eliminando tildes y mayúsculas")
    void testNormalizarTexto() {
        assertEquals("CALETA PENUELAS", CoordenadaNormalizer.normalizarTexto("Caleta Peñuelas"));
        assertEquals("CONCEPCION", CoordenadaNormalizer.normalizarTexto("  Concepción  "));
        assertEquals("LOS VILOS", CoordenadaNormalizer.normalizarTexto("Los    Vilos"));
    }
}
