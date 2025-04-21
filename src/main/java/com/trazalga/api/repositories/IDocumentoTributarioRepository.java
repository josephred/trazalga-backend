package com.trazalga.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.DocumentoTributarioModel;

@Repository
public interface IDocumentoTributarioRepository extends JpaRepository<DocumentoTributarioModel, Long> {

    
}
