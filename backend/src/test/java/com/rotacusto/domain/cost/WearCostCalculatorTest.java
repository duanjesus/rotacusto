package com.rotacusto.domain.cost;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.rotacusto.domain.VehicleProfile;
import com.rotacusto.entity.enums.TipoEnergia;
import com.rotacusto.entity.enums.VehicleType;

class WearCostCalculatorTest {

    @Test
    void calculatesWearCostFromDistanceAndCostPerKm() {
        // 500 km * R$ 0,35/km = R$ 175,00
        VehicleProfile profile = new VehicleProfile(VehicleType.CARRO, TipoEnergia.COMBUSTAO, 10.0, 2, 0.35, 6.0);

        double cost = WearCostCalculator.calculate(500.0, profile);

        assertEquals(175.0, cost, 0.001);
    }
}
