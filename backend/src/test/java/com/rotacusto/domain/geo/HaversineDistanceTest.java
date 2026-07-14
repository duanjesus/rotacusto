package com.rotacusto.domain.geo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.rotacusto.domain.Coordinates;

class HaversineDistanceTest {

    @Test
    void samePointHasZeroDistance() {
        Coordinates p = new Coordinates(-22.9068, -43.1729);
        assertEquals(0.0, HaversineDistance.km(p, p), 0.001);
    }

    @Test
    void computesKnownDistanceBetweenRioAndSaoPauloWithinTolerance() {
        // Distância em linha reta Rio-SP é ~357km (rota rodoviária é maior, ~430km)
        Coordinates rio = new Coordinates(-22.9068, -43.1729);
        Coordinates saoPaulo = new Coordinates(-23.5505, -46.6333);

        double distanceKm = HaversineDistance.km(rio, saoPaulo);

        assertEquals(357.0, distanceKm, 10.0);
    }
}
