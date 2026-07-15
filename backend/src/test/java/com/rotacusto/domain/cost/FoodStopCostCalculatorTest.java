package com.rotacusto.domain.cost;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FoodStopCostCalculatorTest {

    @Test
    void tripShorterThanIntervalHasNoStops() {
        // 90 min de viagem, intervalo de 3h -> nem completa 1 intervalo
        assertEquals(0.0, FoodStopCostCalculator.calculate(90.0, 3.0, 25.0), 0.001);
    }

    @Test
    void tripExactlyAtIntervalCountsOneStop() {
        // 180 min = 3h exatas, intervalo de 3h -> 1 parada
        assertEquals(25.0, FoodStopCostCalculator.calculate(180.0, 3.0, 25.0), 0.001);
    }

    @Test
    void roundsDownPartialIntervals() {
        // 400 min = 6h40, intervalo de 2h -> floor(6.67 / 2) = 3 paradas
        assertEquals(75.0, FoodStopCostCalculator.calculate(400.0, 2.0, 25.0), 0.001);
    }

    @Test
    void zeroOrNegativeIntervalIsTreatedAsDisabledInsteadOfCrashing() {
        // Configuração inválida (ou "desligado") não deveria virar Infinity/NaN.
        assertEquals(0.0, FoodStopCostCalculator.calculate(600.0, 0.0, 25.0), 0.001);
        assertEquals(0.0, FoodStopCostCalculator.calculate(600.0, -1.0, 25.0), 0.001);
    }
}
