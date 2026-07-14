package com.rotacusto.domain.cost;

import com.rotacusto.domain.VehicleProfile;

public final class WearCostCalculator {

    private WearCostCalculator() {
    }

    public static double calculate(double distanciaKm, VehicleProfile profile) {
        return distanciaKm * profile.custoDesgastePorKm();
    }
}
