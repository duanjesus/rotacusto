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
import com.rotacusto.domain.OsmRadar;
import com.rotacusto.entity.enums.RadarType;

@ExtendWith(MockitoExtension.class)
class RadarServiceTest {

    @Mock
    private OverpassClient overpassClient;

    @Test
    void returnsOnlyCamerasWithinDetectionRadiusOfTheRoute() {
        RadarService service = new RadarService(overpassClient, 0.3);

        OsmRadar perto = new OsmRadar(RadarType.VELOCIDADE, -22.90, -42.30);
        OsmRadar longe = new OsmRadar(RadarType.VELOCIDADE, -10.0, -50.0);
        when(overpassClient.findRadarsInBoundingBox(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(perto, longe));

        List<Coordinates> rota = List.of(new Coordinates(-22.90, -42.30));

        List<OsmRadar> resultado = service.findCamerasNearRoute(rota);

        assertEquals(1, resultado.size());
        assertEquals(-22.90, resultado.get(0).lat());
    }

    @Test
    void returnsBothRadarTypesWhenBothAreWithinRange() {
        RadarService service = new RadarService(overpassClient, 0.3);

        OsmRadar velocidade = new OsmRadar(RadarType.VELOCIDADE, -22.90, -42.30);
        OsmRadar avancoSinal = new OsmRadar(RadarType.AVANCO_SINAL, -22.9001, -42.3001);
        when(overpassClient.findRadarsInBoundingBox(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(velocidade, avancoSinal));

        List<Coordinates> rota = List.of(new Coordinates(-22.90, -42.30));

        List<OsmRadar> resultado = service.findCamerasNearRoute(rota);

        assertEquals(2, resultado.size());
        assertTrue(resultado.stream().anyMatch(r -> r.tipo() == RadarType.VELOCIDADE));
        assertTrue(resultado.stream().anyMatch(r -> r.tipo() == RadarType.AVANCO_SINAL));
    }

    @Test
    void fallsBackToEmptyListWhenOverpassFails() {
        RadarService service = new RadarService(overpassClient, 0.3);
        when(overpassClient.findRadarsInBoundingBox(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new RuntimeException("Overpass indisponível"));

        List<Coordinates> rota = List.of(new Coordinates(-22.90, -42.30));

        assertTrue(service.findCamerasNearRoute(rota).isEmpty());
    }
}
