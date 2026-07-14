package com.rotacusto.domain;

import com.rotacusto.entity.VehicleModel;
import com.rotacusto.entity.enums.TipoEnergia;
import com.rotacusto.entity.enums.VehicleType;

/**
 * consumoPorUnidade/precoPorUnidade são genéricos por design: km/L e R$/L
 * para COMBUSTAO, km/kWh e R$/kWh para ELETRICO. A fórmula de custo
 * (distância / consumo * preço) não muda com a unidade.
 */
public record VehicleProfile(
        VehicleType tipo,
        TipoEnergia tipoEnergia,
        double consumoPorUnidade,
        int numeroEixos,
        double custoDesgastePorKm,
        double precoPorUnidade) {

    /**
     * Deriva o perfil de cálculo a partir de um modelo do catálogo, usando o
     * consumo de estrada (mais representativo para viagens) quando o veículo
     * é a combustão, ou o consumo elétrico quando é elétrico.
     */
    public static VehicleProfile fromModel(VehicleModel model, double precoPorUnidade) {
        double consumo = model.getTipoEnergia() == TipoEnergia.ELETRICO
                ? model.getConsumoKmPorKWh()
                : model.getConsumoEstradaKmL();
        return new VehicleProfile(
                model.getTipo(),
                model.getTipoEnergia(),
                consumo,
                model.getNumeroEixos(),
                model.getCustoDesgastePorKm(),
                precoPorUnidade);
    }
}
