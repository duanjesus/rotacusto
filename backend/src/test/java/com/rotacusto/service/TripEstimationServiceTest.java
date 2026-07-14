package com.rotacusto.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rotacusto.domain.Coordinates;
import com.rotacusto.domain.OsmFuelStation;
import com.rotacusto.domain.RouteResult;
import com.rotacusto.dto.request.TripEstimateRequestDTO;
import com.rotacusto.dto.response.TripCostBreakdownDTO;
import com.rotacusto.entity.TollPlaza;
import com.rotacusto.entity.VehicleModel;
import com.rotacusto.entity.enums.VehicleType;

@ExtendWith(MockitoExtension.class)
class TripEstimationServiceTest {

    @Mock
    private GeocodingService geocodingService;

    @Mock
    private RoutingService routingService;

    @Mock
    private VehicleModelService vehicleModelService;

    @Mock
    private TollService tollService;

    @Mock
    private FuelStationService fuelStationService;

    @InjectMocks
    private TripEstimationService tripEstimationService;

    @Test
    void estimatesTripCostUsingVehicleModelFromCatalog() {
        Coordinates origem = new Coordinates(-22.9711, -43.1822); // Copacabana/RJ
        Coordinates destino = new Coordinates(-20.6633, -40.4967); // Guarapari/ES
        when(geocodingService.resolve("Copacabana, RJ")).thenReturn(origem);
        when(geocodingService.resolve("Guarapari, ES")).thenReturn(destino);

        RouteResult route = new RouteResult(500.0, 360.0,
                List.of(origem, new Coordinates(-21.5, -41.5), destino));
        when(routingService.route(origem, destino)).thenReturn(route);

        VehicleModel mobi = new VehicleModel();
        mobi.setId(1L);
        mobi.setMarca("Fiat");
        mobi.setModelo("Mobi 1.0");
        mobi.setAno(2024);
        mobi.setTipo(VehicleType.CARRO);
        mobi.setConsumoCidadeKmL(8.0);
        mobi.setConsumoEstradaKmL(10.0);
        mobi.setNumeroEixos(2);
        mobi.setCustoDesgastePorKm(0.35);
        when(vehicleModelService.findById(1L)).thenReturn(mobi);

        TripEstimateRequestDTO request = new TripEstimateRequestDTO(
                "Copacabana, RJ", "Guarapari, ES", 1L, null, 6.0);

        TripCostBreakdownDTO result = tripEstimationService.estimate(request);

        // 500km / 10km/l * 6,00 = 300,00 | 500km * 0,35 = 175,00
        assertEquals(500.0, result.distanciaKm(), 0.001);
        assertEquals(360.0, result.duracaoMin(), 0.001);
        assertEquals(300.0, result.custoCombustivel(), 0.001);
        assertEquals(175.0, result.custoDesgaste(), 0.001);
        assertEquals(475.0, result.total(), 0.001);
        assertEquals(3, result.geometriaRota().size());
    }

    @Test
    void estimatesTripCostUsingManualVehicleProfileWhenNoModelIdGiven() {
        Coordinates origem = new Coordinates(-22.9, -43.1);
        Coordinates destino = new Coordinates(-20.6, -40.4);
        when(geocodingService.resolve(anyString())).thenReturn(origem, destino);
        when(routingService.route(any(), any())).thenReturn(new RouteResult(100.0, 60.0, List.of(origem, destino)));

        var manualProfile = new com.rotacusto.dto.request.VehicleProfileRequestDTO(
                VehicleType.MOTO, 20.0, 2, 0.15);
        TripEstimateRequestDTO request = new TripEstimateRequestDTO(
                "A", "B", null, manualProfile, 6.0);

        TripCostBreakdownDTO result = tripEstimationService.estimate(request);

        // 100km / 20km/l * 6,00 = 30,00 | 100km * 0,15 = 15,00
        assertEquals(30.0, result.custoCombustivel(), 0.001);
        assertEquals(15.0, result.custoDesgaste(), 0.001);
        assertEquals(45.0, result.total(), 0.001);
    }

