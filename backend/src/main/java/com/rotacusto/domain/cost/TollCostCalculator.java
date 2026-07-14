package com.rotacusto.domain.cost;

import java.util.List;

import com.rotacusto.domain.VehicleProfile;
import com.rotacusto.entity.TollPlaza;
import com.rotacusto.entity.enums.VehicleType;

public final class TollCostCalculator {

    private TollCostCalculator() {
    }

    public static double calculate(List<TollPlaza> praçasCruzadas, VehicleProfile profile) {
        double total = 0.0;
        for (TollPlaza praca : praçasCruzadas) {
            total += tarifaFor(praca, profile);
        }
        return total;
    }

    private static double tarifaFor(TollPlaza praca, VehicleProfile profile) {
        if (profile.tipo() == VehicleType.MOTO && praca.getTarifaMoto() != null) {
            return praca.getTarifaMoto();
        }
        return praca.getTarifaPorEixo() * profile.numeroEixos();
    }
}
