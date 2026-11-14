package com.trazalga.api.services.catalogo;

import com.trazalga.api.services.catalogo.NombreFormatoOficial.Resultado;
import com.trazalga.api.services.catalogo.NombreFormatoOficial.TipoFila;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("QF.2 / QF.8 - Parser puro del catálogo oficial Sernapesca")
class NombreFormatoOficialTest {

    private static final List<String[]> FILAS_REALES = List.of(
            new String[]{"141", "HUIRO NEGRO O CHASCON"},
            new String[]{"142", "HUIRO PALO"},
            new String[]{"145", "HUIRO"},
            new String[]{"39917", "HUIRO PALO - SECO (SOLO ALGAS) - NINGUNO - MOLIDA / PICADA - NINGUNO - A GRANEL - 1.0 - KILOGRAMO"},
            new String[]{"40959", "CHASCON O HUIRO NEGRO - SECO (SOLO ALGAS) - NINGUNO - MOLIDA/PICADA - NINGUNO - A GRANEL - 1 - KILOGRAMO"},
            new String[]{"45401", "CHASCON O HUIRO NEGRO - SECO (SOLO ALGAS) - NINGUNO - ENTERO - FARDO - A GRANEL - 1 - KILOGRAMO"},
            new String[]{"45402", "CHASCON O HUIRO NEGRO - SECO (SOLO ALGAS) - NINGUNO - PICADA - 3/4 - A GRANEL - 1 - KILOGRAMO"},
            new String[]{"45403", "CHASCON O HUIRO NEGRO - SECO (SOLO ALGAS) - NINGUNO - PICADA - FINO - A GRANEL - 1 - KILOGRAMO"},
            new String[]{"45455", "HUIRO PALO - SECO (SOLO ALGAS) - NINGUNO - ENTERO - FARDO - A GRANEL - 1 - KILOGRAMO"},
            new String[]{"45456", "HUIRO PALO - SECO (SOLO ALGAS) - NINGUNO - PICADA - FINO - A GRANEL - 1 - KILOGRAMO"},
            new String[]{"45457", "HUIRO PALO - SECO (SOLO ALGAS) - NINGUNO - PICADA - 3/4 - A GRANEL - 1 - KILOGRAMO"},
            new String[]{"45461", "HUIRO - SECO (SOLO ALGAS) - NINGUNO - ENTERO - FARDO - A GRANEL - 1 - KILOGRAMO"},
            new String[]{"45462", "HUIRO - SECO (SOLO ALGAS) - NINGUNO - PICADA - 3/4 - A GRANEL - 1 - KILOGRAMO"},
            new String[]{"45463", "HUIRO - SECO (SOLO ALGAS) - NINGUNO - PICADA - FINO - A GRANEL - 1 - KILOGRAMO"},
            new String[]{"46137", "HUIRO - SECO (SOLO ALGAS) - NINGUNO - POLVO - NINGUNO - A GRANEL - 1 - KILOGRAMO"},
            new String[]{"46184", "HUIRO NEGRO O CHASCON - SECO (SOLO ALGAS) - NINGUNO - POLVO - NINGUNO - A GRANEL - 1 - KILOGRAMO"},
            new String[]{"46185", "HUIRO PALO - SECO (SOLO ALGAS) - NINGUNO - POLVO - NINGUNO - A GRANEL - 1 - KILOGRAMO"},
            new String[]{"46756", "HUIRO NEGRO O CHASCON - SECO (SOLO ALGAS) - NINGUNO - DESECHO - NINGUNO - A GRANEL - 1 - KILOGRAMO"},
            new String[]{"47027", "HUIRO PALO - SECO (SOLO ALGAS) - NINGUNO - PICADA - NINGUNO - A GRANEL - 1 - KILOGRAMO"},
            new String[]{"47257", "HUIRO PALO - SECO (SOLO ALGAS) - NINGUNO - PICADA - FINO (HOJAS) - A GRANEL - 1 - KILOGRAMO"},
            new String[]{"47258", "HUIRO PALO - SECO (SOLO ALGAS) - NINGUNO - PICADA - 3/4 (HOJAS) - A GRANEL - 1 - KILOGRAMO"},
            new String[]{"47954", "HUIRO PALO - SECO (SOLO ALGAS) - NINGUNO - PELLET- NINGUNO - A GRANEL - 1 - KILOGRAMO"}
    );

