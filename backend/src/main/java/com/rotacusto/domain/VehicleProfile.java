package com.rotacusto.domain;

import com.rotacusto.entity.VehicleModel;
import com.rotacusto.entity.enums.TipoCombustivel;
import com.rotacusto.entity.enums.VehicleType;

/**
 * consumoPorUnidade/precoPorUnidade são genéricos por design: km/L e R$/L
 * para gasolina/etanol/diesel, km/kWh e R$/kWh para elétrico. A fórmula de
 * custo (distância / consumo * preço) não muda com a unidade.
 *
 * <p>{@code consumoCidadePorUnidade} é nullable — populado só pra veículos a
 * combustão vindos do catálogo (permite {@link
 * com.rotacusto.domain.cost.FuelCostCalculator} misturar consumo cidade/
 * rodovia conforme o trecho real da rota); fica {@code null} pra elétrico
 * (só tem consumo único, {@code consumoKmPorKWh}) e pra perfil MANUAL (só
 * tem {@code consumoPorUnidade} — sem distinção cidade/estrada). Nesses
 * casos o cálculo cai no comportamento antigo, só com
 * {@code consumoPorUnidade}.
 */
public record VehicleProfile(
        VehicleType tipo,
        TipoCombustivel tipoCombustivel,
        double consumoPorUnidade,
        int numeroEixos,
        double custoDesgastePorKm,
        double precoPorUnidade,
        Double consumoCidadePorUnidade) {

    /**
     * Construtor de compatibilidade (sem consumo de cidade) — usado por todo
     * call site que não tem esse dado (perfil manual) ou não testa blend de
     * consumo, sem precisar tocar em nenhum deles.
     */
    public VehicleProfile(VehicleType tipo, TipoCombustivel tipoCombustivel, double consumoPorUnidade,
            int numeroEixos, double custoDesgastePorKm, double precoPorUnidade) {
        this(tipo, tipoCombustivel, consumoPorUnidade, numeroEixos, custoDesgastePorKm, precoPorUnidade, null);
    }

    /**
     * Deriva o perfil de cálculo a partir de um modelo do catálogo, usando o
     * consumo de estrada (mais representativo para viagens) quando o veículo
     * é a combustão (o modelo já é uma linha específica de um combustível —
     * gasolina e etanol do mesmo carro são duas linhas separadas), ou o
     * consumo elétrico quando é elétrico. Consumo de cidade só é preenchido
     * pra combustão — é o que habilita o blend por trecho.
     */
    public static VehicleProfile fromModel(VehicleModel model, double precoPorUnidade) {
        boolean eletrico = model.getTipoCombustivel() == TipoCombustivel.ELETRICO;
        double consumo = eletrico ? model.getConsumoKmPorKWh() : model.getConsumoEstradaKmL();
        Double consumoCidade = eletrico ? null : model.getConsumoCidadeKmL();
        return new VehicleProfile(
                model.getTipo(),
                model.getTipoCombustivel(),
                consumo,
                model.getNumeroEixos(),
                model.getCustoDesgastePorKm(),
                precoPorUnidade,
                consumoCidade);
    }
}
