package com.rotacusto.domain;

import java.util.List;

/** Um ponto de parada pra lanche calculado (Fase 13) + os restaurantes reais
 * mais próximos dele, ordenados por distância — o usuário escolhe. */
public record FoodStopSuggestion(Coordinates ponto, List<OsmRestaurant> restaurantes) {
}
