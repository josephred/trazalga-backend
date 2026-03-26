package com.trazalga.api.services;

import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.trazalga.api.models.VaraderoModel;
import com.trazalga.api.repositories.IVaraderoRepository;

@Service
public class VaraderoService {

    @Autowired
    IVaraderoRepository varaderoRepository;

    public ArrayList<VaraderoModel> getVaraderos() {
        return (ArrayList<VaraderoModel>) varaderoRepository.findAll();
    }

}
