package com.trazalga.api.services;

import java.util.ArrayList;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.models.CaletaModel;
import com.trazalga.api.repositories.ICaletaRepository;

@Service
public class CaletaService {

    @Autowired
    ICaletaRepository caletaRepository;

    public ArrayList<CaletaModel> getCaletas(){
        return (ArrayList<CaletaModel>) caletaRepository.findAll();
    } 

    public java.util.List<CaletaModel> getByComuna(Long comunaId){
        return caletaRepository.findByComunaId(comunaId);
    }

    public java.util.List<CaletaModel> getByRegion(Long regionId){
        return caletaRepository.findByRegionId(regionId);
    }

    public CaletaModel saveCaleta(CaletaModel caleta){
        return caletaRepository.save(caleta);
    }

    public Optional<CaletaModel> getById(Long id){
        return caletaRepository.findById(id);
    }

    @Autowired
    private com.trazalga.api.repositories.ICoordenadaCambioLogRepository cambioLogRepository;

    @Autowired(required = false)
    private com.trazalga.api.services.geo.ReferenciaOrigenService referenciaOrigenService;

    public CaletaModel updateById(CaletaModel request, Long id){
        CaletaModel caleta = caletaRepository.findById(id).orElseThrow();
        if (request.getNombre() != null) caleta.setNombre(request.getNombre());
        if (request.getRegion() != null) caleta.setRegion(request.getRegion());
        if (request.getComuna() != null) caleta.setComuna(request.getComuna());
        if (request.getVaradero() != null) caleta.setVaradero(request.getVaradero());

        boolean cambioCoord = false;
        Double latAnt = caleta.getLatitud();
        Double lonAnt = caleta.getLongitud();
        String fuenteAnt = caleta.getFuenteCoordenada();

        if (request.getLatitud() != null && !request.getLatitud().equals(latAnt)) {
            caleta.setLatitud(request.getLatitud());
            cambioCoord = true;
        }
        if (request.getLongitud() != null && !request.getLongitud().equals(lonAnt)) {
            caleta.setLongitud(request.getLongitud());
            cambioCoord = true;
        }

        if (cambioCoord) {
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            caleta.setFuenteCoordenada("MANUAL");
            caleta.setCoordenadaActualizadaEn(now);
            caleta.setCoordenadaActualizadaPor("MANUAL_ADMIN");

            cambioLogRepository.save(com.trazalga.api.models.CoordenadaCambioLogModel.builder()
                    .entidadTipo("CALETA")
                    .entidadId(caleta.getId())
                    .entidadNombre(caleta.getNombre())
                    .latitudAnterior(latAnt)
                    .longitudAnterior(lonAnt)
                    .fuenteAnterior(fuenteAnt)
                    .latitudNueva(caleta.getLatitud())
                    .longitudNueva(caleta.getLongitud())
                    .fuenteNueva("MANUAL")
                    .actualizadoEn(now)
                    .actualizadoPor("MANUAL_ADMIN")
                    .build());
        }

        CaletaModel saved = caletaRepository.save(caleta);
        if (cambioCoord && referenciaOrigenService != null) {
            referenciaOrigenService.invalidarCache();
        }
        return saved;
    }

    public Boolean deleteCaleta(Long id){
        try{
            caletaRepository.deleteById(id);
            return true;
        } catch(Exception e){
            return false;
        }
    }
}
