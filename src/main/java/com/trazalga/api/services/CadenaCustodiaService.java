package com.trazalga.api.services;

import com.trazalga.api.dto.TrazabilidadEdgeDTO;
import com.trazalga.api.dto.TrazabilidadNodoDTO;
import com.trazalga.api.dto.TrazabilidadResponseDTO;
import com.trazalga.api.services.trazabilidad.SeleccionTokens;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio unificado para la resolución y trazabilidad de la cadena de custodia (T2.1).
 *
 * <p>Centraliza la lógica de recorrido hacia atrás (declaraciones consumidas)
 * y hacia adelante (destinatarios), cálculo de horas en bodega virtual y semáforos
 * bajo normativa de Retención (Res. 3602).</p>
 */
@Service
@Slf4j
public class CadenaCustodiaService {

    private static final int TOPE_MAX_NODOS = 500;

    @PersistenceContext
    private final EntityManager entityManager;

    private final ConfiguracionGeneralService configService;

    // Constructor con inyección de dependencias
    @Autowired
    public CadenaCustodiaService(EntityManager entityManager,
                                 @Autowired(required = false) ConfiguracionGeneralService configService) {
        this.entityManager = entityManager;
        this.configService = configService;
    }

    // =========================================================================
    // DTOs Y MODELOS DE DOMINIO DE LA CADENA
    // =========================================================================

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrigenRef {
        private String tipo; // RECOLECTOR, ARMADOR, AREA
        private Long id;
        private String folio;
        private Date fecha;
        private String hora;
        private LocalDateTime timestamp;
        private BigDecimal cantidad;
        private String rut;
        private String actor;
        private Long especieId;
        private String especieNombre;
        private Long humedadEstadoId;
        private String humedadNombre;
        private Long usuarioDestinatarioId;
        private String nombreDestinatario;
        private Long declaracionDestinatarioId;
        private String consumidaPorTipo;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComercializadorTramo {
        private Long id; // declaracion_comercializador id, o null si sin despachar
        private int salto; // 1, 2, ...
        private LocalDateTime entrada;
        private LocalDateTime salida; // null si no ha despachado
        private Double horasEnBodega;
        private String semaforo; // VERDE, AMARILLO, ROJO
        private boolean despachado;

        // Datos del destinatario / comercializador
        private Long usuarioDestinatarioId;
        private String rut;
        private String actor;
        private String folio;
        private BigDecimal cantidad;
        private Date fechaDeclaracion;
        private String hora;
        private Date fechaTraslado;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SemaforoInfo {
        private String semaforo; // "VERDE", "AMARILLO", "ROJO"
        private double horas;
        private int plazoMaxHoras;
        private int preavisoPct;
        private String estadoHumedad;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Cadena {
        private String tipoConsulta;
        private Long idConsulta;

        @Builder.Default
        private List<OrigenRef> origenes = new ArrayList<>();

        @Builder.Default
        private List<Long> comercializadorIds = new ArrayList<>(); // en orden de saltos

        @Builder.Default
        private List<ComercializadorTramo> comercializadoresTramos = new ArrayList<>(); // información por salto

        private Long plantaAbastecimientoId;

        @Builder.Default
        private List<Long> plantaProduccionIds = new ArrayList<>();

        @Builder.Default
        private List<Long> plantaDestinoIds = new ArrayList<>();

        @Builder.Default
        private List<TrazabilidadNodoDTO> nodos = new ArrayList<>();

        @Builder.Default
        private List<TrazabilidadEdgeDTO> enlaces = new ArrayList<>();

        private String estadoHumedadPredominante;

        // Helpers de conveniencia
        public List<Long> getRecolectorIds() {
            return origenes.stream()
                    .filter(o -> "RECOLECTOR".equalsIgnoreCase(o.getTipo()))
                    .map(OrigenRef::getId)
                    .collect(Collectors.toList());
        }

        public List<Long> getArmadorIds() {
            return origenes.stream()
                    .filter(o -> "ARMADOR".equalsIgnoreCase(o.getTipo()))
                    .map(OrigenRef::getId)
                    .collect(Collectors.toList());
        }

        public List<Long> getAreaIds() {
            return origenes.stream()
                    .filter(o -> "AREA".equalsIgnoreCase(o.getTipo()))
                    .map(OrigenRef::getId)
                    .collect(Collectors.toList());
        }

        public TrazabilidadResponseDTO toTrazabilidadResponseDTO() {
            return new TrazabilidadResponseDTO(nodos, enlaces);
        }
    }

    @Getter
    @Setter
    private static class RawNodeData {
        private Long id;
        private String tipoStr;
        private String roleName;
        private String nombreActor;
        private String rutActor;
        private Date fecha;
        private String hora;
        private LocalDateTime timestamp;
        private BigDecimal cantidad;
        private String folio;
        private Long declaracionDestinatarioId;
        private String consumidaPorTipo;
        private String declaracionesSeleccionadas;
        private Long usuarioDestinatarioId;
        private String nombreDestinatario;
        private Long especieId;
        private String especieNombre;
        private Long humedadEstadoId;
        private String humedadNombre;
        private TrazabilidadNodoDTO dto;
    }

    // =========================================================================
    // RESOLUCIÓN DE CADENA POR LOTES (T2.1)
    // =========================================================================

    public Cadena resolver(Integer tipo, Long id) {
        return resolver(normalizarTipo(tipo), id);
    }

    public Cadena resolver(String tipo, Long id) {
        String initialTipoStr = normalizarTipo(tipo);
        if (id == null || "DESCONOCIDO".equals(initialTipoStr)) {
            return Cadena.builder()
                    .tipoConsulta(initialTipoStr)
                    .idConsulta(id)
                    .build();
        }

        String initialKey = initialTipoStr + ":" + id;
        Set<String> visitedKeys = new LinkedHashSet<>();
        visitedKeys.add(initialKey);

        Map<String, RawNodeData> allNodes = new LinkedHashMap<>();
        List<TrazabilidadEdgeDTO> enlaces = new ArrayList<>();
        Set<String> edgeSignatures = new HashSet<>();

        Set<String> currentFrontier = new LinkedHashSet<>();
        currentFrontier.add(initialKey);

        while (!currentFrontier.isEmpty() && allNodes.size() < TOPE_MAX_NODOS) {
            // 1. Agrupar la frontera actual por tipo para hacer una consulta por tipo de eslabón
            Map<String, Set<Long>> pendingByType = new HashMap<>();
            for (String key : currentFrontier) {
                int idx = key.indexOf(':');
                if (idx > 0) {
                    String t = key.substring(0, idx);
                    try {
                        Long nid = Long.parseLong(key.substring(idx + 1));
                        pendingByType.computeIfAbsent(t, k -> new HashSet<>()).add(nid);
                    } catch (NumberFormatException ignored) {}
                }
            }

            // 2. Ejecutar una consulta por tipo de eslabón
            for (Map.Entry<String, Set<Long>> entry : pendingByType.entrySet()) {
                if (allNodes.size() >= TOPE_MAX_NODOS) break;
                String tStr = entry.getKey();
                Set<Long> idsToFetch = entry.getValue();

                Map<Long, RawNodeData> batchMap = fetchBatchNodes(tStr, idsToFetch);
                for (Map.Entry<Long, RawNodeData> bEntry : batchMap.entrySet()) {
                    if (allNodes.size() >= TOPE_MAX_NODOS) break;
                    allNodes.put(tStr + ":" + bEntry.getKey(), bEntry.getValue());
                }
            }

            // 3. Expandir la frontera (hacia adelante y hacia atrás)
            Set<String> nextFrontier = new LinkedHashSet<>();
            for (String key : currentFrontier) {
                RawNodeData raw = allNodes.get(key);
                if (raw == null) continue;

                // Eslabón siguiente aguas abajo (Forward: declaracion_destinatario_id)
                if (raw.getDeclaracionDestinatarioId() != null && raw.getConsumidaPorTipo() != null) {
                    String childTipo = normalizarTipo(raw.getConsumidaPorTipo());
                    String childKey = childTipo + ":" + raw.getDeclaracionDestinatarioId();
                    String edgeKey = key + "->" + childKey;
                    if (edgeSignatures.add(edgeKey)) {
                        enlaces.add(new TrazabilidadEdgeDTO(key, childKey));
                    }
                    if (!visitedKeys.contains(childKey) && visitedKeys.size() < TOPE_MAX_NODOS) {
                        visitedKeys.add(childKey);
                        nextFrontier.add(childKey);
                    }
                }

                // Eslabones previos aguas arriba (Backward: declaraciones_seleccionadas)
                if (raw.getDeclaracionesSeleccionadas() != null && !raw.getDeclaracionesSeleccionadas().trim().isEmpty()) {
                    Map<String, List<Long>> parsed = SeleccionTokens.parse(raw.getDeclaracionesSeleccionadas());
                    List<String> possibleParents = getPossibleParentTypes(raw.getTipoStr());
                    for (String pType : possibleParents) {
                        List<Long> pIds = SeleccionTokens.idsParaTipo(parsed, pType);
                        for (Long pId : pIds) {
                            String parentKey = normalizarTipo(pType) + ":" + pId;
                            String edgeKey = parentKey + "->" + key;
                            if (edgeSignatures.add(edgeKey)) {
                                enlaces.add(new TrazabilidadEdgeDTO(parentKey, key));
                            }
                            if (!visitedKeys.contains(parentKey) && visitedKeys.size() < TOPE_MAX_NODOS) {
                                visitedKeys.add(parentKey);
                                nextFrontier.add(parentKey);
                            }
                        }
                    }
                }
            }

            currentFrontier = nextFrontier;
        }

        // Eliminar enlaces que apunten a nodos no encontrados en base de datos
        enlaces.removeIf(e -> !allNodes.containsKey(e.getSource()) || !allNodes.containsKey(e.getTarget()));

        // Construir la estructura Cadena categorizada
        return armarCadena(initialTipoStr, id, allNodes, enlaces);
    }

    public TrazabilidadResponseDTO getTrazabilidad(Integer tipo, Long id) {
        return resolver(tipo, id).toTrazabilidadResponseDTO();
    }

    public TrazabilidadResponseDTO getTrazabilidad(String tipo, Long id) {
        return resolver(tipo, id).toTrazabilidadResponseDTO();
    }

    // =========================================================================
    // CONSULTAS POR TIPO DE ESLABÓN (BATCH)
    // =========================================================================

    private Map<Long, RawNodeData> fetchBatchNodes(String tipoStr, Collection<Long> ids) {
        Map<Long, RawNodeData> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return map;
        }

        String tableName;
        String roleName;
        String dateCol = "fecha_declaracion";
        String amountCol = "desembarque";
        String folioCol = "folio_origen";
        boolean hasSeleccionadas = false;
        String extraJoinEspecie = "LEFT JOIN especie e ON d.especie_id = e.id ";
        String extraJoinHumedad = "LEFT JOIN humedad_estado he ON d.humedad_estado_id = he.id ";
        String extraCols = "d.especie_id, e.nombre as esp_nom, d.humedad_estado_id, he.nombre as hum_nom ";

        switch (tipoStr) {
            case "RECOLECTOR":
                tableName = "declaracion_recolector";
                roleName = "Recolector";
                break;
            case "ARMADOR":
                tableName = "declaracion_armador";
                roleName = "Armador";
                break;
            case "AREA":
                tableName = "declaracion_area";
                roleName = "Área de Manejo";
                break;
            case "COMERCIALIZADOR":
                tableName = "declaracion_comercializador";
                roleName = "Comercializador";
                amountCol = "cantidad";
                hasSeleccionadas = true;
                extraJoinEspecie = "";
                extraJoinHumedad = "";
                extraCols = "NULL as esp_id, NULL as esp_nom, NULL as hum_id, NULL as hum_nom ";
                break;
            case "PLANTA_ABASTECIMIENTO":
                tableName = "declaracion_planta_abastecimiento";
                roleName = "Planta Abastecimiento";
                amountCol = "cantidad";
                dateCol = "fecha_ingreso_planta";
                folioCol = "folio_declaracion_a_pla";
                hasSeleccionadas = true;
                break;
            case "PLANTA_PRODUCCION":
                tableName = "declaracion_planta_produccion";
                roleName = "Planta Producción";
                amountCol = "cantidad_producto";
                dateCol = "fecha_produccion";
                folioCol = "folio_declaracion_p_pla";
                hasSeleccionadas = true;
                extraJoinEspecie = "LEFT JOIN especie e ON d.materia_prima_especie_id = e.id ";
                extraCols = "d.materia_prima_especie_id as esp_id, e.nombre as esp_nom, d.humedad_estado_id, he.nombre as hum_nom ";
                break;
            case "PLANTA_DESTINO":
                tableName = "declaracion_planta_destino";
                roleName = "Planta Destino";
                amountCol = "cantidad";
                dateCol = "fecha_declaracion_destino";
                folioCol = "folio_declaracion_destino";
                hasSeleccionadas = true;
                extraJoinEspecie = "";
                extraJoinHumedad = "";
                extraCols = "NULL as esp_id, NULL as esp_nom, NULL as hum_id, NULL as hum_nom ";
                break;
            default:
                return map;
        }

        String selCol = hasSeleccionadas ? "d.declaraciones_seleccionadas" : "NULL as declaraciones_seleccionadas";

        String sql = "SELECT d.id, u.nombres, u.apellidop, u.rut, d." + dateCol + ", d." + amountCol + ", d." + folioCol + ", " +
                "d.declaracion_destinatario_id, d.consumida_por_tipo, " + selCol + ", d.hora, d.usuario_destinatario_id, " +
                extraCols + ", " +
                "udest.rut as rut_dest, TRIM(CONCAT(COALESCE(udest.nombres, ''), ' ', COALESCE(udest.apellidop, ''))) as nom_dest " +
                "FROM " + tableName + " d " +
                "INNER JOIN usuario u ON d.usuario_id = u.id " +
                extraJoinEspecie +
                extraJoinHumedad +
                "LEFT JOIN usuario udest ON d.usuario_destinatario_id = udest.id " +
                "WHERE d.id IN (:ids)";

        try {
            Query query = entityManager.createNativeQuery(sql);
            query.setParameter("ids", ids);
            @SuppressWarnings("unchecked")
            List<Object[]> rows = query.getResultList();

            for (Object[] r : rows) {
                Long nid = ((Number) r[0]).longValue();
                String actor = (r[1] != null ? r[1].toString() : "") + " " + (r[2] != null ? r[2].toString() : "");
                String rut = r[3] != null ? r[3].toString() : "";
                Date d = toDate(r[4]);
                BigDecimal cant = r[5] != null ? new BigDecimal(r[5].toString()) : BigDecimal.ZERO;
                String folio = r[6] != null ? r[6].toString() : "";
                Long destId = r[7] != null ? ((Number) r[7]).longValue() : null;
                String destTipo = r[8] != null ? r[8].toString() : null;
                String decSel = r[9] != null ? r[9].toString() : null;
                String hora = r[10] != null ? r[10].toString() : null;
                Long uDestId = r[11] != null ? ((Number) r[11]).longValue() : null;
                Long espId = r[12] != null ? ((Number) r[12]).longValue() : null;
                String espNom = r[13] != null ? r[13].toString() : null;
                Long humId = r[14] != null ? ((Number) r[14]).longValue() : null;
                String humNom = r[15] != null ? r[15].toString() : null;
                String rutDest = r[16] != null ? r[16].toString() : null;
                String nomDest = r[17] != null ? r[17].toString() : null;

                LocalDateTime ts = ReportService.parseTimestamp(d, hora);

                TrazabilidadNodoDTO dto = TrazabilidadNodoDTO.builder()
                        .idUnico(tipoStr + ":" + nid)
                        .idDeclaracion(nid)
                        .tipoNodo(roleName)
                        .nombreActor(actor.trim())
                        .rutActor(rut)
                        .fecha(d)
                        .cantidad(cant)
                        .descripcionEvento("Declaración de tipo " + roleName)
                        .folio(folio)
                        .build();

                RawNodeData raw = new RawNodeData();
                raw.setId(nid);
                raw.setTipoStr(tipoStr);
                raw.setRoleName(roleName);
                raw.setNombreActor(actor.trim());
                raw.setRutActor(rut);
                raw.setFecha(d);
                raw.setHora(hora);
                raw.setTimestamp(ts);
                raw.setCantidad(cant);
                raw.setFolio(folio);
                raw.setDeclaracionDestinatarioId(destId);
                raw.setConsumidaPorTipo(destTipo);
                raw.setDeclaracionesSeleccionadas(decSel);
                raw.setUsuarioDestinatarioId(uDestId);
                raw.setNombreDestinatario(nomDest != null && !nomDest.isBlank() ? nomDest : null);
                raw.setEspecieId(espId);
                raw.setEspecieNombre(espNom);
                raw.setHumedadEstadoId(humId);
                raw.setHumedadNombre(humNom);
                raw.setDto(dto);

                map.put(nid, raw);
            }
        } catch (Exception e) {
            log.error("Error al consultar lote de nodos para tipo {}: {}", tipoStr, e.getMessage(), e);
        }

        return map;
    }

    // =========================================================================
    // ENSAMBLADO Y ORDENACIÓN DE LA CADENA
    // =========================================================================

    private Cadena armarCadena(String tipoConsulta, Long idConsulta,
                               Map<String, RawNodeData> allNodes,
                               List<TrazabilidadEdgeDTO> enlaces) {

        Cadena cadena = Cadena.builder()
                .tipoConsulta(tipoConsulta)
                .idConsulta(idConsulta)
                .nodos(allNodes.values().stream().map(RawNodeData::getDto).collect(Collectors.toList()))
                .enlaces(new ArrayList<>(enlaces))
                .build();

        // 1. Extraer orígenes
        List<OrigenRef> origenes = new ArrayList<>();
        Map<String, Integer> humedadConteo = new HashMap<>();

        for (RawNodeData n : allNodes.values()) {
            if ("RECOLECTOR".equals(n.getTipoStr()) || "ARMADOR".equals(n.getTipoStr()) || "AREA".equals(n.getTipoStr())) {
                origenes.add(OrigenRef.builder()
                        .tipo(n.getTipoStr())
                        .id(n.getId())
                        .folio(n.getFolio())
                        .fecha(n.getFecha())
                        .hora(n.getHora())
                        .timestamp(n.getTimestamp())
                        .cantidad(n.getCantidad())
                        .rut(n.getRutActor())
                        .actor(n.getNombreActor())
                        .especieId(n.getEspecieId())
                        .especieNombre(n.getEspecieNombre())
                        .humedadEstadoId(n.getHumedadEstadoId())
                        .humedadNombre(n.getHumedadNombre())
                        .usuarioDestinatarioId(n.getUsuarioDestinatarioId())
                        .nombreDestinatario(n.getNombreDestinatario())
                        .declaracionDestinatarioId(n.getDeclaracionDestinatarioId())
                        .consumidaPorTipo(n.getConsumidaPorTipo())
                        .build());

                if (n.getHumedadNombre() != null) {
                    String norm = normalizarEstadoHumedad(n.getHumedadNombre());
                    humedadConteo.put(norm, humedadConteo.getOrDefault(norm, 0) + 1);
                }
            } else if ("PLANTA_ABASTECIMIENTO".equals(n.getTipoStr())) {
                cadena.setPlantaAbastecimientoId(n.getId());
            } else if ("PLANTA_PRODUCCION".equals(n.getTipoStr())) {
                cadena.getPlantaProduccionIds().add(n.getId());
            } else if ("PLANTA_DESTINO".equals(n.getTipoStr())) {
                cadena.getPlantaDestinoIds().add(n.getId());
            }
        }

        // Determinar estado de humedad predominante
        String estadoHumedadPredominante = "HÚMEDO";
        if (!humedadConteo.isEmpty()) {
            estadoHumedadPredominante = humedadConteo.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("HÚMEDO");
        }
        cadena.setEstadoHumedadPredominante(estadoHumedadPredominante);

        // Si la consulta fue sobre un origen específico, colocarlo primero
        origenes.sort((a, b) -> {
            boolean aMatch = a.getTipo().equalsIgnoreCase(tipoConsulta) && a.getId().equals(idConsulta);
            boolean bMatch = b.getTipo().equalsIgnoreCase(tipoConsulta) && b.getId().equals(idConsulta);
            if (aMatch && !bMatch) return -1;
            if (!aMatch && bMatch) return 1;
            if (a.getTimestamp() != null && b.getTimestamp() != null) {
                return a.getTimestamp().compareTo(b.getTimestamp());
            }
            return a.getId().compareTo(b.getId());
        });
        cadena.setOrigenes(origenes);

        // 2. Extraer comercializadores en orden de saltos
        List<RawNodeData> comNodes = allNodes.values().stream()
                .filter(n -> "COMERCIALIZADOR".equals(n.getTipoStr()))
                .collect(Collectors.toList());

        // Orden de saltos mediante distancia desde orígenes en el grafo dirigido
        Map<String, List<String>> adj = new HashMap<>();
        for (TrazabilidadEdgeDTO edge : enlaces) {
            adj.computeIfAbsent(edge.getSource(), k -> new ArrayList<>()).add(edge.getTarget());
        }

        Map<String, Integer> dist = new HashMap<>();
        Queue<String> queue = new LinkedList<>();

        for (OrigenRef o : origenes) {
            String oKey = o.getTipo() + ":" + o.getId();
            dist.put(oKey, 0);
            queue.add(oKey);
        }

        // Si la consulta no tiene orígenes cargados, iniciar con comercializadores de grado de entrada 0
        if (queue.isEmpty()) {
            Set<String> targets = enlaces.stream().map(TrazabilidadEdgeDTO::getTarget).collect(Collectors.toSet());
            for (RawNodeData cn : comNodes) {
                String cKey = "COMERCIALIZADOR:" + cn.getId();
                if (!targets.contains(cKey)) {
                    dist.put(cKey, 1);
                    queue.add(cKey);
                }
            }
        }

        while (!queue.isEmpty()) {
            String curr = queue.poll();
            int d = dist.getOrDefault(curr, 0);
            for (String nbr : adj.getOrDefault(curr, Collections.emptyList())) {
                if (d + 1 > dist.getOrDefault(nbr, -1)) {
                    dist.put(nbr, d + 1);
                    queue.add(nbr);
                }
            }
        }

        comNodes.sort((a, b) -> {
            int dA = dist.getOrDefault("COMERCIALIZADOR:" + a.getId(), 1);
            int dB = dist.getOrDefault("COMERCIALIZADOR:" + b.getId(), 1);
            if (dA != dB) return Integer.compare(dA, dB);
            if (a.getTimestamp() != null && b.getTimestamp() != null) {
                return a.getTimestamp().compareTo(b.getTimestamp());
            }
            return a.getId().compareTo(b.getId());
        });

        List<Long> comIds = comNodes.stream().map(RawNodeData::getId).collect(Collectors.toList());
        cadena.setComercializadorIds(comIds);

        // 3. Construir tramos de comercializadores con tiempos en bodega y semáforos
        List<ComercializadorTramo> tramos = new ArrayList<>();

        if (!comNodes.isEmpty()) {
            for (int i = 0; i < comNodes.size(); i++) {
                RawNodeData cNode = comNodes.get(i);
                int salto = i + 1;
                LocalDateTime entrada;
                LocalDateTime salida = cNode.getTimestamp();

                if (salto == 1) {
                    // La entrada al primer comercializador es la declaración de origen anterior
                    String cKey = "COMERCIALIZADOR:" + cNode.getId();
                    LocalDateTime minOrigenTs = null;
                    for (TrazabilidadEdgeDTO e : enlaces) {
                        if (cKey.equals(e.getTarget())) {
                            RawNodeData parent = allNodes.get(e.getSource());
                            if (parent != null && parent.getTimestamp() != null) {
                                if (minOrigenTs == null || parent.getTimestamp().isBefore(minOrigenTs)) {
                                    minOrigenTs = parent.getTimestamp();
                                }
                            }
                        }
                    }
                    if (minOrigenTs == null && !origenes.isEmpty()) {
                        minOrigenTs = origenes.stream()
                                .map(OrigenRef::getTimestamp)
                                .filter(Objects::nonNull)
                                .min(LocalDateTime::compareTo)
                                .orElse(null);
                    }
                    entrada = minOrigenTs != null ? minOrigenTs : cNode.getTimestamp();
                } else {
                    // La entrada a un comercializador subsecuente es la salida del comercializador previo
                    RawNodeData prev = comNodes.get(i - 1);
                    entrada = prev.getTimestamp();
                }

                double h = horasEnBodega(entrada, salida);
                String sem = calcularSemaforo(h, estadoHumedadPredominante);

                tramos.add(ComercializadorTramo.builder()
                        .id(cNode.getId())
                        .salto(salto)
                        .entrada(entrada)
                        .salida(salida)
                        .horasEnBodega(h)
                        .semaforo(sem)
                        .despachado(true)
                        .usuarioDestinatarioId(cNode.getUsuarioDestinatarioId())
                        .rut(cNode.getRutActor())
                        .actor(cNode.getNombreActor())
                        .folio(cNode.getFolio())
                        .cantidad(cNode.getCantidad())
                        .fechaDeclaracion(cNode.getFecha())
                        .hora(cNode.getHora())
                        .build());
            }

            // Comprobar si el último comercializador despachó a otro que aún no declara (sin despachar)
            RawNodeData lastCom = comNodes.get(comNodes.size() - 1);
            if (lastCom.getDeclaracionDestinatarioId() == null && lastCom.getUsuarioDestinatarioId() != null
                    && !"PLANTA_ABASTECIMIENTO".equalsIgnoreCase(lastCom.getConsumidaPorTipo())) {
                LocalDateTime entradaPendiente = lastCom.getTimestamp();
                double hPendiente = horasEnBodega(entradaPendiente, null);
                String semPendiente = calcularSemaforo(hPendiente, estadoHumedadPredominante);

                tramos.add(ComercializadorTramo.builder()
                        .id(null)
                        .salto(comNodes.size() + 1)
                        .entrada(entradaPendiente)
                        .salida(null)
                        .horasEnBodega(hPendiente)
                        .semaforo(semPendiente)
                        .despachado(false)
                        .usuarioDestinatarioId(lastCom.getUsuarioDestinatarioId())
                        .actor(lastCom.getNombreDestinatario())
                        .build());
            }

        } else if (!origenes.isEmpty()) {
            // Caso: sin despachar (el origen declaró hacia un comercializador, pero éste aún no despacha)
            OrigenRef primOrigen = origenes.get(0);
            boolean vendidoAComercializador = primOrigen.getUsuarioDestinatarioId() != null
                    && !"PLANTA_ABASTECIMIENTO".equalsIgnoreCase(primOrigen.getConsumidaPorTipo())
                    && primOrigen.getDeclaracionDestinatarioId() == null;

            if (vendidoAComercializador) {
                LocalDateTime minEntrada = origenes.stream()
                        .map(OrigenRef::getTimestamp)
                        .filter(Objects::nonNull)
                        .min(LocalDateTime::compareTo)
                        .orElse(primOrigen.getTimestamp());

                double h = horasEnBodega(minEntrada, null);
                String sem = calcularSemaforo(h, estadoHumedadPredominante);

                tramos.add(ComercializadorTramo.builder()
                        .id(null)
                        .salto(1)
                        .entrada(minEntrada)
                        .salida(null)
                        .horasEnBodega(h)
                        .semaforo(sem)
                        .despachado(false)
                        .usuarioDestinatarioId(primOrigen.getUsuarioDestinatarioId())
                        .actor(primOrigen.getNombreDestinatario())
                        .build());
            }
        }

        cadena.setComercializadoresTramos(tramos);
        return cadena;
    }

    // =========================================================================
    // HORAS EN BODEGA Y SEMÁFORO (RES. 3602)
    // =========================================================================

    /**
     * Calcula las horas transcurridas en bodega entre la entrada y la salida.
     * Si la salida no ha ocurrido todavía, corre hasta ahora ({@code LocalDateTime.now()}).
     */
    public static double horasEnBodega(LocalDateTime entrada, LocalDateTime salida) {
        if (entrada == null) {
            return 0.0;
        }
        LocalDateTime fin = salida != null ? salida : LocalDateTime.now();
        long millis = Duration.between(entrada, fin).toMillis();
        double horas = Math.max(0.0, millis / 3600000.0);
        return Math.round(horas * 10.0) / 10.0;
    }

    public static double horasEnBodega(Date fechaEntrada, String horaEntrada, Date fechaSalida, String horaSalida) {
        LocalDateTime ent = ReportService.parseTimestamp(fechaEntrada, horaEntrada);
        LocalDateTime sal = fechaSalida != null ? ReportService.parseTimestamp(fechaSalida, horaSalida) : null;
        return horasEnBodega(ent, sal);
    }

    public static double horasEnBodega(Date fechaEntrada, String horaEntrada) {
        return horasEnBodega(fechaEntrada, horaEntrada, null, null);
    }

    /**
     * Evalúa el semáforo normativo según la Resolución 3602.
     * Retorna "VERDE", "AMARILLO" (preaviso) o "ROJO" (plazo excedido).
     */
    public String calcularSemaforo(double horas, String estadoHumedad) {
        return evaluarSemaforo(horas, estadoHumedad).getSemaforo();
    }

    public SemaforoInfo evaluarSemaforo(double horas, String estadoHumedad) {
        int humedoMaxH = configService != null ? configService.getInt("retencion_humedo_max_horas", 24) : 24;
        int semihumedoMaxH = configService != null ? configService.getInt("retencion_semihumedo_max_horas", 72) : 72;
        int semisecoMaxH = configService != null ? configService.getInt("retencion_semiseco_max_horas", 216) : 216;
        int preavisoPct = configService != null ? configService.getInt("retencion_preaviso_pct", 80) : 80;

        String estadoNorm = normalizarEstadoHumedad(estadoHumedad);
        int plazoMaxHoras;

        switch (estadoNorm) {
            case "HÚMEDO":
                plazoMaxHoras = humedoMaxH;
                break;
            case "SEMI HÚMEDO":
                plazoMaxHoras = semihumedoMaxH;
                break;
            case "SEMI SECO":
                plazoMaxHoras = semisecoMaxH;
                break;
            case "SECO":
            default:
                plazoMaxHoras = Integer.MAX_VALUE;
                break;
        }

        String semaforo;
        if (plazoMaxHoras == Integer.MAX_VALUE) {
            semaforo = "VERDE";
        } else if (horas > plazoMaxHoras) {
            semaforo = "ROJO";
        } else if (horas >= Math.round(plazoMaxHoras * (preavisoPct / 100.0))) {
            semaforo = "AMARILLO";
        } else {
            semaforo = "VERDE";
        }

        return SemaforoInfo.builder()
                .semaforo(semaforo)
                .horas(horas)
                .plazoMaxHoras(plazoMaxHoras)
                .preavisoPct(preavisoPct)
                .estadoHumedad(estadoNorm)
                .build();
    }

    // =========================================================================
    // UTILIDADES DE CONVERSIÓN Y NORMALIZACIÓN
    // =========================================================================

    public String normalizarTipo(Object tipo) {
        if (tipo == null) return "DESCONOCIDO";

        if (tipo instanceof Number) {
            int val = ((Number) tipo).intValue();
            switch (val) {
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

        String s = tipo.toString().trim();
        try {
            int num = Integer.parseInt(s);
            return normalizarTipo(num);
        } catch (NumberFormatException ignored) {}

        String upper = s.toUpperCase().replace('-', '_');
        if (upper.equals("RO") || upper.equals("RECOLECTOR")) return "RECOLECTOR";
        if (upper.equals("DA") || upper.equals("ARMADOR")) return "ARMADOR";
        if (upper.equals("AREA") || upper.equals("AMERB")) return "AREA";
        if (upper.equals("AC") || upper.equals("COMERCIALIZADOR")) return "COMERCIALIZADOR";
        if (upper.equals("DAPLA") || upper.equals("A_PLA") || upper.equals("PLANTA") || upper.equals("PLANTA_ABASTECIMIENTO")) return "PLANTA_ABASTECIMIENTO";
        if (upper.equals("P_PLA") || upper.equals("PLANTA_PRODUCCION")) return "PLANTA_PRODUCCION";
        if (upper.equals("DESTINO") || upper.equals("PLANTA_DESTINO")) return "PLANTA_DESTINO";

        return upper;
    }

    public String normalizarEstadoHumedad(String hum) {
        if (hum == null) return "HÚMEDO";
        String h = hum.toUpperCase().trim();
        if (h.contains("SEMI") && (h.contains("HUM") || h.contains("HÚM"))) return "SEMI HÚMEDO";
        if (h.contains("SEMI") && (h.contains("SEC"))) return "SEMI SECO";
        if (h.contains("HUM") || h.contains("HÚM")) return "HÚMEDO";
        if (h.contains("SEC")) return "SECO";
        return "HÚMEDO";
    }

    public List<String> getPossibleParentTypes(String tipo) {
        if (tipo == null) return Collections.emptyList();
        switch (tipo.toUpperCase()) {
            case "COMERCIALIZADOR":
            case "PLANTA_ABASTECIMIENTO":
                return Arrays.asList("RECOLECTOR", "ARMADOR", "AREA", "COMERCIALIZADOR");
            case "PLANTA_PRODUCCION":
                return Collections.singletonList("PLANTA_ABASTECIMIENTO");
            case "PLANTA_DESTINO":
                return Arrays.asList("PLANTA_PRODUCCION", "PLANTA_ABASTECIMIENTO");
            default:
                return Collections.emptyList();
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
