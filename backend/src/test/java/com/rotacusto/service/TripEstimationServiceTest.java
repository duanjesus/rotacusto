package com.rotacusto.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rotacusto.domain.Coordinates;
import com.rotacusto.domain.OsmFuelStation;
import com.rotacusto.domain.RouteResult;
import com.rotacusto.dto.request.TripEstimateRequestDTO;
import com.rotacusto.dto.response.TripCostBreakdownDTO;
import com.rotacusto.entity.TollPlaza;
import com.rotacusto.entity.VehicleModel;
import com.rotacusto.entity.enums.TipoCombustivel;
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

    @Mock
    private RoadAlertService roadAlertService;

    private TripEstimationService tripEstimationService;

    /**
     * @InjectMocks não dá conta dos dois parâmetros @Value primitivos
     * (intervalo/custo de parada pra lanche) — o Mockito passaria 0.0 pra
     * eles, o que geraria NaN nos totais (divisão por intervalo zero). A
     * maioria dos testes aqui não é sobre lanche, então um intervalo bem
     * largo (1000h) desliga isso sem afetar as asserções existentes.
     */
    @BeforeEach
    void setUp() {
        tripEstimationService = new TripEstimationService(
                geocodingService, routingService, vehicleModelService, tollService, fuelStationService,
                roadAlertService, 1000.0, 25.0);
        // Alertas de trânsito (Fase 6.6) são consultados em toda estimativa, mas
        // não são o foco da maioria dos testes aqui — lenient() evita falha de
        // "unnecessary stubbing" nos poucos que não chegam a usar o retorno.
        org.mockito.Mockito.lenient().when(roadAlertService.findNearRoute(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
    }

    @Test
    void estimatesTripCostUsingVehicleModelFromCatalog() {
        Coordinates origem = new Coordinates(-22.9711, -43.1822); // Copacabana/RJ
        Coordinates destino = new Coordinates(-20.6633, -40.4967); // Guarapari/ES
        when(geocodingService.resolve("Copacabana, RJ")).thenReturn(origem);
        when(geocodingService.resolve("Guarapari, ES")).thenReturn(destino);

        RouteResult route = new RouteResult(500.0, 360.0,
                List.of(origem, new Coordinates(-21.5, -41.5), destino), List.of());
        when(routingService.route(List.of(origem, destino))).thenReturn(route);

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
                "Copacabana, RJ", "Guarapari, ES", 1L, null, 6.0, null, null);

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
        when(routingService.route(any())).thenReturn(new RouteResult(100.0, 60.0, List.of(origem, destino), List.of()));

        var manualProfile = new com.rotacusto.dto.request.VehicleProfileRequestDTO(
                VehicleType.MOTO, TipoCombustivel.GASOLINA, 20.0, 2, 0.15);
        TripEstimateRequestDTO request = new TripEstimateRequestDTO(
                "A", "B", null, manualProfile, 6.0, null, null);

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

        RouteResult route = new RouteResult(500.0, 360.0, List.of(origem, destino), List.of());
        when(routingService.route(List.of(origem, destino))).thenReturn(route);

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
                "Copacabana, RJ", "Guarapari, ES", 1L, null, 6.0, null, null);

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

        RouteResult route = new RouteResult(500.0, 360.0, List.of(origem, destino), List.of());
        when(routingService.route(List.of(origem, destino))).thenReturn(route);

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
                "Copacabana, RJ", "Guarapari, ES", 1L, null, 6.0, null, null);

        TripCostBreakdownDTO result = tripEstimationService.estimate(request);

        assertEquals(2, result.postosNaRota().size());
        assertEquals("Posto Ipiranga", result.postoSugerido().nome());
    }

    @Test
    void estimatesTripCostUsingPrecoPorKWhForElectricVehicle() {
        Coordinates origem = new Coordinates(-22.9711, -43.1822);
        Coordinates destino = new Coordinates(-20.6633, -40.4967);
        when(geocodingService.resolve("Copacabana, RJ")).thenReturn(origem);
        when(geocodingService.resolve("Guarapari, ES")).thenReturn(destino);

        RouteResult route = new RouteResult(300.0, 240.0, List.of(origem, destino), List.of());
        when(routingService.route(List.of(origem, destino))).thenReturn(route);

        VehicleModel bolt = new VehicleModel();
        bolt.setId(2L);
        bolt.setTipo(VehicleType.CARRO);
        bolt.setTipoCombustivel(TipoCombustivel.ELETRICO);
        bolt.setConsumoKmPorKWh(6.0);
        bolt.setNumeroEixos(2);
        bolt.setCustoDesgastePorKm(0.40);
        when(vehicleModelService.findById(2L)).thenReturn(bolt);

        TripEstimateRequestDTO request = new TripEstimateRequestDTO(
                "Copacabana, RJ", "Guarapari, ES", 2L, null, null, 0.90, null);

        TripCostBreakdownDTO result = tripEstimationService.estimate(request);

        // 300km / 6km/kWh * 0,90 = 45,00 | 300km * 0,40 = 120,00
        assertEquals(45.0, result.custoCombustivel(), 0.001);
        assertEquals(120.0, result.custoDesgaste(), 0.001);
        // veículo elétrico não sugere posto de gasolina
        assertEquals(0, result.postosNaRota().size());
    }

    @Test
    void throwsWhenElectricVehicleResolvedButPrecoPorKWhMissing() {
        when(geocodingService.resolve(anyString())).thenReturn(new Coordinates(-22.9, -43.1), new Coordinates(-20.6, -40.4));
        when(routingService.route(any()))
                .thenReturn(new RouteResult(300.0, 240.0,
                        List.of(new Coordinates(-22.9, -43.1), new Coordinates(-20.6, -40.4)), List.of()));

        VehicleModel bolt = new VehicleModel();
        bolt.setId(2L);
        bolt.setTipo(VehicleType.CARRO);
        bolt.setTipoCombustivel(TipoCombustivel.ELETRICO);
        bolt.setConsumoKmPorKWh(6.0);
        bolt.setNumeroEixos(2);
        bolt.setCustoDesgastePorKm(0.40);
        when(vehicleModelService.findById(2L)).thenReturn(bolt);

        TripEstimateRequestDTO request = new TripEstimateRequestDTO("A", "B", 2L, null, 6.0, null, null);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> tripEstimationService.estimate(request));
    }

    @Test
    void includesFoodStopCostForLongTrips() {
        Coordinates origem = new Coordinates(-22.9711, -43.1822);
        Coordinates destino = new Coordinates(-20.6633, -40.4967);
        when(geocodingService.resolve("Copacabana, RJ")).thenReturn(origem);
        when(geocodingService.resolve("Guarapari, ES")).thenReturn(destino);

        // 8h de viagem — usa o service com intervalo real (3h) em vez do
        // setUp() padrão (que desliga lanche pra não afetar os outros testes).
        tripEstimationService = new TripEstimationService(
                geocodingService, routingService, vehicleModelService, tollService, fuelStationService,
                roadAlertService, 3.0, 25.0);
        RouteResult route = new RouteResult(700.0, 480.0, List.of(origem, destino), List.of());
        when(routingService.route(List.of(origem, destino))).thenReturn(route);

        VehicleModel mobi = new VehicleModel();
        mobi.setId(1L);
        mobi.setTipo(VehicleType.CARRO);
        mobi.setConsumoEstradaKmL(10.0);
        mobi.setNumeroEixos(2);
        mobi.setCustoDesgastePorKm(0.35);
        when(vehicleModelService.findById(1L)).thenReturn(mobi);

        TripEstimateRequestDTO request = new TripEstimateRequestDTO(
                "Copacabana, RJ", "Guarapari, ES", 1L, null, 6.0, null, null);

        TripCostBreakdownDTO result = tripEstimationService.estimate(request);

        // 480 min = 8h, intervalo 3h -> floor(8/3) = 2 paradas * R$ 25 = R$ 50
        assertEquals(50.0, result.custoLanche(), 0.001);
    }

    @Test
    void resolvesStopsInOrderAndSendsFullWaypointListToRouting() {
        Coordinates origem = new Coordinates(-22.9711, -43.1822); // Copacabana/RJ
        Coordinates parada = new Coordinates(-22.7469, -41.8817); // Búzios/RJ
        Coordinates destino = new Coordinates(-20.6633, -40.4967); // Guarapari/ES
        when(geocodingService.resolve("Copacabana, RJ")).thenReturn(origem);
        when(geocodingService.resolve("Búzios, RJ")).thenReturn(parada);
        when(geocodingService.resolve("Guarapari, ES")).thenReturn(destino);

        RouteResult route = new RouteResult(600.0, 420.0, List.of(origem, parada, destino), List.of());
        when(routingService.route(List.of(origem, parada, destino))).thenReturn(route);

        VehicleModel mobi = new VehicleModel();
        mobi.setId(1L);
        mobi.setTipo(VehicleType.CARRO);
        mobi.setConsumoEstradaKmL(10.0);
        mobi.setNumeroEixos(2);
        mobi.setCustoDesgastePorKm(0.35);
        when(vehicleModelService.findById(1L)).thenReturn(mobi);

        TripEstimateRequestDTO request = new TripEstimateRequestDTO(
                "Copacabana, RJ", "Guarapari, ES", 1L, null, 6.0, null, List.of("Búzios, RJ"));

        TripCostBreakdownDTO result = tripEstimationService.estimate(request);

        assertEquals(600.0, result.distanciaKm(), 0.001);
        assertEquals(1, result.paradasNaRota().size());
        assertEquals(parada.lat(), result.paradasNaRota().get(0).lat(), 0.0001);
        assertEquals(parada.lon(), result.paradasNaRota().get(0).lon(), 0.0001);
    }

    @Test
    void returnsEmptyParadasNaRotaWhenNoStopsRequested() {
        Coordinates origem = new Coordinates(-22.9711, -43.1822);
        Coordinates destino = new Coordinates(-20.6633, -40.4967);
        when(geocodingService.resolve("Copacabana, RJ")).thenReturn(origem);
        when(geocodingService.resolve("Guarapari, ES")).thenReturn(destino);

        RouteResult route = new RouteResult(500.0, 360.0, List.of(origem, destino), List.of());
        when(routingService.route(List.of(origem, destino))).thenReturn(route);

        VehicleModel mobi = new VehicleModel();
        mobi.setId(1L);
        mobi.setTipo(VehicleType.CARRO);
        mobi.setConsumoEstradaKmL(10.0);
        mobi.setNumeroEixos(2);
        mobi.setCustoDesgastePorKm(0.35);
        when(vehicleModelService.findById(1L)).thenReturn(mobi);

        TripEstimateRequestDTO request = new TripEstimateRequestDTO(
                "Copacabana, RJ", "Guarapari, ES", 1L, null, 6.0, null, null);

        TripCostBreakdownDTO result = tripEstimationService.estimate(request);

        assertEquals(0, result.paradasNaRota().size());
    }
}
