package com.rotacusto.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * O veículo é informado por {@code vehicleModelId} (escolhido do catálogo) OU por um
 * {@code vehicleProfile} manual — validado em TripEstimationService. O preço também
 * é condicional ao tipo de energia do veículo resolvido: {@code precoCombustivelPorLitro}
 * para veículos a combustão, {@code precoPorKWh} para elétricos — exatamente um dos
 * dois é exigido, também validado em TripEstimationService (não dá pra expressar essa
 * regra com anotações simples).
 */
public record TripEstimateRequestDTO(
        @NotBlank String origem,
        @NotBlank String destino,
        Long vehicleModelId,
        VehicleProfileRequestDTO vehicleProfile,
        @Positive Double precoCombustivelPorLitro,
        @Positive Double precoPorKWh) {
}
