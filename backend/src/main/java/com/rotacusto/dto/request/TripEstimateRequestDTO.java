package com.rotacusto.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * O veículo é informado por {@code vehicleModelId} (escolhido do catálogo) OU por um
 * {@code vehicleProfile} manual — validado em TripEstimationService. O preço também
 * é condicional ao combustível do veículo resolvido: {@code precoPorLitro} serve pra
 * gasolina/etanol/diesel (o veículo escolhido já fixa qual dos três é), {@code precoPorKWh}
 * só pra elétrico — exatamente um dos dois é exigido, também validado em
 * TripEstimationService (não dá pra expressar essa regra com anotações simples).
 */
public record TripEstimateRequestDTO(
        @NotBlank String origem,
        @NotBlank String destino,
        Long vehicleModelId,
        VehicleProfileRequestDTO vehicleProfile,
        @Positive Double precoPorLitro,
        @Positive Double precoPorKWh) {
}
