package com.trazalga.api.services.sync;

import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.trazalga.api.dto.sernapesca.AmIdentificacionDto;
import com.trazalga.api.dto.sernapesca.ComboIntDto;
import com.trazalga.api.dto.sernapesca.DestinatarioDto;
import com.trazalga.api.dto.sernapesca.EmbarcacionDto;
import com.trazalga.api.dto.sernapesca.MetodoRecoleccionDto;
import com.trazalga.api.dto.sernapesca.PescadorDto;
import com.trazalga.api.dto.sernapesca.RegionTreeDto;
import com.trazalga.api.dto.sernapesca.SernapescaResponse;
import com.trazalga.api.dto.sernapesca.SernapescaSingleResponse;

/**
 * Cliente del API público de Sernapesca (https://data-api.sernapesca.cl).
 * Cada método devuelve la lista contenida en el campo {@code data} de la
 * respuesta estándar; ante error devuelve lista vacía (no lanza).
 */
@Component
public class SernapescaApiClient {

    private static final Logger log = LoggerFactory.getLogger(SernapescaApiClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public SernapescaApiClient(RestTemplate restTemplate,
            @Value("${sernapesca.api.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    /** Listado de regiones (codigo + valor). */
    public List<ComboIntDto> getRegiones() {
        return getList("/commons/regiones", new ParameterizedTypeReference<SernapescaResponse<ComboIntDto>>() {});
    }

    /** Jerarquía región -> comuna -> caleta. */
    public List<RegionTreeDto> getRegionComunaCaleta() {
        return getList("/commons/region-comuna-caleta",
                new ParameterizedTypeReference<SernapescaResponse<RegionTreeDto>>() {});
    }

    /** Métodos de recolección (-> tipos de extracción). */
    public List<MetodoRecoleccionDto> getMetodosRecoleccion() {
        return getList("/commons/metodo-recoleccion/buscar",
                new ParameterizedTypeReference<SernapescaResponse<MetodoRecoleccionDto>>() {});
    }

    /** Especies autorizadas para recolección de orilla (codigo + valor). */
    public List<ComboIntDto> getEspeciesAutorizadas() {
        return getList("/especies/recolector/autorizadas",
                new ParameterizedTypeReference<SernapescaResponse<ComboIntDto>>() {});
    }

    /** Embarcaciones por código de región. */
    public List<EmbarcacionDto> getEmbarcacionesPorRegion(int codigoRegion) {
        String url = UriComponentsBuilder.fromPath("/embarcacion/por-region")
                .queryParam("codigoRegion", codigoRegion).toUriString();
        return getList(url, new ParameterizedTypeReference<SernapescaResponse<EmbarcacionDto>>() {});
    }

    /** Recolectores de orilla / buzos por código de región. */
    public List<PescadorDto> getRecolectoresPorRegion(int codigoRegion) {
        String url = UriComponentsBuilder.fromPath("/pescadores/recolector/por-region")
                .queryParam("codigoRegion", codigoRegion).toUriString();
        return getList(url, new ParameterizedTypeReference<SernapescaResponse<PescadorDto>>() {});
    }

    /** Áreas de manejo (AMERB) por código de región. */
    public List<AmIdentificacionDto> getAreasManejoPorRegion(int cdRegion) {
        String url = UriComponentsBuilder.fromPath("/area-manejo/por-region")
                .queryParam("cdRegion", cdRegion).toUriString();
        return getList(url, new ParameterizedTypeReference<SernapescaResponse<AmIdentificacionDto>>() {});
    }

    /** Obtiene Pescador por RUT (sin dígito verificador). */
    public PescadorDto getPescadorPorRut(Integer rut) {
        String url = UriComponentsBuilder.fromPath("/pescadores/buscar")
                .queryParam("rut", rut).toUriString();
        return getObject(url, new ParameterizedTypeReference<SernapescaSingleResponse<PescadorDto>>() {});
    }

    /** Obtiene Embarcaciones de un armador por Folio RPA. */
    public List<EmbarcacionDto> getEmbarcacionesPorArmador(Integer rpaArmador, String tipoArmador) {
        String url = UriComponentsBuilder.fromPath("/embarcacion/por-armador")
                .queryParam("rpaArmador", rpaArmador)
                .queryParam("tipoArmador", tipoArmador).toUriString();
        return getList(url, new ParameterizedTypeReference<SernapescaResponse<EmbarcacionDto>>() {});
    }

    /** Plantas (destinatarios) por código de región. */
    public List<DestinatarioDto> getPlantasPorRegion(int cdRegion) {
        String url = UriComponentsBuilder.fromPath("/destinatarios/planta/por-region")
                .queryParam("codigoRegion", cdRegion).toUriString();
        return getList(url, new ParameterizedTypeReference<SernapescaResponse<DestinatarioDto>>() {});
    }

    /** Obtiene Pescador por Folio RPA. */
    public PescadorDto getPescadorPorFolioRpa(Integer folioRpa) {
        String url = UriComponentsBuilder.fromPath("/pescadores/" + folioRpa).toUriString();
        return getObject(url, new ParameterizedTypeReference<SernapescaSingleResponse<PescadorDto>>() {});
    }

    /** Obtiene Embarcaciones por Folio RPA. */
    public List<EmbarcacionDto> getEmbarcacionesPorFolioRpa(Integer folioRpa) {
        String url = UriComponentsBuilder.fromPath("/embarcacion/por-folio-rpa")
                .queryParam("folioRpa", folioRpa).toUriString();
        return getList(url, new ParameterizedTypeReference<SernapescaResponse<EmbarcacionDto>>() {});
    }

    private <T> List<T> getList(String path, ParameterizedTypeReference<SernapescaResponse<T>> typeRef) {
        String url = baseUrl + path;
        try {
            ResponseEntity<SernapescaResponse<T>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null, typeRef);
            SernapescaResponse<T> body = response.getBody();
            if (body == null || body.getData() == null) {
                return Collections.emptyList();
            }
            return body.getData();
        } catch (Exception e) {
            log.warn("Error consultando Sernapesca {}: {}", url, e.getMessage());
            return Collections.emptyList();
        }
    }

    private <T> T getObject(String path, ParameterizedTypeReference<SernapescaSingleResponse<T>> typeRef) {
        String url = baseUrl + path;
        try {
            ResponseEntity<SernapescaSingleResponse<T>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null, typeRef);
            SernapescaSingleResponse<T> body = response.getBody();
            if (body == null || body.getData() == null) {
                return null;
            }
            return body.getData();
        } catch (Exception e) {
            log.warn("Error consultando Sernapesca {}: {}", url, e.getMessage());
            return null;
        }
    }
}
