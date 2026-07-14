package com.rotacusto.service;

import java.util.List;
import java.util.Optional;

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
import com.rotacusto.dto.response.TollPlazaResponseDTO;
import com.rotacusto.dto.response.TripCostBreakdownDTO;
import com.rotacusto.entity.TollPlaza;
import com.rotacusto.entity.VehicleModel;
import com.rotacusto.entity.enums.TipoEnergia;

@Service
public class TripEstimationService {

    private final GeocodingService geocodingService;
    private final RoutingService routingService;
    private final VehicleModelService vehicleModelService;
    private final TollService tollService;
    private final FuelStationService fuelStationService;

    public TripEstimationService(GeocodingService geocodingService, RoutingService routingService,
            VehicleModelService vehicleModelService, TollService tollService, FuelStationService fuelStationService) {
        this.geocodingService = geocodingService;
        this.routingService = routingService;
        this.vehicleModelService = vehicleModelService;
        this.tollService = tollService;
        this.fuelStationService = fuelStationService;
    }

    public TripCostBreakdownDTO estimate(TripEstimateRequestDTO request) {
        Coordinates origem = geocodingService.resolve(request.origem());
        Coordinates destino = geocodingService.resolve(request.destino());
        RouteResult route = routingService.route(origem, destino);

        VehicleProfile profile = resolveProfile(request);
        List<TollPlaza> praçasCruzadas = tollService.findCrossedPlazas(route.geometria());
        TripCostBreakdown breakdown = TripCostCalculator.calculate(route.distanciaKm(), route.duracaoMin(), profile,
                praçasCruzadas);

        // Postos de gasolina não fazem sentido pra elétrico (precisaria de pontos de
        // recarga, um recurso diferente, fora de escopo por enquanto).
        boolean eletrico = profile.tipoEnergia() == TipoEnergia.ELETRICO;
        List<OsmFuelStation> postos = eletrico ? List.of() : fuelStationService.findStationsNearRoute(route.geometria());
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
                postoSugerido.map(p -> new FuelStationResponseDTO(p.nome(), p.lat(), p.lon())).orElse(null));
    }

    private VehicleProfile resolveProfile(TripEstimateRequestDTO request) {
        if (request.vehicleModelId() != null) {
            VehicleModel model = vehicleModelService.findById(request.vehicleModelId());
            double preco = precoParaTipoEnergia(request, model.getTipoEnergia());
            return VehicleProfile.fromModel(model, preco);
        }

        VehicleProfileRequestDTO manual = request.vehicleProfile();
        if (manual == null) {
            throw new IllegalArgumentException("Informe vehicleModelId (catálogo) ou vehicleProfile (manual).");
        }
        double preco = precoParaTipoEnergia(request, manual.tipoEnergia());
        return new VehicleProfile(
                manual.tipo(),
                manual.tipoEnergia(),
                manual.consumoPorUnidade(),
                manual.numeroEixos(),
                manual.custoDesgastePorKm(),
                preco);
    }

    /**
     * Preço é condicional ao tipo de energia do veículo resolvido — não dá pra
     * expressar essa regra (exatamente um dos dois campos) com anotações simples.
     */
    private double precoParaTipoEnergia(TripEstimateRequestDTO request, TipoEnergia tipoEnergia) {
        if (tipoEnergia == TipoEnergia.ELETRICO) {
            if (request.precoPorKWh() == null) {
                throw new IllegalArgumentException("Veículo elétrico: informe precoPorKWh.");
            }
            return request.precoPorKWh();
        }
        if (request.precoCombustivelPorLitro() == null) {
            throw new IllegalArgumentException("Veículo a combustão: informe precoCombustivelPorLitro.");
        }
        return request.precoCombustivelPorLitro();
    }
}
