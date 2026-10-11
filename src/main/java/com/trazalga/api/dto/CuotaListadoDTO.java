package com.trazalga.api.dto;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CuotaListadoDTO {
    private Long id;
    private String ambito; // AREA_LIBRE, AMERB
    private String perfil; // RECOLECTOR, ARMADOR, AREA
    private String nivelAgregacion; // COMUNA, REGION, PROVINCIA, MACROZONA, INDIVIDUAL, NACIONAL

    // Territorio
    private Long regionId;
    private String regionNombre;
    private Long provinciaId;
    private String provinciaNombre;
    private Long comunaId; // comuna cabecera
    private String comunaNombre; // nombre comuna cabecera
    private Set<Long> comunaIds;
    private List<String> comunaNombres;
    private String comunasNombre; // e.g. "Coquimbo + La Serena"

    // Especie y Método
    private Long especieId;
    private String especieNombre;
    private Long extraccionTipoId;
    private String extraccionTipoNombre;

    // Vigencia
    private String periodo; // MENSUAL, ANUAL, DIARIO
    private Date fechaInicio;
    private Date fechaFin;
    private String vigenciaDescripcion; // e.g. "Marzo 2026" o "01-03-2026 al 31-03-2026"

    // Cuota y Consumo
    private Double limiteKg;
    private BigDecimal limiteNominal;
    private BigDecimal limiteEfectivo;
    private BigDecimal consumoAcumulado;
    private Double porcentajeUso;
    private BigDecimal saldoDisponible;

    // Métrica y Calidad
    private String metrica; // CAPTURA, DESEMBARQUE
    private Long humedadEstadoId;
    private String humedadEstadoNombre;
    private BigDecimal factorConversion;
    private String modoAccion; // SOLO_ALERTA, BLOQUEO_DECLARACION
    private String resolucion;

    // Estado y Alcance
    private String estado; // ABIERTA, CERRADA
    private Date fechaCierre;
    private String motivoCierre;
    private Boolean activo;
    private String alcance; // "Comunas Coquimbo + La Serena", "Región Coquimbo", etc.
    private Boolean esFormatoAnterior; // true si periodo != MENSUAL, sin fechas, o nivel no comunal/regional

    // Campos de cuotas individuales y plantillas (TM.1 / TM.2)
    private Boolean esPlantilla;
    private Long usuarioId;
    private String usuarioNombre;
    private Integer personasConActividad;
    private Integer personasSobreLimite;
    private Double maxPorcentaje;
    private String textoConsumoPlantilla;
}
