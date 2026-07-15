package com.rotacusto.domain;

/**
 * Uma instrução de navegação turn-by-turn (ex.: "Vire à direita na Rua X").
 * {@code wayPointInicio}/{@code wayPointFim} são índices dentro da lista de
 * {@code geometria} de {@link RouteResult} — marcam o trecho da rota ao qual
 * essa instrução se refere, usados pra casar a posição GPS ao vivo com a
 * instrução certa durante a navegação.
 */
public record RouteStep(String instrucao, double distanciaM, double duracaoS, int wayPointInicio, int wayPointFim) {
}
