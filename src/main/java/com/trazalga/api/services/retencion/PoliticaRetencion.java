package com.trazalga.api.services.retencion;

import java.time.LocalDateTime;
import java.util.Date;

import org.springframework.stereotype.Component;

import com.trazalga.api.services.indicadores.CoherenciaHumedad;
import com.trazalga.api.services.parametros.ParametrosRetencion;

/**
 * Motor canónico de evaluación de políticas de retención en bodega virtual (TR.1, TR.3, TH.1).
 * Unifica la normalización de humedad y el semáforo de la Resolución Exenta 3602/2017
 * con las reglas de bloqueo automático de carga en bodega (D3), delegando la evaluación
 * del tramo esperado por edad desde la extracción en {@link CoherenciaHumedad}.
 */
@Component
public class PoliticaRetencion {

    /**
     * Normaliza cualquier variante textual de humedad a los cuatro estados canónicos
     * para el semáforo de retención: HÚMEDO, SEMI HÚMEDO, SEMI SECO, SECO.
     * Para la evaluación de coherencia sin fallback se usa {@link CoherenciaHumedad#normalizarEstadoDeclarado(String)}.
     */
    public static String normalizarEstadoHumedad(String hum) {
        String sinFallback = CoherenciaHumedad.normalizarEstadoDeclarado(hum);
        return sinFallback != null ? sinFallback : "HÚMEDO";
    }

    /**
     * Mapea un estado de humedad normalizado a la clave estándar de matriz 4x3.
     */
    public static String claveMatrizHumedad(String estadoNormalizado) {
        if (estadoNormalizado == null) return "HUMEDO";
        String n = estadoNormalizado.toUpperCase().trim();
        if (n.contains("SEMI") && (n.contains("HUM") || n.contains("HÚM"))) return "SEMI_HUMEDO";
        if (n.contains("SEMI") && n.contains("SEC")) return "SEMI_SECO";
        if (n.contains("SEC")) return "SECO";
        return "HUMEDO";
    }

    /**
     * Retorna el nombre canónico del parámetro de bloqueo según la clave de humedad (TR.3).
     */
    public static String parametroBloqueoNombre(String claveMatriz) {
        if ("SEMI_HUMEDO".equalsIgnoreCase(claveMatriz)) return com.trazalga.api.services.parametros.ParametrosService.PARAM_RETENCION_BLOQUEO_SEMIHUMEDO;
        if ("SEMI_SECO".equalsIgnoreCase(claveMatriz)) return com.trazalga.api.services.parametros.ParametrosService.PARAM_RETENCION_BLOQUEO_SEMISECO;
        return com.trazalga.api.services.parametros.ParametrosService.PARAM_RETENCION_BLOQUEO_HUMEDO;
    }

    public ResultadoEvaluacionRetencion evaluar(
            Long especieId,
            String especieNombre,
            String humedadRaw,
            double horas,
            ParametrosRetencion params
    ) {
        return evaluar(especieId, especieNombre, humedadRaw, horas, null, null, params, null);
    }

