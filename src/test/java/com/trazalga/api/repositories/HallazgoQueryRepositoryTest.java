package com.trazalga.api.repositories;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.Date;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import com.trazalga.api.IntegracionBase;
import com.trazalga.api.dto.FiltroHallazgos;
import com.trazalga.api.dto.HallazgoDetalleDTO;
import com.trazalga.api.models.*;

@Transactional
class HallazgoQueryRepositoryTest extends IntegracionBase {

    @Autowired
    private HallazgoQueryRepository queryRepository;

    @Autowired
    private IDeclaracionMarcaRepository marcaRepository;

    @Autowired
    private IDeclaracionRecolectorRepository recolectorRepository;

    @Autowired
    private IUsuarioRepository usuarioRepository;

    @Autowired
    private IEspecieRepository especieRepository;

    @Autowired
    private IHumedadEstadoRepository humedadRepository;

    @Autowired
    private ICaletaRepository caletaRepository;

    @Autowired
    private IComunaRepository comunaRepository;

    @Autowired
    private IExtraccionTipoRepository extraccionTipoRepository;

    @Test
    @DisplayName("TA.3 - Marca histórica sin criterio estructurado respalda criterioTexto en detalle")
    @WithMockUser(roles = "ADMIN")
    void marcaHistoricaUsaDetalleComoCriterioTexto() {
        DeclaracionMarcaModel historica = DeclaracionMarcaModel.builder()
                .declaracionTipo("RECOLECTOR")
                .declaracionId(9999L)
                .marca("DESEMBARQUE_ATIPICO")
                .detalle("Detalle histórico ingresado manualmente antes del estándar")
                .resuelta(false)
                .estadoGestion("PENDIENTE")
                .origen("VALIDACION")
                .claveIdempotencia("RECOLECTOR:9999:DESEMBARQUE_ATIPICO:HISTORICA")
                .build();
        marcaRepository.save(historica);

        FiltroHallazgos filtro = FiltroHallazgos.builder()
                .marca("DESEMBARQUE_ATIPICO")
                .build();

        Page<HallazgoDetalleDTO> pagina = queryRepository.buscar(filtro, PageRequest.of(0, 10));

        assertFalse(pagina.isEmpty());
        HallazgoDetalleDTO match = pagina.getContent().stream()
                .filter(h -> "RECOLECTOR:9999:DESEMBARQUE_ATIPICO:HISTORICA".equals(h.getClaveIdempotencia()))
                .findFirst().orElse(null);

        assertNotNull(match);
        assertEquals("Detalle histórico ingresado manualmente antes del estándar", match.getCriterioTexto());
        assertNull(match.getCriterioParametro());
    }

    @Test
    @DisplayName("TA.3 - Marca con criterio estructurado formatea criterioTexto en es-CL")
    @WithMockUser(roles = "ADMIN")
    void marcaConCriterioEstructuradoFormateaCriterioTexto() {
        DeclaracionMarcaModel estructurada = DeclaracionMarcaModel.builder()
                .declaracionTipo("RECOLECTOR")
                .declaracionId(8888L)
                .marca("DESEMBARQUE_ATIPICO")
                .criterioParametro("desembarque_umbral_atipico_kg")
                .criterioUmbral("5000")
                .criterioValor("6200")
                .criterioUnidad("kg")
                .detalle("Fallback no utilizado si criterio está presente")
                .resuelta(false)
                .estadoGestion("PENDIENTE")
                .origen("VALIDACION")
                .claveIdempotencia("RECOLECTOR:8888:DESEMBARQUE_ATIPICO:ESTRUCTURADA")
                .build();
        marcaRepository.save(estructurada);

        FiltroHallazgos filtro = FiltroHallazgos.builder()
                .marca("DESEMBARQUE_ATIPICO")
                .build();

        Page<HallazgoDetalleDTO> pagina = queryRepository.buscar(filtro, PageRequest.of(0, 10));

        HallazgoDetalleDTO match = pagina.getContent().stream()
                .filter(h -> "RECOLECTOR:8888:DESEMBARQUE_ATIPICO:ESTRUCTURADA".equals(h.getClaveIdempotencia()))
                .findFirst().orElse(null);

        assertNotNull(match);
        assertEquals("6.200 kg supera el umbral de 5.000 kg", match.getCriterioTexto());
        assertEquals("desembarque_umbral_atipico_kg", match.getCriterioParametro());
        assertEquals("5000", match.getCriterioUmbral());
        assertEquals("6200", match.getCriterioValor());
        assertEquals("kg", match.getCriterioUnidad());
    }

