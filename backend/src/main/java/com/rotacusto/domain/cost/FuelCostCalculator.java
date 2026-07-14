package com.rotacusto.domain.cost;

import com.rotacusto.domain.VehicleProfile;

public final class FuelCostCalculator {

    private FuelCostCalculator() {
    }

    public static double calculate(double distanciaKm, VehicleProfile profile) {
        return distanciaKm / profile.consumoPorUnidade() * profile.precoPorUnidade();
    }
}
