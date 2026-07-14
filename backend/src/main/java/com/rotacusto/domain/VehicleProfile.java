package com.rotacusto.domain;

import com.rotacusto.entity.VehicleModel;
import com.rotacusto.entity.enums.VehicleType;

public record VehicleProfile(
        VehicleType tipo,
        double consumoKmPorLitro,
        int numeroEixos,
        double custoDesgastePorKm,
        double precoCombustivelPorLitro) {

    /**
     * Deriva o perfil de cálculo a partir de um modelo do catálogo, usando o
     * consumo de estrada (mais representativo para viagens).
     */
    public static VehicleProfile fromModel(VehicleModel model, double precoCombustivelPorLitro) {
        return new VehicleProfile(
                model.getTipo(),
                model.getConsumoEstradaKmL(),
                model.getNumeroEixos(),
                model.getCustoDesgastePorKm(),
                precoCombustivelPorLitro);
    }
}
