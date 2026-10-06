package com.trazalga.api.services;

import com.trazalga.api.dto.ResultadoFolioDTO;
import com.trazalga.api.models.ConsultaFolioLogModel;
import com.trazalga.api.repositories.IConsultaFolioLogRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * Servicio para búsqueda puntual por folio, patente, código de embarcación
 * o documento tributario (T2.2).
 */
@Service
@Slf4j
public class ConsultaFolioService {

    @PersistenceContext
    private final EntityManager entityManager;

    private final ConfiguracionGeneralService configService;

    private final IConsultaFolioLogRepository logRepository;

    @Autowired
    public ConsultaFolioService(EntityManager entityManager,
                                @Autowired(required = false) ConfiguracionGeneralService configService,
                                @Autowired(required = false) IConsultaFolioLogRepository logRepository) {
        this.entityManager = entityManager;
        this.configService = configService;
        this.logRepository = logRepository;
    }

    /**
     * Realiza la búsqueda puntual por folio o documento.
     *
     * @param q       Texto o folio ingresado por el usuario
     * @param userRut RUT del usuario autenticado que realiza la búsqueda (del JWT)
     * @return Lista de hasta {@code consulta_folio_max_resultados} resultados
     */
    public List<ResultadoFolioDTO> buscar(String q, String userRut) {
        String qNorm = normalizarBusqueda(q);
        if (qNorm.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> termsSet = generarTerminosBusqueda(qNorm, q);
        List<String> terms = new ArrayList<>(termsSet);

        int diasEmbarcacion = configService != null ? configService.getInt("consulta_folio_dias_embarcacion", 30) : 30;
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -diasEmbarcacion);
        Date fechaDesdeEmbarcacion = cal.getTime();

        int maxResultados = configService != null ? configService.getInt("consulta_folio_max_resultados", 20) : 20;

        // Fase 1: Coincidencia exacta
        List<ResultadoFolioDTO> encontrados = ejecutarConsulta(terms, null, fechaDesdeEmbarcacion);

        // Fase 2: Coincidencia por el final si no hay resultados y longitud >= 6
        if (encontrados.isEmpty() && qNorm.length() >= 6) {
            String suffixPattern = "%" + qNorm;
            encontrados = ejecutarConsulta(null, suffixPattern, fechaDesdeEmbarcacion);
        }

        // Deduplicar declaraciones preservando la coincidencia más específica
        Map<String, ResultadoFolioDTO> dedup = new LinkedHashMap<>();
        for (ResultadoFolioDTO r : encontrados) {
            String key = r.getTipo() + ":" + r.getId();
            dedup.putIfAbsent(key, r);
        }

        List<ResultadoFolioDTO> resultadoFinal = new ArrayList<>(dedup.values());

        // Ordenar del más reciente al más antiguo
        resultadoFinal.sort((a, b) -> {
            if (a.getFecha() != null && b.getFecha() != null) {
                int cmp = b.getFecha().compareTo(a.getFecha());
                if (cmp != 0) return cmp;
            }
            if (a.getId() != null && b.getId() != null) {
                return b.getId().compareTo(a.getId());
            }
            return 0;
        });

        if (resultadoFinal.size() > maxResultados) {
            resultadoFinal = resultadoFinal.subList(0, maxResultados);
        }

        // Registrar auditoría de la consulta
        registrarLog(userRut, q, resultadoFinal.size());

        return resultadoFinal;
    }

    /**
     * Normaliza la entrada a mayúsculas sin espacios ni guiones.
     */
    public String normalizarBusqueda(String q) {
        if (q == null) return "";
        return q.toUpperCase().replaceAll("[\\s\\-]+", "").trim();
    }

