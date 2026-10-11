package com.trazalga.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ControlCuotaDiariaDTO {
    private String especieNombre;
    private BigDecimal volumenExtraido;
    private BigDecimal limiteCuota; // Límite efectivo en la métrica evaluada (para compatibilidad)
    private BigDecimal limiteNominal; // Límite original declarado en la cuota (ej. 5.000 kg secos)
    private BigDecimal limiteEfectivo; // Límite tras aplicar factor de conversión (ej. 17.900 kg captura)
    private String humedadEstadoNombre; // Estado de humedad nominal (ej. "Seco")
    private String metrica; // CAPTURA o DESEMBARQUE
    private BigDecimal factorConversion; // Factor de conversión aplicado (ej. 3.5800)
    private String descripcionEquivalencia; // Ej: "5.000 kg secos ≡ 17.900 kg captura (factor 3,58)"
    private Double porcentajeUso;
    // Alcance de la cuota: "Global", nombre del actor o nombre del área de manejo
    private String alcance;
    private Long cuotaId;
    private String periodo;
    private String ambito;
    private String extraccionTipoNombre;
    private Long comunaId;
    private String comunaNombre;
    private Set<Long> comunaIds;
    private String comunasNombre;
    private String fechaInicio;
    private String fechaFin;
    private String vigenciaFormateada;

    // Campos de cuotas individuales y plantillas (TM.1 / TM.2)
    private Boolean esPlantilla;
    private Integer personasConActividad;
    private Integer personasSobreLimite;
    private Double maxPorcentaje;
    private String textoConsumoPlantilla;
    private Long usuarioId;
    private String usuarioNombre;
    private Long provinciaId;
    private String provinciaNombre;
    private Long regionId;
    private String regionNombre;
}