    @Test
    @DisplayName("QF.8: 22 nombres reales -> 3 recursos, 19 formatos, 0 por revisar y casos críticos")
    void parseaLos22NombresReales() {
        Map<String, Resultado> porCodigo = new LinkedHashMap<>();
        int recursos = 0;
        int formatos = 0;
        int porRevisar = 0;

        for (String[] fila : FILAS_REALES) {
            Resultado r = NombreFormatoOficial.analizar(fila[0], fila[1]);
            porCodigo.put(fila[0], r);
            if (r.tipo() == TipoFila.RECURSO) recursos++;
            else if (r.tipo() == TipoFila.FORMATO) formatos++;
            else porRevisar++;
        }

        assertEquals(3, recursos, "Deben reconocerse 3 recursos (141, 142, 145)");
        assertEquals(19, formatos, "Deben reconocerse 19 formatos");
        assertEquals(0, porRevisar, "No debe haber filas por revisar en el catálogo real");

        // 40959 y 46184 dan la misma especie (sinónimos HUIRO NEGRO O CHASCON vs CHASCON O HUIRO NEGRO)
        Resultado r40959 = porCodigo.get("40959");
        Resultado r46184 = porCodigo.get("46184");
        assertEquals("HUIRO NEGRO", r40959.especieCanon());
        assertEquals(r40959.especieCanon(), r46184.especieCanon());
        assertEquals(104, r40959.nombreOficial().length());

        // 39917 y 40959 dan la misma presentación (MOLIDA/PICADA), preservando redacción original
        Resultado r39917 = porCodigo.get("39917");
        assertEquals("MOLIDA/PICADA", r39917.presentacion());
        assertEquals(r39917.presentacion(), r40959.presentacion());
        assertEquals("", r39917.calibre());
        assertTrue(r39917.nombreOficial().contains("MOLIDA / PICADA"));
        assertTrue(r39917.nombreOficial().contains("1.0"));

        // 47954 acepta PELLET- NINGUNO sin espacio antes del guion y calibre vacío
        Resultado r47954 = porCodigo.get("47954");
        assertEquals("PELLET", r47954.presentacion());
        assertEquals("", r47954.calibre());
        assertTrue(r47954.nombreOficial().contains("PELLET- NINGUNO"));

        // Otros calibres representativos
        assertEquals("FARDO", porCodigo.get("45401").calibre());
        assertEquals("FINO (HOJAS)", porCodigo.get("47257").calibre());
        assertEquals("3/4 (HOJAS)", porCodigo.get("47258").calibre());
    }

    @Test
    @DisplayName("QF.8: un nombre con 7 partes o datos desconocidos/vacíos queda por revisar")
    void filasInvalidasQuedanPorRevisar() {
        Resultado sietePartes = NombreFormatoOficial.analizar(
                "99001",
                "HUIRO PALO - SECO (SOLO ALGAS) - NINGUNO - PICADA - 3/4 - A GRANEL - 1");
        assertEquals(TipoFila.POR_REVISAR, sietePartes.tipo());
        assertTrue(sietePartes.motivo().contains("8 partes"));

        Resultado especieDesconocida = NombreFormatoOficial.analizar(
                "99002",
                "COCHAYUYO - SECO (SOLO ALGAS) - NINGUNO - PICADA - 3/4 - A GRANEL - 1 - KILOGRAMO");
        assertEquals(TipoFila.POR_REVISAR, especieDesconocida.tipo());

        Resultado estadoDesconocido = NombreFormatoOficial.analizar(
                "99003",
                "HUIRO PALO - HUMEDO (SOLO ALGAS) - NINGUNO - PICADA - 3/4 - A GRANEL - 1 - KILOGRAMO");
        assertEquals(TipoFila.POR_REVISAR, estadoDesconocido.tipo());

        Resultado codigoVacio = NombreFormatoOficial.analizar("  ", "HUIRO PALO");
        assertEquals(TipoFila.POR_REVISAR, codigoVacio.tipo());

        Resultado espaciosExtra = NombreFormatoOficial.analizar(
                " 45455 ",
                "  HUIRO   PALO - SECO  (SOLO ALGAS) - NINGUNO - ENTERO - FARDO - A GRANEL - 1 - KILOGRAMO  ");
        assertEquals(TipoFila.FORMATO, espaciosExtra.tipo());
        assertEquals("45455", espaciosExtra.codigo());
        assertFalse(espaciosExtra.nombreOficial().contains("  "));
    }
}