    @Test
    void includesCrossedTollPlazasInBreakdownAndResponseList() {
        Coordinates origem = new Coordinates(-22.9711, -43.1822);
        Coordinates destino = new Coordinates(-20.6633, -40.4967);
        when(geocodingService.resolve("Copacabana, RJ")).thenReturn(origem);
        when(geocodingService.resolve("Guarapari, ES")).thenReturn(destino);

        RouteResult route = new RouteResult(500.0, 360.0, List.of(origem, destino));
        when(routingService.route(origem, destino)).thenReturn(route);

        VehicleModel mobi = new VehicleModel();
        mobi.setId(1L);
        mobi.setTipo(VehicleType.CARRO);
        mobi.setConsumoEstradaKmL(10.0);
        mobi.setNumeroEixos(2);
        mobi.setCustoDesgastePorKm(0.35);
        when(vehicleModelService.findById(1L)).thenReturn(mobi);

        TollPlaza praca = new TollPlaza();
        praca.setNome("Pedágio BR-101 - Rio Bonito/RJ");
        praca.setRodovia("BR-101");
        praca.setConcessionaria("Arteris Fluminense");
        praca.setLat(-22.72);
        praca.setLng(-42.62);
        praca.setTarifaPorEixo(5.20); // carro (2 eixos) -> R$ 10,40
        when(tollService.findCrossedPlazas(route.geometria())).thenReturn(List.of(praca));

        TripEstimateRequestDTO request = new TripEstimateRequestDTO(
                "Copacabana, RJ", "Guarapari, ES", 1L, null, 6.0);

        TripCostBreakdownDTO result = tripEstimationService.estimate(request);

        assertEquals(10.40, result.custoPedagio(), 0.001);
        assertEquals(1, result.pedagiosNaRota().size());
        assertEquals("Pedágio BR-101 - Rio Bonito/RJ", result.pedagiosNaRota().get(0).nome());
        assertEquals(10.40, result.pedagiosNaRota().get(0).valorCobrado(), 0.001);
    }

    @Test
    void includesFuelStationsAndSuggestedStopInResponse() {
        Coordinates origem = new Coordinates(-22.9711, -43.1822);
        Coordinates destino = new Coordinates(-20.6633, -40.4967);
        when(geocodingService.resolve("Copacabana, RJ")).thenReturn(origem);
        when(geocodingService.resolve("Guarapari, ES")).thenReturn(destino);

        RouteResult route = new RouteResult(500.0, 360.0, List.of(origem, destino));
        when(routingService.route(origem, destino)).thenReturn(route);

        VehicleModel mobi = new VehicleModel();
        mobi.setId(1L);
        mobi.setTipo(VehicleType.CARRO);
        mobi.setConsumoEstradaKmL(10.0);
        mobi.setNumeroEixos(2);
        mobi.setCustoDesgastePorKm(0.35);
        when(vehicleModelService.findById(1L)).thenReturn(mobi);

        OsmFuelStation posto1 = new OsmFuelStation("Posto Ipiranga", -21.8, -42.0);
        OsmFuelStation posto2 = new OsmFuelStation("Posto Shell", -21.5, -41.5);
        when(fuelStationService.findStationsNearRoute(route.geometria())).thenReturn(List.of(posto1, posto2));
        when(fuelStationService.suggestStop(List.of(posto1, posto2), route.geometria()))
                .thenReturn(java.util.Optional.of(posto1));

        TripEstimateRequestDTO request = new TripEstimateRequestDTO(
                "Copacabana, RJ", "Guarapari, ES", 1L, null, 6.0);

        TripCostBreakdownDTO result = tripEstimationService.estimate(request);

        assertEquals(2, result.postosNaRota().size());
        assertEquals("Posto Ipiranga", result.postoSugerido().nome());
    }
}
