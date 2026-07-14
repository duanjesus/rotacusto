package com.rotacusto.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.rotacusto.domain.Coordinates;
import com.rotacusto.domain.RouteResult;
import com.rotacusto.domain.TripCostBreakdown;
import com.rotacusto.domain.TripCostCalculator;
import com.rotacusto.domain.VehicleProfile;
import com.rotacusto.domain.cost.TollCostCalculator;
import com.rotacusto.dto.request.TripEstimateRequestDTO;
import com.rotacusto.dto.request.VehicleProfileRequestDTO;
import com.rotacusto.dto.response.CoordinateDTO;
import com.rotacusto.dto.response.TollPlazaResponseDTO;
import com.rotacusto.dto.response.TripCostBreakdownDTO;
import com.rotacusto.entity.TollPlaza;
import com.rotacusto.entity.VehicleModel;

@Service
public class TripEstimationService {

    private final GeocodingService geocodingService;
    private final RoutingService routingService;
    private final VehicleModelService vehicleModelService;
    private final TollService tollService;

    public TripEstimationService(GeocodingService geocodingService, RoutingService routingService,
            VehicleModelService vehicleModelService, TollService tollService) {
        this.geocodingService = geocodingService;
        this.routingService = routingService;
        this.vehicleModelService = vehicleModelService;
        this.tollService = tollService;
    }

    public TripCostBreakdownDTO estimate(TripEstimateRequestDTO request) {
        Coordinates origem = geocodingService.resolve(request.origem());
        Coordinates destino = geocodingService.resolve(request.destino());
        RouteResult route = routingService.route(origem, destino);

        VehicleProfile profile = resolveProfile(request);
        List<TollPlaza> praçasCruzadas = tollService.findCrossedPlazas(route.geometria());
        TripCostBreakdown breakdown = TripCostCalculator.calculate(route.distanciaKm(), route.duracaoMin(), profile,
                praçasCruzadas);

        List<CoordinateDTO> geometria = route.geometria().stream()
                .map(c -> new CoordinateDTO(c.lat(), c.lon()))
                .toList();
        List<TollPlazaResponseDTO> pedagios = praçasCruzadas.stream()
                .map(p -> new TollPlazaResponseDTO(p.getNome(), p.getRodovia(), p.getConcessionaria(), p.getLat(),
                        p.getLng(), TollCostCalculator.calculate(List.of(p), profile)))
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
                pedagios);
    }

    private VehicleProfile resolveProfile(TripEstimateRequestDTO request) {
        if (request.vehicleModelId() != null) {
            VehicleModel model = vehicleModelService.findById(request.vehicleModelId());
            return VehicleProfile.fromModel(model, request.precoCombustivelPorLitro());
        }

        VehicleProfileRequestDTO manual = request.vehicleProfile();
        if (manual == null) {
            throw new IllegalArgumentException("Informe vehicleModelId (catálogo) ou vehicleProfile (manual).");
        }
        return new VehicleProfile(
                manual.tipo(),
                manual.consumoKmPorLitro(),
                manual.numeroEixos(),
                manual.custoDesgastePorKm(),
                request.precoCombustivelPorLitro());
    }
}
