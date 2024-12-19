package com.trazalga.api.services;

import java.util.ArrayList;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.models.DocumentoTributarioModel;
import com.trazalga.api.repositories.IDocumentoTributarioRepository;

@Service
public class DocumentoTributarioService {

    @Autowired
    IDocumentoTributarioRepository documentoTributarioRepository;

    public ArrayList<DocumentoTributarioModel> getDocumentoTributarios(){
        return (ArrayList<DocumentoTributarioModel>) documentoTributarioRepository.findAll();
    } 

    public DocumentoTributarioModel saveDocumentoTributario(DocumentoTributarioModel documentoTributario){
        return documentoTributarioRepository.save(documentoTributario);
    }

    public Optional<DocumentoTributarioModel> getById(Long id){
        return documentoTributarioRepository.findById(id);
    }

    public DocumentoTributarioModel updateById(DocumentoTributarioModel request,Long id){
        DocumentoTributarioModel documentoTributario = documentoTributarioRepository.findById(id).get();
        documentoTributario.setTipo(request.getTipo());
        documentoTributario.setNumero(request.getNumero());
        documentoTributario.setFecha(request.getFecha());
        documentoTributarioRepository.save(documentoTributario);
        return documentoTributario;
    }

    public Boolean deleteDocumentoTributario(Long id){
        try{
            documentoTributarioRepository.deleteById(id);
            return true;
        } catch(Exception e){
            return false;
        }
    }
}