    /**
     * Evalúa integralmente una carga contra la normativa Res. 3602 y la política de bloqueo,
     * delegando el cálculo del tramo esperado por edad y su coherencia en {@link CoherenciaHumedad} (TH.1).
     */
    public ResultadoEvaluacionRetencion evaluar(
            Long especieId,
            String especieNombre,
            String humedadRaw,
            double horas,
            Date fechaExtraccion,
            LocalDateTime momentoEvento,
            ParametrosRetencion params,
            CoherenciaHumedad.ParametrosCoherencia paramsCoherencia
    ) {
        java.util.Objects.requireNonNull(params, "ParametrosRetencion no puede ser nulo para evaluar retención");

        String estadoDeclarado = normalizarEstadoHumedad(humedadRaw);
        String claveMatriz = claveMatrizHumedad(estadoDeclarado);

        int plazoMaxHoras;
        switch (claveMatriz) {
            case "HUMEDO":
                plazoMaxHoras = params.humedoMaxHoras();
                break;
            case "SEMI_HUMEDO":
                plazoMaxHoras = params.semihumedoMaxHoras();
                break;
            case "SEMI_SECO":
                plazoMaxHoras = params.semisecoMaxHoras();
                break;
            case "SECO":
            default:
                plazoMaxHoras = Integer.MAX_VALUE;
                break;
        }

        double semaforoPreavisoPct = params.semaforoPreavisoPct();
        int preavisoHoras = (plazoMaxHoras == Integer.MAX_VALUE)
                ? Integer.MAX_VALUE
                : (int) Math.round(plazoMaxHoras * (semaforoPreavisoPct / 100.0));

        String semaforo;
        boolean vencido = false;
        boolean enPreavisoSemaforo = false;

        if (plazoMaxHoras == Integer.MAX_VALUE) {
            semaforo = "VERDE";
        } else if (horas > plazoMaxHoras) {
            semaforo = "ROJO";
            vencido = true;
        } else if (horas >= preavisoHoras) {
            semaforo = "AMARILLO";
            enPreavisoSemaforo = true;
        } else {
            semaforo = "VERDE";
        }

        // TH.1: Delegación canónica del tramo esperado por edad y coherencia a CoherenciaHumedad
        CoherenciaHumedad.ParametrosCoherencia cohParams = paramsCoherencia != null
                ? paramsCoherencia
                : CoherenciaHumedad.ParametrosCoherencia.builder()
                        .edadMaxHumedoHoras(params.humedoMaxHoras())
                        .edadMaxSemihumedoHoras(params.semihumedoMaxHoras())
                        .edadMaxSemisecoHoras(params.semisecoMaxHoras())
                        .modo(CoherenciaHumedad.MODO_DIRECCIONAL)
                        .build();

        String tramoReal;
        boolean inconsistenciaHumedad;
        if (fechaExtraccion != null && momentoEvento != null) {
            CoherenciaHumedad.ResultadoCoherencia resCoh =
                    CoherenciaHumedad.evaluar(humedadRaw, fechaExtraccion, momentoEvento, cohParams);
            tramoReal = resCoh.getTramoMinimo() != null
                    ? resCoh.getTramoMinimo()
                    : CoherenciaHumedad.tramoPorEdad(horas, cohParams);
            inconsistenciaHumedad = resCoh.isIncoherente();
        } else {
            tramoReal = CoherenciaHumedad.tramoPorEdad(horas, cohParams);
            int seqDec = CoherenciaHumedad.ordenSequedad(CoherenciaHumedad.normalizarEstadoDeclarado(humedadRaw));
            int seqTramo = CoherenciaHumedad.ordenSequedad(tramoReal);
            if (seqDec < 0) {
                inconsistenciaHumedad = false;
            } else if (CoherenciaHumedad.MODO_ESTRICTO.equalsIgnoreCase(cohParams.getModo())) {
                inconsistenciaHumedad = (seqDec != seqTramo);
            } else {
                inconsistenciaHumedad = (seqDec < seqTramo);
            }
        }

        // Evaluación de Bloqueo Automático de Carga (D3)
        boolean bloqueoActivo = params.bloqueoActivo();
        boolean aplicaEspecie = false;
        int umbralBloqueo = 0;
        boolean bloqueoRequerido = false;
        boolean enPreavisoBloqueo = false;

        if (bloqueoActivo) {
            aplicaEspecie = evaluarAplicaEspecie(especieId, especieNombre, params.bloqueoEspecies());
            if (aplicaEspecie) {
                switch (claveMatriz) {
                    case "HUMEDO":
                        umbralBloqueo = params.bloqueoHumedoHoras();
                        break;
                    case "SEMI_HUMEDO":
                        umbralBloqueo = params.bloqueoSemihumedoHoras();
                        break;
                    case "SEMI_SECO":
                        umbralBloqueo = params.bloqueoSemisecoHoras();
                        break;
                    default:
                        umbralBloqueo = 0;
                        break;
                }

                if (umbralBloqueo > 0) {
                    double preavisoBloqueoPct = params.bloqueoPreavisoPct();
                    double umbralPreavisoBloqueo = umbralBloqueo * (preavisoBloqueoPct / 100.0);

                    if (horas >= umbralBloqueo) {
                        bloqueoRequerido = true;
                    } else if (horas >= umbralPreavisoBloqueo) {
                        enPreavisoBloqueo = true;
                    }
                }
            }
        }

        String motivoDetalle = generarMotivo(
                bloqueoRequerido, vencido, enPreavisoBloqueo, enPreavisoSemaforo,
                horas, plazoMaxHoras, umbralBloqueo, estadoDeclarado);

        return ResultadoEvaluacionRetencion.builder()
                .estadoDeclarado(estadoDeclarado)
                .claveMatriz(claveMatriz)
                .tramoReal(tramoReal)
                .plazoMaxHoras(plazoMaxHoras)
                .preavisoSemaforoHoras(preavisoHoras)
                .semaforo(semaforo)
                .vencido(vencido)
                .enPreavisoSemaforo(enPreavisoSemaforo)
                .bloqueoAplicaEspecie(aplicaEspecie)
                .umbralBloqueoHoras(umbralBloqueo)
                .bloqueoRequerido(bloqueoRequerido)
                .enPreavisoBloqueo(enPreavisoBloqueo)
                .inconsistenciaHumedad(inconsistenciaHumedad)
                .motivoDetalle(motivoDetalle)
                .parametroBloqueoNombre(parametroBloqueoNombre(claveMatriz))
                .build();
    }

    private boolean evaluarAplicaEspecie(Long especieId, String especieNombre, String filtroEspecies) {
        if (filtroEspecies == null || filtroEspecies.isBlank()
                || filtroEspecies.equalsIgnoreCase("TODAS")
                || filtroEspecies.equalsIgnoreCase("ALL")) {
            return true;
        }

        String[] tokens = filtroEspecies.split(",");
        for (String t : tokens) {
            String token = t.trim();
            if (token.isEmpty()) continue;
            if (especieId != null && token.equalsIgnoreCase(String.valueOf(especieId))) {
                return true;
            }
            if (especieNombre != null && token.equalsIgnoreCase(especieNombre)) {
                return true;
            }
        }
        return false;
    }

    private String generarMotivo(
            boolean bloqueoRequerido, boolean vencido, boolean enPreavisoBloqueo, boolean enPreavisoSemaforo,
            double horas, int plazoMax, int umbralBloqueo, String humedad) {
        long h = Math.round(horas);
        if (bloqueoRequerido) {
            return String.format("Retención excedida: %d h en bodega superan el límite de bloqueo de %d h para recurso %s",
                    h, umbralBloqueo, humedad);
        }
        if (vencido) {
            return String.format("Plazo normativo Res. 3602 excedido: %d h transcurridas vs máx %d h (%s)",
                    h, plazoMax, humedad);
        }
        if (enPreavisoBloqueo) {
            return String.format("Preaviso de bloqueo: %d h transcurridas (bloqueo automático a las %d h)",
                    h, umbralBloqueo);
        }
        if (enPreavisoSemaforo) {
            return String.format("Preaviso de semáforo Res. 3602: %d h transcurridas de %d h permitidas",
                    h, plazoMax);
        }
        return "Dentro de plazo normativo";
    }
}
