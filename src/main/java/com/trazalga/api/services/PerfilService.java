package com.trazalga.api.services;

import java.util.ArrayList;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.models.PerfilModel;
import com.trazalga.api.repositories.IPerfilRepository;

@Service
public class PerfilService {

    @Autowired
    IPerfilRepository perfilRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    /**
     * Perfiles compuestos para RUT que operan con múltiples roles receptores
     * (mismo patrón que los perfiles 9-11 de la banda extractiva).
     * Idempotente: solo inserta si el id no existe. INSERT nativo porque los ids
     * son significativos (la app mapea roles por id) y GenerationType.IDENTITY
     * ignora el id provisto en repository.save().
     */
    @jakarta.annotation.PostConstruct
    public void seedPerfilesCompuestos() {
        if (!perfilRepository.existsById(12L)) {
            jdbcTemplate.update("INSERT INTO perfil (id, nombre, descripcion) VALUES (?, ?, ?)",
                    12L, "COMERCIALIZADOR / P. ABASTECIMIENTO",
                    "Comercializador / Planta Abastecimiento");
        }
        if (!perfilRepository.existsById(13L)) {
            jdbcTemplate.update("INSERT INTO perfil (id, nombre, descripcion) VALUES (?, ?, ?)",
                    13L, "COMERCIALIZADOR / P. ABASTECIMIENTO / P. PRODUCCION",
                    "Comercializador / Planta Abastecimiento / Planta Produccion");
        }
    }

    public ArrayList<PerfilModel> getPerfiles(){
        return (ArrayList<PerfilModel>) perfilRepository.findAll();
    }
    
    public PerfilModel savePerfil(PerfilModel perfilModel){
        return perfilRepository.save(perfilModel);
    }

    public Optional<PerfilModel> getById(Long id){
        return perfilRepository.findById(id);
    }
    
    public PerfilModel updateById(PerfilModel request, Long id){
        PerfilModel perfilModel = perfilRepository.findById(id).get();
        perfilModel.setNombre(request.getNombre());
        perfilModel.setDescripcion(request.getDescripcion());
        perfilRepository.save(perfilModel);
        return perfilModel;
    }

    public Boolean deletePerfil(Long id){
        try{
            perfilRepository.deleteById(id);
            return true;
        } catch( Exception e){return false;}
    }
}
