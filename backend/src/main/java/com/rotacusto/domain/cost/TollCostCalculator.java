package com.rotacusto.domain.cost;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import com.rotacusto.domain.VehicleProfile;
import com.rotacusto.entity.TollPlaza;
import com.rotacusto.entity.enums.VehicleType;

public final class TollCostCalculator {

    private TollCostCalculator() {
    }

    /**
     * @param dataViagem data usada pra decidir se a tarifa de fim de semana/feriado se
     *                   aplica (ver {@link TollPlaza#getTarifaPorEixoFimDeSemana()}) —
     *                   passada explicitamente (não {@code LocalDate.now()} interno) pra
     *                   manter a função pura e testável com datas sintéticas, mesmo
     *                   padrão já usado em outros detectores do projeto (ex.
     *                   {@code TrafficDetector} no Flutter). Só sábado/domingo contam
     *                   como fim de semana — feriados nacionais não são detectados
     *                   (calendário de feriados é uma feature à parte, fora de escopo
     *                   pra uma única praça com esse comportamento).
     */
    public static double calculate(List<TollPlaza> praçasCruzadas, VehicleProfile profile, LocalDate dataViagem) {
        double total = 0.0;
        for (TollPlaza praca : praçasCruzadas) {
            total += tarifaFor(praca, profile, dataViagem);
        }
        return total;
    }

    private static double tarifaFor(TollPlaza praca, VehicleProfile profile, LocalDate dataViagem) {
        if (profile.tipo() == VehicleType.MOTO && praca.getTarifaMoto() != null) {
            return praca.getTarifaMoto();
        }
        if (isFimDeSemana(dataViagem) && praca.getTarifaPorEixoFimDeSemana() != null) {
            return praca.getTarifaPorEixoFimDeSemana() * profile.numeroEixos();
        }
        return praca.getTarifaPorEixo() * profile.numeroEixos();
    }

    private static boolean isFimDeSemana(LocalDate data) {
        DayOfWeek dia = data.getDayOfWeek();
        return dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY;
    }
}
