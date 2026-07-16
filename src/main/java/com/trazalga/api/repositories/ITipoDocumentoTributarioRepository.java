package com.trazalga.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.trazalga.api.models.TipoDocumentoTributarioModel;

@Repository
public interface ITipoDocumentoTributarioRepository extends JpaRepository<TipoDocumentoTributarioModel, Long> {
}
