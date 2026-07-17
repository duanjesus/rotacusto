package com.rotacusto.dto.request;

import jakarta.validation.constraints.NotNull;

/** raioKm nulo usa o raio padrão configurado (rotacusto.traffic-reports.detection-radius-km). */
public record NearbyTrafficRequestDTO(
        @NotNull Double lat,
        @NotNull Double lng,
        Double raioKm) {
}
