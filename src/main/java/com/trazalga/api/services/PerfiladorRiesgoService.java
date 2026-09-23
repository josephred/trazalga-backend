package com.trazalga.api.services;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PerfiladorRiesgoService {

    @Autowired
    private ConfiguracionGeneralService configService;

    @Autowired
    private ReportService reportService;

    public static final String NIVEL_VERDE = "VERDE";
    public static final String NIVEL_AMARILLO = "AMARILLO";
    public static final String NIVEL_ROJO = "ROJO";

    public Map<String, Object> evaluarRiesgo(Date startDate, Date endDate, Long regionId, Long actorId, String nivelFiltro) {
        boolean activo = configService.getBoolean("riesgo_activo", true);
        double varAmarillo = configService.getDouble("riesgo_variacion_amarillo_pct", 5.0);
        double varRojo = configService.getDouble("riesgo_variacion_rojo_pct", 10.0);
        int diasAmarillo = configService.getInt("riesgo_dias_amarillo", 3);
        int diasRojo = configService.getInt("riesgo_dias_rojo", 7);
        String escalaHumedoSinMerma = configService.getValor("riesgo_escala_humedo_sin_merma", "ROJO");
        int agravanteVeda = configService.getInt("riesgo_agravante_veda_niveles", 1);
        int agravanteLed = configService.getInt("riesgo_agravante_led_niveles", 1);
        String estadosSujetos = configService.getValor("retencion_bodega_estados_sujetos", "HUMEDO");

        Map<String, Object> response = new HashMap<>();
        response.put("activo", activo);
        response.put("controlDesactivado", !activo);

        if (!activo) {
            response.put("mensaje", "Perfilador de riesgo de fiscalización desactivado en Administración");
            response.put("resumen", buildEmptyResumen());
            response.put("matriz", buildEmptyMatriz());
            response.put("actores", Collections.emptyList());
            response.put("lotes", Collections.emptyList());
            return response;
        }

        // Obtener todos los lotes de trazabilidad calculados con sus métricas biológicas y marcas
        List<Map<String, Object>> lotesBrutos = reportService.getTrazabilidadLoteDetalle(startDate, endDate, null);

        List<Map<String, Object>> lotesEvaluados = new ArrayList<>();
        Map<String, Map<String, Object>> matrizCeldas = initMatrizCeldas();
        Map<String, ActorAggregator> mapaActores = new HashMap<>();

        long totalVerde = 0;
        long totalAmarillo = 0;
        long totalRojo = 0;
        double kgVerde = 0.0;
        double kgAmarillo = 0.0;
        double kgRojo = 0.0;

        for (Map<String, Object> lote : lotesBrutos) {
            String folio = (String) lote.get("folioOrigen");
            String humedad = (String) lote.get("humedadOrigen");
            double deltaPct = lote.get("deltaPct") != null ? ((Number) lote.get("deltaPct")).doubleValue() : 0.0;
            double absDelta = Math.abs(deltaPct);
            int diasEnBodega = lote.get("diasEnBodega") != null ? ((Number) lote.get("diasEnBodega")).intValue() : 0;
            double kgLote = lote.get("kgDestino") != null ? ((Number) lote.get("kgDestino")).doubleValue() :
                    (lote.get("kgOrigen") != null ? ((Number) lote.get("kgOrigen")).doubleValue() : 0.0);
            String severidadBiologica = (String) lote.get("severidadBiologica");
            String inconsistenciaBiologica = (String) lote.get("inconsistenciaBiologica");

            @SuppressWarnings("unchecked")
            List<String> marcasActivas = (List<String>) lote.getOrDefault("marcasActivas", Collections.emptyList());

            // 1. Nivel de Variación de Peso
            String nivelVariacion;
            if (absDelta >= varRojo) {
                nivelVariacion = NIVEL_ROJO;
            } else if (absDelta >= varAmarillo) {
                nivelVariacion = NIVEL_AMARILLO;
            } else {
                nivelVariacion = NIVEL_VERDE;
            }

            // 2. Nivel de Retención en Bodega Virtual (sólo para estados sujetos a control)
            boolean esEstadoSujeto = estadosSujetos != null && humedad != null && estadosSujetos.toUpperCase().contains(humedad.toUpperCase());
            String nivelRetencion;
            if (esEstadoSujeto) {
                if (diasEnBodega >= diasRojo) {
                    nivelRetencion = NIVEL_ROJO;
                } else if (diasEnBodega >= diasAmarillo) {
                    nivelRetencion = NIVEL_AMARILLO;
                } else {
                    nivelRetencion = NIVEL_VERDE;
                }
            } else {
                nivelRetencion = NIVEL_VERDE;
            }

            // 3. Nivel Base = mayor de los dos
            int nivelBaseInt = Math.max(levelToInt(nivelVariacion), levelToInt(nivelRetencion));
            int nivelFinalInt = nivelBaseInt;
            List<String> motivos = new ArrayList<>();

            if (nivelVariacion.equals(NIVEL_ROJO)) {
                motivos.add(String.format("Variación severa de peso (%.1f%% sobre umbral rojo %.1f%%)", deltaPct, varRojo));
            } else if (nivelVariacion.equals(NIVEL_AMARILLO)) {
                motivos.add(String.format("Variación moderada de peso (%.1f%% sobre umbral amarillo %.1f%%)", deltaPct, varAmarillo));
            }

            if (nivelRetencion.equals(NIVEL_ROJO)) {
                motivos.add(String.format("Retención crítica en bodega virtual (%d días sobre umbral %dd)", diasEnBodega, diasRojo));
            } else if (nivelRetencion.equals(NIVEL_AMARILLO)) {
                motivos.add(String.format("Retención preventiva en bodega virtual (%d días sobre umbral %dd)", diasEnBodega, diasAmarillo));
            }

            // 4. Modificador Biológico (Escalamiento Crítico R8.1)
            if ("CRITICA".equalsIgnoreCase(severidadBiologica)) {
                int nivelEscalaInt = levelToInt(escalaHumedoSinMerma);
                if (nivelEscalaInt > nivelFinalInt) {
                    nivelFinalInt = nivelEscalaInt;
                }
                motivos.add("Inconsistencia biológica grave: alga húmeda en tránsito prolongado sin merma o con aumento de peso (escala a " + escalaHumedoSinMerma + ")");
            } else if ("ATENCION".equalsIgnoreCase(severidadBiologica)) {
                motivos.add("Merma física atípica (" + (lote.get("motivoMerma") != null ? lote.get("motivoMerma") : inconsistenciaBiologica) + ")");
            }

            // 5. Agravantes Sancionatorios Activos (EN_VEDA, LED_EXCEDIDO)
            List<String> agravantesAplicados = new ArrayList<>();
            if (marcasActivas != null) {
                if (marcasActivas.contains("EN_VEDA")) {
                    nivelFinalInt = Math.min(2, nivelFinalInt + agravanteVeda);
                    agravantesAplicados.add("EN_VEDA");
                    motivos.add(String.format("Agravante: extracción registrada en período o zona EN_VEDA (+%d nivel)", agravanteVeda));
                }
                if (marcasActivas.contains("LED_EXCEDIDO")) {
                    nivelFinalInt = Math.min(2, nivelFinalInt + agravanteLed);
                    agravantesAplicados.add("LED_EXCEDIDO");
                    motivos.add(String.format("Agravante: sobrepaso de límite de extracción diario LED_EXCEDIDO (+%d nivel)", agravanteLed));
                }
            }

            String nivelRiesgoFinal = intToLevel(nivelFinalInt);

            if (motivos.isEmpty()) {
                motivos.add("Lote consistente: variación dentro de tolerancia, permanencia regular y sin marcas de fiscalización");
            }

            // Registro en Matriz 3x3
            String celdaKey = nivelVariacion + "_" + nivelRetencion;
            if (matrizCeldas.containsKey(celdaKey)) {
                Map<String, Object> celda = matrizCeldas.get(celdaKey);
                celda.put("conteo", ((Number) celda.get("conteo")).longValue() + 1);
                celda.put("kg", Math.round((((Number) celda.get("kg")).doubleValue() + kgLote) * 100.0) / 100.0);
            }

            // Contadores globales
            if (NIVEL_ROJO.equals(nivelRiesgoFinal)) {
                totalRojo++;
                kgRojo += kgLote;
            } else if (NIVEL_AMARILLO.equals(nivelRiesgoFinal)) {
                totalAmarillo++;
                kgAmarillo += kgLote;
            } else {
                totalVerde++;
                kgVerde += kgLote;
            }

            // Jerarquización de Actores
            registrarActor(mapaActores, (String) lote.get("actorComercializador"), null, "COMERCIALIZADOR", nivelRiesgoFinal, kgLote);
            registrarActor(mapaActores, (String) lote.get("actorPlanta"), null, "PLANTA", nivelRiesgoFinal, kgLote);
            registrarActor(mapaActores, (String) lote.get("actorOrigen"), (String) lote.get("rutOrigen"), (String) lote.get("eslabonOrigen"), nivelRiesgoFinal, kgLote);

            // Filtrado por nivel si fue solicitado
            if (nivelFiltro != null && !nivelFiltro.isEmpty() && !nivelFiltro.equalsIgnoreCase("TODOS")) {
                if (!nivelRiesgoFinal.equalsIgnoreCase(nivelFiltro)) {
                    continue;
                }
            }

            Map<String, Object> loteEval = new HashMap<>(lote);
            loteEval.put("nivelVariacion", nivelVariacion);
            loteEval.put("nivelRetencion", nivelRetencion);
            loteEval.put("nivelBase", intToLevel(nivelBaseInt));
            loteEval.put("nivelRiesgo", nivelRiesgoFinal);
            loteEval.put("marcasAgravantes", agravantesAplicados);
            loteEval.put("motivoRiesgo", String.join(" · ", motivos));
            loteEval.put("scoreRiesgo", nivelFinalInt);

            lotesEvaluados.add(loteEval);
        }

        // Ordenar lotes por nivel descendente (ROJO > AMARILLO > VERDE) y luego por fecha
        lotesEvaluados.sort((a, b) -> {
            int cmp = Integer.compare(levelToInt((String) b.get("nivelRiesgo")), levelToInt((String) a.get("nivelRiesgo")));
            if (cmp != 0) return cmp;
            Date fA = (Date) a.get("fechaOrigen");
            Date fB = (Date) b.get("fechaOrigen");
            if (fA != null && fB != null) return fB.compareTo(fA);
            return 0;
        });

        // Jerarquizar actores: ordenados por peorNivel descendente, luego por lotesRojo, luego por kg
        List<Map<String, Object>> listaActores = mapaActores.values().stream()
                .map(ActorAggregator::toMap)
                .sorted((a, b) -> {
                    int cmpNivel = Integer.compare(levelToInt((String) b.get("peorNivel")), levelToInt((String) a.get("peorNivel")));
                    if (cmpNivel != 0) return cmpNivel;
                    int cmpRojo = Long.compare((Long) b.get("lotesRojo"), (Long) a.get("lotesRojo"));
                    if (cmpRojo != 0) return cmpRojo;
                    return Double.compare((Double) b.get("totalKg"), (Double) a.get("totalKg"));
                })
                .collect(Collectors.toList());

        long totalLotes = totalVerde + totalAmarillo + totalRojo;
        double totalKg = Math.round((kgVerde + kgAmarillo + kgRojo) * 100.0) / 100.0;

        Map<String, Object> resumen = new HashMap<>();
        resumen.put("totalLotes", totalLotes);
        resumen.put("totalKg", totalKg);
        resumen.put("verde", buildNivelResumen(totalVerde, kgVerde, totalLotes));
        resumen.put("amarillo", buildNivelResumen(totalAmarillo, kgAmarillo, totalLotes));
        resumen.put("rojo", buildNivelResumen(totalRojo, kgRojo, totalLotes));

        response.put("resumen", resumen);
        response.put("matriz", buildMatrizResponse(matrizCeldas));
        response.put("actores", listaActores);
        response.put("lotes", lotesEvaluados);

        Map<String, Object> configVigente = new HashMap<>();
        configVigente.put("riesgo_variacion_amarillo_pct", varAmarillo);
        configVigente.put("riesgo_variacion_rojo_pct", varRojo);
        configVigente.put("riesgo_dias_amarillo", diasAmarillo);
        configVigente.put("riesgo_dias_rojo", diasRojo);
        configVigente.put("riesgo_escala_humedo_sin_merma", escalaHumedoSinMerma);
        configVigente.put("riesgo_agravante_veda_niveles", agravanteVeda);
        configVigente.put("riesgo_agravante_led_niveles", agravanteLed);
        response.put("configuracionVigente", configVigente);

        return response;
    }

    private void registrarActor(Map<String, ActorAggregator> map, String nombre, String rut, String tipo, String nivel, double kg) {
        if (nombre == null || nombre.trim().isEmpty() || "null".equalsIgnoreCase(nombre.trim())) return;
        String key = nombre.trim().toUpperCase();
        map.computeIfAbsent(key, k -> new ActorAggregator(nombre.trim(), rut, tipo)).addLote(nivel, kg);
    }

    public static int levelToInt(String nivel) {
        if (NIVEL_ROJO.equalsIgnoreCase(nivel) || "CRITICA".equalsIgnoreCase(nivel)) return 2;
        if (NIVEL_AMARILLO.equalsIgnoreCase(nivel) || "ATENCION".equalsIgnoreCase(nivel)) return 1;
        return 0; // VERDE | NEUTRA
    }

    public static String intToLevel(int val) {
        if (val >= 2) return NIVEL_ROJO;
        if (val == 1) return NIVEL_AMARILLO;
        return NIVEL_VERDE;
    }

    private Map<String, Object> buildNivelResumen(long count, double kg, long totalLotes) {
        Map<String, Object> m = new HashMap<>();
        m.put("conteo", count);
        m.put("kg", Math.round(kg * 100.0) / 100.0);
        m.put("porcentaje", totalLotes > 0 ? Math.round(((double) count / totalLotes) * 1000.0) / 10.0 : 0.0);
        return m;
    }

    private Map<String, Map<String, Object>> initMatrizCeldas() {
        String[] niveles = {NIVEL_VERDE, NIVEL_AMARILLO, NIVEL_ROJO};
        Map<String, Map<String, Object>> map = new HashMap<>();
        for (String v : niveles) {
            for (String r : niveles) {
                Map<String, Object> c = new HashMap<>();
                c.put("variacion", v);
                c.put("retencion", r);
                c.put("nivelDominante", intToLevel(Math.max(levelToInt(v), levelToInt(r))));
                c.put("conteo", 0L);
                c.put("kg", 0.0);
                map.put(v + "_" + r, c);
            }
        }
        return map;
    }

    private List<Map<String, Object>> buildMatrizResponse(Map<String, Map<String, Object>> celdas) {
        return new ArrayList<>(celdas.values());
    }

    private Map<String, Object> buildEmptyResumen() {
        Map<String, Object> m = new HashMap<>();
        m.put("totalLotes", 0L);
        m.put("totalKg", 0.0);
        m.put("verde", buildNivelResumen(0, 0, 0));
        m.put("amarillo", buildNivelResumen(0, 0, 0));
        m.put("rojo", buildNivelResumen(0, 0, 0));
        return m;
    }

    private List<Map<String, Object>> buildEmptyMatriz() {
        return buildMatrizResponse(initMatrizCeldas());
    }

    private static class ActorAggregator {
        private final String nombre;
        private final String rut;
        private final String tipo;
        private long lotesVerde = 0;
        private long lotesAmarillo = 0;
        private long lotesRojo = 0;
        private double totalKg = 0.0;

        public ActorAggregator(String nombre, String rut, String tipo) {
            this.nombre = nombre;
            this.rut = rut;
            this.tipo = tipo;
        }

        public void addLote(String nivel, double kg) {
            this.totalKg += kg;
            if (NIVEL_ROJO.equals(nivel)) {
                this.lotesRojo++;
            } else if (NIVEL_AMARILLO.equals(nivel)) {
                this.lotesAmarillo++;
            } else {
                this.lotesVerde++;
            }
        }

        public String getPeorNivel() {
            if (lotesRojo > 0) return NIVEL_ROJO;
            if (lotesAmarillo > 0) return NIVEL_AMARILLO;
            return NIVEL_VERDE;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new HashMap<>();
            m.put("actor", nombre);
            m.put("rut", rut);
            m.put("tipoActor", tipo);
            m.put("peorNivel", getPeorNivel());
            m.put("lotesVerde", lotesVerde);
            m.put("lotesAmarillo", lotesAmarillo);
            m.put("lotesRojo", lotesRojo);
            m.put("totalLotes", lotesVerde + lotesAmarillo + lotesRojo);
            m.put("totalKg", Math.round(totalKg * 100.0) / 100.0);
            return m;
        }
    }
}
