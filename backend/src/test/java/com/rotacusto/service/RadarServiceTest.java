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
import com.rotacusto.domain.OsmSpeedCamera;

@ExtendWith(MockitoExtension.class)
class RadarServiceTest {

    @Mock
    private OverpassClient overpassClient;

    @Test
    void returnsOnlyCamerasWithinDetectionRadiusOfTheRoute() {
        RadarService service = new RadarService(overpassClient, 0.3);

        OsmSpeedCamera perto = new OsmSpeedCamera(-22.90, -42.30);
        OsmSpeedCamera longe = new OsmSpeedCamera(-10.0, -50.0);
        when(overpassClient.findSpeedCamerasInBoundingBox(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(perto, longe));

        List<Coordinates> rota = List.of(new Coordinates(-22.90, -42.30));

        List<OsmSpeedCamera> resultado = service.findCamerasNearRoute(rota);

        assertEquals(1, resultado.size());
        assertEquals(-22.90, resultado.get(0).lat());
    }

    @Test
    void fallsBackToEmptyListWhenOverpassFails() {
        RadarService service = new RadarService(overpassClient, 0.3);
        when(overpassClient.findSpeedCamerasInBoundingBox(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new RuntimeException("Overpass indisponível"));

        List<Coordinates> rota = List.of(new Coordinates(-22.90, -42.30));

        assertTrue(service.findCamerasNearRoute(rota).isEmpty());
    }
}
