package com.rotacusto.dto.request;

import jakarta.validation.constraints.NotNull;

/** raioKm nulo usa o raio padrão configurado (rotacusto.road-alerts.detection-radius-km). */
public record NearbyRoadAlertsRequestDTO(
        @NotNull Double lat,
        @NotNull Double lng,
        Double raioKm) {
}
