package com.trazalga.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.trazalga.api.models.AmerbModel;
import java.util.List;

@Repository
public interface IAmerbRepository extends JpaRepository<AmerbModel, Long> {
    
    // Buscar AMERB por región
    List<AmerbModel> findByRegion(String region);

    // Buscar AMERB por estado (ejemplo: "vigente", "caducada")
    List<AmerbModel> findByEstado(String estado);

    // Buscar AMERB por código Sernapesca
    AmerbModel findByCodigoSernapesca(String codigoSernapesca);

    // Buscar AMERB por folio organización
    AmerbModel findByFolioOrganizacion(Integer folioOrganizacion);

    // Buscar AMERB por nombre (contiene)
    List<AmerbModel> findByNombreContaining(String nombre);
}
