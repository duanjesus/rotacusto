package com.rotacusto.domain.cost;

import java.util.List;

import com.rotacusto.domain.RouteStep;
import com.rotacusto.domain.VehicleProfile;

public final class FuelCostCalculator {

    /**
     * Velocidade média do trecho abaixo disso é tratada como "cidade", acima
     * como "rodovia" — o ORS não marca tipo de via por trecho, então isso é
     * uma aproximação (proxy razoável, não uma classificação exata) baseada
     * em distância/duração de cada {@link RouteStep}.
     */
    private static final double LIMIAR_RODOVIA_KMH = 60.0;

    private FuelCostCalculator() {
    }

    /**
     * Sem trechos de rota (lista vazia) ou sem consumo de cidade conhecido
     * pro veículo (elétrico, ou perfil manual sem essa distinção) — cai no
     * comportamento antigo: consumo único pra distância inteira. Só faz o
     * blend cidade/rodovia quando os dois estão disponíveis.
     */
    public static double calculate(double distanciaKm, VehicleProfile profile, List<RouteStep> passos) {
        if (passos.isEmpty() || profile.consumoCidadePorUnidade() == null) {
            return distanciaKm / profile.consumoPorUnidade() * profile.precoPorUnidade();
        }

        double distanciaRodoviaKm = 0.0;
        double distanciaCidadeKm = 0.0;
        for (RouteStep passo : passos) {
            double distanciaPassoKm = passo.distanciaM() / 1000.0;
            boolean semDuracao = passo.duracaoS() <= 0;
            double velocidadeKmh = semDuracao ? Double.MAX_VALUE : (passo.distanciaM() / passo.duracaoS()) * 3.6;
            if (velocidadeKmh >= LIMIAR_RODOVIA_KMH) {
                distanciaRodoviaKm += distanciaPassoKm;
            } else {
                distanciaCidadeKm += distanciaPassoKm;
            }
        }

        double custoRodovia = distanciaRodoviaKm / profile.consumoPorUnidade() * profile.precoPorUnidade();
        double custoCidade = distanciaCidadeKm / profile.consumoCidadePorUnidade() * profile.precoPorUnidade();
        return custoRodovia + custoCidade;
    }
}
