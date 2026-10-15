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
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;

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

    @Autowired
    private IDeclaracionArmadorRepository armadorRepository;

    @Autowired
    private IDeclaracionAreaRepository areaRepository;

    @Autowired
    private IDeclaracionComercializadorRepository comercializadorRepository;

    @Autowired
    private IEmbarcacionRepository embarcacionRepository;

    @Autowired
    private IBuzoRepository buzoRepository;

    @Autowired
    private IAmerbRepository amerbRepository;

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("TA.3 - Consulta paginada de hallazgos: 120 marcas, página 2 de 50 en 7 consultas o menos con enriquecimiento")
    @WithMockUser(roles = "ADMIN")
    void consultaPaginada120MarcasEn7ConsultasOMenos() {
        marcaRepository.deleteAll();

        // 1. Datos maestros base creados directamente por la prueba
        UsuarioModel recolectorUser = usuarioRepository.save(UsuarioModel.builder()
                .rut("11111111-1")
                .nombres("Juan")
                .apellidop("Perez")
                .correo("recolector@test.cl")
                .build());

        UsuarioModel armadorUser = usuarioRepository.save(UsuarioModel.builder()
                .rut("22222222-2")
                .nombres("Carlos")
                .apellidop("Armador")
                .correo("armador@test.cl")
                .build());

        UsuarioModel areaUser = usuarioRepository.save(UsuarioModel.builder()
                .rut("33333333-3")
                .nombres("Mario")
                .apellidop("Area")
                .correo("area@test.cl")
                .build());

        UsuarioModel comercializadorUser = usuarioRepository.save(UsuarioModel.builder()
                .rut("44444444-4")
                .nombres("Ana")
                .apellidop("Comercio")
                .correo("comercio@test.cl")
                .build());

        UsuarioModel destinatarioUser = usuarioRepository.save(UsuarioModel.builder()
                .rut("99999999-9")
                .nombres("Destinatario")
                .apellidop("General")
                .correo("dest@test.cl")
                .build());

        EspecieModel especie = especieRepository.save(EspecieModel.builder()
                .nombre("Huiro Negro")
                .activo(true)
                .build());

        HumedadEstadoModel humedad = humedadRepository.save(HumedadEstadoModel.builder()
                .nombre("Húmedo")
                .rangoInicio(0)
                .rangoFin(100)
                .build());

        ComunaModel comuna = comunaRepository.save(ComunaModel.builder()
                .nombre("Coquimbo")
                .build());

        CaletaModel caleta = caletaRepository.save(CaletaModel.builder()
                .nombre("Caleta Coquimbo")
                .comuna(comuna)
                .build());

        ExtraccionTipoModel extraccionTipo = extraccionTipoRepository.save(ExtraccionTipoModel.builder()
                .nombre("Barrido")
                .build());

        EmbarcacionModel embarcacion = embarcacionRepository.save(EmbarcacionModel.builder()
                .nombre("Doña Elena")
                .codigo("EMB-9988")
                .codigoRegion(4)
                .build());

        BuzoModel buzo = buzoRepository.save(BuzoModel.builder()
                .nombre("Pedro Buzo")
                .codigo("BUZ-1122")
                .codigoRegion(4)
                .build());

        AmerbModel amerb = amerbRepository.save(AmerbModel.builder()
                .nombre("AMERB Peñuelas")
                .codigoSernapesca("AMERB-01")
                .comuna(comuna)
                .build());

        // 2. Crear 30 declaraciones de cada uno de los 4 tipos (total 120 declaraciones)
        java.util.List<DeclaracionRecolectorModel> recolectores = new java.util.ArrayList<>();
        java.util.List<DeclaracionArmadorModel> armadores = new java.util.ArrayList<>();
        java.util.List<DeclaracionAreaModel> areas = new java.util.ArrayList<>();
        java.util.List<DeclaracionComercializadorModel> comercializadores = new java.util.ArrayList<>();

        for (int i = 0; i < 30; i++) {
            recolectores.add(recolectorRepository.save(DeclaracionRecolectorModel.builder()
                    .usuario(recolectorUser)
                    .especie(especie)
                    .humedadEstado(humedad)
                    .caleta(caleta)
                    .comuna(comuna)
                    .extraccionTipo(extraccionTipo)
                    .fechaDeclaracion(new Date())
                    .fechaExtraccion(new Date())
                    .hora("10:00:00")
                    .nombre("Recolector " + i)
                    .codigoSernapesca("RPA-REC-" + i)
                    .folioDesembarqueRo("RO-" + (1000 + i))
                    .desembarque(new BigDecimal("1200.00"))
                    .captura(new BigDecimal("1200.00"))
                    .codigoDestinatario("99999999-9")
                    .nombreDestinatario("Destinatario General")
                    .usuarioDestinatario(destinatarioUser)
                    .build()));

            armadores.add(armadorRepository.save(DeclaracionArmadorModel.builder()
                    .usuario(armadorUser)
                    .especie(especie)
                    .humedadEstado(humedad)
                    .caleta(caleta)
                    .comuna(comuna)
                    .embarcacion(embarcacion)
                    .buzo(buzo)
                    .fechaDeclaracion(new Date())
                    .fechaExtraccion(new Date())
                    .hora("11:00:00")
                    .folioDesembarqueDa("DA-" + (2000 + i))
                    .codigoSernapescaEmbarcacion("EMB-9988")
                    .desembarque(new BigDecimal("3500.00"))
                    .captura(3500.0)
                    .usuarioDestinatario(destinatarioUser)
                    .build()));

            areas.add(areaRepository.save(DeclaracionAreaModel.builder()
                    .usuario(areaUser)
                    .especie(especie)
                    .humedadEstado(humedad)
                    .caleta(caleta)
                    .amerb(amerb)
                    .fechaDeclaracion(new Date())
                    .fechaExtraccion(new Date())
                    .hora("12:00:00")
                    .folioDesembarqueAmerb("AM-" + (3000 + i))
                    .codigoSernapescaAmerb("AMERB-01")
                    .desembarque(2500.0)
                    .captura(2500.0)
                    .tipoDestinatario("comercializador")
                    .usuarioDestinatario(destinatarioUser)
                    .build()));

            comercializadores.add(comercializadorRepository.save(DeclaracionComercializadorModel.builder()
                    .usuario(comercializadorUser)
                    .especie(especie)
                    .humedadEstado(humedad)
                    .fechaDeclaracion(new Date())
                    .hora("13:00:00")
                    .folioDesembarqueAc("AC-" + (4000 + i))
                    .codigoSernapesca("COM-7788")
                    .nombreComercializador("Comercializadora " + i)
                    .cantidad(new BigDecimal("5000.00"))
                    .codigoDestinatario("99999999-9")
                    .nombreDestinatario("Destinatario General")
                    .usuarioDestinatario(destinatarioUser)
                    .build()));
        }

        // 3. Crear 120 marcas intercaladas de los 4 tipos de declaración
        for (int i = 0; i < 120; i++) {
            int idx = i / 4;
            int tipoIdx = i % 4;
            String tipo;
            Long declId;

            switch (tipoIdx) {
                case 0 -> {
                    tipo = "RECOLECTOR";
                    declId = recolectores.get(idx).getId();
                }
                case 1 -> {
                    tipo = "ARMADOR";
                    declId = armadores.get(idx).getId();
                }
                case 2 -> {
                    tipo = "AREA";
                    declId = areas.get(idx).getId();
                }
                default -> {
                    tipo = "COMERCIALIZADOR";
                    declId = comercializadores.get(idx).getId();
                }
            }

            DeclaracionMarcaModel marca = DeclaracionMarcaModel.builder()
                    .declaracionTipo(tipo)
                    .declaracionId(declId)
                    .marca("DESEMBARQUE_ATIPICO")
                    .criterioParametro("desembarque_umbral_atipico_kg")
                    .criterioUmbral("5000")
                    .criterioValor("6000")
                    .criterioUnidad("kg")
                    .detalle("Marca de aceptación TA.3 número " + i)
                    .resuelta(false)
                    .estadoGestion("PENDIENTE")
                    .origen("VALIDACION")
                    .claveIdempotencia("TA3:" + tipo + ":" + declId + ":" + i)
                    .build();
            marcaRepository.save(marca);
        }

        // Sincronizar y limpiar persistencia antes de medir estadísticas
        em.flush();
        em.clear();

        org.hibernate.SessionFactory sessionFactory = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class);
        org.hibernate.stat.Statistics stats = sessionFactory.getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        // 4. Solicitar la página 2 de 50 (PageRequest.of(1, 50))
        Page<HallazgoDetalleDTO> pagina = queryRepository.buscar(
                FiltroHallazgos.builder().build(),
                PageRequest.of(1, 50)
        );

        // 5. Comprobar que se ejecutan 7 consultas SQL o menos
        long preparedStatements = stats.getPrepareStatementCount();
        assertTrue(preparedStatements <= 7,
                "Se esperaban 7 consultas o menos, pero se ejecutaron: " + preparedStatements);

        // 6. Comprobar 50 filas enriquecidas: folio, actor y (para armador) embarcación
        assertEquals(120, pagina.getTotalElements(), "Deben existir 120 marcas en total");
        assertEquals(50, pagina.getContent().size(), "La página 2 de 50 debe contener exactamente 50 elementos");

        for (HallazgoDetalleDTO dto : pagina.getContent()) {
            assertNotNull(dto.getFolio(), "El folio debe estar enriquecido para tipo " + dto.getDeclaracionTipo());
            assertNotNull(dto.getActorNombre(), "El actor debe estar enriquecido para tipo " + dto.getDeclaracionTipo());
            if ("ARMADOR".equalsIgnoreCase(dto.getDeclaracionTipo())) {
                assertNotNull(dto.getEmbarcacionNombre(), "La embarcación debe estar enriquecida para armadores");
                assertNotNull(dto.getEmbarcacionCodigo(), "El código de embarcación debe estar enriquecido para armadores");
            }
        }

        // Asegurar que en la página 2 coexisten los 4 tipos de declaración
        assertTrue(pagina.getContent().stream().anyMatch(d -> "RECOLECTOR".equalsIgnoreCase(d.getDeclaracionTipo())),
                "Debe haber RECOLECTOR en la página 2");
        assertTrue(pagina.getContent().stream().anyMatch(d -> "ARMADOR".equalsIgnoreCase(d.getDeclaracionTipo())),
                "Debe haber ARMADOR en la página 2");
        assertTrue(pagina.getContent().stream().anyMatch(d -> "AREA".equalsIgnoreCase(d.getDeclaracionTipo())),
                "Debe haber AREA en la página 2");
        assertTrue(pagina.getContent().stream().anyMatch(d -> "COMERCIALIZADOR".equalsIgnoreCase(d.getDeclaracionTipo())),
                "Debe haber COMERCIALIZADOR en la página 2");
    }
}
