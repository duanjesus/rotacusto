package com.rotacusto.service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.rotacusto.domain.Coordinates;
import com.rotacusto.domain.OsmFuelStation;
import com.rotacusto.domain.RouteResult;
import com.rotacusto.domain.TripCostBreakdown;
import com.rotacusto.domain.TripCostCalculator;
import com.rotacusto.domain.VehicleProfile;
import com.rotacusto.domain.cost.TollCostCalculator;
import com.rotacusto.dto.request.TripEstimateRequestDTO;
import com.rotacusto.dto.request.VehicleProfileRequestDTO;
import com.rotacusto.dto.response.CoordinateDTO;
import com.rotacusto.dto.response.FuelStationResponseDTO;
import com.rotacusto.dto.response.RouteStepDTO;
import com.rotacusto.dto.response.TollPlazaResponseDTO;
import com.rotacusto.dto.response.TripCostBreakdownDTO;
import com.rotacusto.entity.TollPlaza;
import com.rotacusto.entity.VehicleModel;
import com.rotacusto.entity.enums.TipoCombustivel;

@Service
public class TripEstimationService {

    private final GeocodingService geocodingService;
    private final RoutingService routingService;
    private final VehicleModelService vehicleModelService;
    private final TollService tollService;
    private final FuelStationService fuelStationService;
    private final double foodStopIntervalHours;
    private final double foodStopAverageCost;

    public TripEstimationService(GeocodingService geocodingService, RoutingService routingService,
            VehicleModelService vehicleModelService, TollService tollService, FuelStationService fuelStationService,
            @Value("${rotacusto.food-stop.interval-hours}") double foodStopIntervalHours,
            @Value("${rotacusto.food-stop.average-cost}") double foodStopAverageCost) {
        this.geocodingService = geocodingService;
        this.routingService = routingService;
        this.vehicleModelService = vehicleModelService;
        this.tollService = tollService;
        this.fuelStationService = fuelStationService;
        this.foodStopIntervalHours = foodStopIntervalHours;
        this.foodStopAverageCost = foodStopAverageCost;
    }

    public TripCostBreakdownDTO estimate(TripEstimateRequestDTO request) {
        Coordinates origem = geocodingService.resolve(request.origem());
        Coordinates destino = geocodingService.resolve(request.destino());
        RouteResult route = routingService.route(origem, destino);

        VehicleProfile profile = resolveProfile(request);

        // Pedágios e postos são duas consultas Overpass independentes — rodar em
        // paralelo corta o pior caso de latência quase pela metade (cada uma já
        // tem timeout+fallback próprio, mas em série os dois piores casos somam).
        // Postos de gasolina não fazem sentido pra elétrico (precisaria de pontos
        // de recarga, um recurso diferente, fora de escopo por enquanto).
        boolean eletrico = profile.tipoCombustivel() == TipoCombustivel.ELETRICO;
        CompletableFuture<List<TollPlaza>> praçasFuture = CompletableFuture
                .supplyAsync(() -> tollService.findCrossedPlazas(route.geometria()));
        CompletableFuture<List<OsmFuelStation>> postosFuture = eletrico
                ? CompletableFuture.completedFuture(List.of())
                : CompletableFuture.supplyAsync(() -> fuelStationService.findStationsNearRoute(route.geometria()));

        List<TollPlaza> praçasCruzadas = praçasFuture.join();
        List<OsmFuelStation> postos = postosFuture.join();

        TripCostBreakdown breakdown = TripCostCalculator.calculate(route.distanciaKm(), route.duracaoMin(), profile,
                praçasCruzadas, foodStopIntervalHours, foodStopAverageCost);
        Optional<OsmFuelStation> postoSugerido = eletrico ? Optional.empty()
                : fuelStationService.suggestStop(postos, route.geometria());

        List<CoordinateDTO> geometria = route.geometria().stream()
                .map(c -> new CoordinateDTO(c.lat(), c.lon()))
                .toList();
        List<TollPlazaResponseDTO> pedagios = praçasCruzadas.stream()
                .map(p -> new TollPlazaResponseDTO(p.getNome(), p.getRodovia(), p.getConcessionaria(), p.getLat(),
                        p.getLng(), TollCostCalculator.calculate(List.of(p), profile)))
                .toList();
        List<FuelStationResponseDTO> postosDTO = postos.stream()
                .map(p -> new FuelStationResponseDTO(p.nome(), p.lat(), p.lon()))
                .toList();
        List<RouteStepDTO> passos = route.passos().stream()
                .map(s -> new RouteStepDTO(s.instrucao(), s.distanciaM(), s.duracaoS(), s.wayPointInicio(), s.wayPointFim()))
                .toList();

        return new TripCostBreakdownDTO(
                breakdown.distanciaKm(),
                breakdown.duracaoMin(),
                breakdown.custoCombustivel(),
                breakdown.custoDesgaste(),
                breakdown.custoPedagio(),
                breakdown.custoLanche(),
                breakdown.total(),
                geometria,
                pedagios,
                postosDTO,
                postoSugerido.map(p -> new FuelStationResponseDTO(p.nome(), p.lat(), p.lon())).orElse(null),
                passos);
    }

    private VehicleProfile resolveProfile(TripEstimateRequestDTO request) {
        if (request.vehicleModelId() != null) {
            VehicleModel model = vehicleModelService.findById(request.vehicleModelId());
            double preco = precoParaTipoCombustivel(request, model.getTipoCombustivel());
            return VehicleProfile.fromModel(model, preco);
        }

        VehicleProfileRequestDTO manual = request.vehicleProfile();
        if (manual == null) {
            throw new IllegalArgumentException("Informe vehicleModelId (catálogo) ou vehicleProfile (manual).");
        }
        double preco = precoParaTipoCombustivel(request, manual.tipoCombustivel());
        return new VehicleProfile(
                manual.tipo(),
                manual.tipoCombustivel(),
                manual.consumoPorUnidade(),
                manual.numeroEixos(),
                manual.custoDesgastePorKm(),
                preco);
    }

    /**
     * Preço é condicional ao combustível do veículo resolvido — não dá pra
     * expressar essa regra (exatamente um dos dois campos) com anotações simples.
     * Gasolina/etanol/diesel usam todos o mesmo campo genérico precoPorLitro (o
     * veículo escolhido já fixa qual dos três é).
     */
    private double precoParaTipoCombustivel(TripEstimateRequestDTO request, TipoCombustivel tipoCombustivel) {
        if (tipoCombustivel == TipoCombustivel.ELETRICO) {
            if (request.precoPorKWh() == null) {
                throw new IllegalArgumentException("Veículo elétrico: informe precoPorKWh.");
            }
            return request.precoPorKWh();
        }
        if (request.precoPorLitro() == null) {
            throw new IllegalArgumentException("Informe precoPorLitro para " + tipoCombustivel + ".");
        }
        return request.precoPorLitro();
    }
}
