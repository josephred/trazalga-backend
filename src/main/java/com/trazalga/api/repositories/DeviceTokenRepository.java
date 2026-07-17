package com.trazalga.api.repositories;

import com.trazalga.api.models.DeviceTokenModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceTokenModel, Long> {
    List<DeviceTokenModel> findByUsuarioId(Long usuarioId);
    java.util.Optional<DeviceTokenModel> findByToken(String token);
    void deleteByToken(String token);
    boolean existsByToken(String token);
}
