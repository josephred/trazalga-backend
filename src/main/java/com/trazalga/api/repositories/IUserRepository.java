package com.trazalga.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trazalga.api.models.UserModel;

@Repository
public interface IUserRepository extends JpaRepository<UserModel, Long> {

    
}
