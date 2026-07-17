package com.rotacusto.dto.response;

import java.util.List;

public record TripCostBreakdownDTO(
        double distanciaKm,
        double duracaoMin,
        double custoCombustivel,
        double custoDesgaste,
        double custoPedagio,
        double custoLanche,
        double total,
        List<CoordinateDTO> geometriaRota,
        List<TollPlazaResponseDTO> pedagiosNaRota,
        List<FuelStationResponseDTO> postosNaRota,
        FuelStationResponseDTO postoSugerido,
        List<RouteStepDTO> passosRota,
        List<CoordinateDTO> paradasNaRota,
        List<RoadAlertResponseDTO> alertasNaRota) {
}
