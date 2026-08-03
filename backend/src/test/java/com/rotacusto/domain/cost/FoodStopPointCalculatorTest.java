package com.rotacusto.domain.cost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.rotacusto.domain.Coordinates;
import com.rotacusto.domain.RouteStep;

class FoodStopPointCalculatorTest {

    @Test
    void returnsEmptyListWhenNoStopsNeeded() {
        List<RouteStep> passos = List.of(new RouteStep("Siga em frente", 1000, 3600, 0, 1));
        List<Coordinates> geometria = List.of(new Coordinates(0, 0), new Coordinates(1, 1));

        assertTrue(FoodStopPointCalculator.computeStopPoints(passos, geometria, 0, 3.0).isEmpty());
    }

    @Test
    void findsSingleStopPointAtIntervalCrossing() {
        // 2 passos de 2h cada (7200s) — a marca de 3h (10800s) cai dentro do 2º passo.
        List<RouteStep> passos = List.of(
                new RouteStep("Passo 1", 100_000, 7200, 0, 1),
                new RouteStep("Passo 2", 100_000, 7200, 1, 2));
        List<Coordinates> geometria = List.of(new Coordinates(0, 0), new Coordinates(1, 1), new Coordinates(2, 2));

        List<Coordinates> pontos = FoodStopPointCalculator.computeStopPoints(passos, geometria, 1, 3.0);

        assertEquals(1, pontos.size());
        assertEquals(new Coordinates(1, 1), pontos.get(0));
    }

    @Test
    void findsMultipleStopPointsInOrder() {
        // 4 passos de 2h cada (8h total), intervalo 3h -> paradas nas marcas de 3h e 6h.
        List<RouteStep> passos = List.of(
                new RouteStep("Passo 1", 100_000, 7200, 0, 1),
                new RouteStep("Passo 2", 100_000, 7200, 1, 2),
                new RouteStep("Passo 3", 100_000, 7200, 2, 3),
                new RouteStep("Passo 4", 100_000, 7200, 3, 4));
        List<Coordinates> geometria = List.of(
                new Coordinates(0, 0), new Coordinates(1, 1), new Coordinates(2, 2),
                new Coordinates(3, 3), new Coordinates(4, 4));

        List<Coordinates> pontos = FoodStopPointCalculator.computeStopPoints(passos, geometria, 2, 3.0);

        assertEquals(2, pontos.size());
        assertEquals(new Coordinates(1, 1), pontos.get(0)); // marca de 3h (10800s) cai no passo 2 (wayPointInicio=1)
        assertEquals(new Coordinates(2, 2), pontos.get(1)); // marca de 6h (21600s) bate exato no fim do passo 3 (wayPointInicio=2)
    }

    @Test
    void clampsWayPointIndexToGeometryBounds() {
        List<RouteStep> passos = List.of(new RouteStep("Passo único", 100_000, 10800, 5, 6));
        List<Coordinates> geometria = List.of(new Coordinates(0, 0), new Coordinates(1, 1));

        List<Coordinates> pontos = FoodStopPointCalculator.computeStopPoints(passos, geometria, 1, 3.0);

        assertEquals(1, pontos.size());
        assertEquals(new Coordinates(1, 1), pontos.get(0));
    }
}
