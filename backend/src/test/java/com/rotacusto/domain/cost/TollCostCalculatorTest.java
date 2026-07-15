package com.rotacusto.domain.cost;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.rotacusto.domain.VehicleProfile;
import com.rotacusto.entity.TollPlaza;
import com.rotacusto.entity.enums.TipoCombustivel;
import com.rotacusto.entity.enums.VehicleType;

class TollCostCalculatorTest {

    @Test
    void carPaysTarifaPorEixoTimesTwoAxlesForEachCrossedPlaza() {
        VehicleProfile carro = new VehicleProfile(VehicleType.CARRO, TipoCombustivel.GASOLINA, 10.0, 2, 0.35, 6.0);
        TollPlaza p1 = new TollPlaza();
        p1.setTarifaPorEixo(5.0);
        TollPlaza p2 = new TollPlaza();
        p2.setTarifaPorEixo(4.5);

        double total = TollCostCalculator.calculate(List.of(p1, p2), carro);

        assertEquals(10.0 + 9.0, total, 0.001);
    }

    @Test
    void motorcycleUsesFixedFareWhenAvailableInsteadOfPerAxleFormula() {
        VehicleProfile moto = new VehicleProfile(VehicleType.MOTO, TipoCombustivel.GASOLINA, 25.0, 2, 0.15, 6.0);
        TollPlaza p1 = new TollPlaza();
        p1.setTarifaPorEixo(5.0);
        p1.setTarifaMoto(3.0);

        double total = TollCostCalculator.calculate(List.of(p1), moto);

        assertEquals(3.0, total, 0.001);
    }

    @Test
    void motorcycleFallsBackToPerAxleFormulaWhenNoFixedFarePublished() {
        VehicleProfile moto = new VehicleProfile(VehicleType.MOTO, TipoCombustivel.GASOLINA, 25.0, 2, 0.15, 6.0);
        TollPlaza p1 = new TollPlaza();
        p1.setTarifaPorEixo(5.0);
        // sem tarifaMoto definida

        double total = TollCostCalculator.calculate(List.of(p1), moto);

        assertEquals(10.0, total, 0.001);
    }

    @Test
    void emptyPlazaListCostsNothing() {
        VehicleProfile carro = new VehicleProfile(VehicleType.CARRO, TipoCombustivel.GASOLINA, 10.0, 2, 0.35, 6.0);
        assertEquals(0.0, TollCostCalculator.calculate(List.of(), carro), 0.001);
    }
}
