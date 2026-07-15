package com.rotacusto.domain.cost;

public final class FoodStopCostCalculator {

    private FoodStopCostCalculator() {
    }

    /**
     * Número de paradas = duração da viagem dividida pelo intervalo
     * recomendado entre paradas, arredondado pra baixo (viagem mais curta
     * que o intervalo não gera parada nenhuma). intervalHours <= 0 é tratado
     * como configuração inválida/desligada — devolve 0 em vez de Infinity/NaN.
     */
    public static double calculate(double duracaoMin, double intervalHours, double averageCost) {
        if (intervalHours <= 0) {
            return 0.0;
        }
        double duracaoHoras = duracaoMin / 60.0;
        long numeroParadas = (long) Math.floor(duracaoHoras / intervalHours);
        return numeroParadas * averageCost;
    }
}
