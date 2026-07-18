package com.rotacusto.domain.cost;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.rotacusto.domain.VehicleProfile;
import com.rotacusto.entity.TollPlaza;
import com.rotacusto.entity.enums.TipoCombustivel;
import com.rotacusto.entity.enums.VehicleType;

class TollCostCalculatorTest {

    // Terça-feira e sábado quaisquer — só o dia da semana importa pro cálculo.
    private static final LocalDate UMA_TERCA_FEIRA = LocalDate.of(2026, 7, 14);
    private static final LocalDate UM_SABADO = LocalDate.of(2026, 7, 18);
    private static final LocalDate UM_DOMINGO = LocalDate.of(2026, 7, 19);

    @Test
    void carPaysTarifaPorEixoTimesTwoAxlesForEachCrossedPlaza() {
        VehicleProfile carro = new VehicleProfile(VehicleType.CARRO, TipoCombustivel.GASOLINA, 10.0, 2, 0.35, 6.0);
        TollPlaza p1 = new TollPlaza();
        p1.setTarifaPorEixo(5.0);
        TollPlaza p2 = new TollPlaza();
        p2.setTarifaPorEixo(4.5);

        double total = TollCostCalculator.calculate(List.of(p1, p2), carro, UMA_TERCA_FEIRA);

        assertEquals(10.0 + 9.0, total, 0.001);
    }

    @Test
    void motorcycleUsesFixedFareWhenAvailableInsteadOfPerAxleFormula() {
        VehicleProfile moto = new VehicleProfile(VehicleType.MOTO, TipoCombustivel.GASOLINA, 25.0, 2, 0.15, 6.0);
        TollPlaza p1 = new TollPlaza();
        p1.setTarifaPorEixo(5.0);
        p1.setTarifaMoto(3.0);

        double total = TollCostCalculator.calculate(List.of(p1), moto, UMA_TERCA_FEIRA);

        assertEquals(3.0, total, 0.001);
    }

    @Test
    void motorcycleFallsBackToPerAxleFormulaWhenNoFixedFarePublished() {
        VehicleProfile moto = new VehicleProfile(VehicleType.MOTO, TipoCombustivel.GASOLINA, 25.0, 2, 0.15, 6.0);
        TollPlaza p1 = new TollPlaza();
        p1.setTarifaPorEixo(5.0);
        // sem tarifaMoto definida

        double total = TollCostCalculator.calculate(List.of(p1), moto, UMA_TERCA_FEIRA);

        assertEquals(10.0, total, 0.001);
    }

    @Test
    void emptyPlazaListCostsNothing() {
        VehicleProfile carro = new VehicleProfile(VehicleType.CARRO, TipoCombustivel.GASOLINA, 10.0, 2, 0.35, 6.0);
        assertEquals(0.0, TollCostCalculator.calculate(List.of(), carro, UMA_TERCA_FEIRA), 0.001);
    }

    @Test
    void usesWeekdayTariffOnATuesday() {
        VehicleProfile carro = new VehicleProfile(VehicleType.CARRO, TipoCombustivel.GASOLINA, 10.0, 2, 0.35, 6.0);
        TollPlaza viaLagos = new TollPlaza();
        viaLagos.setTarifaPorEixo(9.20);
        viaLagos.setTarifaPorEixoFimDeSemana(15.30);

        double total = TollCostCalculator.calculate(List.of(viaLagos), carro, UMA_TERCA_FEIRA);

        assertEquals(18.40, total, 0.001);
    }

    @Test
    void usesWeekendTariffOnASaturday() {
        VehicleProfile carro = new VehicleProfile(VehicleType.CARRO, TipoCombustivel.GASOLINA, 10.0, 2, 0.35, 6.0);
        TollPlaza viaLagos = new TollPlaza();
        viaLagos.setTarifaPorEixo(9.20);
        viaLagos.setTarifaPorEixoFimDeSemana(15.30);

        double total = TollCostCalculator.calculate(List.of(viaLagos), carro, UM_SABADO);

        assertEquals(30.60, total, 0.001);
    }

    @Test
    void usesWeekendTariffOnASunday() {
        VehicleProfile carro = new VehicleProfile(VehicleType.CARRO, TipoCombustivel.GASOLINA, 10.0, 2, 0.35, 6.0);
        TollPlaza viaLagos = new TollPlaza();
        viaLagos.setTarifaPorEixo(9.20);
        viaLagos.setTarifaPorEixoFimDeSemana(15.30);

        double total = TollCostCalculator.calculate(List.of(viaLagos), carro, UM_DOMINGO);

        assertEquals(30.60, total, 0.001);
    }

    @Test
    void weekendFallsBackToWeekdayTariffWhenNoWeekendTariffPublished() {
        VehicleProfile carro = new VehicleProfile(VehicleType.CARRO, TipoCombustivel.GASOLINA, 10.0, 2, 0.35, 6.0);
        TollPlaza p1 = new TollPlaza();
        p1.setTarifaPorEixo(5.0);
        // sem tarifaPorEixoFimDeSemana definida — mesma praça imensa maioria do dataset

        double total = TollCostCalculator.calculate(List.of(p1), carro, UM_SABADO);

        assertEquals(10.0, total, 0.001);
    }
}
