package com.trazalga.api.services.cuotas;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.trazalga.api.dto.ConsumoPersonaDTO;
import com.trazalga.api.dto.ResumenPlantillaDTO;
import com.trazalga.api.models.ComunaModel;
import com.trazalga.api.models.CuotaExtraccionModel;
import com.trazalga.api.repositories.ICuotaExtraccionRepository;
import com.trazalga.api.services.ConfiguracionGeneralService;
import com.trazalga.api.services.CuotaExtraccionService;
import com.trazalga.api.services.parametros.ParametrosService;

import lombok.extern.slf4j.Slf4j;

/**
 * Consulta y agregación de consumo de cuotas individuales y plantillas por persona (TM.2 / K8).
 * Une declaraciones de recolectores y armadores según imputación TC.8,
 * identifica a las personas pertenecientes al territorio de la plantilla (Sujeto)
 * y totaliza sus extracciones del período (Objeto).
 */
@Component
@Slf4j
public class ConsumoIndividualQuery {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    @Lazy
    private CuotaExtraccionService cuotaExtraccionService;

    @Autowired
    @Lazy
    private ICuotaExtraccionRepository cuotaRepository;

    @Autowired
    private ResolutorImputacionTerritorial resolutorImputacion;

    @Autowired
    private ParametrosService parametrosService;

    @Autowired
    @Lazy
    private ConfiguracionGeneralService configService;

