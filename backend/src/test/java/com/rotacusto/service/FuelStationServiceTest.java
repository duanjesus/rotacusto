package com.rotacusto.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rotacusto.client.OverpassClient;
import com.rotacusto.domain.Coordinates;
import com.rotacusto.domain.OsmFuelStation;

@ExtendWith(MockitoExtension.class)
class FuelStationServiceTest {

    @Mock
    private OverpassClient overpassClient;

    @Test
    void returnsOnlyStationsWithinDetectionRadiusOfTheRoute() {
        FuelStationService service = new FuelStationService(overpassClient, 2.0);

        OsmFuelStation perto = new OsmFuelStation("Perto", -22.90, -42.30);
        OsmFuelStation longe = new OsmFuelStation("Longe", -10.0, -50.0);
        when(overpassClient.findFuelStationsInBoundingBox(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(perto, longe));

        List<Coordinates> rota = List.of(new Coordinates(-22.90, -42.30));

        List<OsmFuelStation> resultado = service.findStationsNearRoute(rota);

        assertEquals(1, resultado.size());
        assertEquals("Perto", resultado.get(0).nome());
    }

    @Test
    void fallsBackToEmptyListWhenOverpassFails() {
        FuelStationService service = new FuelStationService(overpassClient, 2.0);
        when(overpassClient.findFuelStationsInBoundingBox(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new RuntimeException("Overpass indisponível"));

        List<Coordinates> rota = List.of(new Coordinates(-22.90, -42.30));

        assertTrue(service.findStationsNearRoute(rota).isEmpty());
    }

    @Test
    void suggestsStationClosestToRouteMidpointByAccumulatedDistance() {
        FuelStationService service = new FuelStationService(overpassClient, 2.0);

        // Rota reta de 4 pontos igualmente espaçados; o meio fica perto do 3º ponto.
        List<Coordinates> rota = List.of(
                new Coordinates(-22.00, -42.00),
                new Coordinates(-22.10, -42.00),
                new Coordinates(-22.20, -42.00),
                new Coordinates(-22.30, -42.00));

        OsmFuelStation noComeco = new OsmFuelStation("Início", -22.01, -42.00);
        OsmFuelStation noMeio = new OsmFuelStation("Meio", -22.19, -42.00);
        OsmFuelStation noFim = new OsmFuelStation("Fim", -22.29, -42.00);

        Optional<OsmFuelStation> sugestao = service.suggestStop(List.of(noComeco, noMeio, noFim), rota);

        assertTrue(sugestao.isPresent());
        assertEquals("Meio", sugestao.get().nome());
    }

    @Test
    void suggestStopReturnsEmptyWhenNoStationsFound() {
        FuelStationService service = new FuelStationService(overpassClient, 2.0);

        List<Coordinates> rota = List.of(new Coordinates(-22.0, -42.0), new Coordinates(-22.1, -42.0));

        assertTrue(service.suggestStop(List.of(), rota).isEmpty());
    }
}
