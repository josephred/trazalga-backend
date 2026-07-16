package com.trazalga.api.models;

import java.util.Date;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;

@Entity
@Table(name = "documento_tributario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class DocumentoTributarioModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "tipo_id", nullable = false)
    private TipoDocumentoTributarioModel tipo;

    @Column
    private String numero;

    @Temporal(TemporalType.DATE)
    @Column
    private Date fecha;

}
