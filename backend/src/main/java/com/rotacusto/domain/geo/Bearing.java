package com.rotacusto.domain.geo;

import com.rotacusto.domain.Coordinates;

/** Direção (rumo, em graus 0-360, 0=norte) entre dois pontos, fórmula padrão de navegação. */
public final class Bearing {

    private Bearing() {
    }

    public static double degrees(Coordinates from, Coordinates to) {
        double lat1 = Math.toRadians(from.lat());
        double lat2 = Math.toRadians(to.lat());
        double deltaLon = Math.toRadians(to.lon() - from.lon());

        double y = Math.sin(deltaLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(deltaLon);
        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360) % 360;
    }

    /** Diferença angular absoluta entre dois rumos, sempre entre 0 e 180 graus. */
    public static double angularDifference(double bearing1, double bearing2) {
        double diff = Math.abs(bearing1 - bearing2) % 360;
        return diff > 180 ? 360 - diff : diff;
    }
}