    /**
     * Genera los términos derivados: si es solo dígitos, antepone prefijos oficiales (RO, DA, AC, DAPLA, AMERB).
     */
    public Set<String> generarTerminosBusqueda(String qNorm, String qRaw) {
        Set<String> terms = new LinkedHashSet<>();
        terms.add(qNorm);

        boolean soloDigitos = qNorm.matches("^\\d+$");
        if (soloDigitos) {
            terms.add("RO" + qNorm);
            terms.add("DA" + qNorm);
            terms.add("AC" + qNorm);
            terms.add("AMERB" + qNorm);
            terms.add("DAPLA" + qNorm);
            terms.add("A-PLA" + qNorm);
            terms.add("A_PLA" + qNorm);
        }

        // Si coincide con patente normalizada
        String patNorm = ConsultaPatenteService.normalizarPatente(qRaw);
        if (patNorm != null && !patNorm.isBlank()) {
            terms.add(patNorm);
        }

        return terms;
    }

    private List<ResultadoFolioDTO> ejecutarConsulta(List<String> exactTerms, String suffixPattern, Date fechaDesde) {
        boolean esExacta = exactTerms != null && !exactTerms.isEmpty();
        String sql = construirSqlUnion(esExacta);

        try {
            Query query = entityManager.createNativeQuery(sql);
            if (esExacta) {
                query.setParameter("terms", exactTerms);
            } else {
                query.setParameter("suffixPattern", suffixPattern);
            }
            query.setParameter("fechaDesde", fechaDesde);

            @SuppressWarnings("unchecked")
            List<Object[]> rows = query.getResultList();
            List<ResultadoFolioDTO> out = new ArrayList<>();

            for (Object[] r : rows) {
                String tipo = r[0] != null ? r[0].toString() : "";
                Long id = r[1] != null ? ((Number) r[1]).longValue() : null;
                String campo = r[2] != null ? r[2].toString() : "";
                String valor = r[3] != null ? r[3].toString() : "";
                Date fecha = toDate(r[4]);
                String actor = r[5] != null ? r[5].toString() : "";
                String comunaOCaleta = r[6] != null ? r[6].toString() : "";
                String especie = r[7] != null ? r[7].toString() : "";
                BigDecimal kg = r[8] != null ? new BigDecimal(r[8].toString()) : BigDecimal.ZERO;
                String estado = r[9] != null ? r[9].toString() : "ENVIADA";

                out.add(ResultadoFolioDTO.builder()
                        .tipo(tipo)
                        .id(id)
                        .campo(campo)
                        .valor(valor)
                        .fecha(fecha)
                        .actor(actor)
                        .comunaOCaleta(comunaOCaleta)
                        .especie(especie)
                        .kg(kg)
                        .estado(estado)
                        .build());
            }

            return out;
        } catch (Exception e) {
            log.error("Error al ejecutar búsqueda por folio: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private String construirSqlUnion(boolean esExacta) {
        String condR_origen = esExacta ? "r.folio_origen IN (:terms)" : "r.folio_origen LIKE :suffixPattern";
        String condR_ro = esExacta ? "r.folio_desembarque_ro IN (:terms)" : "r.folio_desembarque_ro LIKE :suffixPattern";

        String condA_origen = esExacta ? "a.folio_origen IN (:terms)" : "a.folio_origen LIKE :suffixPattern";
        String condA_da = esExacta ? "a.folio_desembarque_da IN (:terms)" : "a.folio_desembarque_da LIKE :suffixPattern";
        String condA_emb = esExacta
                ? "(emb.codigo IN (:terms) OR a.codigo_sernapesca_embarcacion IN (:terms))"
                : "(emb.codigo LIKE :suffixPattern OR a.codigo_sernapesca_embarcacion LIKE :suffixPattern)";

        String condAr_origen = esExacta ? "ar.folio_origen IN (:terms)" : "ar.folio_origen LIKE :suffixPattern";
        String condAr_amerb = esExacta ? "ar.folio_desembarque_amerb IN (:terms)" : "ar.folio_desembarque_amerb LIKE :suffixPattern";

        String condC_origen = esExacta ? "c.folio_origen IN (:terms)" : "c.folio_origen LIKE :suffixPattern";
        String condC_ac = esExacta ? "c.folio_desembarque_ac IN (:terms)" : "c.folio_desembarque_ac LIKE :suffixPattern";
        String condC_docOrig = esExacta ? "c.documento_tributario_origen_numero IN (:terms)" : "c.documento_tributario_origen_numero LIKE :suffixPattern";
        String condC_docDest = esExacta ? "c.documento_tributario_destino_numero IN (:terms)" : "c.documento_tributario_destino_numero LIKE :suffixPattern";
        String condC_patente = esExacta ? "c.placa_patente IN (:terms)" : "c.placa_patente LIKE :suffixPattern";
        String condC_carro = esExacta ? "c.placa_patente_carro IN (:terms)" : "c.placa_patente_carro LIKE :suffixPattern";

        String condP_origen = esExacta ? "p.folio_origen IN (:terms)" : "p.folio_origen LIKE :suffixPattern";
        String condP_dapla = esExacta ? "p.folio_declaracion_a_pla IN (:terms)" : "p.folio_declaracion_a_pla LIKE :suffixPattern";
        String condP_docOrig = esExacta ? "p.documento_tributario_origen_numero IN (:terms)" : "p.documento_tributario_origen_numero LIKE :suffixPattern";
        String condP_docDest = esExacta ? "p.documento_tributario_destino_numero IN (:terms)" : "p.documento_tributario_destino_numero LIKE :suffixPattern";
        String condP_docNum = esExacta ? "p.documento_tributario_numero IN (:terms)" : "p.documento_tributario_numero LIKE :suffixPattern";
        String condP_patente = esExacta ? "p.placa_patente IN (:terms)" : "p.placa_patente LIKE :suffixPattern";
        String condP_carro = esExacta ? "p.placa_patente_carro IN (:terms)" : "p.placa_patente_carro LIKE :suffixPattern";

        StringBuilder sb = new StringBuilder();

        // 1. Recolector - Folio Origen
        sb.append("SELECT 'RECOLECTOR' as tipo, r.id, 'Folio Origen' as campo, r.folio_origen as valor, ")
                .append("r.fecha_declaracion as fecha, TRIM(CONCAT(COALESCE(u.nombres, ''), ' ', COALESCE(u.apellidop, ''))) as actor, ")
                .append("COALESCE(c.nombre, com.nombre, 'Sin ubicación') as comuna_o_caleta, ")
                .append("COALESCE(e.nombre, 'Sin especie') as especie, r.desembarque as kg, COALESCE(r.estado, 'ENVIADA') as estado ")
                .append("FROM declaracion_recolector r ")
                .append("INNER JOIN usuario u ON r.usuario_id = u.id ")
                .append("LEFT JOIN caleta c ON r.caleta_id = c.id ")
                .append("LEFT JOIN comuna com ON r.comuna_id = com.id ")
                .append("LEFT JOIN especie e ON r.especie_id = e.id ")
                .append("WHERE ").append(condR_origen).append(" ");

        // 2. Recolector - Folio RO
        sb.append("UNION ALL ")
                .append("SELECT 'RECOLECTOR' as tipo, r.id, 'Folio RO' as campo, r.folio_desembarque_ro as valor, ")
                .append("r.fecha_declaracion as fecha, TRIM(CONCAT(COALESCE(u.nombres, ''), ' ', COALESCE(u.apellidop, ''))) as actor, ")
                .append("COALESCE(c.nombre, com.nombre, 'Sin ubicación') as comuna_o_caleta, ")
                .append("COALESCE(e.nombre, 'Sin especie') as especie, r.desembarque as kg, COALESCE(r.estado, 'ENVIADA') as estado ")
                .append("FROM declaracion_recolector r ")
                .append("INNER JOIN usuario u ON r.usuario_id = u.id ")
                .append("LEFT JOIN caleta c ON r.caleta_id = c.id ")
                .append("LEFT JOIN comuna com ON r.comuna_id = com.id ")
                .append("LEFT JOIN especie e ON r.especie_id = e.id ")
                .append("WHERE ").append(condR_ro).append(" ");

        // 3. Armador - Folio Origen
        sb.append("UNION ALL ")
                .append("SELECT 'ARMADOR' as tipo, a.id, 'Folio Origen' as campo, a.folio_origen as valor, ")
                .append("a.fecha_declaracion as fecha, TRIM(CONCAT(COALESCE(u.nombres, ''), ' ', COALESCE(u.apellidop, ''))) as actor, ")
                .append("COALESCE(c.nombre, com.nombre, 'Sin ubicación') as comuna_o_caleta, ")
                .append("COALESCE(e.nombre, 'Sin especie') as especie, a.desembarque as kg, COALESCE(a.estado, 'ENVIADA') as estado ")
                .append("FROM declaracion_armador a ")
                .append("INNER JOIN usuario u ON a.usuario_id = u.id ")
                .append("LEFT JOIN caleta c ON a.caleta_id = c.id ")
                .append("LEFT JOIN comuna com ON a.comuna_id = com.id ")
                .append("LEFT JOIN especie e ON a.especie_id = e.id ")
                .append("WHERE ").append(condA_origen).append(" ");

        // 4. Armador - Folio DA
        sb.append("UNION ALL ")
                .append("SELECT 'ARMADOR' as tipo, a.id, 'Folio DA' as campo, a.folio_desembarque_da as valor, ")
                .append("a.fecha_declaracion as fecha, TRIM(CONCAT(COALESCE(u.nombres, ''), ' ', COALESCE(u.apellidop, ''))) as actor, ")
                .append("COALESCE(c.nombre, com.nombre, 'Sin ubicación') as comuna_o_caleta, ")
                .append("COALESCE(e.nombre, 'Sin especie') as especie, a.desembarque as kg, COALESCE(a.estado, 'ENVIADA') as estado ")
                .append("FROM declaracion_armador a ")
                .append("INNER JOIN usuario u ON a.usuario_id = u.id ")
                .append("LEFT JOIN caleta c ON a.caleta_id = c.id ")
                .append("LEFT JOIN comuna com ON a.comuna_id = com.id ")
                .append("LEFT JOIN especie e ON a.especie_id = e.id ")
                .append("WHERE ").append(condA_da).append(" ");

        // 5. Armador - Código de embarcación
        sb.append("UNION ALL ")
                .append("SELECT 'ARMADOR' as tipo, a.id, 'Código de embarcación' as campo, COALESCE(emb.codigo, a.codigo_sernapesca_embarcacion) as valor, ")
                .append("a.fecha_declaracion as fecha, TRIM(CONCAT(COALESCE(u.nombres, ''), ' ', COALESCE(u.apellidop, ''))) as actor, ")
                .append("COALESCE(c.nombre, com.nombre, 'Sin ubicación') as comuna_o_caleta, ")
                .append("COALESCE(e.nombre, 'Sin especie') as especie, a.desembarque as kg, COALESCE(a.estado, 'ENVIADA') as estado ")
                .append("FROM declaracion_armador a ")
                .append("INNER JOIN usuario u ON a.usuario_id = u.id ")
                .append("LEFT JOIN caleta c ON a.caleta_id = c.id ")
                .append("LEFT JOIN comuna com ON a.comuna_id = com.id ")
                .append("LEFT JOIN especie e ON a.especie_id = e.id ")
                .append("LEFT JOIN embarcacion emb ON a.embarcacion_id = emb.id ")
                .append("WHERE ").append(condA_emb).append(" AND a.fecha_declaracion >= :fechaDesde ");

        // 6. Area - Folio Origen
        sb.append("UNION ALL ")
                .append("SELECT 'AREA' as tipo, ar.id, 'Folio Origen' as campo, ar.folio_origen as valor, ")
                .append("ar.fecha_declaracion as fecha, TRIM(CONCAT(COALESCE(u.nombres, ''), ' ', COALESCE(u.apellidop, ''))) as actor, ")
                .append("COALESCE(c.nombre, 'Sin caleta') as comuna_o_caleta, ")
                .append("COALESCE(e.nombre, 'Sin especie') as especie, ar.desembarque as kg, COALESCE(ar.estado, 'ENVIADA') as estado ")
                .append("FROM declaracion_area ar ")
                .append("INNER JOIN usuario u ON ar.usuario_id = u.id ")
                .append("LEFT JOIN caleta c ON ar.caleta_id = c.id ")
                .append("LEFT JOIN especie e ON ar.especie_id = e.id ")
                .append("WHERE ").append(condAr_origen).append(" ");

        // 7. Area - Folio AMERB
        sb.append("UNION ALL ")
                .append("SELECT 'AREA' as tipo, ar.id, 'Folio AMERB' as campo, ar.folio_desembarque_amerb as valor, ")
                .append("ar.fecha_declaracion as fecha, TRIM(CONCAT(COALESCE(u.nombres, ''), ' ', COALESCE(u.apellidop, ''))) as actor, ")
                .append("COALESCE(c.nombre, 'Sin caleta') as comuna_o_caleta, ")
                .append("COALESCE(e.nombre, 'Sin especie') as especie, ar.desembarque as kg, COALESCE(ar.estado, 'ENVIADA') as estado ")
                .append("FROM declaracion_area ar ")
                .append("INNER JOIN usuario u ON ar.usuario_id = u.id ")
                .append("LEFT JOIN caleta c ON ar.caleta_id = c.id ")
                .append("LEFT JOIN especie e ON ar.especie_id = e.id ")
                .append("WHERE ").append(condAr_amerb).append(" ");

        // 8. Comercializador - Folio Origen
        sb.append("UNION ALL ")
                .append("SELECT 'COMERCIALIZADOR' as tipo, c.id, 'Folio Origen' as campo, c.folio_origen as valor, ")
                .append("c.fecha_declaracion as fecha, TRIM(CONCAT(COALESCE(u.nombres, ''), ' ', COALESCE(u.apellidop, ''))) as actor, ")
                .append("COALESCE(com.nombre, 'Sin comuna') as comuna_o_caleta, ")
                .append("COALESCE(e.nombre, 'Sin especie') as especie, c.cantidad as kg, COALESCE(c.estado, 'ENVIADA') as estado ")
                .append("FROM declaracion_comercializador c ")
                .append("INNER JOIN usuario u ON c.usuario_id = u.id ")
                .append("LEFT JOIN comuna com ON u.comuna_id = com.id ")
                .append("LEFT JOIN especie e ON c.especie_id = e.id ")
                .append("WHERE ").append(condC_origen).append(" ");

        // 9. Comercializador - Folio AC
        sb.append("UNION ALL ")
                .append("SELECT 'COMERCIALIZADOR' as tipo, c.id, 'Folio AC' as campo, c.folio_desembarque_ac as valor, ")
                .append("c.fecha_declaracion as fecha, TRIM(CONCAT(COALESCE(u.nombres, ''), ' ', COALESCE(u.apellidop, ''))) as actor, ")
                .append("COALESCE(com.nombre, 'Sin comuna') as comuna_o_caleta, ")
                .append("COALESCE(e.nombre, 'Sin especie') as especie, c.cantidad as kg, COALESCE(c.estado, 'ENVIADA') as estado ")
                .append("FROM declaracion_comercializador c ")
                .append("INNER JOIN usuario u ON c.usuario_id = u.id ")
                .append("LEFT JOIN comuna com ON u.comuna_id = com.id ")
                .append("LEFT JOIN especie e ON c.especie_id = e.id ")
                .append("WHERE ").append(condC_ac).append(" ");

        // 10. Comercializador - Guía/Doc Origen
        sb.append("UNION ALL ")
                .append("SELECT 'COMERCIALIZADOR' as tipo, c.id, 'Guía de despacho N.º' as campo, c.documento_tributario_origen_numero as valor, ")
                .append("c.fecha_declaracion as fecha, TRIM(CONCAT(COALESCE(u.nombres, ''), ' ', COALESCE(u.apellidop, ''))) as actor, ")
                .append("COALESCE(com.nombre, 'Sin comuna') as comuna_o_caleta, ")
                .append("COALESCE(e.nombre, 'Sin especie') as especie, c.cantidad as kg, COALESCE(c.estado, 'ENVIADA') as estado ")
                .append("FROM declaracion_comercializador c ")
                .append("INNER JOIN usuario u ON c.usuario_id = u.id ")
                .append("LEFT JOIN comuna com ON u.comuna_id = com.id ")
                .append("LEFT JOIN especie e ON c.especie_id = e.id ")
                .append("WHERE ").append(condC_docOrig).append(" ");

        // 11. Comercializador - Guía/Doc Destino
        sb.append("UNION ALL ")
                .append("SELECT 'COMERCIALIZADOR' as tipo, c.id, 'Guía de despacho destino' as campo, c.documento_tributario_destino_numero as valor, ")
                .append("c.fecha_declaracion as fecha, TRIM(CONCAT(COALESCE(u.nombres, ''), ' ', COALESCE(u.apellidop, ''))) as actor, ")
                .append("COALESCE(com.nombre, 'Sin comuna') as comuna_o_caleta, ")
                .append("COALESCE(e.nombre, 'Sin especie') as especie, c.cantidad as kg, COALESCE(c.estado, 'ENVIADA') as estado ")
                .append("FROM declaracion_comercializador c ")
                .append("INNER JOIN usuario u ON c.usuario_id = u.id ")
                .append("LEFT JOIN comuna com ON u.comuna_id = com.id ")
                .append("LEFT JOIN especie e ON c.especie_id = e.id ")
                .append("WHERE ").append(condC_docDest).append(" ");

        // 12. Comercializador - Patente
        sb.append("UNION ALL ")
                .append("SELECT 'COMERCIALIZADOR' as tipo, c.id, 'Patente camión' as campo, c.placa_patente as valor, ")
                .append("c.fecha_declaracion as fecha, TRIM(CONCAT(COALESCE(u.nombres, ''), ' ', COALESCE(u.apellidop, ''))) as actor, ")
                .append("COALESCE(com.nombre, 'Sin comuna') as comuna_o_caleta, ")
                .append("COALESCE(e.nombre, 'Sin especie') as especie, c.cantidad as kg, COALESCE(c.estado, 'ENVIADA') as estado ")
                .append("FROM declaracion_comercializador c ")
                .append("INNER JOIN usuario u ON c.usuario_id = u.id ")
                .append("LEFT JOIN comuna com ON u.comuna_id = com.id ")
                .append("LEFT JOIN especie e ON c.especie_id = e.id ")
                .append("WHERE ").append(condC_patente).append(" ");

        // 13. Comercializador - Patente Carro
        sb.append("UNION ALL ")
                .append("SELECT 'COMERCIALIZADOR' as tipo, c.id, 'Patente carro' as campo, c.placa_patente_carro as valor, ")
                .append("c.fecha_declaracion as fecha, TRIM(CONCAT(COALESCE(u.nombres, ''), ' ', COALESCE(u.apellidop, ''))) as actor, ")
                .append("COALESCE(com.nombre, 'Sin comuna') as comuna_o_caleta, ")
                .append("COALESCE(e.nombre, 'Sin especie') as especie, c.cantidad as kg, COALESCE(c.estado, 'ENVIADA') as estado ")
                .append("FROM declaracion_comercializador c ")
                .append("INNER JOIN usuario u ON c.usuario_id = u.id ")
                .append("LEFT JOIN comuna com ON u.comuna_id = com.id ")
                .append("LEFT JOIN especie e ON c.especie_id = e.id ")
                .append("WHERE ").append(condC_carro).append(" ");

        // 14. Planta Abastecimiento - Folio Origen
        sb.append("UNION ALL ")
                .append("SELECT 'PLANTA_ABASTECIMIENTO' as tipo, p.id, 'Folio Origen' as campo, p.folio_origen as valor, ")
                .append("p.fecha_ingreso_planta as fecha, TRIM(CONCAT(COALESCE(u.nombres, ''), ' ', COALESCE(u.apellidop, ''))) as actor, ")
                .append("COALESCE(p.nombre_planta, com.nombre, 'Planta') as comuna_o_caleta, ")
                .append("COALESCE(e.nombre, 'Sin especie') as especie, p.cantidad as kg, COALESCE(p.estado, 'ENVIADA') as estado ")
                .append("FROM declaracion_planta_abastecimiento p ")
                .append("INNER JOIN usuario u ON p.usuario_id = u.id ")
                .append("LEFT JOIN comuna com ON u.comuna_id = com.id ")
                .append("LEFT JOIN especie e ON p.especie_id = e.id ")
                .append("WHERE ").append(condP_origen).append(" ");

        // 15. Planta Abastecimiento - Folio DAPLA
        sb.append("UNION ALL ")
                .append("SELECT 'PLANTA_ABASTECIMIENTO' as tipo, p.id, 'Folio DAPLA' as campo, p.folio_declaracion_a_pla as valor, ")
                .append("p.fecha_ingreso_planta as fecha, TRIM(CONCAT(COALESCE(u.nombres, ''), ' ', COALESCE(u.apellidop, ''))) as actor, ")
                .append("COALESCE(p.nombre_planta, com.nombre, 'Planta') as comuna_o_caleta, ")
                .append("COALESCE(e.nombre, 'Sin especie') as especie, p.cantidad as kg, COALESCE(p.estado, 'ENVIADA') as estado ")
                .append("FROM declaracion_planta_abastecimiento p ")
                .append("INNER JOIN usuario u ON p.usuario_id = u.id ")
                .append("LEFT JOIN comuna com ON u.comuna_id = com.id ")
                .append("LEFT JOIN especie e ON p.especie_id = e.id ")
                .append("WHERE ").append(condP_dapla).append(" ");

        // 16. Planta Abastecimiento - Guía/Doc Origen
        sb.append("UNION ALL ")
                .append("SELECT 'PLANTA_ABASTECIMIENTO' as tipo, p.id, 'Guía de despacho N.º' as campo, p.documento_tributario_origen_numero as valor, ")
                .append("p.fecha_ingreso_planta as fecha, TRIM(CONCAT(COALESCE(u.nombres, ''), ' ', COALESCE(u.apellidop, ''))) as actor, ")
                .append("COALESCE(p.nombre_planta, com.nombre, 'Planta') as comuna_o_caleta, ")
                .append("COALESCE(e.nombre, 'Sin especie') as especie, p.cantidad as kg, COALESCE(p.estado, 'ENVIADA') as estado ")
                .append("FROM declaracion_planta_abastecimiento p ")
                .append("INNER JOIN usuario u ON p.usuario_id = u.id ")
                .append("LEFT JOIN comuna com ON u.comuna_id = com.id ")
                .append("LEFT JOIN especie e ON p.especie_id = e.id ")
                .append("WHERE ").append(condP_docOrig).append(" ");

        // 17. Planta Abastecimiento - Guía/Doc Destino
        sb.append("UNION ALL ")
                .append("SELECT 'PLANTA_ABASTECIMIENTO' as tipo, p.id, 'Guía de despacho destino' as campo, p.documento_tributario_destino_numero as valor, ")
                .append("p.fecha_ingreso_planta as fecha, TRIM(CONCAT(COALESCE(u.nombres, ''), ' ', COALESCE(u.apellidop, ''))) as actor, ")
                .append("COALESCE(p.nombre_planta, com.nombre, 'Planta') as comuna_o_caleta, ")
                .append("COALESCE(e.nombre, 'Sin especie') as especie, p.cantidad as kg, COALESCE(p.estado, 'ENVIADA') as estado ")
                .append("FROM declaracion_planta_abastecimiento p ")
                .append("INNER JOIN usuario u ON p.usuario_id = u.id ")
                .append("LEFT JOIN comuna com ON u.comuna_id = com.id ")
                .append("LEFT JOIN especie e ON p.especie_id = e.id ")
                .append("WHERE ").append(condP_docDest).append(" ");

        // 18. Planta Abastecimiento - Doc Tributario
        sb.append("UNION ALL ")
                .append("SELECT 'PLANTA_ABASTECIMIENTO' as tipo, p.id, 'Documento tributario' as campo, p.documento_tributario_numero as valor, ")
                .append("p.fecha_ingreso_planta as fecha, TRIM(CONCAT(COALESCE(u.nombres, ''), ' ', COALESCE(u.apellidop, ''))) as actor, ")
                .append("COALESCE(p.nombre_planta, com.nombre, 'Planta') as comuna_o_caleta, ")
                .append("COALESCE(e.nombre, 'Sin especie') as especie, p.cantidad as kg, COALESCE(p.estado, 'ENVIADA') as estado ")
                .append("FROM declaracion_planta_abastecimiento p ")
                .append("INNER JOIN usuario u ON p.usuario_id = u.id ")
                .append("LEFT JOIN comuna com ON u.comuna_id = com.id ")
                .append("LEFT JOIN especie e ON p.especie_id = e.id ")
                .append("WHERE ").append(condP_docNum).append(" ");

        // 19. Planta Abastecimiento - Patente
        sb.append("UNION ALL ")
                .append("SELECT 'PLANTA_ABASTECIMIENTO' as tipo, p.id, 'Patente camión' as campo, p.placa_patente as valor, ")
                .append("p.fecha_ingreso_planta as fecha, TRIM(CONCAT(COALESCE(u.nombres, ''), ' ', COALESCE(u.apellidop, ''))) as actor, ")
                .append("COALESCE(p.nombre_planta, com.nombre, 'Planta') as comuna_o_caleta, ")
                .append("COALESCE(e.nombre, 'Sin especie') as especie, p.cantidad as kg, COALESCE(p.estado, 'ENVIADA') as estado ")
                .append("FROM declaracion_planta_abastecimiento p ")
                .append("INNER JOIN usuario u ON p.usuario_id = u.id ")
                .append("LEFT JOIN comuna com ON u.comuna_id = com.id ")
                .append("LEFT JOIN especie e ON p.especie_id = e.id ")
                .append("WHERE ").append(condP_patente).append(" ");

        // 20. Planta Abastecimiento - Patente Carro
        sb.append("UNION ALL ")
                .append("SELECT 'PLANTA_ABASTECIMIENTO' as tipo, p.id, 'Patente carro' as campo, p.placa_patente_carro as valor, ")
                .append("p.fecha_ingreso_planta as fecha, TRIM(CONCAT(COALESCE(u.nombres, ''), ' ', COALESCE(u.apellidop, ''))) as actor, ")
                .append("COALESCE(p.nombre_planta, com.nombre, 'Planta') as comuna_o_caleta, ")
                .append("COALESCE(e.nombre, 'Sin especie') as especie, p.cantidad as kg, COALESCE(p.estado, 'ENVIADA') as estado ")
                .append("FROM declaracion_planta_abastecimiento p ")
                .append("INNER JOIN usuario u ON p.usuario_id = u.id ")
                .append("LEFT JOIN comuna com ON u.comuna_id = com.id ")
                .append("LEFT JOIN especie e ON p.especie_id = e.id ")
                .append("WHERE ").append(condP_carro);

        return sb.toString();
    }

    private void registrarLog(String userRut, String textoBuscado, int cantidadResultados) {
        if (logRepository == null || userRut == null || userRut.isBlank()) {
            return;
        }
        try {
            logRepository.save(ConsultaFolioLogModel.builder()
                    .usuarioRut(userRut)
                    .textoBuscado(textoBuscado != null ? textoBuscado : "")
                    .cantidadResultados(cantidadResultados)
                    .fecha(new Date())
                    .build());
        } catch (Exception e) {
            log.warn("No se pudo registrar log de consulta folio: {}", e.getMessage());
        }
    }

    private Date toDate(Object o) {
        if (o == null) return null;
        if (o instanceof java.sql.Timestamp) return new Date(((java.sql.Timestamp) o).getTime());
        if (o instanceof java.sql.Date) return new Date(((java.sql.Date) o).getTime());
        if (o instanceof Date) return (Date) o;
        return null;
    }
}
