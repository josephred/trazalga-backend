package com.trazalga.api.models;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Autorización de un fiscalizador sobre una declaración congelada (refinamiento 30-10, punto 3).
 * <ul>
 *   <li>{@code EDICION}: el dueño puede editar una vez, antes de {@code venceEn}.</li>
 *   <li>{@code ELIMINACION}: el fiscalizador eliminó la declaración; {@code snapshot} guarda la
 *       declaración y sus marcas en JSON como respaldo.</li>
 * </ul>
 */
@Entity
@Table(name = "declaracion_autorizacion", indexes = {
        @Index(name = "ix_decl_aut_decl", columnList = "declaracion_tipo, declaracion_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeclaracionAutorizacionModel {

    public static final String TIPO_EDICION = "EDICION";
    public static final String TIPO_ELIMINACION = "ELIMINACION";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "declaracion_tipo", nullable = false, length = 30)
    private String declaracionTipo;

    @Column(name = "declaracion_id", nullable = false)
    private Long declaracionId;

    @Column(name = "tipo", nullable = false, length = 20)
    private String tipo;

    @Column(name = "motivo", nullable = false, length = 500)
    private String motivo;

    @Column(name = "acta_verificacion", nullable = false, length = 120)
    private String actaVerificacion;

    @Column(name = "autorizado_por_usuario_id")
    private Long autorizadoPorUsuarioId;

    @Column(name = "autorizado_por_rut", length = 20)
    private String autorizadoPorRut;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "autorizado_en", nullable = false)
    private Date autorizadoEn;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "vence_en")
    private Date venceEn;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "usada_en")
    private Date usadaEn;

    @Column(name = "snapshot", columnDefinition = "TEXT")
    private String snapshot;
}
