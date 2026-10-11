package com.trazalga.api.models;

import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.FetchType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import lombok.*;
import lombok.experimental.Accessors;

@Entity
@Table(name = "cuota_extraccion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class CuotaExtraccionModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * @deprecated Desde refinamiento 25-sep. Para cuotas AREA_LIBRE el consumo
     * suma recolector + armador. Para AMERB se conserva el comportamiento original.
     * Se mantiene por compatibilidad de datos históricos.
     */
    @Deprecated
    @Column(nullable = false)
    private String perfil;

    /**
     * Ámbito de la cuota: "AREA_LIBRE" o "AMERB".
     * Para cuotas AREA_LIBRE, el consumo suma recolectores y armadores.
     * Para AMERB, el consumo aplica exclusivamente a declaracion_area.
     */
    @Column(name = "ambito", nullable = false, length = 20)
    @Builder.Default
    private String ambito = "AREA_LIBRE";

    // Especie afectada (nullable = null means applies to any especie)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "especie_id", nullable = true)
    private EspecieModel especie;

    // Región (nullable = null means applies to any region)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id", nullable = true)
    private RegionModel region;

    // Macrozona (nullable = null means applies to any macrozona)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "macrozona_id", nullable = true)
    private MacrozonaModel macrozona;

    // Provincia (nullable = null means applies to any provincia)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provincia_id", nullable = true)
    private ProvinciaModel provincia;

    // Comuna cabecera (conservada por compatibilidad histórica y consultas directas)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comuna_id", nullable = true)
    private ComunaModel comuna;

    // Selección múltiple de comunas (T1.1)
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "cuota_extraccion_comuna",
               joinColumns = @JoinColumn(name = "cuota_id"),
               inverseJoinColumns = @JoinColumn(name = "comuna_id"))
    @Builder.Default
    private Set<ComunaModel> comunas = new LinkedHashSet<>();

    // Actor específico (nullable = null means applies to any actor of the perfil)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    private UsuarioModel usuario;

    // Área de manejo específica (nullable = null means applies to any AMERB; only relevant for perfil AREA)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "amerb_id", nullable = true)
    private AmerbModel amerb;

    // Método de extracción específico
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "extraccion_tipo_id", nullable = true)
    private ExtraccionTipoModel extraccionTipo;

    // Estado de humedad en que está expresado el límite (nullable = ya expresado en metrica)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "humedad_estado_id", nullable = true)
    private HumedadEstadoModel humedadEstado;

    // Nivel de agregación territorial / personal: COMUNA | PROVINCIA | REGION | INDIVIDUAL
    @Column(name = "nivel_agregacion", nullable = false, length = 20)
    @Builder.Default
    private String nivelAgregacion = "COMUNA";

    // Métrica evaluada: CAPTURA | DESEMBARQUE
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String metrica = "CAPTURA";

    // Si es plantilla para asignación individual
    @Column(name = "es_plantilla", nullable = false)
    @Builder.Default
    private Boolean esPlantilla = false;

    // Modo de acción: SOLO_ALERTA | BLOQUEO_DECLARACION
    @Column(name = "modo_accion", nullable = false, length = 30)
    @Builder.Default
    private String modoAccion = "SOLO_ALERTA";

    // Periodo: "DIARIO", "MENSUAL", "ANUAL", etc.
    @Column(nullable = false)
    private String periodo;

    // Límite en kilogramos
    @Column(name = "limite_kg", nullable = false)
    private Double limiteKg;

    @Temporal(TemporalType.DATE)
    @Column(name = "fecha_inicio", nullable = true)
    private Date fechaInicio;

    @Temporal(TemporalType.DATE)
    @Column(name = "fecha_fin", nullable = true)
    private Date fechaFin;

    @Column(length = 100)
    private String resolucion;

    // Estado administrativo de la cuota: ABIERTA | CERRADA
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String estado = "ABIERTA";

    @Setter(AccessLevel.NONE)
    @Temporal(TemporalType.DATE)
    @Column(name = "fecha_cierre", nullable = true)
    private Date fechaCierre;

    @Setter(AccessLevel.NONE)
    @Temporal(TemporalType.DATE)
    @Column(name = "fecha_cierre_automatico", nullable = true)
    private Date fechaCierreAutomatico;

    @Setter(AccessLevel.NONE)
    @Column(name = "motivo_cierre", nullable = true, length = 30)
    private String motivoCierre; // AGOTAMIENTO | ADMINISTRATIVO | VENCIMIENTO

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Date createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_at", nullable = false)
    private Date updatedAt;

    @PrePersist
    protected void onCreate() {
        Date now = new Date();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = new Date();
    }

    /**
     * Cierra la cuota administrativamente o por agotamiento/vencimiento (TC.2).
     * Solo opera desde estado ABIERTA.
     *
     * @param motivo Motivo de cierre: "ADMINISTRATIVO", "AGOTAMIENTO", "VENCIMIENTO".
     * @param fecha Fecha de cierre (si es nula, se usa la fecha actual).
     * @param usuarioId ID del usuario responsable (opcional).
     */
    public void cerrar(String motivo, Date fecha, Long usuarioId) {
        if ("CERRADA".equalsIgnoreCase(this.estado)) {
            throw new IllegalStateException("La cuota ID " + this.id + " ya se encuentra cerrada.");
        }
        String motivoNorm = (motivo != null && !motivo.isBlank()) ? motivo.trim().toUpperCase() : "ADMINISTRATIVO";
        Date fechaEfectiva = (fecha != null) ? fecha : new Date();

        if ("AGOTAMIENTO".equals(motivoNorm)) {
            this.fechaCierreAutomatico = fechaEfectiva;
        } else {
            this.fechaCierre = fechaEfectiva;
        }
        this.motivoCierre = motivoNorm;
        this.estado = "CERRADA";
    }

    /**
     * Reabre una cuota previamente cerrada (TC.2).
     * Solo opera desde estado CERRADA y exige un motivo justificativo no vacío.
     *
     * @param motivo Motivo de reapertura obligatorio.
     * @param usuarioId ID del usuario responsable (opcional).
     */
    public void reabrir(String motivo, Long usuarioId) {
        if (!"CERRADA".equalsIgnoreCase(this.estado)) {
            throw new IllegalStateException("La cuota ID " + this.id + " no está cerrada (estado actual: " + this.estado + ").");
        }
        if (motivo == null || motivo.trim().isEmpty()) {
            throw new IllegalArgumentException("El motivo de reapertura es obligatorio.");
        }
        this.estado = "ABIERTA";
        this.fechaCierre = null;
        this.fechaCierreAutomatico = null;
        this.motivoCierre = null;
    }

    /**
     * Determina la fecha efectiva de cierre para la evaluación de declaraciones (TC.2 / TC.4).
     *
     * 1. fechaCierre (cierre manual o por vencimiento ejecutado).
     * 2. fechaCierreAutomatico (cierre por agotamiento).
     * 3. Si ambas son nulas, el cierre por vencimiento está activo y fechaFin < hoy: fechaFin.
     *    Así, las reglas de TC.4 no dependen de la hora a la que corra la tarea diaria de TC.3.
     */
    public java.time.LocalDate fechaCierreEfectiva(java.time.LocalDate hoy, boolean cierrePorVencimientoActivo) {
        if (this.fechaCierre != null) {
            return toLocalDateSafe(this.fechaCierre);
        }
        if (this.fechaCierreAutomatico != null) {
            return toLocalDateSafe(this.fechaCierreAutomatico);
        }
        if (cierrePorVencimientoActivo && this.fechaFin != null && hoy != null) {
            java.time.LocalDate fFin = toLocalDateSafe(this.fechaFin);
            if (fFin.isBefore(hoy)) {
                return fFin;
            }
        }
        return null;
    }

    public java.time.LocalDate fechaCierreEfectiva(Date hoyDate, boolean cierrePorVencimientoActivo) {
        java.time.LocalDate hoy = hoyDate != null ? toLocalDateSafe(hoyDate) : java.time.LocalDate.now();
        return fechaCierreEfectiva(hoy, cierrePorVencimientoActivo);
    }

    private static java.time.LocalDate toLocalDateSafe(Date date) {
        if (date == null) return null;
        if (date instanceof java.sql.Date) {
            return ((java.sql.Date) date).toLocalDate();
        }
        return date.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
    }
}

