package com.rotacusto.domain;

import com.rotacusto.entity.VehicleModel;
import com.rotacusto.entity.enums.TipoCombustivel;
import com.rotacusto.entity.enums.VehicleType;

/**
 * consumoPorUnidade/precoPorUnidade são genéricos por design: km/L e R$/L
 * para gasolina/etanol/diesel, km/kWh e R$/kWh para elétrico. A fórmula de
 * custo (distância / consumo * preço) não muda com a unidade.
 */
public record VehicleProfile(
        VehicleType tipo,
        TipoCombustivel tipoCombustivel,
        double consumoPorUnidade,
        int numeroEixos,
        double custoDesgastePorKm,
        double precoPorUnidade) {

    /**
     * Deriva o perfil de cálculo a partir de um modelo do catálogo, usando o
     * consumo de estrada (mais representativo para viagens) quando o veículo
     * é a combustão (o modelo já é uma linha específica de um combustível —
     * gasolina e etanol do mesmo carro são duas linhas separadas), ou o
     * consumo elétrico quando é elétrico.
     */
    public static VehicleProfile fromModel(VehicleModel model, double precoPorUnidade) {
        double consumo = model.getTipoCombustivel() == TipoCombustivel.ELETRICO
                ? model.getConsumoKmPorKWh()
                : model.getConsumoEstradaKmL();
        return new VehicleProfile(
                model.getTipo(),
                model.getTipoCombustivel(),
                consumo,
                model.getNumeroEixos(),
                model.getCustoDesgastePorKm(),
                precoPorUnidade);
    }
}
