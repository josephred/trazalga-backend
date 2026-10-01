package com.trazalga.api.models;

import java.util.Date;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;

@Entity
@Table(name = "consulta_patente_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class ConsultaPatenteLogModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String patente;

    @Column(name = "ip_hash", nullable = false, length = 64)
    private String ipHash;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date fecha;

    @Column(nullable = false, length = 20)
    private String resultado; // VIGENTE | NO_VIGENTE | SIN_REGISTROS
}
