package com.rotacusto.mapper;

import org.springframework.stereotype.Component;

import com.rotacusto.dto.response.VehicleModelResponseDTO;
import com.rotacusto.entity.VehicleModel;

@Component
public class VehicleModelMapper {

    public VehicleModelResponseDTO toResponseDTO(VehicleModel model) {
        return new VehicleModelResponseDTO(
                model.getId(),
                model.getMarca(),
                model.getModelo(),
                model.getAno(),
                model.getTipo(),
                model.getTipoCombustivel(),
                model.getConsumoCidadeKmL(),
                model.getConsumoEstradaKmL(),
                model.getConsumoKmPorKWh(),
                model.getNumeroEixos(),
                model.getCustoDesgastePorKm());
    }
}
