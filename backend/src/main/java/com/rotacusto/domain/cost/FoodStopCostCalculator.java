package com.rotacusto.domain.cost;

public final class FoodStopCostCalculator {

    private FoodStopCostCalculator() {
    }

    public static double calculate(double duracaoMin, double intervalHours, double averageCost) {
        return numeroDeParadas(duracaoMin, intervalHours) * averageCost;
    }

    /**
     * Número de paradas = duração da viagem dividida pelo intervalo
     * recomendado entre paradas, arredondado pra baixo (viagem mais curta
     * que o intervalo não gera parada nenhuma). intervalHours <= 0 é tratado
     * como configuração inválida/desligada — devolve 0 em vez de Infinity/NaN.
     * Público (Fase 13) — reaproveitado pra achar os pontos de parada reais
     * ao longo da rota, não só o custo agregado.
     */
    public static long numeroDeParadas(double duracaoMin, double intervalHours) {
        if (intervalHours <= 0) {
            return 0L;
        }
        double duracaoHoras = duracaoMin / 60.0;
        return (long) Math.floor(duracaoHoras / intervalHours);
    }
}
