package com.rotacusto.service;

import java.util.ArrayList;
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
import com.rotacusto.dto.response.RoadAlertResponseDTO;
import com.rotacusto.dto.response.RouteStepDTO;
import com.rotacusto.dto.response.TollPlazaResponseDTO;
import com.rotacusto.dto.response.TrafficReportResponseDTO;
import com.rotacusto.dto.response.TripCostBreakdownDTO;
import com.rotacusto.entity.RoadAlert;
import com.rotacusto.entity.TollPlaza;
import com.rotacusto.entity.TrafficReport;
import com.rotacusto.entity.VehicleModel;
import com.rotacusto.entity.enums.TipoCombustivel;

@Service
public class TripEstimationService {

    private final GeocodingService geocodingService;
    private final RoutingService routingService;
    private final VehicleModelService vehicleModelService;
    private final TollService tollService;
    private final FuelStationService fuelStationService;
    private final RoadAlertService roadAlertService;
    private final TrafficReportService trafficReportService;
    private final double foodStopIntervalHours;
    private final double foodStopAverageCost;

    public TripEstimationService(GeocodingService geocodingService, RoutingService routingService,
            VehicleModelService vehicleModelService, TollService tollService, FuelStationService fuelStationService,
            RoadAlertService roadAlertService, TrafficReportService trafficReportService,
            @Value("${rotacusto.food-stop.interval-hours}") double foodStopIntervalHours,
            @Value("${rotacusto.food-stop.average-cost}") double foodStopAverageCost) {
        this.geocodingService = geocodingService;
        this.routingService = routingService;
        this.vehicleModelService = vehicleModelService;
        this.tollService = tollService;
        this.fuelStationService = fuelStationService;
        this.roadAlertService = roadAlertService;
        this.trafficReportService = trafficReportService;
        this.foodStopIntervalHours = foodStopIntervalHours;
        this.foodStopAverageCost = foodStopAverageCost;
    }

    public TripCostBreakdownDTO estimate(TripEstimateRequestDTO request) {
        Coordinates origem = geocodingService.resolve(request.origem());
        List<Coordinates> paradas = request.paradas() == null ? List.of()
                : request.paradas().stream().map(geocodingService::resolve).toList();
        Coordinates destino = geocodingService.resolve(request.destino());

        List<Coordinates> waypoints = new ArrayList<>();
        waypoints.add(origem);
        waypoints.addAll(paradas);
        waypoints.add(destino);
        RouteResult route = routingService.route(waypoints);

        VehicleProfile profile = resolveProfile(request);
        return buildBreakdownDTO(route, profile, paradas);
    }

    /**
     * Rotas alternativas (Fase 10) — só disponível pra origem→destino simples, sem
     * paradas: o ORS não oferece {@code alternative_routes} com waypoints
     * intermediários (ver {@link RoutingService#routes}). Cada alternativa recebe um
     * breakdown COMPLETO (pedágio, posto, alertas, trânsito — tudo, não uma versão
     * resumida), pra comparação de custo ser honesta com o valor real de cada opção.
     */
    public List<TripCostBreakdownDTO> estimateAlternatives(TripEstimateRequestDTO request) {
        if (request.paradas() != null && !request.paradas().isEmpty()) {
            throw new IllegalArgumentException("Rotas alternativas só estão disponíveis pra viagens sem paradas.");
        }
        Coordinates origem = geocodingService.resolve(request.origem());
        Coordinates destino = geocodingService.resolve(request.destino());
        VehicleProfile profile = resolveProfile(request);

        List<RouteResult> rotas = routingService.routes(List.of(origem, destino));
        return rotas.stream().map(rota -> buildBreakdownDTO(rota, profile, List.of())).toList();
    }

    private TripCostBreakdownDTO buildBreakdownDTO(RouteResult route, VehicleProfile profile,
            List<Coordinates> paradas) {
        // Pedágios, postos, alertas de trânsito e relatos de trânsito lento são
        // quatro consultas independentes — rodar em paralelo corta o pior caso de
        // latência (cada uma já tem timeout/fallback próprio, mas em série os piores
        // casos somam). Postos de gasolina não fazem sentido pra elétrico (precisaria
        // de pontos de recarga, um recurso diferente, fora de escopo por enquanto).
        // Alertas/relatos de trânsito (Fases 6.6/6.7) são só consultas ao próprio
        // banco (sem serviço externo), bem mais rápidas que as outras duas, mas
        // entram no mesmo padrão por consistência.
        boolean eletrico = profile.tipoCombustivel() == TipoCombustivel.ELETRICO;
        CompletableFuture<List<TollPlaza>> praçasFuture = CompletableFuture
                .supplyAsync(() -> tollService.findCrossedPlazas(route.geometria()));
        CompletableFuture<List<OsmFuelStation>> postosFuture = eletrico
                ? CompletableFuture.completedFuture(List.of())
                : CompletableFuture.supplyAsync(() -> fuelStationService.findStationsNearRoute(route.geometria()));
        CompletableFuture<List<RoadAlert>> alertasFuture = CompletableFuture
                .supplyAsync(() -> roadAlertService.findNearRoute(route.geometria()));
        CompletableFuture<List<TrafficReport>> trafegoFuture = CompletableFuture
                .supplyAsync(() -> trafficReportService.findNearRoute(route.geometria()));

        List<TollPlaza> praçasCruzadas = praçasFuture.join();
        List<OsmFuelStation> postos = postosFuture.join();
        List<RoadAlert> alertas = alertasFuture.join();
        List<TrafficReport> trafego = trafegoFuture.join();

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
        List<CoordinateDTO> paradasNaRota = paradas.stream().map(c -> new CoordinateDTO(c.lat(), c.lon())).toList();
        List<RoadAlertResponseDTO> alertasDTO = alertas.stream()
                .map(a -> new RoadAlertResponseDTO(a.getId(), a.getTipo(), a.getLat(), a.getLng(), a.getCriadoEm(),
                        a.getExpiraEm()))
                .toList();
        List<TrafficReportResponseDTO> trafegoDTO = trafego.stream()
                .map(t -> new TrafficReportResponseDTO(t.getId(), t.getSeveridade(), t.getLat(), t.getLng(),
                        t.getCriadoEm(), t.getExpiraEm()))
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
                passos,
                paradasNaRota,
                alertasDTO,
                trafegoDTO);
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
