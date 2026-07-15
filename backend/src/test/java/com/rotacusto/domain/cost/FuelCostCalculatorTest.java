package com.rotacusto.domain.cost;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.rotacusto.domain.VehicleProfile;
import com.rotacusto.entity.enums.TipoCombustivel;
import com.rotacusto.entity.enums.VehicleType;

class FuelCostCalculatorTest {

    @Test
    void calculatesFuelCostFromDistanceConsumptionAndPrice() {
        // 500 km, 10 km/l, R$ 6,00/l -> 50 litros * 6,00 = R$ 300,00
        VehicleProfile profile = new VehicleProfile(VehicleType.CARRO, TipoCombustivel.GASOLINA, 10.0, 2, 0.35, 6.0);

        double cost = FuelCostCalculator.calculate(500.0, profile);

        assertEquals(300.0, cost, 0.001);
    }

    @Test
    void zeroDistanceCostsNothing() {
        VehicleProfile profile = new VehicleProfile(VehicleType.CARRO, TipoCombustivel.GASOLINA, 10.0, 2, 0.35, 6.0);

        assertEquals(0.0, FuelCostCalculator.calculate(0.0, profile), 0.001);
    }

    @Test
    void calculatesElectricCostFromDistanceConsumptionAndPricePerKWh() {
        // 300 km, 6 km/kWh, R$ 0,90/kWh -> 50 kWh * 0,90 = R$ 45,00
        VehicleProfile profile = new VehicleProfile(VehicleType.CARRO, TipoCombustivel.ELETRICO, 6.0, 2, 0.4, 0.90);

        double cost = FuelCostCalculator.calculate(300.0, profile);

        assertEquals(45.0, cost, 0.001);
    }
}
