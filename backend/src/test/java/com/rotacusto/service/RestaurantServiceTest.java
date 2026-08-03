package com.rotacusto.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rotacusto.client.OverpassClient;
import com.rotacusto.domain.Coordinates;
import com.rotacusto.domain.FoodStopSuggestion;
import com.rotacusto.domain.OsmRestaurant;
import com.rotacusto.domain.RouteStep;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceTest {

    @Mock
    private OverpassClient overpassClient;

    @Test
    void returnsOnlyRestaurantsWithinDetectionRadiusOfTheRoute() {
        RestaurantService service = new RestaurantService(overpassClient, 3.0, 8);

        OsmRestaurant perto = new OsmRestaurant("Perto", -22.90, -42.30);
        OsmRestaurant longe = new OsmRestaurant("Longe", -10.0, -50.0);
        when(overpassClient.findRestaurantsInBoundingBox(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(perto, longe));

        List<Coordinates> rota = List.of(new Coordinates(-22.90, -42.30));

        List<OsmRestaurant> resultado = service.findRestaurantsNearRoute(rota);

        assertEquals(1, resultado.size());
        assertEquals("Perto", resultado.get(0).nome());
    }

    @Test
    void fallsBackToEmptyListWhenOverpassFails() {
        RestaurantService service = new RestaurantService(overpassClient, 3.0, 8);
        when(overpassClient.findRestaurantsInBoundingBox(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new RuntimeException("Overpass indisponível"));

        List<Coordinates> rota = List.of(new Coordinates(-22.90, -42.30));

        assertTrue(service.findRestaurantsNearRoute(rota).isEmpty());
    }

    @Test
    void suggestStopsReturnsEmptyWhenNoStopsNeeded() {
        RestaurantService service = new RestaurantService(overpassClient, 3.0, 8);

        List<RouteStep> passos = List.of(new RouteStep("Siga em frente", 1000, 3600, 0, 1));
        List<Coordinates> geometria = List.of(new Coordinates(0, 0), new Coordinates(1, 1));

        assertTrue(service.suggestStops(geometria, passos, 0, 3.0).isEmpty());
    }

    @Test
    void suggestStopsGroupsRestaurantsClosestToEachStopPointSortedByDistance() {
        RestaurantService service = new RestaurantService(overpassClient, 3.0, 8);

        // 2 passos de 2h cada -> marca de 3h (10800s) cai no 2º passo, wayPointInicio=1.
        List<RouteStep> passos = List.of(
                new RouteStep("Passo 1", 100_000, 7200, 0, 1),
                new RouteStep("Passo 2", 100_000, 7200, 1, 2));
        List<Coordinates> geometria = List.of(
                new Coordinates(-22.90, -42.30), new Coordinates(-22.91, -42.31), new Coordinates(-22.92, -42.32));

        OsmRestaurant maisPerto = new OsmRestaurant("Mais perto", -22.9101, -42.3101);
        OsmRestaurant maisLonge = new OsmRestaurant("Mais longe", -22.9150, -42.3150);
        when(overpassClient.findRestaurantsInBoundingBox(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(maisLonge, maisPerto));

        List<FoodStopSuggestion> sugestoes = service.suggestStops(geometria, passos, 1, 3.0);

        assertEquals(1, sugestoes.size());
        assertEquals(new Coordinates(-22.91, -42.31), sugestoes.get(0).ponto());
        assertEquals(2, sugestoes.get(0).restaurantes().size());
        assertEquals("Mais perto", sugestoes.get(0).restaurantes().get(0).nome());
        assertEquals("Mais longe", sugestoes.get(0).restaurantes().get(1).nome());
    }

    @Test
    void suggestStopsCapsRestaurantsPerStopAtConfiguredMax() {
        RestaurantService service = new RestaurantService(overpassClient, 3.0, 2);

        List<RouteStep> passos = List.of(new RouteStep("Passo único", 100_000, 10800, 0, 1));
        List<Coordinates> geometria = List.of(new Coordinates(-22.90, -42.30), new Coordinates(-22.91, -42.31));

        List<OsmRestaurant> tresRestaurantes = List.of(
                new OsmRestaurant("A", -22.9101, -42.3101),
                new OsmRestaurant("B", -22.9102, -42.3102),
                new OsmRestaurant("C", -22.9103, -42.3103));
        when(overpassClient.findRestaurantsInBoundingBox(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(tresRestaurantes);

        List<FoodStopSuggestion> sugestoes = service.suggestStops(geometria, passos, 1, 3.0);

        assertEquals(2, sugestoes.get(0).restaurantes().size());
    }
}
