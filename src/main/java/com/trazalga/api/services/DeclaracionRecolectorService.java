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
import com.trazalga.api.models.DeclaracionBuzosModel;
import com.trazalga.api.models.PerfilModel;
import com.trazalga.api.models.BuzoModel;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;
import com.trazalga.api.repositories.DeclaracionBuzosRepository;
import com.trazalga.api.repositories.IBuzoRepository;

@Service
public class DeclaracionRecolectorService {
    
    @Autowired
    IDeclaracionRecolectorRepository declaracionRecolectorRepository;

    @Autowired
    private DeclaracionBuzosRepository declaracionBuzosRepository;

    @Autowired
    private IBuzoRepository buzoRepository;
    
    public ArrayList<DeclaracionRecolectorModel> getDeclaracionesRecolector(){
        ArrayList<DeclaracionRecolectorModel> list = (ArrayList<DeclaracionRecolectorModel>) declaracionRecolectorRepository.findAll();
        list.forEach(this::populateBuzos);
        return list;
    }

    public ArrayList<DeclaracionRecolectorModel> getDeclaracionesRecolectorIdUsuario(Long id){
        ArrayList<DeclaracionRecolectorModel> list = (ArrayList<DeclaracionRecolectorModel>) declaracionRecolectorRepository.findAllByUsuarioId(id);
        list.forEach(this::populateBuzos);
        return list;
    }

    public List<DeclaracionRecolectorModel> getDeclaracionesByUsuarioDestinatarioConDeclaracionNula(Long usuarioDestinatarioId) {
        List<DeclaracionRecolectorModel> list = declaracionRecolectorRepository.findByUsuarioDestinatarioIdAndDeclaracionDestinatarioIsNull(usuarioDestinatarioId);
        list.forEach(this::populateBuzos);
        return list;
    }


    public DeclaracionRecolectorModel saveDeclaracionRecolector(DeclaracionRecolectorModel declaracionRecolectorModel){
        sanearComposicion(declaracionRecolectorModel);
        calcularTasaDiaria(declaracionRecolectorModel);
        DeclaracionRecolectorModel saved = declaracionRecolectorRepository.save(declaracionRecolectorModel);

        // Guardar buzos
        if (declaracionRecolectorModel.getBuzos() != null && !declaracionRecolectorModel.getBuzos().isEmpty()) {
            PerfilModel perfil = saved.getUsuario() != null ? saved.getUsuario().getPerfil() : null;
            for (BuzoModel buzoReq : declaracionRecolectorModel.getBuzos()) {
                if (buzoReq.getId() != null) {
                    buzoRepository.findById(buzoReq.getId()).ifPresent(buzo -> {
                        DeclaracionBuzosModel db = new DeclaracionBuzosModel();
                        db.setPerfil(perfil);
                        db.setBuzo(buzo);
                        db.setDeclaracionRecolector(saved);
                        declaracionBuzosRepository.save(db);
                    });
                }
            }
        }
        populateBuzos(saved);
        return saved;
    }

    public Optional<DeclaracionRecolectorModel> getById(Long id){
        Optional<DeclaracionRecolectorModel> opt = declaracionRecolectorRepository.findById(id);
        opt.ifPresent(this::populateBuzos);
        return opt;
    }

    public DeclaracionRecolectorModel updateById(DeclaracionRecolectorModel request, Long id){
        DeclaracionRecolectorModel declaracionRecolectorModel = declaracionRecolectorRepository.findById(id).get();
        if (declaracionRecolectorModel.getDeclaracionDestinatario() != null) {
            throw new IllegalArgumentException("Esta declaración ya ha sido seleccionada o ingresada en otra declaración y no puede ser modificada.");
        }
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
        
        sanearComposicion(declaracionRecolectorModel);
        calcularTasaDiaria(declaracionRecolectorModel);
        DeclaracionRecolectorModel updated = declaracionRecolectorRepository.save(declaracionRecolectorModel);

        // Actualizar buzos
        List<DeclaracionBuzosModel> anteriores = declaracionBuzosRepository.findByDeclaracionRecolectorId(id);
        declaracionBuzosRepository.deleteAll(anteriores);

        if (request.getBuzos() != null && !request.getBuzos().isEmpty()) {
            PerfilModel perfil = updated.getUsuario() != null ? updated.getUsuario().getPerfil() : null;
            for (BuzoModel buzoReq : request.getBuzos()) {
                if (buzoReq.getId() != null) {
                    buzoRepository.findById(buzoReq.getId()).ifPresent(buzo -> {
                        DeclaracionBuzosModel db = new DeclaracionBuzosModel();
                        db.setPerfil(perfil);
                        db.setBuzo(buzo);
                        db.setDeclaracionRecolector(updated);
                        declaracionBuzosRepository.save(db);
                    });
                }
            }
        }
        populateBuzos(updated);
        return updated;
    }

    public Boolean deleteDeclaracionRecolector(Long id){
        DeclaracionRecolectorModel model = declaracionRecolectorRepository.findById(id).orElse(null);
        if (model != null && model.getDeclaracionDestinatario() != null) {
            throw new IllegalArgumentException("Esta declaración ya ha sido seleccionada o ingresada en otra declaración y no puede ser eliminada.");
        }
        try{
            List<DeclaracionBuzosModel> buzos = declaracionBuzosRepository.findByDeclaracionRecolectorId(id);
            declaracionBuzosRepository.deleteAll(buzos);
            declaracionRecolectorRepository.deleteById(id);
            return true;
        } catch( Exception e){return false;}
    }

    private void populateBuzos(DeclaracionRecolectorModel model) {
        if (model != null && model.getId() != null) {
            List<DeclaracionBuzosModel> dbList = declaracionBuzosRepository.findByDeclaracionRecolectorId(model.getId());
            List<BuzoModel> buzos = new ArrayList<>();
            for (DeclaracionBuzosModel db : dbList) {
                buzos.add(db.getBuzo());
            }
            model.setBuzos(buzos);
        }
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

    private void sanearComposicion(DeclaracionRecolectorModel model) {
        if (model.getComposicion() != null && 
            (model.getComposicion().getId() == null || model.getComposicion().getId() == 0)) {
            model.setComposicion(null);
        }
    }

}
