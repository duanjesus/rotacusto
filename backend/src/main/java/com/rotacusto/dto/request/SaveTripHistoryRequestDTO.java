package com.rotacusto.dto.request;

import com.rotacusto.dto.response.TripCostBreakdownDTO;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * O app manda de volta o {@code TripCostBreakdownDTO} inteiro que já recebeu de
 * {@code POST /api/trips/estimate} — mais simples que recalcular no back-end, e
 * garante que o que fica salvo é exatamente o que o usuário viu na tela.
 * origem/destino vêm à parte porque o breakdown em si não carrega texto livre,
 * só coordenadas.
 */
public record SaveTripHistoryRequestDTO(
        @NotBlank String origem,
        @NotBlank String destino,
        @NotNull @Valid TripCostBreakdownDTO breakdown) {
}
