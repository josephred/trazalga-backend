package com.trazalga.api.services.cuotas;

import com.trazalga.api.models.CuotaExtraccionModel;

/**
 * Estrategia de Alcance de Cuota (TM.1).
 * Define las reglas de validación, normalización de territorio, solapamiento
 * y jerarquía para cada nivel de agregación de una cuota de extracción.
 */
public interface AlcanceCuota {

    /**
     * Clave única del nivel de agregación / alcance:
     * COMUNA, PROVINCIA, REGION, INDIVIDUAL_PLANTILLA, INDIVIDUAL_PERSONA, AREA, MACROZONA, NACIONAL.
     */
    String clave();

    /**
     * Valida los datos requeridos por el nivel y normaliza los punteros territoriales
     * (por ejemplo, fija la región a partir de la provincia o comunas, limpia campos sobrantes).
     *
     * @param c Cuota a validar y normalizar.
     * @throws IllegalArgumentException si falta un dato mandatorio o la jerarquía es inconsistente.
     */
    void validarYNormalizar(CuotaExtraccionModel c);

    /**
     * Evalúa si dos cuotas del mismo ámbito y período solapan en su alcance territorial o personal.
     *
     * @param a Primera cuota.
     * @param b Segunda cuota.
     * @return true si comparten el mismo ámbito territorial / usuario según este alcance.
     */
    boolean mismoAlcance(CuotaExtraccionModel a, CuotaExtraccionModel b);

    /**
     * Retorna una descripción amigable del alcance de la cuota:
     * «Comunas Coquimbo + La Serena», «Provincia Huasco», «Región Antofagasta»,
     * «Por persona (tope general) - Región Antofagasta», «Persona Juan Pérez (12.345.678-9)».
     *
     * @param c Cuota a describir.
     * @return Cadena descriptiva.
     */
    String describir(CuotaExtraccionModel c);

    /**
     * Retorna una descripción amigable del conflicto territorial o personal entre dos cuotas.
     */
    default String describirConflicto(CuotaExtraccionModel c, CuotaExtraccionModel otra) {
        return describir(otra);
    }

    /**
     * Indica si las cuotas bajo este alcance se comparan en la jerarquía territorial con los totales.
     * Las cuotas individuales (por persona o plantillas) no se comparan con totales comunales/regionales.
     *
     * @return true si participa en validarJerarquia, false si no se compara.
     */
    boolean comparableEnJerarquia();
}
