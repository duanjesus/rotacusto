package com.rotacusto.domain.geo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.rotacusto.domain.Coordinates;

class BearingTest {

    @Test
    void bearingDueNorthIsZero() {
        Coordinates from = new Coordinates(0.0, 0.0);
        Coordinates to = new Coordinates(1.0, 0.0);
        assertEquals(0.0, Bearing.degrees(from, to), 1.0);
    }

    @Test
    void bearingDueEastIsNinety() {
        Coordinates from = new Coordinates(0.0, 0.0);
        Coordinates to = new Coordinates(0.0, 1.0);
        assertEquals(90.0, Bearing.degrees(from, to), 1.0);
    }

    @Test
    void bearingDueSouthIs180() {
        Coordinates from = new Coordinates(0.0, 0.0);
        Coordinates to = new Coordinates(-1.0, 0.0);
        assertEquals(180.0, Bearing.degrees(from, to), 1.0);
    }

    @Test
    void bearingDueWestIs270() {
        Coordinates from = new Coordinates(0.0, 0.0);
        Coordinates to = new Coordinates(0.0, -1.0);
        assertEquals(270.0, Bearing.degrees(from, to), 1.0);
    }

    @Test
    void angularDifferenceHandlesWraparound() {
        // 10° e 350° são só 20° de distância "dando a volta" por 0°, não 340°.
        assertEquals(20.0, Bearing.angularDifference(10.0, 350.0), 0.5);
        assertEquals(180.0, Bearing.angularDifference(0.0, 180.0), 0.5);
        assertEquals(90.0, Bearing.angularDifference(0.0, 90.0), 0.5);
    }
}
