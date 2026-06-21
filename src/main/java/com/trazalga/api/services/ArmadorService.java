package com.trazalga.api.services;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.trazalga.api.dto.ArmadorDto;
import com.trazalga.api.dto.sernapesca.EmbarcacionDto;
import com.trazalga.api.dto.sernapesca.PescadorDto;
import com.trazalga.api.services.sync.SernapescaApiClient;

@Service
public class ArmadorService {

    private final SernapescaApiClient apiClient;

    public ArmadorService(SernapescaApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public List<ArmadorDto> obtenerArmadorPorRut(String rutCompleto) {
        if (rutCompleto == null || rutCompleto.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // Extraer RUT sin dígito verificador
        String rutStr = rutCompleto.contains("-") ? rutCompleto.split("-")[0] : rutCompleto;
        Integer rutInt;
        try {
            rutInt = Integer.parseInt(rutStr);
        } catch (NumberFormatException e) {
            return new ArrayList<>();
        }

        // 1. Obtener Pescador de Sernapesca
        PescadorDto pescador = apiClient.getPescadorPorRut(rutInt);
        if (pescador == null || pescador.getFolioRpa() == null) {
            return new ArrayList<>();
        }

        // 2. Obtener Embarcaciones usando tipoArmador = "N" (Natural)
        List<EmbarcacionDto> embarcaciones = apiClient.getEmbarcacionesPorArmador(pescador.getFolioRpa(), "N");

        // 3. Armar la respuesta (se envía como lista porque el frontend espera un arreglo)
        ArmadorDto armador = ArmadorDto.builder()
                .rut(rutCompleto) // mantener el formato que pide el front
                .nombre(pescador.getNombreCompleto())
                .embarcaciones(embarcaciones != null ? embarcaciones : new ArrayList<>())
                .build();

        List<ArmadorDto> respuesta = new ArrayList<>();
        respuesta.add(armador);
        return respuesta;
    }
}
