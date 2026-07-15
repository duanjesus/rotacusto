package com.rotacusto.domain;

import java.util.List;

import com.rotacusto.domain.cost.FoodStopCostCalculator;
import com.rotacusto.domain.cost.FuelCostCalculator;
import com.rotacusto.domain.cost.TollCostCalculator;
import com.rotacusto.domain.cost.WearCostCalculator;
import com.rotacusto.entity.TollPlaza;

/**
 * Compõe o custo total de uma viagem.
 */
public final class TripCostCalculator {

    private TripCostCalculator() {
    }

    public static TripCostBreakdown calculate(double distanciaKm, double duracaoMin, VehicleProfile profile,
            List<TollPlaza> praçasCruzadas, double foodStopIntervalHours, double foodStopAverageCost) {
        double custoCombustivel = FuelCostCalculator.calculate(distanciaKm, profile);
        double custoDesgaste = WearCostCalculator.calculate(distanciaKm, profile);
        double custoPedagio = TollCostCalculator.calculate(praçasCruzadas, profile);
        double custoLanche = FoodStopCostCalculator.calculate(duracaoMin, foodStopIntervalHours, foodStopAverageCost);
        double total = custoCombustivel + custoDesgaste + custoPedagio + custoLanche;

        return new TripCostBreakdown(distanciaKm, duracaoMin, custoCombustivel, custoDesgaste, custoPedagio,
                custoLanche, total);
    }
}
