package com.trazalga.api.models;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * Metadato de archivo adjunto almacenado en disco (NA.4: fotos de voucher de romana y guías).
 */
@Entity
@Table(name = "adjunto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class AdjuntoModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String uuid;

    @Column(name = "nombre_original", nullable = false, length = 255)
    private String nombreOriginal;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "tamano_bytes", nullable = false)
    private Long tamanoBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "ruta_relativa", nullable = false, length = 255)
    private String rutaRelativa;

    @Column(name = "tipo_documento", length = 40)
    private String tipoDocumento;

    @Column(name = "subido_por_usuario_id")
    private Long subidoPorUsuarioId;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Date creadoEn;

    @PrePersist
    protected void onCreate() {
        if (this.creadoEn == null) {
            this.creadoEn = new Date();
        }
    }
}

