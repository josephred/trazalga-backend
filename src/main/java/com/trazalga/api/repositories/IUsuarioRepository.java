package com.trazalga.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.UsuarioModel;

@Repository
public interface IUsuarioRepository extends JpaRepository<UsuarioModel, Long> {

}
