package com.trazalga.api.models;

import java.util.Date;
import jakarta.persistence.*;
import lombok.*;

/**
 * Entidad de auditoría para registrar cada búsqueda por folio realizada en la consola (T2.2).
 */
@Entity
@Table(name = "consulta_folio_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsultaFolioLogModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_rut", nullable = false, length = 20)
    private String usuarioRut;

    @Column(name = "texto_buscado", nullable = false, length = 100)
    private String textoBuscado;

    @Column(name = "cantidad_resultados", nullable = false)
    private Integer cantidadResultados;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "fecha", nullable = false)
    private Date fecha;
}
