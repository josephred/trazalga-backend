package com.trazalga.api.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.models.DeclaracionRecolectorModel;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;

@Service
public class DeclaracionRecolectorService {
    
    @Autowired
    IDeclaracionRecolectorRepository declaracionRecolectorRepository;
    
    public ArrayList<DeclaracionRecolectorModel> getDeclaracionesRecolector(){
        return (ArrayList<DeclaracionRecolectorModel>) declaracionRecolectorRepository.findAll();
        // return (ArrayList<DeclaracionRecolectorModel>) declaracionRecolectorRepository.findAllOrderByCampoEspecificoDesc();
    }

    public ArrayList<DeclaracionRecolectorModel> getDeclaracionesRecolectorIdUsuario(Long id){
        return (ArrayList<DeclaracionRecolectorModel>) declaracionRecolectorRepository.findAllByUsuarioId(id);
    }

    public List<DeclaracionRecolectorModel> getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(Long usuarioDestinatarioId) {
        return declaracionRecolectorRepository.findByUsuarioDestinatarioIdAndDeclaracionDestinatarioIsNull(usuarioDestinatarioId);
    }


    public DeclaracionRecolectorModel saveDeclaracionRecolector(DeclaracionRecolectorModel declaracionRecolectorModel){
        calcularTasaDiaria(declaracionRecolectorModel);
        return declaracionRecolectorRepository.save(declaracionRecolectorModel);
    }

    public Optional<DeclaracionRecolectorModel> getById(Long id){
        return declaracionRecolectorRepository.findById(id);
    }

    public DeclaracionRecolectorModel updateById(DeclaracionRecolectorModel request, Long id){
        DeclaracionRecolectorModel declaracionRecolectorModel = declaracionRecolectorRepository.findById(id).get();
        declaracionRecolectorModel.setFolioOrigen(request.getFolioOrigen());
        declaracionRecolectorModel.setFolioDesembarqueRo(request.getFolioDesembarqueRo());
        declaracionRecolectorModel.setFechaExtraccion(request.getFechaExtraccion());
        declaracionRecolectorModel.setPeriodoExtraccionInicio(request.getPeriodoExtraccionInicio());
        declaracionRecolectorModel.setPeriodoExtraccionFin(request.getPeriodoExtraccionFin());
        declaracionRecolectorModel.setFechaDeclaracion(request.getFechaDeclaracion());
        declaracionRecolectorModel.setHora(request.getHora());
        declaracionRecolectorModel.setNombre(request.getNombre());
        declaracionRecolectorModel.setCodigoSernapesca(request.getCodigoSernapesca());
        declaracionRecolectorModel.setVaradero(request.getVaradero());        
        declaracionRecolectorModel.setCaleta(request.getCaleta());
        //declaracionRecolectorModel.setGeorreferencia(request.getGeorreferencia());
        declaracionRecolectorModel.setLatitud(request.getLatitud());
        declaracionRecolectorModel.setLongitud(request.getLongitud());
        declaracionRecolectorModel.setEspecie(request.getEspecie());
        declaracionRecolectorModel.setComuna(request.getComuna());
        declaracionRecolectorModel.setExtraccionTipo(request.getExtraccionTipo());
        declaracionRecolectorModel.setComposicion(request.getComposicion());
        declaracionRecolectorModel.setHumedadEstado(request.getHumedadEstado());
        declaracionRecolectorModel.setHumedad(request.getHumedad());
        declaracionRecolectorModel.setDesembarque(request.getDesembarque());
        declaracionRecolectorModel.setCaptura(request.getCaptura());
        declaracionRecolectorModel.setCodigoDestinatario(request.getCodigoDestinatario());
        declaracionRecolectorModel.setUsuarioDestinatario(request.getUsuarioDestinatario());
        
        calcularTasaDiaria(declaracionRecolectorModel);
        declaracionRecolectorRepository.save(declaracionRecolectorModel);
        return declaracionRecolectorModel;
    }

    public Boolean deleteDeclaracionRecolector(Long id){
        try{
            declaracionRecolectorRepository.deleteById(id);
            return true;
        } catch( Exception e){return false;}
    }

    public String getLastFolioOrigen() {
        List<String> folios = declaracionRecolectorRepository.findLastFolioOrigen();
        return folios.isEmpty() ? null : folios.getFirst();
    }

    public String getLastFolioDesembarqueRo() {
        List<String> folios = declaracionRecolectorRepository.findLastFolioDesembarqueRo();
        return folios.isEmpty() ? null : folios.getFirst();
    }


    /**
     * Calcula la tasa diaria de recolección y sincroniza fechaExtraccion.
     * Fórmula: tasa = desembarque / (DATEDIFF(fin, inicio) + 1)
     * Mantiene retrocompatibilidad: fechaExtraccion = periodoExtraccionFin
     */
    private void calcularTasaDiaria(DeclaracionRecolectorModel model) {
        if (model.getPeriodoExtraccionInicio() != null
                && model.getPeriodoExtraccionFin() != null
                && model.getDesembarque() != null) {

            long diffMs = model.getPeriodoExtraccionFin().getTime()
                        - model.getPeriodoExtraccionInicio().getTime();
            long dias = TimeUnit.DAYS.convert(diffMs, TimeUnit.MILLISECONDS) + 1;

            if (dias <= 0) {
                throw new IllegalArgumentException(
                    "Periodo de extracción inválido: la fecha de inicio no puede ser posterior a la fecha de fin.");
            }

            BigDecimal tasaDiaria = model.getDesembarque()
                .divide(BigDecimal.valueOf(dias), 3, RoundingMode.HALF_UP);
            model.setTasaDiariaRecoleccion(tasaDiaria);

            // Retrocompatibilidad: fecha_extraccion = fecha fin del periodo
            model.setFechaExtraccion(model.getPeriodoExtraccionFin());
        }
    }

}
