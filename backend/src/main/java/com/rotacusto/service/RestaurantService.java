package com.rotacusto.service;

import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.rotacusto.client.OverpassClient;
import com.rotacusto.domain.Coordinates;
import com.rotacusto.domain.FoodStopSuggestion;
import com.rotacusto.domain.OsmRestaurant;
import com.rotacusto.domain.RouteStep;
import com.rotacusto.domain.cost.FoodStopPointCalculator;
import com.rotacusto.domain.geo.BoundingBoxCalculator;
import com.rotacusto.domain.geo.HaversineDistance;

/**
 * Restaurantes reais ao longo da rota (OpenStreetMap via Overpass), agrupados
 * pelos pontos de parada pra lanche já calculados (Fase 13) — uma lista pro
 * usuário escolher, não um valor auto-sugerido como {@code postoSugerido}.
 */
@Service
public class RestaurantService {

    private static final Logger log = LoggerFactory.getLogger(RestaurantService.class);
    private static final double BBOX_PADDING_DEGREES = 0.05; // ~5km

    private final OverpassClient overpassClient;
    private final double detectionRadiusKm;
    private final int maxPorParada;

    public RestaurantService(
            OverpassClient overpassClient,
            @Value("${rotacusto.restaurants.detection-radius-km}") double detectionRadiusKm,
            @Value("${rotacusto.restaurants.max-por-parada}") int maxPorParada) {
        this.overpassClient = overpassClient;
        this.detectionRadiusKm = detectionRadiusKm;
        this.maxPorParada = maxPorParada;
    }

    public List<OsmRestaurant> findRestaurantsNearRoute(List<Coordinates> geometriaRota) {
        try {
            double[] bbox = BoundingBoxCalculator.compute(geometriaRota, BBOX_PADDING_DEGREES);
            List<OsmRestaurant> candidates = overpassClient.findRestaurantsInBoundingBox(bbox[0], bbox[1], bbox[2], bbox[3]);
            return candidates.stream()
                    .filter(r -> isNearRoute(r, geometriaRota))
                    .toList();
        } catch (Exception e) {
            log.warn("Falha ao consultar restaurantes via OpenStreetMap (Overpass)", e);
            return List.of();
        }
    }

    /**
     * Um {@link FoodStopSuggestion} por parada calculada, cada um com até
     * {@code maxPorParada} restaurantes ordenados do mais próximo pro mais
     * longe daquele ponto — {@code numeroParadas <= 0} devolve lista vazia
     * (viagem curta, sem parada).
     */
    public List<FoodStopSuggestion> suggestStops(List<Coordinates> geometria, List<RouteStep> passos,
            long numeroParadas, double intervalHours) {
        if (numeroParadas <= 0) {
            return List.of();
        }

        List<Coordinates> pontosDeParada = FoodStopPointCalculator.computeStopPoints(passos, geometria, numeroParadas,
                intervalHours);
        List<OsmRestaurant> restaurantesDaRota = findRestaurantsNearRoute(geometria);

        return pontosDeParada.stream()
                .map(ponto -> new FoodStopSuggestion(ponto, restaurantesProximos(ponto, restaurantesDaRota)))
                .toList();
    }

    private boolean isNearRoute(OsmRestaurant restaurante, List<Coordinates> geometriaRota) {
        Coordinates ponto = new Coordinates(restaurante.lat(), restaurante.lon());
        return geometriaRota.stream().anyMatch(p -> HaversineDistance.km(p, ponto) <= detectionRadiusKm);
    }

    private List<OsmRestaurant> restaurantesProximos(Coordinates ponto, List<OsmRestaurant> restaurantes) {
        return restaurantes.stream()
                .filter(r -> HaversineDistance.km(ponto, new Coordinates(r.lat(), r.lon())) <= detectionRadiusKm)
                .sorted(Comparator.comparingDouble(r -> HaversineDistance.km(ponto, new Coordinates(r.lat(), r.lon()))))
                .limit(maxPorParada)
                .toList();
    }
}
