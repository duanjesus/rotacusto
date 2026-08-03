package com.rotacusto.domain.cost;

import java.util.ArrayList;
import java.util.List;

import com.rotacusto.domain.Coordinates;
import com.rotacusto.domain.RouteStep;

/**
 * Acha os pontos reais ao longo da rota onde cada parada pra lanche calculada
 * (Fase 13, ver {@link FoodStopCostCalculator#numeroDeParadas}) deveria
 * acontecer. Diferente de sugestão de posto de combustível (que usa distância
 * acumulada — autonomia de tanque é sobre km), parada pra lanche dispara por
 * TEMPO acumulado, então o ponto também precisa ser achado por tempo: anda
 * pelos passos da rota somando {@code duracaoS} até cruzar cada múltiplo de
 * {@code intervalHours}, e usa o {@code wayPointInicio} do passo onde isso
 * acontece pra indexar direto na geometria da rota.
 */
public final class FoodStopPointCalculator {

    private FoodStopPointCalculator() {
    }

    public static List<Coordinates> computeStopPoints(List<RouteStep> passos, List<Coordinates> geometria,
            long numeroParadas, double intervalHours) {
        List<Coordinates> pontos = new ArrayList<>();
        if (numeroParadas <= 0 || passos.isEmpty() || geometria.isEmpty()) {
            return pontos;
        }

        double intervalSegundos = intervalHours * 3600.0;
        double duracaoAcumuladaS = 0.0;
        long proximaParadaAlvo = 1;

        for (RouteStep passo : passos) {
            duracaoAcumuladaS += passo.duracaoS();
            while (proximaParadaAlvo <= numeroParadas && duracaoAcumuladaS >= proximaParadaAlvo * intervalSegundos) {
                int indice = Math.min(passo.wayPointInicio(), geometria.size() - 1);
                pontos.add(geometria.get(indice));
                proximaParadaAlvo++;
            }
        }

        return pontos;
    }
}
