package com.rotacusto.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.rotacusto.entity.TollPlaza;
import com.rotacusto.entity.enums.TipoCombustivel;
import com.rotacusto.entity.enums.VehicleType;

class TripCostCalculatorTest {

    // Intervalo bem largo pra desligar parada de lanche nos testes que não são sobre isso.
    private static final double NO_FOOD_STOP_INTERVAL = 1000.0;

    @Test
    void composesFuelAndWearIntoTotalWhenNoTollsCrossed() {
        // 500 km, 10 km/l, R$ 6,00/l -> combustível R$ 300,00; desgaste 500 * 0,35 = R$ 175,00
        VehicleProfile profile = new VehicleProfile(VehicleType.CARRO, TipoCombustivel.GASOLINA, 10.0, 2, 0.35, 6.0);

        TripCostBreakdown breakdown = TripCostCalculator.calculate(500.0, 360.0, profile, List.of(),
                NO_FOOD_STOP_INTERVAL, 25.0);

        assertEquals(500.0, breakdown.distanciaKm(), 0.001);
        assertEquals(360.0, breakdown.duracaoMin(), 0.001);
        assertEquals(300.0, breakdown.custoCombustivel(), 0.001);
        assertEquals(175.0, breakdown.custoDesgaste(), 0.001);
        assertEquals(0.0, breakdown.custoPedagio(), 0.001);
        assertEquals(0.0, breakdown.custoLanche(), 0.001);
        assertEquals(475.0, breakdown.total(), 0.001);
    }

    @Test
    void includesTollCostWhenPlazasAreCrossed() {
        VehicleProfile profile = new VehicleProfile(VehicleType.CARRO, TipoCombustivel.GASOLINA, 10.0, 2, 0.35, 6.0);

        TollPlaza praca1 = new TollPlaza();
        praca1.setTarifaPorEixo(5.0); // carro (2 eixos) -> R$ 10,00
        TollPlaza praca2 = new TollPlaza();
        praca2.setTarifaPorEixo(4.5); // carro (2 eixos) -> R$ 9,00

        TripCostBreakdown breakdown = TripCostCalculator.calculate(500.0, 360.0, profile, List.of(praca1, praca2),
                NO_FOOD_STOP_INTERVAL, 25.0);

        assertEquals(19.0, breakdown.custoPedagio(), 0.001);
        assertEquals(300.0 + 175.0 + 19.0, breakdown.total(), 0.001);
    }

    @Test
    void includesFoodStopCostForLongTrips() {
        VehicleProfile profile = new VehicleProfile(VehicleType.CARRO, TipoCombustivel.GASOLINA, 10.0, 2, 0.35, 6.0);

        // 400 min = 6h40 de viagem, parada a cada 2h, R$ 30 por parada -> floor(6.67/2) = 3 paradas = R$ 90
        TripCostBreakdown breakdown = TripCostCalculator.calculate(500.0, 400.0, profile, List.of(), 2.0, 30.0);

        assertEquals(90.0, breakdown.custoLanche(), 0.001);
        assertEquals(300.0 + 175.0 + 90.0, breakdown.total(), 0.001);
    }
}
