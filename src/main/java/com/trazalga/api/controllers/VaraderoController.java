package com.trazalga.api.controllers;

import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trazalga.api.models.VaraderoModel;
import com.trazalga.api.services.VaraderoService;

@RestController
@RequestMapping("/varadero")
public class VaraderoController {

    @Autowired
    VaraderoService varaderoService;

    @GetMapping
    public ArrayList<VaraderoModel> getVaraderos() {
        return varaderoService.getVaraderos();
    }

}
