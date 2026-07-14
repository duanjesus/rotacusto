package com.rotacusto.dto.response;

/** Par marca+modelo distinto, sem ano — usado no passo 1 da escolha de veículo. */
public record VehicleModelSummaryDTO(String marca, String modelo) {
}
