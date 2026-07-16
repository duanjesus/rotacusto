package com.rotacusto.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * O veículo é informado por {@code vehicleModelId} (escolhido do catálogo) OU por um
 * {@code vehicleProfile} manual — validado em TripEstimationService. O preço também
 * é condicional ao combustível do veículo resolvido: {@code precoPorLitro} serve pra
 * gasolina/etanol/diesel (o veículo escolhido já fixa qual dos três é), {@code precoPorKWh}
 * só pra elétrico — exatamente um dos dois é exigido, também validado em
 * TripEstimationService (não dá pra expressar essa regra com anotações simples).
 *
 * {@code paradas} são zero ou mais pontos intermediários (texto livre ou "lat,lon",
 * mesmo formato de origem/destino), visitados nesta ordem entre origem e destino — uma
 * rota única contínua, não N viagens separadas. Nulo ou vazio = comportamento de sempre.
 */
public record TripEstimateRequestDTO(
        @NotBlank String origem,
        @NotBlank String destino,
        Long vehicleModelId,
        VehicleProfileRequestDTO vehicleProfile,
        @Positive Double precoPorLitro,
        @Positive Double precoPorKWh,
        List<String> paradas) {
}
