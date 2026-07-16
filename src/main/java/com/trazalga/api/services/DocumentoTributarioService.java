package com.trazalga.api.services;

import java.util.ArrayList;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

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
        if (documentoTributario.getNumero() == null || !documentoTributario.getNumero().matches("^\\d+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El número de documento tributario debe contener solo dígitos.");
        }
        return documentoTributarioRepository.save(documentoTributario);
    }

    public Optional<DocumentoTributarioModel> getById(Long id){
        return documentoTributarioRepository.findById(id);
    }

    public DocumentoTributarioModel updateById(DocumentoTributarioModel request,Long id){
        if (request.getNumero() == null || !request.getNumero().matches("^\\d+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El número de documento tributario debe contener solo dígitos.");
        }
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
