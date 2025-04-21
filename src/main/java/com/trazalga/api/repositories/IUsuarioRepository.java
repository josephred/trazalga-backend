package com.trazalga.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.UsuarioModel;

import java.util.ArrayList;
import java.util.List;


@Repository
public interface IUsuarioRepository extends JpaRepository<UsuarioModel, Long> {

    UsuarioModel findByRut(String rut);
  
    // @Query("SELECT u, u.comuna FROM UsuarioModel u WHERE u.perfil.id IN :perfiles")
    // List<UsuarioModel> findUsuariosByPerfiles(@Param("perfiles") List<Long> perfiles);

    @Query("SELECT u FROM UsuarioModel u JOIN FETCH u.comuna WHERE u.perfil.id IN :perfiles")
    List<UsuarioModel> findUsuariosByPerfiles(@Param("perfiles") List<Long> perfiles);
}
