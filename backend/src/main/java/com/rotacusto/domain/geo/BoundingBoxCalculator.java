package com.rotacusto.domain.geo;

import java.util.List;

import com.rotacusto.domain.Coordinates;

public final class BoundingBoxCalculator {

    private BoundingBoxCalculator() {
    }

    /** Retorna [minLat, minLon, maxLat, maxLon] com uma margem em graus. */
    public static double[] compute(List<Coordinates> route, double paddingDegrees) {
        double minLat = Double.MAX_VALUE;
        double maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE;
        double maxLon = -Double.MAX_VALUE;
        for (Coordinates c : route) {
            minLat = Math.min(minLat, c.lat());
            maxLat = Math.max(maxLat, c.lat());
            minLon = Math.min(minLon, c.lon());
            maxLon = Math.max(maxLon, c.lon());
        }
        return new double[] {
                minLat - paddingDegrees,
                minLon - paddingDegrees,
                maxLat + paddingDegrees,
                maxLon + paddingDegrees
        };
    }
}
