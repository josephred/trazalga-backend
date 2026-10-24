package com.trazalga.api.repositories;

import com.trazalga.api.models.TransportistaAliasModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ITransportistaAliasRepository extends JpaRepository<TransportistaAliasModel, Long> {

    Optional<TransportistaAliasModel> findByTransportistaIdAndNombreNormalizado(Long transportistaId, String nombreNormalizado);

    List<TransportistaAliasModel> findByTransportistaId(Long transportistaId);

    List<TransportistaAliasModel> findByTransportistaIdOrderByVecesDesc(Long transportistaId);
}
