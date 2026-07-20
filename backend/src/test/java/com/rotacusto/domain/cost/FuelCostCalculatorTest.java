package com.rotacusto.domain.cost;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.rotacusto.domain.RouteStep;
import com.rotacusto.domain.VehicleProfile;
import com.rotacusto.entity.enums.TipoCombustivel;
import com.rotacusto.entity.enums.VehicleType;

class FuelCostCalculatorTest {

    @Test
    void calculatesFuelCostFromDistanceConsumptionAndPrice() {
        // 500 km, 10 km/l, R$ 6,00/l -> 50 litros * 6,00 = R$ 300,00
        VehicleProfile profile = new VehicleProfile(VehicleType.CARRO, TipoCombustivel.GASOLINA, 10.0, 2, 0.35, 6.0);

        double cost = FuelCostCalculator.calculate(500.0, profile, List.of());

        assertEquals(300.0, cost, 0.001);
    }

    @Test
    void zeroDistanceCostsNothing() {
        VehicleProfile profile = new VehicleProfile(VehicleType.CARRO, TipoCombustivel.GASOLINA, 10.0, 2, 0.35, 6.0);

        assertEquals(0.0, FuelCostCalculator.calculate(0.0, profile, List.of()), 0.001);
    }

    @Test
    void calculatesElectricCostFromDistanceConsumptionAndPricePerKWh() {
        // 300 km, 6 km/kWh, R$ 0,90/kWh -> 50 kWh * 0,90 = R$ 45,00
        VehicleProfile profile = new VehicleProfile(VehicleType.CARRO, TipoCombustivel.ELETRICO, 6.0, 2, 0.4, 0.90);

        double cost = FuelCostCalculator.calculate(300.0, profile, List.of());

        assertEquals(45.0, cost, 0.001);
    }

    @Test
    void blendsCityAndHighwayConsumptionAcrossRouteSteps() {
        // cidade 8 km/l, estrada (consumoPorUnidade) 12 km/l, R$ 6,00/l.
        VehicleProfile profile = new VehicleProfile(VehicleType.CARRO, TipoCombustivel.GASOLINA, 12.0, 2, 0.35, 6.0,
                8.0);

        // Trecho 1: 10km em 360s -> 100km/h, é "rodovia" (>= 60km/h).
        RouteStep rodovia = new RouteStep("Siga pela BR", 10000.0, 360.0, 0, 1);
        // Trecho 2: 5km em 900s -> 20km/h, é "cidade" (< 60km/h).
        RouteStep cidade = new RouteStep("Vire na rua X", 5000.0, 900.0, 1, 2);

        // Rodovia: 10km / 12km/l * 6,00 = R$ 5,00. Cidade: 5km / 8km/l * 6,00 = R$ 3,75.
        double cost = FuelCostCalculator.calculate(15.0, profile, List.of(rodovia, cidade));

        assertEquals(8.75, cost, 0.001);
    }

    @Test
    void fallsBackToSingleConsumptionWhenProfileHasNoCityConsumption() {
        // Perfil manual (construtor de 6 args) não tem consumo de cidade — mesmo com
        // passos de rota populados, não dá pra fazer blend, cai no cálculo antigo.
        VehicleProfile profile = new VehicleProfile(VehicleType.CARRO, TipoCombustivel.GASOLINA, 10.0, 2, 0.35, 6.0);
        RouteStep passo = new RouteStep("Vire na rua X", 5000.0, 900.0, 0, 1);

        double cost = FuelCostCalculator.calculate(5.0, profile, List.of(passo));

        assertEquals(3.0, cost, 0.001); // 5km / 10km/l * 6,00
    }

    @Test
    void fallsBackToSingleConsumptionWhenNoRouteStepsAvailable() {
        // Consumo de cidade existe, mas sem passos da rota não dá pra classificar
        // trecho nenhum — cai no cálculo antigo, usando consumoPorUnidade sozinho.
        VehicleProfile profile = new VehicleProfile(VehicleType.CARRO, TipoCombustivel.GASOLINA, 12.0, 2, 0.35, 6.0,
                8.0);

        double cost = FuelCostCalculator.calculate(15.0, profile, List.of());

        assertEquals(7.5, cost, 0.001); // 15km / 12km/l * 6,00
    }
}