    /**
     * Calcula el detalle de consumo por persona para una cuota plantilla o individual.
     */
    public List<ConsumoPersonaDTO> porPersona(CuotaExtraccionModel cuota, Date fechaEval) {
        if (cuota == null) {
            return Collections.emptyList();
        }
        if (fechaEval == null) {
            fechaEval = new Date();
        }

        BigDecimal limiteEfectivo = cuotaExtraccionService.calcularLimiteEfectivo(cuota, fechaEval);
        java.sql.Date[] rango = cuotaExtraccionService.calcularRangoFechas(cuota, fechaEval);
        java.sql.Date fechaInicio = rango[0];
        java.sql.Date fechaFin = rango[1];

        String modo = (configService != null) ? configService.getValor("cuota_fecha_imputacion", "EXTRACCION") : "EXTRACCION";
        String colFecha = "DECLARACION".equalsIgnoreCase(modo) ? "fecha_declaracion" : "fecha_extraccion";
        boolean esCaptura = !"DESEMBARQUE".equalsIgnoreCase(cuota.getMetrica());
        String colVolumen = esCaptura ? "captura_kg" : "desembarque_kg";

        Long especieId = cuota.getEspecie() != null ? cuota.getEspecie().getId() : null;
        Long extraccionTipoId = cuota.getExtraccionTipo() != null ? cuota.getExtraccionTipo().getId() : null;

        // D6: Detectar usuarios con cuota individual específica propia para excluirlos de la plantilla
        Set<Long> usuariosConCuotaPropia = Collections.emptySet();
        if (Boolean.TRUE.equals(cuota.getEsPlantilla())) {
            usuariosConCuotaPropia = obtenerUsuariosConCuotaPropia(cuota);
        }

        // Expresión de imputación para recolector y armador (TC.8)
        ReglaImputacionTerritorial reglaRecolector = resolutorImputacion.resolver("RECOLECTOR");
        ReglaImputacionTerritorial reglaArmador = resolutorImputacion.resolver("ARMADOR");
        String sqlComunaRec = reglaRecolector.sqlComuna("dr", "u_rec");
        String sqlComunaArm = reglaArmador.sqlComuna("da", "u_arm");

        // Construir consulta nativa unificada
        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        sql.append("SELECT ")
           .append("  u.id AS usuario_id, ")
           .append("  u.rut AS rut, ")
           .append("  CONCAT(COALESCE(u.nombres, ''), ' ', COALESCE(u.apellidop, '')) AS nombre, ")
           .append("  COALESCE(p.nombre, 'RECOLECTOR') AS perfil, ")
           .append("  c_u.nombre AS comuna_nombre, ")
           .append("  GROUP_CONCAT(DISTINCT emb.nombre SEPARATOR ', ') AS embarcaciones, ")
           .append("  COUNT(DISTINCT t.decl_id) AS cantidad_declaraciones, ")
           .append("  COALESCE(SUM(t.volumen), 0) AS volumen_total ")
           .append("FROM ( ")
           // Rama Recolectores
           .append("  SELECT dr.usuario_id, dr.id AS decl_id, NULL AS embarcacion_id, ")
           .append("         dr.").append(colVolumen).append(" AS volumen, ")
           .append("         ").append(sqlComunaRec).append(" AS comuna_imputada_id ")
           .append("  FROM declaracion_recolector dr ")
           .append("  LEFT JOIN usuario u_rec ON dr.usuario_id = u_rec.id ")
           .append("  WHERE dr.").append(colFecha).append(" >= :fechaInicio AND dr.").append(colFecha).append(" <= :fechaFin ");
        if (especieId != null) {
            sql.append("    AND dr.especie_id = :especieId ");
            params.put("especieId", especieId);
        }
        if (extraccionTipoId != null) {
            sql.append("    AND dr.extraccion_tipo_id = :extraccionTipoId ");
            params.put("extraccionTipoId", extraccionTipoId);
        }

        sql.append("  UNION ALL ")
           // Rama Armadores
           .append("  SELECT da.usuario_id, da.id AS decl_id, da.embarcacion_id, ")
           .append("         da.").append(colVolumen).append(" AS volumen, ")
           .append("         ").append(sqlComunaArm).append(" AS comuna_imputada_id ")
           .append("  FROM declaracion_armador da ")
           .append("  LEFT JOIN usuario u_arm ON da.usuario_id = u_arm.id ")
           .append("  WHERE da.").append(colFecha).append(" >= :fechaInicio AND da.").append(colFecha).append(" <= :fechaFin ");
        if (especieId != null) {
            sql.append("    AND da.especie_id = :especieId ");
        }
        if (extraccionTipoId != null) {
            sql.append("    AND da.extraccion_tipo_id = :extraccionTipoId ");
        }

        sql.append(") t ")
           .append("JOIN usuario u ON t.usuario_id = u.id ")
           .append("LEFT JOIN perfil p ON u.perfil_id = p.id ")
           .append("LEFT JOIN embarcacion emb ON t.embarcacion_id = emb.id ")
           .append("LEFT JOIN comuna c_u ON c_u.id = COALESCE(u.comuna_id, t.comuna_imputada_id) ");

        params.put("fechaInicio", fechaInicio);
        params.put("fechaFin", fechaFin);

        // Filtro de Sujeto: persona específica vs plantilla territorial
        if (cuota.getUsuario() != null) {
            sql.append("WHERE u.id = :targetUsuarioId ");
            params.put("targetUsuarioId", cuota.getUsuario().getId());
        } else if (Boolean.TRUE.equals(cuota.getEsPlantilla())) {
            sql.append("WHERE 1=1 ");
            Set<Long> cIds = AlcanceComunal.idsComunas(cuota);
            if (!cIds.isEmpty()) {
                sql.append("  AND c_u.id IN (:filtroComunaIds) ");
                params.put("filtroComunaIds", cIds);
            } else if (cuota.getProvincia() != null) {
                sql.append("  AND c_u.provincia_id = :filtroProvinciaId ");
                params.put("filtroProvinciaId", cuota.getProvincia().getId());
            } else if (cuota.getRegion() != null) {
                sql.append("  AND c_u.region_id = :filtroRegionId ");
                params.put("filtroRegionId", cuota.getRegion().getId());
            }

            if (!usuariosConCuotaPropia.isEmpty()) {
                sql.append("  AND u.id NOT IN (:excluirUsuarios) ");
                params.put("excluirUsuarios", usuariosConCuotaPropia);
            }
        } else {
            sql.append("WHERE 1=1 ");
        }

        sql.append("GROUP BY u.id, u.rut, u.nombres, u.apellidop, p.nombre, c_u.nombre ");
        sql.append("ORDER BY volumen_total DESC ");

        Query q = entityManager.createNativeQuery(sql.toString());
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            q.setParameter(entry.getKey(), entry.getValue());
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();
        List<ConsumoPersonaDTO> resultado = new ArrayList<>();

        for (Object[] r : rows) {
            Long uId = r[0] != null ? ((Number) r[0]).longValue() : null;
            String rut = r[1] != null ? r[1].toString() : "";
            String nombre = r[2] != null ? r[2].toString().trim() : "";
            String perfil = r[3] != null ? r[3].toString() : "";
            String comunaNom = r[4] != null ? r[4].toString() : "";
            String embarcacionesStr = r[5] != null ? r[5].toString() : null;
            int cantDecl = r[6] != null ? ((Number) r[6]).intValue() : 0;
            BigDecimal vol = r[7] != null ? new BigDecimal(r[7].toString()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

            List<String> embList = Collections.emptyList();
            if (embarcacionesStr != null && !embarcacionesStr.isBlank()) {
                embList = Arrays.stream(embarcacionesStr.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .distinct()
                        .toList();
            }

            Double pct = 0.0;
            if (limiteEfectivo.compareTo(BigDecimal.ZERO) > 0) {
                pct = vol.divide(limiteEfectivo, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue();
            }

            BigDecimal exceso = vol.subtract(limiteEfectivo);
            if (exceso.compareTo(BigDecimal.ZERO) < 0) {
                exceso = BigDecimal.ZERO;
            }

            resultado.add(ConsumoPersonaDTO.builder()
                    .usuarioId(uId)
                    .rut(rut)
                    .nombre(nombre.isEmpty() ? "Persona " + uId : nombre)
                    .perfil(perfil)
                    .comunaInscripcion(comunaNom)
                    .embarcacionesUsadas(embList)
                    .cantidadDeclaraciones(cantDecl)
                    .consumo(vol)
                    .limiteEfectivo(limiteEfectivo)
                    .porcentaje(Math.round(pct * 100.0) / 100.0)
                    .exceso(exceso)
                    .build());
        }

        return resultado;
    }

    /**
     * Genera el resumen consolidado de una plantilla para el tablero y el listado de cuotas.
     */
    public ResumenPlantillaDTO resumenPlantilla(CuotaExtraccionModel cuota, Date fechaEval) {
        List<ConsumoPersonaDTO> personas = porPersona(cuota, fechaEval);
        BigDecimal limiteEfectivo = cuotaExtraccionService.calcularLimiteEfectivo(cuota, fechaEval);

        int conActividad = 0;
        int sobreLimite = 0;
        double maxPct = 0.0;
        BigDecimal totalConsumido = BigDecimal.ZERO;

        for (ConsumoPersonaDTO p : personas) {
            if (p.getConsumo() != null && p.getConsumo().compareTo(BigDecimal.ZERO) > 0) {
                conActividad++;
                totalConsumido = totalConsumido.add(p.getConsumo());
            }
            if (p.getConsumo() != null && limiteEfectivo.compareTo(BigDecimal.ZERO) > 0) {
                if (p.getConsumo().compareTo(limiteEfectivo) > 0) {
                    sobreLimite++;
                }
                if (p.getPorcentaje() != null && p.getPorcentaje() > maxPct) {
                    maxPct = p.getPorcentaje();
                }
            }
        }

        String texto;
        if (conActividad == 0) {
            texto = "0 personas con actividad";
        } else {
            texto = String.format("%d de %d personas sobre su tope (máx. %.0f %%)",
                    sobreLimite, conActividad, maxPct);
        }

        return ResumenPlantillaDTO.builder()
                .personasConActividad(conActividad)
                .personasSobreLimite(sobreLimite)
                .maxPorcentaje(Math.round(maxPct * 100.0) / 100.0)
                .textoConsumo(texto)
                .consumoTotal(totalConsumido)
                .detallePersonas(personas)
                .build();
    }

    private Set<Long> obtenerUsuariosConCuotaPropia(CuotaExtraccionModel plantilla) {
        try {
            List<CuotaExtraccionModel> activas = cuotaRepository.findByActivoTrue();
            Long espId = plantilla.getEspecie() != null ? plantilla.getEspecie().getId() : null;
            Long metId = plantilla.getExtraccionTipo() != null ? plantilla.getExtraccionTipo().getId() : null;

            return activas.stream()
                    .filter(c -> c.getUsuario() != null
                            && !Boolean.TRUE.equals(c.getEsPlantilla())
                            && espId != null && c.getEspecie() != null && espId.equals(c.getEspecie().getId())
                            && (metId == null || c.getExtraccionTipo() == null || metId.equals(c.getExtraccionTipo().getId()))
                            && cuotaExtraccionService.seSolapan(plantilla, c))
                    .map(c -> c.getUsuario().getId())
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("Error resolviendo usuarios con cuota propia: {}", e.getMessage());
            return Collections.emptySet();
        }
    }
}
