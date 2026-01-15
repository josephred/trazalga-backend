package com.trazalga.api.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.trazalga.api.dto.PerformanceReport;
import com.trazalga.api.services.StressTestService;

@RestController
@RequestMapping("/api/stress-test")
public class StressTestController {

    @Autowired
    private StressTestService stressTestService;

    @PostMapping("/run")
    public PerformanceReport runStressTest(@RequestParam(defaultValue = "1000") int cantidad) {
        return stressTestService.generarCargaMasiva(cantidad);
    }
}
