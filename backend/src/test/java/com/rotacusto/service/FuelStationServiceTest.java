package com.rotacusto.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rotacusto.client.NominatimClient;
import com.rotacusto.client.OverpassClient;
import com.rotacusto.domain.Coordinates;
import com.rotacusto.domain.OsmFuelStation;
import com.rotacusto.domain.PricedFuelStation;
import com.rotacusto.domain.ReverseGeocodeResult;
import com.rotacusto.entity.enums.TipoCombustivel;

@ExtendWith(MockitoExtension.class)
class FuelStationServiceTest {

    @Mock
    private OverpassClient overpassClient;

    @Mock
    private NominatimClient nominatimClient;

    @Mock
    private FuelPriceService fuelPriceService;

    private FuelStationService newService(int candidatosK) {
        return new FuelStationService(overpassClient, nominatimClient, fuelPriceService, 2.0, candidatosK, 0L);
    }

    @Test
    void returnsOnlyStationsWithinDetectionRadiusOfTheRoute() {
        FuelStationService service = newService(5);

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
        FuelStationService service = newService(5);
        when(overpassClient.findFuelStationsInBoundingBox(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new RuntimeException("Overpass indisponível"));

        List<Coordinates> rota = List.of(new Coordinates(-22.90, -42.30));

        assertTrue(service.findStationsNearRoute(rota).isEmpty());
    }

    @Test
    void suggestsStationClosestToRouteMidpointByAccumulatedDistance() {
        FuelStationService service = newService(5);

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
        FuelStationService service = newService(5);

        List<Coordinates> rota = List.of(new Coordinates(-22.0, -42.0), new Coordinates(-22.1, -42.0));

        assertTrue(service.suggestStop(List.of(), rota).isEmpty());
    }

    @Test
    void suggestBestPricedStopReturnsCheapestAmongCandidates() throws Exception {
        FuelStationService service = newService(5);
        List<Coordinates> rota = List.of(new Coordinates(-22.00, -42.00), new Coordinates(-22.30, -42.00));

        OsmFuelStation caro = new OsmFuelStation("Caro", -22.15, -42.00);
        OsmFuelStation barato = new OsmFuelStation("Barato", -22.16, -42.00);

        when(nominatimClient.reverseGeocode(caro.lat(), caro.lon()))
                .thenReturn(Optional.of(new ReverseGeocodeResult("Cidade Cara", "RJ")));
        when(nominatimClient.reverseGeocode(barato.lat(), barato.lon()))
                .thenReturn(Optional.of(new ReverseGeocodeResult("Cidade Barata", "RJ")));
        when(fuelPriceService.buscarPrecoPorMunicipio(eq("Cidade Cara"), eq("RJ"), eq(TipoCombustivel.GASOLINA)))
                .thenReturn(Optional.of(7.50));
        when(fuelPriceService.buscarPrecoPorMunicipio(eq("Cidade Barata"), eq("RJ"), eq(TipoCombustivel.GASOLINA)))
                .thenReturn(Optional.of(5.90));

        Optional<PricedFuelStation> resultado = service.suggestBestPricedStop(
                List.of(caro, barato), rota, TipoCombustivel.GASOLINA);

        assertTrue(resultado.isPresent());
        assertEquals("Barato", resultado.get().station().nome());
        assertEquals(5.90, resultado.get().precoMedio());
        assertEquals("Cidade Barata", resultado.get().municipio());
    }

    @Test
    void suggestBestPricedStopFallsBackToNearestToMidpointWhenNoCandidateHasPrice() throws Exception {
        FuelStationService service = newService(5);
        List<Coordinates> rota = List.of(
                new Coordinates(-22.00, -42.00),
                new Coordinates(-22.10, -42.00),
                new Coordinates(-22.20, -42.00),
                new Coordinates(-22.30, -42.00));

        OsmFuelStation noComeco = new OsmFuelStation("Início", -22.01, -42.00);
        OsmFuelStation noMeio = new OsmFuelStation("Meio", -22.19, -42.00);

        lenient().when(nominatimClient.reverseGeocode(anyDouble(), anyDouble()))
                .thenThrow(new RuntimeException("Nominatim indisponível"));

        Optional<PricedFuelStation> resultado = service.suggestBestPricedStop(
                List.of(noComeco, noMeio), rota, TipoCombustivel.GASOLINA);

        assertTrue(resultado.isPresent());
        assertEquals("Meio", resultado.get().station().nome());
        assertEquals(null, resultado.get().precoMedio());
        assertEquals(null, resultado.get().municipio());
    }

    @Test
    void suggestBestPricedStopOnlyReverseGeocodesUpToKNearestCandidates() throws Exception {
        FuelStationService service = newService(1);
        // Rota reta de 4 pontos igualmente espaçados (mesmo padrão do teste de
        // suggestStop acima) — o meio geométrico fica perto do 3º ponto.
        List<Coordinates> rota = List.of(
                new Coordinates(-22.00, -42.00),
                new Coordinates(-22.10, -42.00),
                new Coordinates(-22.20, -42.00),
                new Coordinates(-22.30, -42.00));

        // "Meio" está mais perto do meio geométrico da rota que "Longe".
        OsmFuelStation meio = new OsmFuelStation("Meio", -22.19, -42.00);
        OsmFuelStation longe = new OsmFuelStation("Longe", -22.29, -42.00);

        when(nominatimClient.reverseGeocode(meio.lat(), meio.lon()))
                .thenReturn(Optional.of(new ReverseGeocodeResult("Cidade", "RJ")));
        when(fuelPriceService.buscarPrecoPorMunicipio(anyString(), anyString(), eq(TipoCombustivel.GASOLINA)))
                .thenReturn(Optional.of(6.00));

        Optional<PricedFuelStation> resultado = service.suggestBestPricedStop(
                List.of(longe, meio), rota, TipoCombustivel.GASOLINA);

        assertTrue(resultado.isPresent());
        assertEquals("Meio", resultado.get().station().nome());
        org.mockito.Mockito.verify(nominatimClient, org.mockito.Mockito.never())
                .reverseGeocode(longe.lat(), longe.lon());
    }
}
