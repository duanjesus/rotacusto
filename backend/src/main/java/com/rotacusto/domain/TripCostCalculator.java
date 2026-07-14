package com.rotacusto.domain;

import java.util.List;

import com.rotacusto.domain.cost.FuelCostCalculator;
import com.rotacusto.domain.cost.TollCostCalculator;
import com.rotacusto.domain.cost.WearCostCalculator;
import com.rotacusto.entity.TollPlaza;

/**
 * Compõe o custo total de uma viagem. Parada para lanche (Fase 4) ainda não
 * entra no MVP — fica em zero até ser implementada.
 */
public final class TripCostCalculator {

    private TripCostCalculator() {
    }

    public static TripCostBreakdown calculate(double distanciaKm, double duracaoMin, VehicleProfile profile,
            List<TollPlaza> praçasCruzadas) {
        double custoCombustivel = FuelCostCalculator.calculate(distanciaKm, profile);
        double custoDesgaste = WearCostCalculator.calculate(distanciaKm, profile);
        double custoPedagio = TollCostCalculator.calculate(praçasCruzadas, profile);
        double custoLanche = 0.0;
        double total = custoCombustivel + custoDesgaste + custoPedagio + custoLanche;

        return new TripCostBreakdown(distanciaKm, duracaoMin, custoCombustivel, custoDesgaste, custoPedagio,
                custoLanche, total);
    }
}
