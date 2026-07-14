package com.rotacusto.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * O veículo é informado por {@code vehicleModelId} (escolhido do catálogo) OU por um
 * {@code vehicleProfile} manual — validado em TripEstimationService.
 */
public record TripEstimateRequestDTO(
        @NotBlank String origem,
        @NotBlank String destino,
        Long vehicleModelId,
        VehicleProfileRequestDTO vehicleProfile,
        @NotNull @Positive Double precoCombustivelPorLitro) {
}
