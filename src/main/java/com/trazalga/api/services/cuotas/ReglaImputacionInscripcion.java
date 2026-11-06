package com.trazalga.api.services.cuotas;

import org.springframework.stereotype.Component;

import com.trazalga.api.dto.ContextoDeclaracion;

/**
 * Imputación de cuota por la comuna del usuario que declara (TC.8; refinamiento 30-10, ajuste del 06-11-2026).
 * <p>
 * La cuota se descuenta en {@code usuario.comuna_id} del declarante, sea recolector, armador o área de
 * manejo. Sólo si el usuario no tiene comuna se usa la comuna de desembarque (brecha K15), para que la
 * declaración no quede fuera de toda cuota.
 * <p>
 * La comuna de desembarque es, por tabla, la misma que usan los servicios de declaración al validar:
 * <ul>
 *   <li>recolector y armador: {@code comuna_id} de la declaración;</li>
 *   <li>área de manejo: la comuna del AMERB o, si falta, la de la caleta ({@code declaracion_area} no
 *       tiene {@code comuna_id}).</li>
 * </ul>
 */
@Component("reglaImputacionInscripcion")
public class ReglaImputacionInscripcion implements ReglaImputacionTerritorial {

    public static final String CLAVE = "INSCRIPCION";

    public static final String TABLA_RECOLECTOR = "declaracion_recolector";
    public static final String TABLA_ARMADOR = "declaracion_armador";
    public static final String TABLA_AREA = "declaracion_area";

    @Override
    public String clave() {
        return CLAVE;
    }

    @Override
    public Long comunaImputacion(ContextoDeclaracion ctx) {
        if (ctx == null) {
            return null;
        }
        if (ctx.getComunaInscripcionId() != null) {
            return ctx.getComunaInscripcionId();
        }
        // Respaldo K15: usuario sin comuna
        return ctx.getComunaDesembarqueId();
    }

    @Override
    public String sqlComuna(String tabla, String aliasDeclaracion, String aliasUsuario) {
        return sql(tabla, aliasDeclaracion, aliasUsuario);
    }

    /** Expresión SQL de la comuna de imputación: la del usuario y, si falta, la de desembarque. */
    public static String sql(String tabla, String aliasDeclaracion, String aliasUsuario) {
        String u = (aliasUsuario != null && !aliasUsuario.isBlank()) ? aliasUsuario : "u";
        return "COALESCE(" + u + ".comuna_id, " + sqlComunaDesembarque(tabla, aliasDeclaracion) + ")";
    }

    /** Expresión SQL de la comuna de desembarque de una tabla de declaraciones de origen. */
    public static String sqlComunaDesembarque(String tabla, String aliasDeclaracion) {
        String d = (aliasDeclaracion != null && !aliasDeclaracion.isBlank()) ? aliasDeclaracion : "d";
        if (TABLA_AREA.equalsIgnoreCase(tabla)) {
            return "COALESCE((SELECT am_imp.comuna_id FROM amerb am_imp WHERE am_imp.id = " + d + ".amerb_id), "
                    + "(SELECT ca_imp.comuna_id FROM caleta ca_imp WHERE ca_imp.id = " + d + ".caleta_id))";
        }
        return d + ".comuna_id";
    }
}