    @Test
    @DisplayName("TA.3 - Hidratación en lote enriquece 50 marcas eficientemente")
    @WithMockUser(roles = "ADMIN")
    void hidratacionEnLoteEnriqueceMarcas() {
        // Asegurar que existan datos base
        UsuarioModel user = usuarioRepository.findAll().stream().findFirst().orElse(null);
        EspecieModel esp = especieRepository.findAll().stream().findFirst().orElse(null);
        HumedadEstadoModel hum = humedadRepository.findAll().stream().findFirst().orElse(null);
        CaletaModel cal = caletaRepository.findAll().stream().findFirst().orElse(null);
        ComunaModel com = comunaRepository.findAll().stream().findFirst().orElse(null);
        ExtraccionTipoModel ext = extraccionTipoRepository.findAll().stream().findFirst().orElse(null);

        if (user != null && esp != null && hum != null && cal != null && com != null && ext != null) {
            DeclaracionRecolectorModel decl = DeclaracionRecolectorModel.builder()
                    .usuario(user)
                    .especie(esp)
                    .humedadEstado(hum)
                    .caleta(cal)
                    .comuna(com)
                    .extraccionTipo(ext)
                    .fechaDeclaracion(new Date())
                    .fechaExtraccion(new Date())
                    .hora("12:00:00")
                    .nombre("Juan Perez")
                    .codigoSernapesca("RPA-12345")
                    .folioDesembarqueRo("RO-99999")
                    .desembarque(new BigDecimal("1500.00"))
                    .captura(new BigDecimal("1500.00"))
                    .build();
            final DeclaracionRecolectorModel savedDecl = recolectorRepository.save(decl);

            DeclaracionMarcaModel marca = DeclaracionMarcaModel.builder()
                    .declaracionTipo("RECOLECTOR")
                    .declaracionId(savedDecl.getId())
                    .marca("LED_EXCEDIDO")
                    .criterioParametro("limite_extraccion_diario_kg")
                    .criterioUmbral("1000")
                    .criterioValor("1500")
                    .criterioUnidad("kg")
                    .resuelta(false)
                    .estadoGestion("PENDIENTE")
                    .origen("VALIDACION")
                    .claveIdempotencia("RECOLECTOR:" + savedDecl.getId() + ":LED_EXCEDIDO:REGLA_1")
                    .build();
            marcaRepository.save(marca);

            FiltroHallazgos filtro = FiltroHallazgos.builder()
                    .marca("LED_EXCEDIDO")
                    .build();

            Page<HallazgoDetalleDTO> pagina = queryRepository.buscar(filtro, PageRequest.of(0, 50));
            assertFalse(pagina.isEmpty());

            HallazgoDetalleDTO dto = pagina.getContent().stream()
                    .filter(h -> ("RECOLECTOR:" + savedDecl.getId() + ":LED_EXCEDIDO:REGLA_1").equals(h.getClaveIdempotencia()))
                    .findFirst().orElse(null);

            assertNotNull(dto);
            assertEquals("RO-99999", dto.getFolio());
            assertEquals("RPA-12345", dto.getRpa());
            assertEquals(1500.0, dto.getKilos());
            assertEquals(esp.getNombre(), dto.getEspecie());
        }
    }
}
