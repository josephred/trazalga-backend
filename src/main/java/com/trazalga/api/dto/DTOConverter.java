package com.trazalga.api.dto;

import com.trazalga.api.models.RegionModel;
import com.trazalga.api.models.UsuarioModel;

public class DTOConverter {
    
    public static UsuarioDTO convertirAUsuarioDTO(UsuarioModel usuarioModel) {
        UsuarioDTO usuarioDTO = new UsuarioDTO();
        usuarioDTO.setId(usuarioModel.getId());
        usuarioDTO.setRut(usuarioModel.getRut());
        usuarioDTO.setNombres(usuarioModel.getNombres());
        usuarioDTO.setApellidop(usuarioModel.getApellidop());
        usuarioDTO.setApellidom(usuarioModel.getApellidom());
        usuarioDTO.setCorreo(usuarioModel.getCorreo());
        usuarioDTO.setFechaCreacion(usuarioModel.getFecha_creacion());
        usuarioDTO.setEstado(usuarioModel.getEstado());
        usuarioDTO.setClave(usuarioModel.getClave());

        // Convertir ComunaModel a ComunaDTO
        if (usuarioModel.getComuna() != null) {
            ComunaDTO comunaDTO = new ComunaDTO();
            comunaDTO.setId(usuarioModel.getComuna().getId());
            comunaDTO.setNombre(usuarioModel.getComuna().getNombre());

            // Convertir RegionModel a RegionDTO si la región está presente
            if (usuarioModel.getComuna().getRegion() != null) {
                RegionModel regionModel = usuarioModel.getComuna().getRegion();
                RegionDTO regionDTO = new RegionDTO();
                regionDTO.setId(regionModel.getId());
                regionDTO.setNombre(regionModel.getNombre());

                comunaDTO.setRegion(regionDTO);
            }
            usuarioDTO.setComuna(comunaDTO);
        }

        // Convertir PerfilModel a PerfilDTO
        if (usuarioModel.getPerfil() != null) {
            PerfilDTO perfilDTO = new PerfilDTO();
            perfilDTO.setId(usuarioModel.getPerfil().getId());
            perfilDTO.setNombre(usuarioModel.getPerfil().getNombre());
            perfilDTO.setDescripcion(usuarioModel.getPerfil().getDescripcion());

            usuarioDTO.setPerfil(perfilDTO);
        }
        return usuarioDTO;
    }
}
